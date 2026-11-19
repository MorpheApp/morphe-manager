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
    alias(libs.plugins.google.services)
    alias(libs.plugins.licensee)
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

    // Unit tests
    testImplementation(libs.kotlin.test.junit)
    // Compose UI under Robolectric, for behavior only a composition shows
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.compose.ui.test.manifest)
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

/** The release of BellSoft's Liberica that every ABI embeds, the same build for each. */
val libericaVersion = "25.0.5.1+5"

/** The feature release of [libericaVersion], which the JDK that links its image has to match. */
val libericaFeature = libericaVersion.substringBefore('.').toInt()

/**
 * The release of MorpheApp/morphe-jvm the packages of the GNU C library come from: termux-pacman's
 * recipes with what Android needs on top, and the packages of termux-pacman kept as they built them.
 */
val glibcRelease = "gpkg-1"

/**
 * The JVM the app embeds for one ABI: Liberica for Linux, pinned by [sha256], which runs on the
 * GNU C library as termux-pacman builds it for Android, see [glibcPackages] and [glibcRelease].
 *
 * @param platform The platform of the Liberica build, as its file names it.
 * @param jmodPlatform The platform its modules are linked for, as jmod names it.
 * @param termuxArch The architecture of the termux-pacman packages, as their repository names it.
 * @param glibcPackages The packages of the GNU C library, each pinned by its SHA-256.
 * @param loader The dynamic loader of the GNU C library, which starts the JVM.
 */
data class EmbeddedJvm(
    val platform: String,
    val sha256: String,
    val jmodPlatform: String,
    val termuxArch: String,
    val glibcPackages: Map<String, String>,
    val loader: String
) {
    val url get() = "https://github.com/bell-sw/Liberica/releases/download/$libericaVersion/bellsoft-jre$libericaVersion-$platform.tar.gz"

    val glibcUrls get() = glibcPackages.mapKeys { (name, _) -> "https://github.com/MorpheApp/morphe-jvm/releases/download/$glibcRelease/$name-$termuxArch.pkg.tar.xz" }
}

/**
 * The JVM the app embeds for patching per ABI it ships one for. A 32 bit x86 device patches inside
 * the app instead.
 *
 * Liberica rather than an Android build of OpenJDK: on 32-bit ARM every Android build of 25 loops
 * for good on loading the patches, while Liberica patches there about a fifth faster than the
 * client VM of 17 did. Android's C library cannot load a build for Linux, so the JVM runs as a
 * process of its own, started by the loader of the GNU C library, which ships as a native library.
 *
 * Android ends an app's process, and the processes it starts, on any system call its own C library
 * never makes, which every other build of glibc makes from its start on, such as set_robust_list,
 * rseq and clone3. termux-pacman builds it for Android, answering those as a kernel without them
 * would, or with the call bionic makes in their place. Its repository only keeps the latest build
 * of each package, so the app takes them from a release of its own, see [glibcRelease].
 */
val embeddedJvms = mapOf(
    "arm64-v8a" to EmbeddedJvm(
        platform = "linux-aarch64",
        sha256 = "335eb057b9b9d7e2c2d62c86b3c042be3974808f9592af608542f949accdb3fa",
        jmodPlatform = "linux-aarch64",
        termuxArch = "aarch64",
        glibcPackages = mapOf(
            "glibc-2.44-0" to "736b29e9d9615645be81c79667aad079741fd6482329cec5424a0a225cf6e53c",
            "zlib-glibc-1.3.2-0" to "823322a4dfaccf77ead5935941320c53cf25f73d113253c53382ac6f42d7868b",
            "gcc-libs-glibc-14.2.1-1" to "9316728ceeb23c320756aa0bb03460f9767b6efdc44a478c86f7fcc44c6e11ec"
        ),
        loader = "ld-linux-aarch64.so.1"
    ),
    "armeabi-v7a" to EmbeddedJvm(
        platform = "linux-arm32-vfp-hflt",
        sha256 = "40285dd2d6f8562be9296bb0ef505ca2b1e655ed07248ade110d46cff8ec28de",
        jmodPlatform = "linux-arm",
        termuxArch = "arm",
        glibcPackages = mapOf(
            "glibc-2.44-0" to "120bed98e3b7be1e2751fef091c3e17c295179b222aa18c6f7fc5ee781884a12",
            "zlib-glibc-1.3.2-0" to "f0489608b9ae7249c72df983b8ed5ee61d9ef0052e5d3398eca139275d1485a0",
            "gcc-libs-glibc-14.2.1-1" to "5b253b412651218f7478de4b747d8f851dc9609e1f218246d841e44caa81613a"
        ),
        loader = "ld-linux-armhf.so.3"
    ),
    "x86_64" to EmbeddedJvm(
        platform = "linux-amd64",
        sha256 = "b76a06e75c1910e96eeb32232a14e260b1d374b9433b7e9e3525a65b70cde731",
        jmodPlatform = "linux-amd64",
        termuxArch = "x86_64",
        glibcPackages = mapOf(
            "glibc-2.44-0" to "9e8171f946aa4a7d00f740b56924c38e2bdfeec8c57b8555b78366c8d5941c8c",
            "zlib-glibc-1.3.2-0" to "aea6ba84be3801808bd5bd06fddf6e304ceaea75a5e73d5248bf99406b0b88bb",
            "gcc-libs-glibc-14.2.1-1" to "5f7fd3600a4ac83357c91cd30641eae488c9afc1e2549e62429cafa4d0badf5a"
        ),
        loader = "ld-linux-x86-64.so.2"
    )
)

/**
 * What of a build the linked modules need: the server VM, the launcher the loader starts it
 * through, the native libraries of the modules and their configuration.
 */
val embeddedJvmFiles = listOf(
    "bin/java", "lib/libjli.so", "lib/server/libjvm.so",
    "lib/libjava.so", "lib/libjimage.so", "lib/libzip.so", "lib/libnet.so", "lib/libnio.so",
    "lib/libverify.so", "lib/libmanagement.so", "lib/libsyslookup.so",
    "lib/jvm.cfg", "lib/tzdb.dat", "lib/security", "conf"
)

/**
 * The modules patching can reach: what the patcher and its libraries use, plus what ART offers patches
 * beyond that, so a patch that runs inside the app also runs in the JVM.
 */
val embeddedJvmModules = listOf(
    // Elliptic curve cryptography is part of java.base since 22, which leaves jdk.crypto.ec empty
    "java.base", "java.logging", "java.xml", "java.sql", "jdk.unsupported", "jdk.charsets",
    // The time spent collecting garbage, which the host watches for a heap too small for the run
    "java.management"
)

/** The name the dynamic loader of the GNU C library ships under, as a native library of the app. */
val glibcLoaderLibrary = "libglibc_loader.so"

/** Where downloaded builds are kept between builds, as they rarely change. */
val embeddedJvmDownloads = File(gradle.gradleUserHomeDir, "caches/morphe-embedded-jvm")

/** llvm-strip of the NDK the app builds its native code with. */
val ndkStripTool = androidComponents.sdkComponents.ndkDirectory.map { ndk ->
    val bin = ndk.dir("toolchains/llvm/prebuilt").asFile.listFiles()!!.single().resolve("bin")
    ndk.file(bin.listFiles()!!.single { it.nameWithoutExtension == "llvm-strip" }.relativeTo(ndk.asFile).invariantSeparatorsPath)
}

/** The task name part of an ABI, such as Arm64v8a. */
fun abiTaskName(abi: String) = abi.replace("-", "").replace("_", "").replaceFirstChar(Char::uppercase)

val prepareGlibcTasks = embeddedJvms.mapValues { (abi, build) ->
    tasks.register<PrepareGlibcTask>("prepareGlibc${abiTaskName(abi)}") {
        group = "build"
        description = "Takes the GNU C library the embedded JVM for $abi runs on."
        packages.set(build.glibcUrls)
        libraryDirectory.set("data/data/com.termux/files/usr/glibc/lib")
        // What the linked modules and the launcher link against, and libgcc_s, which that zlib does
        libraries.set(listOf("libc.so.6", "libm.so.6", "libdl.so.2", "libpthread.so.0", "librt.so.1", "libz.so.1", "libgcc_s.so.1"))
        loader.set(build.loader)
        this.abi.set(abi)
        loaderLibraryName.set(glibcLoaderLibrary)
        downloadCache.set(embeddedJvmDownloads)
        runtimeFiles.set(layout.buildDirectory.dir("intermediates/embeddedJvm/glibc/$abi"))
        nativeLibraries.set(layout.buildDirectory.dir("generated/glibcLoader/$abi"))
    }
}

val embeddedJvmTasks = embeddedJvms.map { (abi, build) ->
    tasks.register<PrepareEmbeddedJvmTask>("prepareEmbeddedJvm${abiTaskName(abi)}") {
        group = "build"
        description = "Links the embedded JVM for $abi."
        url.set(build.url)
        sha256.set(build.sha256)
        targetPlatform.set(build.jmodPlatform)
        modules.set(embeddedJvmModules)
        runtimeFiles.set(embeddedJvmFiles)
        additionalFiles.from(prepareGlibcTasks.getValue(abi).flatMap { it.runtimeFiles })
        stripTool.set(ndkStripTool)
        jdk.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libericaFeature)) })
        downloadCache.set(embeddedJvmDownloads)
        outputArchive.set(layout.buildDirectory.file("intermediates/embeddedJvm/$abi.zip.xz"))
        outputImage.set(layout.buildDirectory.file("intermediates/embeddedJvm/$abi.modules"))
    }
}

/** The ABIs of [embeddedJvms] as a Java array literal for BuildConfig. */
val embeddedJvmAbis = embeddedJvms.keys.joinToString(prefix = "{", postfix = "}") { abi -> "\"$abi\"" }

/** Changes with whatever goes into the runtime, which the app then unpacks again. */
val embeddedJvmId = (embeddedJvms.values.flatMap { build -> listOf(build.sha256) + build.glibcPackages.values } +
        embeddedJvmFiles + embeddedJvmModules).hashCode().toUInt().toString(16)

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
        buildConfigField("String", "GLIBC_LOADER_LIBRARY", "\"$glibcLoaderLibrary\"")

        // The launcher is plain C, which needs no C++ runtime
        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
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
    // Robolectric reads the merged manifest and resources to start the activity a Compose test runs in
    testOptions.unitTests.isIncludeAndroidResources = true

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

/**
 * The license each URL stands for that a dependency names its license by rather than by an SPDX
 * identifier. The licenses page shows that license for it, see [LicensesAssetsTask].
 */
val licensesByUrl = mapOf(
    "http://antlr.org/license.html" to "ANTLR-BSD",
    "http://www.antlr.org/license.html" to "ANTLR-BSD",
    "https://developer.android.com/studio/terms.html" to "ASDKL",
    "https://www.bouncycastle.org/licence.html" to "Bouncy-Castle",
    "https://github.com/composablehorizons/composeicons/blob/main/LICENSE" to "MIT",
    "https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE" to "MIT",
    "https://opensource.org/license/mit" to "MIT",
)

// A dependency under any other license fails the build, as the app could not be shipped with it
licensee {
    listOf("0BSD", "Apache-2.0", "BSD-3-Clause").forEach(::allow)
    licensesByUrl.forEach { (url, license) -> allowUrl(url) { because(license) } }
    ignoreDependencies("app.morphe") { because("Morphe, listed as the manager itself") }
    // JitPack builds come without a license, so these are listed by hand in licenses/licenses.json
    ignoreDependencies("com.github.MorpheApp", "ARSCLib") { because("Apache-2.0, listed by hand") }
    ignoreDependencies("com.github.topjohnwu.libsu", "core") { because("Apache-2.0, listed by hand") }
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
        // Robolectric sets up the shared memory of Android 16 through the JDK's own internals
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
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
        prepareGlibcTasks.values.forEach { task ->
            variant.sources.jniLibs?.addGeneratedSourceDirectory(task, PrepareGlibcTask::nativeLibraries)
        }

        val variantName = variant.name.replaceFirstChar(Char::uppercase)
        val licensesAssets = tasks.register<LicensesAssetsTask>("${variant.name}LicensesAssets") {
            group = "build"
            description = "Lays out the libraries the $variantName build ships and their licenses as assets."
            report.from(tasks.named("licenseeAndroid$variantName"))
            extras.set(layout.projectDirectory.file("licenses/licenses.json"))
            texts.set(layout.projectDirectory.dir("licenses/texts"))
            notice.set(rootProject.layout.projectDirectory.file("NOTICE"))
            licenseUrls.set(licensesByUrl)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(licensesAssets, LicensesAssetsTask::outputDirectory)
    }
}
