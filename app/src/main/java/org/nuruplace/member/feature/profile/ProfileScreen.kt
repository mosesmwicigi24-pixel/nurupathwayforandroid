// Profile — the iOS "Account" tab. Identity header (avatar + level), personal
// information card, achievements, growth scores, milestones, and certificates.
// Ported 1:1 from the iOS Profile RootView. Uses the shared PROF palette + primitives
// from ProfileShared.kt (same package). The gear opens Settings (which owns sign-out);
// the old menu rows + body sign-out button are dropped to match iOS.
package org.nuruplace.member.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.data.net.Badge
import org.nuruplace.member.data.net.Certificate
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScoresSummary
import org.nuruplace.member.data.net.UserProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(me: MeResponse?, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    var scores by remember { mutableStateOf<ScoresSummary?>(null) }
    var achievements by remember { mutableStateOf<Achievements?>(null) }
    var certs by remember { mutableStateOf<List<Certificate>>(emptyList()) }
    LaunchedEffect(Unit) {
        scores = runCatching { Net.client.api.scores() }.getOrNull()
        achievements = runCatching { Net.client.api.achievements() }.getOrNull()
        certs = runCatching { Net.client.api.certificates().data }.getOrDefault(emptyList())
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var avatarUrl by remember(me) { mutableStateOf(me?.profile?.avatarUrl) }
    var sheetBadge by remember { mutableStateOf<Badge?>(null) }
    var copiedCode by remember { mutableStateOf<String?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val part = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes ->
                    MultipartBody.Part.createFormData(
                        "file",
                        "avatar.jpg",
                        bytes.toRequestBody("image/*".toMediaTypeOrNull()),
                    )
                }
            }
            if (part != null) {
                runCatching { Net.client.api.uploadAvatar(part).avatarUrl }.getOrNull()?.let { avatarUrl = it }
            }
        }
    }
    fun pickAvatar() =
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val p = me?.profile
    val fullName = p?.fullName ?: "Member"
    val email = p?.email ?: ""
    val level = me?.enrollment?.currentLevel ?: 1

    Column(
        Modifier
            .fillMaxSize()
            .background(PROF.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        ProfCreamHeaderBox {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACCOUNT", style = pInter(11, FontWeight.Bold, 1.98f), color = PROF.eyebrow)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PROF.white)
                            .border(1.dp, PROF.border, RoundedCornerShape(16.dp))
                            .clickable { onOpen("settings") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PROF.navy, modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PROF.tintBlue)
                                .border(2.dp, PROF.gold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize().clip(CircleShape),
                                )
                            } else {
                                Text(
                                    initials(fullName),
                                    style = pInter(28, FontWeight.SemiBold),
                                    color = PROF.navyMid,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(PROF.gold)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { pickAvatar() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Change photo", tint = PROF.navy, modifier = Modifier.size(11.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(fullName, style = pSerif(22, FontWeight.Medium, -0.44f), color = PROF.navy)
                        Text(email, style = pInter(13), color = PROF.ink600)
                        Row(
                            Modifier
                                .clip(Capsule)
                                .background(PROF.white)
                                .border(1.dp, PROF.gold.copy(alpha = 0.5f), Capsule)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = PROF.eyebrow, modifier = Modifier.size(11.dp))
                            Text("Level $level", style = pInter(11, FontWeight.SemiBold), color = PROF.eyebrow)
                        }
                    }
                }
            }
        }

        // ── Body ────────────────────────────────────────────────────────────
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PersonalInformationCard(p)
            AchievementsSection(achievements) { sheetBadge = it }
            GrowthScoresCard(scores, onOpen)
            MilestonesCard(me)
            CertificatesCard(
                certs = certs,
                copiedCode = copiedCode,
                onCopy = { code ->
                    clipboard.setText(AnnotatedString(code))
                    copiedCode = code
                },
                onDownload = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
        }
    }

    // ── Badge detail sheet ──────────────────────────────────────────────────
    sheetBadge?.let { b ->
        ModalBottomSheet(onDismissRequest = { sheetBadge = null }) {
            val st = badgeStyle(b.category)
            val earned = b.awardedAt != null
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(if (earned) st.tint else PROF.locked)
                        .then(
                            if (earned) Modifier.border(2.dp, st.color, CircleShape)
                            else Modifier.border(1.dp, PROF.border, CircleShape),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(st.icon, contentDescription = null, tint = if (earned) st.color else PROF.rowLabel, modifier = Modifier.size(42.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(if (earned) PROF.success.copy(alpha = 0.09f) else PROF.locked)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (earned) Icons.Filled.Check else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (earned) PROF.success else PROF.rowLabel,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                if (earned) "Earned ${certDate(b.awardedAt)}" else "Locked",
                                style = pInter(10, FontWeight.Bold),
                                color = if (earned) PROF.success else PROF.rowLabel,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(st.color.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            (b.category ?: "").replaceFirstChar { it.uppercase() },
                            style = pInter(10, FontWeight.Bold),
                            color = st.color,
                        )
                    }
                }
                Text(b.name, style = pSerif(26, FontWeight.SemiBold, -0.5f), color = PROF.navy)
                Text(b.description, style = pInter(13), color = PROF.sub, textAlign = TextAlign.Center)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PROF.gold)
                        .clickable { sheetBadge = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (earned) "Keep going" else "Got it", style = pInter(14, FontWeight.Bold), color = PROF.navy)
                }
            }
        }
    }
}

// ── Section card scaffold ───────────────────────────────────────────────────
@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PROF.white)
            .border(1.dp, PROF.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PROF.kicker, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = pInter(11, FontWeight.Bold, 1.4f), color = PROF.kicker)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PROF.border))
}

// ── Personal information ────────────────────────────────────────────────────
@Composable
private fun PersonalInformationCard(p: UserProfile?) {
    val userId = p?.userId ?: ""
    val memberId = "NRU-" + userId.filter { it.isLetterOrDigit() }.takeLast(8).uppercase() + "-2026"
    SectionCard {
        SectionTitle(Icons.Filled.Person, "PERSONAL INFORMATION")

        // Member ID row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(PROF.gold.copy(alpha = 0.08f), PROF.surface)))
                .border(1.dp, PROF.gold.copy(alpha = 0.23f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.white)
                    .border(1.dp, PROF.gold.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FingerprintIcon, contentDescription = null, tint = PROF.kicker, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MEMBER ID", style = pInter(10, FontWeight.SemiBold, 1.2f), color = PROF.rowLabel)
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(10.dp))
                }
                Text(memberId, style = pSerif(13, FontWeight.SemiBold), color = PROF.navy)
            }
            Text("PERMANENT", style = pInter(9, FontWeight.SemiBold, 0.9f), color = PROF.rowLabel)
        }

        InfoRow(Icons.Filled.MailOutline, "EMAIL", p?.email ?: "—", editable = false)
        HairlineDivider()
        InfoRow(Icons.Filled.Person, "FULL NAME", p?.fullName ?: "—", editable = true)
        InfoRow(Icons.Filled.Call, "PHONE", p?.phoneNumber ?: "—", editable = true)
        InfoRow(Icons.Filled.CalendarToday, "DATE OF BIRTH", p?.dateOfBirth ?: "Not set", editable = true)
        InfoRow(Icons.Filled.Group, "GENDER", p?.gender ?: "—", editable = true)
        InfoRow(
            Icons.Filled.Public,
            "COUNTRY",
            (flagEmoji(p?.countryCode) + " " + countryName(p?.countryCode)).trim(),
            editable = true,
        )
        InfoRow(Icons.Filled.LocationOn, "CITY", p?.city ?: "—", editable = true)
        HairlineDivider()
        LanguagesRow()
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, editable: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.surface)
                    .border(1.dp, PROF.border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = pInter(10, FontWeight.SemiBold, 1.2f), color = PROF.rowLabel)
                Text(value, style = pInter(13, FontWeight.Medium), color = PROF.navy)
            }
            if (editable) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun LanguagesRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PROF.surface)
                .border(1.dp, PROF.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Translate, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("LANGUAGES SPOKEN", style = pInter(10, FontWeight.SemiBold, 1.2f), color = PROF.rowLabel)
            Box(Modifier.padding(top = 2.dp)) {
                Box(
                    Modifier
                        .clip(Capsule)
                        .background(PROF.gold.copy(alpha = 0.12f))
                        .border(1.dp, PROF.gold.copy(alpha = 0.4f), Capsule)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("English", style = pInter(12, FontWeight.Medium), color = PROF.verify)
                        Icon(Icons.Filled.Check, contentDescription = null, tint = PROF.verify, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

// ── Achievements ────────────────────────────────────────────────────────────
@Composable
private fun AchievementsSection(achievements: Achievements?, onBadge: (Badge) -> Unit) {
    SectionCard {
        SectionTitle(Icons.Filled.AutoAwesome, "ACHIEVEMENTS") {
            Text("See all", style = pInter(11, FontWeight.SemiBold), color = PROF.navy)
        }
        val badges = achievements?.badges.orEmpty()
        if (badges.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(PROF.locked)
                        .border(1.dp, PROF.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Verified, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                badges.forEach { b ->
                    val st = badgeStyle(b.category)
                    val earned = b.awardedAt != null
                    Column(
                        Modifier.width(66.dp).clickable { onBadge(b) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (earned) st.tint else PROF.locked)
                                .then(
                                    if (earned) Modifier.border(1.5.dp, st.color, CircleShape)
                                    else Modifier.border(1.dp, PROF.border, CircleShape),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(st.icon, contentDescription = null, tint = if (earned) st.color else PROF.rowLabel, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            b.name,
                            style = pInter(9, if (earned) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (earned) PROF.navy else PROF.rowLabel,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Badges celebrate your growth — not competition.",
            style = pInter(11).copy(fontStyle = FontStyle.Italic),
            color = PROF.rowLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

// ── Growth scores ───────────────────────────────────────────────────────────
@Composable
private fun GrowthScoresCard(scores: ScoresSummary?, onOpen: (String) -> Unit) {
    SectionCard {
        SectionTitle(Icons.Filled.TrendingUp, "GROWTH SCORES")

        Row(
            Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PROF.goldTint)
                    .border(1.5.dp, PROF.gold.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (scores?.overall?.score ?: 0).toString(),
                    style = pSerif(16, FontWeight.SemiBold),
                    color = PROF.navy,
                    textAlign = TextAlign.Center,
                )
            }
            Column {
                Text("Overall", style = pInter(13, FontWeight.SemiBold), color = PROF.navy)
                Text(
                    scores?.overall?.band?.replaceFirstChar { it.uppercase() } ?: "—",
                    style = pInter(11),
                    color = PROF.verify,
                )
            }
        }
        HairlineDivider()
        ScoreRow("Word", scores?.word?.score ?: 0, "word", Icons.Filled.MenuBook, onOpen)
        HairlineDivider()
        ScoreRow("Prayer", scores?.prayer?.score ?: 0, "prayer", Icons.Filled.FavoriteBorder, onOpen)
        HairlineDivider()
        ScoreRow("Habits", scores?.habits?.score ?: 0, "habits", Icons.Filled.LocalFireDepartment, onOpen)
        HairlineDivider()
        ScoreRow("Curriculum", scores?.curriculum?.score ?: 0, "curriculum", Icons.Filled.School, onOpen)
        HairlineDivider()
        ScoreRow("Attendance", scores?.attendance?.score ?: 0, "attendance", Icons.Filled.Group, onOpen)

        Text(
            "Tap a score to see why — scores are formative, never a leaderboard.",
            style = pInter(11).copy(fontStyle = FontStyle.Italic),
            color = PROF.rowLabel,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ScoreRow(name: String, value: Int, pillar: String, icon: ImageVector, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen("score/$pillar") }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PROF.surface)
                .border(1.dp, PROF.border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = pInter(13, FontWeight.SemiBold), color = PROF.navy)
                Spacer(Modifier.weight(1f))
                Text(value.toString(), style = pInter(13, FontWeight.Bold), color = PROF.kicker)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(5.dp)
                    .clip(Capsule)
                    .background(PROF.surface),
            ) {
                if (value > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = value.coerceIn(0, 100) / 100f)
                            .height(5.dp)
                            .clip(Capsule)
                            .background(PROF.gold),
                    )
                }
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(16.dp))
    }
}

// ── Milestones ──────────────────────────────────────────────────────────────
private enum class MilestoneState { DONE, ACTIVE, FUTURE }
private data class MilestoneItem(val title: String, val subtitle: String, val state: MilestoneState)

@Composable
private fun MilestonesCard(me: MeResponse?) {
    val isBaptized = me?.profile?.isBaptized == true
    val level = me?.enrollment?.currentLevel ?: 1
    val items = listOf(
        MilestoneItem(
            "Baptism",
            if (isBaptized) "Recorded — welcome to the family" else "Not yet recorded",
            if (isBaptized) MilestoneState.DONE else MilestoneState.FUTURE,
        ),
        MilestoneItem("Level $level · in progress", "Keep going", MilestoneState.ACTIVE),
        MilestoneItem("Pathway completion", "Your journey continues", MilestoneState.FUTURE),
    )
    SectionCard {
        SectionTitle(Icons.Filled.TrackChanges, "MILESTONES")
        Column(Modifier.padding(top = 8.dp)) {
            items.forEachIndexed { index, item ->
                val last = index == items.lastIndex
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .then(
                                    when (item.state) {
                                        MilestoneState.DONE -> Modifier.background(PROF.gold)
                                        MilestoneState.ACTIVE -> Modifier.background(Color.White).border(2.dp, PROF.gold, CircleShape)
                                        MilestoneState.FUTURE -> Modifier.background(PROF.locked).border(1.dp, PROF.border, CircleShape)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                when (item.state) {
                                    MilestoneState.DONE -> Icons.Filled.Check
                                    MilestoneState.ACTIVE -> Icons.Filled.CalendarToday
                                    MilestoneState.FUTURE -> Icons.Filled.FavoriteBorder
                                },
                                contentDescription = null,
                                tint = when (item.state) {
                                    MilestoneState.DONE -> Color.White
                                    MilestoneState.ACTIVE -> PROF.gold
                                    MilestoneState.FUTURE -> PROF.rowLabel
                                },
                                modifier = Modifier.size(if (item.state == MilestoneState.DONE) 14.dp else 13.dp),
                            )
                        }
                        if (!last) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(if (item.state == MilestoneState.DONE) PROF.gold else PROF.navy.copy(alpha = 0.12f)),
                            )
                        }
                    }
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(
                            item.title,
                            style = pInter(13, FontWeight.SemiBold),
                            color = if (item.state == MilestoneState.FUTURE) PROF.rowLabel else PROF.navy,
                        )
                        Text(item.subtitle, style = pInter(11), color = PROF.sub)
                    }
                }
            }
        }
    }
}

// ── Certificates ────────────────────────────────────────────────────────────
@Composable
private fun CertificatesCard(
    certs: List<Certificate>,
    copiedCode: String?,
    onCopy: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    SectionCard {
        SectionTitle(Icons.Filled.Verified, "CERTIFICATES")
        if (certs.isEmpty()) {
            Text(
                "Your certificates will appear here as you complete each Pathway level.",
                style = pInter(11).copy(fontStyle = FontStyle.Italic),
                color = PROF.rowLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            return@SectionCard
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            certs.forEach { c ->
                CertificateCard(c, copied = copiedCode == c.verificationCode, onCopy = onCopy, onDownload = onDownload)
            }
        }
        Text(
            "The name on a certificate is fixed at issuance and won't change if you edit your profile.",
            style = pInter(11).copy(fontStyle = FontStyle.Italic),
            color = PROF.rowLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun CertificateCard(
    c: Certificate,
    copied: Boolean,
    onCopy: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    val levelLabel = (c.levelNumber?.toString() ?: "")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PROF.surface)
            .border(1.dp, PROF.border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.certSeal)
                    .border(1.dp, PROF.gold.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = PROF.gold, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Pathway Level $levelLabel".trim(), style = pInter(14, FontWeight.SemiBold, -0.14f), color = PROF.navy)
                Text("Level $levelLabel · Issued ${certDate(c.issuedAt)}".trim(), style = pInter(11), color = PROF.sub)
            }
        }

        // Hash pill
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PROF.white)
                .border(1.dp, PROF.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(FingerprintIcon, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(13.dp))
            Text(
                c.verificationCode,
                style = pInter(12, FontWeight.SemiBold, 0.5f).copy(fontFamily = FontFamily.Monospace),
                color = PROF.navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.clickable { onCopy(c.verificationCode) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) PROF.success else PROF.gold,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (copied) "Copied" else "Copy",
                    style = pInter(10, FontWeight.Bold),
                    color = if (copied) PROF.success else PROF.gold,
                )
            }
        }

        // Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.gold.copy(alpha = 0.10f))
                    .border(1.dp, PROF.gold.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = PROF.verify, modifier = Modifier.size(13.dp))
                Text("Signed · Verify", style = pInter(10, FontWeight.Bold), color = PROF.verify)
                Spacer(Modifier.weight(1f))
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.gold)
                    .clickable { onDownload(c.downloadUrl) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Download, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(13.dp))
                Text("Download PDF", style = pInter(10, FontWeight.Bold), color = PROF.navy)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────
private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "N"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun countryName(code: String?): String = when (code?.trim()?.uppercase()) {
    null, "" -> ""
    "KE" -> "Kenya"
    "US" -> "United States"
    "GB" -> "United Kingdom"
    "NG" -> "Nigeria"
    "ZA" -> "South Africa"
    "GH" -> "Ghana"
    "UG" -> "Uganda"
    "TZ" -> "Tanzania"
    "CA" -> "Canada"
    "AU" -> "Australia"
    "IN" -> "India"
    else -> code.trim().uppercase()
}

private val CERT_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private fun certDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate().format(CERT_DATE_FMT)
    }.recoverCatching {
        // Fall back to date-only ISO (yyyy-MM-dd)
        java.time.LocalDate.parse(iso.take(10)).format(CERT_DATE_FMT)
    }.getOrDefault(iso)
}
