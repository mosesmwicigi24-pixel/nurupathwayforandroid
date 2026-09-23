// Departments — where members serve (docs/PARTNERS_PROGRAMME.md §4). The
// Departments segment of the You tab (YouScreen): every active department of
// my congregation as a card — photo, purpose, leader, how many serve, whether
// it is "a good fit for you" (my top gifts ∩ its gift_keys, decided server-
// side), open needs, my own standing, and the newest post. Tapping a card
// opens DepartmentScreen. Everything here is derived by GET /departments;
// nothing is a second copy of the truth.
package org.nuruplace.member.feature.departments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.Department
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import org.nuruplace.member.util.relTime

private val CardShape = RoundedCornerShape(16.dp)

/** The Departments segment (You tab). Pull-to-refresh via AsyncContent's
 *  brand NuruRefreshBox; the list is the scrollable it wraps. */
@Composable
fun DepartmentsSegment(onOpen: (String) -> Unit) {
    AsyncContent(
        load = { Net.client.api.departments().data },
        refreshable = true,
    ) { departments, _ ->
        if (departments.isEmpty()) {
            DepartmentsEmpty()
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(Nuru.paper),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(
                        "WHERE TO SERVE",
                        style = NuruType.sectionLabel, color = Nuru.eyebrow,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                items(departments, key = { it.departmentId }) { d ->
                    DepartmentCard(d) { onOpen(d.departmentId) }
                }
                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun DepartmentsEmpty() {
    // A scrollable so pull-to-refresh still works on the empty state.
    LazyColumn(
        Modifier.fillMaxSize().background(Nuru.paper),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.lg),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(CardShape).background(Nuru.white)
                    .border(1.dp, Nuru.border, CardShape)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Nuru.goldChipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Diversity3, contentDescription = null, tint = Nuru.goldChipText, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(Spacing.xs))
                Text("No departments yet", style = NuruType.cardTitle, color = Nuru.ink, textAlign = TextAlign.Center)
                Text(
                    "When your church sets up its teams they'll appear here — what each one does, who leads it, and where you'd be a good fit.",
                    style = NuruType.body, color = Nuru.ink600, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepartmentCard(d: Department, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(CardShape).background(Nuru.white)
            .border(1.dp, Nuru.border, CardShape)
            .clickable { onOpen() },
    ) {
        Box(Modifier.fillMaxWidth().height(128.dp)) {
            DepartmentPhoto(d.imageUrl, Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            myStatusChip(d)?.let { (label, bg, fg) ->
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { DeptChip(label, bg, fg) }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.name, style = nuruSerif(18, FontWeight.SemiBold), color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.purpose.isNotBlank()) {
                Text(d.purpose, style = NuruType.body, color = Nuru.ink600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.leaderName != null) {
                    PersonAvatar(d.leaderAvatar, d.leaderName, 22.dp)
                    Text("Led by ${d.leaderName}", style = NuruType.caption, color = Nuru.ink600, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("·", style = NuruType.caption, color = Nuru.ink300)
                }
                Text(servingCount(d.memberCount), style = NuruType.caption, color = Nuru.ink600)
            }
            val chips = buildList {
                if (d.fit) add(Triple("Good fit for you${matchedGiftsSuffix(d)}", Nuru.goldChipBg, Nuru.goldChipText) to Icons.Filled.AutoAwesome)
                if (d.openNeeds > 0) add(Triple(if (d.openNeeds == 1) "1 open need" else "${d.openNeeds} open needs", Nuru.infoBg, Nuru.info) to Icons.Filled.VolunteerActivism)
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { (c, icon) -> DeptChip(c.first, c.second, c.third, icon) }
                }
            }
            d.latestPost?.takeIf { it.isNotBlank() }?.let { post ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Nuru.surface).padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(14.dp))
                    Text(post, style = NuruType.caption, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    relTime(d.latestPostAt).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = NuruType.micro, color = Nuru.ink400)
                    }
                }
            }
        }
    }
}

// ── Shared primitives (also used by DepartmentScreen) ─────────────────────

/** The department's photo, or the gold-gradient fallback with the team mark. */
@Composable
internal fun DepartmentPhoto(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    Box(modifier.clip(shape).background(Nuru.goldGradient), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.Diversity3, contentDescription = null,
                tint = Nuru.navyDeep.copy(alpha = 0.55f), modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** A person's avatar, or their initial on a gold tint. */
@Composable
internal fun PersonAvatar(url: String?, name: String?, size: Dp) {
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
            Text(
                name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                style = nuruSans((size.value * 0.42f).toInt().coerceAtLeast(9), FontWeight.SemiBold),
                color = Nuru.goldLo,
            )
        }
    }
}

@Composable
internal fun DeptChip(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Text(text, style = nuruSans(11, FontWeight.SemiBold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** My standing on a department → chip (label, bg, fg), or null when I have none. */
internal fun myStatusChip(d: Department): Triple<String, Color, Color>? = when {
    d.isActive && (d.myRole == "leader" || d.isLeader) -> Triple("Leading", Nuru.goldChipBg, Nuru.goldChipText)
    d.isActive -> Triple("Serving", Nuru.successBg, Nuru.successText)
    d.isRequested -> Triple("Requested", Nuru.warningBg, Nuru.warning)
    else -> null
}

internal fun servingCount(n: Int): String = when (n) {
    0 -> "No one serving yet"
    1 -> "1 serving"
    else -> "$n serving"
}

/** `gift_keys` are machine keys ("teaching", "helps_service") — read them as words. */
internal fun giftLabel(key: String): String =
    key.trim().split('_', '-', ' ').filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

internal fun matchedGiftsSuffix(d: Department): String =
    d.matchedGifts.takeIf { it.isNotEmpty() }?.joinToString(", ") { giftLabel(it) }?.let { " · $it" } ?: ""
