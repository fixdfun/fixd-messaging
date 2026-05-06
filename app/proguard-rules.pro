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
