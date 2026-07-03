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

## Screens still to port (mirrors iOS PORT_STATUS — ~37 total)
- ☐ Tab shell (Home · Pathway · Grow · Community · Profile)
- ☐ **Phase 1 Pathway** — Levels, LevelDetail, Module, Quiz (5 kinds, §1.9 lock)
- ☐ **Phase 2 Grow** — Devotional, MemoryVerse, ReadingPlans (+detail/day),
  PrayerJournal, VerseLibrary
- ☐ **Phase 3 Community** — PrayerWall (+detail), Chat inbox + thread
- ☐ **Phase 4 Events** — Events, EventDetail, Notifications, Calendar
- ☐ **Phase 5 Giving** (online-only, §5.6) — Giving, Statement, Receipt
- ☐ **Phase 6 Profile** — Profile detail, Gifts, Resources, Assistant
- ☐ Home dashboard full build (rhythm ring, next-action hero, verse of the day)
- ☐ Offline engine (queue + cursors + SQLCipher cache), connectivity, push (FCM)

Each phase builds clean before moving on, same as the iOS port.
