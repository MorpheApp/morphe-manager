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
}

// The app consumes the archive through this, so it is rebuilt whenever anything in it changes
configurations.create("hostJar") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(hostJar)
}
