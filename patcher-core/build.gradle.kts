import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What patching needs and nothing of Android, so the same code runs inside the app and in the
// JVM the app embeds for patching
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.morphe.patcher)
    implementation(libs.arsclib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit)
}

// The full Kotlin reflection, which neither the patcher nor patches use, is the largest library
// the host would ship. Without it, Kotlin falls back to the reflection of its standard library.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

val proguard: Configuration = configurations.create("proguard") {
    isCanBeConsumed = false
}

dependencies {
    proguard(libs.proguard)
}

// Everything the embedded JVM runs, in one archive the app ships beside the runtime
val hostJar = tasks.register<Jar>("hostJar") {
    group = "build"
    description = "Packs the patcher host and everything it runs on into one archive."
    archiveClassifier = "host"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.map { it.output })
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { if (it.isDirectory) it else zipTree(it) } })
    // Signatures of the merged libraries no longer match, and the JVM would reject the archive over them
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    // The archive is not marked multi-release, so the JVM never reads the classes kept for other releases
    exclude("META-INF/versions/**")
    // What the app's APK leaves out of the same libraries, see its packaging excludes
    exclude(
        "META-INF/*.version",
        "DebugProbesKt.bin",
        "smali.properties",
        "baksmali.properties",
        "org/bouncycastle/pqc/**/*.properties",
        "org/bouncycastle/x509/**/*.properties",
        "org/antlr/codegen/**",
        "org/antlr/tool/**",
        "com/android/tools/smali/smali/*.tokens"
    )
    // The Android frameworks of ARSCLib, which the app's APK carries already. JvmInstallation adds
    // them back from there as it unpacks the host. Left out before shrinking, too, as ProGuard
    // would repack them as archives of their own.
    exclude("frameworks/**")
}

// The libraries of the host take up most of it, and patching reaches only some of them
val shrunkHostJar = tasks.register<ShrinkJarTask>("shrinkHostJar") {
    group = "build"
    description = "Shrinks the patcher host to what it and the patches it runs can reach."
    inputJar.set(hostJar.flatMap { it.archiveFile })
    rules.from("host-rules.pro")
    // The rules are read before shrinking, and the build information only matters to Maven
    excludes.addAll("META-INF/proguard/**", "META-INF/com.android.tools/**", "META-INF/maven/**")
    // The JDK the embedded JVM is built from, see embeddedJvms of the app
    jdk.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    proguardClasspath.from(proguard)
    outputJar.set(layout.buildDirectory.file("libs/${project.name}-host-shrunk.jar"))
}

// The app consumes the archive through this, so it is rebuilt whenever anything in it changes
configurations.create("hostJar") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(shrunkHostJar.flatMap { it.outputJar })
}
