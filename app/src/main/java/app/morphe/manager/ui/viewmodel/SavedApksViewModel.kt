/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.domain.apk.InstalledPatchState
import app.morphe.manager.domain.apk.LocalApkSources
import app.morphe.manager.domain.installer.BatchUninstaller
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.patcher.util.NativeLibs
import app.morphe.manager.ui.screen.settings.system.ApkItemData
import app.morphe.manager.ui.screen.settings.system.selectionKey
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One row of a saved APK list: what it shows, and the record behind it. */
sealed interface SavedApkRow {
    val data: ApkItemData
}

data class PatchedApkRow(override val data: ApkItemData, val installedApp: InstalledApp) : SavedApkRow

data class OriginalApkRow(override val data: ApkItemData, val apk: OriginalApk) : SavedApkRow

/**
 * The saved APKs of one kind, listed for the user to install, uninstall or delete.
 *
 * The work runs here rather than in the dialog listing them, since closing the dialog would
 * otherwise cut a batch off halfway, with some files deleted and some apps removed.
 */
abstract class SavedApksViewModel<T : SavedApkRow>(
    private val messages: UserMessages,
    private val batchUninstaller: BatchUninstaller
) : ViewModel() {
    // Installs and removals shown before the records behind the rows catch up, dropped with
    // every fresh read of them
    private val installedMarks = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** The rows, read afresh whenever what they are built from changes. */
    protected abstract fun loadRows(): Flow<List<T>>

    /**
     * The rows, null until they are first read. Read only while something shows them, and built
     * on first use, once the subclass that loads them is set up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<T>?> by lazy {
        loadRows()
            .onEach { installedMarks.value = emptyMap() }
            .flatMapLatest { loaded -> installedMarks.map { marks -> loaded.map { it.marked(marks) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Shown once a single APK is deleted, the one the user confirmed on its own. */
    @get:StringRes
    protected abstract val deletedOneMessage: Int

    protected abstract fun T.withInstalled(installed: Boolean): T

    protected abstract fun T.uninstallTarget(): BatchUninstaller.Target

    /** Deletes the files of [rows], returning whether all of them are gone. */
    protected abstract suspend fun deleteFiles(rows: List<T>): Boolean

    protected abstract fun T.installRequest(file: File): InstallQueueRequest

    /** What installs [items] one after another, skipping those whose file is gone. */
    fun installRequests(items: List<ApkItemData>): List<InstallQueueRequest> =
        rowsOf(items).mapNotNull { row -> row.data.file?.takeIf(File::exists)?.let { row.installRequest(it) } }

    fun uninstall(items: List<ApkItemData>) {
        val rows = rowsOf(items)
        if (rows.isEmpty()) return
        viewModelScope.launch {
            batchUninstaller.uninstall(rows.map { it.uninstallTarget() }) { target ->
                markInstalled(target.packageName, installed = false)
            }
        }
    }

    fun delete(items: List<ApkItemData>) = delete(items, R.string.settings_system_apks_deleted_all)

    /** Deletes the one APK of [item], confirmed on its own. */
    fun deleteOne(item: ApkItemData) = delete(listOf(item), deletedOneMessage)

    private fun delete(items: List<ApkItemData>, @StringRes deletedMessage: Int) {
        val rows = rowsOf(items)
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val deleted = deleteFiles(rows)
            messages.show(if (deleted) deletedMessage else R.string.settings_system_apks_delete_failed)
        }
    }

    /**
     * Marks the rows of [packageName] installed or not, which the records behind them only
     * report once the device has told them.
     */
    protected fun markInstalled(packageName: String, installed: Boolean) {
        installedMarks.update { it + (packageName to installed) }
    }

    private fun T.marked(marks: Map<String, Boolean>): T {
        val installed = marks[data.packageName] ?: marks[data.originalPackageName] ?: return this
        return withInstalled(installed)
    }

    private fun rowsOf(items: List<ApkItemData>): List<T> {
        // By key rather than by index, so a list that changed under the selection still finds them
        val keys = items.mapTo(mutableSetOf()) { it.selectionKey }
        return rows.value.orEmpty().filter { it.data.selectionKey in keys }
    }

    protected fun onInstalledRequest(row: T): (String) -> Unit = { packageName ->
        markInstalled(row.data.packageName, installed = true)
        markInstalled(packageName, installed = true)
    }
}

/** The patched APKs kept after patching, each standing for the record of the app it built. */
class PatchedApksViewModel(
    messages: UserMessages,
    batchUninstaller: BatchUninstaller,
    private val installedAppRepository: InstalledAppRepository,
    private val originalApkRepository: OriginalApkRepository,
    private val appDataResolver: AppDataResolver,
    private val pm: PM,
    private val localApkSources: LocalApkSources
) : SavedApksViewModel<PatchedApkRow>(messages, batchUninstaller) {
    override val deletedOneMessage = R.string.settings_system_patched_apks_deleted

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadRows() = combine(
        installedAppRepository.getAll(),
        installedAppRepository.savedPatchedApkChanges.onStart { emit(emptySet()) }
    ) { apps, _ -> apps }.mapLatest { apps ->
        withContext(Dispatchers.IO) {
            // Records read once for all rows
            val records = ResolverRecords(apps, originalApkRepository.getAll().first())
            apps.mapNotNull { app ->
                // Only the copies this record owns, so a renamed app never lists the
                // archive an unrenamed record keeps under the same original name
                val storedFile = installedAppRepository.savedPatchedApkFiles(app)
                    .firstOrNull { it.exists() } ?: return@mapNotNull null
                val snapshot = localApkSources.trackedAppSnapshot(app)
                val savedFile = snapshot.savedPatchedApk

                val resolvedData = appDataResolver.resolveAppData(
                    app.currentPackageName,
                    preferredSource = AppDataSource.PATCHED_APK,
                    records = records
                )
                // Taken from the archive the row actually points at, which can differ from
                // the resolver's answer once the installed app is no longer the patched one
                val savedDisplayName = snapshot.savedPatchedApkInfo
                    ?.let { packageInfo -> runCatching { with(pm) { packageInfo.label() } }.getOrNull() }
                    ?.takeUnless(String::isBlank)
                val displayName = savedDisplayName ?: resolvedData.displayName

                PatchedApkRow(
                    data = ApkItemData(
                        packageName = app.currentPackageName,
                        displayName = displayName,
                        exportFileName = app.exportFileName(displayName),
                        version = app.version,
                        fileSize = (savedFile ?: storedFile).length(),
                        file = savedFile,
                        installType = app.installType,
                        isInstalledOnDevice = snapshot.patchState == InstalledPatchState.Patched,
                        abis = savedFile?.let(NativeLibs::extractAbisFromApk).orEmpty(),
                        packageInfo = snapshot.savedPatchedApkInfo ?: resolvedData.packageInfo,
                        originalPackageName = app.originalPackageName
                    ),
                    installedApp = app
                )
            }
        }
    }

    override fun PatchedApkRow.withInstalled(installed: Boolean) =
        copy(data = data.copy(isInstalledOnDevice = installed))

    override fun PatchedApkRow.uninstallTarget() =
        BatchUninstaller.Target(data.packageName, data.installType, tracked = installedApp)

    override suspend fun deleteFiles(rows: List<PatchedApkRow>) =
        installedAppRepository.deleteSavedPatchedApks(rows.map { it.installedApp })

    override fun PatchedApkRow.installRequest(file: File) = InstallQueueRequest(
        file = file,
        appName = data.displayName,
        originalPackageName = installedApp.originalPackageName,
        installedApp = installedApp,
        onPersistApp = { packageName, installType ->
            installedAppRepository.recordReinstall(installedApp, packageName, installType)
            true
        },
        onInstalled = onInstalledRequest(this)
    )
}

/** The original APKs kept for patching again, which belong to no record of their own. */
class OriginalApksViewModel(
    messages: UserMessages,
    batchUninstaller: BatchUninstaller,
    private val originalApkRepository: OriginalApkRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val appDataResolver: AppDataResolver,
    private val pm: PM
) : SavedApksViewModel<OriginalApkRow>(messages, batchUninstaller) {
    override val deletedOneMessage = R.string.settings_system_original_apks_deleted

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadRows() = flow {
        // Records left behind by a file that is already gone would show a stale size and count
        originalApkRepository.pruneMissingApks()
        emitAll(originalApkRepository.getAll())
    }.mapLatest { apks ->
        withContext(Dispatchers.IO) {
            // Records read once for all rows
            val records = ResolverRecords(installedAppRepository.getAll().first(), apks)
            apks.map { apk ->
                val resolvedData = appDataResolver.resolveAppData(
                    apk.packageName,
                    preferredSource = AppDataSource.ORIGINAL_APK,
                    records = records
                )
                val apkFile = File(apk.filePath).takeIf { it.exists() }

                OriginalApkRow(
                    data = ApkItemData(
                        packageName = apk.packageName,
                        displayName = resolvedData.displayName,
                        exportFileName = ExportNameFormatter.format(
                            ApkExportData(
                                appName = resolvedData.displayName,
                                packageName = apk.packageName,
                                appVersion = apk.version
                            )
                        ),
                        version = apk.version,
                        fileSize = apk.fileSize,
                        file = apkFile,
                        isInstalledOnDevice = pm.getPackageInfo(apk.packageName) != null,
                        abis = apkFile?.let(NativeLibs::extractAbisFromApk).orEmpty(),
                        packageInfo = resolvedData.packageInfo
                    ),
                    apk = apk
                )
            }
        }
    }

    override fun OriginalApkRow.withInstalled(installed: Boolean) =
        copy(data = data.copy(isInstalledOnDevice = installed))

    // An original is the stock app, which has no patched build to prove before it goes
    override fun OriginalApkRow.uninstallTarget() =
        BatchUninstaller.Target(data.packageName, data.installType, verify = false)

    override suspend fun deleteFiles(rows: List<OriginalApkRow>): Boolean {
        rows.forEach { originalApkRepository.delete(it.apk) }
        return true
    }

    override fun OriginalApkRow.installRequest(file: File) = InstallQueueRequest(
        file = file,
        appName = data.displayName,
        originalPackageName = data.packageName,
        onPersistApp = { _, _ -> true },
        onInstalled = onInstalledRequest(this)
    )
}
