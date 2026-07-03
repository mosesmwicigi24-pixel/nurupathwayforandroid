# Firebase — add-alongside setup (Auth Email/Password + Firestore)

Firebase is added **alongside** the existing custom backend (`pathway.nuruplace.org`),
not as a replacement. The existing JWT auth, offline sync engine and §1.9 gating are
untouched. This branch (`feat/firebase-integration`) wires the SDK + a guarded
`FirebaseAuthService` (Email/Password) + `FirestoreRepo`, all **dormant** until the
config file is present — so the build stays green with no Firebase project attached.

Target Firebase project: **`777897756817`** · Android package: **`com.nuruplace`**

## What's already done (this branch)
- `firebase-bom`, `firebase-auth-ktx`, `firebase-firestore-ktx` deps added (compile-only until activated).
- `google-services` plugin declared (root) and version-cataloged, but **not applied** in `app/build.gradle.kts` (commented) — applying it without `google-services.json` fails the build.
- `data/firebase/FirebaseAuthService.kt` — Email/Password `signIn` / `register` / `signOut` / `currentUser`, guarded by `isConfigured()`.
- `data/firebase/FirestoreRepo.kt` — generic `set` / `get` / `listMine`, user-scoped, guarded.

## Activation (2 steps — needs your Firebase access)

### 1. Register the Android app + get `google-services.json`
Firebase console → project `777897756817` → **Add app → Android**:
- **Package name:** `com.nuruplace`
- (SHA-1 optional — not needed for Email/Password or Firestore)
- Download **`google-services.json`** → place at **`app/google-services.json`** (git-ignored; treat as a secret).

Then in the console: **Authentication → Sign-in method → enable Email/Password**, and
**Firestore Database → Create database** (start in production mode; add security rules).

### 2. Apply the plugin
In `app/build.gradle.kts`, uncomment:
```kotlin
alias(libs.plugins.google.services)
```

That's it — `FirebaseApp` auto-initialises from `google-services.json`, and
`FirebaseAuthService.isConfigured()` flips to true. Tell me and I'll wire the sign-in
UI (a "Continue with email (Firebase)" path on the Login screen), model the concrete
Firestore collections you want, add security rules, and build + verify green.

## Notes
- Suggested Firestore security rule baseline (each member owns their docs):
  ```
  match /{collection}/{doc} {
    allow read, write: if request.auth != null
      && request.resource.data.owner_uid == request.auth.uid;
  }
  ```
- FCM push (separate track) also lives in this project once `google-services.json` is in —
  the backend already accepts a device token via `POST /me/devices`.
