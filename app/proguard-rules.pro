# Minification is disabled for release builds; rules kept for future use.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Retrofit + OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# kotlinx.serialization
-keepclassmembers class com.arcsus.arctv.** {
    *** Companion;
}
-keepclasseswithmembers class com.arcsus.arctv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
