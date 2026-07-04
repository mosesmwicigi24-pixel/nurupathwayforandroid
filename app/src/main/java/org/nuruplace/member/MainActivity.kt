package org.nuruplace.member

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.nuruplace.member.feature.shell.RootScaffold
import org.nuruplace.member.ui.theme.NuruTheme

/** Launcher-shortcut / notification destination, handed from the intent to the
 *  nav graph. Consumed exactly once so recompositions don't re-navigate. */
object PendingDest {
    var route: String? = null
    fun consume(): String? = route.also { route = null }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // branded TGNM splash on every cold start
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PendingDest.route = intent?.getStringExtra("nuru.dest")
        setContent {
            NuruTheme { RootScaffold() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PendingDest.route = intent.getStringExtra("nuru.dest")
    }
}
