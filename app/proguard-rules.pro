# LyricStage release shrinking.
# Libraries (Compose, Room, OkHttp, kotlinx.serialization, coroutines) ship their own rules.
# The app's own classes are small, so keep them whole: no surprises from reflection,
# Room-generated code or serializers, while unused library code is still removed.
-keep class com.lyricstage.** { *; }

# kotlinx.serialization: keep generated serializers and companion lookups
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp optional platform integrations that are not on Android
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
