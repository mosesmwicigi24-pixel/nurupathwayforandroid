package org.nuruplace.member

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.nuruplace.member.feature.shell.RootScaffold
import org.nuruplace.member.ui.theme.NuruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NuruTheme { RootScaffold() }
        }
    }
}
