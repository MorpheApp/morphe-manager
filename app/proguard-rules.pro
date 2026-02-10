-dontobfuscate

-keep class app.morphe.manager.patcher.runtime.process.* { *; }
-keep class app.morphe.manager.plugin.** { *; }
-keep class app.morphe.patcher.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class kotlin.** { *; }
-keepnames class com.android.apksig.internal.** { *; }
-keepnames class org.xmlpull.** { *; }

-dontwarn android.content.res.**
-dontwarn com.google.j2objc.annotations.*
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.slf4j.**