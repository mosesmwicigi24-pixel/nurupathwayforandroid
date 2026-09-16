package org.nuruplace.member

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.feature.shell.RootScaffold
import org.nuruplace.member.ui.theme.NuruTheme

/** Launcher-shortcut / notification destination, handed from the intent to the
 *  nav graph. Consumed exactly once so recompositions don't re-navigate.
 *  Backed by Compose state (not a plain var) so a WARM dispatch — the
 *  activity already alive (`singleTop`), `onNewIntent` fires but `setContent`
 *  does not re-run — still reaches the nav graph: MainShell keys its
 *  consuming `LaunchedEffect` off `PendingDest.route`, so a write here from
 *  `onNewIntent` re-triggers it exactly like a fresh cold-start read would.
 *  (Read with a Friend's nuru://join/{token} tapped while the app is already
 *  open needed this; it also fixes the same latent gap for every existing
 *  notification/shortcut destination.) */
object PendingDest {
    var route: String? by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    fun set(dest: String?) { route = dest }
    fun consume(): String? = route.also { route = null }
}

/** nuru://join/{token} OR the verified App Link https://pathway.nuruplace.org/
 *  join/{token} (Read with a Friend, docs/READING_SOCIAL_PLAN.md §5) → the
 *  in-app invite-preview route. The token is the last path segment; any query
 *  string is ignored. Null for any other/absent intent data. */
private fun readingJoinDest(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    val customScheme = uri.scheme == "nuru" && uri.host == "join"
    val appLink = uri.scheme == "https" && uri.host == "pathway.nuruplace.org" && uri.path.orEmpty().startsWith("/join/")
    if (!customScheme && !appLink) return null
    val token = uri.lastPathSegment?.takeIf { isJoinToken(it) } ?: return null
    return "reading/join/$token"
}

/** A join token is opaque URL-safe text; anything else (a slash, a space)
 *  would break the `reading/join/{token}` route match, so it is refused. */
private fun isJoinToken(s: String): Boolean =
    s.isNotBlank() && s.length <= 128 && s.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

/** `join_token=<token>` out of a Play Install Referrer string ("utm_source=…&
 *  join_token=…"), tolerating a once-more-encoded payload (some link builders
 *  double-encode the referrer). Null when absent or malformed. */
private fun joinTokenFromReferrer(referrer: String): String? {
    fun parse(s: String): String? = s.split('&')
        .map { it.substringBefore('=') to it.substringAfter('=', "") }
        .firstOrNull { (k, _) -> k == "join_token" }
        ?.second?.let { android.net.Uri.decode(it) }
        ?.takeIf { isJoinToken(it) }
    return parse(referrer) ?: parse(android.net.Uri.decode(referrer))
}

// FragmentActivity (not the bare ComponentActivity) — BiometricPrompt for the
// Broadcast fingerprint unlock (§5.3 step-up, data/BroadcastLock.kt) needs a
// FragmentActivity host. FragmentActivity IS a ComponentActivity, so
// enableEdgeToEdge()/installSplashScreen()/setContent{} below are unaffected.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // branded TGNM splash on every cold start
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PendingDest.set(readingJoinDest(intent) ?: intent?.getStringExtra("nuru.dest"))
        // Store-then-plan (Read with a Friend): a member who tapped a join link
        // WITHOUT the app installed lands on Play; the referrer Play hands back
        // on first launch carries `join_token=<token>` so the invite still
        // opens. Read exactly once per install, never blocks the UI, and a real
        // deep link in this same launch always wins.
        if (PendingDest.route == null) checkInstallReferrerOnce()
        setContent {
            NuruTheme { RootScaffold() }
        }
    }

    private fun checkInstallReferrerOnce() {
        if (AppPrefs.installReferrerChecked) return
        // Flagged BEFORE the attempt: one shot per install, whatever Play answers.
        AppPrefs.installReferrerChecked = true
        runCatching {
            val client = InstallReferrerClient.newBuilder(this).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    runCatching {
                        if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val referrer = client.installReferrer.installReferrer.orEmpty()
                            joinTokenFromReferrer(referrer)?.let { token ->
                                if (PendingDest.route == null) PendingDest.set("reading/join/$token")
                            }
                        }
                    }
                    runCatching { client.endConnection() }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PendingDest.set(readingJoinDest(intent) ?: intent.getStringExtra("nuru.dest"))
    }
}
