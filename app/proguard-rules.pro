# Onyx Keyboard — ProGuard / R8 rules

# Keep JNI bridge (native symbols are resolved by name)
-keep class com.onyx.keyboard.nativebridge.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Room entities use reflection-free generated code, but keep model names for debugging
-keepnames class com.onyx.keyboard.data.db.** { *; }
