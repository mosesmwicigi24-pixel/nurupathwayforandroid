// Profile — the iOS "Account" tab. Identity header (avatar + level), personal
// information card, achievements, growth scores, milestones, and certificates.
// Ported 1:1 from the iOS Profile RootView. Uses the shared PROF palette + primitives
// from ProfileShared.kt (same package). Settings (which owns sign-out) is the You
// tab's own segment — its gear is the tab's one (EXPERIENCE.md §6.2); the old menu
// rows + body sign-out button are dropped to match iOS.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import org.nuruplace.member.ui.components.NuruModalBottomSheet
import org.nuruplace.member.ui.components.largeText
import org.nuruplace.member.ui.components.WholeWordsText
import org.nuruplace.member.ui.components.CappedFontScale
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.ui.components.rememberHeld
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Badge
import org.nuruplace.member.data.net.Certificate
import org.nuruplace.member.data.net.Department
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScoresSummary
import org.nuruplace.member.data.net.UserProfile
import org.nuruplace.member.feature.give.kenyanMobileDisplay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import org.nuruplace.member.ui.icons.Lucide

private val Capsule = RoundedCornerShape(999.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(me: MeResponse?, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    // Held by the destination (rememberHeld, EXPERIENCE.md §7.2 #8): Back
    // from a score, Settings or a certificate finds the same page, refreshed
    // in place — never back to zeros. A part that fails keeps what is shown.
    var scores by rememberHeld("Profile.scores") { mutableStateOf<ScoresSummary?>(null) }
    var achievements by rememberHeld("Profile.achievements") { mutableStateOf<Achievements?>(null) }
    var badgeGallery by rememberHeld("Profile.badgeGallery") { mutableStateOf<List<Badge>>(emptyList()) }
    var certs by rememberHeld("Profile.certs") { mutableStateOf<List<Certificate>>(emptyList()) }
    // Departments I actively serve in (GET /me/departments, spec §4) — the
    // "Serving in" card below; requests-in-waiting live on the Departments
    // segment, not here.
    var serving by rememberHeld("Profile.serving") { mutableStateOf<List<Department>>(emptyList()) }
    // The member's journey (§3), derived as Home and Map view derive it (the
    // summary, then the current level's trail): the milestones speak its words.
    var journey by rememberHeld("Profile.journey") { mutableStateOf<org.nuruplace.member.feature.pathway.Journey?>(null) }
    LaunchedEffect(Unit) {
        journey = runCatching {
            val summary = Net.client.api.pathway()
            val trail = org.nuruplace.member.feature.pathway.JourneyState.derive(summary)?.levelNumber
                ?.let { n -> runCatching { Net.client.api.levelModules(n).data }.getOrNull() }
            org.nuruplace.member.feature.pathway.JourneyState.derive(summary, trail)
        }.getOrElse { journey }
        scores = runCatching { Net.client.api.scores() }.getOrElse { scores }
        achievements = runCatching { Net.client.api.achievements() }.getOrElse { achievements }
        certs = runCatching { Net.client.api.certificates().data }.getOrElse { certs }
        serving = runCatching { Net.client.api.myDepartments().data.filter { it.isActive } }.getOrElse { serving }
        // GET /badges catalogue merged with earned awards (iOS ProfileView.loadExtras):
        // earned first (with awarded_at), then locked — so the rail shows what's
        // still ahead, not just trophies already won.
        val catalogue = runCatching { Net.client.api.badgesCatalogue().data }.getOrDefault(emptyList())
        if (catalogue.isNotEmpty()) {
            val earnedByCode = achievements?.badges.orEmpty().associateBy { it.code }
            badgeGallery = catalogue
                .map { c -> earnedByCode[c.code] ?: c }
                .sortedByDescending { it.awardedAt != null }
        }
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var avatarUrl by remember(me) { mutableStateOf(me?.profile?.avatarUrl) }
    var sheetBadge by remember { mutableStateOf<Badge?>(null) }
    var copiedCode by remember { mutableStateOf<String?>(null) }
    // Locally editable profile — refreshed from the PATCH /me response after each save.
    var profile by remember(me) { mutableStateOf(me?.profile) }
    var editing by remember { mutableStateOf<EditField?>(null) }

    // A new photo: made upright and small (≤ 512 px, as iOS) so it fits the
    // server's 5 MB cap, then sent; a photo that didn't save says so — it
    // failed in silence, and a full-size camera picture always did.
    var avatarUploading by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !avatarUploading) scope.launch {
            avatarUploading = true
            val part = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.let { org.nuruplace.member.util.PhotoShrink.jpeg(it, maxDim = 512) }
                    ?.let { jpeg ->
                        MultipartBody.Part.createFormData("file", "avatar.jpg", jpeg.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                    }
            }
            if (part == null) {
                org.nuruplace.member.ui.components.QuickNotice.show("Couldn't change your photo. That file isn't a picture we can use.")
            } else {
                org.nuruplace.member.ui.components.noticeOnFailure(context, lead = "Couldn't change your photo.") {
                    Net.client.api.uploadAvatar(part).avatarUrl
                }?.let { avatarUrl = it }
            }
            avatarUploading = false
        }
    }
    fun pickAvatar() =
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val p = profile ?: me?.profile
    val fullName = p?.fullName ?: "Member"
    val email = p?.email ?: ""
    // Nullable on purpose. This was `?: 1`, so a member with no enrollment was
    // shown "Level 1" — false for twenty-eight real members for up to 42 days
    // while they waited to be placed (backend #420 / migration 193). A missing
    // standing must look missing.
    val level = me?.enrollment?.currentLevel

    Column(
        Modifier
            .fillMaxSize()
            .background(PROF.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        ProfCreamHeaderBox {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                // One settings gear on the You tab (EXPERIENCE.md §6.2): the
                // segment bar's. Profile's own gear, a second door to the
                // same Settings, is gone.
                Text("ACCOUNT", style = NuruType.kicker, color = PROF.eyebrow)
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
                                // The palette's gold-chip tint, navy initials —
                                // it was a pale blue with blue letters, a second
                                // blue (§8.1 rule 1; final walk C16).
                                .background(Nuru.goldChipBg)
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
                                // Letters inside a fixed circle keep the everyday
                                // size (§9.6 #4, as iOS): at the largest text
                                // "AT" showed only "A". The name beside it grows.
                                CappedFontScale(1f) {
                                    Text(
                                        initials(fullName),
                                        style = pInter(28, FontWeight.SemiBold),
                                        color = PROF.navy,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1, softWrap = false,
                                    )
                                }
                            }
                            // The new photo on its way.
                            if (avatarUploading) {
                                Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                }
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
                            Icon(Lucide.Pencil, contentDescription = "Change photo", tint = PROF.navy, modifier = Modifier.size(14.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(fullName, style = pSerif(22, FontWeight.Medium, -0.44f), color = PROF.navy)
                        // An address breaks only after its "@" (final walk C3,
                        // Android #10: "student1@dev / .local" at the largest).
                        Text(emailBreaks(email), style = pInter(13), color = PROF.ink600)
                        if (level != null) {
                            Row(
                                Modifier
                                    .clip(Capsule)
                                    .background(PROF.white)
                                    .border(1.dp, PROF.gold.copy(alpha = 0.5f), Capsule)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Lucide.Award, contentDescription = null, tint = PROF.eyebrow, modifier = Modifier.size(14.dp))
                                Text("Level $level", style = pInter(11, FontWeight.SemiBold), color = PROF.eyebrow)
                            }
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
            PersonalInformationCard(p, onEdit = { editing = it })
            // Discipler console entry — staff only. The server enforces the role
            // (requireRole Instructor on /disciples); this just hides the door.
            if (p?.role in setOf("Instructor", "Admin", "SuperAdmin")) {
                DisciplesEntryCard { onOpen("disciples") }
            }
            if (serving.isNotEmpty()) ServingInCard(serving) { onOpen("department/$it") }
            AchievementsSection(achievements, badgeGallery) { sheetBadge = it }
            GrowthScoresCard(scores, onOpen)
            AiConsentCard()
            MilestonesCard(me, journey)
            CertificatesCard(
                certs = certs,
                copiedCode = copiedCode,
                onCopy = { code ->
                    clipboard.setText(AnnotatedString(code))
                    copiedCode = code
                },
                onDownload = { cert ->
                    // download_url is a RELATIVE, auth-required media path — a browser
                    // ACTION_VIEW 401s. Fetch through the authed client, stash in
                    // cache/shared, and hand a content:// URI to a PDF viewer.
                    scope.launch {
                        runCatching {
                            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val body = Net.client.api.certificatePdf(cert.verificationCode)
                                val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                                val f = java.io.File(dir, "nuru-certificate-${cert.verificationCode}.pdf")
                                body.byteStream().use { input -> f.outputStream().use { input.copyTo(it) } }
                                f
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", bytes,
                            )
                            val view = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(
                                Intent.createChooser(view, "Open certificate").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
            )
            // The member's ID, at the page's foot as an action (as iOS): it
            // still copies whole, for the office or support, but is never shown
            // as a fact to read (§8.1 rule 8 — it was a raw 36-character row).
            p?.userId?.takeIf { it.isNotBlank() }?.let { uid ->
                MemberIdFoot(onCopy = { clipboard.setText(AnnotatedString(uid)) })
            }
        }
    }

    // ── Badge detail sheet ──────────────────────────────────────────────────
    sheetBadge?.let { b ->
        NuruModalBottomSheet(onDismissRequest = { sheetBadge = null }) {
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
                                if (earned) Lucide.Check else Lucide.Lock,
                                contentDescription = null,
                                tint = if (earned) PROF.success else PROF.rowLabel,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                if (earned) "Earned ${certDate(b.awardedAt)}" else "Locked",
                                style = pInter(11, FontWeight.Bold),
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
                            style = pInter(11, FontWeight.Bold),
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

    // ── Personal-info edit sheet ────────────────────────────────────────────
    editing?.let { field ->
        EditFieldSheet(
            field = field,
            profile = p,
            onDismiss = { editing = null },
            onSaved = { res ->
                profile = res.profile
                editing = null
            },
        )
    }
}

// ── Personal-info edit fields ───────────────────────────────────────────────
private enum class EditField(
    val title: String,
    val wireKey: String,
    val helper: String? = null,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    // Email joined the editable set on 2026-08-15 (owner ruling: user_id is the
    // assigned identifier, everything else may change). No capitalisation and an
    // email keyboard — an address the phone has helpfully capitalised is a login
    // that silently fails. The server trims, lowercases, refuses one another
    // live account holds, and records the change.
    EMAIL("Email", "email", keyboardType = KeyboardType.Email),
    NAME("Full name", "full_name", capitalization = KeyboardCapitalization.Words),
    PHONE("Phone", "phone_number", keyboardType = KeyboardType.Phone),
    DOB("Date of birth", "date_of_birth", helper = "YYYY-MM-DD"),
    GENDER("Gender", "gender"),
    COUNTRY("Country", "country_code", helper = "2-letter code, e.g. KE", capitalization = KeyboardCapitalization.Characters),
    CITY("City", "city", capitalization = KeyboardCapitalization.Words),
}

private val DOB_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
private val EMAIL_SHAPE = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFieldSheet(
    field: EditField,
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSaved: (MeResponse) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember(field) {
        mutableStateOf(
            when (field) {
                EditField.EMAIL -> profile?.email ?: ""
                EditField.NAME -> profile?.fullName ?: ""
                EditField.PHONE -> profile?.phoneNumber ?: ""
                // "1990-01-01" — the form the field asks for (the server
                // sends midnight UTC, which never matched it).
                EditField.DOB -> profileDateEditValue(profile?.dateOfBirth)
                EditField.GENDER -> profile?.gender ?: ""
                EditField.COUNTRY -> profile?.countryCode ?: ""
                EditField.CITY -> profile?.city ?: ""
            },
        )
    }
    var busy by remember(field) { mutableStateOf(false) }
    var error by remember(field) { mutableStateOf<String?>(null) }

    // The value that goes on the wire — trimmed; country code uppercased.
    val wireValue = when (field) {
        EditField.COUNTRY -> value.trim().uppercase()
        // Lowercased here as well as server-side, so the member sees the value
        // that will actually be saved rather than being silently corrected.
        EditField.EMAIL -> value.trim().lowercase()
        else -> value.trim()
    }
    val valid = when (field) {
        EditField.NAME, EditField.PHONE, EditField.CITY -> wireValue.isNotBlank()
        // Deliberately loose — just enough to catch a missing @ before a round
        // trip. The server is the authority on what a valid address is, and on
        // whether it is already taken (409).
        EditField.EMAIL -> EMAIL_SHAPE.matches(wireValue)
        EditField.DOB -> DOB_REGEX.matches(wireValue)
        EditField.COUNTRY -> wireValue.length == 2 && wireValue.all { it.isLetter() }
        EditField.GENDER -> GENDER_OPTIONS.any { it.second == wireValue }
    }

    fun save() {
        if (!valid || busy) return
        scope.launch {
            busy = true
            try {
                val body = buildJsonObject {
                    put(field.wireKey, JsonPrimitive(wireValue))
                    put("row_version", JsonPrimitive(profile?.rowVersion ?: 0))
                }
                // PATCH returns only {user_id, row_version}; refetch /me for the
                // authoritative profile (and the fresh row_version for future edits).
                Net.client.api.updateMe(body)
                onSaved(Net.client.api.me())
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    // Open to its content, never half-way (the keyboard used to drop it to
    // the half state), with a visible way out (Cycle 3's closing walk: "Save"
    // was the only control; the other sheets have ✕).
    NuruModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = PROF.white,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.title, style = pSerif(18, FontWeight.SemiBold), color = PROF.navy, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(PROF.surface).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = "Close", tint = PROF.navy, modifier = Modifier.size(18.dp))
                }
            }
            field.helper?.let { helper ->
                Text(helper, style = pInter(11), color = PROF.sub)
            }

            if (field == EditField.GENDER) {
                GENDER_OPTIONS.forEach { (label, wire) ->
                    val selected = value == wire
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) PROF.gold.copy(alpha = 0.12f) else PROF.surface)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) PROF.gold else PROF.border,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { value = wire }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            label,
                            style = pInter(13, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                            color = PROF.navy,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Lucide.Check, contentDescription = null, tint = PROF.gold, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PROF.surface)
                        .border(1.dp, PROF.border, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        textStyle = pInter(14, FontWeight.Medium).copy(color = PROF.navy),
                        cursorBrush = SolidColor(PROF.gold),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = field.keyboardType,
                            capitalization = field.capitalization,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            error?.let { msg ->
                Text(msg, style = pInter(11, FontWeight.Medium), color = PROF.danger)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (valid) PROF.gold else PROF.gold.copy(alpha = 0.4f))
                    .clickable(enabled = valid && !busy) { save() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PROF.navy, strokeWidth = 2.dp)
                } else {
                    Text("Save", style = pInter(14, FontWeight.Bold), color = PROF.navy)
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
        Icon(icon, contentDescription = null, tint = PROF.kicker, modifier = Modifier.size(14.dp))
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
private fun PersonalInformationCard(p: UserProfile?, onEdit: (EditField) -> Unit) {
    SectionCard {
        SectionTitle(Lucide.User, "PERSONAL INFORMATION")

        // No raw Member ID (§8.1 rule 8; Cycle 3's closing walk): the row
        // showed the 36-character user_id — data, not a word for a member.

        // Words, never data (§8.1 rule 8, §8.2 #8): an empty value reads
        // "Not set" (it read "—"), a birthday "1 Jan 1990" — the calendar date
        // sent, never shifted by the phone's zone (it read the raw
        // "1989-12-31T21:00:00.000Z") — and a gender its own words.
        // The address breaks only after its "@" (final walk C3, Android #10:
        // "student1@dev / .local" in this row at the largest text).
        InfoRow(Lucide.Mail, "EMAIL", emailBreaks(profileValue(p?.email)), onEdit = { onEdit(EditField.EMAIL) })
        HairlineDivider()
        InfoRow(Lucide.User, "FULL NAME", profileValue(p?.fullName), onEdit = { onEdit(EditField.NAME) })
        // Read the Kenyan way, as Give shows it ("0700 000 000", not "+254700000000");
        // the edit sheet still starts from the stored number.
        InfoRow(
            Lucide.Phone, "PHONE",
            p?.phoneNumber?.takeIf { it.isNotBlank() }?.let(::kenyanMobileDisplay) ?: NOT_SET,
            onEdit = { onEdit(EditField.PHONE) },
        )
        InfoRow(Lucide.Calendar, "DATE OF BIRTH", profileDateLabel(p?.dateOfBirth), onEdit = { onEdit(EditField.DOB) })
        InfoRow(Lucide.Users, "GENDER", profileGenderLabel(p?.gender), onEdit = { onEdit(EditField.GENDER) })
        InfoRow(
            Lucide.Globe,
            "COUNTRY",
            (flagEmoji(p?.countryCode) + " " + countryName(p?.countryCode)).trim().ifEmpty { NOT_SET },
            onEdit = { onEdit(EditField.COUNTRY) },
        )
        InfoRow(Lucide.MapPin, "CITY", profileValue(p?.city), onEdit = { onEdit(EditField.CITY) })
        HairlineDivider()
        LanguagesRow()
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onEdit: (() -> Unit)? = null) {
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
                    .background(PROF.goldTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = pInter(11, FontWeight.SemiBold, 1.2f), color = PROF.rowLabel)
                // A profile field is a control row (§8.1 rule 3): Inter 14 medium.
                Text(value, style = NuruType.controlTitle, color = PROF.navy)
            }
            if (onEdit != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Pencil, contentDescription = "Edit $label", tint = PROF.rowLabel, modifier = Modifier.size(14.dp))
                }
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
                .background(PROF.goldTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Languages, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("LANGUAGES SPOKEN", style = pInter(11, FontWeight.SemiBold, 1.2f), color = PROF.rowLabel)
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
                        Icon(Lucide.Check, contentDescription = null, tint = PROF.verify, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── Discipler console entry (staff only) ────────────────────────────────────
// ── Serving in ──────────────────────────────────────────────────────────────
/** The departments this member actively serves in (spec §4: an approved
 *  request "shows on the department and on the member's profile"). One row
 *  each → the department page. Hidden entirely when there are none. */
@Composable
private fun ServingInCard(serving: List<Department>, onOpen: (String) -> Unit) {
    SectionCard {
        SectionTitle(Lucide.Users2, "SERVING IN")
        Column(Modifier.padding(top = 4.dp)) {
            serving.forEachIndexed { index, d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(d.departmentId) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(PROF.goldChipBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!d.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = d.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Icon(Lucide.Users2, contentDescription = null, tint = PROF.kicker, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = pInter(14, FontWeight.SemiBold), color = PROF.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (d.myRole == "leader") "Leader" else "Serving",
                            style = pInter(11), color = PROF.sub,
                        )
                    }
                    Icon(Lucide.ChevronRight, null, tint = PROF.rowLabel, modifier = Modifier.size(18.dp))
                }
                if (index != serving.lastIndex) HairlineDivider()
            }
        }
    }
}

@Composable
private fun DisciplesEntryCard(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PROF.navy)
            .clickable { onOpen() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PROF.gold),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.Users, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text("SHEPHERD THE FLOCK", style = pInter(11, FontWeight.Bold, 1.4f), color = PROF.gold)
            Text("Your disciples", style = pInter(14, FontWeight.SemiBold), color = Color.White)
            Text("Roster, journeys & pending reflections", style = pInter(11), color = Color.White.copy(alpha = 0.7f))
        }
        Icon(Lucide.ChevronRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}

// ── Achievements ────────────────────────────────────────────────────────────
@Composable
private fun AchievementsSection(achievements: Achievements?, gallery: List<Badge>, onBadge: (Badge) -> Unit) {
    SectionCard {
        SectionTitle(Lucide.Sparkles, "ACHIEVEMENTS") {
            // A text action is gold (§8.1 rule 4).
            Text("See all", style = pInter(11, FontWeight.SemiBold), color = PROF.gold)
        }
        // Catalogue merge (GET /badges): earned first, locked after — the rail
        // shows what's ahead. Falls back to earned-only while the catalogue loads.
        val badges = gallery.ifEmpty { achievements?.badges.orEmpty() }
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
                    Icon(Lucide.BadgeCheck, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(22.dp))
                }
            }
        } else androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            val large = largeText()
            // A deliberate peek (§8.1 rule 9; final walk C16: the fifth badge
            // was cut wherever the row happened to end): four and a half tiles
            // at the everyday sizes, two and a half past them — the half says
            // the rail goes on.
            val tile = badgeTileWidth(maxWidth, count = badges.size, large = large)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(BADGE_GAP),
            ) {
                badges.forEach { b ->
                    val st = badgeStyle(b.category)
                    val earned = b.awardedAt != null
                    Column(
                        Modifier.width(tile).clickable { onBadge(b) },
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
                            Icon(st.icon, contentDescription = null, tint = if (earned) st.color else PROF.rowLabel, modifier = Modifier.size(22.dp))
                        }
                        // Whole words, never cut (final walk C3, Android #10:
                        // "Thirt / y-Da", "Sever / y Fait"): the name never
                        // breaks at its hyphen, steps its size down only until
                        // its longest word fits, and the tile widens at the
                        // largest text.
                        WholeWordsText(
                            badgeLabel(b.name),
                            style = pInter(11, if (earned) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (earned) PROF.navy else PROF.rowLabel,
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
        SectionTitle(Lucide.TrendingUp, "GROWTH SCORES")

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
                // Unknown until the scores answer: "—", never a "0" (§7 rule 5).
                // A figure in a fixed ring keeps the everyday size (§9.6 #4).
                org.nuruplace.member.ui.components.CappedFontScale(1f) {
                    Text(
                        scores?.overall?.score?.toString() ?: "—",
                        style = pSerif(16, FontWeight.SemiBold),
                        color = PROF.navy,
                        textAlign = TextAlign.Center,
                    )
                }
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
        ScoreRow("Word", scores?.word?.score, "word", Lucide.BookOpen, onOpen)
        HairlineDivider()
        ScoreRow("Prayer", scores?.prayer?.score, "prayer", Lucide.Heart, onOpen)
        HairlineDivider()
        ScoreRow("Habits", scores?.habits?.score, "habits", Lucide.Flame, onOpen)
        HairlineDivider()
        ScoreRow("Curriculum", scores?.curriculum?.score, "curriculum", Lucide.GraduationCap, onOpen)
        HairlineDivider()
        ScoreRow("Attendance", scores?.attendance?.score, "attendance", Lucide.Users, onOpen)

        Text(
            "Tap a score to see why — scores are formative, never a leaderboard.",
            style = pInter(11).copy(fontStyle = FontStyle.Italic),
            color = PROF.rowLabel,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ScoreRow(name: String, value: Int?, pillar: String, icon: ImageVector, onOpen: (String) -> Unit) {
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
                .background(PROF.goldTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = pInter(13, FontWeight.SemiBold), color = PROF.navy)
                Spacer(Modifier.weight(1f))
                Text(value?.toString() ?: "—", style = pInter(13, FontWeight.Bold), color = PROF.kicker)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(5.dp)
                    .clip(Capsule)
                    .background(PROF.surface),
            ) {
                if (value != null && value > 0) {
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
        Icon(Lucide.ChevronRight, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(18.dp))
    }
}

// ── Milestones ──────────────────────────────────────────────────────────────
/** The member's own level on Profile's milestones, in the journey's words (§3)
 *  — the pill Home and Pathway show, over the next step: ("Level 1 · Exam
 *  ready", "Take the Level 1 exam"). Null until the journey is known, or when
 *  it is about another level: the row waits rather than tell a second story
 *  (iOS ProfileMilestoneWords, 3137194). */
internal fun profileMilestoneWords(level: Int, journey: org.nuruplace.member.feature.pathway.Journey?): Pair<String, String>? =
    journey?.takeIf { it.levelNumber == level }?.let { "Level $level · ${it.pill}" to it.next.title }
private enum class MilestoneState { DONE, ACTIVE, FUTURE }
private data class MilestoneItem(val title: String, val subtitle: String, val state: MilestoneState)

@Composable
private fun MilestonesCard(me: MeResponse?, journey: org.nuruplace.member.feature.pathway.Journey?) {
    val isBaptized = me?.profile?.isBaptized == true
    val level = me?.enrollment?.currentLevel
    val items = listOf(
        MilestoneItem(
            "Baptism",
            if (isBaptized) "Recorded — welcome to the family" else "Not yet recorded",
            if (isBaptized) MilestoneState.DONE else MilestoneState.FUTURE,
        ),
        // Without an enrollment there is no level in progress. The old `?: 1`
        // printed "Level 1 · in progress · Keep going" to members who had never
        // been placed on the pathway — encouragement to keep doing something
        // they had never been able to start.
        // The member's own level in the journey's words — "Level 1 · Exam
        // ready · Take the Level 1 exam" — never "in progress · Keep going"
        // beside every other screen's "Exam ready" (Cycle 3 E13, Cycle 4).
        // Until the journey is known the row waits: no second story.
        if (level != null) {
            profileMilestoneWords(level, journey)?.let { (label, meta) -> MilestoneItem(label, meta, MilestoneState.ACTIVE) }
        } else {
            MilestoneItem("Your pathway", "Starting soon — your leader is setting you up", MilestoneState.FUTURE)
        },
        MilestoneItem("Pathway completion", "Your journey continues", MilestoneState.FUTURE),
    ).filterNotNull()
    SectionCard {
        SectionTitle(Lucide.Target, "MILESTONES")
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
                                    MilestoneState.DONE -> Lucide.Check
                                    MilestoneState.ACTIVE -> Lucide.Calendar
                                    MilestoneState.FUTURE -> Lucide.Heart
                                },
                                contentDescription = null,
                                tint = when (item.state) {
                                    MilestoneState.DONE -> Color.White
                                    MilestoneState.ACTIVE -> PROF.gold
                                    MilestoneState.FUTURE -> PROF.rowLabel
                                },
                                modifier = Modifier.size(14.dp),
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
    onDownload: (Certificate) -> Unit,
) {
    SectionCard {
        SectionTitle(Lucide.BadgeCheck, "CERTIFICATES")
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
    onDownload: (Certificate) -> Unit,
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
                Icon(Lucide.Award, contentDescription = null, tint = PROF.gold, modifier = Modifier.size(22.dp))
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
            Icon(FingerprintIcon, contentDescription = null, tint = PROF.rowLabel, modifier = Modifier.size(14.dp))
            Text(
                c.verificationCode,
                style = pInter(12, FontWeight.SemiBold, 1.1f),   // Inter + tracking, never system Monospace
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
                    if (copied) Lucide.Check else Lucide.Copy,
                    contentDescription = null,
                    tint = if (copied) PROF.success else PROF.gold,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (copied) "Copied" else "Copy",
                    style = pInter(11, FontWeight.Bold),
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
                Icon(Lucide.ShieldCheck, contentDescription = null, tint = PROF.verify, modifier = Modifier.size(14.dp))
                Text("Signed · Verify", style = pInter(11, FontWeight.Bold), color = PROF.verify)
                Spacer(Modifier.weight(1f))
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PROF.gold)
                    .clickable { onDownload(c) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Icon(Lucide.Download, contentDescription = null, tint = PROF.navy, modifier = Modifier.size(14.dp))
                Text("Download PDF", style = pInter(11, FontWeight.Bold), color = PROF.navy)
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

// One date form (§8.1 rule 8): "Mon 5 Oct" (the year when it isn't this
// year) — it read "Oct 5, 2026", and the raw value when it couldn't be read.
private fun certDate(iso: String?): String = org.nuruplace.member.util.NuruDates.day(iso).orEmpty()


// --- Nuru Intelligence: the personalization covenant (one switch) ---
@androidx.compose.runtime.Composable
private fun AiConsentCard() {
    var optOut by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Why the last change didn't land (§4) — "check your connection" was
    // wrong whenever the server itself refused.
    var consentSaveFailed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val consentContext = androidx.compose.ui.platform.LocalContext.current
    var loaded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        optOut = runCatching { org.nuruplace.member.data.net.Net.client.api.aiConsent().optOut }.getOrDefault(false)
        loaded = true
    }
    SectionCard {
        SectionTitle(Lucide.Sparkles, "NURU INTELLIGENCE")
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    "Personal companion & Sunday Letter",
                    style = org.nuruplace.member.ui.theme.NuruType.rowTitle,
                    color = org.nuruplace.member.ui.theme.Nuru.navy,
                )
                androidx.compose.material3.Text(
                    "Nuru remembers your journey to walk with you personally. Your prayer journal is never read — ever. Turn this off and Nuru forgets your story, stops reading your reflections, and pauses your Sunday Letters.",
                    style = org.nuruplace.member.ui.theme.NuruType.caption,
                    color = org.nuruplace.member.ui.theme.Nuru.ink600,
                )
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(10.dp))
            androidx.compose.material3.Switch(
                checked = !optOut,
                enabled = loaded,
                onCheckedChange = { on ->
                    val previous = optOut
                    optOut = !on
                    consentSaveFailed = null
                    scope.launch {
                        // Consent must never lie: if the server didn't record
                        // it, don't display it.
                        runCatching {
                            org.nuruplace.member.data.net.Net.client.api.setAiConsent(
                                org.nuruplace.member.data.net.AiConsentBody(optOut = !on),
                            )
                        }.onFailure {
                            optOut = previous
                            consentSaveFailed = org.nuruplace.member.data.net.ApiException.saveFailureLine(it, consentContext)
                        }
                    }
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = org.nuruplace.member.ui.theme.Nuru.gold,
                ),
            )
        }
        consentSaveFailed?.let { line ->
            androidx.compose.material3.Text(
                line,
                style = org.nuruplace.member.ui.theme.NuruType.micro,
                color = androidx.compose.ui.graphics.Color(0xFFB91C1C),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** "Copy member ID" — a quiet text action; "Member ID copied" for a moment. */
@Composable
private fun MemberIdFoot(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1_600); copied = false }
    }
    val label = if (copied) "Member ID copied" else "Copy member ID"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clickable(onClickLabel = label) { onCopy(); copied = true },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (copied) Lucide.Check else Lucide.FingerprintPattern, contentDescription = null,
            tint = org.nuruplace.member.ui.theme.Nuru.goldChipText, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = pInter(13, FontWeight.SemiBold), color = org.nuruplace.member.ui.theme.Nuru.goldChipText)
    }
}

private val BADGE_GAP = 10.dp

/** A badge tile's width on the rail: its everyday 66 dp (104 past the
 *  everyday sizes) while the badges fit; when they don't, the width at which
 *  four and a half (two and a half) show, so the rail ends on a deliberate
 *  half tile — never wherever a badge happens to be cut. */
internal fun badgeTileWidth(available: androidx.compose.ui.unit.Dp, count: Int, large: Boolean): androidx.compose.ui.unit.Dp {
    val natural = if (large) 104.dp else 66.dp
    val shown = if (large) 2.5f else 4.5f
    val whole = shown.toInt()
    val fits = natural * count + BADGE_GAP * (count - 1) <= available
    if (fits) return natural
    return ((available - BADGE_GAP * whole) / shown).coerceAtLeast(56.dp)
}
