-dontobfuscate

-keep class app.morphe.** { *; }
-keep class app.morphe.patcher.** { *; }
-keep class app.revanced.manager.patcher.runtime.process.* { *; }
-keep class app.revanced.manager.plugin.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class kotlin.** { *; }
-keepnames class com.android.apksig.internal.** { *; }
-keepnames class org.xmlpull.** { *; }

-dontwarn android.content.res.**
-dontwarn com.google.j2objc.annotations.*
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.slf4j.**