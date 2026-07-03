// Give — online-only (§5.6). Pick a fund + amount + method, create a REAL intent
// server-side (never fabricated). M-Pesa STK push is the default; PayPal returns
// an approve URL; card is flagged "soon" (needs the Stripe SDK, SAQ-A). Port of
// the iOS GivingView.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GiveBody
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

private val FUNDS = listOf("tithe" to "Tithe", "offering" to "Offering", "gift" to "Gift", "mission" to "Mission", "discipleship" to "Discipleship")
private val PRESETS = listOf(100, 500, 1000, 2000, 5000)

fun fmtMoney(minor: Int, currency: String): String = "$currency ${"%,.2f".format(minor / 100.0)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GivingScreen(onBack: () -> Unit, onOpenStatement: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fund by remember { mutableStateOf("tithe") }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("mpesa") }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<GivingIntentResult?>(null) }

    val amt = amount.toIntOrNull() ?: 0

    result?.let { r ->
        GiveResult(r, onDone = { result = null; amount = "" })
        return
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader("Give", kicker = "Return to God what is His", onBack = onBack)
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            NuruCard {
                Kicker("Fund")
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FUNDS.forEach { (key, label) -> Chip(label, fund == key) { fund = key } }
                }
            }
            NuruCard {
                Kicker("Amount (KES)")
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PRESETS.forEach { p -> Chip("KES $p", amount == p.toString()) { amount = p.toString() } }
                }
            }
            NuruCard {
                Kicker("Method")
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Chip("M-Pesa", method == "mpesa") { method = "mpesa" }
                    Chip("PayPal", method == "paypal") { method = "paypal" }
                    Chip("Card · soon", false) {}
                }
                if (method == "mpesa") {
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone (2547…)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
            PrimaryButton(
                label = if (amt > 0) "Give ${fmtMoney(amt * 100, "KES")}" else "Give",
                loading = busy,
                enabled = amt > 0 && (method != "mpesa" || phone.isNotBlank()),
                onClick = {
                    if (!busy) {
                        busy = true; error = null
                        scope.launch {
                            try {
                                result = Net.client.api.giving(GiveBody(fund, amt * 100, "KES", method, phone.ifBlank { null }, UUID.randomUUID().toString()))
                            } catch (e: Exception) { error = ApiException.message(e) } finally { busy = false }
                        }
                    }
                },
            )
            TextButton(onClick = onOpenStatement) { Text("View my giving statement", style = NuruType.cardCta, color = Nuru.goldLo) }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Radii.pill))
            .background(if (on) Nuru.goldTint else Nuru.surface)
            .border(1.dp, if (on) Nuru.gold else Nuru.border, RoundedCornerShape(Radii.pill))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, style = NuruType.cardCta, color = if (on) Nuru.goldChipText else Nuru.ink) }
}

@Composable
private fun GiveResult(r: GivingIntentResult, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Nuru.ceremonyGradient).padding(Spacing.screen),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🙏", style = NuruType.display)
        Spacer(Modifier.height(Spacing.base))
        Text(
            when {
                r.approveUrl != null -> "Continue on PayPal"
                r.provider == "mpesa" || r.providerRef != null -> "Check your phone"
                else -> "Thank you for giving"
            },
            style = NuruType.title, color = Nuru.onNavy, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            when {
                r.approveUrl != null -> "Approve the gift in PayPal to complete it."
                r.provider == "mpesa" || r.providerRef != null -> "Enter your M-Pesa PIN on the prompt we just sent."
                else -> "Your gift is being processed (status: ${r.status})."
            },
            style = NuruType.body, color = Nuru.onNavyDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Box(Modifier.clickable { onDone() }.padding(Spacing.md)) { Text("Done", style = NuruType.cardCta, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
    }
}
