# DriveDelta R8/ProGuard rules (Checkpoint 10).
# NOTE: release `isMinifyEnabled` is still false for the MVP internal track; these rules are staged
# so that flipping minify on later is low-risk. Verify a minified release build before shipping.

# --- Kotlin / reflection metadata ---
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, EnclosingMethod
-keepattributes SourceFile, LineNumberTable

# --- kotlinx.serialization ---
# Keep @Serializable classes' synthetic serializer(); the plugin generates these.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.drivedelta.**$$serializer { *; }
-keepclassmembers class app.drivedelta.** {
    *** Companion;
}
# DTOs are (de)serialized reflectively.
-keep class app.drivedelta.data.remote.roads.Roads* { *; }

# --- Retrofit / OkHttp ---
-keepattributes RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface app.drivedelta.data.remote.roads.RoadsApiService
-keepclasseswithmembers interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# --- Hilt / Dagger (mostly covered by consumer rules; keep generated components) ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# --- Firebase / Firestore ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# DriveDelta uses explicit toMap()/fromMap for Firestore, so no reflective model classes to keep.

# --- Room ---
-keep class app.drivedelta.data.local.entity.** { *; }
-dontwarn androidx.room.paging.**

# --- Vico (currently unused — custom Canvas chart; kept defensive) ---
-dontwarn com.patrykandpatrick.vico.**

# --- Domain models referenced by name when building Firestore maps ---
-keep class app.drivedelta.domain.model.** { *; }
