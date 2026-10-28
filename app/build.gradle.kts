import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.random.Random

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.devtools)
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.about.libraries.android)
    alias(libs.plugins.google.services)
    signing
}

/** The patcher host for the embedded JVM, see [embeddedJvmAssets]. */
val patcherHost: Configuration = configurations.create("patcherHost") {
    isCanBeConsumed = false
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
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    // Haze (blur of what scrolls under floating dialog chrome)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Coil (async image loading, network image)
    implementation(libs.coil.compose)
    implementation(libs.coil.appiconloader)
    implementation(libs.appiconloader.iconloaderlib)

    // KotlinX
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collection.immutable)
    implementation(libs.kotlinx.datetime)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Morphe
    implementation(project(":patcher-core"))
    implementation(libs.morphe.patcher)
    patcherHost(project(":patcher-core", "hostJar"))
    // Unpacks the embedded JVM, which ships compressed with xz
    implementation(libs.xz)

    implementation(libs.androidx.documentfile)

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

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.workmanager)

    // Licenses
    implementation(libs.about.libraries.core)
    implementation(libs.about.libraries.m3)

    // Ktor
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    // Firebase Cloud Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.base)

    // Reorderable lists
    implementation(libs.reorderable)

    // Icons
    implementation(libs.icons.material.symbols.rounded)
    implementation(libs.icons.material.symbols.rounded.filled)
    implementation(libs.icons.font.awesome.brands)

    // Semantic versioning parser
    implementation(libs.semver.parser)

    // Unit tests
    testImplementation(libs.kotlin.test.junit)
}

// The full Kotlin reflection comes with the patcher, yet neither it, the patches nor the app go
// beyond the reflection of the standard library, as the patcher host already runs without it
configurations.configureEach {
    if (name.endsWith("RuntimeClasspath")) exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

/**
 * Languages Morphe is translated into as language and region pairs, read from the resource folders
 * Crowdin writes, so a new language needs no change anywhere else.
 */
val translations = project.file("src/main/res").listFiles().orEmpty()
    .mapNotNull { Regex("values-([a-z]{2,3})(?:-r([A-Z]{2}))?").matchEntire(it.name) }
    .map { it.groupValues[1] to it.groupValues[2] }
    .sortedWith(compareBy({ it.first }, { it.second }))

/**
 * Locales kept from library resources. Each comes with and without the region, since libraries
 * mostly use the bare one.
 */
val translatedLocales = translations
    .flatMap { (language, region) ->
        // Filipino is still filed under its legacy Tagalog code by some libraries
        listOfNotNull(
            language,
            "$language-r$region".takeIf { region.isNotEmpty() },
            "tl".takeIf { language == "fil" }
        )
    }
    .plus("en")
    .toSet()

/**
 * Translations as BCP 47 tags for the in-app language picker, written as a Java array literal for
 * BuildConfig. Resource folders still name Indonesian, Hebrew and Yiddish by their withdrawn ISO
 * codes, which tags no longer accept.
 */
val translationTags = translations.joinToString(prefix = "{", postfix = "}") { (language, region) ->
    val tagLanguage = mapOf("in" to "id", "iw" to "he", "ji" to "yi")[language] ?: language
    val tag = listOf(tagLanguage, region).filter { it.isNotEmpty() }.joinToString("-")
    "\"$tag\""
}

/**
 * An Android build of OpenJDK by AngelAuraMC, pinned by [sha256].
 *
 * @param feature The feature release, which the JDK that links its image has to match.
 * @param platform The platform its modules are linked for, as jmod names it.
 */
data class EmbeddedJvm(val feature: Int, val file: String, val sha256: String, val platform: String) {
    val url get() = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre$feature/$file"
}

/**
 * The JVM the app embeds for patching per ABI it ships one for. A 32 bit x86 device patches inside
 * the app instead.
 *
 * 25 in a 64-bit process, whose compact object headers leave a large run room for about 8% more.
 * 17 on 32-bit ARM, as the JIT of 25 there loops for good on loading the patches, C1 alone
 * included, in more than one build of it. PatcherProcesses tunes each by the bitness of the process.
 */
val embeddedJvms = mapOf(
    "arm64-v8a" to EmbeddedJvm(25, "jre25-android-arm64.tar.xz", "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37", "linux-aarch64"),
    "armeabi-v7a" to EmbeddedJvm(17, "jre17-android-arm.tar.xz", "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d", "linux-arm"),
    "x86_64" to EmbeddedJvm(25, "jre25-android-x86_64.tar.xz", "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d", "linux-amd64")
)

/**
 * The modules patching can reach: what the patcher and its libraries use, plus what ART offers patches
 * beyond that, so a patch that runs inside the app also runs in the JVM.
 */
val embeddedJvmModules = listOf(
    "java.base", "java.logging", "java.xml", "java.sql", "jdk.unsupported", "jdk.charsets", "jdk.crypto.ec",
    // The time spent collecting garbage, which the host watches for a heap too small for the run
    "java.management"
)

/**
 * What of a build the linked modules need, its native libraries above all, whichever of them the
 * build has. A build ships the server VM, but for the 32-bit ARM one, which ships the client VM.
 */
val embeddedJvmFiles = listOf(
    "lib/*/libjvm.so",
    "lib/libjava.so", "lib/libjimage.so", "lib/libzip.so", "lib/libnet.so", "lib/libnio.so",
    "lib/libverify.so", "lib/libmanagement.so",
    // Only in 25, whose java.base looks up native symbols through it
    "lib/libsyslookup.so",
    "lib/jvm.cfg", "lib/tzdb.dat", "lib/security", "conf"
)

val embeddedJvmTasks = embeddedJvms.map { (abi, build) ->
    tasks.register<PrepareEmbeddedJvmTask>("prepareEmbeddedJvm${abi.replace("-", "").replace("_", "").replaceFirstChar(Char::uppercase)}") {
        group = "build"
        description = "Links the embedded JVM for $abi."
        url.set(build.url)
        sha256.set(build.sha256)
        targetPlatform.set(build.platform)
        modules.set(embeddedJvmModules)
        runtimeFiles.set(embeddedJvmFiles)
        jdk.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(build.feature)) })
        downloadCache.set(File(gradle.gradleUserHomeDir, "caches/morphe-embedded-jvm"))
        outputArchive.set(layout.buildDirectory.file("intermediates/embeddedJvm/$abi.zip.xz"))
        outputImage.set(layout.buildDirectory.file("intermediates/embeddedJvm/$abi.modules"))
    }
}

/** The ABIs of [embeddedJvms] as a Java array literal for BuildConfig. */
val embeddedJvmAbis = embeddedJvms.keys.joinToString(prefix = "{", postfix = "}") { abi -> "\"$abi\"" }

/** Changes with whatever goes into the runtime, which the app then unpacks again. */
val embeddedJvmId = (embeddedJvms.values.map { build -> build.sha256 } + embeddedJvmModules + embeddedJvmFiles)
    .hashCode().toUInt().toString(16)

/** Assets the embedded JVM runs from, read by JvmInstallation. */
val embeddedJvmAssets = tasks.register<EmbeddedJvmAssetsTask>("embeddedJvmAssets") {
    group = "build"
    description = "Lays out the embedded JVM runtimes and the patcher host as assets."
    runtimes.from(embeddedJvmTasks.map { task -> task.flatMap { it.outputArchive } })
    images.from(embeddedJvmTasks.map { task -> task.flatMap { it.outputImage } })
    baseAbi.set(embeddedJvms.keys.first())
    hostJar.from(patcherHost)
    outputDirectory.set(layout.buildDirectory.dir("generated/embeddedJvm"))
}

android {
    namespace = "app.morphe.manager"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.morphe.manager"
        minSdk = 26

        versionName = version.toString()

        // VersionCode derived from current time (1-minute intervals) + offset.
        val nowMillis = System.currentTimeMillis()
        val timestampVersionCode = (nowMillis / (60 * 1000)).toInt()
        // Offset of the prior v1.1.1 version code to ensure the code is always newer for old installations.
        // If a new app is used this offset should be changed to zero.
        // 1 minute rounding and this offset still gives ~4,000 years of valid version codes
        // and still fall into Play store max version code range.
        val versionCodeOffset = 10010100
        versionCode = timestampVersionCode + versionCodeOffset

        // Expose the resolved morphe-patcher version so PatcherViewModel can compare it
        // against the Patcher-Version declared in .mpp bundle manifests at runtime.
        buildConfigField("String", "PATCHER_VERSION", "\"${libs.versions.morphe.patcher.get()}\"")

        buildConfigField("String[]", "TRANSLATIONS", translationTags)

        buildConfigField("String[]", "EMBEDDED_JVM_ABIS", embeddedJvmAbis)
        buildConfigField("String", "EMBEDDED_JVM_ID", "\"$embeddedJvmId\"")

        // The embedded JVM links against the shared C++ runtime, which the app ships alongside the launcher
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                // Only an ABI the app ships a JVM for starts one
                abiFilters += embeddedJvms.keys
            }
        }

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Unit tests run on the JVM, where android.util.Log is only a stub
    testOptions.unitTests.isReturnDefaultValues = true

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                // Build junk
                "/prebuilt/**",
                "/smali.properties",
                "/baksmali.properties",
                "/properties/apktool.properties",

                // Kotlin / debug metadata
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin",

                // Specific META-INF junk
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",

                // Crypto optional metadata
                "/org/bouncycastle/pqc/**.properties",
                "/org/bouncycastle/x509/**.properties",

                // ANTLR tool templates and grammar tokens; smali only needs the runtime at patch time
                "/org/antlr/codegen/**",
                "/org/antlr/tool/**",
                "/com/android/tools/smali/smali/*.tokens",

                // Mocking agent resources whose classes R8 already strips
                "/org/mockito/**",
                "/win32-x86/**",
                "/win32-x86-64/**"
            )
        )

        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        // The embedded JVM comes packed already, so deflating it again only slows down unpacking it
        noCompress += "xz"

        // Libraries ship strings in far more languages than Morphe has, which only bloat resources.arsc
        @Suppress("UnstableApiUsage")
        localeFilters += translatedLocales

        // Lists the translations for the per-app language setting of Android 13+ from the same
        // resource folders, with the default locale taken from res/resources.properties
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        lintConfig = file("lint.xml")
        disable += setOf("MissingTranslation")
        // Morphe ships as an APK, never as a bundle split by language
        disable += setOf("AppBundleLocaleChanges")
        // Crowdin drops a removed string from the translations only on its next sync
        warning += setOf("ExtraTranslation")
        baseline = file("lint-baseline.xml")
    }
}

// APK output file name
base.archivesName.set(provider {
    "${rootProject.name}-$version"
})

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
}

aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.EXACT
    }
}

tasks {
    whenTaskAdded {
        if (name.startsWith("lintVital")) {
            enabled = false
        }
    }

    // CI logs are all anyone reads of a failed run, so a failure prints its whole message there
    withType<Test>().configureEach {
        testLogging {
            events(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

/**
 * Launcher icons on offer, the one place they are defined. Each is a background with a mark over it.
 * The launcher only shows icons the manifest declares, so the build generates from this an
 * activity-alias per icon, the adaptive icon it shows and the catalog the app picks from.
 *
 * Backgrounds are listed in picker order. A background's drawable is ic_launcher_background_<id>
 * and its name settings_appearance_app_icon_<id>; a mark's name is
 * settings_appearance_app_icon_mark_<id>.
 *
 * The launcher remembers an icon by its alias, MainActivity_<Background>, plus _<Mark> for any mark
 * but the background's base one. Released ids and base marks must therefore never change, or users
 * of that icon would lose it on update. A background's color can change freely.
 */
val launcherIconMarks = linkedMapOf(
    "color" to "ic_launcher_foreground",
    "white" to "ic_launcher_foreground_white",
    "black" to "ic_launcher_foreground_black"
)

val launcherIconBackgrounds = listOf(
    // id to (base mark, other marks that read on it). A mark reads when it keeps a contrast of
    // about 2.5:1 against the background, the brand mark about 2:1 at its weaker end
    "default" to ("color" to listOf("black")),
    "light_2" to ("color" to listOf("black")),
    "light_3" to ("color" to listOf("black")),
    "light_4" to ("color" to listOf("black")),
    "light_5" to ("color" to listOf("black")),
    "light_6" to ("color" to listOf("black")),
    "light_7" to ("color" to listOf("black")),
    "dark_1" to ("white" to listOf("black")),
    "accent_1" to ("white" to listOf("black")),
    "accent_4" to ("white" to listOf("black")),
    "dark_3" to ("white" to listOf("black")),
    "dark_4" to ("white" to emptyList()),
    "dark_5" to ("white" to listOf("black")),
    "dark_2" to ("color" to listOf("white")),
    "accent_2" to ("white" to listOf("color")),
    "accent_3" to ("black" to listOf("color"))
)

val generateLauncherIcons = tasks.register<GenerateLauncherIconsTask>("generateLauncherIcons") {
    group = "build"
    description = "Generates the launcher icon aliases, adaptive icons and app catalog."
    marks.set(launcherIconMarks)
    backgrounds.set(launcherIconBackgrounds.map { (id, marks) -> listOf(id, marks.first) + marks.second })
    resDirectory.set(layout.buildDirectory.dir("generated/launcherIcons/res"))
    kotlinDirectory.set(layout.buildDirectory.dir("generated/launcherIcons/kotlin"))
    manifestFile.set(layout.buildDirectory.file("generated/launcherIcons/AndroidManifest.xml"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateLauncherIcons, GenerateLauncherIconsTask::resDirectory)
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateLauncherIcons, GenerateLauncherIconsTask::kotlinDirectory)
        variant.sources.manifests.addGeneratedManifestFile(generateLauncherIcons, GenerateLauncherIconsTask::manifestFile)
        variant.sources.assets?.addGeneratedSourceDirectory(embeddedJvmAssets, EmbeddedJvmAssetsTask::outputDirectory)
    }
}
