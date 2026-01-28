# Add project specific ProGuard rules here.

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Compose
-keep class androidx.compose.** { *; }

# Keep accessibility service
-keep class com.lawanpmo.autoblocklist.service.UrlBlockerAccessibilityService { *; }
