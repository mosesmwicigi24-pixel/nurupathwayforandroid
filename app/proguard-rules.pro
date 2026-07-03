# kotlinx.serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class org.nuruplace.member.**$$serializer { *; }
-keepclassmembers class org.nuruplace.member.** { *** Companion; }
# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
