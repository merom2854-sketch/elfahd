-optimizationpasses 5
-dontusemixedcaseclassnames
-repackageclasses 'com.alfahdtv.secure'
-allowaccessmodification
-keepattributes *Annotation*

# Keep only the methods exposed deliberately to the WebView bridge.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.alfahdtv.app.MainActivity$NativePlayerBridge { *; }
-keep class com.alfahdtv.app.MainActivity$NativeNavigationBridge { *; }
