# Add project specific ProGuard rules here.
-keep class com.express.video.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
