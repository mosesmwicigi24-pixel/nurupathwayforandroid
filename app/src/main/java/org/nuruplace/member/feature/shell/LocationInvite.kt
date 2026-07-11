// Location-first onboarding + silent geotag refresh — port of iOS
// LocationInvite.swift.
//   • LocationInviteDialog — shown ONCE right after first login: a warm ask to
//     share an approximate area so leaders can knit cells by neighborhood.
//     Accepting fires the OS permission prompt, takes one coarse fix, and POSTs
//     /me/location. Declining never asks again (Settings keeps the switch).
//   • RefreshLocationIfSharing — every app open, if the member said yes AND the
//     OS permission is still granted, silently re-POST a coarse fix. The server
//     keeps only a ~1.2 km geohash either way.
package org.nuruplace.member.feature.shell

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.LocationBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private val InviteGold = Color(0xFFE8CA6C)

/** Silent per-open refresh for members already sharing. Call from the shell. */
@Composable
fun RefreshLocationIfSharing() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (!AppPrefs.shareLocation) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect // revoked at OS level — respect it
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("MissingPermission")
                Tasks.await(client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null))
            }.getOrNull()
        }
        if (loc != null) runCatching { Net.client.api.shareLocation(LocationBody(loc.latitude, loc.longitude)) }
    }
}

@Composable
fun LocationInviteDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    fun captureAndShare() {
        working = true
        scope.launch {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("MissingPermission")
                    Tasks.await(client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null))
                }.getOrNull()
            }
            if (loc != null) {
                runCatching { Net.client.api.shareLocation(LocationBody(loc.latitude, loc.longitude)) }
                AppPrefs.updateShareLocation(true)
            }
            working = false
            onDismiss()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) captureAndShare() else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0F2A47), Color(0xFF081020))))
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(InviteGold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text("📍", style = NuruType.display) }
            Spacer(Modifier.height(16.dp))
            Text(
                "Be found by your church family",
                style = NuruType.rowTitle.copy(fontSize = 21.sp), color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Share your approximate area — never your exact position — and your leaders can connect you with brothers and sisters near you: a cell close to home, someone to walk with.",
                style = NuruType.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "We keep only a coarse ~1 km area. You can stop sharing anytime in Profile → Settings.",
                style = NuruType.micro, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(InviteGold, Color(0xFFB6862F))))
                    .clickable(enabled = !working) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) captureAndShare() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (working) CircularProgressIndicator(color = Color(0xFF1E2A1F), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Share my area", style = NuruType.cardCta, color = Color(0xFF1E2A1F))
            }
            Spacer(Modifier.height(Spacing.sm))
            Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text("Not now", style = NuruType.body, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
