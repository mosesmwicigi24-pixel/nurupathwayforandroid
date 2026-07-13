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
