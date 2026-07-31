// Nuru Live — bottom-dock item eligibility/ordering (owner layout redesign,
// 2026-08-01, see docs/PARITY_AUDIT.md's dated entry). The owner's screenshot
// of the guest-on-stage screen showed reaction buttons floating down the
// right edge ON TOP of the shared content, a self-preview overlapping it
// bottom-left, and a separate scattered row of controls at the very bottom —
// "lots of wasted space, content hard to follow." The fix: the content gets
// the WHOLE screen, and every control (reactions, raise-hand, chat, camera
// on/off, switch camera, mic, speaker, leave/end) lives in ONE bottom dock
// instead. This file is the PURE (no Compose dependency) half of that dock —
// which items a viewer vs. an on-stage guest actually sees, and in what
// order — kept separate from the composable that renders it (LiveChrome.kt)
// specifically so the eligibility/ordering logic is unit-testable without a
// Compose/Robolectric harness, matching every other pure-logic split in this
// feature (LiveInteractions.kt's abbreviateCount/orderedReactionEntries,
// GuestStageCompositor.kt's promoteSpotlight/railItems, etc.).
package org.nuruplace.member.feature.live

/** Who is looking at the Live screen right now. A pure viewer never
 *  publishes anything of their own, so the camera/mic/switch-camera controls
 *  never apply to them — those only make sense once a member has been
 *  accepted onto the stage and [WhipPublisher] is actually sending their
 *  camera+mic. */
enum class LiveDockRole { VIEWER, GUEST_ON_STAGE }

/** One tappable slot in the bottom dock. [REACT_LOVE]/[REACT_FIRE]/[REACT_LIKE]
 *  map 1:1 to the three backend reaction keys ([reactionEmoji]); the rest are
 *  self-explanatory. Order here is left-to-right render order — see
 *  [liveDockItems]'s doc for why that order was chosen. */
enum class LiveDockItem {
    REACT_LOVE, REACT_FIRE, REACT_LIKE,
    RAISE_HAND,
    CHAT,
    CAMERA_TOGGLE, SWITCH_CAMERA, MIC_TOGGLE,
    SPEAKER,
    LEAVE_STAGE,
}

/** The three reaction dock items, in the SAME left-to-right order
 *  [LiveActionRail]/[orderedReactionEntries]'s `KNOWN_REACTION_ORDER` already
 *  established — a viewer/guest who reaches for the rail muscle-memory
 *  shouldn't find the emojis reordered just because they moved from a
 *  vertical rail into a horizontal dock. */
private val REACTION_ITEMS = listOf(LiveDockItem.REACT_LOVE, LiveDockItem.REACT_FIRE, LiveDockItem.REACT_LIKE)

/** Every viewer/guest gets the audience cluster (react, raise hand, chat)
 *  first, regardless of role — this is the part of the dock that never
 *  changes shape as you're invited on stage and back off again, so a guest
 *  whose invite is withdrawn mid-broadcast doesn't see the WHOLE dock
 *  reshuffle, just the publish controls disappearing off the end. */
private val AUDIENCE_ITEMS = REACTION_ITEMS + listOf(LiveDockItem.RAISE_HAND, LiveDockItem.CHAT)

/**
 * Stable, deterministic dock contents for [role] — pure so the eligibility
 * and left-to-right order is unit-testable (see LiveDockLogicTest) without a
 * Compose harness. Order: reactions -> raise hand -> chat -> [guest-only
 * publish controls: camera, switch camera, mic] -> speaker -> [leave stage].
 *
 * [isVideoKind] gates [LiveDockItem.CAMERA_TOGGLE]/[LiveDockItem.SWITCH_CAMERA]
 * off for an audio-kind broadcast, where a guest only ever publishes a mic
 * (mirrors [WhipPublisher] always sending a video+audio pair today, but this
 * keeps the dock honest if/when an audio-only guest publish path ships —
 * never show a camera control for a stream that has no video track at all).
 *
 * [LiveDockItem.SPEAKER] is offered to BOTH roles — it governs the device's
 * own audio output route (earpiece vs. speaker), which matters for anyone
 * listening, not just an on-stage guest (see LivePlayerScreen's
 * `applyGuestLiveAudioRoute` doc for why it's especially easy to get stuck on
 * the earpiece once a guest's own mic publish flips the device into
 * WebRTC's communication audio mode).
 */
fun liveDockItems(role: LiveDockRole, isVideoKind: Boolean = true): List<LiveDockItem> = when (role) {
    LiveDockRole.VIEWER -> AUDIENCE_ITEMS + LiveDockItem.SPEAKER
    LiveDockRole.GUEST_ON_STAGE -> {
        val publishControls = buildList {
            if (isVideoKind) {
                add(LiveDockItem.CAMERA_TOGGLE)
                add(LiveDockItem.SWITCH_CAMERA)
            }
            add(LiveDockItem.MIC_TOGGLE)
        }
        AUDIENCE_ITEMS + publishControls + LiveDockItem.SPEAKER + LiveDockItem.LEAVE_STAGE
    }
}
