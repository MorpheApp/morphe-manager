import kotlin.random.Random
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.devtools)
    alias(libs.plugins.about.libraries)
    signing
}

val outputApkFileName = "${rootProject.name}-$version.apk"

val arscLib by configurations.creating

val strippedArscLib by tasks.registering(Jar::class) {
    archiveFileName.set("ARSCLib-android.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    doFirst {
        from(arscLib.resolve().map { zipTree(it) })
    }
    exclude("android/**", "org/xmlpull/**")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.ktx)
    implementation(libs.runtime.ktx)
    implementation(libs.runtime.compose)
    implementation(libs.splash.screen)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.preferences.datastore)
    implementation(libs.appcompat)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.livedata)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    // Accompanist
    implementation(libs.accompanist.drawablepainter)

    // Placeholder
    implementation(libs.placeholder.material3)

    // Coil (async image loading, network image)
    implementation(libs.coil.compose)
    implementation(libs.coil.appiconloader)

    // KotlinX
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collection.immutable)
    implementation(libs.kotlinx.datetime)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    // Morphe
    implementation(libs.morphe.patcher)
    implementation(libs.morphe.library)

    arscLib("io.github.reandroid:ARSCLib:1.3.8")
    implementation(files(strippedArscLib))
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Downloader plugins
    implementation(project(":api"))

    // Native processes
    implementation(libs.kotlin.process)

    // HiddenAPI
    compileOnly(libs.hidden.api.stub)
    implementation(libs.hidden.api.bypass)

    // Shizuku / Sui
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // LibSU
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.libsu.nio)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.navigation)
    implementation(libs.koin.workmanager)

    // Licenses
    implementation(libs.about.libraries)

    // Ktor
    implementation(libs.ktor.core)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    // Markdown
    implementation(libs.markdown.renderer)

    // Fading Edges
    implementation(libs.fading.edges)

    // Scrollbars
    implementation(libs.scrollbars)

    // EnumUtil
    implementation(libs.enumutil)
    ksp(libs.enumutil.ksp)

    // Reorderable lists
    implementation(libs.reorderable)

    // Compose Icons
    implementation(libs.compose.icons.fontawesome)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Semantic versioning string parser
        classpath(libs.semver.parser)
    }
}

android {
    namespace = "app.morphe.manager"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "app.morphe.manager"

        minSdk = 26
        targetSdk = 35

        val versionStr = if (version == "unspecified") "1.0.0" else version.toString()
        versionName = versionStr

        // VersionCode derived from current time (1-minute intervals) + offset.
        val nowMillis = System.currentTimeMillis()
        val timestampVersionCode = (nowMillis / (60 * 1000)).toInt()
        // Offset of the prior v1.1.1 version code to ensure the code is always newer for old installs.
        // If a new app is used this offset should be changed to zero.
        // 1 minute rounding and this offset still gives ~4,000 years of valid version codes
        // and still fall into Play store max version code range.
        val versionCodeOffset = 10010100
        versionCode = timestampVersionCode + versionCodeOffset

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
            buildConfigField("long", "BUILD_ID", "${Random.nextLong()}L")
        }

        release {
            if (!project.hasProperty("noProguard")) {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }

            val keystoreFile = file("keystore.jks")

            signingConfig = if (project.hasProperty("signAsDebug") || !keystoreFile.exists()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.create("release") {
                    storeFile = keystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEYSTORE_ENTRY_ALIAS")
                    keyPassword = System.getenv("KEYSTORE_ENTRY_PASSWORD")
                }
            }

            buildConfigField("long", "BUILD_ID", "0L")
        }
    }

    applicationVariants.all {
        outputs.all {
            this as com.android.build.gradle.internal.api.ApkVariantOutputImpl

            outputFileName = outputApkFileName
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                "/prebuilt/**",
                "META-INF/DEPENDENCIES",
                "META-INF/**.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "org/bouncycastle/pqc/**.properties",
                "org/bouncycastle/x509/**.properties",
            )
        )
        jniLibs {
            useLegacyPackaging = true
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    android {
        androidResources {
            generateLocaleConfig = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        disable += setOf("MissingTranslation")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    whenTaskAdded {
        if (name.startsWith("lintVital")) {
            enabled = false
        }
    }

    // Needed by gradle-semantic-release-plugin.
    // Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435.
    val publish by registering {
        group = "publishing"
        description = "Build the release APK"

        dependsOn("assembleRelease")

        val apk = project.layout.buildDirectory.file("outputs/apk/release/${outputApkFileName}")
        val ascFile = apk.map { it.asFile.resolveSibling("${it.asFile.name}.asc") }

        inputs.file(apk).withPropertyName("inputApk")
        outputs.file(ascFile).withPropertyName("outputAsc")

        doLast {
            signing {
                useGpgCmd()
                sign(apk.get().asFile)
            }
        }
    }
}
