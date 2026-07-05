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

| 6b | Prayer detail 5-emoji reaction bar | Wire accepts arbitrary emoji; iOS detail shows 🙏❤️🕊️🙌✨ per-emoji chips from reactions[]. Verified live: 🙏2 ❤️3 highlighted as mine, rest neutral. | ✅ f7e805c |
| 9 | Giving receipt_code | financial/service.ts serves receipt_code on history+detail; DTOs dropped it. Statement "Ref …" line + receipt Reference now use it. | ✅ bf0bed2 |
| 10 | PATCH /me profile editing | identity/index.ts:160; body full_name/phone_number/gender/city/country_code/date_of_birth/… + row_version (strict zod — omit unset fields; explicit null rejected). iOS EditFieldSheet is the blueprint. Live verify caught: PATCH returns ONLY {user_id,row_version} (service.ts:670) — parse UpdateMeRes + refetch /me. Verified on emulator vs prod (City save round-trips clean, fresh row_version retained). | ✅ 7917aa7 + 7e82595 |
| 11 | Radio react/comments/program | radio/index.ts:163-177: react kind∈heart\|amen\|fire (+client_event_id) → {counts}; comments GET/POST; program GET. Verified live on air: heart tap → server counts 3/2/2 "7 reactions today"; comment "Amen from Android" posted + rendered from refetch with avatar. | ✅ d3616ec |
| 12 | giving/paypal/capture | MemberAPI+Ops.swift:16 → POST {order_id} → {status}; without it PayPal gifts never settle on Android. "I've approved — confirm gift" button on GiveResult calls capture with the order ref. | ✅ 7aaa4e9 |
| 13 | POST /auth/logout | refresh-token revocation on sign-out (security); Android sign-out was local-only. AuthStore.signOut now revokes best-effort (never blocks local sign-out). | ✅ c989178 |
| 14 | Chat prayer chip (ai_tag) | ChatMessage.ai_tag=="prayer" → iOS PrayerChip; Android decoded aiTag but never rendered. Chip shows "🙏 I'm praying"/"🙏 Praying · N" and posts the 🙏 reaction. | ✅ eabbeca |
| 15 | Chat send attachments/replies | Re-traced: the send-schema extras are exercised by the STAFF broadcast path (iOS broadcast composer + portal), not member send — iOS member send is text-only too (MemberAPI.swift:534), so the clients are parity-equal. Received image attachments already render on both (ChatThreadScreen:312 / ChatThreadView:771). last_duration previews voice notes no client can send. Member-side attachment/reply/voice send → backlog #20 as a feature build, not a parity gap. | ✖ parity-equal (→ #20) |
| 16 | UserProfile drops socials/account_status/role_keys | identity/service.ts getMe serves socials/account_status/require_2fa/created_at/role_keys; client dropped all. Now decoded (require_2fa via @SerialName — digit defeats the snake-case strategy). PATCH-loss risk was already avoided by the JsonObject body. | ✅ ad55e82 |
| 17 | EventDetail images[] gallery | wire returns images[]=[primary,…gallery]; Android showed primary only. Gallery strip card renders images[1:]; compile-verified (no gallery data in prod yet — decode-safe default). | ✅ aa82f79 |
| 18 | Calendar occurrence status/rescheduled | projectRange drops cancelled occurrences; moved ones carry rescheduled=true with new start/end applied. RESCHEDULED pill (top-end of card cover) surfaces it; compile-verified (no moved occurrence in prod yet — decode-safe default). | ✅ 1856c94 |
| 19 | Prayer audio_waveform | posts+comments carry audio_url+audio_waveform. Re-traced vs iOS: NO client renders the waveform — iOS shows a passive "Voice prayer" tag when audio_url is set (PrayerWallView.voiceTag). Android now matches. Waveform render + playback → backlog #20. | ✅ 16657cd |
| 20 | Smaller adds | ✅ /badges catalogue (aab7cd1 — locked badges in the rail, verified live) · ✅ /me/home/greeting (adf13c2 — daily word under the greeting, verified live). Still open: verse reactions (home/index.ts:38,43) · giving statement/receipt PDFs · /scripture lookup · share-prayer-to-wall · /home/featured-event · TailoredVerse.mood · cert PDF authed fetch · screen telemetry · chat broadcast+attachments sign (staff) · community threads · /me/discipleship · member voice/attachment/reply send (from #15) · prayer waveform render (from #19). | ◐ backlog |

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
- `f7e805c` — feat(prayer-wall): 5-emoji quick-reaction bar on detail (#6b)
- `aa82f79` — feat(events): images[] gallery strip on event detail (#17)
- `1856c94` — feat(events): RESCHEDULED pill on occurrence cards (#18)
- `ad55e82` — feat(identity): decode full getMe profile — socials/account_status/require_2fa/created_at/role_keys (#16)

## Live verification (emulator vs prod, 2026-07-04)

- Events: header "1 you're going" · RSVPs segment [1] · Your RSVPs lists Sunday Service · TIME "9:00 AM – 1:00 PM".
- Profile: City edit sheet opens seeded ("Nairobi"), Save round-trips PATCH /me → refetch, sheet dismisses clean.
- Radio (on air, "Night Worship hour"): heart react → server aggregate counts ❤️3 🙏2 🙌2 ("7 reactions today"); comment "Amen from Android" posted and re-rendered from the server with author name + avatar.
- Prayer detail: 5-emoji bar live — 🙏2 ❤️3 rendered gold (mine=true from the wire), 🕊️🙌✨ neutral; wall list "2 praying" confirms the Wave-1 🙏 fix is counting.

## iOS mini-pass (nuru-member-ios, 2026-07-04)

Auditor's "iOS lags" list re-checked against the code — most items were already
covered (peerUserId → createDm; ChatPerson flair renders in ChatView; radio
reconciles POST /react counts). Real gaps found + fixed (commit `41cb7fc`,
pushed to main with the stranded discipleship commit `0b39f72`):

- EventDetail never decoded `images[]` and never rendered `videoUrl` (model
  field existed, view ignored it) → gallery rail + universal-player video tile.
- CalendarOccurrence didn't decode `status`/`rescheduled` → RESCHEDULED gold
  pill in the card's status slot (same slot as LIVE / "Filling fast").
- PATCH /me response shape checked: iOS decodes `EmptyResponse` + refetches via
  auth.loadProfile() — already safe; only Android had the MeResponse mis-typing.

`xcodebuild` simulator build green.

## Session 2 (2026-07-04, continued autonomous run) — backlog burn-down

| # | Area | Trace (wire truth → client state) | Resolution |
|---|------|-----------------------------------|------------|
| 21 | Verse reactions + mood + Save/Share | home/index.ts:37-45: GET/POST /me/home/verse/reactions (enum'd emoji, one per member/day, switching moves it) → {counts,mine,total}; TailoredVerse.mood served but dropped. Android verse card had none of iOS's reaction/Save/Share row. Verified live: ❤️ tap → "❤️ 1" gold chip from the server response; mood "FRIENDSHIP" renders in the kicker. Save = existing PUT /me/verses quick-save; Share = ACTION_SEND. | ✅ 205032a |
| 22 | /home/featured-event orphaned on BOTH clients | calendar/index.ts:208 serves the portal's "feature on homepage" toggle (setSeriesFeatured, leaderPlus, partial unique index = exactly one); iOS declared MemberAPI.featuredEvent + the model but NO view called it; Android had nothing — the admin toggle had zero member-facing effect. Featured-gathering card added to BOTH Homes (cover, title, description, local start + location → Events tab), hidden on data:null. iOS commit b73addd (pushed to main). | ✅ a994656 + iOS b73addd |
| 23 | /me/discipleship — student Hub missing on Android | discipleship/index.ts:17 + service.ts MyDiscipleship (discipler resolve → cell leader fallback, dm_conversation_id never created on GET, can_message false for minors, progression w/ awaiting usher, scores, reflections w/ feedback, notes reserved). Android port of iOS DiscipleshipHubView (~490 lines); Pathway's "Your Discipleship Hub" row was a mislabeled stub routing to Mentor — now routes here. Verified live: hub loads, renders the paired-soon empty state (server resolved no discipler for this account). | ✅ 0c3fbf1 |
| 24 | share-prayer-to-wall | prayer-wall/index.ts:44 POST /me/prayers/{id}/share-to-wall → 201 {post_id}, idempotent (re-share returns existing), 404 for offline-created entries the server hasn't synced. Confirm dialog + "On the wall 🙏" state; NEVER queued offline (member-visible copy) — mirrors iOS PrayerJournalView. | ✅ a4bb0e1 |
| 25 | Certificate PDF download broken | certificates/service.ts:99 emits download_url as a RELATIVE media path; media/index.ts:188 requires the authed session + ownership. Android's browser ACTION_VIEW on that path could NEVER work (unresolvable relative URI; 401 without the bearer). Now: @Streaming authed fetch → cache/shared → FileProvider content:// → PDF viewer chooser. Manifest gains the FileProvider (new file_paths.xml). | ✅ c4fbd2f |

Still open in #20: giving statement/receipt PDFs · /scripture lookup · TailoredVerse follow-ups · screen telemetry · chat broadcast+attachments sign (staff) · community threads · member voice/attachment/reply send · prayer waveform render.

| 26 | Giving statement/receipt PDFs | financial/index.ts:71,88 — statement.pdf + receipt.pdf accept bearer OR ?token= (built for RN Linking). Neither client used them; Android's statement Download icon shipped INERT. Both now stream through the authed client (a ?token= URL would leak the JWT into browser history) → FileProvider viewer. Receipt screen gains a gold download button. | ✅ 185d913 |

## Session 3 (2026-07-04) — discipler console on both member apps

| # | Area | Trace (wire truth → client state) | Resolution |
|---|------|-----------------------------------|------------|
| 27 | Discipler console (roster + dossier) missing on BOTH apps | discipleship/index.ts: GET /disciples (requireRole Instructor; roster pre-sorted needs-action first — awaiting_level, pending_reflections, band risk) + GET /disciples/{id} (assertInScope; the PASTORAL view — reflections include the body, which /me/discipleship withholds). Only the web portal consumed them. Built on both apps: triage roster (band pills, "Usher · L{n}", pending-reflection chips, last-active red at 7d+) + dossier (engagement e-score/band, "Awaiting YOUR usher into Level {n}" banner, reflections with body + your feedback, recent activity, Message CTA via existing-DM-or-create). Entry: staff-gated "Your disciples" card on Profile (role ∈ Instructor/Admin/SuperAdmin — hides the door; the server enforces regardless). Live negative-verify: the signed-in Student account sees no entry. Android c4e75d3+f234bf7+cf750f7+e6bff1c · iOS 6726fc4 (pushed to main, BUILD SUCCEEDED). | ✅ |

## Session 4 (2026-07-04, "check the entire system" run)

| # | Area | Resolution |
|---|------|------------|
| 28 | Keyboard hid inputs everywhere | adjustResize + imePadding sweep across 18 screens (pinned composers, scrolling forms, bottom sheets). ✅ 36669f0 |
| 29 | Load-error dead ends (missing event / failed quiz → restart app) | AsyncContent error state now always offers "‹ Go back" via the back dispatcher — fixed once for every screen. ✅ db30c9b |
| 30 | Font schema | ui/theme/TypeSchema.kt = the ONE source of rhythm (Inter 1.4× line-height, Fraunces 1.25×, tracking rules); all 8 per-surface helpers are one-line delegates; zero call-site churn. ✅ e16f13b |
| 31 | Chat hub rows to the reference | Unread gold circles, 3-avatar stacks + member pill, "🎙 Voice message · m:ss" (new last_duration/message_count/reaction_count decodes), topic meta, navy "+ Join". Skipped as not-on-wire: pin, huddle, "N new today". Verified live. ✅ c5836b0 |
| 32 | Member voice notes (record → live wave → post → playable wave) | NEW backend POST /me/media/audio (pathway PR #338 — prayer audio_url/audio_waveform + chat attachment fields finally have a member upload path; 5 MB, tests green). Android: VoiceRecorder + WaveformBars/LiveWave; chat mic composer + voice bubbles; prayer-wall Add-voice + playable waves (server cap 80 ints; we send 64). ⚠ prod 404s until PR #338 merges + deploys. ✅ 6fb8394 |
| 33 | Navy headers → Home cream chrome | Discipleship trio, shared ScreenHeader (9 screens), quiz + plan-day headers. ✅ 384ba16 |
| 34 | Cross-surface conflicts (audit → fixes) | Currency: money(minor,currency) on BOTH apps (USD PayPal gifts no longer print "KSh"); "Your cell" wording on both Homes + iOS assistant; usher verb unified; portal band labels "At risk" + MemberProfile bandStyle keys NEVER matched the raw wire (at-risk members rendered neutral) — fixed. Android 8953e37 · iOS 8e1ceca · portal on pathway feat/member-voice-notes. Still open from the audit: cohort/cell wire rename (P1), Mentor-vs-Hub duplicate surface, OpenAPI rsvp body gap, legacy 'rejected' reflection state, chat kind vocab, env-resolution drift. |
| 35 | Seeded-content quality | Migration 1758000000096 strips '_ref_' wrappers no client renders (plain text = correct on every surface); seeds/07 fixed at source; seed-home-demo.mjs now uses curated subject-matched Unsplash + initials medallions instead of random picsum/pravatar. Home welcome-video no longer prints its caption twice (Android + fix pending iOS). |

Follow-ups queued: iOS voice notes + chat-row polish mirror · deploy backend after PR #338 · event content grooming in the portal (radio programs are runtime content, not seeds).

## Session 5 (2026-07-04) — cascade stacks, Broadcast, AI drafts, honest rhythm

| # | Area | Resolution |
|---|------|------------|
| 36 | Avatar stacks — exact reference cascade | 20dp circles overlapping -7dp, 2dp white rings, layered right-over-left, count pill as the final overlapping element. Verified live on spaces + discover rows. ✅ |
| 37 | Broadcast tab | Server design already right (POST /chat/broadcast fans out as individual DMs; replies come back 1:1). Added the missing mobile admin composer: staff-only 4th tab, confirm dialog, "Delivered to N members". Members correctly see nothing new (verified: Student account shows no tab). ✅ |
| 38 | AI drafts where members type | ✨ AiDraftButton in the chat composer + prayer comment bar: last 5 messages → /assistant/chat (server-side key) → SUMMARY + editable DRAFT sheet (Use / edit / Discard). Grounded via conversation_id, context_limit 5. ✅ |
| 39 | Today's rhythm = reflection of real acts | Backend: prayer-wall post/comment/reaction fulfill prayer; scripture_read counts as Word; module + plan-day reflections fulfill reflection (all once/day EAT; devotional-reflection + verse-practice + journal feeders already existed). Android chips are read-only — tick green from the server, no tap. Manual endpoint kept for compat. ✅ (pathway 2ea1b6f · android 40af1f0) |

iOS mirror queued: cascade stacks, staff broadcast composer, AiDraft, read-only rhythm chips (iOS rhythm card taps → remove).

Session-5 iOS mirror landed (nuru-member-ios f0ee55f, BUILD SUCCEEDED): cascade stacks exact, broadcast copy/flow aligned (the tab pre-existed on iOS — Android was ported FROM it; iOS keeps its photo-attachment + AI-polish extras), AiDraftButton in chat composer + prayer comments, rhythm tiles read-only. Both member apps now match on all four session-5 features.

Session 6 (2026-07-04):

| # | Item | Resolution |
|---|------|------------|
| 40 | Commissioning summit reimagined — "powerful, beautiful, real" | Both apps: real worship-gathering image (visually verified before shipping — the first pick from memory was a kingfisher), gold ring + ★ SENT chip at 100% (lock + AHEAD OF YOU before), ceremonial double-ring gold medal seal, tracked COMMISSIONED, the actual charge in italic serif — "Go therefore and make disciples of all nations…" MATTHEW 28:19 — a level-dot road (one gold dot per level walked), and a personal line: "{name}, you have been commissioned — go." / "N levels between you and being sent." Reaching 100% fires the once-ever commissioned celebration. Android verified live on-device. ✅ (android 3452edc · ios 5152314) |
| 41 | Audio upload cap 250 MB, whole flow | Backend multer AUDIO_MAX_BYTES 110→250 MB + error copy (pathway eb0a308, PR #339, deployed — readyz 200, route live); nginx /v1/admin/media/audio/upload gate 115m→260m reloaded; portal RadioStudio pre-check 110→250 MB rebuilt + rsynced. ✅ |
| 42 | Radio: iPhone mis-fit + no back · LIVE bar → wave | iOS player rendered oversized/off-center with the back button off-screen — two fullScreenCovers (Home + Root) presented the same screen and it sized itself from hand-read key-window insets under ignoresSafeArea. Fixed: ONE cover owned by RootView (Home posts .nuruOpenRadio), standard safe-area layout, backdrop alone bleeds. Both apps: the LIVE loading-style sweep replaced with a subtle breathing gold wave (layered sines, bright mid-line, edge fade) — verified live on the emulator. ✅ (android 797f0ea · ios 9c15be8) |
| 43 | Android OEM-native feel pass (v2.1.5/16) | Branded TGNM cold-start splash (SplashScreen compat), predictive back flag (One UI 7/8 peek), themed-icon monochrome layer, first-ever haptics layer via the OS effect map (tab tick, radio play tap, celebration CONFIRM — Samsung/HiOS/stock each render natively), long-press shortcuts (Radio/Pathway/Prayer/Give via PendingDest intent extras), dropped deprecated statusBarColor. Emulator-verified: splash, shortcut panel, shortcut→radio nav. APK NuruPathway-2.1.5-16 on Desktop. ✅ (android 5e59750) |
| 44 | iPhone 17 Pro Max: Pathway edge-spill + header top-crop | Two more bare scaledToFill images (surrender figure + summit card) inflated the Pathway column past both edges (cut "F", half ring) — same class as the radio bug, same Color.clear-overlay fix. Full-bleed headers hard-coded 60pt under the paper status stripe; island area is ~62pt on the 17 Pro Max so the bell badge slid under — Home/Pathway hub/map now pad from the real inset (shared NuruSafeArea.top). ✅ (ios 454c462) |
| 45 | iOS app-wide fill-image sweep | Audited all 24 scaledToFill sites against the inflation rule. 18 proven safe (fixed frames / GeometryReader / already on the Color.clear-overlay pattern); 6 latent hazards fixed before anyone hit them: LevelDetail encouragement image, HomeFadeInImage wrapper (Home featured + announcement cards), welcome video thumb, PlanSegment video poster, GivingStatement header backdrop, VideoPlayerPage poster. The bug class is now extinct on iOS. ✅ (ios 4d95e5c) |

## Session 7 (2026-07-05) — login crisis, real live-listener presence, prod closeout

| # | Item | Resolution |
|---|------|------------|
| 46 | Android "can't connect / could not connect to the servers" login outage (wide device mix: S24 Ultra, Tecno, Xiaomi, Infinix, itel) | NOT a launch crash / Android-version compat issue — the app opened; **login** failed, two independent causes. (1) Backend had no `app.set("trust proxy", 1)`, so behind the VPS nginx `req.ip` was the container IP for every request → the `/v1/auth` limiter keyed ALL logins to ONE shared bucket (cap 20) and mass-429'd any burst. Fix: trust proxy 1 + raised /v1/auth to cap 240 / refill 4. (2) Android OkHttp had NO explicit timeouts — on a ~1 KB/s network the default 10s read timeout hung ("taking too long"). Fix: connect 15 / read 30 / write 30 / call 45 on both clients. Verified: 30 rapid prod logins → 0×429. ✅ (pathway 9e6e6dd, deployed · android ApiClient.kt) → memory [[prod-login-rate-limit]] |
| 47 | Real live-listener presence — roster replaces placeholders (web + iPad), reactions rise TikTok/IG-style | Backend: `radio_listeners(program_id,user_id,last_seen)` + `POST /radio/programs/:id/listening` heartbeat (upsert) + `GET …/listeners` roster (45s window, real name+avatar), health prefers live count. Both member apps heartbeat every ~20s while playing (Android LiveRadioScreen LaunchedEffect · iOS RadioPlayerView .task). Web RadioStudio + iPad RadioStudioView render the named roster (avatar+name+"Listening" dot) with the authoritative count; count-only stat retired. TikTok/IG full-screen rising hearts/hands + growing counter shipped on the member radio page. ✅ (pathway #344 → 032e023, deployed · iPad pathwayforipad #2 → 774d6fc, BUILD SUCCEEDED) |
| 48 | Prod closeout — VPS/backend/DB/portal/iPad complete | Presence image 032e023 deployed to the VPS (pull→tag→migrate→recreate api+worker). DB verified: `radio_listeners` table live (program_id,user_id,last_seen). `/radio/programs/:id/listeners` live (401 unauth = route present). Portal dist with the real roster rsynced to /var/www/pathway-portal. iPad named roster merged. Also live this session: member→user elevation + per-permission + web-registration removal (099), 18 authored reading-plans seeded (097/098/100 — 25 plans total), iPad USB-C mic plug-and-play. ✅ |

Both member apps now write presence; web + iPad both read the real named roster. iOS device rebuild (heartbeat commit acbc7a6) + Android 2.1.8 APK distribution are the remaining manual device steps — not deploys.

## Session 7b (2026-07-05) — reading plans: retire broken legacy seeds, fix browse gap

| # | Item | Resolution |
|---|------|------------|
| 49 | "Plans not showing on the phone apps" | Root cause was TWO things. (1) The catalogue mixed the **18 authored 'Power to Become' plans** (all complete: full day count + 5 segments/day) with **7 legacy demo seeds**, three of which are broken on the wire — psalms-of-comfort (0 days), sermon-on-the-mount (0 days), gospel-of-john (4 of 21) — so they open EMPTY. Retired all 7 via migration 102 (is_active=false; reversible; enrolment history kept). Prod now serves exactly the 18. (2) Both clients bucketed browse rows as Short (≤7d) + Longer (≥14d), so 10-day plans (most of the 18) only showed in Featured (first 8) or via a category tap — never a browse row. Added a **Mid-length (8–13d)** collection to both apps so every plan surfaces. iOS rebuilt build 7 + installed on Pastor + Jackline iPhones; Android committed (APK on request — no Android device connected). ✅ (pathway c77a723 #346 · ios build 7 · android main) |

## Session 8 (2026-07-05/06) — the Plans experience arc (iOS builds 8→25)

| # | Item | Resolution |
|---|------|------------|
| 50 | Plans journey restructure (owner-directed, iterative) | Single-scroll reader → then the FOUR-page journey: Plan → Days (big serif date chips, never covered by ticks; gold tint + corner badge when done) → DAY HUB (big serif day numeral, story-arc rows, Next pill, walk strip, inline reminder) → focused part reader. Day consolidated into a story arc: **Watch/Listen** (portrait screen-filling media + scanty keynotes) → **The Word** (Scripture woven into the teaching, Go Deeper folded in) → **Respond** (Talk it Over + Prayer + Reflection on one page). Parts tick on 'Finished' (whole group, server-backed, live hub update via NotificationCenter). ✅ |
| 51 | Reading feel + psychology | Dwell-based completion replaced by explicit per-part finish; '🐢 slow down' nudge (dropped with the hub model); ~5s Canvas fireworks 'boom' on day complete; plan-completion keepsake (gold seal, Matthew 25:23 blessing, shareable branded card via ImageRenderer/ShareLink, 'Continue your journey'); grace-first streak copy + Quiet Mode; resume banners (Home + Plans) + daily reminder notification; warm night/sepia reader mode (ReaderPalette env); Pathway typographic voice adopted (Inter 16 Medium ls7 body; Fraunces MEDIUM + negative kerning for ceremony; italic serif for scripture/prompts). ✅ |
| 52 | Craftsmanship pass (build 23) + tab-bar burial fix (24) | Dead affordances made honest ('See all' removed, Invite + Read-with-a-friend → real ShareLinks); orientation copy ('N of 3 parts read', 'YOUR JOURNEY · N DAYS', 'Show all N days'); warmer CTAs ('Begin Day 1', 'I've read today's Word', 'Amen — finished'); doneIds passthrough (reopened part greets 'Done'); onAppear/onDisappear push race fixed (bar buried 'Mark day complete'). ✅ |
| 53 | Talk it Over — shared plan-day conversation | Full stack: migration 121 (plan_day_talk_posts + likes), GET/POST /growth/plans/:id/days/:n/talk + POST /growth/talk/:id/like (author name/avatar, like_count, my-like; posting fulfils reflection rhythm §1.8); OpenAPI documented. iOS TalkItOverView: participants avatar stack, TODAY'S QUESTION serif prompt, response cards with encouragement hearts, pinned composer, empty state. Entry on the Respond page. (pathway 98c452f #349 · ios build 25) ✅ |

**Android parity DEBT (deliberate):** the ENTIRE Session-8 plans experience exists only on iOS. The Kotlin app still has the old tab-through reader. Port list: 4-page journey + story-arc hub, part readers, night mode, resume banners + reminder, grace copy + Quiet Mode, keepsake, browse progress rails, Pathway type, Talk it Over. |
