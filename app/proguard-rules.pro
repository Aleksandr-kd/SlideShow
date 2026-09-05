# Add project specific ProGuard rules here.

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore / Proto
-dontwarn androidx.datastore.**

# Kotlinx serialization / coroutines (reflection)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn kotlinx.coroutines.**

# Keep enum entries used by name (ThemeMode, PlayOrder, TransitionMode)
-keepclassmembers enum com.example.slideshow.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# RuStore SDK
-keep class ru.rustore.sdk.** { *; }
-dontwarn ru.rustore.sdk.**
