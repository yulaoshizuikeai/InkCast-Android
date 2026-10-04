# Proguard rules for InkCast-Android

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# AndroidX Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-dontwarn kotlinx.coroutines.**
