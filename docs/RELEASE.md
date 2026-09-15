# Shipping the Android app to Google Play

Three steps, no laptop required once the secrets are in place.

1. **Bump the number.** In `app/build.gradle.kts` raise `versionCode` (every
   Play upload needs a higher one than the last; production is 71 as of
   September 2026) and set `versionName`. Merge it to `main`.
2. **Build it signed.** GitHub → Actions → **Android release** → Run workflow →
   `main`. About ten minutes. The run summary shows the versionCode it read
   back from the built APK and the signing certificate.
3. **Upload it.** Download the artifact (`NuruPathway-<versionName>-vc<versionCode>`),
   then Play Console → Production → Create new release → drop in the `.aab`.
   The `.apk` next to it is for sideloading a tester's phone.

## One-time: the secrets the workflow signs with

Settings → Secrets and variables → Actions → New repository secret. Values
come from the Mac that holds the upload key; nothing is typed anywhere else.

| Secret | Value |
|---|---|
| `ANDROID_UPLOAD_KEYSTORE_B64` | `base64 -i nuru-release.keystore \| pbcopy`, then paste |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `ANDROID_KEY_ALIAS` | `keyAlias` from `keystore.properties` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
| `GOOGLE_SERVICES_JSON` | optional: contents of the real `app/google-services.json`, so Crashlytics can de-obfuscate release crashes |

Losing the upload keystore means Play must reset the key for the app. Keep the
original backed up off the Mac as well.

## The same build on the Mac

With `keystore.properties` beside the pathway checkout (where
`app/build.gradle.kts` looks for it) and the real `app/google-services.json`:

```bash
./gradlew bundleRelease    # -> app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease  # -> app/build/outputs/apk/release/app-release.apk
```
