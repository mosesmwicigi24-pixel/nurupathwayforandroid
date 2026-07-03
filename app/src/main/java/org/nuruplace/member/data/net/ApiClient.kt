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
import okhttp3.Authenticator
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

    // Bare client (no authenticator) used only for the refresh call, so refresh
    // can never recurse into itself.
    private val bareClient = OkHttpClient()

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
        .addInterceptor(authInterceptor)
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
        res.session?.let { vault.set(it.accessToken, it.refreshToken) }
        return res
    }

    suspend fun completeMfa(mfaToken: String, code: String): Session {
        val s = api.completeMfa(MfaBody(mfaToken, code))
        vault.set(s.accessToken, s.refreshToken)
        return s
    }

    private fun baseUrl(): String {
        val b = BuildConfig.API_BASE_URL
        return if (b.endsWith("/")) b else "$b/"
    }

    private fun base(path: String) = baseUrl() + path

    private fun responseCount(r: Response): Int {
        var n = 1
        var p = r.priorResponse
        while (p != null) { n++; p = p.priorResponse }
        return n
    }
}
