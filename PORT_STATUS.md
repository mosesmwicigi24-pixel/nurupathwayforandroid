# Nuru member app — Swift (SwiftUI) → Android (Kotlin/Compose) port

Fresh, clean Android app that ports the native iOS member app
(`../nuru-member-ios`, repo `nurupathwayforios`) screen-for-screen. Same backend
+ OpenAPI contract as the iOS app and `@nuru/mobile` — this is a **client rewrite
only**. Legend: `✅ done · ◑ in progress · ☐ not started`.

## Stack
Kotlin · Jetpack Compose (Material3) · Navigation-Compose · Retrofit/OkHttp ·
kotlinx.serialization (SnakeCase) · DataStore + EncryptedSharedPreferences (token
vault, §5.7) · Coil. `applicationId org.nuruplace.member`, minSdk 26 / target 35,
JDK 17, AGP 8.5.2 / Gradle 8.9. Bundled OFL fonts: Inter (body) + Fraunces
(display) — parity with iOS/RN.

Base URL (mirrors iOS `resolveBaseURL`): debug → `http://10.0.2.2:8080/v1`
(emulator → host localhost); release → `https://pathway.nuruplace.org/v1`.

## Phase 0 — foundation + Login→Home slice (✅ this session)
- ✅ Design system — `ui/theme/NuruTheme.kt` (colors, gradients, radii, spacing,
  Inter/Fraunces type scale). Port of `NuruTheme.swift`.
- ✅ Core components — `ui/components/Components.kt` (NuruCard, PrimaryButton,
  BrandMark, Kicker, NuruField).
- ✅ Networking — `data/net/ApiClient.kt` (OkHttp + Retrofit, bearer inject,
  **single-flight 401 refresh** via Authenticator + lock), `MemberApi.kt`,
  `Dtos.kt`, `ApiException.kt`. Port of `APIClient.swift`.
- ✅ Token vault — `data/TokenVault.kt` (AES-256 EncryptedSharedPreferences).
  Port of `KeychainStore.swift`.
- ✅ AuthStore — `auth/AuthStore.kt` (bootstrap, login, /me, sign-out; StateFlow).
- ✅ LoginScreen (+ 2FA step) → `feature/login/LoginScreen.kt`.
- ✅ HomeScreen (greeting header + first card) → `feature/home/HomeScreen.kt`.
- ✅ Root switch (Login ↔ Home) → `feature/shell/RootScaffold.kt`.
- **Verified:** `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**, debug APK
  produced. Authed runtime render pending a live backend session (as with iOS).

## Phase 1 — Pathway (✅ done, builds green + unit-tested)
- ✅ Tab shell (Home · Pathway · Grow · Community · Profile) → `feature/shell/MainShell.kt`
  (NavHost + bottom bar; Grow/Community/Profile are placeholders until their phases).
- ✅ Levels hub → `LevelsScreen.kt` (GET /me/pathway; §1.9 locked levels dimmed).
- ✅ Level detail → `LevelDetailScreen.kt` (module trail; finished level → exam CTA).
- ✅ Module → `ModuleScreen.kt` (lesson + reflection/mark-complete, or quiz CTA).
- ✅ Quiz + Level Exam → `QuizScreen.kt` (one-per-screen flow, all 5 kinds,
  server-scored verdict, retry; shared by module quiz + level exam).
- ✅ Gating → `LevelGating.kt` (pure §1.9) + `test/…/LevelGatingTest.kt` (5 tests pass).
- ✅ DTOs + endpoints → `PathwayDtos.kt` (FlexInt numeric-drift, polymorphic quiz
  options) + `MemberApi.kt` (pathway/modules/quiz/exam).
- **Verified:** `assembleDebug` + `assembleRelease` (R8) + `testDebugUnitTest` all green.

## Screens still to port (mirrors iOS PORT_STATUS — ~37 total)
- ✅ **Phase 2 Grow** — Grow hub, Devotional (+save reflection), Memory verses
  (type-to-match practice), Reading plans (+detail/day/segments), Prayer journal
  (add/answer/delete), Verse library (add/delete). Builds green (debug+R8).
- ✅ **Phase 3 Community** — Community hub, Prayer wall (+compose/pray/detail/comment),
  Chat inbox (conversations + discover spaces) + thread (send). Builds green.
- ✅ **Phase 4 Events** — Events list (calendar window), Event detail (RSVP + roster),
  Notification center (mark-all-read). Builds green.
- ✅ **Phase 5 Giving** (online-only §5.6) — Give (fund/amount/method, M-Pesa STK +
  PayPal, card "soon"), Statement (history), Receipt (ledger trail). Builds green.
- ☐ **Phase 6 Profile** — Profile detail, Gifts, Resources, Assistant
- ☐ Home dashboard full build (rhythm ring, next-action hero, verse of the day)
- ☐ Offline engine (queue + cursors + SQLCipher cache), connectivity, push (FCM)

Each phase builds clean before moving on, same as the iOS port.
