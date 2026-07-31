# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class org.nuruplace.member.**$$serializer { *; }
-keepclassmembers class org.nuruplace.member.** { *** Companion; }
# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Tink / security-crypto references errorprone annotations not on the classpath ---
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Retrofit / OkHttp ---
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Keep our serializable DTOs (models decoded by kotlinx.serialization) ---
-keep class org.nuruplace.member.data.net.**$$serializer { *; }
-keepclassmembers class org.nuruplace.member.data.net.** { *; }

# --- WebRTC (io.github.webrtc-sdk:android) — ROOT CAUSE FIX, 2026-07-31 ---
# Real-device crash, root-caused from an adb-pulled tombstone off the
# owner's S24 (release build, sideloaded APK): SIGTRAP/TRAP_BRKPT inside
# libjingle_peerconnection_so.so's JNI_OnLoad, the FIRST time
# PeerConnectionFactory.initialize()/createPeerConnectionFactory() runs —
# i.e. the instant the host subscribes to a guest (WhepSubscriber) or a
# guest publishes (WhipPublisher). SIGTRAP/TRAP_BRKPT here is WebRTC's own
# native RTC_CHECK/abort. This AAR (verified: unzipped the resolved
# io.github.webrtc-sdk:android:144.7559.09 .aar — no proguard.txt or
# consumer-rules.pro anywhere inside it, so R8 gets ZERO automatic
# protection for this library) ships a native library whose JNI_OnLoad
# resolves Java members BY NAME by reflection (RegisterNatives /
# @CalledByNative) — with app/build.gradle.kts's release buildType running
# isMinifyEnabled=true + isShrinkResources=true and this file previously
# having NO org.webrtc rule at all, R8 was free to rename/strip exactly the
# members libjingle's native init depends on, so registration failed and the
# native library aborted the whole process. This is why every debug build,
# compile, and unit test passed clean — R8 only runs on the release build
# type, which is what the owner's device actually runs.
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keepattributes *Annotation*
-dontwarn org.webrtc.**

# --- RootEncoder (com.github.pedroSG94.RootEncoder) — audited as part of the
# same incident (owner doctrine: "one failure = a class, audit for sibling
# occurrences"). Verified: unzipped the resolved library/common/rtmp 2.5.9
# .aars — NONE contain a .jni/ dir or any .so file (RootEncoder is pure
# Kotlin/Java over Android's own MediaCodec, no custom native library), so
# it carries NONE of WebRTC's JNI-registration-by-reflection risk. Kept
# anyway, narrowly, as cheap insurance against the RTMP/WHIP url-parsing
# reflection GenericStream's constructor overload resolution depends on
# (LiveBroadcastEngine.kt's buildPublishUrl/buildBroadcaster) being altered
# by aggressive shrinking — NOT because a crash was observed or proven here.
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**

# --- Sibling-risk audit (same incident, same doctrine: "one failure = a
# class, audit for sibling occurrences"). Every dependency in this app that
# ships a native .so was checked for a bundled proguard.txt/consumer-rules.pro
# (`unzip -l` on the resolved .aar): ML Kit barcode-scanning 17.3.0,
# CameraX camera-core/camera-camera2 1.4.2, and androidx.graphics:graphics-path
# 1.0.1 ALL ship their own proguard.txt (auto-applied, already protected —
# not the gap). androidx.datastore:datastore-core-android 1.1.4 (native lib
# libdatastore_shared_counter.so, backing androidx.datastore.core.
# NativeSharedCounter/MultiProcessCoordinator) does NOT ship one — same gap
# class as webrtc-sdk, but LATENT here: grepped the whole app for
# "MultiProcess"/"multiprocess" and found zero usage, so this app never
# actually constructs a multi-process DataStore and this native path is
# dead code today. Kept anyway as free insurance against a future
# multi-process DataStore add silently inheriting the same crash.
-keep class androidx.datastore.core.NativeSharedCounter { *; }
