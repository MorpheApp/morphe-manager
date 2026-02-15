package app.morphe.manager.util

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

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneId.systemDefault())

    fun format(template: String?, data: PatchedAppExportData): String {
        val resolvedTemplate = template?.takeIf { it.isNotBlank() } ?: DEFAULT_TEMPLATE
        val sanitizedTemplate = replaceVariables(resolvedTemplate, data)
        val ensuredExtension = ensureExtension(sanitizedTemplate)
        val clean = ensuredExtension.trim().ifEmpty { DEFAULT_TEMPLATE }
        return FilenameUtils.sanitize(clean)
    }

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
