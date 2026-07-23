/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import android.app.Application
import android.content.Context
import android.os.StatFs
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.installer.InstallerFileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Aggregated storage and cache footprint of the app. */
data class StorageStats(
    val originalApksBytes: Long,
    val patchedApksBytes: Long,
    val patchBundlesBytes: Long,
    val keystoreBytes: Long,
    val appDataBytes: Long,
    val httpCacheBytes: Long,
    val installerShareBytes: Long,
    val patcherWorkspaceBytes: Long,
    val temporaryBytes: Long,
    val deviceFreeBytes: Long
) {
    val totalCacheBytes: Long
        get() = httpCacheBytes + installerShareBytes + patcherWorkspaceBytes + temporaryBytes

    val appUsedBytes: Long
        get() = originalApksBytes + patchedApksBytes + patchBundlesBytes +
                keystoreBytes + appDataBytes + totalCacheBytes

    companion object {
        val Empty = StorageStats(
            originalApksBytes = 0L,
            patchedApksBytes = 0L,
            patchBundlesBytes = 0L,
            keystoreBytes = 0L,
            appDataBytes = 0L,
            httpCacheBytes = 0L,
            installerShareBytes = 0L,
            patcherWorkspaceBytes = 0L,
            temporaryBytes = 0L,
            deviceFreeBytes = 0L
        )
    }
}

/**
 * Computes on-disk usage of app-managed directories (APKs, caches, bundles, keystore).
 * Consumers observe [stats]; call [refresh] to force a rescan after mutations.
 */
class StorageStatsRepository(
    private val app: Application,
    private val filesystem: Filesystem
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _stats = MutableStateFlow(StorageStats.Empty)
    val stats: StateFlow<StorageStats> = _stats.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    init {
        scope.launch {
            refreshTrigger.collect { _stats.value = computeStats() }
        }
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    /** Deletes the OkHttp on-disk cache. Safe: the client rebuilds it on demand. */
    suspend fun clearHttpCache(): Long = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, HTTP_CACHE_DIR)
        val freed = dir.totalBytes()
        dir.wipeContents()
        refresh()
        freed
    }

    suspend fun clearInstallerShareCache(): Long = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, InstallerFileProvider.SHARE_DIR)
        val freed = dir.totalBytes()
        dir.wipeContents()
        refresh()
        freed
    }

    suspend fun clearTemporary(): Long = withContext(Dispatchers.IO) {
        val freed = filesystem.tempDir.totalBytes() + filesystem.uiTempDir.totalBytes()
        filesystem.tempDir.wipeContents()
        filesystem.uiTempDir.wipeContents()
        refresh()
        freed
    }

    /**
     * Deletes patcher runtime files under `cacheDir`: the persistent `framework/` and `patcher/`
     * subdirectories plus any orphaned scratch files at `cacheDir` root left behind by aborted
     * bundle imports, keystore imports, or process-runtime merges.
     */
    suspend fun clearPatcherWorkspace(): Long = withContext(Dispatchers.IO) {
        val entries = collectPatcherWorkspaceEntries()
        val freed = entries.sumOf { it.totalBytes() }
        entries.forEach { it.deleteRecursively() }
        refresh()
        freed
    }

    /** Wipes everything considered safe to drop (http, installer share, patcher workspace, ephemeral). */
    suspend fun clearAllCaches(): Long = withContext(Dispatchers.IO) {
        clearHttpCache() +
            clearInstallerShareCache() +
            clearPatcherWorkspace() +
            clearTemporary()
    }

    private suspend fun computeStats(): StorageStats = withContext(Dispatchers.IO) {
        StorageStats(
            originalApksBytes = filesystem.originalApksDir.totalBytes(),
            patchedApksBytes = app.getDir(PATCHED_APPS_DIR, Context.MODE_PRIVATE).totalBytes(),
            patchBundlesBytes = app.getDir(PATCH_BUNDLES_DIR, Context.MODE_PRIVATE).totalBytes(),
            keystoreBytes = app.getDir(SIGNING_DIR, Context.MODE_PRIVATE)
                .resolve(KEYSTORE_FILE).length().coerceAtLeast(0),
            appDataBytes = readAppDataBytes(),
            httpCacheBytes = File(app.cacheDir, HTTP_CACHE_DIR).totalBytes(),
            installerShareBytes = File(app.cacheDir, InstallerFileProvider.SHARE_DIR).totalBytes(),
            patcherWorkspaceBytes = collectPatcherWorkspaceEntries().sumOf { it.totalBytes() },
            temporaryBytes = filesystem.tempDir.totalBytes() + filesystem.uiTempDir.totalBytes(),
            deviceFreeBytes = readDeviceFreeBytes()
        )
    }

    /**
     * Size of Room database files, DataStore preferences, and no-backup files. Not clearable from
     * the UI (removing these would wipe patch history and user settings); reported as an
     * informational bucket so the histogram total matches Android's app-info more closely.
     */
    private fun readAppDataBytes(): Long {
        val databases = File(app.dataDir, "databases").totalBytes()
        val dataStore = File(app.filesDir, "datastore").totalBytes()
        val noBackup = runCatching { app.noBackupFilesDir.totalBytes() }.getOrDefault(0L)
        return databases + dataStore + noBackup
    }

    private fun readDeviceFreeBytes(): Long = runCatching {
        StatFs(app.filesDir.absolutePath).availableBytes
    }.getOrDefault(0L)

    /**
     * Entries under [Application.getCacheDir] that belong to the patcher workspace:
     * everything except the buckets accounted for by other stats fields ([HTTP_CACHE_DIR] and
     * [InstallerFileProvider.SHARE_DIR]).
     */
    private fun collectPatcherWorkspaceEntries(): List<File> {
        val cacheDir = app.cacheDir
        if (!cacheDir.exists()) return emptyList()
        return cacheDir.listFiles()?.filter { entry ->
            entry.name != HTTP_CACHE_DIR && entry.name != InstallerFileProvider.SHARE_DIR
        } ?: emptyList()
    }

    companion object {
        const val HTTP_CACHE_DIR = "cache"
        private const val PATCHED_APPS_DIR = "patched-apps"
        private const val PATCH_BUNDLES_DIR = "patch_bundles"
        private const val SIGNING_DIR = "signing"
        private const val KEYSTORE_FILE = "morphe.keystore"
    }
}

private fun File.totalBytes(): Long =
    if (!exists()) 0L else walkBottomUp().filter { it.isFile }.sumOf { it.length() }

private fun File.wipeContents() {
    if (!exists()) return
    listFiles()?.forEach { it.deleteRecursively() }
}
