# Keep Jsoup's JAR-based class detection (it uses reflection on classpath scanning).
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil
-dontwarn coil3.**
