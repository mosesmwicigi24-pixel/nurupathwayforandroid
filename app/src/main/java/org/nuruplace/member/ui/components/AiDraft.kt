// AI reply drafting — a reusable ✨ composer add-on. Tap → Nuru reads the recent
// messages (one summarize-and-draft call through the existing POST assistant/chat),
// then a bottom sheet shows a one-line summary + an EDITABLE draft the member can
// send ("Use draft" → onUseDraft), tweak in place, or throw away ("Discard").
// Colors mirror the "Quick help from Nuru" AI card in ChatScreen (purple orb
// 0xFFC4B5FD→0xFF7C3AED→0xFF2A1259 + brand gold 0xFFC89B3C).
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.AssistantChatBody
import org.nuruplace.member.data.net.AssistantMessage
import org.nuruplace.member.data.net.Net

// AI-card palette (must match CHAT.aiOrb / CHAT.aiPurpleGlow / CHAT.gold in ChatShared).
private val AiPurple = Color(0xFF7C3AED)
private val AiPurpleLight = Color(0xFFC4B5FD)
private val AiPurpleDeep = Color(0xFF2A1259)
private val AiGold = Color(0xFFC89B3C)
private val AiOrb = Brush.radialGradient(listOf(AiPurpleLight, AiPurple, AiPurpleDeep))
private val GoldGrad = Brush.verticalGradient(listOf(Color(0xFFE5BC3A), Color(0xFFC9A227), Color(0xFFA8861C)))
private val Pill = RoundedCornerShape(999.dp)

/**
 * ✨ icon button for a composer row. [recentMessages] is (author → text),
 * oldest→newest — callers pass at most 5. [conversationId] (when the surface is
 * a chat thread) lets the server ground on messages the member can access.
 * [onUseDraft] receives the (possibly edited) draft; the caller puts it in its
 * composer so the member still explicitly taps send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDraftButton(
    recentMessages: List<Pair<String, String>>,
    conversationId: String? = null,
    onUseDraft: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    fun requestDraft() {
        if (busy) return
        busy = true
        scope.launch {
            val reply = runCatching {
                Net.client.api.assistantChat(
                    AssistantChatBody(
                        messages = listOf(AssistantMessage("user", buildDraftPrompt(recentMessages))),
                        conversationId = conversationId,
                        contextLimit = 5,
                    ),
                ).reply
            }.getOrNull()
            busy = false
            failed = reply.isNullOrBlank()
            val (s, d) = parseSummaryDraft(reply.orEmpty())
            summary = s
            draft = d
            sheetOpen = true
        }
    }

    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(AiOrb)
            .border(1.dp, AiGold.copy(alpha = 0.45f), CircleShape)
            .clickable(enabled = !busy) { requestDraft() },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(13.dp))
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Draft a reply with Nuru", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = GrowPal.white) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(AiOrb), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text("NURU SUGGESTS", style = gInter(10, FontWeight.Bold, 1.6f), color = GrowPal.eyebrow)
                }
                Text(
                    if (failed) "Nuru couldn't reach the assistant just now — you can still write your own reply below." else summary ?: "Here's a reply you could send.",
                    style = gInter(12).copy(lineHeight = 17.sp),
                    color = GrowPal.ink400,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.surface)
                        .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    if (draft.isBlank()) Text("Write your reply…", style = gInter(13), color = GrowPal.ink300)
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = gInter(13).copy(lineHeight = 19.sp, color = GrowPal.ink),
                        maxLines = 8,
                        cursorBrush = SolidColor(AiGold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val canUse = draft.isNotBlank()
                    Box(
                        Modifier
                            .clip(Pill)
                            .background(GoldGrad)
                            .clickable(enabled = canUse) {
                                onUseDraft(draft.trim())
                                sheetOpen = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("Use draft", style = gInter(13, FontWeight.Bold), color = if (canUse) Color.White else Color.White.copy(alpha = 0.6f))
                    }
                    Box(
                        Modifier
                            .clip(Pill)
                            .background(GrowPal.white)
                            .border(1.dp, GrowPal.border, Pill)
                            .clickable { sheetOpen = false }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text("Discard", style = gInter(13, FontWeight.SemiBold), color = GrowPal.ink600)
                    }
                }
            }
        }
    }
}

/** One user turn asking Nuru to summarize the recent messages and draft a reply. */
private fun buildDraftPrompt(recent: List<Pair<String, String>>): String {
    val lines = recent.takeLast(5).map { (author, text) ->
        "[$author]: ${text.replace("\n", " ").trim().take(400)}"
    }
    val convo = if (lines.isEmpty()) "(no messages yet — this reply would start the conversation)" else lines.joinToString("\n")
    return "Here are the last messages in a church group chat:\n$convo\n" +
        "Summarize the conversation in one short sentence, then draft a warm, natural reply I could send " +
        "(2-3 sentences, no emojis unless fitting). Reply in this exact format:\n" +
        "SUMMARY: …\nDRAFT: …"
}

/**
 * Defensive SUMMARY:/DRAFT: parse. If the model skipped the format the whole
 * reply becomes the draft (summary null).
 */
private fun parseSummaryDraft(reply: String): Pair<String?, String> {
    val text = reply.trim()
    if (text.isEmpty()) return null to ""
    val draftIdx = text.indexOf("DRAFT:", ignoreCase = true)
    if (draftIdx < 0) return null to text
    val draft = text.substring(draftIdx + "DRAFT:".length).trim()
    val summaryIdx = text.indexOf("SUMMARY:", ignoreCase = true)
    val summary = if (summaryIdx in 0 until draftIdx) {
        text.substring(summaryIdx + "SUMMARY:".length, draftIdx).trim().takeIf { it.isNotBlank() }
    } else {
        null
    }
    return summary to draft.ifBlank { text }
}
