# Firebase — add-alongside (FCM push + Email/Password auth)

Firebase is added **alongside** the existing custom backend (`pathway.nuruplace.org`),
not as a replacement. **Postgres stays the source of truth — Firestore is intentionally
NOT used** (it would duplicate Postgres + our own offline engine). Firebase is used for
exactly two things:

- **FCM push** — the real win (free on the Spark plan, forever).
- **Email/Password auth** — optional add-alongside sign-in (does not gate the app).

Project: **`pathway-63ca4`** (`777897756817`) · Android package: **`com.nuruplace`**

## What's wired (this branch, `feat/firebase-integration`)
- `firebase-bom` + `firebase-auth-ktx` + `firebase-messaging-ktx`; `google-services`
  plugin **applied** (`app/google-services.json` present, git-ignored).
- `data/firebase/FirebaseAuthService.kt` — Email/Password `signIn`/`register`/`signOut`,
  surfaced at **Settings → Firebase account**.
- `data/firebase/NuruMessagingService.kt` — FCM: `onNewToken` → `POST /me/devices`
  (the backend already accepts `{platform, app_version, model, push_token}`);
  `onMessageReceived` → local notification on channel `nuru_default`.
- `data/firebase/PushRegistration.kt` — on entering the authed shell, asks for
  `POST_NOTIFICATIONS` (Android 13+), fetches the FCM token, registers the device.
- Manifest: `POST_NOTIFICATIONS`, the messaging `<service>`, default channel meta-data.

## Console toggles (yours)
1. **Authentication → Sign-in method → Email/Password → Enable.** *(done ✓)*
2. **FCM needs no toggle** — Cloud Messaging is on by default. To *send* from the backend,
   generate an **Admin SDK service-account key** (Project Settings → Service accounts →
   Generate new private key) and keep it **server-side only** (never in the app/repo).

## Backend dispatcher (separate, server-side task)
The client registers tokens at `POST /me/devices`. To actually deliver pushes, the
backend sends via the **FCM HTTP v1 API** using the Admin SDK service-account key.
That's a backend change, tracked separately.

## Cost
On **Spark**: FCM push is **free and unlimited**; Email/Password auth is free at this
scale; there is **no billing** (no card, no overage — services just cap at the free
tier). FCM never requires Blaze.
