// Devotional — today's word + a reflection the member can save (which also ticks
// the Reflection rhythm, server-side). Port of the iOS DevotionalView.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Devotional
import org.nuruplace.member.data.net.DevotionalReflectionBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun DevotionalScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.devotional() }) { d: Devotional, _ ->
        val scope = rememberCoroutineScope()
        var reflection by remember(d.devotionalId) { mutableStateOf(d.myReflection ?: "") }
        var busy by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(d.myReflection != null) }
        var error by remember { mutableStateOf<String?>(null) }

        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            ScreenHeader(d.title, kicker = d.series ?: "Devotional", onBack = onBack)
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                d.scriptureRef?.let { ref ->
                    NuruCard {
                        Kicker(ref)
                        d.scriptureText?.let {
                            Spacer(Modifier.height(Spacing.sm))
                            Text(it, style = NuruType.bodyLg, color = Nuru.ink)
                        }
                    }
                }
                Text(d.body, style = NuruType.bodyLg, color = Nuru.ink)

                NuruCard {
                    Kicker(d.reflectionPrompt ?: "Reflect")
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = reflection,
                        onValueChange = { reflection = it; saved = false },
                        minLines = 3,
                        shape = RoundedCornerShape(Radii.control),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Spacer(Modifier.height(Spacing.sm)); Text(it, style = NuruType.caption, color = Nuru.danger) }
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        label = if (saved) "Saved ✓" else "Save reflection",
                        loading = busy,
                        enabled = reflection.isNotBlank() && !saved,
                        onClick = {
                            if (!busy) {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        Net.client.api.saveDevotionalReflection(
                                            DevotionalReflectionBody(d.devotionalId, reflection.trim()),
                                        )
                                        saved = true
                                    } catch (e: Exception) {
                                        error = ApiException.message(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
