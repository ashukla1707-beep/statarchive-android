# Stat Archive WebView bridge is invoked from JavaScript, so R8 must keep
# methods annotated with @JavascriptInterface even when Java code does not
# reference them directly.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the concrete bridge class stable for WebView reflection. Activity
# entry points are retained by the Android Gradle/manifest-generated rules;
# blanket Activity keeps would unnecessarily block R8 optimization.
-keep class com.statarchive.app.MainActivity$AndroidFileBridge { *; }
