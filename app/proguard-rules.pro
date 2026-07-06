# Keep DreamService and its subclasses
-keep class * extends android.service.dreams.DreamService { *; }
-keep class com.tvtoolbox.screensaver.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-dontwarn coil.**
