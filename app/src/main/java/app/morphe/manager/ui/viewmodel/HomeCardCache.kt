/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.bundles.AppVersionStatus
import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.util.tag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The home cards of the last build, kept on disk so a cold start shows them before the patch
 * bundles load.
 *
 * Only bundle and package manager data is kept. Records and home preferences are read live, and
 * cached cards carry no PackageInfo, so their icons are resolved by package name.
 */
internal class HomeCardCache(private val file: File) {
    // Defaults included, so the format is always written
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Volatile
    private var lastWritten: CachedHome? = null

    /** The cards of the last build, or null if missing or unreadable. */
    fun read(): CachedHome? = runCatching {
        json.decodeFromString<CachedHome>(file.readText())
            .takeIf { it.format == CACHE_FORMAT }
            ?.also { lastWritten = it }
    }.onFailure { if (file.exists()) Log.w(tag, "Discarding unreadable home card cache", it) }
        .getOrNull()

    /**
     * Keeps [cards] for the next cold start unless already cached. A deleted file is written again.
     */
    fun write(cards: HomeCards) {
        val cached = CachedHome(
            visible = cards.visible.map(::CachedCard),
            hidden = cards.hidden.map(::CachedCard),
            sourceGroups = cards.sourceGroups.map(::CachedSourceGroup)
        )
        if (cached == lastWritten && file.exists()) return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(json.encodeToString(CachedHome.serializer(), cached))
            // Atomic swap, so a killed write keeps the previous file
            if (!temp.renameTo(file)) error("Could not replace ${file.name}")
            lastWritten = cached
        }.onFailure { Log.w(tag, "Could not keep the home cards", it) }
    }
}

/** Bumped when [CachedHome] changes shape, so older files are dropped. */
private const val CACHE_FORMAT = 1

@Serializable
internal data class CachedHome(
    val visible: List<CachedCard>,
    val hidden: List<CachedCard>,
    val sourceGroups: List<CachedSourceGroup>,
    val format: Int = CACHE_FORMAT
) {
    /** These cards, with records from [installedApps] and the sort mode from [prefs]. */
    fun toCards(installedApps: List<InstalledApp>, prefs: HomePrefs): HomeCards {
        val records = installedApps.associateBy { it.currentPackageName }
        // Cards are keyed by id on screen, and a file kept by an older build can repeat one
        return HomeCards(
            visible = visible.distinctBy { it.id }.map { it.toItem(records) },
            hidden = hidden.distinctBy { it.id }.map { it.toItem(records) },
            sortMode = prefs.sortMode,
            sourceGroups = sourceGroups.map { it.toGroup() }
        )
    }

    /** Cards that showed an update badge. */
    fun idsWithUpdate(): Set<String> =
        (visible + hidden).filter { it.hasUpdate }.mapTo(HashSet()) { it.id }
}

@Serializable
internal data class CachedCard(
    val id: String,
    val packageName: String,
    val displayName: String,
    val colors: List<Int>,
    val installedPackage: String?,
    val version: String,
    val isPinnedByDefault: Boolean,
    val isInstalledOnDevice: Boolean,
    val isDeleted: Boolean,
    val isInstallStateNotPatched: Boolean,
    val isInstallStateUnknown: Boolean,
    val savedApkPath: String?,
    val hasUpdate: Boolean,
    val versionStatus: CachedVersionStatus?,
    val isClone: Boolean,
    val nameSuffix: String?
) {
    constructor(item: HomeAppItem) : this(
        id = item.id,
        packageName = item.packageName,
        displayName = item.displayName,
        colors = item.gradientColors.map { it.toArgb() },
        installedPackage = item.installedApp?.currentPackageName,
        version = item.version,
        isPinnedByDefault = item.isPinnedByDefault,
        isInstalledOnDevice = item.isInstalledOnDevice,
        isDeleted = item.isDeleted,
        isInstallStateNotPatched = item.isInstallStateNotPatched,
        isInstallStateUnknown = item.isInstallStateUnknown,
        savedApkPath = item.savedApkFile?.path,
        hasUpdate = item.hasUpdate,
        versionStatus = item.versionStatus?.let(::CachedVersionStatus),
        isClone = item.isClone,
        nameSuffix = item.nameSuffix
    )

    fun toItem(records: Map<String, InstalledApp>) = HomeAppItem(
        id = id,
        packageName = packageName,
        displayName = displayName,
        gradientColors = colors.map(::Color),
        // A removed record no longer claims an install
        installedApp = installedPackage?.let(records::get),
        packageInfo = null,
        version = version,
        isPinnedByDefault = isPinnedByDefault,
        isInstalledOnDevice = isInstalledOnDevice,
        isDeleted = isDeleted,
        isInstallStateNotPatched = isInstallStateNotPatched,
        isInstallStateUnknown = isInstallStateUnknown,
        isInstallStatePending = false,
        // Saved APKs can be cleared while the app is closed
        savedApkFile = savedApkPath?.let(::File)?.takeIf { it.exists() },
        hasUpdate = hasUpdate,
        versionStatus = versionStatus?.toStatus(),
        patchCount = 0,
        isClone = isClone,
        nameSuffix = nameSuffix
    )
}

@Serializable
internal data class CachedVersionStatus(
    val installedVersion: String,
    val supportedVersion: String,
    val isBehind: Boolean
) {
    constructor(status: AppVersionStatus) :
        this(status.installedVersion, status.supportedVersion, status.isBehind)

    fun toStatus() = AppVersionStatus(installedVersion, supportedVersion, isBehind)
}

@Serializable
internal data class CachedSourceGroup(
    val uid: Int,
    val name: String,
    val packageNames: Set<String>,
    val packageOrder: List<String>,
    val collapsed: Boolean,
    val avatarUrl: String?,
    val fallbackAvatarUrl: String?
) {
    constructor(group: HomeAppSourceGroup) : this(
        uid = group.uid,
        name = group.name,
        packageNames = group.packageNames,
        packageOrder = group.packageOrder,
        collapsed = group.collapsed,
        avatarUrl = group.avatarUrl,
        fallbackAvatarUrl = group.fallbackAvatarUrl
    )

    fun toGroup() = HomeAppSourceGroup(
        uid, name, packageNames, packageOrder, collapsed, avatarUrl, fallbackAvatarUrl
    )
}
