package app.revanced.manager.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.network.service.HttpService
import app.revanced.manager.network.utils.APIResponse
import io.ktor.client.request.url
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PatchListCatalog {
    private const val PREF_NAME = "patch_list_catalog"
    private const val KEY_DATA = "data"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val CACHE_TTL = 24 * 60 * 60 * 1000L
    private const val RAW_URL =
        "https://raw.githubusercontent.com/Jman-Github/ReVanced-Patch-Bundles/bundles/patch-bundles/PATCH-LIST-CATALOG.md"
    private const val CATALOG_BASE_URL =
        "https://github.com/Jman-Github/ReVanced-Patch-Bundles/blob/bundles/patch-bundles/PATCH-LIST-CATALOG.md"
    private const val FALLBACK_ANCHOR = "-patch-list-catalog-key"
    private const val REVANCED_ANCHOR = "-revanced-bundle-patch-list"
    private val REVANCED_SHORTCUT_KEY = normalize("ReVanced Bundle Patch List")

    @Volatile
    private var runtimeMap: Map<String, String> = emptyMap()
    private var prefs: SharedPreferences? = null
    private val mutex = Mutex()

    fun initialize(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        val cached = decodeMap(sharedPrefs.getString(KEY_DATA, null))
        if (cached.isNotEmpty()) {
            runtimeMap = cached
        }
    }

    suspend fun refreshIfNeeded(httpService: HttpService) {
        val sharedPrefs = prefs ?: return
        val now = System.currentTimeMillis()
        val last = sharedPrefs.getLong(KEY_TIMESTAMP, 0L)
        if (now - last < CACHE_TTL) return

        mutex.withLock {
            val currentLast = sharedPrefs.getLong(KEY_TIMESTAMP, 0L)
            if (now - currentLast < CACHE_TTL) return@withLock
            when (val response = httpService.request<String> { url(RAW_URL) }) {
                is APIResponse.Success -> {
                    val map = parseCatalog(response.data)
                    if (map.isNotEmpty()) {
                        runtimeMap = map
                        sharedPrefs.edit()
                            .putString(KEY_DATA, encodeMap(map))
                            .putLong(KEY_TIMESTAMP, now)
                            .apply()
                    }
                }
                else -> {
                    // Keep the current runtime map (may be empty) to avoid stale hardcoded fallbacks.
                }
            }
        }
    }

    fun resolveCatalogUrl(source: PatchBundleSource): String? {
        val anchor = findAnchor(runtimeMap, source)
        return anchor?.let { "$CATALOG_BASE_URL#$it" } ?: "$CATALOG_BASE_URL#$FALLBACK_ANCHOR"
    }

    fun revancedCatalogUrl(): String = "$CATALOG_BASE_URL#$REVANCED_ANCHOR"

    private fun findAnchor(map: Map<String, String>, source: PatchBundleSource): String? {
        val candidates = buildCandidateList(source)
        return candidates
            .sortedByDescending { it.length } // Prefer the most specific candidate first.
            .firstNotNullOfOrNull { candidate ->
                map[candidate]
            }
    }

    private fun buildCandidateList(source: PatchBundleSource): List<String> {
        val manifestName = source.patchBundle?.manifestAttributes?.name
        val manifestSourceRaw = source.patchBundle?.manifestAttributes?.source
        val manifestSource = manifestSourceRaw?.let(::normalizeManifestSource)
        val remoteEndpoint = (source as? RemotePatchBundle)?.endpoint

        val ownerRepo = (manifestSource ?: remoteEndpoint)?.let(::parseOwnerRepo)
        val owner = ownerRepo?.first
        val repo = ownerRepo?.second

        val derivedNames = buildList {
            remoteEndpoint?.let { addAll(extractNamesFromEndpoint(it, owner, repo, includeOwnerRepo = true)) }
            manifestSource?.let { addAll(extractNamesFromEndpoint(it, owner, repo, includeOwnerRepo = true)) }
            ownerRepo?.let { (o, r) ->
                add("$o-$r")
                add(o)
                add(r)
            }
        }.ifEmpty { listOfNotNull(manifestSource, remoteEndpoint, manifestSourceRaw) }
        val endpointSegment = remoteEndpoint?.substringAfterLast('/')?.substringBefore('.')
        val manifestSourceSegment = manifestSource?.substringAfterLast('/')?.substringBefore('.')

        val fallbacks = listOfNotNull(
            manifestName,
            source.name,
            source.displayTitle,
            endpointSegment,
            manifestSourceSegment
        )

        val normalized = (derivedNames + fallbacks)
            .mapNotNull { normalize(it).takeIf(String::isNotBlank) }
            .distinct()

        return if (source.isDefault) normalized else normalized.filterNot { it == REVANCED_SHORTCUT_KEY }
    }

    private val endpointSuffixes = listOf(
        "-latest-patches-bundle",
        "-patches-bundle",
        "-patch-bundles",
        "-latest-bundle",
        "-patches",
        "-patch",
        "-bundle",
        "-bundles",
        "_latest"
    )

    private val genericSegmentNames = setOf(
        "bundles", "bundle", "patch", "patches", "patch-bundles", "releases", "download", "blob", "raw", "latest"
    )

    private fun extractNamesFromEndpoint(
        url: String,
        owner: String?,
        repo: String?,
        includeOwnerRepo: Boolean = false
    ): List<String> {
        val rawSegments = runCatching { Uri.parse(url).pathSegments }.getOrElse { emptyList() }
        val startIndex = rawSegments.indexOfFirst { it.contains("patch-bundles", ignoreCase = true) }
        val segments = if (startIndex >= 0 && startIndex + 1 < rawSegments.size) {
            rawSegments.drop(startIndex + 1)
        } else rawSegments
        val filtered = segments.filter { it.isNotBlank() }
        if (filtered.isEmpty()) return emptyList()

        val ignore = buildSet {
            if (!includeOwnerRepo) {
                owner?.let { add(normalize(it)) }
                repo?.let { add(normalize(it)) }
            }
            addAll(genericSegmentNames)
        }

        return filtered.mapNotNull { segment ->
            val candidate = cleanSegment(segment)
            val normalized = normalize(candidate)
            if (normalized.isBlank() || normalized in ignore) null else normalized
        }
    }

    private fun cleanSegment(segment: String): String {
        val base = segment.substringBeforeLast('.')
        var cleaned = base.lowercase(Locale.US)
        endpointSuffixes.forEach { suffix ->
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.removeSuffix(suffix)
            }
        }
        cleaned = cleaned.replace(Regex("[^a-z0-9-]"), "")
        return cleaned
    }


    private fun normalizeManifestSource(source: String): String {
        val trimmed = source.trim()
        return when {
            trimmed.startsWith("git@") -> {
                val path = trimmed.substringAfter(":").removeSuffix(".git")
                "https://github.com/$path"
            }
            trimmed.startsWith("ssh://git@") -> {
                val path = trimmed.substringAfterLast(":").removeSuffix(".git")
                "https://github.com/$path"
            }
            else -> trimmed.removeSuffix(".git")
        }
    }
    private fun parseOwnerRepo(endpoint: String): Pair<String, String>? =
        runCatching<Pair<String, String>?> {
            val uri = Uri.parse(endpoint)
            val segments = uri.pathSegments
            if (segments.size >= 2) segments[0] to segments[1] else null
        }.getOrNull()

    private fun parseCatalog(body: String): Map<String, String> {
        val shortcutRegex = Regex("\\[(.+?)\\]\\(#([^)]+)\\)")
        val lines = body.lineSequence()
        val map = mutableMapOf<String, String>()
        var inShortcuts = false
        for (line in lines) {
            if (!inShortcuts) {
                if (line.contains("Patch Bundle Shortcuts")) {
                    inShortcuts = true
                }
                continue
            }
            if (line.startsWith("---")) break
            val match = shortcutRegex.find(line.trim()) ?: continue
            val label = match.groupValues[1]
            val anchor = match.groupValues[2].removePrefix("#")
            val normalized = normalize(label)
            if (normalized.isNotEmpty()) {
                map[normalized] = anchor
            }
        }
        return map
    }

    private fun normalize(input: String): String {
        var value = input.lowercase(Locale.US)
        value = value.replace("bundle patch list", "")
        value = value.replace("patch list", "")
        value = value.replace("bundle", "")
        value = value.replace(Regex("[^a-z0-9-]"), "")
        value = value.replace(Regex("-+"), "-").trim('-')
        return value
    }

    private fun encodeMap(map: Map<String, String>): String =
        map.entries.joinToString(separator = "\n") { "${it.key}=${it.value}" }

    private fun decodeMap(serialized: String?): Map<String, String> {
        if (serialized.isNullOrBlank()) return emptyMap()
        return serialized.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = line.substring(0, idx)
                val value = line.substring(idx + 1)
                if (key.isBlank() || value.isBlank()) return@mapNotNull null
                key to value
            }
            .toMap()
    }
}
