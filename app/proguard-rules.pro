# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.widgeon.app.**$$serializer { *; }
-keepclassmembers class com.widgeon.app.** { *** Companion; }
-keepclasseswithmembers class com.widgeon.app.** { kotlinx.serialization.KSerializer serializer(...); }

# RemoteViews reflection (setColorStateList etc. invoked by name)
-keep class android.widget.RemoteViews { *; }

# Tink (via androidx.security:security-crypto) references compile-time-only annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
