// "Ask to be connected" — finding a cell (owner, 2026-10-05; pathway docs/
// EXPERIENCE.md §9.2 #12). 37 of 76 members in production have no cell, and
// "Find your cell" opened Community, which has no way to find one. The member
// says where they live and when they're free; the request goes to THEIR OWN
// pastor in their pastoral thread (POST /me/cell-connection, pathway ed1525d),
// and the pastor assigns the cell with the tools they already have. No list
// of cells or homes is ever shown to members.
//
//   - in a cell already → the cell page (the server says so: GET, or a 409);
//   - asked before (on any phone) → "Sent to your pastor on ‹day› — they'll
//     connect you · Open the conversation";
//   - otherwise the short form → "Ask the church";
//   - a minor, or a church with no pastor to receive it → the server's own
//     words (422), never a guess of ours.
//
// The typed words stay on screen when a send fails (§7.4), and one client id
// is kept across retries, so a send the server already took is never doubled.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CellConnectionBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.StateLanguage
import org.nuruplace.member.data.net.StateMessage
import org.nuruplace.member.ui.components.FailedState
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.components.StateCard
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.NuruDates
import java.time.ZoneId

/** The words — one place, both apps' (EXPERIENCE.md §9.2 #12). Pure, so
 *  CellConnectWordsTest pins them. */
object CellConnectWords {
    const val KICKER = "Your cell"
    const val TITLE = "Ask to be connected"
    const val LINE = "Tell the church where you live and when you're free — your pastor will connect you to a cell."
    const val AREA = "Where do you live?"
    const val AREA_HINT = "Your area or estate"
    const val TIMES = "When are you free?"
    const val TIMES_HINT = "Weekday evenings, Saturday mornings…"
    const val NOTE = "Anything else? (optional)"
    const val ASK = "Ask the church"
    const val OPEN = "Open the conversation"

    /** The server's own bounds: an area of 2–80 characters, a time of 2–120. */
    fun canAsk(area: String, times: String, note: String): Boolean =
        area.trim().length in 2..80 && times.trim().length in 2..120 && note.trim().length <= 300

    /** "Sent to your pastor on Mon 5 Oct — they'll connect you" (the year
     *  only when it isn't this year). */
    fun sent(requestedAtIso: String, zone: ZoneId = NAIROBI, today: java.time.LocalDate = java.time.LocalDate.now(zone)): String {
        val day = NuruDates.day(requestedAtIso, zone, today)
        return if (day != null) "Sent to your pastor on $day — they'll connect you" else "Sent to your pastor — they'll connect you"
    }

    private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")
}

private sealed interface CellAsk {
    data object Loading : CellAsk
    data class Failed(val message: StateMessage) : CellAsk
    data object Form : CellAsk
    data class Sent(val requestedAt: String, val conversationId: String) : CellAsk
    /** The server's own words: a minor, or no pastor to receive it. */
    data class Refused(val words: String) : CellAsk
}

@Composable
fun CellConnectScreen(
    onBack: () -> Unit,
    /** Already in a cell: the cell page, in place of this one. */
    onInCell: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var state by remember { mutableStateOf<CellAsk>(CellAsk.Loading) }
    var attempt by remember { mutableStateOf(0) }
    // The member's words — kept through a failed send (§7.4) and a rotation.
    var area by rememberSaveable { mutableStateOf("") }
    var times by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    // One id for this ask, kept across retries: a send the server already
    // took is a no-op the second time (§2.1).
    val clientId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        state = CellAsk.Loading
        state = runCatching { Net.client.api.cellConnection() }.fold(
            onSuccess = { s ->
                when {
                    s.inCell -> { onInCell(); CellAsk.Loading }
                    s.request != null -> CellAsk.Sent(s.request.requestedAt, s.request.conversationId)
                    else -> CellAsk.Form
                }
            },
            onFailure = { e ->
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                CellAsk.Failed(ApiException.state(e, context))
            },
        )
    }

    fun ask() {
        if (sending || !CellConnectWords.canAsk(area, times, note)) return
        sending = true
        sendError = null
        scope.launch {
            val r = runCatching {
                Net.client.api.askCellConnection(
                    CellConnectionBody(
                        area = area.trim(), availability = times.trim(),
                        note = note.trim().ifEmpty { null }, clientMutationId = clientId,
                    ),
                )
            }
            r.exceptionOrNull()?.let { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
            sending = false
            r.onSuccess { state = CellAsk.Sent(it.requestedAt, it.conversationId) }
            r.onFailure { e ->
                val err = ApiException.serverError(e)
                when {
                    // "You're already in a cell." — the cell page says the rest.
                    err?.status == 409 -> onInCell()
                    // The server's own words: a minor, or no pastor yet.
                    err?.status == 422 && !err.message.isNullOrBlank() -> state = CellAsk.Refused(err.message!!)
                    // Anything else: why, in §4's words; the words typed stay.
                    else -> sendError = if (err != null) "${ApiException.SEND_FAILED} ${StateLanguage.serverError.sentence}"
                    else ApiException.failureLine(ApiException.SEND_FAILED, e, context)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
        ScreenHeader(CellConnectWords.TITLE, kicker = CellConnectWords.KICKER, onBack = onBack)
        when (val s = state) {
            CellAsk.Loading -> Box(Modifier.fillMaxWidth().padding(Spacing.screen)) {
                org.nuruplace.member.ui.components.SkeletonBlock(height = 160.dp)
            }
            is CellAsk.Failed -> FailedState(s.message, onRetry = { attempt++ }, modifier = Modifier.padding(Spacing.screen), onBack = onBack)
            is CellAsk.Refused -> StateCard(
                title = s.words,
                glyph = Lucide.Users,
                modifier = Modifier.padding(Spacing.screen),
                actionLabel = "Go back",
                onAction = onBack,
            )
            is CellAsk.Sent -> StateCard(
                title = CellConnectWords.sent(s.requestedAt),
                line = "Your pastor has your request in your conversation together.",
                glyph = Lucide.Check,
                modifier = Modifier.padding(Spacing.screen),
                actionLabel = CellConnectWords.OPEN.takeIf { s.conversationId.isNotBlank() },
                onAction = { onOpenThread(s.conversationId) },
            )
            CellAsk.Form -> {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.base),
                ) {
                    Text(CellConnectWords.LINE, style = NuruType.body, color = Nuru.ink600)
                    AskField(CellConnectWords.AREA, area, CellConnectWords.AREA_HINT, singleLine = true) { area = it.take(80) }
                    AskField(CellConnectWords.TIMES, times, CellConnectWords.TIMES_HINT, singleLine = true) { times = it.take(120) }
                    AskField(CellConnectWords.NOTE, note, null, singleLine = false) { note = it.take(300) }
                    sendError?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
                }
                Box(Modifier.fillMaxWidth().background(Nuru.white).navigationBarsPadding().padding(Spacing.screen)) {
                    PrimaryButton(
                        CellConnectWords.ASK, onClick = { ask() },
                        enabled = CellConnectWords.canAsk(area, times, note), loading = sending,
                    )
                }
            }
        }
    }
}

@Composable
private fun AskField(label: String, value: String, hint: String?, singleLine: Boolean, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = NuruType.controlTitle, color = Nuru.navy, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            placeholder = hint?.let { { Text(it, style = NuruType.body, color = Nuru.ink400) } },
            textStyle = NuruType.bodyLg,
            shape = RoundedCornerShape(Radii.control),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Nuru.white,
                unfocusedContainerColor = Nuru.white,
                focusedBorderColor = Nuru.gold,
                unfocusedBorderColor = Nuru.border,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
    }
}
