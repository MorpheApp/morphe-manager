package app.revanced.manager.util

import app.morphe.manager.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PatchedAppExportData(
    val appName: String?,
    val packageName: String,
    val appVersion: String?,
    val patchBundleVersions: List<String> = emptyList(),
    val patchBundleNames: List<String> = emptyList(),
    val generatedAt: Instant = Instant.now(),
    val managerVersion: String = BuildConfig.VERSION_NAME
)

object ExportNameFormatter {
    const val DEFAULT_TEMPLATE = "{app name}-{app version}-{patches version}.apk"

    data class Variable(
        val token: String,
        val label: Int,
        val description: Int
    )

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneId.systemDefault())

    fun availableVariables(): List<Variable> = listOf(
        Variable("{app name}", app.morphe.manager.R.string.export_name_variable_app_name, app.morphe.manager.R.string.export_name_variable_app_name_description),
        Variable("{package name}", app.morphe.manager.R.string.export_name_variable_package_name, app.morphe.manager.R.string.export_name_variable_package_name_description),
        Variable("{app version}", app.morphe.manager.R.string.export_name_variable_app_version, app.morphe.manager.R.string.export_name_variable_app_version_description),
        Variable("{patches version}", app.morphe.manager.R.string.export_name_variable_patches_version, app.morphe.manager.R.string.export_name_variable_patches_version_description),
        Variable("{patch bundle names}", app.morphe.manager.R.string.export_name_variable_bundle_names, app.morphe.manager.R.string.export_name_variable_bundle_names_description),
        Variable("{manager version}", app.morphe.manager.R.string.export_name_variable_manager_version, app.morphe.manager.R.string.export_name_variable_manager_version_description),
        Variable("{timestamp}", app.morphe.manager.R.string.export_name_variable_timestamp, app.morphe.manager.R.string.export_name_variable_timestamp_description),
        Variable("{date}", app.morphe.manager.R.string.export_name_variable_date, app.morphe.manager.R.string.export_name_variable_date_description)
    )

    fun format(template: String?, data: PatchedAppExportData): String {
        val resolvedTemplate = template?.takeIf { it.isNotBlank() } ?: DEFAULT_TEMPLATE
        val sanitizedTemplate = replaceVariables(resolvedTemplate, data)
        val ensuredExtension = ensureExtension(sanitizedTemplate)
        val clean = ensuredExtension.trim().ifEmpty { DEFAULT_TEMPLATE }
        return FilenameUtils.sanitize(clean)
    }

    fun preview(template: String): String = format(
        template,
        PatchedAppExportData(
            appName = "ExampleApp",
            packageName = "com.example.app",
            appVersion = "1.2.3",
            patchBundleVersions = listOf("2.201.0"),
            patchBundleNames = listOf("ReVanced Extended"),
            generatedAt = Instant.now()
        )
    )

    private fun replaceVariables(template: String, data: PatchedAppExportData): String {
        val replacements: Map<String, String> = buildMap {
            put("{app name}", data.appName?.takeUnless { it.isBlank() } ?: data.packageName)
            put("{package name}", data.packageName)
            put("{app version}", formatVersion(data.appVersion) ?: "unknown")
            put(
                "{patches version}",
                joinValues(
                    data.patchBundleVersions.mapNotNull(::formatVersion),
                    fallback = "unknown",
                    limit = 1
                )
            )
            put(
                "{patch bundle names}",
                joinValues(
                    data.patchBundleNames,
                    fallback = "bundles",
                    limit = 2,
                    separator = "_"
                )
            )
            put("{manager version}", data.managerVersion.takeUnless { it.isBlank() } ?: "unknown")
            put("{timestamp}", timestampFormatter.format(data.generatedAt))
            put("{date}", dateFormatter.format(data.generatedAt))
        }

        return replacements.entries.fold(template) { acc, (token, value) ->
            acc.replace(token, value)
        }
    }

    private fun formatVersion(raw: String?): String? {
        val trimmed = raw?.trim()?.takeUnless { it.isEmpty() } ?: return null
        val normalized = trimmed.removePrefix("v").removePrefix("V")
        return "v$normalized"
    }

    private fun joinValues(
        values: List<String>,
        fallback: String,
        limit: Int,
        separator: String = "+"
    ): String {
        val filtered = values.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (filtered.isEmpty()) return fallback
        val effective = if (limit > 0) filtered.take(limit) else filtered
        return effective.joinToString(separator)
    }

    private fun ensureExtension(value: String): String =
        if (value.endsWith(".apk", ignoreCase = true)) value else "$value.apk"
}
