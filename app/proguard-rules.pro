# Stat Archive WebView bridge is invoked from JavaScript, so R8 must keep
# methods annotated with @JavascriptInterface even when Java code does not
# reference them directly.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the concrete bridge class name/members stable for WebView reflection.
-keep class com.statarchive.app.MainActivity$AndroidFileBridge { *; }

# Keep activity entry points referenced from AndroidManifest.xml.
-keep class com.statarchive.app.MainActivity { *; }
-keep class com.statarchive.app.SplashActivity { *; }
