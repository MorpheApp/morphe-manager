/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Lays out the baseline profile as the asset ART compiles the app with while it installs, on
 * Android 15 and up and whatever installs it. The Android Gradle plugin only packages the profile for
 * ProfileInstaller, under another path and in an older format, which ART does not read on its own.
 * It does write the profile in the format ART reads, as dex metadata next to the APK.
 */
@CacheableTask
abstract class EmbeddedProfileAssetsTask : DefaultTask() {
    /** What the Android Gradle plugin compiles the baseline profile to, among it the dex metadata. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledProfile: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun layOut() {
        val profile = compiledProfile.asFileTree.files
            .filter { it.extension == "dm" }
            .sorted()
            .map(::profileOf)
            .firstOrNull { it.startsWith(HEADER) }
            ?: throw GradleException("No dex metadata has a profile in the format ART embeds")

        outputDirectory.get().file(PROFILE_ASSET).asFile.apply {
            parentFile.mkdirs()
            writeBytes(profile)
        }
    }

    private fun profileOf(dexMetadata: File) = ZipFile(dexMetadata).use { zip ->
        val entry = zip.getEntry("primary.prof") ?: throw GradleException("No profile in $dexMetadata")
        zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        /** Where artd looks for the profile below the assets, see kEmbeddedProfileEntry in AOSP. */
        const val PROFILE_ASSET = "art-profile/baseline.prof"

        /** The magic number and the version of the profile format of Android 12 and up. */
        val HEADER = "pro".toByteArray() + 0 + "015".toByteArray() + 0
    }
}
