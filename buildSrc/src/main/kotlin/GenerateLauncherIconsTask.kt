/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes the launcher icons of a catalog: an activity-alias per icon in a manifest the app merges,
 * the adaptive icon each one shows, and the catalog the app picks from. The catalog itself, and
 * the rules its ids follow, live in app/build.gradle.kts.
 */
abstract class GenerateLauncherIconsTask : DefaultTask() {
    /** Mark id to its foreground drawable. */
    @get:Input
    abstract val marks: MapProperty<String, String>

    /** Background ids in picker order, each with its base mark and then its other marks. */
    @get:Input
    abstract val backgrounds: ListProperty<List<String>>

    @get:OutputDirectory
    abstract val resDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    private fun pascal(id: String) = id.split('_').joinToString("_") { it.replaceFirstChar(Char::uppercase) }
    private fun constant(id: String) = id.uppercase()

    @TaskAction
    fun generate() {
        val marks = marks.get()
        // Each icon: background id, mark id, whether the mark is the base one
        val icons = backgrounds.get().flatMap { entry ->
            val id = entry.first()
            entry.drop(1).mapIndexed { index, mark -> Triple(id, mark, index == 0) }
        }
        fun suffix(icon: Triple<String, String, Boolean>) =
            if (icon.third) icon.first else "${icon.first}_${icon.second}"

        val mipmaps = resDirectory.get().dir("mipmap-anydpi-v26").asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        icons.forEach { icon ->
            mipmaps.resolve("ic_launcher_${suffix(icon)}.xml").writeText(
                """
                |<?xml version="1.0" encoding="utf-8"?>
                |<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                |    <background android:drawable="@drawable/ic_launcher_background_${icon.first}" />
                |    <foreground android:drawable="@drawable/${marks.getValue(icon.second)}" />
                |    <monochrome android:drawable="@drawable/${marks.getValue("color")}" />
                |</adaptive-icon>
                |""".trimMargin()
            )
        }

        manifestFile.get().asFile.writeText(buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            // Lint checks this manifest on its own, so the TV support the main manifest declares for
            // the leanback launcher entries is repeated here
            appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    xmlns:tools="http://schemas.android.com/tools">""")
            appendLine("""    <uses-feature android:name="android.software.leanback" android:required="false" />""")
            appendLine("""    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />""")
            appendLine("""    <application tools:ignore="MissingTvBanner">""")
            icons.forEachIndexed { index, icon ->
                val suffix = suffix(icon)
                // Only the first icon is on until the user picks another
                appendLine(
                    """
                    |        <activity-alias
                    |            android:name="app.morphe.manager.MainActivity_${pascal(suffix)}"
                    |            android:enabled="${index == 0}"
                    |            android:exported="true"
                    |            android:icon="@mipmap/ic_launcher_$suffix"
                    |            android:roundIcon="@mipmap/ic_launcher_$suffix"
                    |            android:label="@string/app_name"
                    |            android:targetActivity="app.morphe.manager.MainActivity">
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.MAIN" />
                    |                <category android:name="android.intent.category.LAUNCHER" />
                    |                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
                    |            </intent-filter>
                    |        </activity-alias>
                    |""".trimMargin()
                )
            }
            appendLine("    </application>")
            appendLine("</manifest>")
        })

        val kotlin = kotlinDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        kotlin.resolve("LauncherIcons.kt").writeText(buildString {
            appendLine("// Generated from the launcher icon catalog in app/build.gradle.kts. Do not edit.")
            appendLine("package app.morphe.manager.domain.manager")
            appendLine()
            appendLine("import androidx.annotation.DrawableRes")
            appendLine("import androidx.annotation.StringRes")
            appendLine("import app.morphe.manager.R")
            appendLine()
            appendLine("/** The Morphe mark drawn over an icon's background. */")
            appendLine("enum class IconMark(@param:StringRes val displayNameResId: Int, @param:DrawableRes val drawableResId: Int) {")
            appendLine(marks.entries.joinToString(",\n") { (id, drawable) ->
                "    ${constant(id)}(R.string.settings_appearance_app_icon_mark_$id, R.drawable.$drawable)"
            })
            appendLine("}")
            appendLine()
            appendLine("/** A background an icon is built on, in picker order, with the mark it is first offered with. */")
            appendLine("enum class IconBackground(@param:StringRes val displayNameResId: Int, @param:DrawableRes val drawableResId: Int, val baseMark: IconMark) {")
            appendLine(backgrounds.get().joinToString(",\n") { entry ->
                val id = entry.first()
                "    ${constant(id)}(R.string.settings_appearance_app_icon_$id, R.drawable.ic_launcher_background_$id, IconMark.${constant(entry[1])})"
            })
            appendLine("}")
            appendLine()
            appendLine("/** A launcher icon: a background, a mark that reads on it, and the alias that shows it. */")
            appendLine("enum class AppIcon(val aliasName: String, val background: IconBackground, val mark: IconMark, @param:DrawableRes val launcherIconResId: Int) {")
            appendLine(icons.joinToString(",\n") { icon ->
                val suffix = suffix(icon)
                "    ${constant(suffix)}(\"app.morphe.manager.MainActivity_${pascal(suffix)}\", " +
                    "IconBackground.${constant(icon.first)}, IconMark.${constant(icon.second)}, R.mipmap.ic_launcher_$suffix)"
            } + ";")
            appendLine()
            appendLine("    companion object")
            appendLine("}")
        })
    }
}
