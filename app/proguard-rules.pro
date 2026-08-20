# Keep Jsoup's JAR-based class detection (it uses reflection on classpath scanning).
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ROME (2.x) discovers its parsers/generators/converters by class NAME from the
# com/rometools/rome/rome.properties resource via Class.forName + reflection.
# R8 renames the classes but not the property strings, so without these rules
# every feed parse fails in release builds ("couldn't add feed", OPML imports
# nothing). rome-modules registers its parsers the same way.
-keep class com.rometools.** { *; }
-dontwarn com.rometools.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil
-dontwarn coil3.**
