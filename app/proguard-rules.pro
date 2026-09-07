# JustBrowse ProGuard rules
-keep class com.justbrowse.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# WebView JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
