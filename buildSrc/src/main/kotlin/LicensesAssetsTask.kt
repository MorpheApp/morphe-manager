/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Lays out what the licenses page of the app shows: every library the app is built with and the
 * licenses it comes under, in [INDEX], and the text of each license beside it. The libraries come
 * from the report of Licensee, which has already checked their licenses, and from [extras] for
 * those that reach the app some other way.
 */
@CacheableTask
abstract class LicensesAssetsTask : DefaultTask() {
    /** What Licensee writes for the variant, among it the artifacts.json report. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val report: ConfigurableFileCollection

    /**
     * The libraries Licensee does not see, such as the manager itself and the JVM it embeds, and
     * the name and URL of every license no dependency names by an SPDX identifier.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extras: RegularFileProperty

    /** The text of each license, named after it, which a license without one goes without. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val texts: DirectoryProperty

    /** The notice of the manager, which its own entry shows in place of its license. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notice: RegularFileProperty

    /** The license each URL stands for that a dependency names its license by. */
    @get:Input
    abstract val licenseUrls: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    @Suppress("UNCHECKED_CAST")
    fun layOut() {
        val assets = outputDirectory.get().dir(ASSETS_DIR).asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        val extras = JsonSlurper().parse(extras.get().asFile) as Map<String, Any?>
        val licenses = (extras["licenses"] as Map<String, Map<String, String?>>).toMutableMap()
        val urls = licenseUrls.get()

        val artifacts = JsonSlurper().parse(report.asFileTree.matching { include(REPORT) }.singleFile)
            as List<Map<String, Any?>>
        val reported = artifacts.map { artifact ->
            val coordinates = "${artifact["groupId"]}:${artifact["artifactId"]}"
            val spdx = artifact["spdxLicenses"] as List<Map<String, String>>? ?: emptyList()
            val unknown = artifact["unknownLicenses"] as List<Map<String, String?>>? ?: emptyList()
            spdx.forEach { licenses.putIfAbsent(it.getValue("identifier"), mapOf("name" to it["name"], "url" to it["url"])) }
            mapOf(
                "name" to (artifact["name"] ?: coordinates),
                "version" to artifact["version"],
                "website" to (artifact["scm"] as Map<String, String?>?)?.get("url"),
                "licenses" to (spdx.map { it.getValue("identifier") } + unknown.map { license ->
                    urls[license["url"]]
                        ?: throw GradleException("$coordinates names its license by ${license["url"]}, which no license is given for")
                }).distinct()
            )
        }

        // An artifact and the platform builds of it go by one name, which is listed once
        val libraries = ((extras["libraries"] as List<Map<String, Any?>>) + reported)
            .distinctBy { it["name"] to it["licenses"] }
            .sortedBy { (it["name"] as String).lowercase() }

        val shown = libraries.flatMap { it["licenses"] as List<String> }.toSet()
        val undefined = shown - licenses.keys
        if (undefined.isNotEmpty()) throw GradleException("No name and URL are given for the licenses $undefined")

        texts.get().asFile.listFiles()!!.forEach { it.copyTo(assets.resolve(it.name)) }
        notice.get().asFile.copyTo(assets.resolve(NOTICE))
        val missing = libraries.mapNotNull { it["text"] as String? }.filterNot { assets.resolve(it).isFile }
        if (missing.isNotEmpty()) throw GradleException("No texts are given for $missing")

        // Each license names its text, so the app never looks for one that is not there
        val listed = licenses.filterKeys(shown::contains).mapValues { (key, license) ->
            license + ("text" to "$key.txt".takeIf { assets.resolve(it).isFile })
        }
        assets.resolve(INDEX).writeText(JsonOutput.toJson(mapOf("licenses" to listed, "libraries" to libraries)))
    }

    private companion object {
        const val ASSETS_DIR = "licenses"
        const val INDEX = "licenses.json"
        const val NOTICE = "NOTICE.txt"
        const val REPORT = "artifacts.json"
    }
}
