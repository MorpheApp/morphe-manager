# What the embedded JVM runs keeps the API patches are written against whole, as the app's own
# shrinking does (app/proguard-rules.pro). Only libraries nothing of it reaches are cut down.
-dontobfuscate
-dontoptimize
-keepattributes *

-keep class app.morphe.manager.** { *; }
-keep class app.morphe.patcher.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class kotlin.** { *; }
-keepnames class com.android.apksig.internal.** { *; }
-keepnames class org.xmlpull.** { *; }

# apksig builds its ASN.1 models reflectively, see app/proguard-rules.pro
-keepclassmembers class com.android.apksig.internal.** {
    <init>();
    <fields>;
}

# Named in META-INF/services, which loads them by name
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { <init>(); }
-keep class org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider { <init>(); }

# Parts of the libraries that are never reached refer to what the jar leaves out: the ANTLR tool
# (see the excludes of hostJar), annotations of build tools, and Android of the patcher's ART path
-dontwarn org.antlr.tool.**
-dontwarn org.antlr.codegen.**
-dontwarn com.google.auto.**
-dontwarn dalvik.system.**
-dontwarn android.annotation.**
# Their methods are signature polymorphic, so a call names a signature they do not declare
-dontwarn java.lang.invoke.VarHandle
-dontwarn java.lang.invoke.MethodHandle
# Notes only report what the keeps above decide on purpose. The pattern is needed, as a bare
# -dontnote next to the libraries' own, which name their classes, silences nothing
-dontnote **
