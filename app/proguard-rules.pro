# ==============================================================================
# PROGUARD / R8 OPTIMIZATION & OBFUSCATION RULES - BRAGA SAÚDE
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. KOTLINX SERIALIZATION
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Mantém classes anotadas com @Serializable e seus Companion Serializers gerados
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}

# ------------------------------------------------------------------------------
# 2. ROOM DATABASE
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomOpenHelper
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. DAGGER HILT & INJECT
# ------------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.hilt.work.HiltWorker { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
    @javax.inject.Inject <init>(...);
}

# ------------------------------------------------------------------------------
# 4. JETPACK COMPOSE & MATERIAL 3
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# ------------------------------------------------------------------------------
# 5. FIREBASE (AUTH, MESSAGING, STORAGE)
# ------------------------------------------------------------------------------
-keepattributes EnclosingMethod
-keep class com.google.firebase.** { *; }
-keep class br.com.bragasaude.data.remote.model.** { *; }
-keep class br.com.bragasaude.data.local.** { *; }
-dontwarn com.google.firebase.**

# ------------------------------------------------------------------------------
# 6. GOOGLE HEALTH CONNECT & PLAY SERVICES (GPS / WEARABLES)
# ------------------------------------------------------------------------------
-keep class androidx.health.connect.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn androidx.health.connect.**

# ------------------------------------------------------------------------------
# 7. PDFBOX ANDROID (extração de texto de PDF — Apache 2.0)
# ------------------------------------------------------------------------------
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ------------------------------------------------------------------------------
# 8. COIL (IMAGE LOADING)
# ------------------------------------------------------------------------------
-keep class coil3.** { *; }
-dontwarn coil3.**

# ------------------------------------------------------------------------------
# 9. GENERAL ANDROID RUNTIME & LOGGING
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------------------------
# 10. STRIP LOGS IN RELEASE BUILDS (LGPD / PHI SANITIZATION) — A8
# ------------------------------------------------------------------------------
# Remove todas as chamadas android.util.Log em builds de release para evitar
# vazamento de metadados clínicos (PII/PHI) no logcat de produção.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ------------------------------------------------------------------------------
# 11. GRPC / JNDI DONTWARN (Firebase Auth)
# ------------------------------------------------------------------------------
-dontwarn javax.naming.**


