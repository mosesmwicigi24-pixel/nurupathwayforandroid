package org.nuruplace.member

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
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

/** nuru://join/{token} (Read with a Friend, docs/READING_SOCIAL_PLAN.md §5) →
 *  the in-app invite-preview route. Null for any other/absent intent data. */
private fun readingJoinDest(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "nuru" || uri.host != "join") return null
    val token = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
    return "reading/join/$token"
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
        setContent {
            NuruTheme { RootScaffold() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PendingDest.set(readingJoinDest(intent) ?: intent.getStringExtra("nuru.dest"))
    }
}
