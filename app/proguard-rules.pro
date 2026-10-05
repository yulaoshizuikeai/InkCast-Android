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

# AndroidX Lifecycle & ViewModels (Prevent R8 from stripping reflection constructors used by ViewModelProvider)
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class com.inkcast.android.ui.MainViewModel {
    public <init>(...);
}

# Domain & Data Models (Preserve JSON serialization & fields)
-keep class com.inkcast.android.data.model.** { *; }

# Compose Runtime
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
