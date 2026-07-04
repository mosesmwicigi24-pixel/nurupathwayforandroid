# Parity Audit — backend truth → mobile apps (2026-07-04)

**Mandate** (owner's instruction, autonomous run): the database / backend / portal / iPad
are the reference. Every field and endpoint exists for a reason; trace that reason and
make the **Android and iOS member apps** reflect it. Never fix in a narrow window —
every change re-checks the whole flow of its page (DB → API → DTO → UI). Small commits,
every finding logged here with its trace and resolution, branches pushed + PRs opened.

**Method**: two read-only auditors diffed (A) backend wire truth (openapi + module
services) vs Android DTOs/renderers, and (B) iOS API consumption vs Android (iOS is
living proof of contract usage). Findings are fixed in ranked order; each fix is one
commit referenced below.

---

## Findings ledger

Status: ☐ open · ◐ in progress · ✅ fixed (commit) · ✖ rejected (reason)

| # | Area | Trace (wire truth → client state) | Resolution |
|---|------|-----------------------------------|------------|
| 1 | Event detail end time | CONFIRMED: GET /events/{id} has NO end on the wire (calendar/service.ts:868-926); end_at lives on /calendar occurrences. Passed as nav state (?end=) from every entry point; TIME tile + Add-to-calendar use it. Verified live: "9:00 AM – 1:00 PM". | ✅ 3e9f62f |
| 2 | Events "My RSVPs" | CONFIRMED: GET /me/rsvps (calendar/index.ts:76) → eventId→status map, as iOS quickRsvps. Wired: header "N you're going", segment count, tab filter. Verified live: 1 going / RSVPs=1 / Your RSVPs lists Sunday Service. | ✅ c7e9646 |
| 3 | Chat hub verse card | Wired to GET me/home/verse (TailoredVerse); static verse is now only the empty-response fallback. | ✅ 965088f |
| 4 | Settings 2FA state | Seeded from GET /me profile.mfa_enabled via LaunchedEffect. | ✅ 8d33050 |
| 5 | Profile field editing | iOS EditFieldSheet edits name/phone/dob/gender/country/city → backend endpoint exists; Android pencils are inert. Resolved via #10. | ✅ 7917aa7 |
| 6 | Prayer-wall reactions | CRITICAL bug found: server counts pray only for emoji=🙏 (service.ts PRAY const); Android sent literal "pray" → never counted. Fixed both call sites. Multi-emoji detail bar still open (→ #6b). | ✅ d72ad62 |
| 7 | Chat prayer chip | `ChatMessage.aiTag == "prayer"` → iOS shows "🙏 I'm praying" chip; Android ignores `aiTag`. Resolved via #14. | ✅ eabbeca |
| 8 | Radio extras | iOS radio uses reactions/chat/remind-me (verify endpoints); Android renders them inert. Resolved via #11. | ✅ d3616ec |

| 6b | Prayer detail 5-emoji reaction bar | Wire accepts arbitrary emoji; iOS detail shows 🙏❤️🕊️🙌✨ per-emoji chips from reactions[]. | ☐ |
| 9 | Giving receipt_code | financial/service.ts serves receipt_code on history+detail; DTOs dropped it. Statement "Ref …" line + receipt Reference now use it. | ✅ bf0bed2 |
| 10 | PATCH /me profile editing | identity/index.ts:160; body full_name/phone_number/gender/city/country_code/date_of_birth/… + row_version (strict zod — omit unset fields; explicit null rejected). iOS EditFieldSheet is the blueprint. Live verify caught: PATCH returns ONLY {user_id,row_version} (service.ts:670) — parse UpdateMeRes + refetch /me. Verified on emulator vs prod (City save round-trips clean, fresh row_version retained). | ✅ 7917aa7 + 7e82595 |
| 11 | Radio react/comments/program | radio/index.ts:163-177: react kind∈heart\|amen\|fire (+client_event_id) → {counts}; comments GET/POST; program GET. Verified live on air: heart tap → server counts 3/2/2 "7 reactions today"; comment "Amen from Android" posted + rendered from refetch with avatar. | ✅ d3616ec |
| 12 | giving/paypal/capture | MemberAPI+Ops.swift:16 → POST {order_id} → {status}; without it PayPal gifts never settle on Android. "I've approved — confirm gift" button on GiveResult calls capture with the order ref. | ✅ 7aaa4e9 |
| 13 | POST /auth/logout | refresh-token revocation on sign-out (security); Android sign-out was local-only. AuthStore.signOut now revokes best-effort (never blocks local sign-out). | ✅ c989178 |
| 14 | Chat prayer chip (ai_tag) | ChatMessage.ai_tag=="prayer" → iOS PrayerChip; Android decoded aiTag but never rendered. Chip shows "🙏 I'm praying"/"🙏 Praying · N" and posts the 🙏 reaction. | ✅ eabbeca |
| 15 | Chat send attachments/replies | send schema accepts attachment_url/attachment_meta/reply_to_id; Android SendMessageBody omits all three; ChatConversation lacks last_duration. | ☐ |
| 16 | UserProfile drops socials/account_status/role_keys | identity/service.ts:567-570 serves them; PATCH round-trip would lose socials. | ☐ |
| 17 | EventDetail images[] gallery | wire returns images[]=[primary,…gallery]; Android shows primary only. | ☐ |
| 18 | Calendar occurrence status/rescheduled | wire serves status + rescheduled; a moved/cancelled occurrence renders as normal. | ☐ |
| 19 | Prayer audio_waveform | posts+comments carry audio_url+audio_waveform; Android drops waveform. | ☐ |
| 20 | Smaller adds | verse reactions (home/index.ts:38,43) · giving statement/receipt PDFs · /me/home/greeting · /badges catalogue · /scripture lookup · share-prayer-to-wall · /home/featured-event · TailoredVerse.mood · cert PDF authed fetch · screen telemetry · chat broadcast+attachments sign (staff) · community threads · /me/discipleship. | ☐ backlog |

(rows appended as auditors report)

---

## Commit log (small chunks, newest last)

- `bc7188f` — docs: open the parity audit ledger
- `965088f` — fix(chat): verse card reads the tailored-verse API (#3)
- `d72ad62` — fix(prayer-wall): send 🙏, not "pray" — CRITICAL count bug (#6)
- `8d33050` — fix(settings): 2FA toggle seeded from the wire (#4)
- `bf0bed2` — fix(give): surface receipt_code (statement + receipt) (#9)
- `3e9f62f` — fix(events): real end time via nav state + real calendar-intent end (#1)
- `c7e9646` — feat(events): /me/rsvps → going count + My RSVPs tab (#2)
- `a8d491d` — docs: Wave-1 resolutions + full ranked findings
- `96d42de` — feat(api): updateMe, radio react/comments/program, PayPal capture, logout
- `eabbeca` — feat(chat): prayer chip for ai_tag=='prayer' (#7/#14)
- `c989178` — fix(auth): server-side refresh-token revocation on sign-out (#13)
- `d3616ec` — feat(radio): real reactions + live comments feed (#8/#11)
- `7917aa7` — feat(profile): field editing via PATCH /me edit sheet (#5/#10)
- `7aaa4e9` — fix(give): PayPal order capture so gifts settle (#12)
- `7e82595` — fix(profile): PATCH /me returns {user_id,row_version}; refetch /me (#10, found by live verify)

## Live verification (emulator vs prod, 2026-07-04)

- Events: header "1 you're going" · RSVPs segment [1] · Your RSVPs lists Sunday Service · TIME "9:00 AM – 1:00 PM".
- Profile: City edit sheet opens seeded ("Nairobi"), Save round-trips PATCH /me → refetch, sheet dismisses clean.
- Radio (on air, "Night Worship hour"): heart react → server aggregate counts ❤️3 🙏2 🙌2 ("7 reactions today"); comment "Amen from Android" posted and re-rendered from the server with author name + avatar.
