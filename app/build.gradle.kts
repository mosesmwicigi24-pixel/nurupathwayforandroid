import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Firebase (add-alongside): active — app/google-services.json present for
    // com.nuruplace in project pathway-63ca4 (777897756817). See FIREBASE_SETUP.md.
    alias(libs.plugins.google.services)
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
    compileSdk = 35

    // JVM unit tests: return defaults for android.* stubs (e.g. android.util.Log)
    // instead of throwing "not mocked".
    testOptions { unitTests.isReturnDefaultValues = true }

    defaultConfig {
        applicationId = "com.nuruplace"   // MUST match the installed app to update testers
        minSdk = 26
        targetSdk = 35
        versionCode = 52                  // bump every release so devices take it as an update
        versionName = "2.27.0"
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
    // Firebase (add-alongside): FCM push + Email/Password auth. Postgres stays the
    // source of truth — Firestore is intentionally NOT used (see FIREBASE_SETUP.md).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
