import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

    defaultConfig {
        applicationId = "com.nuruplace"   // MUST match the installed app to update testers
        minSdk = 26
        targetSdk = 35
        versionCode = 10                  // > 9 (installed RN build) so it registers as an update
        versionName = "2.0.0"
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
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/v1\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://pathway.nuruplace.org/v1\"")
            signing?.let { signingConfig = signingConfigs.getByName("release") }
            isMinifyEnabled = true
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
