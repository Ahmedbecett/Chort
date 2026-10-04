# TokPulse Production ProGuard & R8 Optimization Rules

# Preserve Room Database Entities and DAOs
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class com.example.data.local.entities.** { *; }
-keep class com.example.data.local.dao.** { *; }
-keepclassmembers class com.example.data.local.entities.** { <fields>; }

# Preserve Firebase Auth, Firestore, and Storage mappings
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.google.firebase.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.storage.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Preserve Moshi & JSON serialization models
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json(name = *) <fields>;
}

# Kotlin Coroutines
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {
    kotlinx.coroutines.MainCoroutineDispatcher createDispatcher(java.util.List);
}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Android Jetpack Credential Manager & Google ID
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Retain line numbers for safe crash debugging
-keepattributes SourceFile,LineNumberTable
