/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Lays out the assets the embedded JVM runs from: a runtime per ABI, named after it, and the
 * patcher host it runs. The app reads them from the jvm directory of its assets.
 */
abstract class EmbeddedJvmAssetsTask : DefaultTask() {
    /** The archives [PrepareEmbeddedJvmTask] packs, each named after its ABI. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimes: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val hostJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun layOut() {
        fileSystem.sync {
            from(runtimes)
            from(hostJar) { rename { HOST_JAR } }
            into(outputDirectory.dir(ASSETS_DIR))
        }
    }

    private companion object {
        const val ASSETS_DIR = "jvm"
        const val HOST_JAR = "patcher-host.jar"
    }
}
