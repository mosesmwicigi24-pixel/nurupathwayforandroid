// The member's own archive of Sunday Letters — GET /me/letters, newest first
// (iOS LetterArchiveView). The editorial letter's "Last week: …" opens it on
// that letter (owner, 2026-10-07: the editorial Sunday Letter); a letter
// opened here is the same editorial page, and its own "Last week" turns to
// the letter before it in place.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PastoralLetter
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.EmptyState
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans

/** The archive's route; `open` names a letter to open on arrival. */
const val LETTERS_ROUTE = "letters?open={open}"

/** The archive, opened on [letterId] when one is named. */
fun lettersRoute(letterId: String? = null): String =
    if (letterId.isNullOrBlank()) "letters" else "letters?open=${android.net.Uri.encode(letterId)}"

@Composable
fun LettersArchiveScreen(
    openLetterId: String?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onSelectTab: (String) -> Unit,
) {
    AsyncContent(load = { Net.client.api.letters().data }) { loaded: List<PastoralLetter>, _ ->
        var letters by remember(loaded) { mutableStateOf(loaded) }
        var open by remember(loaded) { mutableStateOf(openLetterId?.let { id -> loaded.firstOrNull { it.letterId == id } }) }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Your letters", kicker = "The Sunday Letter", onBack = onBack)
            if (letters.isEmpty()) {
                EmptyState(
                    "No letters yet",
                    modifier = Modifier.padding(Spacing.screen),
                    line = "One arrives every Sunday evening, written from your own week.",
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(letters, key = { it.letterId }) { lt -> ArchiveRow(lt) { open = lt } }
                }
            }
        }
        open?.let { lt ->
            LetterDialog(
                lt,
                archive = letters,
                onDismiss = { open = null },
                onRead = { letters = letters.map { if (it.letterId == lt.letterId) it.copy(readAt = "read") else it } },
                onNextStep = { route, moduleId ->
                    open = null
                    when (val dest = letterStepDest(route, moduleId)) {
                        is WeekDest.Screen -> onNavigate(dest.route)
                        is WeekDest.Tab -> onSelectTab(dest.route)
                        is WeekDest.Event -> onNavigate("event/${dest.occurrenceId}?end=${android.net.Uri.encode(dest.endAt.orEmpty())}")
                    }
                },
                onWriteBack = { id -> open = null; onNavigate("chat/$id?ctx=pastoral") },
                // Already in the archive: "Last week" turns to that letter here.
                onOpenLetters = { id -> open = letters.firstOrNull { it.letterId == id } },
            )
        }
    }
}

/** One letter: the gold envelope, its title, its Sunday; a gold dot while unread. */
@Composable
private fun ArchiveRow(letter: PastoralLetter, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.white).border(1.dp, Nuru.border, shape)
            .clickable(onClickLabel = "Read") { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFE8CA6C), Color(0xFFB6862F)))),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.Mail, contentDescription = null, tint = Color(0xFF1E2A1F), modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(letter.displayTitle ?: "Your Sunday Letter", style = NuruType.rowTitle, color = Nuru.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            org.nuruplace.member.util.NuruDates.day(letter.weekOf)?.let {
                Text("Week of $it", style = nuruSans(12), color = Nuru.ink600)
            }
        }
        if (letter.isUnread) Box(Modifier.size(7.dp).clip(CircleShape).background(Nuru.gold))
        Icon(Lucide.ChevronRight, contentDescription = null, tint = Nuru.ink300, modifier = Modifier.size(18.dp))
    }
}
