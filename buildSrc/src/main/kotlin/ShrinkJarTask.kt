/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Drops from a jar what nothing it keeps reaches, with ProGuard, which only shrinks: names, code
 * and attributes of what is left stay as they are.
 *
 * Besides [rules], ProGuard follows the rules the libraries in the jar ship for shrinkers under
 * META-INF/proguard, which R8 would pick up on its own. The classes of the JDK the jar runs on
 * are the library it is shrunk against, so a method that overrides one of them is never taken
 * for unused.
 */
@CacheableTask
abstract class ShrinkJarTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rules: ConfigurableFileCollection

    /** Paths in the jar that are left out of the shrunk one, as ProGuard filters write them. */
    @get:Input
    abstract val excludes: ListProperty<String>

    /** The JDK the jar runs on, which also runs ProGuard. */
    @get:Nested
    abstract val jdk: Property<JavaLauncher>

    @get:Classpath
    abstract val proguardClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun shrink() {
        val work = temporaryDir.apply {
            deleteRecursively()
            mkdirs()
        }
        val jdkHome = jdk.get().metadata.installationPath.asFile
        val jar = inputJar.get().asFile
        val output = outputJar.get().asFile.apply { delete() }

        val jdkClasses = work.resolve("jdk")
        exec.exec {
            commandLine(
                jdkHome.resolve("bin").tool("jimage"), "extract",
                "--dir", jdkClasses.path, jdkHome.resolve("lib/modules").path
            )
        }

        val libraryRules = work.resolve("library-rules").apply { mkdirs() }
        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(LIBRARY_RULES) && it.name.endsWith(".pro") }
                .forEach { entry ->
                    libraryRules.resolve(entry.name.removePrefix(LIBRARY_RULES).replace('/', '_'))
                        .writeBytes(zip.getInputStream(entry).use { it.readBytes() })
                }
        }

        val filter = excludes.get().joinToString(",") { "!$it" }
        val configuration = work.resolve("proguard.pro")
        configuration.writeText(
            buildString {
                appendLine("-injars ${jar.quoted()}($filter)")
                appendLine("-outjars ${output.quoted()}")
                // Each module apart, as ProGuard expects the path of a class to follow its name
                jdkClasses.listFiles()!!.sorted().forEach { appendLine("-libraryjars ${it.quoted()}(!module-info.class,**.class)") }
                (rules.files + libraryRules.listFiles()!!.sorted()).forEach { appendLine("-include ${it.quoted()}") }
            }
        )

        exec.javaexec {
            executable = jdk.get().executablePath.asFile.path
            classpath = proguardClasspath
            mainClass.set("proguard.ProGuard")
            args("@${configuration.path}")
        }
    }

    private fun File.tool(name: String) = resolve(name).takeIf { it.exists() }?.path ?: resolve("$name.exe").path

    private fun File.quoted() = "\"${invariantSeparatorsPath}\""

    private companion object {
        const val LIBRARY_RULES = "META-INF/proguard/"
    }
}
