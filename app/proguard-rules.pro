# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.arbhlabs.taprelay.** {
    *** Companion;
}
-keepclasseswithmembers class com.arbhlabs.taprelay.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.arbhlabs.taprelay.**$$serializer { *; }

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Room generated
-keep class * extends androidx.room.RoomDatabase { <init>(); }
