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
