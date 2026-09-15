// HTTP stack for the member app — the Android counterpart of the iOS APIClient
// actor and RN client.ts. Injects the gateway JWT (§1.3), targets the versioned
// API (§3.1), and silently rotates the access token once on a 401 via the stored
// refresh token. Refresh is single-flight (§5.3): OkHttp serializes Authenticator
// calls and a lock + token-change check means concurrent 401s share ONE refresh,
// so a rotated (one-time-use) refresh token is never spent twice.
package org.nuruplace.member.data.net

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.nuruplace.member.BuildConfig
import org.nuruplace.member.data.TokenVault
import org.nuruplace.member.data.offline.Connectivity
import org.nuruplace.member.data.offline.OfflineDb
import org.nuruplace.member.data.offline.OfflineQueue
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Process-wide HTTP holder, initialised once from the Application. */
object Net {
    lateinit var client: ApiClient
        private set

    fun init(context: Context) {
        if (!::client.isInitialized) client = ApiClient(context.applicationContext)
    }
}

class ApiClient(context: Context) {
    val vault = TokenVault(context)

    /** Invoked when the refresh token itself is dead — the app returns to /login. */
    var onSessionExpired: (() -> Unit)? = null

    /** Fire-and-forget scope for telemetry (e.g. the final engagement flush on
     *  screen exit) that must outlive a composable's own lifecycle scope. */
    val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase   // camelCase ↔ snake_case, like the iOS decoder
    }

    private val jsonMedia = "application/json".toMediaType()
    private val refreshLock = Any()

    private val authInterceptor = Interceptor { chain ->
        val b = chain.request().newBuilder().header("Accept", "application/json")
        vault.accessToken?.let { b.header("Authorization", "Bearer $it") }
        chain.proceed(b.build())
    }

    // ── The last good copy ──────────────────────────────────────────────────
    // The server has been vanishing for two to four hours at a time (pathway
    // docs/DEPLOYMENT.md, incident ledger 2026-09-05→11). While it is away a
    // member should see a stale Home, not a blank page. So every GET's last
    // good response is kept on disk, and served ONLY when the wire fails:
    // online reads always go to the network (max-age=0), so nothing is ever
    // stale while the server answers. Cleared whenever the signed-in member
    // changes, so nobody is shown someone else's copies.
    private val httpCache = Cache(File(context.cacheDir, "api-http"), 10L * 1024 * 1024)

    /** Reads whose last good copy is worth keeping: GETs that are not auth and
     *  not the offline engine's own sync traffic (it keeps its own truth). */
    private fun keepsLastGoodCopy(req: Request): Boolean {
        if (req.method != "GET") return false
        val path = req.url.encodedPath.lowercase()
        return !path.contains("/auth/") && !path.contains("/sync")
    }

    /** Network interceptor: marks a good GET response storable. "public" is what
     *  lets OkHttp keep a response to a request that carried an Authorization
     *  header; max-age=0 sends every online read to the wire regardless. */
    private val storeLastGoodCopy = Interceptor { chain ->
        val req = chain.request()
        val resp = chain.proceed(req)
        if (!keepsLastGoodCopy(req) || !resp.isSuccessful) return@Interceptor resp
        resp.newBuilder().header("Cache-Control", "public, max-age=0").removeHeader("Pragma").build()
    }

    /** Application interceptor, first in the chain: a GET that failed on the
     *  wire is answered from its last good copy (up to seven days old); a GET
     *  with no copy, and every write, fails exactly as before. */
    private val serveLastGoodCopy = Interceptor { chain ->
        val req = chain.request()
        try {
            chain.proceed(req).also { if (it.networkResponse != null) ServerReach.reached() }
        } catch (e: IOException) {
            if (!keepsLastGoodCopy(req)) throw e
            val copy = runCatching {
                chain.proceed(
                    req.newBuilder()
                        .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build())
                        .build(),
                )
            }.getOrNull()
            // OkHttp answers an unsatisfiable only-if-cached request with a synthetic 504.
            if (copy != null && copy.code == 200) {
                ServerReach.servedStale()
                copy.newBuilder().header("X-Nuru-Stale", "1").build()
            } else {
                copy?.close()
                throw e
            }
        }
    }

    /** Drop every kept copy — on sign-in and sign-out, never on a token refresh. */
    private fun forgetLastGoodCopies() {
        runCatching { httpCache.evictAll() }
        ServerReach.reached()
    }

    // Bare client (no authenticator) used only for the refresh call, so refresh
    // can never recurse into itself.
    private val bareClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val authenticator = Authenticator { _, response ->
        if (responseCount(response) >= 2) return@Authenticator null   // already retried once — give up
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(refreshLock) {
            val current = vault.accessToken
            // Another concurrent 401 already refreshed → just retry with the fresh token.
            if (current != null && current != failedToken) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $current").build()
            }
            if (!doRefresh()) {
                vault.clear()
                forgetLastGoodCopies()
                onSessionExpired?.invoke()
                return@Authenticator null
            }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${vault.accessToken}").build()
        }
    }

    private fun doRefresh(): Boolean {
        val rt = vault.refreshToken ?: return false
        return try {
            val body = json.encodeToString(RefreshBody.serializer(), RefreshBody(rt)).toRequestBody(jsonMedia)
            val req = Request.Builder().url(base("auth/token/refresh")).post(body).build()
            bareClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val s = json.decodeFromString(Session.serializer(), resp.body!!.string())
                vault.set(s.accessToken, s.refreshToken)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private val okhttp = OkHttpClient.Builder()
        // Bounded timeouts so a flaky/slow mobile network fails predictably with a
        // clear message instead of hanging (which read as "the app froze" / an ANR
        // and "can't connect"). Generous enough for a slow first login, capped by
        // an overall callTimeout so no request ever hangs indefinitely. Connect is
        // 10 s: a SYN that Paris has not answered in ten seconds will not be
        // answered in fifteen, and the last good copy is waiting behind it.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(httpCache)
        .addInterceptor(serveLastGoodCopy)
        .addInterceptor(authInterceptor)
        .addNetworkInterceptor(storeLastGoodCopy)
        .authenticator(authenticator)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    val api: MemberApi = Retrofit.Builder()
        .baseUrl(baseUrl())
        .client(okhttp)
        .addConverterFactory(json.asConverterFactory(jsonMedia))
        .build()
        .create(MemberApi::class.java)

    /** The offline mutation queue + its replay engine (§1.7). Payload JSON is
     *  structure-preserving, so a plain Json (no naming strategy) is used for it. */
    val offline: OfflineQueue = OfflineQueue(
        dao = OfflineDb.get(context).mutations(),
        connectivity = Connectivity(context),
        apiProvider = { api },
        json = Json { ignoreUnknownKeys = true },
    ).also { it.start() }

    /** Login is the one endpoint that may return a 2FA challenge instead of a session. */
    suspend fun login(email: String, password: String): LoginResponse {
        val res = api.login(LoginBody(email.trim(), password))
        res.session?.let { vault.set(it.accessToken, it.refreshToken); forgetLastGoodCopies() }
        return res
    }

    suspend fun completeMfa(mfaToken: String, code: String): Session {
        val s = api.completeMfa(MfaBody(mfaToken, code))
        vault.set(s.accessToken, s.refreshToken)
        forgetLastGoodCopies()
        return s
    }

    /** Sign-out: the next member on this phone starts with no copies of this one's reads. */
    fun signOutLocally() {
        vault.clear()
        forgetLastGoodCopies()
    }

    private fun baseUrl(): String {
        val b = BuildConfig.API_BASE_URL
        return if (b.endsWith("/")) b else "$b/"
    }

    private fun base(path: String) = baseUrl() + path

    /** The API's origin (scheme+host[+port]) with any trailing versioned
     *  `/v1` suffix stripped. Nuru Live hands back RELATIVE media paths
     *  (`hls_url` / `recording_url`) rooted at the origin, not at `/v1` —
     *  callers resolve them via [resolveMediaUrl] rather than concatenating
     *  onto [baseUrl] directly. */
    fun mediaOrigin(): String {
        val trimmed = BuildConfig.API_BASE_URL.trimEnd('/')
        return trimmed.removeSuffix("/v1")
    }

    /** Resolve a possibly-relative media path against [mediaOrigin]. Already
     *  absolute (http/https) urls pass through unchanged. */
    fun resolveMediaUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return mediaOrigin() + (if (path.startsWith("/")) path else "/$path")
    }

    private fun responseCount(r: Response): Int {
        var n = 1
        var p = r.priorResponse
        while (p != null) { n++; p = p.priorResponse }
        return n
    }
}
