pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Nuru Live (L3) — RootEncoder (RTMP publish) ships via JitPack only,
        // never landed on Maven Central. Project-level repo declarations are
        // disallowed by repositoriesMode above, so it MUST be declared here.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NuruMember"
include(":app")
