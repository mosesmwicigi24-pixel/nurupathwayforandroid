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

## Session 8 addendum (2026-07-06) — Talk standalone, notifications, global voice, PDF-canonical catalogue (iOS builds 26→28)

| # | Item | Resolution |
|---|------|------------|
| 54 | Talk it Over made STANDALONE on the day journey (owner correction) | Day hub arc is now Watch/Listen → **The Word** → **Respond** (Prayer + Reflection) → **Talk it Over** as its own row/page (TalkRoute; opening it marks the talk segment read). Media-first ordering confirmed (video/audio rows lead when present). ✅ (ios build 26) |
| 55 | Notification deep-links + universal back | Tapping a notification routes to its exact home: announcementId → announcement, moduleId → module, level → Pathway level, event → Events, giving/payment → Give, badge/certificate → Profile; anything unroutable opens a read+dismiss popup sheet (0.42/medium detents). Back-arrow audit across all pages: clean. ✅ (ios build 27) |
| 56 | Global typographic voice (owner-set) | The Pathway module-reader weight is now the APP-WIDE default: `interFace` regular → **Inter-Medium** for every title, subtitle and paragraph; Fraunces stays the ceremony serif. One warm reading voice everywhere. ✅ (ios build 28 — installed on Pastor + Jackline iPhones) |
| 57 | Study-plan catalogue rebuilt from the author's own PDFs — 18 → **22 live** | Found Moses' 22 "PLAN — <Title> (N Days)" PDFs, extracted (pdftotext -layout), and reseeded every plan with his verbatim words: PDF-canonical verses, Talk it Over questions, prayers (+ blessing lines), Go Deeper refs (migrations 122–139). FOUR NEW plans added: Covenant Love, Lead Yourself First, The God of Systems, The Reward System (140–143). Migration 144 keeps the deeper approved devotional per (plan, day) where the earlier authored teaching ran longer. Prod verified: **22 plans · 237 days · 5 segments/day · Devotional avg ~1,531 chars**. Content is server-side → live on ALL member apps instantly. ✅ (pathway #350 → 97f43c1, deployed) |

| 58 | Tabs tell the truth + reading instruments in Plans (iOS build 29) | Owner: Home's "For you today" opened a Pathway module while the tab stayed Home. Cross-tab deep links added (TabRouter pathwayLink/planLink/eventLink): Pathway content from Home nudges/progress cards/notifications now lands ON the Pathway tab at the exact module; plan links (resume banner, mini, Grow tile) land on Plans; Home's live-now card lands on Events. Reading instruments unified: shared NuruReadingBar + NuruPaceRail (NuruTheme), now also in the Plan part readers; the right-rail pacer rebuilt to be actually visible (4pt/22% track, 85% fill, 12pt white-ringed dot — old 3pt/12% rail washed out on device). Installed: Pastor + Jackline. ✅ (ios d6a0132) |

| 59 | Home warmth + exact-tap notifications + widget groundwork (iOS build 31) | Home: 20pt section rhythm, gold-pill header links (Open wall / View / View all), minis breathe. "Meet your discipler" card REBUILT — its pager used WHITE system dots on a white card + a fixed 150pt frame, which read as an empty/invisible card on device (owner report); now content-hugging with serif name, full quote, message pill, gold dots (hub itself was already fully wired to GET /me/discipleship). iOS notification TAPS now land on the exact target (module/level/announcement/tab) + mark read. nuru:// URL scheme + routing shipped. NuruWidgets WidgetKit target (Pathway/Chat/Radio doors) written but PARKED — Xcode on the build Mac has NO Apple ID signed in, so the new appex bundle id can't be auto-provisioned on the free team; sign in once → re-embed → widgets live. Installed: Pastor + Jackline. ✅ (ios 171902a) |

| 60 | Talk it Over: visible back, no forced tick, AI compose (iOS build 32 + backend PR #351) | Owner: the back arrow was BURIED under the status bar (page wrongly ignored the top safe area — removed), and opening the page auto-marked the talk part read; now only the gold "I've talked it over" button seals it — back leaves the day untouched. NEW: sparkles AI compose in the composer — POST /growth/plans/:id/days/:n/talk/assist (assistant provider: Groq live in prod, fake in tests) drafts an honest first-person starter from the day's questions or polishes the member's own words (voice kept); suggestion only, member edits + sends. OpenAPI + test (growth-content 10/10). Installed: Pastor + Jackline. ✅ (ios 0c8fa53 · pathway #351) |

| 61 | Android APK 2.1.9-20 + personalized notification popup (iOS build 33) + talk-assist LIVE | Signed universal APK **NuruPathway-2.1.9-20.apk** (28 MB, CN=Nuru Place 37ed00…) on the Desktop — same code as 2.1.8-19 (docs-only commits since), bumped so devices holding 19 update. Talk-assist backend deployed to the VPS (43c6ee2; route answers 401 unauth — LIVE with the Groq key). iOS notification popup rebuilt as a personal card: greeting by name, the message, live quick stats (streak · level · plan day), template-aware encouragement, gold "Continue my journey" → active Pathway level. Installed: Pastor + Jackline. ✅ (android 6521656 · ios 70d68b5) |

| 62 | "Mark all read" always legible (iOS build 34) | The all-read state dimmed the gold-on-navy pill to 40% opacity — an unreadable gray blob (owner report). Now: unread → full-strength navy pill, bright-gold bold label; all read → calm gold-tinted "All read" chip at full opacity. Installed: Pastor + Jackline. ✅ (ios 8ed16ec) |

## Session 9b (2026-07-07) — quiz/exam submit fix (Android)

**BUG (owner-reported, "Request body failed validation" on the exam):** Android's
QuizScreen minted `client_mutation_id = "mut-<n>-<nanotime>"`, but the backend
submit schema is `client_mutation_id: z.string().uuid()` — so EVERY module-quiz
AND level-exam submit was rejected by validation (iOS was unaffected: it sends a
real UUID). Fixed: `newId()` → `UUID.randomUUID().toString()`. APK **2.3.1 (vc23)**.
The answer-shuffle work was NOT the cause (shuffling reorders choices; grading
matches by id/text; submit shape unchanged).

## Session 9 (2026-07-07) — the level-exam feature + Android parity push

**Backend (pathway #352 → deployed):** level exam `review→published` publish gate
(levels.exam_status, migration 145; member exam hidden until published); server-side
**answer-choice shuffling** for module quizzes AND level exams (correct answer no
longer pinned to slot A; grade-safe by id/text); new **50/30/20 level score** —
GET /me/levels/:n/score = exam(50) + module quizzes(30) + participation(20) = /100.
Portal + iPad Quiz Builder got a Review/Publish toggle.

**iOS (build 35):** PathwayLevel.exam_published gates the exam CTA; exam pass ceremony
shows the 50/30/20 breakdown; answers shuffle server-side (no client change).

**Android parity brought forward (this session):**

| # | Item | Resolution |
|---|------|------------|
| 63 | Global Inter-Medium voice | TypeSchema nuruSans() default weight Normal→Medium — every title/subtitle/paragraph app-wide, matching iOS build 28. (android c3de136) |
| 64 | Exam publish gate + 50/30/20 score | PathwayLevel.examPublished hides the exam CTA until published; LevelScore DTO + GET /me/levels/:n/score; the level-complete ceremony renders exam(50)+modules(30)+participation(20)=/100 with gold bars. Answer shuffle needs no client change (wire order is now server-shuffled). (android c3de136) |
| 65 | Personalized notification popup + exact deep-links + FCM cold-tap | Unroutable notifications open a read-and-continue popup (greeting by name · live streak/level/plan chips · encouragement · "Continue my journey" → Pathway), matching iOS build 33; routeFor lands on the EXACT target (level/{n}, Profile for badge/cert, Give for payment); FCM cold-tap attaches nuru.dest so a tray tap deep-links instead of opening Home. (android 2fee413) |
| 66 | Talk it Over + AI compose | New TalkItOverScreen (navy header · today's question serif card · response cards with encouragement hearts · pinned composer with an AI sparkle → /talk/assist). DTOs + 4 endpoints + nav route + entry card on PlanDayScreen. Home feed rhythm 16→20dp. (android 067c5bb) |

**Android APK 2.2.0 (versionCode 21)** carries #63–#66.

**iOS home-screen widgets SHIPPED (build 36, ios 76840da):** the parked NuruWidgets
WidgetKit target is now embedded — the owner signed an Apple ID into Xcode, so the
appex bundle id provisions on the free team. Re-added the app→widget dependency +
"Embed Foundation Extensions" phase and set the widget PRODUCT_NAME (was nil → empty
".appex" / "multiple commands produce" error). Three branded doors (Pathway / Chat /
Radio) open via nuru:// deep links. Installed on Jackline's iPhone; Pastor's iPhone
reinstall (builds 35/36) pending its reconnection.

**Four-page Plans journey SHIPPED (android 0bfb302, APK 2.3.0 vc22):** the last
big Android parity item is done. Rebuilt to the iOS Plans arc: PlanReaderKit
(day/night reader palette + the reading progress hairline + right-rail pace dot +
PlanProgressBus + palette-aware reader blocks); PlanDayScreen is now the story-arc
DAY HUB (Watch/Listen → The Word → Respond → Talk it Over, each a tappable row with
a Next pill / live tick); PlanPartReaderScreen (new — one focused part on a warm
day/night canvas, The Word weaves Scripture+teaching+Go Deeper, Respond carries the
prayer + reflection, "Finished" ticks the whole part); PlanKeepsakeScreen (new —
plan-completion gold seal + Matthew 25:23). Nav: plan/{id}/day/{n}/part/{tag}/{i}
+ plan/{id}/keepsake. Android is now at iOS parity across font, exam feature,
notifications, Talk it Over, AND the Plans journey.

**Android APK history:** 2.1.9-20 (level-exam readiness) → 2.2.0-21 (#63-66) →
**2.3.0-22 (four-page Plans journey)** on the Desktop.

**Android parity DEBT (unchanged, restated):** entire Session-8 plans experience + this addendum (standalone Talk, notification deep-links, global Inter-Medium voice) are iOS-only. Backend/content items (22-plan catalogue, talk API) are shared and already live for Android.
**Backlog (logged, not started):** CMS media authoring for Watch/Listen segments; eventId in notification payloads for per-event deep-links; Android plans-experience port.

---

## Session 10 — the level exam is now a VISIBLE, LOCKED row in the trail

**The ask (owner):** *"When I finish the last module of a level, it should take me
to the level exam. I should SEE the exam on the trail — though it's locked until I
finish everything above it. Visible, but not accessible until I'm granted that
permission."* The previous model hid the exam container and used a separate gate,
so members couldn't see where the exam was.

**Backend (pathway#353, deployed):**
- `listModulesForLevel` returns the `exit_exam` module again (was hidden). Its
  `unlocked` = every content module done; `completed` = a passing
  `level_exam_attempt` (never a read-to-complete). Only surfaced once
  `levels.exam_status = 'published'`. Still kept OUT of the "finish every module"
  exam gate so it can't deadlock the exam it fronts.
- Pathway summary counts the exam row only when published, so `N of M done`
  matches exactly what's visible in the trail.
- Home next-action skips the exam container → once content is done it routes to
  the Pathway tab (where the row lives), not the reader.
- Prod data cleanup: deleted 2 stale `exit_exam` `module_progress` completions
  left by the old deadlock's "mark complete" (an exam container must only be
  "completed" by passing).
- Tests: exam row visible+locked before content, unlocks after, flips to
  completed on pass; hidden while in review. Full backend suite green.
- Also de-flaked the calendar "next occurrence" test (anchored the series a week
  ahead) — it had started failing by wall-clock.

**iOS (build 37, ios main):** `LevelModule.isExam`; the hub `PWModuleRow` and the
level-detail trail render the exam row distinctly (award glyph, gold tint, "Level
exam · ready / locked / passed" captions, a "Start exam" pill) and stay visibly
locked until unlocked; tapping the unlocked row opens the exam (`PathwayRoute.exam`)
— including every resume/continue affordance, via a shared `openModuleId` router.
Installed build 37 on Pastor's + Jackline's iPhones.

**Android (APK 2.4.0 vc24, android main):** parity — `LevelModule.isExam`; hub
`ModuleRow` + LevelDetail `ModuleStation` render the exam row distinctly (trophy,
gold, exam captions, "Start exam"); the row / its Continue affordance opens the
exam (`onOpenExam`/`onTakeExam`); the standalone exam gate/button is kept only as a
fallback for levels with no exam module. On the Desktop as NuruMember-2.4.0.apk.

**Blast radius:** member-facing → backend + iOS + Android. Admin (web portal +
iPad) = N/A (the Review→Publish toggle already exists; this only changes how the
member trail presents the published exam).

---

## Session 11 — Home ring = rolling 28-day growth score + trend; attendance fixed

**The ask (owner):** the Home top ring should show the growth score (average of
habit/word/prayer/curriculum/attendance), attendance was stuck at 0 on iOS, and
add a **28-day rolling comparison** (Facebook-insights style) so the number shows
whether you're growing or slipping.

**Root causes found (read-only scan, two agents):**
- iOS Home ring was bound to *pathway module-completion %* (hence a flat 100%),
  not the growth score. Android's ring already used the growth score.
- Attendance filtered `interaction_events.kind = 'attendance'` — a tag NOTHING
  ever emits (check-ins are `check_in`), so its 0.20 weight sat at 0 and dragged
  the composite down for every app-only member.
- No time-trend anywhere: word/prayer/habits used 14d, attendance 30d, curriculum
  lifetime; nothing compared periods.

**Backend (pathway#354, deployed):** every domain now measured over a rolling
28-day window ending at an `asOf` instant (default now) — relative to the request
so it slides on its own. `all()` computes the composite for the current 28 days
AND the previous 28 (`asOf − 28d`) and returns `trend {window_days, previous,
delta, direction, domains}` — no stored history, two windows in one request.
Attendance redefined as PRESENT DAYS = any in-app activity over the window
(owner's call: app presence), target 20/28. Curriculum bounded by `asOf`. Weights
unchanged (25/25/20/15/15).

**iOS (build 38):** Home ring rebound to `scores.overall.score` + a ▲/▼ trend
badge; "Your progress" card gets an "Up/Down N vs last 28 days" caption and
per-domain deltas; `ScoresSummary.trend` (optional). Installed on Pastor's iPhone.

**Android (APK 2.5.0 vc25, android main):** ring already showed the growth score;
added `ScoreTrend` DTO, a ▲/▼ `TrendBadge` on the ring, the trend caption + per-
domain deltas on the bars. On the Desktop as NuruMember-2.5.0.apk.

**Blast radius:** member-facing → backend + iOS + Android. Admin (portal/iPad) =
N/A. Deferred (flagged, not done): adminops leader-side attendance uses the same
dead `kind='attendance'` branch — a separate pastoral-metric decision.

---

## Session 12 — event wall "Hype the room": photo attach + bigger box + cleaner layout

**The ask (owner):** the "Hype the room" buzz composer should have camera + upload
image as a "+", maximize the text box, and reorganize the "You" tag and Post button.

**Backend (pathway#355, deployed):** new `POST /me/media/image` — a generic member
image → { url } upload (multipart 'file', images only, 5 MB), mirroring /me/avatar
but not stored on the user. The event-post schema already accepted image_url, so
composers just attach the returned URL.

**iOS (build 39) + Android (APK 2.6.0 vc26):** the composer became a roomy card —
- **"+" attach** opens Take Photo (camera) / Choose Photo (library); the picked
  image is downscaled (≤1600px, JPEG ~0.82), previewed with a remove (×), uploaded
  on Post, and attached as the post's image_url.
- **Maximized text area** — multi-line (3…9 lines, min 76pt/dp) on its own row.
- **Reorganized** — "You" in a header row above the text; action row is
  attach · flame · —— · Post; a post now needs text OR a photo; Post shows a
  spinner/"Posting" while uploading.
- iOS: uploadPostImage + EvdCameraPicker (UIImagePickerController) + UIImage
  downscale; createEventPost carries imageUrl. Android: uploadPostImage +
  PickVisualMedia/TakePicture (FileProvider) + downscaleJpeg; EventPostBody.imageUrl.
Installed iOS 39 on Pastor's iPhone; APK 2.6.0 on the Desktop.

**Blast radius:** member-facing → backend + iOS + Android. Admin (portal/iPad) = N/A.

---

## Session 13 — AI PHASE 1: the Member Story brain, story-aware Nuru, the Sunday Letter

**The vision (owner: "lets build!"):** one understanding layer per member, every AI
surface drinks from it — "one brain, many hands."

**Backend (pathway#356, deployed + migration 146):**
- `modules/intelligence`: ContentIndexService (Postgres FTS over OUR OWN teaching —
  1,011 sources → 1,272 chunks live), StoryService (member_story = SQL facts + AI
  narrative; nightly), LettersService (the Sunday Letter — weekly, idempotent per
  user+week, notifications template sunday_letter), /me/letters* + /me/ai consent
  covenant + /admin/intelligence/* triggers (Admin+).
- Nuru assistant is STORY-AWARE: narrative + facts line + top-3 own-teaching chunks
  with bracketed citations + crisis guardrails (Befrienders Kenya, discipler-first).
- AnthropicProvider (Claude) preferred with tiers fast/standard/deep
  (haiku/sonnet/opus, env-overridable); Groq/Gemini/Fake fallbacks. Prod currently
  runs on the existing GROQ key until ANTHROPIC_API_KEY lands.
- Crons: nightly reindex+story rebuild (03:00 EAT); Sunday Letters 16:00 EAT.
- Covenant: prayer BODIES never reach a model (counts only); opt-out stops
  written-content grounding, deletes the story, pauses letters.
- LIVE PROOF: Moses's first letter written on prod (Exodus 3:14; names his actual
  "God & His Nature" module, his upward trend, his prayer rhythm; points to his
  discipler). 8 letters written for active members (week 2026-07-05).

**iOS (build 42) + Android (APK 2.9.0 vc29):** Home "A letter was written for you"
gold knock (unread only) → stationery reader (navy backdrop, cream paper, gold wax
seal, scripture pill, serif body, share; marks read on open). Profile "NURU
INTELLIGENCE" consent toggle with the covenant copy. Letters/consent API on both.

**Install state:** APK 2.9.0 on the Desktop. iOS 42 built; Pastor's iPhone hit the
developer-disk-image mount error (locked/asleep) and David's went offline — both
take build 42 on next reconnect (build 41 still runs fine meanwhile; the letter
knock is server-driven and appears as soon as 42 lands).

**Ops notes:** admin triggers can be fired by minting a 15-min SuperAdmin JWT
inside pathway-api-1 (node --input-type=commonjs, sign with JWT_SIGNING_KEY —
NOT "JWT_SECRET"; container workdir /repo/packages/backend; strip \r from exec
output before using in curl headers).

**Blast radius:** backend + iOS + Android. Portal/iPad = N/A this phase (Phase 2
Flock Brief will land there).

---

## Session 14 — AI PHASE 2: the Shepherd's Pulse (LIVE)

**Backend (pathway#357, deployed + migration 147):** signals engine — drift_risk
(deterministic vs the member's own baseline), emotion (tier-fast AI over ≤36h of
CONSENTED writing; tone + one line, never text), crisis (keyword prefilter + AI
confirm, FAIL-SAFE; care-flag push to cell leaders + multiplier). Weekly Flock
Brief per leader (celebrate/watch/reach-out-first + suggested opener), idempotent
per leader+week. dailyGreeting now reads the freshest 48h heart signal. Leader-
scoped routes (§5.4: leader_assignments ∪ relationship_tree) + Admin triggers.
Crons: scan 03:30 EAT nightly; briefs Saturday 19:00 EAT. 6 new tests; suite green.

**Portal:** new Flock Brief page (stationery brief + live signals table, severity
chips, Ack, Admin run-now) under Intelligence nav. Deployed.

**LIVE PROOF (first prod run):** drift caught "Moses Mwicigi has gone quiet —
active ~3.0 days/week before, 0 in the last 7" (true — he's been building, not
using the app); emotion read "Priscilla Wawira sounds hopeful: she feels God's
presence" from a real reflection. 0 crises. **Flock Briefs pending activation:**
leader_assignments is empty in prod — assign cell leaders in the portal and
Saturday's cron (or the Run button) writes each leader's brief.

**Blast radius:** backend + web portal. Member apps = N/A (member-facing effect
is the emotion-aware morning greeting, server-side). iPad Flock Brief screen =
deferred to next iPad iteration.

## Session 15 — AI PHASE 3: the living curriculum (LIVE)
**Date:** 2026-07-11 · **PRs:** pathway#358 (backend, migration 148) + pathway#359
(CI fix: down sections for migrations 146–148 — the reversibility gate had been
red on main since Phase 2) · iOS#53 (build 43) · Android#18 (2.10.0, vc30)

**Backend (deployed, migration 148 applied):**
- `GET /v1/modules/:id/explain?style=simple|swahili|story` — the SAME lesson
  re-rendered by Nuru; cached per (module, style) in `module_explanations`
  (content, not personal data — generated once, served to everyone). §1.9-gated
  via assertUnlocked.
- `POST /v1/modules/:id/quiz/remediation` — "Review with Nuru": composed from
  the member's latest FAILED attempt's exact misses (quiz_attempt_answers
  is_correct=FALSE, ≤8), cached per attempt in `quiz_remediations`; teaches the
  concepts, never dumps the answer key. VALIDATION_FAILED with no failed attempt.
- `GET /v1/growth/memory-verses/due` — verse spaced repetition (no new schema):
  <50% → 1d, <80% → 3d, ≥80% → 7d, mastered → 21d; weakest+stalest first.
- Home next-action v2 (server-driven, zero client change): quiz_retry (62),
  verse_review (58), gentle_return (84 on a ≤3d drift_risk signal + quiet day).

**iOS (build 43):** QuizFailScreen gains gold "Review with Nuru" (sparkles) →
NuruCoachSheet (stationery over navy, retry action). Reader header gains ✨ →
confirmationDialog (Simple English / Kwa Kiswahili / As a story) → ExplainSheet
with in-sheet style chips. New MemberAPI+Learning.swift.

**Android (2.10.0):** same feature pair — FailResult gold "Review with Nuru"
button → NuruCoachDialog; ModuleScreen header AutoAwesome button → ExplainDialog
(style chips inside; no pre-menu — jumps straight to Simple). New endpoints in
MemberApi + PathwayDtos; moduleId threaded QuizScreen→FailResult (level exams
pass null — remediation is per-module).

**LIVE PROOF:** prod smoke rendered Level 1 · Module 1 in Kiswahili ("Mungu na
Asili Yake… 'Nimi Ni Yeye Aliye' (Kutoka 3:14)") and verses/due returned Moses'
real due verse (Romans 12:2). Runs on the Groq/Llama fallback until
ANTHROPIC_API_KEY lands in /opt/pathway/.env.

**Parity deltas:** iOS has a pre-menu (confirmationDialog) before the explain
sheet; Android opens the dialog directly with chips — same capability. iPad
portal untouched (member-facing feature). Portal N/A.

## Session 16 — AI PHASE 4: the liturgy Home + community intelligence
**Date:** 2026-07-11 · **PRs:** pathway#360 (backend, migration 149) · iOS#54
(build 44) · Android#19 (2.11.0, vc31)

**Backend (migration 149: liturgies, community_moments, moment_blessings,
prayer_nudges):**
- `GET /v1/home/liturgy` — the prayer line for RIGHT NOW: part by EAT clock
  (morning 04–11 / midday 11–16 / evening 16–21 / night), church season by
  deterministic computus (Meeus/Jones/Butcher Easter; advent/christmas/lent/
  easter/ordinary), Sunday flag. AI composes all four lines ONCE per
  congregation per day (strict JSON, one call); model down → fixed fallback
  served and never cached. Cron 01:00 UTC (=04:00 EAT).
- Moments: nightly 00:45 UTC scan detects milestones straight from data
  (module_progress, certificates, memory_verse_progress mastered,
  reading_plan_progress completed) → community_moments, idempotent
  UNIQUE(user,kind,ref). NO AI — titles deterministic. `GET /v1/community/
  moments` (cong-scoped, 14d, blessing counts + my_blessing).
- Blessings: `POST .../moments/{id}/bless` kind=amen|heart|fire, one per
  member per moment (repeat switches, xmax=0 detects first), first bless
  push-notifies the celebrated member. 403 FORBIDDEN_SCOPE cross-cong.
- Prayer chains: */15 cron invites ≤3 covenant pray-ers per new wall post
  (cell-mates ranked first, never the author), prayer_nudges = replay-safe,
  LIMIT subtracts already-nudged so the cap holds across rescans.

**Clients (identical placement):** LiturgyCard on Home right after the letter
knock (navy card, gold "PART · SEASON" kicker, scripture pill, serif line);
CelebrationsRail after the prayer wall (horizontal cards: avatar + first name +
serif title + 🙌 ❤️ 🔥 chips, optimistic updates, gold ring on mine). iOS
LiturgyCards.swift + MemberAPI+Liturgy.swift; Android LiturgyCards.kt + DTOs
in HomeDtos.kt.

**⚠️ CRITICAL MIGRATION LESSON (fixed in #360):** node-pg-migrate SQL files
need BOTH `-- Up Migration` AND `-- Down Migration` markers. With ONLY the
down marker (what pathway#359 added to 146–148), upSql = THE WHOLE FILE —
fresh DBs created the intelligence tables then immediately ran the drops.
Prod was safe (rows already in pgmigrations) but tests/CI/fresh envs silently
lost every intelligence table. #360 adds the Up headers to 146–149. RULE: any
new migration with a Down section MUST carry the `-- Up Migration` header.

**Parity deltas:** none — same cards, same slots, same behavior. Portal/iPad
N/A (member-facing).

## Session 17 — Location-first onboarding + pairing by city/country (10–50 km)
**Date:** 2026-07-11 · **PRs:** pathway#361 (backend+portal, deployed, no
migration) · iOS#56 (build 45) · Android#20 (2.12.0, vc32)

**Product decision (recorded):** the ask was "grant location at install,
automatically." Platforms make pre-granted permission impossible (OS-enforced
runtime prompt) and DPA/store policy require a real choice — so the shipped
design maximizes legitimate opt-in instead: a ONE-TIME warm invitation right
after first login ("Be found by your church family") → OS prompt → coarse fix
→ POST /me/location; thereafter geotags refresh SILENTLY on every app open
(no member action ever again). Settings keeps the withdraw switch. Server
still stores ONLY geohash6 (~1.2 km); coordinates discarded on ingest.

**Backend/portal:** GET /admin/members/proximity gains group_by=radius|city|
country. City = geohash center snapped to nearest gazetteer town ≤40 km
(towns.ts: Kenya-dense + EA capitals + diaspora hubs — offline, deterministic,
no geocoding service). Country = congregation's country (countries.name).
Radius default 3→10 km, portal chips 10/20/30/50 (cap 50), Distance|City|
Country segmented control. Tests 16/16 (new: Nairobi/Thika city snap, KE/UG
country split). LIVE PROOF: prod grouped Jackline Njeri + Moses Nganga under
"Kitengela" and all 4 opted-in members under "Kenya".

**Clients (identical):** LocationInviteSheet/-Dialog (navy card, gold CTA,
"~1 km area" honesty line) fired once post-login from RootView/MainShell;
silent per-open refresh honors OS-level revocation. iOS LocationInvite.swift;
Android LocationInvite.kt + AppPrefs.locationInviteShown.

**Gotchas:** Android `by mutableStateOf` in MainShell needed the
androidx.compose.runtime.setValue import; /usr/libexec/java_home has NO JDK
registered on this Mac — use JAVA_HOME=/opt/homebrew/opt/openjdk@17 directly.

## Session 17b — Android field fixes from Jackline's device (2.13.0)
**Date:** 2026-07-11 · **PR:** android#21 (vc33)
- **Give → Custom**: the chip silently did `amountMajor += 500` (no editor).
  Now opens CustomAmountDialog — numeric keyboard, KSh prefix, digits-only,
  bounds 1..2,000,000, "Set amount" confirm. iOS already had an editor.
- **Lesson reader tables**: GFM pipe tables rendered as raw `|` text (seen on
  L1 "Christian Living & Character" §1.3). parseMarkdown gains a table branch
  (header row + `|---|` separator detection) + Md.Table renderer: bordered
  14dp-radius table, bold header on ML.surface, hairline row dividers,
  weight(1f) wrapping cells, ragged rows padded to header width.
- **Content reflow (both apps, no rebuild):** Level 1 lesson bodies carried
  `\n\n` paragraph breaks MID-SENTENCE (docx→text import artifact — verified
  at byte level in prod). Conservative joiner (merge only lowercase-starting
  blocks or orphaned `(citation)` blocks; `N)` markers get their own
  paragraph; structure untouched) applied to prod DB (8/11 modules) +
  canonical seed (pathway#362); RAG reindexed 1,011→1,273 chunks. Apps render
  from the DB, so text flows on next module open — no client change.
- **Modules 1–6 structured to the M7 house style (pathway#363):** six parallel
  reformat passes (headings/#-title/key-verse blockquote/Topic Outline/
  attributed scripture blockquotes/lists), each verified verbatim by
  word-stream diff. Prod DB updated (H2 counts M1:9 M2:8 M3:12 M4:14 M5:16
  M6:11), RAG reindexed (1,275 chunks), canonical seed aligned. Content-only —
  no app rebuild.

## Session 17c — Trail nodes keep the module number (iOS 46 / Android 2.14.0)
**PRs:** ios#57 · android#22. Field feedback: the ✓ replaced the module number
after completion (and ▶ replaced it on the next module) — members lost their
place. New treatment on BOTH apps: the number NEVER leaves the 36-unit
medallion; state moved to the fill + a 15-unit corner seal (navy check-seal =
done, lock-seal = locked, navy ring = next). iOS 46 installed on Pastor's
phone; Jackline offline (has 45, takes 46 next connect); APK 2.14.0 on
Desktop.
- **2.15.0 (vc35, android#23): lesson tables to iOS parity** — horizontal
  scroller (108dp min columns), gold 14% header band, hairline row+column
  dividers, cream ground, 14dp rounding, 12dp cell padding — mirrors iOS
  MLTableBlock. (Screenshot 1 in the report was 2.12.0, pre-table-support;
  2.13.0 rendered tables but flat.)

## Session 17d — "Hear it another way" 404 root-cause + reader space (iOS 48 · Android code-only)
**PRs:** ios#59 · android#25. Field report: explain sheet always failed +
endless spinner; reader bottom gate ate the screen and hid the reflection.
- **ROOT CAUSE (iOS only):** `get("modules/X/explain?style=Y")` — the query
  was inside the path, so appendingPathComponent percent-encoded the `?` →
  `/explain%3Fstyle=…` → 404. The AI itself is FREE and healthy (Groq
  composed simple + story for L1M1 instantly when hit correctly). Fix: pass
  query via the client's query dict. RULE: never embed `?` in APIClient paths.
- Explain sheet/dialog: spinner only while loading; failures show message +
  "Try again" (Android uses a retryTick to re-arm the LaunchedEffect).
- Bottom gate BOTH apps: segmented bar dropped (step chips carry state);
  done state collapses to one line + CTA (~half height); iOS adds extra
  clearance above the gate for the reflection card.
- Build 48 verified ON-DEVICE (devicectl info apps → 48) and launched.

## Session 17e — Completed-module reading room (pathway#364 · iOS 49 · Android code-only)
Field design request: once finished, the reader should stop gating.
- Backend (deployed): GET /modules/{id} + completed / completed_at /
  best_score (per-user, fetched OUTSIDE the shared content cache).
- Both apps: finished modules drop the bottom gate entirely (full-height
  reading); header Read|Reflect → ✓ COMPLETED · score% · d MMM yyyy · HH:mm
  ribbon + navy Retake pill (quizzed modules); reflection folds to the saved
  words (read-only, Saved) with an Edit chip that unfolds the editor.
- iOS 49 verified on-device; Android in #26, rides the next requested APK.
- Deploy note: GHCR pull "denied" recurred → docker logout ghcr.io then pull.
- **17f — sealed module + lower CTA (ios#61 build 50 · android#27, code-only):**
  Start-the-quiz bar 24pt lower on iOS (tab-bar clearance shelf); completed
  modules SEALED on both apps — folded reflection loses the Edit chip, one
  quiet "Revisit this module" button opens the intentional dialog (Edit my
  reflection / Retake the quiz). ALSO: the #60 squash DROPPED a commit (struct
  defs) leaving iOS main uncompilable — restored in #61; lesson: after
  push-then-merge chains, verify main compiles (grep the symbol) before moving on.

## Session 18 — Companion Wave 1: ECHOES (pathway#365 · iOS 51 · Android code-only)
**"The app remembers me" — deliberately AI-FREE.** echo_log (migration 150,
PK user+day) + EchoesService, GET /home/echo. Priorities: welcome_back (≥4
quiet days via module_engagement, 7-day cooldown, Psalm 103 — grace never
guilt) → reflection_echo (member's OWN words from 6–10 days ago quoted
verbatim ≤220 chars, each reflection echoes ONCE ever) → anniversary (module
completed exactly 30 days ago). Craft rules IN CODE: one echo/day (stable
across refetches), specificity-or-silence (null when nothing true), nothing
repeats. 5 tests.
**Clients:** HomeEchoCard (verse-tinted card, serif quote behind a gold rule)
under the liturgy on both apps; iOS adds the TEN-MINUTE WHISPER — one soft
navy line slides up after 10 continuous minutes in a lesson (6-line scripture
pool rotating by day-of-year), holds 9s, fades, once per module open, never
blocks touch. Android whisper = next pass.
**LIVE PROOF:** deployed; Moses' echo correctly null (nothing specific true);
4 real members already eligible — Jackline's "I'm praying that Holy Spirit
will open my heart this week…" (6d) meets her on next open. iOS 51 verified
on-device.

## Session 19 — Companion Wave 2: discipler voice notes + cell presence (pathway#366 · iOS #63/build 52 · Android #29 code-only)
**Backend (pathway#366, migration 151, DEPLOYED):** `module_voice_notes` —
ONE note per (module, congregation), Instructor+ records/replaces (upsert),
author-or-Admin deletes; module payload gains `voice_note {author_name,
avatar_url, audio_url, duration_sec}` per the member's congregation (outside
the shared content cache, beside the completion summary); `GET
/community/presence` = cell-mates in module_engagement within 7d, self
excluded, ≤3 first names, congregation fallback when cell-less. 6 tests
(RBAC 403, upsert, cross-congregation invisibility, presence scoping).
Upload rides the EXISTING `POST /me/media/audio` (multipart m4a, 5 MB).
**iOS (#63, build 52):** VoiceNoteCard ("A WORD FROM <NAME>", avatar,
AVPlayer play/pause, gold progress line, verseBg) on the reader's first page
BEFORE video/audio; VoiceNoteLeaderRow + VoiceRecordSheet (AVAudioRecorder
AAC m4a mono, 5-min cap, preview/redo, "Share with your congregation");
CellPresenceLine on the Pathway hub. NSMicrophoneUsageDescription added
(both configs). Build 52 device-VERIFIED on Pastor's iPhone + Jackline's
iPhone + the iPad (all three reachable this session).
**Android (#29, code-only per standing rule):** VoiceNote.kt port — same
three composables on the EXISTING VoiceRecorder/VoicePlayer engine + the
existing uploadVoiceNote; ModuleDetail.voiceNote additive; role check via
me().profile.role in {Instructor, Admin, SuperAdmin} (mirrors the server
ladder); Material icons (repo has no Lucide-compose). compileDebugKotlin
green; NO APK — accumulates for the next requested build.
**PARITY NOTE:** Android module screen shows the card via localVoiceNote
state after sharing (no full reload); iOS reloads the module. Same visible
result. Home-echo whisper port + this wave's Android build remain queued
behind "build android".

## Session 20 — Companion Wave 3: footprints + Your Walk (pathway#367 · iOS #64/build 53 · Android #30 code-only)
**Backend (pathway#367, NO migration, DEPLOYED):** `GET /modules/:id/
footprints` — cell-mates (congregation fallback) who completed the module:
≤5 first names + avatars newest-first + window-count total; self excluded,
strangers invisible. `GET /me/walk` — the member's journey as ONE UNION ALL
thread over real tables: began (enrollments.started_at), modules finished,
reflections (140-char excerpt as quote), levels passed (DISTINCT ON first
passing attempt), certificates (revoked excluded), verses mastered
(best_match_pct), plans completed, badges; newest-first LIMIT 80. 4 tests.
**LIVE PROOF:** Moses' prod walk returns 6 real events (3 verses, First
Steps badge, "God & His Nature", began 25 Jun).
**iOS (#64, build 53):** FootprintsStrip (overlapping avatars capsule) at
the reader's first page ABOVE the Wave-2 voice note; YourWalkView — navy
hero ("Look how far He has brought you"), gold thread, milestone nodes
(began/level/certificate) filled gold, reflection quotes in Fraunces
italic; PathwayWalkRow ("EVERY STEP, REMEMBERED") after DisciplershipRow;
PathwayRoute.walk. GOTCHA: adding an enum case broke exhaustive switch in
GrowView's PathwayRoute destination too — patch EVERY switch. Build 53
device-verified on Pastor's iPhone + Jackline's + iPad.
**Android (#30, code-only):** YourWalkScreen.kt port (Material icons:
Flag/MenuBook/Edit/School/Verified/FormatQuote/Bookmark/EmojiEvents; NuruType
has NO pageTitle/serifBody — use title/rowTitle.copy) + FootprintsStrip;
WalkRow on hub (new onOpenWalk param) + route "your-walk" in MainShell;
DTO dateLine regex. compileDebugKotlin green; NO APK (accumulates #22–#30).

## Session 21 — Leadership intelligence: the cockpit learns the waves (pathway#368 · portal DEPLOYED · iPad synced/staged)
**Backend (pathway#368, no migration, DEPLOYED):** `/admin/analytics/
intelligence` gains `formation`: totals (verses mastered/learning, plans,
badges, all-time reading hours, readers_7d), 8-week weekly series
(modules/reflections/verses/readers via generate_series), reflection stats
+ 8 latest excerpts (≥25 chars), companion counters 7d (echoes,
welcome_backs, moments, blessings), voice-note coverage per congregation
(pct of published modules), cells-reading-together (7d readers/members),
level-exam pass rates. Plus `GET /admin/members/:id/walk` (perm
members:view) reusing Wave-3 WalkService. 4 tests.
**LIVE PROOF (prod):** readers_7d=30, 33.8 reading hours, 44 verses
mastered, 13 badges; last week: 24 reflections + 33 verses + 30 readers;
21 blessings 7d; Upperroom Media cell 9/10 reading. Voice notes [] (none
recorded yet — table shows the coverage gap itself).
**Portal (DEPLOYED, index-DLZO2GVe.js):** Member Intelligence + "Formation
& companion" section (6-KPI strip, 4-series area chart, Voices of the
flock excerpts, cells-reading progress bars, voice-note coverage table,
exams BarList); Member Profile + "Their walk" gold-dot timeline
(reflection TEXT omitted on that page per its §5.4 stance; excerpts live
only in the Admin-gated cockpit, mirroring Reflection Queue access).
**iPad (pathwayforipad d0e84c7):** its admin-web src was a stale #314-era
mirror with ZERO unique files (verified) — wholesale src+shared rsync from
pathway @efe5a97, ipad:build + cap sync green, Release App.app built; iPad
went OFFLINE before install (was reachable earlier for member build 53) —
staged at /tmp/portal-dd, rerun command in the sync commit message.

## Session 22 — REVERSE parity: Android→iOS backport (ios#65 · build 54)
User: "some Android changes are not on iPhone — celebrate family, welcome
back card etc." Two-agent deep audit (screens/Home cards + API/DTO
tolerance), Android→iOS direction. FINDING: iOS Home already HAD every
Android card incl. CelebrationsRail + HomeEchoCard (wired in feedSections,
verified line-by-line; live prod moments JSON decodes with the exact iOS
structs). The REAL differences:
**(1) Chat voice messages — the one true feature gap.** iOS mic was a dead
placeholder, received notes an inert "Voice note" pill. Ported: ChatVoice
.swift (recorder w/ 10 Hz metering→live wave, waveformFor(64), 5-min cap;
one-at-a-time player; WaveformBars w/ played-fraction fill;
VoiceMessageBubble), composer recording strip (pulsing dot/wave/clock/
cancel/send), send = existing uploadVoiceAudio → POST message w/
{duration, waveform} meta; ChatMessage.attachmentMeta; inbox preview
"Voice message · 0:42" via new lastDuration.
**(2) Decode fragility — why Android shows cards iOS drops.** Android DTOs
default EVERY field; iOS structs were strict and every feed fetch is try?
— ONE sparse field silently blanks a whole card. Hardened tolerant
decoders: CommunityMoment, HomeLiturgy, HomeEcho, RhythmToday,
Achievements.Streak, GrowthScore, ScoresSummary.Overall, VerseReactions,
PrayerWallPost(+audio_waveform), ChatMessage, PathwaySummary/Level,
PlanDayReflection; CONFIRMED BUG: null occurs_at sank the whole /me/rsvps
list (Android had made it nullable; iOS hadn't). GOTCHA: custom
init(from:) suppresses memberwise inits — re-add them where optimistic UI
constructs models (ChatMessage pending, RhythmToday default).
**(3) Device census**: iOS now POSTs me/devices (platform/appVersion/
model; push_token awaits APNs/paid program) — iPhones finally appear in
Member Intelligence device analytics.
**(4)** Home "View all" → real Announcements list (AnnouncementsAllView +
AppRoute.announcementsList), was the generic inbox.
NOT ported (deliberate): FirebaseAccountScreen (FCM plumbing). Statement/
receipt PDFs stay native-rendered on iOS (divergent by design).
Build 54 verified on Pastor's + Jackline's iPhones; iPad offline (owes 54
+ the staged Nuru Portal build from Session 21).

## Session 23 — iOS SUPER-SMOOTH pass (ios#66 · build 55 · pathway#369 deployed)
User: "iOS forgives nothing — make me love the iPhone version." 3 audit
agents + EMPIRICAL simulator run (local backend :8080, seeded dev DB,
computer-use drove login as student1@dev.local).
**HEADLINE (root cause of every 'missing card' report):** HomeLiturgyCard,
HomeEchoCard, CelebrationsRail, FootprintsStrip, CellPresenceLine were
DEAD-BY-CONSTRUCTION on iOS — `Group { if let … }` renders EmptyView until
data arrives and `.task` attached to an uninstalled view NEVER FIRES.
Request census proved /home/liturgy, /home/echo, /community/moments were
never called. Fix: zero-height Color.clear install-anchor in the empty
branch. Re-proven live: liturgy card + Celebrate-the-family render on Home.
SWIFT RULE: never hang .task off a conditionally-empty Group.
**Voice engine (16 defects from adversarial review, all fixed):** 5-min
autostop kept-not-discarded (ready-to-send strip); send failure = retained
file + tap-to-retry (was silent loss); playback observes didPlayToEnd +
asset duration (meta-less notes played exactly 1s; wedged 'playing');
interruption observers both recorders; duration from recorder.currentTime
(tick-counting undercounted while scrolling — timers now .common mode);
threadShared.stop() on thread leave + before recording (was: audio playing
over Home forever + mic recording the other note); hold-counted
AVAudioSession release (playAndRecord lingered app-wide); guard record();
mic-denied hint; waveformFor ceil-chunking (tail of 6-13s takes dropped);
sheet swipe-dismiss cleanup (1Hz timer leaked for app lifetime); preview
delegate; Redo stops preview.
**Tolerance completed: 110 structs** (chat inbox/thread, events, giving,
gifts, growth, quiz/exam, discipleship, radio, profile) — extension-init
pattern preserves memberwise inits; gates fail CLOSED (locked ?? true,
§1.9). **Feel:** Quiz+Module error traps get Go-back; Add-to-calendar
ships a real .ics; Delete-account answers honestly; liturgy/echo/
celebrations refetch on foreground (keep-alive tab shell showed
yesterday's card forever); levels=[] → retry not 'Level 1 of 0';
walk/announcements offline ≠ empty; AuthStore never nil-overwrites.
**Backend (pathway#369 DEPLOYED, migration 152):** client_devices was
plain-INSERT per launch — deduped to one row per (user, platform,
COALESCE(model,'')) + upsert; census now honest (53 rows).
Build 55 verified on Pastor's + Jackline's iPhones; iPad offline (owes 55
+ staged portal).

## Session 24 — the signature-feel pass (ios#67/build 56 · android#31 code-only · iPad portal INSTALLED)
UX thesis executed: iPhone = signature moments + motion grammar; Android =
perceived-performance craft (its two cheap-feel tells: bare spinners, zero
pull-to-refresh).
**iOS (#67, build 56):** "Day sealed · well walked" — third rhythm item
completing plays success haptic + one-shot gold radial sweep + persistent
caption (witnessed transitions only; all-done first loads stay silent);
numericText rolling digits on streak/bless counts; SCRUBBABLE voice
waveforms (drag→frame-accurate seek, ticker yields to the finger,
drag-on-silent plays-then-seeks); once-per-session staggered Home entrance
(8 cards, 40ms, static flag survives the .id(textScale) rebuild; skeleton
never plays it). Tab bar already had the intended haptic+spring — left
alone. ALL motion gated on accessibilityReduceMotion.
**Android (#31, code-only):** Skeleton.kt shimmer system (HomeSkeleton,
ListSkeleton) replacing spinners on Home/Pathway/Chat/Events/PrayerWall;
NuruRefreshBox (material3 1.3.0 PullToRefreshBox, gold) on all five +
AsyncContent in-place reloads (kills the full-screen flash on every chat
send/reaction — pre-existing wart); pressScale() physics (pointerInput
watcher, sits before clip/background) on hero/verse/give/module rows/hub
rows/bless chips; house-Haptics voice (bless tap, send tap, voice-send
confirm); staggered entrance (process flag, graphicsLayer-only).
**Devices:** Pastor's iPhone build 56 ✓; iPad CAME BACK — member build 56
✓ AND the staged Nuru Portal wrap (leadership intelligence cockpit)
INSTALLED ✓ (Session 21 debt cleared). Jackline offline this round (on
55; takes 56 when reachable). Android APK still parked (#22–#31 pending).

## Session 25 — Analytics A+B+C SHIPPED (pathway#370 · migrations 153+154 · portal+iPad live)
Executes the approved decision report in full. **A:** device_tiers lookup
(~40 ILIKE patterns, longest wins, unmatched='unclassified'; ETHICS:
aggregate capacity proxy only) + economics block (tiers, giving_by_tier
via highest-tier-per-member DISTINCT ON, payday day-of-month buckets,
provider split). **B:** auth_events (migration 154) + fire-and-forget
INSERT at issueSession = true login telemetry; notification effectiveness
derived from existing notifications.read_at (sent/read/median-min).
**C (server-side):** reachout.untouched — zero inbound blessings/prayers
30d visit list (pw.author_user_id NOT user_id — gotcha); radio reach.
C client heartbeats (radio minutes, network sampling) RIDE THE NEXT APP
BUILDS — not yet in clients.
**Portal:** "Congregational economics" (tier donut NAVY/GOLD/TEAL, giving-
by-capacity + widow's-mite line, payday BarChart, providers) + "Retention
& reachout" (login KPIs w/ capturing-from-today, cohort bars, care list,
radio); span-8 grid added. Deployed (index-Cx3wCZku.js); iPad wrap
re-synced + INSTALLED.
**FIRST LIVE READ (prod):** tiers 7 premium/14 mid/29 entry/1 unclassified;
giving: premium 5 givers KES 20,640 of 16 gifts vs mid 1×KES 200 vs entry
3×KES 3,200 — capacity-giving correlation REAL; 100% M-Pesa; payday spread
surprisingly flat (16-20th heaviest by count); cohorts Jun 16→11 active
(69%), Jul 41→22 (54%); notifications 163 sent/25 read (15%!, median 13.9h
— a leadership finding); 12 untouched members; radio 18 weekly listeners.
TEST GOTCHA: resetDb truncates migration-seeded lookups — tests must
re-seed device_tiers rows.

## Session 26 — C-layer CLOSED (pathway#371 deployed mig 155 · ios#68/build 57 · android#32 code-only)
The last gap of the A+B+C program. **Backend:** radio_listeners
.listen_seconds accumulated by the EXISTING 20s player heartbeat
(elapsed-since-last-beat LEAST-capped 60s — resumed sessions can't book
the gap); client_devices.network via me/devices (COALESCE keeps last
known when clients omit); economics.connectivity split + radio
minutes_all_time in the cockpit. KEY INSIGHT: radio minutes needed ZERO
client work — the heartbeat was already there. **iOS (#68, build 57 on
Pastor's phone):** one-shot NWPathMonitor sample (1s cap, Task.detached
off the login path) → network on registration. **Android (#32):** same
via ConnectivityManager (ACCESS_NETWORK_STATE added to manifest) + THE
TEN-MINUTE WHISPER PORTED (600s → one of the six iOS lines, stable per
module via floorMod hash, 9s fade, Fraunces italic on verse-bg) — the
last iOS-only member feature. Android APK debt now #31-#32 since 2.16.0.
Jackline (55) + iPad (56) offline this round — profiles all fresh from
this week, they catch up on next reachability. TEST GOTCHAS: radio_programs
uses id not program_id; category CHECK is capitalized ('Sermon').

## Session 27 — Home cards fusing (ios#69 · build 58)
Owner screenshot: ON AIR/liturgy/echo cards fused (~3pt gaps). ROOT CAUSE:
the staggered-entrance 12pt rise (Session 24) could strand on rows after a
scheduling race — measured gap 20−12=8pt, the confession number. FIX:
1.8s after the rise, feedStaged flips false inside a disablesAnimations
Transaction → all rows' offset/opacity become plain zeros, nothing left
to mis-evaluate. PIXEL-verified (PIL scan at card-edge x=60: all gaps
≥17.3pt page fill, none fused). Android immune — its entrance is
graphicsLayer-only (no layout participation). DEBUG LESSON: eyeballing
screenshot gaps misleads (ink text classifies as card fill) — scan card
EDGES with exact fill colors. Build 58 → iPad ✓; Pastor+Jackline pending
reachability.

## Session 28 — fusion, second strike → structural fix (ios#70 · build 60)
The retire patch (S27) didn't hold on device (radio+liturgy fused again).
Mechanism REMOVED instead of re-patched: (1) entrance is OPACITY-ONLY —
the 12pt rise painted rows off their layout slot and two distinct races
could strand it; fades can't move geometry, fusion now impossible by
construction. (2) Feed rows keyed by STABLE ids (onair/liturgy/echo/…)
not array offsets — the late-arriving radio bar inserted at index 0 and
rebuilt every row's identity mid-flight (also kills the tear-down/refetch
flash from the S23 audit's #18). LESSON: never animate layout-adjacent
geometry on rows whose membership changes async; after two strikes,
delete the mechanism. Build 60: Pastor ✓ iPad ✓, Jackline offline.
Android unaffected in the field; its rise is graphicsLayer-only + Compose
target-state animation (no coalescing race) — left as is, watch it.

## Session 29 — fusion SOLVED: the liturgy glow circle (ios#72 · build 64)
Third strike ended the case. With Moses' Android screenshot as reference
("do like in the android"), pixel-forensics on his post-force-quit iOS
screenshot showed ONLY two dead seams — radio→liturgy and liturgy→echo
at 0pt — while echo→reflection and reflection→hero were healthy 20pt.
REPRO AT LAST: seeded the LOCAL backend with a live radio program (the
sim never had one — why every prior fix "passed" in sim: the corrupted
seam was never on screen) + today's echo_log row; sim then rendered his
phone exactly (3pt/3pt/20/20). onGeometryChange instrumentation proved
LAYOUT PERFECT (every row at prev.maxY+20) — the PAINT lied: liturgy
card reported h=116 but painted 150 = exactly its background ZStack's
fixed 150×150 blurred glow Circle. A ZStack sizes to its largest child,
so the background out-grew the card and spilled ±17pt of navy over both
neighbouring gaps. Entrance animation empirically EXONERATED (removed →
identical paint). FIX: gradient owns the size, glow in .overlay (never
inflates), clip at card bounds — GivingStatementView's documented
edge-spill pattern. Audited all .background(ZStack+fixed-frame) sites:
ResourcesLibraryView safe (clips outside), rest clean. After: all four
seams 20.0pt with entrance restored. Android N/A — Compose backgrounds
are size-taking modifiers; its Home was always correct (the reference).
NEW RIG (sim+DEBUG only, zero release impact): NURU_AUTOLOGIN=1 env
auto-submits dev login AND suppresses the notif-permission alert →
fully scripted screenshot verification (simctl launch + PIL seam scan).
Sim gotchas: psql -c multi-statement is ONE transaction (echo seed error
rolled back the radio insert silently); SpringBoard permission alerts
survive app reinstall — only a device reboot clears them; Simulator.app
can run windowless (AppleScript sees 0 windows) — drive via simctl only.
Build 64 built; Pastor's iPhone unavailable at press time — install on
next reachability (Jackline 55, iPad 62 also pending).

## Session 30 — the verse beheld: tableau + selah + share-as-picture (pathway#372 · ios#73 · android#33)
Owner: "the cards are beautiful now — break the wall of text, something to
behold." Verse-of-the-day becomes a PHOTOGRAPH carrying the verse.
BACKEND (pathway#372): GET /me/home/verse now returns art:{url,alt} — a
hand-curated, theme-matched image per (user,day), deterministic; mood-lib
themes draw the union pool. CURATION DISCIPLINE: all 26 candidate URLs
verified live AND every image visually reviewed on a rendered proof sheet
(a "grapes" verse must never land on a plate of vegetables) — first pass
had 3 mismatches (a Bible photo tagged "seedlings" etc.), fixed by eye.
17/17 home tests (4 new). CLIENTS: VerseTableauHeader (216pt, white serif
over a 3-stop scrim, owned+clipped frame per the ios#72 ornament rule) +
SelahDivider (two gold hairlines meeting at a small cross) after rhythm
and after progress — a rest for the eye + share-as-picture: iOS
ImageRenderer @3x, Android Canvas 1080×1332 (both draw art+verse+ref+gold-
cross brand line; text set by us, always crisp); both fall back to text
share on render failure, and to the classic cream card when art is
absent/offline. iOS verified in-sim (Isaiah 30:21 over a starfield;
NURU_UITEST_TOP hoist hook for headless screenshots); Android
compileDebugKotlin clean. DEPLOYED to prod (image 14:06Z, readyz 200;
/me/home/verse returns art — Philippians 4:13 over a sunrise valley).
iOS build 65 installed on Pastor's iPhone (launched) + iPad. GOTCHA: a
scripted python multi-file edit hit the WRONG `val shape =
RoundedCornerShape(20.dp)` (HomeCard shares the literal) and corrupted the
primitive — reverted the file and redid the VerseCard restructure with
targeted Edits; lesson: never sed/python-replace a non-unique anchor in
a large Compose file, use the Edit tool with surrounding context.

## Session 31 — the liturgy hour beheld (pathway#373 · ios#74 · android#34)
Owner: "in the ordinary can we have images change based on time of day —
morning/noon/evening/night — like the verse?" The liturgy card now carries
a PHOTOGRAPH of the hour it names. BACKEND (pathway#373): liturgy.ts gains
LITURGY_ART (4 curated images per part) + pickLiturgyArt(part, dayKey) —
deterministic per (part, EAT day) so the whole congregation shares the
day's tableau and it rotates daily; current() returns art:{url,alt}. Since
`part` IS the clock, the image tracks morning→dawn, midday→bright sky,
evening→golden sunset, night→starfield. 16 images verified live AND
visually reviewed on a per-part proof sheet — the review CAUGHT mislabels
(a sleeping person + a spice board tagged morning; a knife board +
eucalyptus tagged evening; mailboxes tagged midday) and replaced them.
5/5 liturgy tests. CLIENTS: image behind a navy scrim (0.42→0.84) with the
kicker + serif line shadowed for legibility over bright noon skies; owned+
clipped frame (ios#72 ornament rule); falls back to the classic navy card
offline. iOS: litBackground @ViewBuilder in LiturgyCards.swift; Android:
Box + AsyncImage matchParentSize + Shadow in TextStyle. iOS verified in-sim
(evening → golden wheat sunset under "EVENING · ORDINARY", header greeting
"Good evening" agreeing). DEPLOYED to prod (image 14:50Z, /home/liturgy
returns art). iOS build 66 on Pastor's iPhone (launched) + iPad. Android
compileDebugKotlin clean (no APK per build pref).

## Session 32 — Nuru Pathway featured on the liturgy card (ios#75 · android#35)
Owner: "make the nuru pathway featured next to evening ordinary." The
liturgy card's hour kicker now carries the brand lockup right beside it:
"EVENING · ORDINARY  [gold mark] Nuru Pathway ✓" (iOS uses the "N"
BrandMark, Android the "✝" mark — each platform's own brand glyph). Member
surface only, NO backend. First attempt kept the scripture ref on the top
row → both truncated ("EVENING · OR…" / "Psalm 139:23…"); fix = move the
citation to a right-aligned chip UNDER the prayer line so the hour + brand
own the top row. Verified in-sim (evening tableau, full brand, no
truncation). iOS build 67 on iPhone17 (launched) + iPad; Android compiles.

## Session 33 — featured welcome video leads the feed (ios#76 · android#36)
Owner: "move this [Nuru Pathway FEATURED video] after the top/header card."
The welcome video was buried ~7 cards down (below liturgy/echo/hero/rhythm/
plan-resume); it's the "start here" moment, so it now leads the feed — first
card right under the greeting header. The thin ON AIR radio bar stays pinned
ABOVE it while a broadcast is live (never bury the live station); with no
broadcast the video is the first card after the header. Member surface only,
NO backend. iOS build 68 on iPhone17 (launched) + iPad; Android compiles.

## Session 34 — verse of the day leads the feed (ios#77 · android#37)
Owner: "put the verse of the day below the header card." The verse tableau
now leads the feed — first card under the greeting, followed by the featured
welcome video (Session 33). Order: header → ON AIR (live only, pinned) →
VERSE → featured video → liturgy → … Member surface only, NO backend. iOS
build 69 on iPhone17 (launched) + iPad; Android compiles (block relocated
via brace-balanced move, single verse?.let confirmed). NOTE the running
order of top-of-home requests this session: video-to-top (S33) then
verse-to-top (S34) → verse ends up first, video second.

## Session 35 — taller liturgy tableau + shared deep-navy block (ios#78 · android#38)
Owner: on the ordinary/liturgy card, more image height + type at the bottom +
a deep-navy block over the image so fonts read clearly; apply the same block
to the verse. LITURGY: art path is now a 206pt tableau (was content-sized) —
hour + Nuru Pathway brand at TOP, prayer line + citation at the BOTTOM where
the veil is deepest; offline → classic navy card (kicker factored into
litKicker/LitKicker). DEEP-NAVY BLOCK: shared DeepNavyBlock (iOS View) /
DeepNavyBlockBrush (Android) — navy 0x0A1628 at 0.48→0.58→0.92 top→bottom;
replaces the verse tableau's old BLACK scrim so both cards read as one family
(image "a bit hidden", darkest at base). Verified in-sim: verse (Proverbs 2:6
over tree canopy) + liturgy (Lamentations 3:22-23 over morning sunrise), type
crisp. Member surface only, NO backend. iOS build 70 on iPhone17 (launched) +
iPad. Android APK rebuilt → 2.18.1/vc39 (~/Desktop/NuruPlace-2.18.1.apk,
V2-signed CN=Nuru Place, com.nuruplace) so testers get this too.

## Session 36 — verse reactions feel instant (fix; ios#79 · android#39)
Owner: "on iPhone the verse-of-the-day reactions are not working." DIAGNOSIS
(not a backend bug): verified the round trip end-to-end — POST ❤️→🔥 moves +
persists on the LIVE server; path/auth-refresh/decoder all fine. The real
issue was client UX: reactVerse waited on the full server round trip with NO
optimistic feedback, and `try?`/getOrNull swallowed any hiccup into nil —
BLANKING the counts — so a tap read as "nothing happened." FIX: both clients
apply the one-per-day toggle OPTIMISTICALLY (tapped chip highlights + count
moves at once), then reconcile with the server and ROLL BACK to the prior
counts on failure. iOS: VerseReactions gained an explicit init() (custom
init(from:) had suppressed the default). Verified in-sim via a NURU_UITEST_
REACT hook (console: ❤️→🔥 moved+persisted). iOS build 71 on iPhone17
(launched) + iPad.
  GOTCHA (iOS device build): xcodebuild release FAILED "No Accounts / No
profiles for org.nuruplace.member.NuruWidgets" — Xcode's Apple ID session is
signed out, so it can't MINT the embedded widget-extension profile (no cached
one; only member+portal app profiles are cached, member valid to 2026-07-18).
The signing CERT + free team (SGC7566QY6) are still present. WORKAROUND used:
temporarily removed the NuruWidgets embed-build-file + PBXTargetDependency
from a pbxproj COPY, built `-target NuruMember` alone with the cached member
profile (CONFIGURATION_BUILD_DIR=build/apponly), then RESTORED pbxproj (git
clean). Durable fix = user re-adds the Apple ID in Xcode → Settings →
Accounts (or the widget stays parked). devicectl install threw transient
"Connection reset by peer" twice — just retry.

## Session 37 — 30-day non-repeating liturgy art + taller/deeper tableau (pathway#374 · ios#80 · android#40)
Owner: many hour-fitting images so a full month never repeats + stunning
night; taller ordinary card + deeper navy block on both cards. BACKEND
(pathway#374): each part pool 4→30 images (120 total). SOURCING: no Unsplash
API key → pulled real photo IDs from unsplash.com/napi/search JSON via the
in-app Browser (navigate to napi URL, parse document.body.innerText in-page
— fetch() is CSP-blocked but navigation returns JSON; filter !premium&&!plus
for free images.unsplash.com/photo-<id>). Downloaded 136+15 thumbs, built
per-bucket proof sheets, VISUALLY culled to 30/bucket (sunrise queries
return sunsets — eyes required). Night = aurora + Milky Way (owner: "ultra
beautiful realistic"). pickLiturgyArt is now a 30-DAY ROTATION: epochDay
(days since Unix epoch from the YYYY-MM-DD key) + PART_OFFSET {0,7,14,21},
index steps +1/day so 30 days pass before any repeat; replaced the FNV-hash
picker. 6/6 liturgy tests (new: pools≥30 & distinct; 30 consecutive days →
30 distinct). CARDS: liturgy tableau 206→232pt; shared DeepNavyBlock deepens
0.48→0.40 top / 0.92→0.97 base so the image is more hidden where the type
sits at the bottom (applies to verse too). Verified in-sim (morning "Dawn
mist in the valley", type crisp). DEPLOYED prod (image 06:49Z, /home/liturgy
returns the new pool). iOS build 72 on iPhone17 (launched) + iPad; Android
APK rebuilt 2.18.2/vc40 (~/Desktop, V2-signed CN=Nuru Place). REMINDER: iOS
release build still needs the app-only workaround (widget signing blocked,
Xcode Apple ID signed out) — build `-target NuruMember` with widget embed+dep
temporarily stripped from a pbxproj copy, restore after.

## Session 38 — portal Chat ⋮ menu: edit/delete my own, moderate others (2026-07-16)
Owner: "make the chats editable, delete by having three dots and the attached
functions" (screenshot: portal Operations → Chat). PORTAL-ONLY change — the
admin surface is web + iPad, so both repos got a byte-identical sync
(pathway#375, pathwayforipad#3, base feat/macbook-version — `gh pr create`
defaulted the base to main and dragged in 93 unrelated files until retargeted;
ALWAYS check `gh pr view --json baseRefName` when the source branch is not
main). NO backend/OpenAPI change: PATCH+DELETE /chat/messages/:id were already
live, already author-only (`WHERE message_id=$1 AND author_user_id=$2`),
already in openapi.yaml, already covered by backend/test/chat.test.ts:50. The
portal previously showed always-visible Flag/Remove on OTHERS' messages and
nothing at all on your own. Now every row has a hover ⋮ whose contents follow
what the server permits: mine → Edit/Delete; other → Flag|Dismiss + Remove;
removed → Restore. A moderator never rewrites another's words. Edit is inline
(Enter saves, Shift+Enter newline, Esc cancels) + "edited" marker from
is_edited. Delete confirms — the thread filters `deleted_at IS NULL` so it is
gone for everyone (vs moderator Remove = is_hidden, restorable: different verb,
different button). Menu FLIPS downward within 96px of the scroller top, else
`overflow-y-auto` clips it. Verified: typecheck+lint+vite build clean; deployed
to /var/www/pathway-portal and confirmed the LIVE gzipped bundle carries the new
strings (curl --compressed https://pathway.nuruplace.org/assets/index-*.js —
note assets are at /assets/, NOT /portal/assets/, which silently returns the
417-byte SPA fallback and reads as "code missing").

GAP FOUND (not fixed — different surface, not what was asked): neither the iOS
nor the Android member app references editMessage/deleteMessage. Leaders can
now edit/delete their own messages from the portal; a MEMBER still cannot take
back or fix a message from their own phone, though the backend has supported it
all along. I had asserted in pathway#375 that the member apps "already have
their own message menus" — that was wrong and is corrected in a PR comment.
Real work, cheap: MemberAPI.editMessage/deleteMessage + a long-press menu on
own bubbles, iOS + Android.

## Session 39 — plans: a day is earned, and the days are walked in order (2026-07-16)
Owner: "you open page one, it marks that you open... you can just read a little
bit, and then you move out without completing all the three steps and the
reflection... You cannot go to plan two, and then there is a message that
encourages you to do plan one." Screenshots: Day 1 "Completed" in the overview
while ALL THREE parts sat unticked; Day 2 showing "0 of 3 parts read" directly
above a gold "Mark day complete" button.

ROOT CAUSE (not a UI slip — the server did as told): POST /growth/plans/:id/
complete-day set completed_days with NO segment check; it validated only
day_number <= day_count. It self-enrolled, took ANY day in ANY order, and
stamped whole-plan completed_at at cardinality >= day_count — a 10-day plan was
finishable in 10 taps with nothing read. TWO completion paths never reconciled:
completeSegment rolled up honestly (unread==0), complete-day just set the flag;
planDetail ORed them (`segDone || completedDays.has(n)`) which is exactly why a
day read "Completed" with 0 parts done. NO day gating existed anywhere —
current_day is advisory bookkeeping nothing reads to authorize.

BACKEND (pathway#376): completeDay now verifies every part read (409
CONTENT_INCOMPLETE + parts_remaining) AND that the day exists (completed_days is
a bare int[] with no FK — it recorded days that don't exist); day N gated on
1..N-1 (409 GATE_LOCKED + next_day), enforced on completeSegment TOO (the day CTA
is not the only door). planDetail returns locked per day + next_day, and
withholds a locked day's content/video_url on the day AND every segment (§1.9
hard-lock discipline — no client can render past the gate). Gate + badge now read
ONE union so they can't drift. MIGRATION 156 rewrites the lying rows: prod had
38 of 56 day-marks unearned across 11 members; dry-run 56→18, 0 rows gained days
(only ever subtracts; segment-less days keep their mark). 5 new tests, VERIFIED
FAILING against the old service (4/5). 655 pass, 0 regressions.

CLIENTS (ios#81, android#42): "Mark day complete" is GONE. While a part remains
the gold CTA is the way INTO it ("Continue · The Word") via the SAME link the row
uses — it can only carry you into the work, never around it; all parts read →
"Seal the day". Locked rows: lock glyph, alpha 0.55, "opens when today is done",
tap → a warm dialog naming the day you're on and why this one waits (not an
error). DTOs: ReadingPlanDay.locked + ReadingPlanDetail.nextDay, defaulting
unlocked so an older server behaves as before.

ALSO iOS-only (ios#81): Talk it Over composer sat UNDER the keyboard. Avoidance
wasn't absent, it was DEFEATED — the composer was a VStack sibling in a ZStack
whose other child was `PL.cream.ignoresSafeArea()`; a ZStack sizes to its LARGEST
child, so the keyboard-ignoring Color inflated the stack full-screen and the
VStack laid out to the screen bottom. SAME ornament lesson as the Home fusion
(ios#72). Fixed to ChatThreadView's proven shape: .background(cream
.ignoresSafeArea()) + composer in the ScrollView's .safeAreaInset(edge:.bottom).
Android's PlanDayScreen already uses .imePadding() — not affected.

OPS TRAPS: the prod PG container is `pathway-postgres-1` (NOT pathway-db-1);
psql heredocs over ssh mangle '{}'::int[] — pipe a .sql file via `docker exec -i`
instead. Sim UI automation is unavailable (computer-use screenshot needs macOS
Screen Recording permission; no idb) — `xcrun simctl io <udid> screenshot` still
works for capture, and the sim's UDID is NOT the phone's.

## Session 40 — Broadcast: identity, the SuperAdmin gate, the password (2026-07-16)
Owner: a Broadcast "tab" for SuperAdmin; replies come home; invite per-thread;
"you need to enter the password to see / access the broadcast content"; and the
bug report "I see it delivered to 40 people instead of 60".

THE THING I GOT WRONG, TWICE: I told the owner the Broadcast tab "doesn't exist
on any client". It has shipped on iOS since 96f25e6 (2 July) — segment, staff
gate, composer. His "40 instead of 60" was not him reading my report; he had SENT
a broadcast from that tab. ALWAYS grep the clients before claiming a feature is
unbuilt; the backend having no consumer is not the same as the client having no
screen.

THE 40: prod = 60 members, 19 of them filed under NO congregation, minus the
sender = 40 exactly. Cause was `z.enum([...]).default("congregation")` — a Zod
default is applied BEFORE the service sees the request, so "didn't say" and "said
congregation" arrive identical and cannot be told apart. Field is now optional;
the service resolves it (SuperAdmin unasked → the whole church).

BACKEND (pathway#377): chat_broadcasts parent row + chat_messages.broadcast_id
(a broadcast had no identity — the copies were indistinguishable from hand-typed
DMs, so nothing could be asked of one). Responses need no table: any message in a
stamped copy's conversation, by the recipient, after it landed. SuperAdmin ONLY
(Instructor+ → Admin+ → SuperAdmin over three corrections). Broadcast threads
shielded from Admin oversight; invite is per-THREAD and the member is TOLD.
Password step-up (pwd_at claim + /auth/confirm-password), carrying any MFA stamp
across. Ticks: delivered (a fact — written server-side in one tx) vs seen (the
existing read receipt). List opens on the last 4 + total. Fixed a latent replay
bug: it returned a RECOUNT of today's membership, not what landed. 48 chat tests.

iOS (ios#82, build 75): APIError.http gained `details` — FORBIDDEN_SCOPE alone
cannot tell "confirm your password" from "you may not". setAccessToken (the
obvious setSession(access:refresh:nil) would have WIPED the refresh token).
confirmPassword bypasses send() (a 401 there auto-refreshes and REPLAYS the wrong
password → two lockout attempts per typo). Draft + client_mutation_id survive the
prompt, so confirm resumes THAT send. Gate narrowed `role != "Student"` →
superadmin. NOTE: adding `details` broke SIX pattern matches across five screens,
and Swift reported them as "failed to produce diagnostic; please submit a bug
report" — NOT as the arity error they were. Budget a build per site.

NEAR-MISS, the worst of the day: `git add packages/backend` swept two untracked
files into #377 — including migrations/1758000000074_seed-plans-john-sermon-
psalms.sql, an unreviewed 215-line seed that opens with DELETE FROM
reading_plan_days. It had NEVER run; node-pg-migrate runs anything absent from
pgmigrations REGARDLESS of number, so being stamped 074 while prod sat at 157
protected nothing — the next deploy would have executed it, cascading through
segments into members' reading_plan_segment_progress (the very data mig 156 had
just made honest). Also collides with the existing 1758000000074_mfa-recovery-
codes prefix. Caught it in the merge's file list, reverted off main BEFORE
deploying, and verified by (a) `find` inside the built image and (b) count of
reading_plan_days = 264 after migrate. LESSON: never `git add <dir>` — add
explicit paths, and read the merge's file list.

VERIFIED LIVE ON PROD: session alone → 403 + details.password_required (both read
and send); pwd_at token → 200; Admin + pwd_at → 403 "Insufficient role"; mig 157
applied; chat_broadcasts exists; 264 plan days intact.

ANDROID: none of the broadcast work exists here — no segment, no composer, no
models. Whole feature outstanding.

## 2026-07-17 — Overnight hardening (iOS PR #85 · Android PR #44)
Same bug found independently on both platforms: the AI-consent toggle (and on
Android three more write flows) claimed success before the server answered.
Both now revert + surface failure — parity by symptom, not by port. Android
additionally: offline-queue idempotency pinned by 3 tests, lint 7 errors → 0.
iOS additionally: force-unwraps/try! purged from user paths, 14 dead symbols
removed (incl. LevelGating.swift — server's .locked fields are the real §1.9
enforcement), first XCTest target with 10 decoding-contract tests.

## 2026-07-17 — Broadcast member parity lands on Android (PR: feat/broadcast-member-parity)
Closes the "whole feature outstanding" gap for the member side + sender basics:
broadcast_id on ChatMessage; Talk with Pastor dressing + gold "Only <pastor>
sees your reply" ribbon; segment chips count unread (vanish at 0, reset on
return); Broadcast tab SuperAdmin-only; send handles 403 password_required via
/auth/confirm-password (same client_mutation_id on retry — idempotent).
STILL iOS-only: the biometric lock (Keychain/Face ID), the last-4 sent list
with ticks/responses detail, and the sent-card. Android sender = composer only.

## 2026-07-17 — Broadcast console lands on portal + iPad (pathway#379 · pathwayforipad#4)
The sender's console now exists on all three SuperAdmin surfaces: iOS member
app (composer + Face ID lock), web portal /broadcast, native iPad Broadcast
section. All share the same §5.3 contract: password gate first (server pwd_at
window is the only clock), composer with idempotent retry, last-4 with
reach/seen/replied, response wall + tick ledger, open-thread navigation.
Portal deployed to pathway.nuruplace.org (bundle index-BpDqHDH5.js).
Biometrics remain iOS-only (portal/iPad gate = password).

## 2026-07-18 — STANDARDIZATION ARC: every verified gap closed (both directions)
Two forensic audits (iOS→Android and Android→iOS, code-verified, ledger treated
as leads only) produced the full matrix; three waves closed it:
- ios#86: iOS rose to Android's honesty standard (Settings + Talk it Over
  visible failure text; welcome-video caption dedup guard).
- android#46 (wave 2): Home resume banner + Live-now card + prayer-wall
  carousel (GET /home/prayer-wall), scanner torch + permanent-denial→Settings,
  radio Remind-me (the bell was a no-op), level encouragements
  (GET /levels/{n}/encouragements), screen-dwell telemetry
  (POST /me/activity/screens).
- android#47 (wave 3): full Broadcast sender console — sent list, detail with
  response wall + tick ledger, and FINGERPRINT unlock (Keystore AES-GCM,
  setUserAuthenticationRequired + setInvalidatedByBiometricEnrollment ==
  iOS biometryCurrentSet; BiometricPrompt+CryptoObject; wiped on 401/sign-out).
Stale ledger claims cleared: Plans journey, Whisper, SRS, footprints, gifts,
voice notes, MFA — all confirmed AT PARITY by code inspection.
REMAINDER (deliberate, each its own arc): (1) cell Discussions board — absent
on Android entirely (iOS DiscussionsView + offline-queued creates); (2) chat
message edit/delete — missing on BOTH apps (portal-only); (3) Android offline
queue is plaintext Room vs iOS encrypted store (touches 16 KB compliance +
needs queue migration); (4) cosmetic: iOS grays the unseen tick where
Android/portal use blue (product said blue).

## 2026-07-18 — Seven times of day (pathway#380 · ios#87 · android#48; backend DEPLOYED)
Owner spec: sunrise 6-9 / morning 9-12 / midday 12-14 / afternoon 14-17 /
evening 17-21 / night 21-24 / midnight 0-6. Server-side band art on BOTH Home
cards from the 120 verified Unsplash images split even/odd (liturgy vs verse —
provably never the same photo at any hour; disjointness unit-tested). Liturgy
doubled: composed line + authored per-band charge + curated verse line w/ text
(2 each, alternating daily). Verse card: "Chosen for your season" ribbon →
rotating encouragement quotes (Spurgeon/Tozer/ten Boom/Moody/Müller + Pastor
Moses) keyed to the verse theme. Clients tolerant — old fields untouched,
absent new fields render exactly as before. Backend image 2026-07-18T06:31Z,
readyz 200. Apps carry the rendering at their NEXT builds (iOS build 77+,
Android vc44+); art + quotes are already live to CURRENT apps via server art.

## 2026-07-18 — Owner's five-task batch, all shipped
1. Day-unlock race FIXED+DEPLOYED (pathway#381/ios#88/android#49): completeSegment
   answers day_complete/next_day_unlocked in-transaction; clients act on the ack,
   always refetch, honest "Finishing your sync…" state. (Prod data showed all
   owner's day-1 segments recorded — pure client-cache race.)
2. Fireworks celebration (ios#89/android#50): rockets→radial glowing sparks ~5s,
   3 pop wavs (silent-switch + Reduce Motion respected), single-Canvas 60fps.
3. Mid-level discipler (ios#90/android#51): reminder pop-up after 3 modules
   (60s auto-dismiss, 1/session, 24h grace), navy/gold stats card after module
   5-6 (progress ring, band, streak) — all from existing payloads.
4. Code-first reset DEPLOYED (pathway#382 mig 159 + android#52): email leads
   with XXXX-XXXX code (same token field — apps unchanged), portal page shows it
   copyable, Android gained its missing in-app enter-code screen.
5. RBAC hardening DEPLOYED (pathway#383/ipad#5): permission-less scope=admin
   logins refused with clear copy; login+/me carry permissions[]; web+iPad
   sidebars show only granted items (server middleware stays the law).
Follow-up chip parked: unwired "Chat" pill on both level screens (pre-existing).
Apps: all client work rides iOS build 77+ / Android vc44+.

## 2026-07-18 — My Prayer Room (branch feat/my-prayer-room, both apps; not yet merged)
Owner spec: replace the two separate Home/Grow/Community entries — "Prayer
Wall" and "Prayer Journal" — with ONE destination, "My Prayer Room", holding
two tabs: Private Prayer (default) and Corporate Prayer.

iOS: new PrayerRoomView hosts a Chat-style capsule segmented control over the
EXISTING PrayerJournalView and PrayerWallView, unmodified apart from a new
`embedded` flag that drops each one's own back-button/hero chrome (Room
supplies one shared header instead) and swaps their header-hosted "+" for a
floating gold FAB. GrowDestination.prayerJournal and CommunityRoute.prayerWall
now both resolve to PrayerRoomView (on the Private/Corporate tab respectively)
in the single nuruDestinations() switch, so every existing NavigationLink/deep
link (Home Grow tile, "Pray for one another" carousel "Open" link, minis row,
Cell "Open community", the app's internal deep-link switch) converged with
zero call-site changes.

Android: same shape — new PrayerRoomScreen hosts a capsule segmented control
over the existing PrayerJournalScreen and PrayerWallScreen, unmodified apart
from an `embedded: Boolean` param. Android's nav graph is string-route based
(no enum indirection), so the old "prayers" and "prayer-wall" list routes
were removed and every caller rewired to a single "prayer-room?tab={tab}"
route (optional query arg, iOS-parity of the "event/{id}?end=" pattern
already in MainShell.kt): Home Grow tile, prayer-wall carousel card, minis
row, the next-action route mapper, notification-tap + FCM push deep links,
and the long-press launcher shortcut. In both cases a specific wall post
still opens PrayerWallDetailView/Screen directly — deep links to a post keep
working unchanged, they just no longer route through a "wall list" screen.

Each private prayer row on both apps gets a new PROMINENT solid-gold "Share
to Corporate Prayer" button (was a small icon/text link among other row
actions) that swaps to a settled green "On the wall 🙏" state once the share
lands, and fires the apps' existing quiet CelebrationCenter banner (no
confetti — same idiom as posting to the wall) plus a confirm haptic on
Android. No new endpoint: both apps' buttons call the SAME
POST /me/prayers/{id}/share-to-wall the journal's share feature already
shipped — this was already the publish primitive, just demoted to a small
control; My Prayer Room promotes it to the headline action of the tab.

Portal unaffected (oversight reads the same wall); backend unchanged.

Verified: iOS `xcodebuild ... build` → BUILD SUCCEEDED, `... test` → 10/10
green. Android `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` →
BUILD SUCCESSFUL. Not pushed / no PRs opened yet (per task instruction).

## 2026-07-18 — Home "Upcoming" curated rows replace the month-calendar grid (branch feat/home-events-rows, Android only)
Owner spec: the Home "Upcoming" section's mini month grid + dot markers (and
its single hardcoded next-event row underneath) is gone, replaced by a plain
list of up to 5 curated event rows sourced from the new `GET /home/events`
contract (already live backend-side, no backend work needed here) — soonest
first, server-capped at 5. Client renders exactly what the wire sends, in
that order: no client-side sort or `.take(5)`.

`UpcomingSection` now takes `List<HomeEventRow>` (new DTO in HomeDtos.kt, new
`Net.client.api.homeEvents()` call in MemberApi.kt, populated in HomeScreen's
existing `LaunchedEffect(refreshTick)` batch alongside the untouched
`upcoming`/`liveNowInfo` calendar fetch that still feeds the "happening now /
starting soon" LiveNowCard). The whole section — header included — hides when
the list is empty. Each row reuses the app's existing Events-tab relative-time
helpers (`evCountdown` + `evTime` from `feature/events/EventsShared.kt`,
"Tomorrow · 3:00 PM" style) rather than inventing new date logic, and its
trailing pill is driven by `my_rsvp`: gold "RSVP" call-to-action when null,
otherwise a Going/Maybe/Can't-go status pill using the app's existing `EV`
RSVP palette (green/amber/gray) — same wording pattern the Events tab already
uses. `MiniMonth` (the day-grid composable) is deleted outright; it had no
other caller. The Events tab's own full calendar screens
(`AllEventsCalendarScreen.kt`, `EventsScreen.kt`) were not touched — they keep
their own real calendar UI.

Verified: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD
SUCCESSFUL. Not pushed / no PR opened (per task instruction).

## 2026-07-18 — Chat edit/delete own messages (branch feat/chat-edit-delete, both member apps, client-only)
Owner spec: mirror the web portal's already-live ⋮-menu Edit/Delete on own
chat bubbles onto both member apps, wired to the already-live backend
endpoints `PATCH /chat/messages/{id}` (body `{ body }` → `{ message_id, body,
is_edited }`, author-only, sets `is_edited = true`) and `DELETE
/chat/messages/{id}` (→ `{ message_id, deleted }`, author-only soft delete —
the row drops out of the next `GET /chat/conversations/{id}` because the
service already filters `deleted_at IS NULL`). No backend or portal changes;
the backend repo was intentionally left untouched (another agent was working
there).

Both apps: the trigger is a long-press on a message you authored (`mine ==
true` only — never on someone else's bubble, a system row, or a broadcast
copy you didn't write). Voice and image messages get Delete only, no Edit.
Edit opens a small prefilled sheet; Delete asks "Delete this message? This
can't be undone." before calling the server. Both actions are optimistic
(the bubble updates/disappears immediately) and roll back to the prior state
plus a brief inline error banner if the PATCH/DELETE throws — no silent lie
about what actually landed.

iOS (`NuruMember/Features/Chat/ChatThreadView.swift`): the existing
`.contextMenu` on `AuroraBubble` (already long-press-native on iOS, already
carrying the reaction emoji row + Copy) grows an `Edit`/`Delete` section
gated on `m.mine`, calling new `MemberAPI.editChatMessage`/`deleteChatMessage`
(`NuruMember/Networking/MemberAPI.swift`, new shared `ChatMessageMutationResult`
DTO tolerant of both response shapes in `NuruMember/Models/Chat.swift`).
`ChatThreadViewModel` grew `editOverrides`/`locallyDeletedIds` dictionaries
layered on top of `allMessages` for the optimistic update/rollback, plus
`editMessage`/`deleteMessage` methods. Edit is a new `EditMessageSheet`
(reuses the existing `PSheetShell`/`GoldSheetButton` chrome from
ProfileView.swift) presented via `.sheet(item:)`; Delete is a
`.confirmationDialog` matching the app's existing destructive-confirm idiom
(same pattern as PrayerJournalView's "Delete this prayer?"). A transient
banner above the composer (mirrors the file's own `micHint` idiom) surfaces
a failed action.

Android (`app/src/main/java/org/nuruplace/member/feature/community/
ChatThreadScreen.kt`): no existing per-message menu affordance to reuse, so
this adds one — `combinedClickable(onLongClick = …)` on own bubbles only,
opening a `ModalBottomSheet` action list (Edit/Delete), matching the app's
dominant sheet-based menu idiom (used everywhere else: AiDraft, MemoryVerse,
Profile, Giving, PrayerWall) over the one-off `DropdownMenu` used for the
attachment picker. Edit reuses the same `ModalBottomSheet` shell with a
prefilled `BasicTextField` + Save button; Delete confirms via `AlertDialog`
(same idiom as `ModuleScreen`'s revisit-module dialog). Local optimistic
state (`editOverrides: Map<String, String>`, `locallyDeleted: Set<String>`)
sits alongside the screen's existing `AsyncContent`-driven `thread` and is
layered onto `messages` via `.filter`/`.map` before rendering — rollback on
failure just removes the entry. New `EditMessageBody`/`EditMessageRes`/
`DeleteMessageRes` DTOs in `data/net/CommunityDtos.kt`, new
`editChatMessage`/`deleteChatMessage` Retrofit methods in
`data/net/MemberApi.kt`.

Not touched: `BroadcastDetailScreen.kt`/`RecipientRow` on Android already
tints the tick blue regardless of seen state (the iOS-only tick-color fix in
this task's second part was iOS-only — Android and the portal already had it
right).

Verified: iOS `xcodebuild -scheme NuruMember -configuration Debug
-destination "id=8265F608-4A98-4E95-9074-7C54BEC4684A" build` → BUILD
SUCCEEDED, `... test` → 10/10 green (`ModelDecodingTests`). Android
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
&& ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → BUILD
SUCCESSFUL. Not pushed / no PRs opened (per task instruction).

## 2026-07-18 — Chat consent UI: connection requests replace unsolicited DMs (branch feat/chat-consent-ui, both member apps, client-only)
Chat Redesign C3a — the backend's C1/C2 "no unsolicited DMs" model
(`docs/CHAT_REDESIGN.md` §4, `docs/CHAT_REDESIGN_PLAN.md` C1 section in the
pathway repo, read-only) now has client UI in both member apps. Contract:
`POST /chat/connections/requests` (+`/:id/accept|/decline`, `DELETE` to
cancel), `GET /chat/connections/requests?direction=incoming|outgoing`, `GET
/chat/connections`, `POST /chat/connections/:user_id/remove|/block|/unblock`,
and `POST /chat/dms` now 403s `CONSENT_REQUIRED` (details.hint = "send a
connection request first") for a brand-new thread between two ordinary
members with no accepted connection — staff and existing threads are
unaffected (grandfathered server-side, verified by reading
`chat/service.ts#createOrGetDm` and `chat/connections.ts`, both untouched).
No backend or portal changes; pathway repo intentionally left untouched
(another agent works there per instruction).

Both apps, same state machine: **not connected** → tapping a directory
person sends a request (was: opened a DM instantly). **request sent** →
shows "Request sent" with a cancel affordance (tap cancels). **request
received** → surfaced in a new "Connection requests" section above Direct
Messages (accept/decline), not a raw directory-row action. **connected** →
tap opens/creates the DM exactly as before. A stale-cache 403
`CONSENT_REQUIRED` from `createDm` (directory hadn't refreshed) auto-offers
the connection request via a dialog rather than a dead-end error. Existing
DM threads keep working untouched — the gate only applies to a genuinely
NEW thread. Empty DM-list state is now "Connect with someone before
starting a chat." The DM segment's unread-count chip now adds pending
incoming requests to the count (both apps), so the tab visibly asks for
attention when someone wants to connect.

Per-connection controls (Remove connection / Block / Unblock) live in the
thread header's ⋮ menu for `dm`-kind threads — **no Report** on either app:
neither has a member-facing report/moderation affordance to reuse (the
flag/remove/restore actions on a message are Admin-console-only,
`requireRole("Admin")` server-side), so it was intentionally left out rather
than half-built. Unblock is offered unconditionally in the menu (neither
client has a per-pair status read beyond the accepted-only `GET
/chat/connections`), so calling it when nothing was blocked is handled as
an expected, harmless 404 rather than hidden behind a guess at state.

iOS (`NuruMember/Features/Chat/`): new `Models/ChatConnections.swift`
(tolerant DTOs: `ConnectionRequestRow`, `ConnectionRow`, decision/action
result shapes) and `Networking/MemberAPI+Connections.swift` (the 8 new
calls). `ChatInboxViewModel` grew `connections`/`incomingRequests`/
`outgoingRequests`/`connectionState(for:)`/`sendConnectionRequest`/
`cancelConnectionRequest`/`acceptConnectionRequest`/
`declineConnectionRequest`, and `startDm` now catches `APIError.http(_,
"CONSENT_REQUIRED", _, _)` into a `consentPrompt` alert. `PersonRow` in
`ChatView.swift` branches on the derived `ConnectionState` for its trailing
affordance; new `IncomingRequestRow`/`OutgoingRequestRow`. `ChatThreadView`'s
`ThreadHeader` gains a `connectionMenu` (SwiftUI `Menu`) replacing the
(previously non-functional, empty-action) AI sparkles button for DM threads
— peer id comes from `ChatConversation.peerUserId`, which the inbox route
already returns and the thread already carried via navigation, so no new
network round-trip was needed on iOS.

Android (`app/src/main/java/org/nuruplace/member/feature/community/`): new
DTOs in `data/net/CommunityDtos.kt` + 8 Retrofit methods in
`data/net/MemberApi.kt`. `ChatShared.kt` grew the shared `ConnectionState`
sealed interface, `connectionStateFor(...)`, and `isConsentRequired(e)`
(mirrors the existing `isPasswordRequired` pattern from
`BroadcastStepUp.kt`). `PersonRow` in `ChatScreen.kt` was made non-private
and connection-state-aware so `NewMessageScreen.kt`'s picker could reuse it
instead of keeping its own separate copy (that duplicate is now deleted).
`DmTab` grew a "CONNECTION REQUESTS" section (`IncomingRequestRow`/
`OutgoingRequestRow`) and both `ChatScreen`/`NewMessageScreen` load
connections/requests alongside people via their existing `AsyncContent`
bundle pattern. One Android-specific gap `ChatThreadScreen.kt` had to close:
`GET /chat/conversations/{id}` does not return `peer_user_id` (only the list
endpoint does — confirmed by reading `chat/service.ts#getConversation`'s
SELECT, read-only), and the thread route only carries the conversation id
(`"chat/{id}"`, no object payload like iOS's nav). Rather than touch the
read-only backend or the nav graph, the thread screen does one best-effort
extra `chatInbox()` call on open and cross-references `peerUserId` for the
current `conversationId` — additive, no contract change. The ⋮ menu itself
is a `DropdownMenu` (existing idiom, see `EventDetailScreen.kt`'s attachment
picker), replacing the AI-sparkles box for `dm`-kind threads with a resolved
peer only.

Verified: iOS `xcodebuild -scheme NuruMember -configuration Debug
-destination "id=8265F608-4A98-4E95-9074-7C54BEC4684A" build` → BUILD
SUCCEEDED, `... test` → 10/10 green. Android `export JAVA_HOME="/Applications/
Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew
:app:compileDebugKotlin :app:testDebugUnitTest` → BUILD SUCCESSFUL, 21/21
unit tests green. Not pushed / no PRs opened (per task instruction).

## 2026-07-18 — Chat four-tab restructure: My Space · Chat · My Discipler · Talk with My Pastor (branch feat/chat-four-tabs, both member apps, client-only)

Chat Redesign C3b (pathway docs/CHAT_REDESIGN.md §1-2, 5-7). The Chat hub's
segments are restructured for members into **My Space · Chat · My Discipler ·
Talk with My Pastor** — SuperAdmin additionally sees **Pastoral Inbox** and the
unchanged **Broadcast**. Backend untouched (all four new routes were already
live: `GET /chat/discipler/conversation`, `POST /chat/pastoral`, `GET
/chat/pastoral/inbox`, `POST /chat/spaces/{id}/join-requests` — shapes
verified by reading `chat/service.ts` and `pastoral/service.ts`, read-only).

**My Space** merges the old "#My Space" (kind=space) and "My Groups"
(kind=group) segments into one tab with sub-sections — the backend already
files both under `type=SPACE` (CHAT_REDESIGN_PLAN.md §2.1, Open Question 4).
The integer-indexed `Segment(0..3)` control became a role-shaped `ChatTab`
enum list in a horizontally scrolling strip (icons + unread chips; a fixed
4-wide `weight(1f)` split can't hold up to six segments). Discover "Join"
tries the immediate `POST /chat/spaces/{id}/join` first (public spaces,
unchanged) and falls back to filing a reviewed `join-requests` on refusal —
the row flips to an hourglass "Requested" pill. Honest note: no live space
currently *requires* review (`joinSpace` has no approval gating server-side),
so the fallback is forward-compatible tolerance, not a path any real space
exercises today.

**Chat** is the C3a consent-gated DM segment, renamed — no behaviour change.
The known discipler/pastoral conversation ids are filtered OUT of its DM list
(they live in their own tabs). Since neither conversations endpoint returns
`type`, that filter is a client-taught heuristic: the pastoral id is cached in
AppPrefs on first open, the discipler id is re-resolved per hub load — a
pastoral thread never opened on THIS device still shows as an ordinary DM row
(mitigated in the thread screen by a cached-id ctx fallback, below).

**My Discipler** is active only when `GET /me/discipleship` yields a
discipler (empty state: "A discipler has not yet been assigned to you.");
opening resolves the DISCIPLER thread via `GET /chat/discipler/conversation`
— never the Hub's legacy `dm_conversation_id`, which is an ordinary unstamped
DM lookup, not the same conversation. The hub load also resolves the id
eagerly *when an assignment exists* purely for the tab's unread badge (the
lazy-create is safe there: the thread it would create is the one the
assignment already implies). DiscipleshipHubScreen itself was left untouched.

**Talk with My Pastor** create-or-opens via `POST /chat/pastoral` (server
resolves assigned → congregation default → SuperAdmin fallback; the rare
`no_pastor` 404 and the minors' FORBIDDEN_SCOPE get friendly copy via the new
`isNoPastor`/`isMinorBlocked` helpers in ChatShared.kt, same body-sniff idiom
as `isPasswordRequired`). The thread screen shows the privacy banner
("Private pastoral conversation." / "Private between you and your assigned
discipler." for the discipler flavour) and, for pastoral, the ⋮ menu: Lock
now · Enable/Disable biometric lock · Mute · Archive · Privacy info — all
four actions client-side-only (no server route backs any of them; Archive is
a local hide with a reopen affordance, Mute silences the tab badge and this
device's own notification rendering). Privacy info is an honest dialog — no
E2EE claim.

**Biometric lock** (`data/PastoralLock.kt`) is deliberately NOT
`BroadcastLock`'s shape: `POST /chat/pastoral` has no server step-up, so
there is no password to carry — no Keystore key, no CryptoObject, no stored
secret. It is a pure `BiometricPrompt` gate with `BIOMETRIC_WEAK or
DEVICE_CREDENTIAL` (biometric-or-device-passcode, matching iOS's
`.deviceOwnerAuthentication`), per-device opt-in (off by default), and its
`unlocked` state lives only in process memory — restart always starts locked,
the thread screen re-locks on ON_STOP (app backgrounded), and sign-out sweeps
the lot (`AuthStore` → `resetForSignOut`). The gate is a true render gate:
ChatThreadScreen early-returns to `PastoralLockScreen` before mark-read, the
thread fetch, or any message composable runs — not an opaque cover. The ctx
rides the nav route (`chat/{id}?ctx=discipler|pastoral`) because
`GET /chat/conversations/{id}` carries no `type`; an un-annotated route falls
back to comparing the cached pastoral id so a deep link can't slip past the
gate. Cross-platform nuance, stated plainly: iOS re-locks on a 5-minute
timeout as well; Android has no wall-clock timeout — it stays open only while
the app is continuously foregrounded, which is the tighter posture in
practice but not identical.

**Pastoral Inbox** (pastor side, "Talk with Your Pastor") reuses the EXISTING
`BroadcastStepUp`/`BroadcastStepUpDialog` machinery verbatim — `GET
/chat/pastoral/inbox` genuinely demands the same §5.3 fresh-password step-up
as Broadcast (fingerprint fast path included), so this is the one place C3b
copies Broadcast's shape on purpose. Client-side the segment is offered to
SuperAdmin only (`pastoralEligible` in MainShell): the server also admits any
user who ever held a `pastor_assignments` row, but its middleware demands the
password step-up BEFORE the not-a-pastor 403 can fire, so there is no
side-effect-free probe — an assigned non-SuperAdmin pastor has no client
entry point today. Real limit, not hidden. A plain 403 after step-up renders
as an honest "no members assigned to you pastorally" empty state.

**Notifications, honestly**: the backend sends NO push for any chat message —
DM, discipler, pastoral or space — today (verified: only `space_join_*` and
connection events call `notify()`). The suppression added to
`NuruMessagingService` (pastoral pushes render the generic "You have a new
private pastoral message." with no preview; dropped entirely when muted) is
therefore defensive future-proofing, not a fix to a live leak — and it only
governs notifications THIS app renders; a notification-payload push the OS
renders while the process is dead never reaches that code.

Android files: `data/net/CommunityDtos.kt` + `MemberApi.kt` (DTOs + 4 calls),
`feature/community/ChatShared.kt` (error helpers), `data/PastoralLock.kt`
(new), `data/AppPrefs.kt` (pastoral keys + sign-out sweep), `auth/AuthStore.kt`,
`feature/community/ChatScreen.kt` (ChatTab enum, merged My Space, three new
tab composables), `feature/community/ChatThreadScreen.kt` (ctx, gate, menu,
banner), `feature/shell/MainShell.kt` (?ctx= route + pastoralEligible),
`data/firebase/NuruMessagingService.kt` (preview suppression). iOS shipped
the mirror restructure the same day on its own feat/chat-four-tabs (see that
repo's PARITY_AUDIT.md entry — same tabs, PastoralLock twin with a 5-minute
window, SuperAdmin inbox inside the pastor tab).

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 21/21 unit tests green. iOS `xcodebuild ... build` →
BUILD SUCCEEDED, `... test` → 10/10 green (pinned simulator, derivedDataPath
build/dd). Not pushed / no PRs opened (per task instruction).

## 2026-07-19 — CHAT REDESIGN EPIC COMPLETE (C0-C4, all deployed)
pathway#385/#386/#387/#388 live in prod (migrations 161-171); apps carry
C3a (and#56/ios#94) + C3b (and#57/ios#95). Four tabs, consent DMs, derived
spaces w/ approval gate, discipler history, pastoral channel + biometric
local lock, typed push notifications (first ever for chat; pastoral generic),
member report, conversation type on wire, 23/25 §16 scenarios proven
(794/794 backend tests). Client-side remainder for next arcs: mute model
(server has none), pastoral-inbox client render of type-driven dedup,
DiscipleshipHub legacy dm path. AWAITING: app builds iOS 77 / Android vc44.

## 2026-07-19 — CHAT EPIC CLIENT REMAINDER CLOSED (both apps)
Closes the four items the 2026-07-19 entry above flagged as "next arcs" —
the backend landed the eligibility probe and the mute contract in the
interim (`chat/pastoral/eligibility`, `chat/conversations/{id}/mute`);
pathway repo was read-only (wire shapes only, `modules/{chat,pastoral}`).

**Pastor inbox entry** — `GET /chat/pastoral/eligibility` → `{is_pastor}`,
no step-up. The "Talk with Your Pastor" segment (Android: `PastoralInbox`
tab; iOS: the inbox section inside the pastor tab) was SuperAdmin-only
client-side even though the server has always admitted any user who ever
held a `pastor_assignments` row, past step-up. Both apps now OR the probe
into the existing SuperAdmin check, cached in memory for the app session
(Android `data/PastorEligibility.kt`; iOS `PastorEligibility` enum in
`Features/Chat/PastoralLock.swift`) and reset on sign-out alongside the rest
of the pastoral device-local state.

**Type-driven dedup** — conversation list + detail rows now carry
`type` (DIRECT/SPACE/DISCIPLER/PASTORAL/BROADCAST/BROADCAST_RESPONSE). Both
apps' Chat-tab DM filter (Android `ChatScreen.kt#dms`; iOS
`ChatInboxViewModel.dms`) now excludes DISCIPLER/PASTORAL by `type` first,
falling back to the old cached/resolved-id heuristic only when a row carries
no `type` (older server) — tolerant decode, not the default path. iOS also
upgraded `ChatThreadViewModel`'s context inference (privacy banner + ⋮ menu)
to the same server-first rule. Android's biometric GATE still has to decide
before the thread ever loads (nav `?ctx=` param, unchanged), but the
post-load render (privacy banner, pastoral ⋮ menu) now upgrades to
`thread.type` when the caller passed no `ctx` — see `ChatThreadScreen.kt`'s
`preloadCtx` vs. `ctx` split.

**DiscipleshipHub legacy path** — Android `DiscipleshipHubScreen`'s
"Message" hero and iOS `DiscipleshipHubView`'s equivalent used to resolve
(or create, via `POST /chat/dms`) a plain DIRECT dm keyed off the hub's
`dmConversationId` — never the DISCIPLER type, so neither the privacy banner
nor admin-invisibility applied. Both now always call
`GET /chat/discipler/conversation` (the same call the My Discipler tab
makes) and navigate with the discipler context explicit (Android
`chat/{id}?ctx=discipler`; iOS `ThreadRoute(context: .discipler)`). Not
touched: `DisciplerDossierScreen`/`DisciplerDossierView` (the leader-side
mirror, out of scope — a separate screen the task didn't name; flagged here,
not fixed).

**Server mute** — `PUT/DELETE chat/conversations/{id}/mute` (`{until?}`,
absent = forever), `muted: Bool` additive on conversation list/detail. The
pastoral ⋮ menu's Mute/Unmute (the only Mute menu item either app has) is
now wired to these routes instead of being purely local: optimistic flip,
server call, revert + inline error on failure (Android reuses the existing
`actionError` banner in `ChatThreadScreen`; iOS reuses `flashActionError` in
`ChatThreadView`). `muted` is still mirrored into the local pref
(`AppPrefs.pastoralMuted` / `PastoralPrefs.muted`) so the Chat tab's badge
suppression — which only fetches the list endpoint — doesn't need a second
round trip, but that pref is now synced FROM the server on every thread load
and every inbox load, not treated as the source of truth. Muted rows show a
small bell-slash glyph: Android `MutedGlyph()` in `ChatScreen.kt` (Space/Dm/
Group rows + the Talk with My Pastor card); iOS the equivalent in
`ChatView.swift` + `PrivateThreadCard`.

Android files: `data/net/CommunityDtos.kt` (+type/muted on ChatConversation/
ChatThreadDetail, PastoralEligibilityRes, MuteConversationBody),
`data/net/MemberApi.kt` (+3 endpoints), `data/PastorEligibility.kt` (new),
`auth/AuthStore.kt` (reset sweep), `feature/shell/MainShell.kt`
(pastorEligible produceState, discipleship route `?ctx=discipler`),
`feature/community/ChatScreen.kt` (dms filter, MutedGlyph, mute state on the
pastor card), `feature/community/ChatThreadScreen.kt` (preloadCtx/ctx split,
server mute wiring), `feature/discipleship/DiscipleshipHubScreen.kt`
(discipler-endpoint hero). iOS files: `Models/Chat.swift` (type/muted),
`Networking/MemberAPI+Pastoral.swift` (+eligibility),
`Networking/MemberAPI.swift` (+mute/unmute), `Features/Chat/PastoralLock.swift`
(PastorEligibility), `Features/Chat/ChatView.swift`,
`Features/Chat/ChatThreadView.swift`, `Features/Chat/PastoralViews.swift`,
`Features/Discipleship/DiscipleshipHubView.swift`.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 21/21 unit tests green (no new tests added — pure
wiring/rendering change, no new pure-function surface to unit test). iOS
`xcodebuild ... build` → BUILD SUCCEEDED, `... test` → 10/10 green (pinned
simulator 8265F608-4A98-4E95-9074-7C54BEC4684A, derivedDataPath build/dd).
Not pushed / no PRs opened (per task instruction).

---

## 2026-07-19 — iOS-only polish (builds 78–84) ported to Android (branch feat/ios-parity-polish, Android only)

iOS shipped six polish commits directly to `nuru-member-ios` main between builds
78 and 84 (owner feedback on the just-launched reading/social surfaces). This
session read each iOS diff (`git show <hash>`) and ported the equivalent to
Android, matching Android's own idioms (Compose, `cInter`/`cSerif`/`gInter`
helpers, `NuruType`, `CHAT`/`Nuru` palettes) rather than transliterating Swift.

**1. Liturgy card hierarchy** (iOS b6f124b → Android). The Home liturgy card
used to shout two large serif lines and cite two scriptures. Rebuilt to ONE
hierarchy: the hour's word large white serif, a short gold "selah" rule
(34dp), the charge + companion verse in small gold italic serif (curly
quotes), closing on a single letter-spaced gold uppercase reference — the
composed line's own `scriptureRef` shows only when there's no companion
verse. Applied to both the art-tableau and classic (no-art) branches; added a
`tableauHeight()` model (232dp base + 30dp charge + 50dp verse) so the bottom
stack never crowds the kicker, mirroring iOS's retuned height math.
`feature/home/LiturgyCards.kt`.

**2. My Prayer Room redesign** (iOS 95984ed + dde8c3a → Android). Room tabs
became Private · Corporate · Answered (`PrayerRoomTab` gained a third case;
the Answered tab hosts `PrayerJournalScreen` pinned via a new `forcedTab:
PrayerTab?` param). The journal's management surface collapsed to ONE row:
Active/Answered filter chips (hidden when force-pinned) + a gold "Add Prayer"
pill that opens a composer dialog (title/body/answered — Android has no
sheet-presentation system yet, so this ships as an `AlertDialog`, functionally
equivalent to iOS's `PrayerComposerSheet`). Each card now shows the owner's
real name (`GET /me` profile.fullName, not "You"), an initials avatar, and a
relative timestamp — and ONLY those three carry the status color (green
answered · orange on-the-corporate-wall · gold private); title/body stay
neutral. All actions moved into a `⋮` `DropdownMenu` (Mark answered/Reopen ·
Publish to Corporate · Edit · Delete), replacing the old stacked buttons.
Answered keeps the green celebration banner; a shared-but-unanswered prayer
shows the quiet orange "On the Corporate wall" line. `sharedToWall` is
session-local (a `Set<String>` lifted to screen scope), matching iOS's
`@Published var sharedToWall: Set<String>` — the server has no persisted
"is this shared" flag on the entry; re-sharing is idempotent either way.
`feature/community/PrayerRoomScreen.kt`, `feature/grow/PrayerJournalScreen.kt`
(rewritten), `feature/shell/MainShell.kt` (`?tab=answered` routing).

**3. Pastoral = broadcast behavior** (iOS 1a1f789 + 761298b → Android). The
Talk-with-My-Pastor thread now shows the personal gold ribbon "Only \<pastor\>
sees your reply" instead of the generic "Private pastoral conversation."
sentence (that generic `PrivacyLabelBanner` now only renders for
`ctx == "discipler"`). Message ticks adopted the broadcast rule everywhere:
ONE WhatsApp-blue tick (`CHAT.tickBlue = 0xFF2F80ED`, new constant in
`ChatShared.kt`) = delivered, TWO blue ticks = seen (`readCount > 0`) — was
previously always "✓✓", gold when read / white-dim otherwise. Applies to every
`mine` message bubble in every thread kind (DM, discipler, pastoral), not
just pastor mail. `feature/community/ChatThreadScreen.kt`,
`feature/community/ChatShared.kt`.

**4. Tap-zone hardening — audited, not ported (Compose doesn't carry either
bug class).** iOS build 81 fixed a `GeometryReader`-under-`fixedSize` bug
where the reading-plan mini card's ambiguous height let the Prayer Room
mini's tap bleed into the card below; build 83 fixed `NavigationLink`s hosted
inside a paged `TabView` firing with a neighbor page's captured value.
Checked Android's `MinisRow`/`MiniCard` (`HomeScreen.kt`) and the "Pray for
one another" `HorizontalPager` (`HomeScreen.kt`): neither exists in Compose.
`clickable`'s hit-test region is exactly the modified composable's measured/
clipped bounds (no `GeometryReader`-style ambiguity possible from a plain
`Column`/`Row` with intrinsic sizing), and `HorizontalPager`'s page content is
a plain `(i, list) -> ...` closure re-evaluated per page — there is no
identity-forwarding mechanism analogous to SwiftUI's `NavigationLink`-in-
`TabView` quirk. No code changed for this item.

**5. Global type tokens** (iOS build 80 `nChipLabel`/`nActionLabel` →
Android). Added `NuruType.chipLabel` (`nuruSans(12, SemiBold)`) and
`NuruType.actionLabel` (`nuruSans(13, Bold)`) to `ui/theme/NuruTheme.kt`,
mirroring the iOS tokens exactly. Wired into the two call sites this session
touched: `PrayerRoomScreen`'s segmented-control chips (previously inline
`gInter(12, FontWeight.SemiBold)`) and the new Prayer Room card's chip/pill
labels. Left Chat's own `cInter`/`cSerif` per-surface helpers alone — Android's
established convention (per `TypeSchema.kt`'s header comment) is per-surface
delegates for surface-local text, promoting to `NuruType` only for genuinely
cross-cutting styles; forcing every existing `cInter(12, .semibold)` chip
call site onto the new global token site-wide would have been scope creep
beyond this session's ported items.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 21/21 unit tests green (no new tests added — pure
UI/wiring change, no new pure-function surface to unit test). versionCode/
versionName/signing untouched. Not pushed / no PR opened (per task
instruction).

## 2026-07-20 — Read with a Friend R3 client (branch feat/read-with-friend-ui, this repo only)
Reading & Social R1 backend (pathway#392, `/v1/reading/*` — shared plan
groups, invites, public `/join/{token}` OG page) now has Android client UI.
Read `pathway/docs/READING_SOCIAL_REDESIGN.md` §3/§6 + `docs/READING_SOCIAL_PLAN.md`
and the backend module (`packages/backend/src/modules/reading-social/{groups,
invites,tokens,publicPage,index}.ts`, read-only) for the exact wire shapes;
built in a dedicated worktree (`.worktrees/rwf`) per the two-agent isolation
instruction — never pushed, commit is local only.

**New:** `data/net/ReadingSocialDtos.kt` (kotlinx.serialization DTOs — the
global `JsonNamingStrategy.SnakeCase` handles the wire conversion, so these
are plain `data class`es with defaults, no hand-rolled tolerant decoders like
iOS needs); `MemberApi.kt` grew the 11 reading-social endpoints; new screen
file `feature/grow/ReadWithFriendScreen.kt` — the hub (`ReadWithFriendHubScreen`,
my active shared groups with per-friend "Doris · Day 3 of 7" rows, reusing
`ChatCircleAvatar` from the community package rather than inventing a second
avatar component), group detail (`ReadingGroupDetailScreen` — roster +
progress bars, invite-a-friend via a `ModalBottomSheet` `FriendPickerSheet`
that reuses `MemberApi.listConnections()`, share-link, leave/archive,
pending-invites with revoke, refetches on every recomposition of `groupId`),
and the invite-preview/accept/decline screen (`ReadingInvitePreviewScreen`).

**Wired:** unlike iOS, Android's Plans screen had NO "Read with a friend"
card at all (only the CtaBar's no-op Invite button existed) — added a new
`InvitationCard` composable to `ReadingPlansScreen.kt`, ported from the iOS
`invitationCard`, opening the hub. `PlanDetailScreen.kt`'s CtaBar "Invite"
button — previously `.clickable { }`, a literal no-op, the largest single
client gap the R0 audit found (`docs/READING_SOCIAL_PLAN.md` §3) — now
creates-or-gets a real group for that plan, mints an open invite, and hands
the public `https://pathway.nuruplace.org/join/{token}` link + a rich message
to `Intent.ACTION_SEND` (the same share pattern `DevotionalScreen.kt` already
uses).

**Incoming:** added the FIRST deep-link infra this app has ever had — an
`android:scheme="nuru" android:host="join"` `VIEW`/`BROWSABLE` intent-filter
on `MainActivity` (`AndroidManifest.xml` previously had only the launcher
filter, confirmed by the R0 audit: "no App Links intent-filter... no
assetlinks.json"). `MainActivity.readingJoinDest()` parses `intent.data` and
feeds `"reading/join/{token}"` through the existing `PendingDest` mechanism.
A `plan_group_invite_received` FCM push does the same via `invite_token` in
the data payload (`NuruMessagingService.destFor`); other `plan_group_*`
templates (no redeemable token) land on the hub.

**Fixed in passing (needed for THIS feature's warm-tap path, not a drive-by):**
`PendingDest` was a plain `var`, not Compose state — `MainShell`'s consuming
`LaunchedEffect(Unit)` only ever ran once per composition, so a deep link or
notification tapped while the app was ALREADY OPEN (`singleTop` → `onNewIntent`
fires, `setContent` does not re-run) silently failed to navigate for every
existing destination, not just this one. Converted to `by mutableStateOf<String?>`
and re-keyed the effect off `PendingDest.route` — a write from `onNewIntent`
now re-triggers it exactly like a cold-start read would.

**Found but out of scope, spun off separately:** `NuruMessagingService.destFor`'s
pre-existing `moduleId`/`announcementId`/`levelNumber` FCM data-key lookups
appear to be dead code — the backend dispatcher (`workers/dispatch.ts`) copies
the payload JSONB into the FCM `data` map VERBATIM (confirmed: no camelCase
conversion for push, unlike REST), and every backend `.schedule()` call site
writes snake_case (`module_id`, `announcement_id`, `level_number`). Read with
a Friend's own `invite_token` lookup is correctly snake_case; the three
pre-existing ones are not. Not touched here — flagged as a background task.

**Deep-link limit (honest, not silently deferred):** only the `nuru://`
custom scheme is wired, per `docs/READING_SOCIAL_PLAN.md` §5 tier 2/3 — no
`autoVerify` App Link for `https://pathway.nuruplace.org/join/{token}`. That
needs `/.well-known/assetlinks.json` hosted at the domain root (backend/infra
work, out of this client-only session). Until it ships, the public
`/join/{token}` page's own inline-script fallback (already live server-side)
still opens the app via the custom scheme when installed, or falls through to
the Play Store after ~1200ms when not.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 21/21 unit tests green (no new tests added — pure
UI/wiring + one new screen file, no new pure-function surface distinct from
what the existing DTO/ViewModel-less Compose pattern already covers).
versionCode/versionName/signing untouched. Not pushed / no PR opened (per
task instruction).
## 2026-07-20 — Selah (My Thoughts) + AI Prayer Points: Prayer Room tabs 3 & 4 (branch feat/selah-prayer-points, this repo only, client against LIVE backend pathway#391, port of iOS same-day build)

Prayer Room grew from three tabs (Private · Corporate · Answered) to four
(Private · Corporate · Selah · Prayer Points). Answered lost its top-level
slot but not its function — `PrayerJournalScreen`'s own Active/Answered chips
already render whenever it isn't force-pinned via `forcedTab`, so nothing
about Answered was removed, only relocated. The `prayer-room?tab={tab}` route
now recognizes `selah`/`prayer-points`; `tab=answered` falls back to Private
rather than 404ing a stale deep link. Segmented control moved from a fixed
3-way `Row` to a horizontally-scrolling `LazyRow` (four labels no longer fit
one equal-width strip on a phone).

**Wire surface added to `MemberApi.kt`** (none of this existed before this
session — traced `packages/backend/src/modules/thoughts/{index,service}.ts`
and `intelligence/prayer.ts` first): `GET/PUT/DELETE me/thoughts[/{id}]`,
`POST me/prayer/assist`, `POST me/prayer/points`; DTOs `Thought`,
`ThoughtSpan{start,end,bold?,italic?,color?,font?}`, `ThoughtUpsertBody`,
`PrayerAssistBody/Res`, `PrayerPointsRes` added to `GrowthDtos.kt` matching
the Zod shapes field-for-field. Writes route through the SAME offline queue
as the prayer journal (`Net.client.offline.runOrQueue("member_thoughts",
"upsert"/"delete", ...)`) — confirmed `member_thoughts:upsert`/`:delete` wired
in the backend's `sync/service.ts:304-316` before relying on it.

**Selah — My Thoughts** (`SelahScreen.kt`) follows `PrayerJournalScreen`'s own
idiom (`AsyncContent` + local composable state, no separate ViewModel class)
rather than introducing a new pattern. One-time explainer card persisted via
`SharedPreferences` (`nuru_selah`/`explainer_dismissed` — Android has no
`@AppStorage`; this is its direct equivalent) with the owner's exact copy;
empty state "Selah. Pause here — write your first thought."

**The editor** (`SelahEditorScreen.kt` + `SelahRichEditor.kt`) bridges a plain
`android.widget.EditText` into Compose via `AndroidView` — Compose's own
`TextField`/`BasicTextField` has no live rich-text editing model (a
`TextFieldValue.annotatedString` renders styling but user edits collapse back
to plain text), so `EditText` + `Editable` spans is the standard Android
idiom, matching iOS's `UITextView` bridge. Bold/Italic use `StyleSpan`
(stacked independently over the same range rather than one combined
`BOLD_ITALIC` span, so each button state reads/writes clean); color uses
`ForegroundColorSpan`; font uses a hand-rolled `CustomTypefaceSpan` — minSdk
26 predates the stock API-28 `android.text.style.TypefaceSpan(Typeface)`,
so this is the pre-28 workaround (carries both the resolved `Typeface` AND
its `SelahFont` key, since `ResourcesCompat.getFont` gives no identity
guarantee for reverse-mapping a bare `Typeface` back to an enum case). Four
font choices: Inter/Fraunces (bundled brand faces) + Serif/Cursive (free
Android system fonts — deliberately not bundling new assets, mirroring iOS's
Georgia/Noteworthy choice of 2 brand + 2 free system faces). **Line spacing is
honestly scoped as a GLOBAL preference, not per-span** — same reasoning as
iOS: the backend's `ThoughtSpan` has no spacing field, so it's
`EditText.setLineSpacing(...)` applied to the whole field, not persisted per
thought.

**Platform-honest gap, stated plainly**: unlike `UITextView`'s "typing
attributes" (toggle Bold with an empty cursor, keep typing bold), plain
`EditText` has no equivalent without a custom `InputConnection`. Formatting
here requires a real selection first — select text, then format — which is
the standard Android rich-text idiom (Docs, Keep, Notion all work this way)
but is a genuine interaction difference from the iOS build, not a bug to fix
later.

**Pen drawing** (`SelahInk.kt`) is a Compose `Canvas` capturing pointer input
via `awaitEachGesture`/`awaitPointerEvent` — each `PointerInputChange.type`
(Compose's own decode of the underlying `MotionEvent` tool type) is inspected
so a real stylus draws a thinner 2.5dp line vs a finger's 5dp, genuine
tool-type capture rather than a finger-only stand-in. Gated to tablet-class
hardware (`smallestScreenWidthDp >= 600`, the standard Android "is this a
tablet" heuristic) as the closest honest analog to iOS's iPad-only gate —
Android styluses ship on tablets/Chromebooks, not phones, and there is no
reliable single "has a stylus" signal on a phone-form-factor device the way
`userInterfaceIdiom == .pad` is a clean proxy on iOS. A drawing renders twice
from the same stroke data — once live via Compose `drawPath` for the canvas,
once via `android.graphics.Canvas`/`Path` for the PNG export — and uploads
through the EXISTING `me/media/image` multipart endpoint
(`Net.client.api.uploadPostImage`, the same call `EventDetailScreen`'s image
composer already uses) — no new upload path invented. Delivered `url`s append
to `drawingUrls`; removable locally before Save.

**Prayer Points** (`PrayerPointsScreen.kt`) against the intelligence layer's
`PrayerAiService`: (a) an assist composer — seed points → `POST
me/prayer/assist` → an editable draft, visually mirroring `AiDraft.kt`'s orb+
sheet idiom (purple→gold, `AutoAwesome` icon) without reusing the component —
the wire shape differs (`assistantChat` summarizes a thread; this takes a
bare `seed`); (b) "Gather my prayer points" → `POST me/prayer/points` → an
editable, removable, copy-all numbered list. Both consent-gate on
`ai_opt_out`; there is no dedicated "Sunday Letter consent prompt" component
in this codebase to reuse (checked — the letter just doesn't compose
server-side when opted out), so this ships its own gate card using
`ProfileScreen.kt`'s `AiConsentCard` copy verbatim, with a one-tap "Turn on
AI personalization" CTA calling the same `setAiConsent` the Profile toggle
uses.

Files: `data/net/GrowthDtos.kt` (+`Thought`/`ThoughtSpan`/`ThoughtUpsertBody`/
`PrayerAssistBody`/`PrayerAssistRes`/`PrayerPointsRes`), `data/net/
MemberApi.kt` (+6 endpoints), `feature/community/SelahRichEditor.kt` (new),
`feature/community/SelahInk.kt` (new), `feature/community/
SelahEditorScreen.kt` (new), `feature/community/SelahScreen.kt` (new),
`feature/community/PrayerPointsScreen.kt` (new), `feature/community/
PrayerRoomScreen.kt` (tabs), `feature/shell/MainShell.kt` (route mapping).

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 21/21 unit tests green (no regressions; no new pure-
function surface to unit test — this session is UI/wiring + a new rich-text/
ink engine, both exercised by compilation and manual reasoning, not new unit
tests). Needed two one-time worktree fixups unrelated to the code itself:
`local.properties` (sdk.dir) and `app/google-services.json` are both
git-ignored and don't exist in a fresh worktree checkout — copied from the
main checkout before the first Gradle invocation. Not pushed / no PR opened
(per task instruction).

## 2026-07-20 — Global reading typography: line-spacing pref + VerseQuoteCard + Selah per-span spacing (branch feat/reading-typography-global, this repo only, port of the same-day iOS session)

Four pieces, all reusing existing tokens (Fraunces, `Nuru` palette) — no
second parchment color, no second quote glyph.

**1. Global line-spacing preference**, exactly parallel to the existing
text-size control. `data/AppPrefs.kt` gained `lineSpacing: Float`
(`mutableFloatStateOf`, default 1.0, persisted in the same `nuru_member_prefs`
SharedPreferences file as `textScale`). `ui/theme/TypeSchema.kt`'s
`nuruSans`/`nuruSerif` factories now multiply every `lineHeight` by
`AppPrefs.lineSpacing` — the ONE hook every derived style (`NuruType.*`,
`gInter`/`gSerif`, `pInter`/`pSerif`, `evInter`/`evSerif`, `plInter`/`plSerif`,
`rInter`/`rSerif`, …) already funnels through. Two follow-on fixes were
needed for this to actually recompose live, matching how `textScale` does it
via `LocalDensity`: `NuruType`'s style properties in `ui/theme/NuruTheme.kt`
were `val`s eagerly computed once at object-init — converted to `get() =`
computed properties so each access re-reads the live pref; and
`MaterialTheme.typography` (a plain `Typography` value, not something
descendants re-read the way `LocalDensity` is) is now built INSIDE the
`NuruTheme()` composable via `remember(AppPrefs.lineSpacing) { Typography(…) }`
instead of a top-level `val`. `feature/profile/SettingsScreen.kt`'s
`DisplayCard` gained a "Line spacing" row directly under "Text size", same
Compact/Default/Relaxed (0.85/1.0/1.35) chip idiom.

**2. Verified + fixed the named reading surfaces.** All the shared
`*Inter`/`*Serif` per-feature delegates already route through
`nuruSans`/`nuruSerif`, so they picked up the pref for free. What didn't:
local factories and inline `.copy(lineHeight = N.sp)` overrides that hardcode
a literal instead of taking the schema default. Added
`ui/theme/TypeSchema.kt#scaledLineHeight(sp)` (`(sp * AppPrefs.lineSpacing).sp`)
and routed every hardcoded literal on the named surfaces through it:
`feature/pathway/ModuleScreen.kt` (the `ml`/`mlSerif` lesson-markdown
renderer — paragraphs, bullets, numbered lists, table cells, the folded
reflection), `feature/grow/PlanSegmentScreen.kt` (`serif()` factory),
`feature/grow/PlanReaderKit.kt` (`rSerif()` factory + `RPassage`/`RKeynotes`),
`feature/grow/PlanPartReaderScreen.kt` ("GO DEEPER" reading block),
`feature/grow/PlanDayScreen.kt` (`VerseBlock` — currently dead code, no
caller, fixed anyway for when it's wired up), `feature/grow/
DevotionalScreen.kt`, `feature/grow/MemoryVerseScreen.kt`. Left untouched
(out of the named scope): Chat/Home/Prayer/Events screens' own hardcoded
lineHeights.

**3. `VerseQuoteCard`** — new shared composable, `ui/components/
VerseQuoteCard.kt`: warm parchment (`#FBF3DF`, distinct from the older
`Nuru.verseBg` #FFF8E6 gold-tint used by `PullQuoteCard`/`RPullQuote`), a left
gold accent bar, a large hanging gold "“" glyph, the verse in Fraunces
over `Nuru.navy`, an uppercase/`Nuru.ink600`/1.4sp-tracked reference line.
Built entirely from `nuruSans`/`nuruSerif`, so it inherits both the text-size
and line-spacing prefs with no local override. Wired in:
`feature/pathway/ModuleScreen.kt` — `MarkdownView`'s blockquote branch now
runs the quote text through a new `splitScriptureQuote()` (a regex,
`ScriptureRefTail`, matching a trailing "— Book Chapter:Verse" attribution,
same shape as this file's existing `WhisperLines`); a match renders
`VerseQuoteCard`, no match falls back to the plain gold-bar blockquote
unchanged. `feature/pathway/LevelDetailScreen.kt` — an authored encouragement
with `kind == "verse"` and both `body`+`scriptureRef` now renders
`VerseQuoteCard` instead of two separate Text rows. `feature/grow/
PlanSegmentScreen.kt` — a `"scripture"`-kind segment (`SegmentBody`).
`feature/grow/PlanPartReaderScreen.kt` — the Word part's `"scripture"`-kind
segment (`PartContent`). Also (not in the original explicit list, but the
same duplicated cream/gold/quote-icon shape, so folded in for consistency):
`feature/grow/DevotionalScreen.kt`'s "today's verse" card.
`PullQuoteCard`/`RPullQuote` are kept (still used for reference-only,
non-quoted callouts in the "talk" segment case) but `RPullQuote` no longer has
a scripture call site — left in place as a shared primitive rather than
deleted, since removing it wasn't requested.

**4. Selah editor per-span line spacing** — the backend
(`packages/backend/src/modules/thoughts/service.ts`) shipped
`ThoughtSpan.spacing` (`z.number().min(0.8).max(2.5).optional()`) this same
day; `data/net/GrowthDtos.kt#ThoughtSpan` gained the matching
`spacing: Float? = null`. `feature/community/SelahRichEditor.kt`: added
`LineSpacingSpan`, a hand-written `android.text.style.LineHeightSpan`
implementation (no framework concrete class below API 29) that scales a
line's `Paint.FontMetricsInt` ascent/descent/top/bottom by a multiplier —
same "write our own span" idiom this file already used for `CustomTypefaceSpan`
(custom fonts, minSdk 26 predates the stock API-28 TypefaceSpan).
`SelahRichText.build()` now applies `LineSpacingSpan` from `span.spacing`;
`SelahRichText.extract()` now scans for `LineSpacingSpan` alongside
bold/italic/color/font, includes it in the boundary set and the "differs"/
run-merge checks, and emits `ThoughtSpan.spacing`. `RichEditorController
.applySpacing(multiplier: Float)` replaced the old whole-`EditText
.setLineSpacing()` call with a per-selection `LineSpacingSpan` (same idiom as
`applyColor`/`applyFont`) — the round trip is closed both ways.
`SelahSpacing` (the toolbar's 3 presets) changed from absolute
`extraPx` values to `0.8..2.5`-range multipliers (Cozy 1.0 / Comfortable 1.4 /
Relaxed 1.8) and gained `nearest(multiplier)` to map a stored per-span value
back to the closest preset. `feature/community/SelahEditorScreen.kt` dropped
its `setLineSpacing(...)` call on the hosted `EditText` and now calls
`controller.applySpacing(s.multiplier)` from the toolbar dropdown.

Honest limits: `VerseQuoteCard`'s hanging-quote glyph is positioned with
fixed dp offsets, not measured against the actual font metrics — reasonable
by eye but not pixel-verified against the iOS build (which had not yet
landed its own `VerseQuoteCard` in `nuru-member-ios` as of this session,
so there was no sibling implementation to diff against — this is an
independent build from the same text spec, not a byte-for-byte port). The
card has no reader-night-mode variant, so a scripture callout in
`PlanPartReaderScreen`'s dark reader palette still renders on light
parchment (a deliberate "Scripture stays set apart" choice, not an oversight,
but worth a second look). `LineSpacingSpan` is a single-multiplier `chooseHeight`
scale, not validated against Android's behavior when two different
`LineHeightSpan`s overlap the same wrapped line (an edge case a member would
have to construct deliberately — select a partial line twice with different
presets). No unit tests were added for the Selah round-trip or the markdown
scripture-detection regex — this codebase has no Robolectric/android-API
test harness for `android.text.*`-touching code (`SelahRichText.build/extract`
were untested before this change too), and `parseMarkdown`/`splitScriptureQuote`
are plain string logic that could be unit-tested but weren't, to keep this
session inside its four stated deliverables.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, all unit tests green (no regressions; no new pure-function
unit tests added — see "Honest limits" above). Not pushed / no PR opened (per
task instruction).

## 2026-07-27 — NURU LIVE EPIC COMPLETE (L0→L4, one day)
Self-hosted zero-cost live video/audio, spec pathway docs/LIVE_STREAMING.md:
L0 MediaMTX on VPS (RTMP 1935 in, HLS via nginx /live/, records fMP4).
L1 backend (pathway#393 mig178 + #395): RBAC live:go|manage, per-stream keys,
auth webhook (a key opens only its own stream — ground-truth verified in prod),
/live/now, heartbeats, recording registrar. L1.5 (#394): Cloudflare R2 free-
egress fan-out — church streams served from pub-086….r2.dev via publisher
daemon (systemd nuru-live-cdn); 1000 viewers = same origin load; ENDLIST
verified from the edge. L2 viewers (ios#97/android#66): Home LIVE banner, cell
card, player, Replays. L3 broadcasters (ios#98/android#67): HaishinKit /
RootEncoder 1080p H.264; Android agent found+fixed a real credential-dropping
URL-parse bug for the church path. L4 (ios#99/android#68): tab bar becomes
Home·Pathway·Plans·You·Live — You fuses Chat|Events|Give|Profile (Chat heart,
unread badge), Live tab only for live:go; Android alias-routes kept every
FCM/shortcut/deep-link site byte-identical; iOS added nuru://you|live hosts.
ON DEVICES: next builds (iOS 77+/Android vc48+). First-broadcast checklist in
[[live-streaming-epic]] memory.

## 2026-07-31 — Nuru Live L5 interactions (viewer) + flicker root-cause

Android had shipped L2-L4 (viewer, broadcaster, tab restructure) but NONE of
L5's interactions — iOS shipped reactions/hand/chat in build 91, Android's
`LivePlayerScreen` was still the silent L2 player. Built the full contract
(`pathway docs/LIVE_INTERACTIVE.md`, backend already live in prod):
POST reactions (like/love/fire), POST hand, GET/POST messages (3s poll,
since-cursor), GET pulse (5s poll — viewer_count/reactions/hands/guests),
POST/DELETE guest invite+respond (L6 scaffolding, no video yet).

**Design** (TikTok + Instagram Live + a classroom touch, per owner brief):
- Full-bleed chrome: top-left ✕, top-right pulsing-dot LIVE pill + eye/viewer
  count, a raised-hands "✋ N" chip stacked under it, broadcaster identity
  chip (initials avatar + name + title, IG-style) below.
- Right rail (TikTok): ❤️ 🔥 👍 stacked with abbreviated counts underneath
  (`abbreviateCount`: 999 / 1.2K / 10K / 1.5M — unit-tested), then ✋
  raise-hand (gold fill when raised) and 💬 chat toggle. Each tap fires a
  particle that floats up the rail with jitter+fade (`LiveParticleController`
  + `LiveParticleLayer`); `pulse.recent_reactions` also seeds a slow ambient
  trickle from OTHER viewers' taps (first poll only seeds the "seen" set —
  it never bursts a stream's whole reaction history on open). Double-tap
  anywhere on the video fires ❤️ + a big IG-style heart pop at the tap point.
  Both burst types are skipped when the system "Remove animations"
  accessibility setting is on (`ANIMATOR_DURATION_SCALE == 0`) — only the
  counter itself still updates, per the design spec's "counter pop only".
- Floating chat (owner's exact spec — NOT a sheet): anchored bottom-left,
  ~2/3 width, translucent dark rounded panel, last 6 messages with the
  oldest fading toward the top, name in gold / body in white; 💬 reveals a
  translucent input pill above it.
- Guest invite (L6 scaffolding only — no WHIP/WHEP video, that is its own
  later phase per the wire contract): a gold "You're invited on stage" card
  with Accept/Decline when `pulse.guests` has me as `invited`; an
  "On stage soon" chip once accepted.
- Home header: reused the exact "church stream is live" boolean Home already
  computes (`churchLive`, vc49 discovery), added a `LiveHeaderChip` between
  the bell and radio icons — same 40dp circle language as
  `CircleButton`, but a breathing red ring instead of a static gold border.
- New files: `feature/live/LiveInteractions.kt` (rail, particles, chat
  overlay, hand/guest chips, `abbreviateCount`, `myHandRaised`/
  `myGuestStatus` — the pure matchers are unit-tested in
  `LiveInteractionsTest`). `LivePlayerScreen.kt` rewritten to wire it all to
  the new `MemberApi` endpoints + `LiveDtos.kt` additions (`LivePulse` with a
  forward-tolerant `reactions: Map<String,Int>` — NOT a fixed {like,love}
  shape — specifically so the backend's in-flight "fire" widening decodes
  without a client change; covered in `LiveDtoTest`).

**Flicker bug — root cause, not a patch.** Owner report: opening the viewer
briefly shows the PREVIOUS test broadcast before snapping to the current one.
Traced the whole chain per the reliability doctrine, not just the symptom:
read `packages/backend/src/modules/live/service.ts` (`listNow`, read-only —
pathway repo untouched) and confirmed the client never reuses a stale
ExoPlayer instance across streams (each `LivePlayerScreen` mount builds a
brand-new `ExoPlayer` via `remember(streamId)`, released in `onDispose`; no
`CacheDataSource`/on-disk HTTP cache is configured, so there is no local
response cache to serve stale segments either). The real cause is server/CDN:
for church-scope streams with `LIVE_CDN_BASE` set, `hls_url` is always the
literal string `"{cdnBase}/live-cdn/church/index.m3u8"` — **identical for
every church broadcast, forever, with no stream_id in the path.** Cloudflare
R2 mirrors that one path from the VPS origin on a lag; the first few seconds
after a NEW stream starts, the edge can still be serving the PREVIOUS
stream's manifest/segments at that same URL — exactly the reported symptom,
and impossible to fully fix from the client because the URL genuinely is
shared.

Client mitigation shipped (`LivePlayerScreen.kt`, see its header comment):
the service already computes a per-request `hls_fallback_url` (the direct
origin path, no CDN in front of it, so it always reflects whatever is
*actually* live right now) but Android was dropping it on the floor. Wired
it through `LiveNowRow.hlsFallbackUrl` → `liveNowRoute()` → the nav args →
the player: on open, a live church stream starts on the fallback/origin URL
for an 8s warm-up window (`CDN_WARM_UP_MS`), then swaps the `MediaItem` to
the CDN url once R2's mirror has almost certainly caught up — the viewer
never touches the CDN's stale copy during the window where staleness is
possible. Also hardened the player's `remember`/`DisposableEffect` key from
`url` to `streamId ?: url`, so even though the CDN url text is identical
across streams, a *new* stream can never accidentally inherit a *previous*
stream's still-alive player instance.

**The correct permanent fix is server-side** (recommended, NOT implemented —
this session may not touch the pathway repo): scope the CDN mirror path by
`stream_id`, e.g. `/live-cdn/church/{stream_id}/index.m3u8`, so a new
broadcast can never alias a previous one's cached objects at all; or, if the
path must stay stable, set a very short `Cache-Control`/TTL on the manifest
object (segments are already uniquely named per-stream by MediaMTX, so the
manifest is almost certainly the only object actually at risk) and/or purge
the R2 object on stream start. Filed for the pathway repo owner, not
actioned here.

**Honest limits**: double-tap-to-heart only wires to the video surface
(`AndroidView` branch) — audio-kind streams (`AudioBackdrop`) have no video
to tap, so that gesture is unavailable there (the rail's ❤️ button still
works). Reaction/hand optimistic updates are "self-healing" via the 5s pulse
poll rather than fully reconciled with rollback-on-failure (the verse-
reaction pattern elsewhere in this app does roll back; skipped here since a
dropped POST is invisible for at most one 5s poll on a fire-and-forget
gesture). The guest-invite card has no push-driven auto-open — it only
appears once the 5s pulse poll picks up the invite, so a backgrounded app's
system notification (existing `live_guest_invite` FCM template) is still the
real-time path; foreground pickup can lag up to 5s. No Robolectric/instrumentation
test exercises the actual Compose overlay (rail taps, particle lifecycle,
chat scroll) — only the pure functions (`abbreviateCount`,
`myHandRaised`/`myGuestStatus`) and DTO decoding are unit-tested, consistent
with this codebase's existing test coverage posture for Live/Compose code.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, all 8 test classes green (36 tests, 0 failures/errors;
13 new: 7 `LiveInteractionsTest` + 6 `LiveDtoTest`). Not pushed / no PR
opened (per task instruction — isolated worktree `feat/live-viewer-l5`).

## 2026-07-31 — Nuru Live L5 broadcaster interactions + orientation-follow fix

Second half of L5 parity: Android's viewer side shipped earlier today
(previous entry above); this pass brings the **broadcaster** HUD
(`LiveBroadcastScreen.kt`) to iOS parity (`GoLiveBroadcastView.swift` +
`BroadcastController.swift`, `nuru-member-ios`), plus a real bug fix the
task specifically called out. Isolated worktree `feat/live-broadcaster-l5`
off `origin/main` (`41e9dd8`, includes PR #73's viewer L5). Not pushed.

**1. Orientation-follow fix (port of iOS 80f4fdd).** Was: `LiveBroadcastScreen.kt`
hard-coded `rotation = 90` in `GenericStream.prepareVideo(...)`, so a
broadcast was encoded portrait 1080×1920 EVEN WHEN THE PHONE WAS PHYSICALLY
HELD LANDSCAPE — the video came out sideways for any landscape broadcaster.
Root-caused against the actual pinned dependency rather than guessed:
pulled the real RootEncoder 2.5.9 sources from GitHub (`gh api
repos/pedroSG94/RootEncoder … ?ref=2.5.9`, matching `gradle/libs.versions.toml`'s
`rootEncoder = "2.5.9"` — this app's `.aar`s in the Gradle cache ship no
sources jar) rather than guessing at the API:
- `library/src/main/java/com/pedro/library/base/StreamBase.kt`
  (`prepareVideo`) computes the ACTUAL encoder width/height from its
  `rotation: Int` param internally — `if (rotation == 90 || rotation == 270)
  glInterface.setEncoderSize(height, width) else setEncoderSize(width,
  height)`. So no manual width/height swap is needed on the call site (the
  task flagged this as a possibility to check) — `VIDEO_WIDTH`/
  `VIDEO_HEIGHT` stay the fixed "landscape-native" 1920×1080 base for BOTH
  orientations; only the `rotation` flag changes. This is the exact pattern
  RootEncoder's own bundled sample app uses (`app/src/main/java/com/pedro/
  streamer/rotation/CameraFragment.kt`: `rotation = if (isVertical) 90 else 0`).
- `encoder/src/main/java/com/pedro/encoder/input/video/CameraHelper.java`'s
  `getCameraOrientation(Context)` is the library's own canonical helper for
  reading "how is the phone held, as a `prepareVideo`-ready rotation value":
  it reads `windowManager.getDefaultDisplay().getRotation()` and converts
  Surface.ROTATION_0/90/180/270 to 90/0/270/180 respectively — exactly the
  convention `prepareVideo`'s `rotation` param expects, and the same helper
  RootEncoder's own `ScreenOrientation.lockScreen()` sample uses. This is
  already on the app's classpath (the "encoder" module RootEncoder pulls in
  transitively — `Camera2Source`/`MicrophoneSource` from the same module were
  already imported here).
- **The fix**: call `CameraHelper.getCameraOrientation(context)` ONCE, inside
  the same `remember { }` block that builds the broadcaster and calls
  `prepareVideo` — i.e. decided at go-live and never touched again for the
  rest of the session, per the task's ask. `VideoBroadcaster` now carries
  that locked `rotation` value and, on camera flip, defensively re-asserts it
  via `stream.setOrientation(cameraOrientationFor(rotation))` — verified
  against `Camera2Source.switchCamera()`'s 2.5.9 source that this is a no-op
  TODAY (it only stops/restarts capture on the same `glInterface` surface and
  never touches orientation — only `StreamBase.changeVideoSource()`, a call
  path this app never uses, does that), kept as cheap insurance against a
  future library version changing that behavior — mirrors the iOS port's own
  "re-attach resets it" comment for the equivalent HaishinKit guard.
  `cameraOrientationFor(rotation)` (`if (rotation == 0) 270 else rotation -
  90`) is `StreamBase.prepareVideo`'s own internal formula, extracted as a
  small pure `internal fun` and unit-tested for all four rotations
  (`LiveBroadcastInteractionsTest`).
- Left `getGlInterface().autoHandleOrientation = true` untouched (task said
  keep everything else the same) — verified against `GlStreamInterface.kt`
  that this only wires a LIVE `SensorRotationManager` that keeps re-asserting
  camera-tilt compensation via `setCameraOrientation`/`setIsPortrait` for the
  rest of the broadcast (keeps footage upright if the phone tilts slightly);
  `encoderWidth`/`encoderHeight` are set ONCE by `prepareVideo` and never
  touched again by that sensor callback, so it doesn't reopen the "resolution
  changes mid-stream" hole this fix closes — orthogonal, not a conflict.
- **Mechanism, stated plainly**: two rotation values, chosen once —
  `rotation=0` (phone held landscape at go-live) encodes 1920×1080;
  `rotation=90` (portrait, or any other physical orientation
  `CameraHelper.getCameraOrientation` doesn't map straight to 0) encodes
  1080×1920. `CameraHelper.getCameraOrientation` actually returns all four of
  0/90/180/270 (reverse-portrait/reverse-landscape included), and
  `prepareVideo`'s own `isPortrait = rotation == 90 || rotation == 270` check
  means both portrait values (90 and 270) correctly resolve to the 1080×1920
  encode shape — a broader fix than the task's literal "two cases" framing,
  gotten for free from reading the real formula instead of hand-rolling one.

**Honest limits on the orientation fix**: `CameraHelper.getCameraOrientation`
reads the WINDOW's current display rotation, which only tracks how the phone
is physically held if the OS's system-wide auto-rotate is ON — unlike iOS's
`UIDevice.current.orientation` (raw accelerometer, tracks physical
orientation even when the user has rotation-lock enabled). A broadcaster who
has Android's rotation lock on and is holding the phone landscape at go-live
will still get a portrait encode, because the window itself never rotated.
The task explicitly asked to "read the display rotation," which is what this
implements; a raw-accelerometer `OrientationEventListener` would be the
closer iOS-equivalent if that gap matters in practice, and is a natural
follow-up, not done here. Separately, and pre-existing/out of scope for this
fix either way: `MainActivity` declares no `android:configChanges`, so if the
device's Activity-level configuration genuinely rotates mid-broadcast (system
auto-rotate on, phone actually turned), Android recreates the whole Activity
— every `remember`-only broadcaster/HUD state (including the locked
orientation) is torn down and rebuilt from scratch, which would visibly
reset the HUD (elapsed timer, phase) even though the underlying server-side
stream row survives. This is a platform behavior that predates this change
and is orthogonal to which orientation gets chosen; not touched here.

**2. Broadcaster L5 HUD.** `LiveBroadcastScreen.kt`'s controls row grows from
`[mic, flip, ·, End]` to `[mic, End, flip, ✋, 💬]` — the exact order (End
moved next to mic, not pinned to the trailing edge) as the iOS port's
`controlsRow`, not a cosmetic choice: keeping literal parity with the
reference the task named. Reuses PR #73's L5 viewer plumbing wherever it
overlaps rather than re-deriving it: `LiveParticleController`/
`LiveParticleLayer` for the floating reaction burst, `myGuestStatus` (its
`(guests, userId)` signature is generic enough to answer "is THIS raised-hand
user already invited", not just "am I invited" — reused as-is for the hands
sheet), and `isReduceMotionEnabled` (previously private to
`LivePlayerScreen.kt`; promoted to `LiveInteractions.kt` so both viewer and
broadcaster share the exact same Reduce-Motion detection instead of a second
copy).
- **✋ hand button**: gold-badged with the raised-hand count (badge hidden at
  0, capped display at 99), fed by a NEW 3s `getLivePulse` poll (the wire
  contract's broadcaster cadence — viewer polls at 5s) folded into the same
  "only while `phase == LIVE`" idiom the existing viewer-count poll in this
  file already uses. Opens `LiveHandsGuestsSheet` (new file,
  `LiveBroadcastInteractions.kt`): a `ModalBottomSheet` (matching
  `GoLiveSetupSheet`'s existing sheet convention in this codebase) listing
  raised hands (avatar/name/"raised Xm ago") each with "Invite to join" (POST
  `guests/:userId`, disabled + relabeled "6 guests max" at the L6 cap) or,
  once already invited/accepted, a status label instead of the button; a
  "Lower" pill that's LOCAL-ONLY UI (the wire contract's `POST /hand` always
  targets the calling user — there is no broadcaster-authority "lower
  someone else's hand" endpoint), documented as such, matching the iOS
  port's identical doc comment and identical `remember`-scoped-to-sheet-
  lifetime behavior. A guests section (mirrors `pulse.guests`, filtered to
  `LiveGuestRow.isActive` — a new small extension property, `status ==
  "invited" || "accepted"`, ported from the iOS model's `isActive`) with
  "Remove" (`DELETE guests/:userId`); an accepted guest reads "Joining soon —
  video in a later update" rather than silently implying real video (L6
  video is its own later phase per the wire contract). Both invite/remove
  call an immediate `onRefreshPulse()` (the sheet's own out-of-cadence pulse
  refresh, mirroring iOS's `pollPulseNow()`) rather than waiting out the rest
  of the 3s window.
- **💬 chat button**: opens `LiveBroadcastChatSheet` (new, same file) — a
  `ModalBottomSheet`, NOT the viewer's floating `LiveChatOverlay`. Documented
  choice: the task left this open ("your call"); iOS also chose a sheet here,
  for the same reason — the broadcaster is filming through their own camera
  preview, and a translucent overlay ACROSS that preview (viewers see it
  over passive video; a broadcaster would see it over their own live framing)
  is a worse fit than a dismissible sheet. 3s-polled since-cursor message
  list (same cadence/shape as the viewer path, reusing the identical
  `getLiveMessages`/`postLiveMessage` DTOs) + composer; light-bubble style
  (mine = `Nuru.myBubble`, others = white + name), NOT the full Aurora chat
  thread (no read receipts/offline queue/edit/delete) — same deliberately
  reduced scope as the iOS sheet.
- **Floating reactions**: `pulse.recent_reactions` seeds `LiveParticleLayer`
  exactly like the viewer path (first poll only seeds the "seen" set, never
  bursts history), anchored bottom-end above the controls row. Reduce Motion
  swaps to a static "❤️ N" running-total chip (`ReactionCounterChip`, new)
  instead of suppressing the feature — same fallback shape as both the
  viewer overlay and the iOS port's `ReactionBurstQueue.reduceMotionTotal`.

**Files**: `feature/live/LiveBroadcastScreen.kt` (orientation fix, L5 state/
polling/HUD wiring, controls-row reorder), `feature/live/
LiveBroadcastInteractions.kt` (new — hands/guests sheet, chat sheet,
`isActive`, `MAX_GUESTS`), `feature/live/LiveInteractions.kt`
(`isReduceMotionEnabled` promoted from `LivePlayerScreen.kt`, now shared),
`feature/live/LivePlayerScreen.kt` (that promotion — no behavior change),
`feature/shell/MainShell.kt` (plumbed `myUserId = me?.profile?.userId` into
`LiveBroadcastScreen`, mirroring the existing `LivePlayerScreen` call one
route above it, so the chat sheet can tell "mine" bubbles apart).

**Honest limits (HUD)**: no Robolectric/instrumentation test exercises the
actual Compose sheets or HUD (rail taps, sheet list rendering, chat scroll)
— only the pure functions (`cameraOrientationFor`, `LiveGuestRow.isActive`,
`MAX_GUESTS`) are unit-tested, consistent with this codebase's existing Live/
Compose test coverage posture (see the viewer L5 entry above, and PR #73's
audit note, for the same tradeoff made the same way). The chat sheet has no
push-driven auto-open and no unread badge on the 💬 button — a broadcaster
only sees new messages by having the sheet open; out of scope here, same as
the viewer path's guest-invite card lacking a push-driven auto-open. L6
guest video (WHIP/WHEP) remains scaffolding-only on both surfaces, per the
wire contract — this pass only manages invite/accept/remove state, same as
the viewer side already did.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, all 9 test classes green (43 tests, 0 failures/errors; 7
new in `LiveBroadcastInteractionsTest`: 4 `cameraOrientationFor` rotations +
2 `LiveGuestRow.isActive` cases + 1 `MAX_GUESTS` pin). Not pushed / no PR
opened (per task instruction — isolated worktree `feat/live-broadcaster-l5`).

## 2026-07-31 — Broadcast Studio: full-bleed stage, background persistence, Camera/Screen/Document source switcher (branch feat/broadcast-studio, this repo only, on top of #73 + #74)

Owner-directed rework of the Go Live screen (screenshot evidence): plain
letterboxed preview with black bars, controls sitting in the black band,
title clipped under the gesture nav bar, no screen/document sharing, and the
stream died the instant you left the live page. All four fixed. Built on top
of origin/main tip (#75, which already includes #73 viewer L5 + #74
broadcaster L5 HUD + orientation-follow) in an isolated worktree
(`.worktrees/bcast-studio`), not pushed.

**1. Full-bleed stage (no more black bars).** Root cause verified against
the pinned RootEncoder 2.5.9 source, not guessed: `GlStreamInterface`
defaults `aspectRatioMode = AspectRatioMode.Adjust`, and
`SizeCalculator.calculateViewPort()`'s `Adjust` branch fits the stream
*inside* the preview view (classic letterbox/pillarbox) — that default was
never overridden anywhere in the old screen. `Fill` runs the same formula
inverted (scales so the stream *covers* the view, cropping the overflow) —
exactly TikTok-style center-crop. Fix is one call at engine build time:
`stream.getGlInterface().setAspectRatioMode(AspectRatioMode.Fill)`
(`LiveBroadcastEngine.kt`, `buildBroadcaster()`). Separately, the HUD itself
was rebuilt so every element floats with its own `WindowInsets` handling
instead of one shared `Column.statusBarsPadding().padding(16.dp)` (the old
layout only padded the TOP for the status bar — nothing accounted for the
bottom gesture-nav inset, which is what actually clipped the title/controls
row on 3-button/gesture-nav devices). New layout: top-left LIVE pill+
duration+watching (`statusBarsPadding()`), bottom-left title chip and
bottom-center controls row, each independently `navigationBarsPadding()`'d
(`LiveHudOverlay` in `LiveBroadcastScreen.kt`). `MainShell.kt`'s `NavHost`
also now skips Scaffold's own content padding entirely for the
`live-broadcast` route (`val contentPadding = if (onLiveBroadcast)
PaddingValues(0.dp) else pad`) so the video genuinely reaches every edge
instead of being pre-inset by the Scaffold slot the way every other
destination is.

**2. Background persistence — the actual headline change.** The RootEncoder
engine (`GenericStream`/`GenericOnlyAudio`, wrapped as before by the
`Broadcaster` interface) moved OUT of the Compose screen entirely and into a
new foreground `Service` (`LiveBroadcastService.kt`) that owns it for the
whole life of a broadcast — a state-machine rewrite of what used to live as
`remember`/`LaunchedEffect` state directly in `LiveBroadcastScreen.kt` (phase,
retry/backoff, ConnectChecker callbacks, mute, camera flip, source switch,
End). `LiveBroadcastScreen.kt` is now a thin observer: it never touches
RootEncoder directly, only through `BroadcastController` (new, singleton,
same "bind once, mirror a StateFlow" idiom as the existing `RadioController`
— it binds to the service, republishes `service.state` into its own
`StateFlow<BroadcastState>`, and every action method is a one-line delegate
to the bound service).
- **The `ON_STOP`-ends-the-broadcast code is GONE, deliberately.** The old
  screen had `LifecycleEventEffect(ON_STOP) { endStream(background = true) }`
  — backgrounding, switching tabs, or locking the phone always ended the
  stream. There is no replacement for it: nothing in the new screen reacts to
  `ON_STOP` at all. The service (started via
  `ContextCompat.startForegroundService` + `ServiceCompat.startForeground`
  with a typed foreground service, `camera|microphone` narrowing to
  `microphone|mediaProjection` while screen-sharing) keeps running
  independent of the Activity/Compose lifecycle entirely; that IS the fix.
- **View re-attach, verified against the pinned source, not guessed.**
  `StreamBase.startPreview(surface, width, height)` / `stopPreview()`
  (library/src/main/java/com/pedro/library/base/StreamBase.kt) only touch
  `glInterface.attachPreview()/deAttachPreview()` — `stopSources()`'s
  `videoSource.stop()` and `glInterface.stop()` calls are BOTH gated on
  `if (!isOnPreview) ...`, and separately `stopStream()`'s own teardown path
  never touches the preview surface at all. So while `isStreaming == true`,
  detaching the preview (leaving the screen) neither stops camera capture
  nor drops the RTMP publish — only the local preview surface goes away;
  `startSources()`'s `addMediaCodecSurface(videoEncoder.inputSurface)` (set
  once at `startStream()`) is what actually feeds the encoder, and it's
  completely independent of the preview attach state. Compose reaches this
  through the exact same `SurfaceHolder.Callback` idiom the old screen used
  (`surfaceCreated → attachPreview`, `surfaceDestroyed → detachPreview`),
  just redirected to `BroadcastController.attachPreview/detachPreview()`
  instead of a locally-owned `broadcaster` val — the SurfaceView's own OS
  lifecycle (created when the composable is placed and visible, destroyed
  when it isn't) already IS "detach on navigate-away/background, re-attach
  on return," so no extra `ON_STOP`/`ON_RESUME` wiring was needed at all.
- **Persistent notification**: "● LIVE — `<title>`" on a new `nuru_live_
  broadcast` channel (`IMPORTANCE_LOW`, silent — this is a status
  indicator, not an alert), tapping it re-opens the exact broadcast route via
  the existing `PendingDest`/`nuru.dest` deep-link plumbing (same mechanism
  `RadioService` already uses to reopen the radio player); its "End" action
  is a `PendingIntent.getService(...)` targeting a new `ACTION_END` the
  service's `onStartCommand` checks for, routing to the SAME
  `endBroadcast()` path a manual End-button tap uses (tagged
  `viaNotification = true` só the eventual Summary screen — if anyone's
  still looking at it — reads "Nuru Live ended" instead of "You were live!").
- **In-app "tap to return" pill** (`BroadcastReturnBar`, new, in
  `LiveBroadcastScreen.kt`): the exact same idiom as the existing viewer-side
  `AppLiveBar` (`LiveDiscoveryUi.kt`) one screen over — a slim strip in
  `MainShell`'s `Scaffold.bottomBar`, above the bottom nav, shown on every
  screen but the broadcast screen itself while `BroadcastController.state`
  has an active (non-SUMMARY) session, ticking its own local `mm:ss` off the
  service's `startedAtMillis`. Tapping it rebuilds the same
  `live-broadcast?...` route from the running `BroadcastSession` (new
  `liveBroadcastRoute(BroadcastSession)` overload in `LiveRoutes.kt`,
  shared with the notification's own PendingIntent so there's exactly one
  route-string builder, not two copies drifting apart). Also fixed, while
  touching this code: the pre-existing viewer `AppLiveBar` could show
  "someone else is live, join" while YOU were the one broadcasting — now
  explicitly excluded via the same `onLiveBroadcast` route check.
- **Service lifecycle / cleanup**: `LiveBroadcastService` is
  started+bound (started so it survives independent of any binding; bound
  so the Controller/UI can reach it). `endBroadcast()` stops the RootEncoder
  engine, best-effort `POST /live/streams/:id/end` (same fire-and-forget-on-
  network-failure precedent as sign-out never blocking on logout), sets
  phase to `SUMMARY`, then `stopSelf()`s its "started" life. Because a bound
  service with an active binding doesn't actually get destroyed by
  `stopSelf()` alone, `BroadcastController` watches for `phase == SUMMARY`
  (a separate one-shot `svc.state.first { ... }` coroutine, NOT folded into
  the state-mirroring `collect{}` loop — self-cancelling a coroutine from
  inside its own `StateFlow.collect{}` block hit a genuine Kotlin type-
  inference recursion error, "Type checking has run into a recursive
  problem," and needed both the split into two coroutines AND an explicit
  `ServiceConnection` type annotation on the `connection` property to
  resolve) and unbinds once it sees it, letting the service actually die
  instead of lingering as a dead-weight bound instance for the rest of the
  app session.

**3. Source switcher — Camera / Screen / Document.** New `Tune` icon button
in the controls row (video kind only) opens `LiveSourceSheet`
(`LiveBroadcastSourceUi.kt`, new file).
- **Runtime switching is genuinely live, not faked.** Verified against
  `StreamBase.changeVideoSource(source: VideoSource)`: it stops+releases the
  OLD `VideoSource`, starts the NEW one on the SAME
  `glInterface.surfaceTexture`, and does none of this through
  `stopStream()`/`startStream()` — `isStreaming` (and the RTMP publish
  itself) never drops. This is the exact mechanism RootEncoder's own
  `app/src/main/java/com/pedro/streamer/screen/ScreenService.kt` sample uses
  for its camera↔screen toggle (fetched and read in full, not inferred),
  including the orientation handling that sample's own comment states
  verbatim: "ScreenSource need use always setCameraOrientation(0) because
  the MediaProjection handle orientation. You also need remove
  autoHandleOrientation if you are using it" — ported into
  `VideoBroadcaster.useScreenSource()`/`useCameraSource()`
  (`LiveBroadcastEngine.kt`), which also restores whichever camera facing
  (front/back) was active before a screen-share round-trip (`Camera2Source.
  switchCamera()` just flips an internal field when the source isn't
  running yet — verified — so calling it on the freshly-constructed
  `Camera2Source` before handing it to `changeVideoSource()` is safe).
- **Camera**: unchanged behavior, default on entry.
- **Screen share**: `MediaProjectionManager.createScreenCaptureIntent()`
  via `rememberLauncherForActivityResult` (must be launched from the
  Activity — the sheet/Service can't own this consent dialog) →
  `BroadcastController.switchToScreenSource(resultCode, data)` →
  `LiveBroadcastService` re-asserts `startForeground` with the
  `mediaProjection` type added, calls `mediaProjectionManager.
  getMediaProjection(resultCode, data)`, and hands it to
  `useScreenSource()`. Mic keeps streaming throughout (untouched — only the
  video source changes).
- **Document**: SAF `ActivityResultContracts.OpenDocument()` (PDF mime)
  picks a `Uri`, THEN the same MediaProjection consent flow runs (Document
  mode rides on the identical Screen source at the engine layer — see below)
  → a full-screen in-app pager (`DocumentPagerScreen`, `HorizontalPager` +
  `android.graphics.pdf.PdfRenderer`, one page open/rendered/closed at a
  time under a `Mutex` since `PdfRenderer` only allows a single open `Page`)
  replaces the camera preview. Because MediaProjection mirrors the WHOLE
  display compositor output (`DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_
  MIRROR`, verified against `ScreenSource.kt`), whatever Compose draws on
  Nuru's own screen IS what's captured — so the pager, drawn full-screen
  locally, is exactly what the congregation sees; no extra plumbing needed
  beyond "put the right UI on screen while the Screen source is active."
- **Engine-vs-UI split**: `BroadcastSource` at the engine layer is only
  `CAMERA`/`SCREEN` — "Document" is a Compose-only mode layered on top of
  `SCREEN` (a `documentUri: Uri?` local to `LiveBroadcastScreen`, gated by
  `documentUri != null && state.source == SCREEN`), not a third engine
  state, since the RootEncoder video source is identical either way.

**Honest, documented limit (source switcher chrome):** MediaProjection
captures the whole display, so there is no way to draw broadcaster-only HUD
chrome that viewers won't also see once Screen/Document is active — unlike
Camera mode, where the HUD lives purely in Compose, entirely separate from
the raw sensor frames RootEncoder encodes. Rather than either (a) hide End/
mute entirely behind "switch back to camera first" — a real safety gap for a
live tool — or (b) keep showing the FULL camera-mode HUD (LIVE pill,
hand/chat buttons, everything) baked into the stream, both Screen and
Document modes deliberately show a MINIMAL control set only (`ScreenMode
Controls`: mic mute, a "Sharing your screen · switch to camera" chip, End;
`DocumentPagerScreen`'s own page-indicator + mute/End/exit row) — the same
tradeoff every mobile screen-share tool with an in-app control accepts, not
a bug. Raised hands / live chat are unreachable while Screen/Document is
active (by the same logic — the full HUD housing those buttons is
intentionally suppressed); switching back to Camera restores them. Peak-
viewer tracking (`peakViewers`, still Compose-local, resubscribes on return
rather than living in the Service) resets to 0 if you fully navigate away
from the broadcast screen and back (e.g. via a bottom-tab switch, which
`popUpTo`s the route out of the back stack) — a cosmetic-only tradeoff, not
a data-loss one, since `/live/now`'s viewer count itself stays server-
authoritative throughout. Pulse polling (raised hands, reactions, chat) is
likewise resubscribe-on-return, not service-owned, per the task's own
either/or framing — nothing is lost since every one of those is an
idempotent GET.

**Manifest**: `FOREGROUND_SERVICE_CAMERA` / `FOREGROUND_SERVICE_MICROPHONE`
/ `FOREGROUND_SERVICE_MEDIA_PROJECTION` added (alongside the pre-existing
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for Radio);
`LiveBroadcastService` declared with `android:foregroundServiceType=
"camera|microphone|mediaProjection"` (the manifest-declared superset —
`ServiceCompat.startForeground()`'s runtime type param narrows to whichever
subset is actually active at each point in the session, gated
`Build.VERSION_CODES.R` since `FOREGROUND_SERVICE_TYPE_CAMERA`/
`_MICROPHONE` need API 30, below which the type param is a no-op — correct,
since pre-R foreground services aren't typed at all).

**Files**: `feature/live/LiveBroadcastEngine.kt` (new — `Broadcaster`/
`VideoBroadcaster`/`AudioBroadcaster`/`buildBroadcaster()` moved out of the
screen so the Service can own them; `buildPublishUrl()` extracted as a pure,
now-tested function; `BroadcastPhase`/`BroadcastSource`/`BroadcastSession`/
`BroadcastState`), `feature/live/LiveBroadcastService.kt` (new — the
foreground service), `feature/live/BroadcastController.kt` (new — the
singleton facade), `feature/live/LiveBroadcastSourceUi.kt` (new — source
sheet, `ScreenModeControls`, `DocumentPagerScreen`/`PdfPage`),
`feature/live/LiveBroadcastScreen.kt` (rewritten — thin observer, rebuilt
inset-aware HUD, `BroadcastReturnBar`), `feature/live/LiveRoutes.kt`
(`liveBroadcastRoute(BroadcastSession)` overload), `feature/shell/
MainShell.kt` (`BroadcastReturnBar` wiring, `onLiveBroadcast` route guard on
both the viewer `AppLiveBar` and the Scaffold content padding),
`AndroidManifest.xml` (permissions + service declaration),
`app/src/test/java/.../LiveBroadcastInteractionsTest.kt` (+2:
`buildPublishUrl` cases for the flat-scope and nested-scope publish URLs).

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 45 tests, 0 failures/errors (43 baseline + 2 new). Not
pushed / no PR opened (isolated worktree `.worktrees/bcast-studio`, branch
`feat/broadcast-studio`, built on top of origin/main #75).

## 2026-07-31 — Nuru Live "taste pass": floating draggable chat, auto-collapsing guest banners, chrome polish, Audience picker, Live hub hero + My Broadcasts, end-of-broadcast stewardship (branch feat/live-taste, this repo only, on top of #73/#74/#76)

Owner feedback + a TikTok Live reference session, run against the finished
Nuru Live epic (L0→L4 + Broadcast Studio) — polish and information-
architecture, no new wire surfaces beyond two small additive endpoints. All
Nuru brand tones throughout (gold `Nuru.gold`/`goldGradient` — the gradient's
middle stop is literally `0xC9A227` — navy `homeNavy`/`homeNavyGradient`,
paper `Nuru.paper`); every feature from the L5/L6/Broadcast Studio arcs is
still present, nothing was removed, only re-presented.

**1. Auto-collapsing guest banners** (`LiveInteractions.kt`'s new
`GuestStageBanner`, replacing the raw `when(myGuestState)` block in
`LivePlayerScreen.kt`): the invite card and the "on stage soon" chip both
start expanded and collapse to a small gold corner pill (`GoldCornerPill`)
~3s later (`LaunchedEffect(status, expanded)`, re-arms itself every time
`expanded` flips back to `true`) — tap the pill to expand again, which
re-arms the same 3s timer. Keyed on `status` (invited/accepted) so accepting
or declining swaps in a fresh banner that starts expanded, satisfying "same
for the invite card after responding" without extra state.

**2 + 3. Shared floating draggable chat** (`LiveInteractions.kt`'s new
`LiveFloatingChat`, ~180 lines) — the single biggest change. Replaces BOTH
the viewer's old fixed bottom-left `LiveChatOverlay` (deleted) AND the
broadcaster's `ModalBottomSheet`-based `LiveBroadcastChatSheet` (deleted from
`LiveBroadcastInteractions.kt`) with one presentational composable used from
both `LivePlayerScreen.kt` and `LiveBroadcastScreen.kt`. Sized ~55%w×26%h by
default, draggable by its header only (`detectDragGestures`, keyed on the
measured container `IntSize` so a rotation/resize restarts the gesture
detector with fresh bounds rather than dragging against stale ones),
collapsible to a 52dp chat-bubble FAB with a small gold unread dot — the FAB
is independently draggable the same way. Offsets live in plain `remember`
(no key), so position survives collapse/expand for the life of the screen —
"remembered in-session," never persisted. The clamp math itself
(`clampDragOffset(offset, containerSize, elementSize)`) is a pure top-level
function specifically so it's unit-testable without Compose/Robolectric —
4 new cases in `LiveInteractionsTest.kt` (inside bounds untouched, negative
clamps to the origin, an offset past the far edge clamps so the element
stays fully visible, an element larger than its own container never
produces a negative max). The broadcaster side required lifting message
polling (3s since-cursor, same cadence as the viewer) out of the deleted
sheet and into `LiveBroadcastScreen.kt` itself, mirroring exactly what
`LivePlayerScreen.kt` already did — the presentational component only ever
takes `messages`/`onSend` as props, per this file's existing "pure UI, wire
calls stay in the screen" rule.

**4. Chrome polish**: `BroadcasterIdentityChip` (`LivePlayerScreen.kt`) now
prefers a real Coil `AsyncImage` avatar (`startedByAvatarUrl`, new
forward-tolerant nullable field on `LiveNowRow`/threaded through
`liveNowRoute`/the `live-player` nav route/`LivePlayerScreen`'s params) over
gold initials, and carries a small gold "HOST" tag next to the name. Every
round chrome control — the viewer's action rail buttons AND the broadcaster
HUD's mic/End/flip/source/hand/chat circles — now share one
`LiveChromeCircleSize` (48dp) / `LiveChromeCircleBg` (`Color.Black` @ 0.38
alpha) constant pair instead of three slightly different sizes/opacities
across two files. A new `GentleEntrance` wrapper (fade + 10dp rise, 360ms)
wraps the LIVE pill, host chip, and action rail (viewer) and the HUD's top
pill + bottom controls row (broadcaster) — Reduce Motion (already-detected
via the existing `isReduceMotionEnabled`) just shows everything immediately,
consistent with every other motion decision already in this file. The
eye-glyph "· 👁 N" watching pill next to LIVE already existed from the L5
session — left as-is, it already matched the ask.

**5. Go Live Audience picker** (`GoLiveSetupSheet.kt`): the old cramped
"Where" chip pair (Church / My cell, shown only when BOTH were eligible) is
now an "Audience" section of full-width rows (`AudienceOption` — navy when
selected, a gold check dot), one row per scope the member is actually
eligible for, so a member with only one eligible scope now sees a labeled
row instead of no picker at all. The cell row's subtitle names the actual
cell ("Only $name sees this") by reusing the SAME `GET /me/cell-summary`
fetch `CellInfoScreen.kt` already makes — no new endpoint. Wire contract is
byte-for-byte unchanged (`scope` "church"|"cell" + `cell_id`); this is
presentation only. Honest limit: the member data model only carries one
`cellGroupId` per user (no `leader_assignments` list ever reaches the
client — `GoLiveShared.kt`'s own header comment already flags this), so
"the broadcaster's own cells" is really "the broadcaster's own cell,"
singular, same limit the L3 session already documented.

**6. Live hub redesign** (`LiveTabScreen.kt`, effectively rewritten): a navy
hero "studio card" (`LiveHeroCard`) fronts the tab — Fraunces `NuruType.
display` "Nuru Live", a warm caption, and a large gold `BreathingGoLivePill`
(a second, slightly-scaled-up gold pill behind the real one, animating
scale 1→1.3 / alpha 0.4→0 on an infinite 1900ms loop — skipped entirely
under Reduce Motion, not just sped up, since it's purely decorative). While
a CHURCH-scope stream is live (`LiveDiscoveryCenter.streams`, the same
app-wide source of truth the AppLiveBar already uses — refreshed once more
on entering this tab for a snappier cold-open rather than waiting out
MainShell's 75s cadence) the hero SWAPS to a "● LIVE now — Watch/Return"
pill: "Return" when `BroadcastController`'s own active session IS that
church stream (i.e. it's the broadcaster's own, still running), "Watch"
otherwise. The "BROADCAST" kicker's owner-reported clipping got a defensive
fix (an explicit `Spacing.sm` top margin ahead of Scaffold's own status-bar
inset) — honest limit: root cause was never conclusively reproduced against
a real device/emulator screenshot in this text-only session, so this is
insurance, not a proven fix; flagged for a follow-up visual check.
"Replays" → **My Broadcasts**: `GET /live/recordings/mine` (new
`MemberApi.getMyLiveRecordings`, already staged pre-session) replaces the
old `ReplaysList` reuse (which showed every PUBLIC replay, not just this
broadcaster's own — the viewer-facing `LiveReplaysScreen`/`ReplaysList` are
untouched and still serve that separate purpose from `EndedState`'s "View
Replays"). Each row (navy tile + kind glyph, Fraunces `NuruType.rowTitle`,
date + scope chip — `replayDate()` in `LiveReplaysScreen.kt` widened from
`private` to `internal` so both files share one formatter) carries a ⋮
`DropdownMenu` (Play · Share · Delete), matching `PrayerJournalScreen.kt`'s
existing menu idiom: Share resolves `recording_url` to an ABSOLUTE url via
`Net.client.resolveMediaUrl` (which resolves against `BuildConfig.
API_BASE_URL`'s origin — `https://pathway.nuruplace.org` in prod, so this
satisfies the "prefix https://pathway.nuruplace.org" ask exactly in
production while staying environment-correct in dev/staging too) before
handing it to a plain `ACTION_SEND` system share sheet; Delete opens the
"Delete '<title>'? / The recording will be gone forever. / Delete forever /
Cancel" confirm dialog → `DELETE /live/recordings/{id}` (new `MemberApi.
deleteLiveRecording`, `recording_id == stream_id`, also pre-staged) → the
list reloads in place via `AsyncContent`'s own `reload` callback. Tasteful
empty state ("Nothing here yet / Every stream you go live with is saved
here automatically").

**7. End-of-broadcast stewardship** (`LiveBroadcastScreen.kt`'s
`SummaryView`, now takes `streamId`): "Keep in Replays" is the gold,
default-emphasis action (simply calls `onDone` — dismissing the whole
screen without ever touching Delete keeps the recording, exactly like it
always implicitly did); "Delete recording" is a quiet, low-emphasis red text
link gated behind the SAME "gone forever" confirm dialog `LiveTabScreen.kt`
uses, calling `DELETE /live/recordings/{streamId}` then `onDone`.
Dismissing the confirm dialog itself (tap outside / Cancel) does not
delete — "dismissing keeps," per the ask.

**Files**: `feature/live/LiveInteractions.kt` (`clampDragOffset`,
`LiveFloatingChat`, `GuestStageBanner`/`GoldCornerPill`, `GentleEntrance`,
`LiveChromeCircleSize`/`Bg`, `RailButton` resized), `feature/live/
LivePlayerScreen.kt` (`startedByAvatarUrl` param, `BroadcasterIdentityChip`
avatar+HOST tag, `GuestStageBanner`/`LiveFloatingChat` wiring, `LiveChatOverlay`
removed), `feature/live/LiveBroadcastScreen.kt` (lifted chat-message polling,
`LiveFloatingChat` wiring replacing the sheet, HUD buttons resized,
`GentleEntrance` wrapping, `SummaryView` stewardship actions + confirm
dialog), `feature/live/LiveBroadcastInteractions.kt` (`LiveBroadcastChatSheet`
+ `ChatBubbleRow` deleted, unused imports trimmed), `feature/live/
GoLiveSetupSheet.kt` (`AudienceOption`, cell-name fetch), `feature/live/
LiveTabScreen.kt` (rewritten — `LiveHeroCard`/`BreathingGoLivePill`/
`LiveNowPill`, `MyBroadcastsList`/`MyBroadcastRow`), `feature/live/
LiveReplaysScreen.kt` (`replayDate` → `internal`), `feature/live/
LiveRoutes.kt` (`startedByAvatarUrl` threaded through `liveNowRoute`),
`feature/shell/MainShell.kt` (`startedByAvatarUrl` nav arg), `data/net/
LiveDtos.kt` + `data/net/MemberApi.kt` (`startedByAvatarUrl`,
`getMyLiveRecordings`, `deleteLiveRecording` — all three already staged by
the predecessor session before it was interrupted; verified compiling and
wired up end-to-end here), `app/src/test/java/.../LiveInteractionsTest.kt`
(+4: `clampDragOffset` cases).

**Predecessor session**: killed twice by transient API errors mid-rewrite
of `LiveInteractions.kt`. Its only surviving work was the wire layer
(`LiveDtos.kt`'s `startedByAvatarUrl`, `MemberApi.kt`'s
`getMyLiveRecordings`/`deleteLiveRecording`) — no Compose changes had
landed. This session kept that wire layer as-is and built the entire taste
pass (all 7 items) on top of it.

**Honest limits**: (a) the hero kicker inset fix is defensive, not a
verified root-cause fix — no device/emulator screenshot was taken this
session (text-only Kotlin/Gradle tooling); (b) "the broadcaster's own
cells" is really "cell," singular — see item 5; (c) Share is a plain
`ACTION_SEND` of the absolute url, not an actual on-device file download —
matches the letter of "system share with ABSOLUTE url" but a user wanting a
literal local file will need a share-target app that fetches it; (d) the
chat panel's default resting position is an approximation (~170dp bottom
margin, tuned by eye against the existing chrome layout in both files, not
measured against a running emulator) — good enough to never overlap the
rail/HUD in the common case, but not derived from real on-screen
measurement.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 49 tests, 0 failures/errors (45 baseline + 4 new). Not
pushed / no PR opened (isolated worktree `.worktrees/live-taste`, branch
`feat/live-taste`, built on top of origin/main #73/#74/#76).

## 2026-07-31 — Nuru Live L6b: real guest video (WHIP publish + WHEP subscribe), replacing the "joining soon" placeholders (branch feat/live-l6b-guest-video, this repo only, on top of #73/#74/#76/#78)

L6a shipped guest invite/accept as pure scaffolding (`status: accepted` on
`pulse.guests[]`, no media). This session wires the actual video: guests
WHIP-publish camera+mic to MediaMTX; the broadcaster WHEP-subscribes to
each accepted guest and renders a tile; guest audio is additionally mixed
into the broadcaster's own outgoing RTMP audio so the CONGREGATION hears
guests too, not just the host.

**1. WebRTC dependency + 16 KB verification.** `io.github.webrtc-sdk:
android:144.7559.09` (latest stable on Maven Central at session time,
LiveKit-maintained Google WebRTC prebuilt). Verified BEFORE wiring any code
against it: unzipped the resolved `.aar`'s `arm64-v8a`/`x86_64`
`libjingle_peerconnection_so.so` and ran `llvm-objdump -p` — every LOAD
segment aligns `2**14` (16 KB) on both 64-bit ABIs. Re-verified post-build
by extracting the SAME `.so` out of the actual assembled debug APK (not
just the raw `.aar`) — alignment survives AGP's merge/strip step unchanged
(`stripDebugDebugSymbols` logs "Unable to strip … libjingle_peerconnection_
so.so, packaging them as they are", i.e. untouched, not re-packed
unaligned). Declared in `gradle/libs.versions.toml` (`webrtcSdk`,
`webrtc-sdk`) + `app/build.gradle.kts`; no new repository needed
(`mavenCentral()` already declared in `settings.gradle.kts`).

**2. Shared WebRTC plumbing** (`feature/live/LiveWebRtc.kt`, new): one
process-wide `PeerConnectionFactory`/`EglBase` (WebRTC's own guidance —
expensive to recreate), suspend wrappers around `SdpObserver`/
`PeerConnection`'s callback API (`createOfferSuspend`/
`setLocalDescriptionSuspend`/`setRemoteDescriptionSuspend`/
`awaitIceGatheringComplete` — polls `iceGatheringState()` with a 4s cap
rather than wiring a second observer callback, since MediaMTX's WHIP/WHEP
is gather-then-send with no trickle channel in the HTTP exchange anyway),
and the WHIP/WHEP HTTP exchange itself (`postSdpOffer`/`deleteResource` —
POST `application/sdp` → 201 + SDP answer body + `Location` header,
resolved against the request URL if relative; DELETE the resolved
`Location` to tear down). One public STUN server
(`stun.l.google.com:19302`) for ICE server-reflexive candidates — no TURN
needed since MediaMTX itself sits on a publicly routable VPS, not behind
NAT. Auth is query-param (`?user=&pass=`), never a header — same idiom as
the RTMP publish URL (`LiveBroadcastEngine.kt`'s `buildPublishUrl`).

**3. `WhipPublisher`** (`feature/live/WhipPublisher.kt`, new) — guest side.
Front camera via `Camera2Enumerator` at 960×540@24 (~800 kbps target,
matches the L6b spec's modest thumbnail-rail profile regardless of device
capability) + mic with echo-cancellation/noise-suppression/AGC
`MediaConstraints`, two `SEND_ONLY` transceivers, offer → gather → POST →
`setRemoteDescription(answer)`. `attachLocalPreview`/`detachLocalPreview`
are decoupled from `start`/`stop` so the self-preview `SurfaceViewRenderer`
(created by Compose on its own schedule) can wire up whichever side of the
race it lands on. `stop()` DELETEs the WHIP resource, stops+disposes the
camera capturer, and tears down the `PeerConnection`.

**4. Guest UX** (`LivePlayerScreen.kt`): the moment `pulse.guests[]` reports
ME as `accepted`, a `LaunchedEffect` checks CAMERA/RECORD_AUDIO permission
(same `rememberLauncherForActivityResult(RequestMultiplePermissions())`
pattern as `GoLiveSetupSheet`'s Go Live flow — a hard denial surfaces an
honest error chip with tap-to-retry, never a silent hang) and drives a
`GuestStageState` (`Idle`/`Connecting`/`Live`/`Error`) through
`GET .../guests/me/ingest` → `WhipPublisher.start()`. `Live` renders a
draggable rounded self-preview PiP (`GuestSelfPreviewPiP`, `GuestStageUi.kt`
— same `clampDragOffset`/`pointerInput(detectDragGestures)` idiom as
`LiveFloatingChat`) with a mic-mute toggle baked into its corner, plus a
red `LeaveStagePill` (→ stop publish + `DELETE guests/{me}`, same visual
weight as the broadcaster HUD's End button). The HLS player's own audio is
muted while `Live` (`player.volume = 0f`) — WHIP is send-only, so without
this a guest hears their own voice echo back several seconds later over
HLS while actively speaking. Backgrounding while on stage
(`LifecycleEventEffect(ON_STOP)`) leaves cleanly rather than half-dying;
screen teardown (`DisposableEffect`) does the same via `Net.client.bgScope`
(survives past the composable's own cancelled `rememberCoroutineScope`,
same precedent `ApiClient.kt` documents for the engagement-flush-on-exit
call). `GuestStageBanner` (`LiveInteractions.kt`) now only handles the
`invited` prompt — the old static `OnStageSoonChip` ("On stage soon") is
gone; `accepted` is real publish state now. Guest publishing does NOT need
a foreground service (per the brief — the screen is on the whole time it's
relevant; `ON_STOP` above is exactly the "otherwise stop cleanly" case).

**5. `WhepSubscriber`** (`feature/live/WhepSubscriber.kt`, new) — host side,
one instance per accepted guest with a `whepUrl` (owner-only field on
`pulse.guests[]`). Two `RECV_ONLY` transceivers; `onTrack` routes a
`VideoTrack` to whatever renderer is attached (or queues until one is,
same decoupled `attachRenderer`/`detachRenderer` pattern as the publisher)
and an `AudioTrack` to `GuestAudioMixer.attach(guestId)`. Authenticated as
the STREAM's own id + streamKey (`?user=<streamId>&pass=<streamKey>`, both
already in scope as `LiveBroadcastScreen` params) — a WHEP subscription is
a broadcaster-authority action, not the guest's own credential.

**6. Host UX** (`LiveBroadcastScreen.kt`): a `mutableStateMapOf` of
`WhepSubscriber` keyed by guest id, reconciled each time `pulse.guests`'
accepted-with-`whepUrl` set changes (`LaunchedEffect` keyed on that id set —
adds a subscriber + starts it, stops+removes ones no longer present).
Renders as `HostGuestRail` (`GuestStageUi.kt`) — a draggable strip (left-
edge grip handle, mirrors `LiveFloatingChat`'s "only the handle drags, the
content scrolls" idiom) of up to `MAX_GUESTS` (6) rounded ~96×128dp tiles
with a name label, horizontally scrollable since 6 tiles don't fit most
phone widths. The host hears guests automatically — WebRTC plays received
audio through the default output regardless of `addSink()`, which is an
additive tap, not a replacement. `LiveBroadcastInteractions.kt`'s
`LiveHandsGuestsSheet` guest-row copy updated: "Joining soon — video in a
later update" → "Live on stage — see the guest rail above"; the raised-
hand row's "Joining soon" → "On stage" (both were stale now that L6b ships
real video).

**7. Reach goal — congregation hears guests too, SHIPPED** (not a fallback):
verified two independent, real mechanisms exist rather than assuming from
memory. (a) `org.webrtc.AudioTrack.addSink(AudioTrackSink)` — confirmed via
`javap` against the resolved 144.7559.09 `classes.jar` — is a genuine
playout/received-side PCM tap (`onData(ByteBuffer, bitsPerSample,
sampleRate, numberOfChannels, numberOfFrames, timestamp)`) per remote
track, additive to normal speaker playback, NOT the capture-side-only
`JavaAudioDeviceModule.SamplesReadyCallback` the task brief flagged as a
dead end. (b) RootEncoder 2.5.9's `AudioSource` (verified against the
pinned tag) is a bare abstract class — `start(getMicrophoneData)`/`stop()`/
`isRunning()`, nothing codec-specific — so a from-scratch subclass is free
to call `getMicrophoneData.inputPCMData(Frame)` with whatever PCM it wants,
whenever it wants (the sanctioned pattern RootEncoder's own
`MixAudioSource.kt` uses, though THAT particular implementation leans on
`AudioPlaybackCaptureConfiguration` — Q+, a second screen-share-style
MediaProjection consent prompt, and the OS hard-blocks capturing
`USAGE_VOICE_COMMUNICATION` content, which is WebRTC's default attribute —
not reused here for exactly those reasons). `GuestAudioMixer.kt` (new)
wires the two together: each guest's `AudioTrackSink` downmixes to mono +
linearly resamples to 44.1 kHz into a bounded (~2s) per-guest ring buffer;
`MixingMicrophoneSource` (a from-scratch RootEncoder `AudioSource`,
mirroring stock `MicrophoneSource.kt`'s exact `MicrophoneManager` wrapping)
sums all active guests' queued samples into the broadcaster's own mic
`Frame` in place, in `inputPCMData`, before forwarding upstream — no
MediaProjection, no second consent prompt, no OS capture restriction.
Swapped in as the VIDEO broadcast's audio source from the very start
(`LiveBroadcastEngine.kt`'s `buildBroadcaster`, the `GenericStream`
4-arg constructor with `Camera2Source` + `MixingMicrophoneSource`, not the
2-arg convenience constructor which bakes in stock `MicrophoneSource`) and
preserved across mute/unmute (`VideoBroadcaster.setMuted` swaps
`NoAudioSource`↔`MixingMicrophoneSource`, never back to plain
`MicrophoneSource` — that would silently drop guest mixing the moment
someone taps mute). `GuestAudioMixer.reset()` on broadcast start + full
teardown (`LiveBroadcastService.kt`) so a stale guest from a previous
session can never bleed into a new one's mix.

**Files**: `feature/live/LiveWebRtc.kt`, `WhipPublisher.kt`,
`WhepSubscriber.kt`, `GuestAudioMixer.kt`, `GuestStageUi.kt` (all new);
`feature/live/LivePlayerScreen.kt` (guest publish state machine +
self-preview PiP + Leave Stage), `LiveBroadcastScreen.kt` (WhepSubscriber
reconciliation + `HostGuestRail`), `LiveBroadcastEngine.kt`
(`MixingMicrophoneSource` wiring, mute-swap fix), `LiveBroadcastService.kt`
(`GuestAudioMixer.reset()` at start + cleanup), `LiveInteractions.kt`
(`GuestStageBanner` simplified to `invited`-only, `OnStageSoonChip`
removed), `LiveBroadcastInteractions.kt` (stale "joining soon" copy fixed
in both the hand row and guest row), `data/net/LiveDtos.kt`
(`LiveGuestRow.whepUrl`, new `LiveGuestIngest`), `data/net/MemberApi.kt`
(`getLiveGuestIngest`), `gradle/libs.versions.toml` + `app/build.gradle.kts`
(webrtc-sdk dependency).

**Honest limits**: (a) audio mixing is wired for VIDEO broadcasts only
(`GenericStream`/`VideoBroadcaster`) — audio-only (`kind: "audio"`)
broadcasts still get host-only guest audio; `GenericOnlyAudio`/
`OnlyAudioBase`'s narrower API wasn't traced for an equivalent
`changeAudioSource` hook in this session, and guests-on-an-audio-only-
broadcast is the rarer combination anyway. (b) the mixer's resampling is
linear interpolation, not a proper sample-rate-conversion filter — correct
math, adequate for spoken voice, not broadcast-grade audio engineering.
(c) none of this was exercised on a real device or against the live
MediaMTX server this session — every claim here is either compile-verified
or traced against the resolved library's actual decompiled API (`javap`)/
source (pinned RootEncoder tag via `gh api`), never assumed from memory,
but WHIP/WHEP's actual on-the-wire behavior (ICE connectivity through real
NATs, MediaMTX's exact SDP answer shape, whether the audio mix sounds
right) needs a live two-device test before this ships. (d) per the task
brief's own flag: coexistence between WebRTC's `JavaAudioDeviceModule`
(host's WHEP playout) and RootEncoder's `MicrophoneManager` (host's own
mic capture) running concurrently was reasoned through the API surface
(playout needs no `AudioRecord` since WHEP here is recvonly, so no capture-
side conflict was found on paper) but never verified against real
`AudioManager` mode/routing behavior on a device — the one item most worth
a live check before this is trusted in front of a congregation.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 49 tests, 0 failures/errors (baseline, unchanged — this
session is client-plumbing/UI, no new unit-testable pure functions).
16 KB alignment re-verified against the actual assembled debug APK (see
item 1). Not pushed / no PR opened (isolated worktree `.worktrees/l6b`,
branch `feat/live-l6b-guest-video`, built on top of origin/main
#73/#74/#76/#78).

## 2026-07-31 — Home-screen widgets: Pathway + Radio (Jetpack Glance, branch feat/android-widgets, this repo only)

**The gap**: iOS ships a `NuruWidgets` extension; Android had none — the
last home-screen-parity item on the board.

**What iOS actually ships, traced first (not assumed)**: `NuruWidgets/
NuruWidgets.swift` (main, and the `feat/rich-widgets`-named branch — same
content) is three **static** `.systemSmall` "door" widgets (Pathway/Chat/
Radio) — a navy-gradient card with an SF Symbol, a kicker, a title and a
caption, `widgetURL`-linking into `nuru://pathway|chat|radio`. No timeline
data, no App Group, no snapshot bridge. The header comment explains why:
*"the free personal signing team has no App Group entitlement, so no live
data crosses the boundary."* There is no `WidgetShared.swift` — that file
doesn't exist on either branch; it was never built. The rich progress-
ring/ON-AIR/listener-count design implied by the brief describes intent
that iOS itself never shipped, for a platform-specific signing reason that
doesn't apply to Android (Glance widgets run in-process; no App Group
needed to share data with the host app). Given that, and per this repo's
autonomous-operation mandate, Android goes further than iOS currently does
rather than cloning the static doors — same brand doors are trivial to add
later if iOS's constraint is ever lifted.

**Built**: two Glance widgets, small (2×2, 110×110dp) and medium (4×2, up
to 250×120dp, `SizeMode.Responsive`), reading a single on-disk snapshot —
**never the network**:
- **Pathway widget** — a gold progress ring (module completion, pre-
  rendered to a `Bitmap` via plain `android.graphics.Canvas`/`Paint` since
  Glance has no arbitrary Canvas — `WidgetRing.kt`), "LEVEL N", "M of T
  modules", the next-module title, and a 🔥 streak line; medium adds the
  level's title (serif) and a gold "Continue" pill.
- **Radio widget** — a red dot + "ON AIR"/"NURU RADIO" + the live program
  title + listener count while on air; the next program (or "Tap to tune
  in") off air. Medium adds a stylized artwork tile (a static gold note
  glyph — NOT the real show art; fetching that would mean networking
  *from the widget*, which is the one rule this feature never breaks) and
  the host name, plus — the "nice touch" from the brief — a tappable
  "● LIVE now" line when the church is broadcasting right now.
- Both tap straight into the app via the SAME `nuru.dest` extra + route
  vocabulary notifications/shortcuts already use (`MainActivity.kt`'s
  `PendingDest`, `MainShell.kt`'s `NavHost` routes) — zero new nav wiring.

**Snapshot bridge** (`widget/WidgetSnapshotStore.kt`, plain
SharedPreferences, mirrors `data/AppPrefs.kt`'s existing pattern): the app
writes a denormalized `WidgetSnapshot` (level/title/completed/total/next-
module/streak, radio on-air/title/host/listeners/next-program, church-live)
at the same points the app already loads that state, then triggers an
immediate `updateAll` —
- `PathwayHubScreen.kt`: once the active level + its resolved "resume"
  module are known (`LaunchedEffect(active?.levelNumber,
  active?.completedModules, resume?.moduleId, streak)`).
- `HomeScreen.kt`: Home's own `radioNowPlaying`/`liveNow` poll, right after
  `churchLive` is computed — the earliest point in a session a snapshot can
  land, even if the member never opens Radio/Pathway.
- `LiveRadioScreen.kt`: its own richer `now`/`nextScheduled` load overwrites
  Home's lighter radio snapshot with listeners/host/next-program once the
  player is opened.

**Refresh cadence**: immediate on every snapshot write, plus a
`WorkManager` periodic job (`WidgetRefresher.schedulePeriodic`, enqueued
`KEEP` from `NuruApp.onCreate`) at WorkManager's own 15-minute floor —
which happens to match iOS's intended 15-min widget-timeline cadence. A
manifest `updatePeriodMillis="1800000"` (Android's 30-min floor) is the
belt-and-suspenders fallback. The periodic worker only calls `updateAll`
against whatever is already on disk — it never touches the network either.

**Brand**: fixed navy `#16273F`/gold `#C9A227` (verified already in use for
the radio panel + chat-bubble gradients, `LiveRadioScreen.kt`/
`ChatShared.kt`), same for light/dark system theme — like every other Nuru
chrome surface, the widget doesn't flip with the device. Display text uses
`FontFamily.Serif` (system serif) for a Fraunces-like feel — **honest
limit**: Glance's font API is system-family-only (`SansSerif`/`Serif`/
`Monospace`/`Cursive`); the bundled Fraunces `.ttf`s in `res/font` cannot
be attached to Glance `TextStyle` the way Compose's own `nuruSerif()` does,
so the widget is visually close but not a pixel-exact Fraunces match.

**Dependencies** (verified against `dl.google.com/android/maven2` — 1.2.0
and 1.3.0 are alpha/beta/rc-only): `androidx.glance:glance-appwidget:1.1.1`
+ `androidx.glance:glance-material3:1.1.1` (only used for the
`ColorProviders(light, dark)` brand-theme builder consumed by the core
`androidx.glance.GlanceTheme` — confirmed via `javap` against the resolved
AARs that `GlanceTheme` itself lives in the glance CORE artifact, not
glance-material3, which ships nothing but that one builder function).
**Zero new native libraries** — confirmed no `.so` in any of the three
resolved Glance AARs, so the 16 KB page-size story (`libs.versions.toml`'s
webrtc-sdk pin) is untouched.

**Files**: `widget/WidgetSnapshotStore.kt`, `WidgetRefresher.kt`,
`WidgetRing.kt`, `WidgetBrand.kt`, `WidgetIntents.kt`,
`PathwayGlanceWidget.kt`, `RadioGlanceWidget.kt` (all new);
`res/xml/pathway_widget_info.xml`, `res/xml/radio_widget_info.xml`,
`res/layout/nuru_widget_loading.xml` (new); `AndroidManifest.xml` (two
`<receiver>`s), `strings.xml` (widget labels/descriptions),
`NuruApp.kt` (schedules the periodic worker), `HomeScreen.kt`/
`PathwayHubScreen.kt`/`LiveRadioScreen.kt` (snapshot-write hooks, additive
only), `gradle/libs.versions.toml` + `app/build.gradle.kts` (Glance deps).

**Honest limits vs iOS**: (a) iOS's OWN widgets are static doors with no
live data at all (App Group entitlement gap, not fixed here — out of this
repo's scope); Android's are richer, so today Android widgets show real
progress/on-air state that iOS's don't. (b) no live artwork/church-stream
thumbnail in the widget — deliberately, per the "never network from the
widget" rule. (c) Fraunces isn't attached to Glance text (system serif
fallback, documented above). (d) not visually verified on a real launcher/
home-screen grid this session (no screenshot pass) — compile + resource-
link + unit-test verified only; worth a quick on-device pin-and-look before
this ships to testers.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 57 tests (49 baseline + 8 new — `WidgetSnapshotTest`:
`modulePct` clamping/rounding + `radioHeadline` on-air/off-air branches),
0 failures/errors. Resource linking (aapt2) implicitly verified the two
`appwidget-provider` XMLs, the loading layout, and the manifest receivers
compile clean. Confirmed zero `.so` files across all three resolved Glance
AARs (`unzip -l` on `~/.gradle/caches`). Not pushed / no PR opened
(isolated worktree `.worktrees/widgets`, branch `feat/android-widgets`,
built on top of origin/main).
## 2026-07-31 — Nuru Live L6c: guest video composited into the outgoing RTMP stream — the CONGREGATION now sees the stage (branch feat/live-l6c-compositing, this repo only, on top of #73/#74/#76/#78/L6b)

L6b got guest audio onto the outgoing mix and gave the HOST a local rail of
guest video tiles (`GuestStageUi.kt`'s `HostGuestRail`) — but that rail was
pure Compose UI on the broadcaster's own screen; nothing about it reached
the RTMP frame the congregation actually watches, which still showed only
the host's camera. This session closes that gap: the host's RootEncoder GL
pipeline now composites live guest video directly into the frame it
publishes, speaker-large + thumbnail rail, with name plates.

**The mechanism** (all read from the pinned RootEncoder 2.5.9 source via
`gh api repos/pedroSG94/RootEncoder/contents/<path>?ref=2.5.9`, and the
resolved `io.github.webrtc-sdk:android:144.7559.09` classes via `javap` —
never assumed from memory, per the task brief). RootEncoder's GL pipeline
(`library/.../view/GlStreamInterface.kt` + `encoder/.../render/
MainRender.kt`) renders the active camera/screen source into an off-screen
FBO at the locked encoder canvas size, then walks a chain of
`BaseFilterRender` objects — each draws a full-screen quad compositing the
PREVIOUS stage's output as background with its own "object" texture
overlaid at a percent-of-canvas position/scale
(`BaseObjectFilterRender.setPosition/setScale`, `Sprite.java`'s own doc:
"0,0 top-left, 100,100 bottom-right"). This is RootEncoder's own sanctioned
sticker/watermark mechanism, verified from source, not a hack.
`SurfaceFilterRender` (`encoder/.../filters/object/SurfaceFilterRender.
java`) is the one object-filter subclass backed by an arbitrary
`android.view.Surface` rather than a static bitmap — exactly what's needed
to feed it LIVE guest frames. On the WebRTC side, `org.webrtc.EglRenderer`
(verified via `javap` against the SAME resolved artifact this app already
depends on) `implements VideoSink`, so `videoTrack.addSink(eglRenderer)`
just works — additively, the same multi-sink pattern
`GuestAudioMixer.kt`/`WhepSubscriber.kt` already established for audio —
and exposes `createEglSurface(Surface)` to bind the `SurfaceFilterRender`'s
Surface as its render target, plus `init(EglBase.Context, int[],
RendererCommon.GlDrawer)` to share GL context with `LiveWebRtc`'s existing
`EglBase` (so the VideoFrame's hardware texture, decoded in that same
context, is valid to sample). RootEncoder's own GL context and WebRTC's
`EglRenderer` never need to share a context WITH EACH OTHER — Android's
`Surface`/`SurfaceTexture` is a `BufferQueue` at the platform level,
deliberately safe across independent GL contexts/threads — so the two
libraries hand frames to each other purely through that `Surface`.

**Fixed slots, not per-guest filters.** A naive per-guest
add/removeFilter design breaks compositing order: `MainRender` chains
filters strictly by insertion order (`reOrderFilters()`'s `previousTexId`
links — read from source), so whichever filter was added first always
renders underneath. If the active speaker swapped to a guest who joined
EARLIER than someone now relegated to the rail, that rail tile — inserted
before the now-fullscreen speaker overlay — would render underneath it and
vanish. Instead `GuestStageCompositor.kt` pre-allocates `MAX_GUESTS` (6)
FIXED slots once per broadcast at `bind()` (slot 0 = speaker role, always
drawn first/underneath; slots 1-5 = rail, always drawn after/on top) and
only ever REASSIGNS which guest's `VideoTrack` feeds a slot's
already-created `EglRenderer` (`removeSink` the old track, `addSink` the
new one) as the active speaker changes — no GL churn, no flicker, no
ordering bug possible. Percent-based geometry means this never touches
encoder resolution — the "never change encoded dimensions mid-publish"
guardrail is satisfied by construction, not by discipline.

**Speaker selection**, per the task brief's own fallback chain, implemented
verbatim: loudest live guest (by audio level) → else most-recently-joined
guest → else no overlay at all (host fills the frame, unchanged behavior).
"Loudest" reuses `GuestAudioMixer.kt`'s ALREADY-decoded, already-resampled
mono PCM (`GuestAudioMixer.push`) — added a `ConcurrentHashMap<String,
Float>` of IIR-smoothed mean-abs-amplitude per guest, updated for free in
the same hot path that already runs for the audio mix, exposed via
`currentLevels()`. No second audio tap.

**Layout.** Speaker slot: ~100%x100% (near-fullscreen — task brief's
"active speaker large"). Rail: vertical stack of ~16%x21% tiles down the
top-right edge, each with a `TextObjectFilterRender` name plate
(RootEncoder's own bundled text-bitmap object filter — `setText` renders a
`Bitmap` via a plain `Canvas`, texture upload deferred to the GL thread
inside `drawFilter()`, so it's safe to call from the compositor's
Main-dispatcher reflow loop) fed guestId→fullName from
`LiveBroadcastScreen.kt`'s pulse poll (the compositor itself only ever
sees WebRTC tracks + ids, never the REST guest row). Screen/Document mode
(task brief item 4): `setScreenSource(true)` disables the speaker-overlay
entirely — the shared screen/document always stays the "large" content,
every live guest (however many) drops into the rail instead, never
promoted over the shared content. Reflow runs on a 1.2s timer (catches a
loudness change with no join/leave event) plus an immediate call +
300ms quick-retry on every attach/detach (catches the common case fast
without waiting on the timer; the retry exists because a `SurfaceFilterRender`'s
`Surface` is only valid once its `addFilter()` has actually been processed
on RootEncoder's own GL thread — async relative to the call site).

**Files**: `feature/live/GuestStageCompositor.kt` (new — the compositor
itself); `GuestAudioMixer.kt` (`currentLevels()` + level tracking, additive
to the existing mix path); `LiveBroadcastEngine.kt`
(`Broadcaster.glStreamInterface()`, `VideoBroadcaster` override returning
`stream.getGlInterface()`, null default for `AudioBroadcaster` since
`GenericOnlyAudio` has no GL pipeline); `LiveBroadcastService.kt`
(`GuestStageCompositor.bind()` at broadcast start alongside
`GuestAudioMixer.reset()`'s existing "no stale guest" discipline,
`.reset()` at `cleanupEngine()`, `.setScreenSource()` in both source-switch
paths); `WhepSubscriber.kt` (`attachVideo`/`detachVideo` calls, additive to
the existing renderer-tile and audio-mixer taps on the same `onTrack`
callback); `LiveBroadcastScreen.kt` (pushes guestId→fullName into the
compositor whenever the pulse poll refreshes).

**Honest limits** (per the task brief's own "never fake" instruction):
(a) **orientation of the composite was reasoned from source, not visually
verified on a device** — traced that `MainRender.drawOffScreen()` (where
filters run) operates on the SAME already-upright, already-oriented-correct
canvas that `screenRender.drawEncoder()` later rotates as one unit for the
final portrait/landscape output, so percent coordinates SHOULD map
correctly onto what the viewer sees in both orientations — but this
session had no way to actually watch a portrait broadcast with a live
guest to confirm the rail lands top-right rather than, say, rotated 90°.
(b) **no aspect-ratio correction on guest tiles** — `EglRenderer` letterboxes
a guest's video into whatever buffer size the target `Surface` was given
(the full locked canvas, e.g. 1080x1920), and `SurfaceFilterRender`'s object
shader then stretches THAT into the assigned rail rectangle with no
crop-to-fill logic (`BaseObjectFilterRender` has no aspect-aware scaling
mode, verified from source) — a guest whose own video aspect doesn't match
the tile shape will look letterboxed-then-stretched, a real but purely
cosmetic defect; fixing it needs a custom `RendererCommon.GlDrawer`
implementing crop, out of scope this session. (c) **name-plate text is not
aspect-corrected either** — `TextStreamObject` bakes text to a
tightly-cropped bitmap sized by string length, then that bitmap gets
stretched into a fixed-percent plate rectangle, so a very short or very
long name may look subtly thin/wide. (d) **not exercised against a live
two-guest broadcast on real devices or the deployed MediaMTX server** —
every claim here is compile-verified or traced against the resolved
library's actual source/decompiled API, never assumed from memory, but
whether the composite actually looks right, holds 30fps under real camera
+ 2 guest decode load, and reflows smoothly as guests join/leave needs a
live test before this is trusted in front of a congregation — the single
most important follow-up. (e) the host's OWN camera can never be shrunk
into a rail tile alongside guests (e.g. if the host wanted to appear small
while a guest is the large speaker) — RootEncoder's base camera/screen
layer is captured directly into the FBO `MainRender.drawOffScreen()`
composites everything else on top of; there is no traced mechanism in the
2.5.9 GL pipeline to re-texture that base layer as a separately
positionable object without a second camera capture session. This is a
deliberate, documented scope boundary, not an oversight: the task brief's
"speaker large + rail" is satisfied for GUEST speakers (which is the
common case — a host on video hosting guests), and the host himself simply
IS the base/background whenever no guest is currently speaking.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 49 tests, 0 failures/errors (baseline unchanged — this
session's new logic, `GuestStageCompositor.kt`'s slot/layout/speaker-
selection math, isn't yet covered by a new unit test — it depends on
RootEncoder/WebRTC concrete classes that aren't mockable without a real GL
context or device, the same category of limitation `GuestAudioMixer.kt`'s
mixing math worked around by being pure-function-testable in isolation;
this session's speaker-selection function (`pickSpeaker`) COULD be
extracted and tested the same way as a fast-follow). Not pushed / no PR
opened (isolated worktree `.worktrees/l6c`, branch
`feat/live-l6c-compositing`, built on top of origin/main
#73/#74/#76/#78/L6b).

## 2026-07-31 — Nuru Live host-stability incident: proven root cause (R8 stripped WebRTC's JNI bridge), plus native-crash-race hardening, guest-tile errors, and a self-hosted-video-to-browser bug (branch fix/live-host-stability, this repo only, on top of L6c)

**Incident**: real two-device test (host Samsung S24 Ultra on vc54, guest
Techno Spark 10/Android 13) found the host's process DYING the instant a
guest joined the stage (viewers then saw "stream has ended"; server-side:
the stream row stayed `status='live'` with zero RTMP publishers connected
— the publish socket really did die, consistent with the whole process
going down). Two more real-device findings rode along: the guest's video
never appeared as a tile on the host, and the guest's phone popped Chrome's
"Download file again?" prompt for a `/media/<uuid>.mov` self-hosted asset
instead of playing it.

**Root cause of the host process death — PROVEN, not inferred.** Mid-session
the owner pulled the actual tombstone off the S24 via adb:
```
F libc: Fatal signal 5 (SIGTRAP), code 1 (TRAP_BRKPT) ... (com.nuruplace)
#00-#03  libjingle_peerconnection_so.so
#04      libjingle_peerconnection_so.so (JNI_OnLoad+64)
...
#15      org.webrtc.PeerConnectionFactory.b          <- obfuscated to "b"
#20-#24  Q9.N1.a / Q9.Y1.a / Q9.g0.invokeSuspend       <- our own Kotlin, obfuscated
```
WebRTC's native library aborts (its own `RTC_CHECK`) inside `JNI_OnLoad`
the FIRST time `PeerConnectionFactory.initialize()`/
`createPeerConnectionFactory()` ever runs on that process — i.e. the
instant the host subscribes to a guest (`WhepSubscriber`) or a guest
publishes (`WhipPublisher`). `app/build.gradle.kts`'s release buildType runs
`isMinifyEnabled=true` + `isShrinkResources=true` (the owner sideloads
release builds), and `app/proguard-rules.pro` had **zero** rules for
`org.webrtc.**`. Verified by unzipping the resolved
`io.github.webrtc-sdk:android:144.7559.09` `.aar`: it ships no
`proguard.txt`/`consumer-rules.pro` at all, so R8 got no automatic
protection either. libjingle's `JNI_OnLoad` resolves Java members it needs
(`@CalledByNative`/native-method counterparts) BY NAME via reflection/
`RegisterNatives`; with no keep rule R8 was free to rename/strip exactly
those members (the trace's `PeerConnectionFactory.b` is the smoking gun),
so native registration failed and the library aborted the whole process.
This is why every debug build, `compileDebugKotlin`, and `testDebugUnitTest`
run passed clean all session — R8 only runs on `release`, which is what the
owner's device actually runs; debug builds structurally cannot reproduce
this class of bug.

**Fix** (`app/proguard-rules.pro`):
```
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keepattributes *Annotation*
-dontwarn org.webrtc.**
```
Plus a narrow `-keep class com.pedro.** { *; }` for RootEncoder (audited,
not proven-broken — see below) and a single-class keep for
`androidx.datastore.core.NativeSharedCounter` (same audit, also latent —
see below).

**Proof, not plausibility** (owner's doctrine): built the actual RELEASE
APK in the worktree (`./gradlew :app:assembleRelease`, unsigned — no
`signingConfigs` present in this isolated worktree, which doesn't affect R8
behavior) and inspected `app/build/outputs/mapping/release/mapping.txt`:
`org.webrtc.PeerConnectionFactory -> org.webrtc.PeerConnectionFactory:`
(identity — class not renamed), and every member that matters for the
crash path preserved by name — `createPeerConnectionFactory`,
`setAudioDeviceModule`, `createPeerConnection`, `initialize`, `dispose`,
etc. all read `-> <same name>` throughout the class and its `$Builder`
nested class. The only things still mapped to bare `a`/`b`/`c` in that
class are three `-$$Nest$...` bridges, which are R8/D8-*synthesized*
nestmate-access trampolines that never existed in the original library
(their own names literally embed the real target's name, e.g.
`-$$Nest$smnativeCreatePeerConnectionFactory -> c`) — not stripped
originals, and not what `JNI_OnLoad`'s native registration looks up.
Reconfirmed after adding the DataStore keep too (same identity lines,
`androidx.datastore.core.NativeSharedCounter -> androidx.datastore.core.NativeSharedCounter:`).
Also confirmed via `unzip -l` on the release APK that `libjingle_peerconnection_so.so`
still ships in all 4 ABIs (arm64-v8a/armeabi-v7a/x86/x86_64) — R8's keep
rules didn't accidentally cause it to be stripped from packaging either.

**Sibling audit** (doctrine: "one failure = a class, audit for sibling
occurrences") — every dependency in this app shipping a native `.so` was
checked for a bundled consumer-proguard file via `unzip -l` on its resolved
`.aar`: ML Kit `barcode-scanning:17.3.0`, CameraX `camera-core`/
`camera-camera2:1.4.2`, and `androidx.graphics:graphics-path:1.0.1` **all**
ship their own `proguard.txt` (auto-applied — never the gap).
`androidx.datastore:datastore-core-android:1.1.4` (native
`libdatastore_shared_counter.so`, backing
`androidx.datastore.core.NativeSharedCounter`/`MultiProcessCoordinator`)
does **not** ship one — the identical gap class as webrtc-sdk, but LATENT
here: grepped the whole app for "MultiProcess"/"multiprocess" and found
zero usage, so this app never constructs a multi-process DataStore and this
native path is dead code today. Kept anyway as free, low-risk insurance.
RootEncoder (`com.github.pedroSG94.RootEncoder:library`/`common`/`rtmp:2.5.9`)
was also audited: none of its AARs contain a `jni/` directory or any `.so`
file at all (it's pure Kotlin/Java over Android's own `MediaCodec`, no
custom native library), so it carries none of this risk class; its `-keep`
above is narrow insurance against reflection-sensitive constructor overload
resolution being altered by shrinking, not a proven bug.

**Also fixed, in the same session** (real-device bugs #2/#3 from the
original report):

- **Guest tile never appearing (#2)** — traced as almost certainly a
  downstream consequence of #1 (the process dying kills everything, guest
  video included), but hardened independently regardless: `sub.start()`
  failures used to be silently swallowed by a bare `runCatching` in
  `LiveBroadcastScreen.kt`, leaving an indefinite black tile with no way to
  tell "still connecting" from "actually broken". `HostGuestTileState` now
  carries an `errorMessage`, surfaced as a `⚠︎ Couldn't connect` overlay on
  the tile (`GuestStageUi.kt`'s `GuestTile`) instead of a silent hang.

- **Self-hosted video opened in Chrome instead of playing in-app (#3)** —
  traced every `ACTION_VIEW`/`openExternal` call site in the app (there are
  exactly three). The live/guest feature itself never calls `openExternal`
  at all. `HomeScreen.kt`'s featured-video card already gates correctly on
  the server's `isExternal` flag. The two real, UNGATED call sites were the
  Reading Plan video cards — `RMediaCard` (`PlanReaderKit.kt`) and
  `VideoCard` (`PlanSegmentScreen.kt`) — which handed ANY `videoUrl`
  straight to `openExternal()` with no host check at all, which for a
  self-hosted `/media/<uuid>.mov` upload is exactly the observed "Download
  file again?" Chrome prompt instead of playing the clip. Added
  `isExternalVideoHost(url)` (`VideoPlayer.kt`, pure `java.net.URI`-based,
  not `android.net.Uri`, specifically so it's meaningfully unit-testable
  under this project's plain-JUnit posture rather than silently returning
  false-for-everything under `unitTests.isReturnDefaultValues=true`) gating
  on YouTube/Vimeo hosts only; both cards now play a self-hosted URL in-app
  via the existing `InlineVideo` (mirroring `HomeScreen`'s own pattern)
  instead of ever reaching `openExternal`.

**Resilience hardening kept from the (demoted, but still real) earlier
hypotheses this session worked through before the tombstone landed** — not
the fix for the process-death bug, but genuine, independently-worthwhile
fixes for real races/gaps found while tracing it:

- **`WhepSubscriber`/`WhipPublisher` start()/stop() race** — both classes'
  `LaunchedEffect`-driven callers (`LiveBroadcastScreen.kt`'s pulse-set
  reconciliation; `LivePlayerScreen.kt`'s `myGuestState` watcher) can call
  `start()` and `stop()` concurrently on the SAME instance if a guest's
  pulse row flickers (e.g. the backend mints `whepUrl` a beat after
  flipping status to `accepted`). `stop()` starts with a suspending network
  call (`LiveWebRtc.deleteResource`) before touching WebRTC objects, so a
  concurrent `start()` racing `dispose()` on the same native `PeerConnection`
  was a real, structural use-after-free risk independent of the R8 bug.
  Both classes now serialize `start()`/`stop()` behind a `Mutex` with a fast
  `stopRequested` pre-check.
- **`GuestStageCompositor.attachVideo`/`detachVideo` off the main thread** —
  `WhepSubscriber.onTrack` fires on WebRTC's own signaling thread, never
  Android's main thread, but RootEncoder's `SurfaceFilterRender` documents,
  verbatim in its own source: "This surface must be rendered using an api
  called on main thread to avoid possible errors." Both methods are now
  `suspend` + `withContext(Dispatchers.Main.immediate)`, and `stop()` AWAITS
  `detachVideo` (previously fire-and-forget) so `dispose()` can never run
  ahead of the detach's own `addSink`/`removeSink` calls.
- **`GuestWebRtcSupervisor`** (new file) — a process-wide registry bridging
  screen-scoped `WhepSubscriber`s to the service-scoped RTMP publish, so
  `LiveBroadcastService.handleDrop()` can tear down every live guest's
  WebRTC FIRST before a reconnect/retry attempt, and a new periodic
  watchdog (`shouldWatchdogTriggerDrop`, extracted pure for testability)
  backstops the case where RootEncoder's publish pipeline dies without ever
  triggering `ConnectChecker`'s network-level callbacks. Documented in both
  places as a last-resort breaker for the (fixed) mic/race hypotheses, not
  a defense against a native SIGTRAP — a real process crash cannot be
  caught or recovered from in-process; only the R8 keep-rule fix prevents
  that class of failure.
- **`LiveWebRtc`'s `AudioDeviceModule`** — the shared `PeerConnectionFactory`
  had no explicit ADM, so WebRTC silently defaulted one in
  (`JavaAudioDeviceModule.builder(context).createAudioDeviceModule()`), a
  long-documented upstream WebRTC-Android gotcha (bug 8214) where the ADM
  opens `AudioRecord` even for a purely recvonly connection. The host only
  ever runs `WhepSubscriber` (recvonly, RootEncoder owns the real broadcast
  mic entirely outside WebRTC) so it has zero legitimate need for WebRTC
  audio recording, ever; kept OFF by default via the SDK's
  `setAudioRecordEnabled(false)`, flipped on only for the guest device's own
  `WhipPublisher` publish window. Demoted from "the" root cause once the
  tombstone proved a hard process death, but kept as a real, independently
  correct fix — a mic conflict is still a genuine defect even though it
  isn't what killed this process.

**Honest limits**: the R8/mapping.txt proof is the load-bearing one — direct,
falsifiable, and specific to the actual crash frame. The resilience/race
fixes above are real and defensible from source (RootEncoder's own
documented main-thread contract; the suspending-`deleteResource()`-before-
`dispose()` ordering is visible directly in the diff) but, per the owner's
own note, could not be re-verified against a live two-device repro this
session (no adb access beyond the one tombstone pull) — the watchdog in
particular is untested against a real silent-death scenario since the only
proven death mode (SIGTRAP) is now closed at the source. `#3`'s fix is
traced and provable from a full `openExternal` call-site audit, but the
EXACT sequence of screens the guest's device was on when the `.mov` prompt
fired was not independently reconstructed — the fix closes every code path
capable of producing that exact symptom app-wide, not just a single
confirmed trigger.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 69 tests, 0 failures/errors (57 baseline + 12 new:
`VideoPlayerTest` for `isExternalVideoHost`, `shouldWatchdogTriggerDrop`
cases in `LiveBroadcastInteractionsTest`). Additionally ran
`./gradlew :app:assembleRelease` (release/R8 path, not part of the
project's standard DoD but required here to falsifiably prove the actual
fix) → BUILD SUCCESSFUL, mapping.txt inspected as documented above. Not
pushed / no PR opened (isolated worktree `.worktrees/hostfix`, branch
`fix/live-host-stability`, built on top of origin/main #73/#74/#76/#78/L6b/L6c).

## 2026-07-31 — Nuru Live vc55: broadcaster preview never appears (race, not the encoder) + host offered "Join live" on their own stream (branch fix/live-preview-and-selfdiscovery, this repo only, on top of #73/#74/#76/#78/L6b/L6c/host-stability)

Two REAL bugs confirmed on the owner's S24 running vc55, with device evidence
gathered via adb during the exact session ("for the last 20 seconds and
counting the camera has not launched", plus a Home screenshot showing the
broadcaster's own stream offered back to them).

**Bug 1 — broadcast preview stayed black/frozen while the publish itself was
completely healthy.** Device evidence ruled out both obvious hypotheses
first: `RtmpSender: wrote Video packet, size 21325 / 28034 / 47341...`
continuously (real imagery, not black frames — too large), the process
stayed alive with an empty crash buffer (not a crash), and SurfaceFlinger
showed a full 1080x2340 `SurfaceView[com.nuruplace/…MainActivity]` created
and later destroyed on navigation — so the encoder/publish path (RootEncoder
`GenericStream`, `LiveBroadcastService`) was never the problem.

Root cause: a genuine, provable race between two independently-async things
introduced by vc55's Broadcast Studio rework (#76), which moved the
RootEncoder engine into the foreground `LiveBroadcastService`, reached only
through `BroadcastController`. `LiveBroadcastScreen`'s `SurfaceView.holder.
addCallback(...)` fires `surfaceCreated()` → `BroadcastController.
attachPreview(view)` the instant the OS hands Compose a valid `Surface` —
independent of, and often FASTER than, `BroadcastController.start()`'s own
`bindService()` (async) and `LiveBroadcastService.startBroadcast()`'s
`buildBroadcaster()` (opens the camera, calls `prepareVideo`/`prepareAudio`
— not instant). Before this fix, `attachPreview()` was `service?.
broadcaster?.startPreview(view)` — a null-safe chain that silently no-oped
whenever either side wasn't ready. Verified against RootEncoder 2.5.9's own
pinned source (`gh api repos/pedroSG94/RootEncoder/contents/library/src/
main/java/com/pedro/library/base/StreamBase.kt?ref=2.5.9`): the SurfaceView
overload of `startPreview()` takes a one-shot snapshot of `surfaceView.
holder.surface` with no listener of its own — `SurfaceHolder.Callback.
surfaceCreated()` fires EXACTLY ONCE per surface lifecycle, so nothing ever
retried the attach once the engine became ready a moment later. Also
confirmed `startPreview(surface, width, height)` throws
`IllegalArgumentException` if the surface isn't valid yet — the absence of
any crash in the device evidence is itself consistent with the call never
reaching RootEncoder at all (the null-safe chain ate it silently), not with
a caught exception.

`GuestStageCompositor` (L6c) was independently audited as a possible
interference source per the task brief — ruled out: it only adds
zero-alpha `SurfaceFilterRender`/`TextObjectFilterRender` object-filters to
the SAME `GlStreamInterface` the preview also renders from, and only
becomes visible once a guest is actually assigned to a slot; it has no
surface-attach/detach interaction with `startPreview`/`stopPreview` at all.

**Fix** (`BroadcastController.kt`): remember whatever `SurfaceView` Compose
most recently handed `attachPreview()` (`pendingPreviewView`) and retry the
attach at every point `service.broadcaster` can transition from null to
non-null — the end of the `pendingStart` branch in `onServiceConnected`, and
the "already bound" branch of `start()`. `Broadcaster.startPreview()`
already no-ops via RootEncoder's own `isOnPreview` flag, so a redundant
reattach is always harmless. Added always-on `Log.d`/`Log.w` breadcrumbs
around every preview lifecycle call (`attachPreview`, `detachPreview`,
`reattachPendingPreview`, each success/failure) tagged `BroadcastController`
so the next device session is diagnosable without adding new instrumentation
first. This closes the race for first go-live (the reported case — cold
`bindService()`), and structurally covers minimize/restore and return-from-
background too (same `service`/`broadcaster` null→non-null transition
points; the SurfaceView's own OS-driven `surfaceDestroyed`/`surfaceCreated`
pair around a backgrounding event already worked correctly once broadcaster
was ready — this fix's gap was specifically the FIRST attach).

**Bug 2 — the host saw a "Join live" bar for their OWN broadcast.** The
owner's Home screenshot (`● LIVE test 2 [Join live]` while THEY were live)
matches `LiveMiniPopup` (`LiveDiscoveryUi.kt`) exactly — Home's floating
muted-preview mini-window, driven end-to-end by `LiveDiscoveryCenter`.
`LiveDiscoveryCenter.ingest()` folded every `GET /live/now` row into shared
state and picked the first not-yet-`seen` stream_id for the popup with NO
filter for the local device's own in-progress broadcast — the exact bug
already root-caused and fixed on iOS's discovery centre, un-ported to
Android.

**Fix**: `BroadcastController.activeSelfStreamId()` (delegating to a new
pure `selfStreamIdFrom(BroadcastState)` in `LiveBroadcastEngine.kt`, unit-
testable without the live StateFlow/Service plumbing — mirrors this file's
existing `shouldWatchdogTriggerDrop` posture) exposes the stream_id THIS
device is currently broadcasting, excluding `SUMMARY` (broadcast genuinely
over). `LiveDiscoveryCenter.ingest()` now filters every incoming row through
a new pure `filterOutSelfStream(rows, selfStreamId)` BEFORE it reaches
`_streams`/the popup-selection logic — the one choke point every discovery
surface reads through (`AppLiveBar`, the mini-window, and `newestWatchable`
— which the `live-now` notification-routed destination in `MainShell.kt`
also reads, so a `live_stream_started` push for the broadcaster's OWN stream
can no longer route them into their own viewer either). Added defensive
guards at three render sites per the task brief, on top of the ingest-level
fix: `HomeScreen.kt`'s `churchLive` (a SEPARATE, directly-fetched `liveNow`
local var, not read from `LiveDiscoveryCenter.streams` — would NOT have been
covered by the ingest fix alone), the mini-popup's own render check, and
`MainShell.kt`'s `showAppLiveBar` condition.

**What else was audited for the same class of bug**: grepped every reader of
`LiveDiscoveryCenter.streams`/`newestWatchable`/`popupStreamId` (`HomeScreen.
kt`, `MainShell.kt`, `LiveTabScreen.kt`) — all either read through the now-
filtered `streams`/`newestWatchable` or got an explicit defensive guard
added directly. No other local/unfiltered `/live/now` fetch site found
besides `HomeScreen.kt`'s `liveNow`.

**New tests** (both pure functions, JVM-only, no Robolectric/Compose
harness — matching this file's existing posture): `LiveDiscoveryCenterTest`
(`filterOutSelfStream` — 5 cases: no self id, self id excluded, self-only
list empties out, unmatched id is a no-op, empty input stays empty) and 3
new cases appended to `LiveBroadcastInteractionsTest` for `selfStreamIdFrom`
(no session → null; CONNECTING/LIVE/RECONNECTING/FAILED/ENDING → surfaced;
SUMMARY → cleared).

**Honest limits**: Bug 1's fix is proven by source-level tracing (the
one-shot `SurfaceHolder.Callback` contract verified against RootEncoder
2.5.9's pinned source) and closes every code path that can produce the
reported symptom, but was NOT re-verified against a live two-device repro
this session (no adb access this run beyond what the owner already
gathered) — the added breadcrumb logging is specifically so the next device
session can confirm the fix (or point at a different gap) directly from
logcat. Bug 2's fix is straightforward and directly matches the reported
screenshot/UI text ("Join live" only exists on `LiveMiniPopup`), but the
owner's literal phrase "app-wide LIVE discovery bar" was interpreted as this
mini-popup rather than the (route-guarded-off-Home) `AppLiveBar` strip,
since only the mini-popup renders on Home at all — the `AppLiveBar` guard
was still added defensively since it shares the same underlying data source.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
→ BUILD SUCCESSFUL, 77 tests, 0 failures/errors (69 baseline + 8 new:
5 in `LiveDiscoveryCenterTest`, 3 in `LiveBroadcastInteractionsTest`).
Additionally ran `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL.
Committed, not pushed / no PR opened (isolated worktree
`.worktrees/prevfix`, branch `fix/live-preview-and-selfdiscovery`, built on
top of origin/main c63a9b9 / rel/vc55b).

## 2026-07-31 — Nuru Live host-process-death ROUND 2 (disassembly-proven root cause) + guest WHIP/WHEP auth fixed to Basic (branch fix/live-guest-auth-and-crash, this repo only, on top of #73/#74/#76/#78/L6b/L6c/host-stability/#85/#86/#87)

Two independent, production-proven bugs, both blocking guests from ever
successfully joining a live broadcast.

### Bug 1 — the host still crashed the FIRST time WebRTC loaded, even after #85/#86

The prior incident (`#85`/`#86`, shipped as vc55, 2.30.0) root-caused a
SIGTRAP/TRAP_BRKPT inside `libjingle_peerconnection_so.so`'s `JNI_OnLoad`
as R8 stripping `org.webrtc.**` members the native init resolves by name,
and fixed it with a `-keep class org.webrtc.** { *; }` rule. The owner
captured the SAME tombstone again on vc56 (versionCode 56, `2.31.0`,
`.worktrees/vc56`), built on top of that fix — proof #85/#86 was necessary
but not sufficient.

**Root cause, proven by disassembly, not inference**: pulled the resolved
`io.github.webrtc-sdk:android:144.7559.09` AAR's `arm64-v8a` `.so` straight
out of Gradle's cache and disassembled it with Xcode's `llvm-objdump`
(`--triple aarch64`) at the tombstone's own PC offsets:

- `JNI_OnLoad+64` (`0x298e60`) calls `InitGlobalJniVariables` (`jvm.cc`,
  `0x394e58`) — its `RTC_CHECK(!g_jvm)` guard is checked and PASSES (`g_jvm`
  was null: this is a first load, not "`JNI_OnLoad` called twice", the
  hypothesis #85/#86 had already correctly demoted).
- `InitGlobalJniVariables` then falls into WebRTC's `jni_zero` JNI-bridge
  bootstrap, which calls `env->FindClass("org/jni_zero/JniInit")`
  (the literal class-name string recovered from `.rodata` at the call
  site, `0x3a2480`–`0x3a2498`).
- Tracing the callee (`0x3a2680`): it calls `FindClass` (JNIEnv vtable
  offset `0x30`, index 6), then `ExceptionCheck()` (offset `0x720`, index
  228); if an exception is pending OR the returned `jclass` is null, it
  calls `ExceptionDescribe()`/`ExceptionClear()` (offsets `0x80`/`0x88`,
  indices 16/17) and falls into a `RTC_CHECK`-style `FatalMessage` builder
  (`0x3a2808`, a `vsnprintf`-into-growing-buffer routine — the
  `Check failed:`-formatter pattern) which ends in `__builtin_trap()`
  (`BRK #imm`) → exactly `SIGTRAP`/`TRAP_BRKPT`, `esr 0x92000006`, matching
  the tombstone frame-for-frame.
- `org.jni_zero.JniInit` (and ~15 siblings: `CalledByNative`,
  `JNINamespace`, `NativeMethods`, …) IS present in the resolved AAR's
  `classes.jar` (`unzip -l`, confirmed) but lives in a package the
  `org.webrtc.**` keep rule never covers, and is only ever referenced via
  JNI reflection — R8 full mode has nothing else keeping it reachable, so
  it strips it. **Falsified against the actual crashing build**: `grep -c
  jni_zero` on `.worktrees/vc56/app/build/outputs/mapping/release/
  mapping.txt` → `0`, and `strings` on both `classes*.dex` inside
  `vc56`'s own `app-release.apk` → zero `jni_zero`/`JniInit` hits anywhere.
  `FindClass` on a class absent from the dex is exactly a
  `ClassNotFoundException` → the exact failure branch traced above.

**Fix**: `app/proguard-rules.pro` — added `-keep class org.jni_zero.** { *;
}` / `-keepclassmembers` / `-dontwarn`, mirroring the existing
`org.webrtc.**` block, with the disassembly trail documented inline.
**Re-verified against a freshly built release APK from this branch**:
`grep -c jni_zero mapping.txt` → `39` (all `org.jni_zero.*` classes present,
unrenamed), and `strings classes2.dex | grep jni_zero/JniInit` →
`Lorg/jni_zero/JniInit;` present. `org.webrtc.PeerConnectionFactory` still
unrenamed (regression check) and every bundled `.so`'s `PT_LOAD` segments
still align at `2**14` (16 KB — unrelated to this fix, re-checked anyway).

**Bulletproofing on top of the root-cause fix** (`LiveWebRtc.kt`) — a native
`BRK` trap can never be caught by `try`/`catch`, so the only thing Kotlin
code can do is (a) guarantee the risky native init call runs AT MOST ONCE
per process no matter how many callers race it, and (b) move that one
attempt to a moment the app can afford to lose it, off the guest-join
critical path:
- New `OneShotInit<T>` (double-checked-locking, `@Volatile` cached
  `Result<T>`): `LiveWebRtc.factory()` now routes through
  `factoryInit.getOrInit { createFactory(context) }` instead of its own
  ad hoc synchronized block. Every caller after the first winner —
  success or failure — gets the SAME cached outcome; a failed attempt is
  NEVER retried. `WhipPublisher` (guest) and `WhepSubscriber` (host) can
  race this concurrently (a guest publish and a host subscribe landing in
  the same instant) and only one native init attempt will ever happen.
- New `LiveWebRtc.warmUp(context)` — proactively runs `factory()` off a
  background dispatcher at a safe moment, wired into `LaunchedEffect(Unit)`
  in `GoLiveSetupSheet.kt` (host, fires the instant the Go Live sheet
  opens — well before the broadcast starts or any guest can join) and
  `LivePlayerScreen.kt` (any viewer of a LIVE stream — before they'd ever
  raise a hand and get accepted as a guest). Fire-and-forget; failures are
  logged, not blocking — the existing `guestTileErrors`/`GuestStageState.
  Error` surfaces from `sub.start()`'s own `runCatching` already turn any
  cached `OneShotInit` failure into the honest in-app error chip the task
  asked for, with no new UI needed.
- `lastInitError()`/`isReady()` exposed for any future caller that wants to
  check WebRTC availability without triggering an attempt.

**If the root cause had turned out to be a genuine SDK incompatibility**:
it isn't — `org.jni_zero.JniInit` is present, loadable, and simply needs
keeping, so no `io.github.webrtc-sdk:android` version change is warranted
or was made.

### Bug 2 — guests could never authenticate (WHIP/WHEP query-param auth silently ignored)

Reported as already proven by the owner via a controlled production
experiment before this session started: POSTing to the WHIP endpoint with
`?user=&pass=` in the URL (the app's existing behavior) reaches the auth
webhook with an EMPTY `user` (`400 VALIDATION_FAILED`); probing the same
endpoint with fabricated `?user=PROBEUSER&pass=PROBEPASS` still forwarded an
empty user (MediaMTX v1.19.3 ignores WHIP/WHEP query-param auth entirely);
the same probe with HTTP Basic auth reached the webhook with the real
username (correctly denied — proving Basic is the mechanism this MediaMTX
version honours).

**Fix** (`LiveWebRtc.kt`, `WhipPublisher.kt`, `WhepSubscriber.kt`):
credentials no longer go in the URL at all. New `basicAuthHeader(user,
pass)` (pure function, `java.util.Base64` — not `android.util.Base64` —
specifically so it runs unmocked in plain JVM unit tests) builds
`Authorization: Basic base64(user:pass)`; `postSdpOffer`/`deleteResource`
now take `(url, user, pass)` and attach that header via two new pure
request builders (`buildSdpOfferRequest`/`buildDeleteRequest`, split out
so tests can inspect the built `Request` without a real network call).
Both `WhipPublisher` and `WhepSubscriber` cache their session's
`(user, pass)` as instance fields (set in `startLocked`) so `stopLocked`'s
teardown DELETE against the WHIP/WHEP `Location` resource URL carries the
SAME Basic auth header the offer POST used — previously that DELETE carried
NO auth at all (the `Location` header never carried the query-param
credentials either), which would have silently no-op'd server-side rather
than failing loudly. Query-string auth is gone entirely — the reported
mechanism-mismatch is fixed at the transport level for both the guest's
publish (`WhipPublisher`) and the host's subscribe (`WhepSubscriber`).

**New tests** (`LiveWebRtcTest.kt`, plain JUnit, no Robolectric — matches
this file's existing posture): `basicAuthHeader` correctness (base64
round-trip, correct user:pass pairing not swapped, credentials containing
colons/special chars still round-trip correctly); `buildSdpOfferRequest`/
`buildDeleteRequest` never put credentials in the URL (`request.url.query`
is null, URL string doesn't contain the raw secret) and always attach the
matching `Authorization` header; different credentials produce different
headers. `OneShotInit` (Bug 1's guard): single-shot value caching, single-
shot failure caching (init never re-invoked on a second call), and a real
50-thread concurrency test (all threads released simultaneously via a
`CountDownLatch`, a slow/racy 20ms init body to maximize the collision
window) asserting the init lambda ran exactly once and every thread
observed the identical cached result.

**What else was audited for the same class of bug**: grepped this repo's
`Request.Builder()` call sites — `LiveWebRtc.kt`'s `postSdpOffer`/
`deleteResource` were the only WHIP/WHEP-authenticated requests; the
RTMP publish path (`LiveBroadcastEngine.buildPublishUrl`) already used
query-param `?user=&pass=` too but is RootEncoder's own RTMP client, a
completely different protocol/auth path (RTMP has no HTTP Basic-auth
concept) that the owner's brief explicitly did not report as broken and
this session did not touch.

**Honest limits**: Bug 1's fix is proven by disassembly of the exact
crashing binary AND falsified/re-verified against real built APKs (before:
`org.jni_zero` absent from both `mapping.txt` and the shipped dex of the
actual vc56 build the tombstone came from; after: present, unrenamed, in a
release APK built from this branch) — as close to a device-level proof as
is possible without a physical device in this session. It was NOT re-run on
the owner's S24 this session (no adb/device access here); the owner's next
sideload is the final confirmation. Bug 2's fix directly matches the
owner's own controlled experiment (Basic auth reached the webhook with the
real username; query params did not) but the actual guest-join round trip
against the LIVE MediaMTX/webhook was likewise not re-run end-to-end this
session (backend webhook changes are explicitly out of scope / being done
by a separate agent per the task brief) — only the client-side request
construction is covered by tests here.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleRelease` → BUILD SUCCESSFUL, 86 tests total (77 baseline + 9
new, all in `LiveWebRtcTest`), 0 failures. `assembleRelease` succeeded
(unsigned — this worktree's copied `local.properties` doesn't carry release
signing secrets, immaterial to dex/mapping verification above). Committed,
not pushed / no PR opened (isolated worktree `.worktrees/guestfix`, branch
`fix/live-guest-auth-and-crash`, built on top of origin/main 3c98751 /
rel/vc56).

## 2026-07-31 — Nuru Live go-live cell targeting: leaders of several cells can now pick WHICH cell to broadcast to (branch feat/live-cell-targeting, this repo only, on top of #73/#74/#76/#78/L6b/L6c/host-stability/#85/#86/#87/#88)

**The gap** (owner-confirmed, tonight's iOS↔Android parity audit): on iOS,
`GoLiveSetupSheet.cellOptions` lists the *specific* cells a broadcaster
leads (sourced from `GET /disciples`, already scoped server-side to the
caller's own `leader_assignments`) and shows a radio-row picker once there's
more than one. Android's `GoLiveSetupSheet.kt` only ever offered a binary
"Everyone" vs a single generic "My cell" — a leader of 3 cells had no way to
choose which one, and Start would silently send whatever `me.profile.
cellGroupId` happened to be (their own personal cell membership, which may
not even be one of the cells they lead).

**Fix — mirrors iOS's semantics exactly, same endpoint, same request
shape**: `GoLiveShared.kt` gains `LedCell(id, name)` plus four pure,
non-Composable functions (unit-testable without Compose or network, same
posture as `filterOutSelfStream`):
- `ledCellsFromRoster` — dedupes `GET /disciples`' `RosterRow` list down to
  the distinct `(cellGroupId, cellName)` pairs it names.
- `deriveCellOptions` — the roster's led cells if any exist; else a single
  fallback option built from the member's own `cellGroupId` (today's
  behavior, unchanged for every non-leader this picker is new for); else an
  empty list — **never a placeholder, never an empty picker**.
- `defaultSelectedCellId` — keeps a still-valid selection, else defaults to
  the first option, else null.
- `resolveCellIdForBody` — the exact `cell_id` POST /live/streams sends: a
  real Kotlin `null` for scope=church (kotlinx's `encodeDefaults = true`,
  already configured in `ApiClient.kt`, serializes that as an EXPLICIT JSON
  `"cell_id":null`, never an omitted key — the Android half of the backend's
  `.nullish()` fix, c65c353), or the picker's selection (falling through the
  same chain `defaultSelectedCellId` would, so a never-mounted picker or a
  stale selection can't block Start) for scope=cell.

`GoLiveSetupSheet.kt` reuses the EXISTING discipler-roster fetch
(`Net.client.api.disciples()`, already used by the Discipleship Hub) —
no new endpoint. Best-effort (`runCatching`): a plain member's `/disciples`
call 403s (Instructor+ only) and `deriveCellOptions` just falls back to the
personal-membership single option, exactly like before. A new
`CellChoiceRow` composable — gold-chip styling (`Nuru.goldChipBg`/
`goldChipText`/`gold`), matching the `Radii.control` corner radius and
`Spacing` tokens already used throughout this sheet, visually distinct from
(nested under) the navy-selected `AudienceOption` it belongs to — renders
one row per led cell, **only** when "A cell / class" is the active audience
**and** there's an actual choice (`cellOptions.size > 1`); a single cell
keeps today's plain `AudienceOption` subtitle, and zero cells never renders
the "A cell / class" option at all (`cellSectionAvailable` gates it). The
target is unmistakable before Start is even tappable — no accidental
church-wide broadcast to a real congregation.

The `lockedScope="cell"` entry point (Cell Info screen's Go Live button)
is untouched — it still forces the member's own personal cell with no
picker shown, matching iOS's `forcedCell` case exactly (no change in scope
for this task).

**New tests** (`GoLiveCellPickerTest.kt`, 21 cases, plain JUnit): zero cells
(no led cells + no personal cell → empty, never a placeholder; blank
personal id treated as absent), exactly one cell (led-cell wins over a
different personal fallback; personal fallback with a real name; personal
fallback with the generic "My cell" label when no name is loaded yet),
many cells (a 3-cell leader gets all 3, in roster order, roster wins over
personal), roster dedup/blank-name/blank-id handling,
`defaultSelectedCellId`'s keep-valid/reset-to-first/nothing-yet cases, and
`resolveCellIdForBody`'s full fallback chain including two JSON-encoding
tests (same `Json` config as `ApiClient`/`LiveDtoTest.kt`) proving
`CreateLiveStreamBody` encodes a literal `"cell_id":null` for church-wide
and the exact selected cell id for scope=cell — the wire-shape half of the
`.nullish()` requirement, not just the Kotlin-level null.

**Where exact iOS parity wasn't possible**: iOS's church-eligibility check
(`LiveBroadcastEligibility.churchEligible`) treats `Instructor` as
church-eligible too (its `staffRoles` set is `{Instructor, Admin,
SuperAdmin}`); Android's `isChurchLiveEligible` only accepts `{Admin,
SuperAdmin}`. This is a pre-existing discrepancy in `GoLiveShared.kt`
unrelated to cell targeting — left untouched since the owner's brief scoped
this session to the cell-picker gap specifically, not a general
`GoLiveSetupSheet` eligibility re-audit. Flagged here for a future pass
rather than folded into this change.

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleRelease` → BUILD SUCCESSFUL, 107 tests total (86 baseline + 21
new, all in `GoLiveCellPickerTest`), 0 failures. `assembleRelease`
succeeded. Committed, not pushed / no PR opened (isolated worktree
`.worktrees/celltarget`, branch `feat/live-cell-targeting`, built on top of
origin/main 2b1522f / rel/vc57).

---

## 2026-07-31 — WHEP subscribe retry-with-backoff + host reaction-emoji bug

**Bug 1 — guest video tile permanently dead ("Couldn't connect to the
stage") even though the guest joins fine.** PROVEN from production MediaMTX
logs (owner testing S24-as-host + iPhone-as-guest, same session guest auth
was confirmed fixed): the host's WHEP subscribe (`WhepSubscriber.kt`,
driven by `GuestWebRtcSupervisor.kt`) fired the INSTANT a guest was marked
accepted, but the guest's own WHIP publish only began 4–32s later
(permission prompt, camera warm-up, ICE) — MediaMTX answered `closed: no
stream is available on path 'guest/…'` (HTTP 404) and the OLD one-shot
`start()` gave up forever, leaving a dead tile under the guest's own name
(that part came from our API and was never wrong). A guest stopping and
restarting publish (`closed: terminated` then a fresh `is publishing`
moments later) hit the exact same dead end. Also observed once: `peer
connection established … closed: deadline exceeded while waiting tracks` —
a connection that came up but never carried media.

**Fix**: `WhepSubscriber.start()` is now the WHOLE connect lifecycle, not a
single attempt — `WhepRetryPolicy.kt` (new, pure, coroutine-free) supplies
the backoff schedule (0.5s → 8s cap, exponential) and failure
classification: HTTP 404/5xx and bare `IOException`s classify as
`NotYetAvailable` (retry), a new client-mirrored
`WhepTracksTimeoutException` (thrown after `TRACK_WAIT_TIMEOUT_MS=12s` of
no `onTrack`) classifies as `TracksTimedOut` (retry, and the half-dead
connection is torn down before the next attempt — never left hanging),
401/403/anything else classifies `Terminal` (surfaced immediately, no point
retrying bad credentials). The whole cycle gets a 45s window
(`WhepRetryPolicy.DEFAULT_WINDOW_MS`) before actually surfacing an error.
Once live, the peer connection's ICE state (`DISCONNECTED`/`FAILED`/
`CLOSED`) is watched; a drop reconnects automatically with a FRESH 45s
window (a guest republish is treated as a brand-new join, not a countdown
already half spent) instead of sticking on a dead tile.
`LiveWebRtc.postSdpOffer` now throws the new `WhipWhepHttpException(code)`
(carries the HTTP status) instead of a bare `IllegalStateException`, so the
policy can classify precisely instead of parsing message strings.

New `WhepConnectionState` (Connecting/Live/Error — shape matches the
guest's own `GuestStageState`) replaces the old boolean-ish
`guestTileErrors` map; `HostGuestTileState`/`GuestTile`
(`GuestStageUi.kt`) now read it directly (Compose `mutableStateOf` on the
subscriber) — the tile shows a subtle "Connecting…" label while retrying
(never the alarming "Couldn't connect" mid-retry — that's what made a join
seconds from working look like the whole feature was broken) and only
flips to the warning chip + a tap-to-retry affordance once the window
genuinely expires or the failure is terminal. `lifecycleMutex` now scopes
to each INDIVIDUAL connect attempt (not the retry loop's backoff waits or
the live-monitoring suspension) so Leave Stage/screen-exit/broadcast-end
can still preempt promptly instead of blocking for up to a backoff or
track-wait duration; `stop()` cancels the track-arrived/connection-drop
signals before acquiring the lock specifically so an attempt parked inside
either wait wakes immediately.

**Bug 2 (owner-reported, fixed alongside) — host sees raw reaction key text
instead of an emoji, with no count.** When a viewer tapped 🔥, the host's
broadcast screen rendered the literal string `"fire"` as floating text and
had NO persistent reaction count at all outside the Reduce-Motion fallback
(`ReactionCounterChip`, a single hardcoded ❤️ total). Root cause: backend
reaction keys (`"like"|"love"|"fire"`, migration
`1758000000182_live-reaction-fire`) are a free string on purpose (an
unclosed set — a new key can ship server-side without breaking old
clients), and there was no shared key→emoji mapping — `LiveActionRail`
(viewer) had the glyphs hardcoded per-button; both screens' floating
particles (`reactionParticles.spawn(...)` / `particles.spawn(...)`) spawned
the RAW key text directly. Added ONE canonical `reactionEmoji(key): String`
(`LiveInteractions.kt`) with a neutral `"✨"` fallback for anything
unrecognized — an unknown key must never render as raw text, which is
exactly how this bug shipped. Every glyph-rendering call site now routes
through it: `LiveActionRail`'s buttons, both screens' particle spawns
(including the viewer's own-tap spawn, which had the same latent bug), and
a new `LiveReactionCounts` composable that gives the broadcaster HUD the
SAME persistent emoji+count pairing the viewer's rail shows (driven
straight off the server-authoritative `pulse.reactions` map, not a
separately-accumulated local tally — the old `reduceMotionReactionTotal`
counter is gone) — shown regardless of Reduce Motion, since a count is
data, not a decorative animation. Ordering/aggregation split into a pure
`orderedReactionEntries()` for testability.

**Tests** (all pure, no real network/WebRTC, per the fix brief):
`WhepRetryPolicyTest.kt` (20 cases) — backoff schedule (initial/doubling/
cap/overflow-safety/custom params), window expiry, HTTP-status
classification (404/5xx retryable, 401/403/unknown terminal),
`WhepTracksTimeoutException`/`WhipWhepHttpException`/bare-`IOException`
classification, and the retryable/terminal partition + reason strings.
`LiveInteractionsTest.kt` (+6 cases) — `reactionEmoji` for every known key
plus unknown/blank/wrong-case never falling through to raw text, and
`orderedReactionEntries` (zero/negative dropped, known-first stable order,
unknown alphabetical after, empty-safe).

Verified: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleRelease` → BUILD SUCCESSFUL, 133 tests total (107 baseline +
26 new: 20 in `WhepRetryPolicyTest`, 6 added to `LiveInteractionsTest`), 0
failures. `assembleRelease` succeeded. Committed, not pushed / no PR opened
(isolated worktree `.worktrees/wheprace`, branch
`fix/whep-subscribe-retry`, built on top of origin/main 8bc3d9e).

**Honest limits**: the retry loop, ICE-drop detection, and republish
recovery are exercised by the pure policy tests plus manual reasoning
about the coroutine/mutex structure — NOT against a real MediaMTX server or
real WebRTC engine (explicitly out of scope per the fix brief: "do not
test real network"). `TRACK_WAIT_TIMEOUT_MS` (12s) and the retry window
(45s) are informed estimates from the production log evidence (worst case
observed: 32s to first publish), not values MediaMTX itself documents —
worth revisiting if a guest join genuinely needs longer than 45s total to
land. The republish-recovery path assumes a drop always shows up as an ICE
state transition to DISCONNECTED/FAILED/CLOSED; if MediaMTX ever tears down
a WHEP session in a way that leaves ICE looking healthy (no such case is in
the log evidence), that specific drop wouldn't be caught until the next
unrelated signal.

## 2026-07-31 — Nuru Live L7: Zoom-style stage (host+guest compositing,
no more portrait letterbox) + full iOS parity sweep (branch
feat/live-stage-layout, this repo only, on top of #73/#74/#76/#78/L6b/L6c/
host-stability/#85/#86/#87/#88/#89/#90)

**Starting point**: a previous agent session was killed mid-task by an
infrastructure error. Its work survived and compiled clean:
`GuestStageCompositor.kt` already had the FULL fixed-slot GL-filter
compositor mechanism (main slot 0 + `RAIL_SLOT_COUNT` rail slots,
`TileRect`/`mainTileRect`/`railTileRects` pure layout math, the
`StageSpotlightState`/`promoteSpotlight`/`demoteSpotlight`/
`toggleSpotlight`/`reconcileSpotlightOnGuestsChanged`/`railItems` spotlight
state machine, alpha-fade slot reassignment, per-guest name plates with a
best-effort mute proxy), plus a new `StageTileFilterRender.kt` +
`stage_rail_fragment.glsl` (a custom RootEncoder object filter with a
rounded-corner + subtle-border fragment shader for the rail tiles). It
stopped at the exact moment it was about to write the Compose UI — no
`LiveStageView.kt`-equivalent existed, and the host's own on-screen stage
was still the OLD draggable `HostGuestRail` (GuestStageUi.kt), completely
disconnected from the spotlight state the compositor already had.

**Owner report (live two-device test, root cause)**: "when now you invite
the person, the 1080x1920 portrait seems to be lost a bit, and the host
video is completely replaced by invited guest." iOS (nuru-member-ios
origin/main 4ed9883, PR #106, same day) had ALREADY shipped and root-caused
the Android-side analogue of both defects:
- **Portrait loss** — a guest's non-matching-aspect track (e.g. landscape)
  got aspect-FIT (letterboxed) instead of aspect-FILLED when it became the
  full-frame tile; the canvas dimensions themselves never moved. On
  Android, the previous agent had ALREADY fixed the Android-specific
  version of this (`EglRenderer.setLayoutAspectRatio` per slot, matched to
  that slot's destination rect aspect) before this session started — see
  `GuestStageCompositor.kt`'s own "DEFECT A" header section.
- **Host replaced outright** — an automatic active-speaker/loudest-guest
  auto-promotion swapped the WHOLE frame with no user control. The previous
  agent had ALREADY replaced this with the `StageSpotlightState` manual
  tap-only model before this session started (no auto-promotion code exists
  anywhere in this codebase's live feature — verified by grep for
  loudest/activeSpeaker/audioLevel-driven promotion; the only place
  `GuestAudioMixer.currentLevels()` is read is the best-effort MUTED
  indicator, never a spotlight decision).

**This session's work — finishing the Compose UI (the part that was never
started)**:
- New `LiveStageView.kt`: the `LiveStage` composable — one full-bleed main
  tile (host by default, or whoever's spotlighted) + a right-edge rail for
  everyone else, replacing `GuestStageUi.kt`'s old free-draggable
  `HostGuestRail`/`GuestTile` (removed entirely — dead code once `LiveStage`
  took over). Every tile (host + each guest) is wrapped in a stable `key()`
  so its underlying `AndroidView` (the host's RootEncoder `SurfaceView` /
  each guest's `WebRtcSurfaceView`) is NEVER recreated on a spotlight
  swap — only `Modifier.offset`/`size`/corner-radius animate via
  `animateDpAsState(tween(SWAP_ANIM_MS, LinearOutSlowInEasing))`, matching
  this app's existing motion grammar and iOS's identical 250ms tween. Rail
  tiles get a higher `zIndex` than the main tile — not cosmetic: the main
  tile is full-canvas, so without it a rail tile would paint underneath and
  vanish. Geometry comes from the SAME `mainTileRect`/`railTileRects`/
  `railItems` the compositor uses (fed this Box's own measured screen size
  instead of the encoder's 1080x1920 canvas) — one algorithm, two canvas
  sizes, so the host's own preview and what the congregation receives
  cannot independently drift.
- Renamed `GuestStageCompositor.setSpotlight` → `tapStageTile` — the single
  entry point `LiveStage`'s host-tile tap (`guestId = null`) and every
  guest-tile tap (`guestId = <userId>`) both call, mirroring iOS's
  `BroadcastController.tapStageTile(_:)` naming exactly per the task brief.
- Wired `LiveBroadcastScreen.kt`: collects `GuestStageCompositor.spotlight`
  + (new) `GuestStageCompositor.mutedGuests` as top-level Compose state,
  replaces the raw full-screen `SurfaceView` `AndroidView` + the
  `HostGuestRail(...)` call with one `LiveStage(...)`, passing the existing
  SurfaceView/SurfaceHolder.Callback block through as `LiveStage`'s
  `hostVideo` content lambda (unchanged internals — `BroadcastController.
  attachPreview/detachPreview` still owns the actual preview attach).
- New `GuestStageCompositorTest.kt` (28 tests): `mainTileRect`/
  `railTileRects` for 0–6 guests (in-bounds, non-overlapping, stable
  top-to-bottom ordering, right-edge placement, main-tile geometry provably
  independent of guest count, invalid-canvas safety), the full
  `StageSpotlightState` promote/demote/toggle/reconcile state machine
  (promote, demote-is-a-no-op-at-host, revert, switch directly between
  guests, full promote→demote→revert→promote→revert cycle, spotlighted
  guest leaving falls back to host, non-spotlighted guest leaving is inert,
  last guest leaving collapses the rail to empty), and `railItems`'s
  display-order contract (host placeholder first when spotlighting, stale
  spotlight ids ignored, capped at `MAX_GUESTS` either way). Mirrors iOS's
  `LiveStageTests.swift` (21 tests) test-for-test where the API shapes
  line up; Android's nullable-`String?` spotlight model needs two entry
  points (`demoteSpotlight`/`tapStageTile(null)` for "tap host's own rail
  thumbnail" vs `toggleSpotlight` for "tap the currently-spotlighted guest's
  own main tile") where iOS's `UInt8` track-number space (host = track 0)
  handles both through one `tap(track:)` — covered as separate cases here
  rather than forced into one shared test.

**Scope expansion mid-task (owner, direct instruction): "the next samsung
build to be at parity with iphone"** — full live-feature parity sweep, not
just the stage layout:

1. **Church-broadcast eligibility widened, Instructor included.**
   `isChurchLiveEligible` (`GoLiveShared.kt`) only offered the "Church"
   scope to `Admin`/`SuperAdmin`; iOS's `LiveBroadcastEligibility.
   churchEligible` (nuru-member-ios) already included `Instructor`, and
   VERIFIED against the actual backend authority
   (`packages/backend/src/modules/live/service.ts:326-327` `isStaff()` →
   `IdentityService.STAFF_ROLES` = `{Instructor, Admin, SuperAdmin}`,
   `packages/backend/src/modules/identity/service.ts:169`) — the backend
   has ALWAYS allowed an Instructor to go live church-wide; Android's
   client-side gate was simply narrower than the server's own rule, so an
   Instructor could never SEE the option the server would have granted.
   Widened to match, with an explicit "OWNER DECISION, 2026-07-31" comment
   at the change site (`GoLiveShared.kt`) so a future session doesn't
   "fix" it back to Admin/SuperAdmin-only assuming it's a regression.
   5 new tests in `GoLiveCellPickerTest.kt`.
2. **Mic-muted rail indicator, debounce ported from iOS.** iOS's
   `BroadcastController.updateMuteHeuristic()` polls every 3s and only
   flags a guest muted after 3 CONSECUTIVE quiet polls (~9s sustained
   silence) — a deliberate anti-flicker debounce, not an instantaneous
   check. `GuestStageCompositor.kt` gained the same shape: a new
   `mutedGuests: StateFlow<Set<String>>`, a dedicated 3000ms ticker
   (independent of the compositor's own 1200ms `reflow()` cadence, so the
   debounce window means a real ~9s regardless of GL-slot timing), and a
   3-consecutive-poll streak counter. Both the composited RTMP name plate
   (`plateNameFor`) and `LiveStageView.kt`'s rail chrome now read this SAME
   `StateFlow` — one debounce, not two independently-computed guesses (an
   IMPROVEMENT over iOS, which has two separate poll loops — the
   compositor's own `setMuted` calls and `BroadcastController`'s dict —
   that happen to agree only because BroadcastController pushes into both).
   The raw per-sample THRESHOLD is deliberately NOT numerically equal to
   iOS's (0.02): Android's signal is `GuestAudioMixer`'s smoothed
   mean-absolute-16-bit-PCM level (0..32767ish), iOS's is WebRTC's
   normalized 0..1 `audioLevel` stat — different scales by construction:
   what's ported exactly is the CADENCE (3000ms) and STREAK (3), so a
   guest reads as muted on both platforms within the same ~9s window.
3. **Rail margin/spacing constants aligned exactly to iOS's
   `LiveStageLayout.swift`** — `RAIL_MARGIN_TOP/BOTTOM/RIGHT_FRACTION`
   0.06/0.06/0.025 → 0.03/0.03/0.03 (iOS's single uniform `marginFraction`),
   `RAIL_GAP_FRACTION` 0.02 → 0.018 (iOS's `spacingFraction`). Purely
   visual — the overflow-proof rail algebra doesn't depend on the specific
   values (re-verified by `GuestStageCompositorTest`'s in-bounds/no-overlap
   coverage after the change). `RAIL_WIDTH_FRACTION` (0.22) already matched
   iOS exactly before this session.
4. **WHEP retry constants** (`WhepRetryPolicy.kt` vs iOS's
   `WhepRetryPolicy.swift`, 4ed9883 tree) — already at exact numeric parity
   before this session: `INITIAL_DELAY_MS`/`initialBackoff` 500ms,
   `MAX_DELAY_MS`/`maxBackoff` 8000ms, `DEFAULT_WINDOW_MS`/`window` 60s,
   `TRACK_WAIT_TIMEOUT_MS`/`attemptWatchdog` 25s. No change needed.
5. **`MAX_GUESTS` cap** — Android 6, iOS `1...6` (host = track 0, guests
   1–6) — already matched, confirmed by reading `BroadcastController.
   nextFreeGuestTrack()`. No change needed.
6. **Animation duration** — `SWAP_ANIM_MS` 250ms already matched iOS's
   `LiveStageView.swapDuration` 0.25s exactly. No change needed.
7. **Border/shadow on rail tiles** — Android's new `LiveStage` rail-tile
   chrome (`Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f))`,
   `Modifier.shadow(8.dp, ...)`) was written to match iOS's SwiftUI
   `.stroke(Color.white.opacity(0.35), lineWidth: 1.5)` /
   `.shadow(radius: 8, ...)` before this parity sweep even started —
   confirmed matching, no change needed.

**Deliberately NOT changed — genuine differences, not gaps**:
- **401/403 retry classification.** iOS's `WhepRetryPolicy.classify(_:)`
  is "deliberately generous" — only a structurally-bad URL and explicit
  cancellation are non-retryable, so a guest with BAD CREDENTIALS retries
  silently for the full 60s window before surfacing an error. Android's
  `classifyWhepHttpStatus` fails FAST on 401/403 (`Terminal`, immediate
  "Not authorized..." message) — matching Android's own pre-existing
  reasoning ("retrying bad credentials changes nothing"). This was left
  as-is rather than degraded to match iOS: it is a real behavioral
  difference, not a numeric-constant gap, and Android's fail-fast behavior
  is arguably the better UX (a guest who will never connect finds out in
  under a second instead of after a minute). Flagged here rather than
  silently changed OR silently left unmentioned.
- **Composited-broadcast corner radius vs. the host's own local preview.**
  The RTMP frame's rail-tile rounding is baked into
  `stage_rail_fragment.glsl` as a GLSL `const float CORNER_RADIUS = 0.16`
  (a fraction of the TILE's own width) — architecturally fixed at
  shader-compile time (`StageTileFilterRender.kt`'s header explains why a
  per-instance uniform isn't reachable from a `BaseObjectFilterRender`
  subclass). iOS instead computes corner radius as a fraction of the
  CANVAS's shortest side (`LiveStageLayout.cornerRadiusFraction = 0.05`),
  shared identically by both its compositor and its SwiftUI stage. Rather
  than making Android's Compose-side rail chrome match iOS's canvas-based
  formula (which would make the host's OWN on-screen preview's rounding
  drift slightly from what the GL shader actually bakes into the broadcast
  frame the congregation receives — a regression against this feature's
  own core "preview must match broadcast" requirement), `LiveStageView.kt`
  computes its Compose corner radius as `rect.width * 0.16f` — the SAME
  0.16 the shader already uses, in the same "fraction of tile width" units.
  Net effect: Android's preview/broadcast pair is pixel-consistent with
  itself; it is not byte-for-byte numerically identical to iOS's
  canvas-fraction formula. A reworked shader mechanism that could accept a
  canvas-relative radius as a uniform would close this, but that's a
  bigger, riskier change than this task's scope justified.
- **Shadow color/offset** — Compose's `Modifier.shadow` doesn't expose a
  directional y-offset or an explicit shadow-color alpha the way SwiftUI's
  `.shadow(color:radius:y:)` does; Android gets a platform-default ambient/
  spot shadow at the same 8dp radius iOS uses, not a pixel-identical shadow
  render. Not fixable without a custom `drawWithContent` shadow — out of
  scope for a visual nuance this minor.

### Parity table (Nuru Live, this session's scope)

| Capability | iOS | Android (after this session) | Status |
|---|---|---|---|
| Stage model | One full-bleed main tile + right-edge rail, manual tap-to-spotlight | Same | PARITY |
| Auto-promotion (active speaker) | Removed (L7) | Never existed in this codebase's current form (removed by the previous agent before this session) | PARITY |
| Portrait/aspect-fill on the main tile | Fixed via `mainOverlay` (`.resizeAspectFill`) never touching the built-in object | Fixed via `EglRenderer.setLayoutAspectRatio` per slot (previous agent) | PARITY (different mechanism, same guarantee) |
| Canvas/encoder dimensions vs. guest count | Never change (`mixer.setVideoMixerSettings` called once) | Never change (fixed GL filter slots, logged before/after) | PARITY |
| Spotlight state machine (promote/demote/revert/switch/leave-fallback) | `LiveStageSpotlight` (`UInt8`, host=0) | `StageSpotlightState` (`String?`, host=null) | PARITY (equivalent semantics, different type shape — see host-tap vs guest-tap entry-point note above) |
| Single tap entry point driving both compositor + local preview | `BroadcastController.tapStageTile(_:)` | `GuestStageCompositor.tapStageTile(guestId)` | PARITY (renamed to match this session) |
| Rail width fraction | 0.22 | 0.22 | PARITY |
| Rail margin/spacing fractions | 0.03 / 0.018 | 0.03 / 0.018 (aligned this session) | PARITY |
| Rail tile aspect | 0.75 (portrait-ish) | 0.75 | PARITY |
| Swap animation duration | 250ms | 250ms | PARITY |
| Rail-tile border | white 0.35 alpha, 1.5pt/dp | white 0.35 alpha, 1.5dp | PARITY |
| Rail-tile shadow | black 0.35 alpha, radius 8, y 4 | platform-default shadow, radius 8dp | DIFFERS — Compose's `shadow` modifier has no color/y-offset knob; visual nuance only |
| Composited-broadcast corner radius | 0.05 × canvas short side | 0.16 × tile width (GLSL-shader-baked) | DIFFERS — different rendering pipelines (HaishinKit screen objects vs. RootEncoder GL filters); Android's choice keeps ITS OWN preview/broadcast pixel-consistent, which iOS's own formula also achieves for itself |
| Max guests on stage | 6 (tracks 1...6) | 6 (`MAX_GUESTS`) | PARITY |
| Guest connection states | connecting / live / failed(message) / ended | Connecting / Live / Error(message) | PARITY |
| Compact vs. full failure-state text sizing | `guestFailureOverlay(..., compact:)` | `GuestConnectionOverlay(..., compact)` | PARITY |
| Mic-muted rail indicator | 3s poll × 3-streak debounce, WebRTC audioLevel threshold 0.02 | 3000ms poll × 3-streak debounce (ported this session), `GuestAudioMixer` PCM-level threshold 60 | PARITY in cadence/debounce shape; threshold numbers intentionally NOT equal (different signal scales) |
| WHEP retry backoff (initial/cap/window/watchdog) | 0.5s / 8s / 60s / 25s | 500ms / 8000ms / 60000ms / 25000ms | PARITY |
| WHEP retry classification of 401/403 | Retryable (retries for the full window) | Terminal (fails immediately) | DIFFERS — deliberate, not changed (see "Deliberately NOT changed" above) |
| Church-wide go-live eligibility | `{Instructor, Admin, SuperAdmin}` | `{Instructor, Admin, SuperAdmin}` (widened this session, owner-directed) | PARITY |
| Cell-scoped go-live eligibility + multi-cell picker | Has a cell / leads several | Has a cell / leads several | PARITY (pre-existing, 2026-07-31 earlier session) |
| Last guest leaving | Rail collapses to empty, host stays/falls back to main | Same (`reconcileSpotlightOnGuestsChanged` + empty `railItems`) | PARITY |
| Video-surface identity across spotlight swaps | Persists (no recreate) | Persists (no recreate — stable `key()` per tile) | PARITY |

**Verify**: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleRelease` → BUILD SUCCESSFUL. 166 tests total (133 baseline +
28 new in `GuestStageCompositorTest` + 5 new in `GoLiveCellPickerTest`), 0
failures. `assembleRelease` (R8-minified) succeeded — only pre-existing,
unrelated deprecation warnings (AutoMirrored icon variants elsewhere in the
app). Committed in this worktree (`.worktrees/stagelayout`, branch
`feat/live-stage-layout`, built on top of origin/main `bc25308`); not
pushed, no PR opened.

**Honest limits**: `GuestStageCompositor`'s new mute-heuristic debounce
(`updateMuteHeuristic`) is not unit-tested directly — it's a private method
on the same GL/WebRTC-bound singleton the rest of this file's own
convention (and iOS's identical choice for `LiveStageCompositor`) already
excludes from direct testing; only its pure building block
(`MUTED_LEVEL_THRESHOLD` comparison) is exercised indirectly through the
existing `isLikelyMuted`-shaped logic. The Compose `LiveStage` composable
itself has no Compose-UI-test coverage (no `createComposeRule` harness
exists anywhere in this codebase yet) — verified by code reading + the
compile/assemble gate, not by an instrumented UI test. `RAIL_MARGIN_*`/
`RAIL_GAP_FRACTION` were changed to match iOS's numbers exactly on the
strength of reading iOS's source; not re-verified against a live two-device
screenshot in this session (no device/emulator available in this
environment).

## 2026-08-01 — LIVE screen layout redesign: one top row, one bottom dock (branch feat/live-screen-layout, this repo only, on top of #73/#74/#76/#78/L6b/L6c/host-stability/#85/#86/#87/#88/#89/#90/#91, worktree `.worktrees/livelayout`)

Owner-directed redesign from a screenshot of the shipped vc60 guest-on-stage
screen: the shared content was squeezed into the middle with a vertical
reaction rail floating down the right edge on top of it, a broadcaster
identity chip floating loose mid-screen, the self-preview overlapping
content top-right (dragged to bottom-left in the reported screenshot), three
scattered top rows (close+badge / identity chip / bottom title box), and a
separate scattered bottom row (Leave stage / page indicator / controls) — an
iOS agent built the identical redesign in parallel on that surface.

**Layout (both the viewer/member screen and the guest-on-stage screen — same
`LivePlayerScreen.kt`, since a guest-on-stage viewer is that composable with
`GuestStageState.Live`):**
- Content is now genuinely edge-to-edge; the `PlayerView`/`AndroidView` was
  already `fillMaxSize()` (no change needed there) — everything else now
  overlays it instead of squeezing it.
- **ONE top row** (`LiveChrome.kt`'s `LiveTopBar`) — close, host avatar +
  name, stream title, LIVE pill, viewer count, raised-hand count, all on one
  line on a downward gradient scrim (never an opaque bar). Replaces the old
  close-badge row + separate `BroadcasterIdentityChip` row + the standalone
  `RaisedHandsChip` corner chip (all three deleted/folded in).
- **ONE bottom dock** (`LiveChrome.kt`'s `LiveBottomDock`), roughly the lower
  two inches (210dp gradient scrim), holding every control: reactions
  (❤️🔥👍), raise hand, chat always; camera on/off, switch camera, mic,
  speaker, and Leave Stage once the viewer is accepted onto the stage.
  Laid out as two rows inside one scrim (audience controls on top, publish
  controls below) purely so a phone-width row never has to fit more than
  five 48dp targets — visually one cohesive dock, not two floating groups.
  Item eligibility/order is a pure function, `LiveDockLogic.kt`'s
  `liveDockItems(role, isVideoKind)` — unit-tested (`LiveDockLogicTest`,
  6 cases: viewer excludes publish controls, guest-on-stage gets every
  control in the owner-specified order, audio-kind guest never sees camera
  controls, reaction order matches the old rail's `KNOWN_REACTION_ORDER`).
  Deleted as dead code once nothing called them any more: the old vertical
  `LiveActionRail`/`RailButton` (right-edge rail) and `RaisedHandsChip`.
- **Self-preview moved to the LEFT edge and is now minimizable** — the guest's
  own camera preview (`GuestStageUi.kt`'s `GuestSelfPreviewPiP`) defaults to
  a left-margin position (was top-right), has its name ("You") attached to
  the tile itself (bottom gradient chrome, mirroring `LiveStageView.kt`'s
  `StageTileChrome` idiom — never a loose floating label, requirement #5),
  and collapses to a small 48dp handle on tap, tappable to restore. Backed
  by a tiny explicit reducer, `SelfPreviewUiState`/`SelfPreviewAction`/
  `reduceSelfPreview` (Collapse/Expand/Toggle), unit-tested
  (`GuestStageUiTest`, 4 cases) rather than a raw boolean flip, per the task
  brief's own ask for "self-preview collapsed-state reducer" coverage.
  State lives in a plain `remember` at the `LivePlayerScreen` call site —
  "remembered within the session," the SAME idiom `LiveFloatingChat`'s own
  collapsed/expanded drag offsets already use, never persisted to disk.
  Mic mute moved OUT of the PiP's corner button into the shared dock (one
  control, one place) — `WhipPublisher` gained `setVideoEnabled(Boolean)`
  and `switchCamera()` for the dock's new camera controls (previously only
  `setMicMuted` existed).
- **Broadcast Studio follows the same grammar where it shares components**
  (`LiveBroadcastScreen.kt`'s `LiveHudOverlay`): the top-left LIVE/duration/
  watching pill and the separate bottom-left title chip merged into ONE top
  row (gradient scrim, ellipsized title sharing the line); the bottom-center
  controls row was already the one dock (mic/End/flip/source/hands/chat)
  and is unchanged. `LiveBroadcastSourceUi.kt`'s `DocumentPagerScreen`
  (Document-share mode — MediaProjection mirrors this exact screen, so it's
  literally what the congregation/guests see): the "Page N / M" indicator
  used to float top-center, separate from the mute/End/exit row at
  bottom-right; it now lives in that SAME bottom-right dock, stacked above
  the controls on one gradient scrim.

**Latency (client-side levers only — server-side HLS tuning is being handled
separately per the task brief, not touched here):**
- `ExoPlayer` now builds with a tightened `DefaultLoadControl`
  (min/max buffer 3000/8000ms, buffer-for-playback 500ms, buffer-for-
  playback-after-rebuffer 1000ms — down from Media3's defaults of
  50000/50000/2500/5000ms) so playback starts fast and never sits several
  seconds behind the live edge just because it accumulated a big buffer.
- Every `MediaItem` (initial load AND the CDN-warm-up swap) now carries a
  `LiveConfiguration` with a 3000ms target offset and a narrow
  [1.0, 1.04] playback-speed band, so ExoPlayer gently speeds up to sit
  close to the live edge instead of silently drifting behind or visibly
  jumping forward. Both levers are safe no-ops for a VOD replay — ExoPlayer
  only applies `LiveConfiguration` to an actual live manifest, and a
  tighter-but-not-starved buffer just makes VOD start faster too.
- Reactions and raise-hand were ALREADY optimistic pre-existing (the pulse
  state updates locally before the POST resolves) — confirmed, not changed.
  Chat was NOT: a sent message only appeared once `POST /live/messages`
  resolved. Fixed with a small local-only `pendingMessages` list appended
  immediately on send and reconciled (dropped) once the real row lands via
  the existing success path or the send fails — `messageCursor` (the 3s
  poll's own since-cursor) is untouched by this, so the poll's dedup logic
  needed no changes. Applied to both `LivePlayerScreen` and
  `LiveBroadcastScreen`'s chat send paths (both use the shared
  `LiveFloatingChat`). `myFullName`/`myAvatarUrl` now flow from
  `MeResponse.profile` through `MainShell` so an optimistic message shows
  the real sender identity, not a placeholder.

**Honest limits:**
- Latency levers were changed and compile/build-verified, but NOT measured
  end-to-end against a live broadcast — no device or live server round-trip
  available in this sandbox. The exact buffer/target-offset numbers are a
  reasoned starting point (Media3's own low-latency-live guidance), not
  tuned against a live measurement.
- `WhipPublisher` still always captures camera video for a guest publish
  regardless of the overall broadcast's `kind` ("audio" vs "video") — a
  pre-existing gap, not introduced or fixed here. The new dock's
  `isVideoKind` gate only hides the camera/switch-camera CONTROLS for an
  audio-kind broadcast; it doesn't change what `WhipPublisher.start()`
  actually captures. Flagged, not fixed — out of scope for a layout task.
- The SPEAKER dock control forces `AudioManager.MODE_IN_COMMUNICATION`
  while a guest is live-publishing (a real, known WebRTC-Android gotcha:
  that mode can otherwise default routing to the earpiece) and restores
  `MODE_NORMAL` on Leave Stage — reasoned from the WebRTC audio-routing
  literature, not verified against a physical device in this sandbox
  (`isSpeakerphoneOn` is deprecated API 31+ in favor of
  `setCommunicationDevice`, but remains the only mechanism spanning this
  app's full minSdk 26 range — same tradeoff `VoiceRecorder.kt` already
  accepts for a deprecated `MediaRecorder()` constructor).
- No Compose UI-test harness exists in this repo (confirmed, matching every
  prior session's note) — the new chrome/dock composables are verified by
  code reading + the compile/assemble gate + the pure-logic unit tests
  behind them, not an instrumented screenshot test.
- The 210dp dock scrim height is a reasoned approximation of "roughly the
  lower two inches," not a device-measured one (dp is defined relative to a
  160dpi baseline, so 210dp ≈ 1.3in — comfortably fits the dock's own
  content including the navigation-bar inset on every device class reasoned
  through, but no physical-device photo was taken to confirm the visual
  "two inches" read).

**Verify**: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/
Contents/Home" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
:app:assembleRelease` → BUILD SUCCESSFUL. 176 tests total (166 baseline + 6
new in `LiveDockLogicTest` + 4 new in `GuestStageUiTest`), 0 failures.
`assembleRelease` (R8-minified) succeeded — only pre-existing, unrelated
deprecation warnings (AutoMirrored icon variants elsewhere in the app; the
two new ones this session introduced, `Icons.Filled.VolumeUp`/`VolumeOff`
and `AudioManager.isSpeakerphoneOn`, were switched to the AutoMirrored
variant / explicitly suppressed respectively). Committed in this worktree
(`.worktrees/livelayout`, branch `feat/live-screen-layout`, built on top of
origin/main `40b2993`); not pushed, no PR opened.
