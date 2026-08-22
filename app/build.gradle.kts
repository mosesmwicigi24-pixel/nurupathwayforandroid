import java.io.File
import java.util.Properties
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Firebase (add-alongside): active — app/google-services.json present for
    // com.nuruplace in project pathway-63ca4 (777897756817). See FIREBASE_SETUP.md.
    alias(libs.plugins.google.services)
    // Crashlytics (+ NDK) — turns a native libjingle/WebRTC crash (tonight's
    // incident: stripped org.jni_zero.JniInit, invisible to a JVM-only
    // reporter) into a symbolicated report instead of raw hex offsets. See
    // the release buildType below for nativeSymbolUploadEnabled + debugSymbolLevel.
    alias(libs.plugins.firebase.crashlytics)
}

// Release signing — REUSES the existing "CN=Nuru Place" upload key so this app
// installs as an UPDATE over the app testers already have (com.nuruplace). The
// passwords live in the RN app's git-ignored keystore.properties; gradle reads
// them at build time — they are never copied into this repo. Provide your own
// keystore.properties at this repo's root to override (also git-ignored).
fun releaseSigning(): Pair<Properties, File>? {
    val candidates = listOf(
        rootProject.file("keystore.properties"),
        rootProject.file("../pathway/packages/mobile/android/keystore.properties"),
    )
    val propsFile = candidates.firstOrNull { it.exists() } ?: return null
    val props = Properties().apply { propsFile.inputStream().use { load(it) } }
    val storeName = props.getProperty("storeFile") ?: return null
    // storeFile is relative to the RN app module (…/android/app).
    val ks = File(File(propsFile.parentFile, "app"), storeName)
        .takeIf { it.exists() } ?: File(propsFile.parentFile, storeName)
    return if (ks.exists()) props to ks else null
}
val signing = releaseSigning()

android {
    namespace = "org.nuruplace.member"
    // Google Play target-API policy (support.google.com/googleplay/android-developer/answer/11926878):
    // new apps/updates must target Android 16 (API 36) by 31 Aug 2026. Android 17
    // (API 37, released 16 Jun 2026) exists but Play doesn't require targeting it
    // until Aug 2027 — 36 is the correct, minimal-risk choice for this deadline.
    compileSdk = 36

    // JVM unit tests: return defaults for android.* stubs (e.g. android.util.Log)
    // instead of throwing "not mocked".
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "com.nuruplace"   // MUST match the installed app to update testers
        minSdk = 26
        targetSdk = 36
        versionCode = 67                  // bump every release so devices take it as an update
        versionName = "2.42.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        signing?.let { (props, ks) ->
            create("release") {
                storeFile = ks
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Point debug at prod for on-emulator design verification (fast, no R8).
            // Flip back to http://10.0.2.2:8080/v1 for local-backend development.
            buildConfigField("String", "API_BASE_URL", "\"https://pathway.nuruplace.org/v1\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://pathway.nuruplace.org/v1\"")
            signing?.let { signingConfig = signingConfigs.getByName("release") }
            isMinifyEnabled = true
            // Resource shrinking (safe mode — keeps resources referenced by name)
            // strips unused resources on top of R8's code shrink → smaller app +
            // a real Play "shrinking" score. Requires minify, which is on.
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // FULL debug symbols on the release .so's — without this AGP strips
            // native symbols before Crashlytics can upload them, and a native
            // crash (tonight's libjingle/jni_zero incident) is back to
            // unreadable hex offsets even with the SDK present.
            ndk { debugSymbolLevel = "FULL" }
            // Uploads those FULL symbols to Firebase so native (NDK/libjingle)
            // crashes symbolicate in the Crashlytics dashboard instead of
            // showing raw addresses. Off by default (build-speed tradeoff) —
            // this is the setting that actually closes tonight's gap.
            configure<CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    // Broadcast fingerprint unlock (§5.3 step-up) — BiometricPrompt + a
    // Keystore key gated on the current biometric enrollment.
    implementation(libs.androidx.biometric)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    // Nuru Live (L3) — RootEncoder RTMP publisher (broadcaster side). Ships
    // only via JitPack (see settings.gradle.kts for the repo declaration).
    implementation(libs.rootencoder)
    // Nuru Live L6b — guest WHIP publish + host WHEP subscribe (real-time
    // guest video, docs/LIVE_INTERACTIVE.md). LiveKit-maintained Google
    // WebRTC prebuilt on Maven Central; verified 16 KB page-size aligned at
    // this pinned version (see libs.versions.toml comment + PARITY_AUDIT.md).
    implementation(libs.webrtc.sdk)
    // 16 KB page-size compliance (Play requirement for targetSdk 35): the old
    // transitive graphics-path 1.0.0 ships a 4 KB-aligned .so — pin the fixed one.
    implementation("androidx.graphics:graphics-path:1.0.1")
    implementation(libs.play.location)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // Radio "remind me when we're live" (item 4): a one-time WorkManager job
    // fired at the next program's scheduledAt — avoids the exact-alarm
    // permission AlarmManager would need.
    implementation(libs.androidx.work.runtime.ktx)
    // Home-screen widgets (Pathway + Radio) — Jetpack Glance. glance-appwidget
    // pulls the glance core transitively; glance-material3 is only for the
    // ColorProviders(light, dark) brand-theme builder (WidgetBrand.kt).
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Firebase (add-alongside): FCM push + Email/Password auth. Postgres stays the
    // source of truth — Firestore is intentionally NOT used (see FIREBASE_SETUP.md).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    // Crashlytics: JVM crash/ANR reporting (self-initializes via ContentProvider,
    // same pattern as FCM — no app code changes needed for basic capture).
    implementation(libs.firebase.crashlytics)
    // Crashlytics NDK: captures native (Mach/Linux-signal-level) crashes —
    // e.g. tonight's libjingle/WebRTC org.jni_zero.JniInit SIGTRAP, which a
    // JVM-only crash reporter never sees at all. Still a distinct BOM-managed
    // artifact at firebase-bom 33.7.0 (no -ktx variant exists for it).
    implementation(libs.firebase.crashlytics.ndk)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
