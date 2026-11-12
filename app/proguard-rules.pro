# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ============================================================
# GENERAL ANDROID RULES
# ============================================================

# Keep line number information for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================
# NOVALAUNCHER CORE — keep all launcher classes
# ============================================================

-keep class com.novalauncher.launcher.** { *; }
-keepclassmembers class com.novalauncher.launcher.** { *; }

# Keep the Application class
-keep class com.novalauncher.launcher.NovaLauncherApp { *; }

# Keep Activities
-keep class com.novalauncher.launcher.LauncherActivity { *; }

# Keep Services
-keep class com.novalauncher.launcher.SystemUIOverlayService { *; }
-keep class com.novalauncher.launcher.NovaNotificationListenerService { *; }

# Keep BroadcastReceivers
-keep class com.novalauncher.launcher.BootReceiver { *; }

# Keep data models (AppInfo etc.)
-keep class com.novalauncher.launcher.AppInfo { *; }
-keepclassmembers class com.novalauncher.launcher.AppInfo { *; }

# Keep PhysicsEngine public API
-keepclassmembers class com.novalauncher.launcher.PhysicsEngine {
    public void start();
    public void stop();
    public void applyForce(...);
    public void setGravity(...);
    public boolean isRunning();
}

# ============================================================
# KOTLIN
# ============================================================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers class kotlin.Lazy { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlin serialization (if used in future)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ============================================================
# ANDROIDX
# ============================================================

# AndroidX Core
-keep class androidx.core.** { *; }
-dontwarn androidx.core.**

# RecyclerView
-keep class androidx.recyclerview.** { *; }

# ViewPager2
-keep class androidx.viewpager2.** { *; }

# AppWidget
-keep class android.appwidget.** { *; }

# ConstraintLayout
-keep class androidx.constraintlayout.** { *; }

# ============================================================
# COIL IMAGE LOADING
# ============================================================

-keep class coil.** { *; }
-dontwarn coil.**

# OkHttp (Coil dependency)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================================
# ANDROID FRAMEWORK — KEEP PARCELABLES & SERIALIZABLES
# ============================================================

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep View constructors for XML inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ============================================================
# SUPPRESS NOISY WARNINGS
# ============================================================

-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

