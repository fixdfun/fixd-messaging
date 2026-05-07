# Fixd Messaging - ProGuard rules
-keep class app.fixd.messaging.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class app.fixd.messaging.**$$serializer { *; }
-keepclassmembers class app.fixd.messaging.** {
    *** Companion;
}
-keepclasseswithmembers class app.fixd.messaging.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Telephony / SMS / MMS - keep PDU classes (used reflectively by AOSP MMS code paths)
-keep class com.google.android.mms.** { *; }
-dontwarn com.google.android.mms.**

# AndroidX
-keep class androidx.lifecycle.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.**

# OkHttp / kotlinx.serialization runtime hints
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Compose runtime needs reflection-free behavior; just suppress noisy warnings
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.reflect.**

# Keep Parcelable CREATOR fields (Compose sometimes parcels state)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep our keyboard service entry-point and any subclasses
-keep class app.fixd.messaging.keyboard.** { *; }
-keep class app.fixd.messaging.spell.** { *; }

# R8 full mode safety: enums used in saved state
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
