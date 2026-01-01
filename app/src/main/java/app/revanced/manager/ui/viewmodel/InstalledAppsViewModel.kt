package app.revanced.manager.ui.viewmodel

import android.content.pm.PackageInfo
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.data.room.profile.PatchProfilePayload
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.RootServiceException
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.mutableStateSetOf
import app.revanced.manager.patcher.patch.PatchBundleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstalledAppsViewModel(
    private val installedAppsRepository: InstalledAppRepository,
    private val patchBundleRepository: PatchBundleRepository,
    private val pm: PM,
    private val rootInstaller: RootInstaller,
    private val filesystem: Filesystem
) : ViewModel() {
    val apps = installedAppsRepository.getAll().flowOn(Dispatchers.IO)

    val packageInfoMap = mutableStateMapOf<String, PackageInfo?>()
    val selectedApps = mutableStateSetOf<String>()
    val missingPackages = mutableStateSetOf<String>()
    val bundleSummaries = mutableStateMapOf<String, List<AppBundleSummary>>()

    init {
        viewModelScope.launch {
            apps.collect { installedApps ->
                val seenPackages = mutableSetOf<String>()
                val newMissing = mutableSetOf<String>()

                installedApps.forEach { installedApp ->
                    val packageName = installedApp.currentPackageName
                    seenPackages += packageName

                    val packageInfo = resolvePackageInfo(installedApp)
                    packageInfoMap[packageName] = packageInfo

                    if (installedApp.installType != InstallType.SAVED && packageInfo == null) {
                        newMissing += packageName
                    }
                }

                val stalePackages = packageInfoMap.keys.toSet() - seenPackages
                stalePackages.forEach { packageName ->
                    packageInfoMap.remove(packageName)
                    missingPackages.remove(packageName)
                    selectedApps.remove(packageName)
                }

                val missingToRemove = missingPackages.filterNot { it in newMissing }.toSet()
                missingPackages.removeAll(missingToRemove)
                val missingToAdd = newMissing.filterNot { it in missingPackages }.toSet()
                missingPackages.addAll(missingToAdd)

                val selectablePackages = installedApps.mapNotNull { app ->
                    when {
                        app.installType == InstallType.SAVED -> app.currentPackageName
                        app.currentPackageName in newMissing -> app.currentPackageName
                        else -> null
                    }
                }.toSet()
                selectedApps.retainAll(selectablePackages)
            }
        }

        viewModelScope.launch {
            combine(
                apps,
                patchBundleRepository.allBundlesInfoFlow,
                patchBundleRepository.sources
            ) { installedApps, bundleInfo, sources ->
                Triple(installedApps, bundleInfo, sources)
            }.collect { (installedApps, bundleInfo, sources) ->
                val sourceMap = sources.associateBy { it.uid }
                val packageNames = installedApps.map { it.currentPackageName }.toSet()

                installedApps.forEach { app ->
                    if (app.installType != InstallType.SAVED) {
                        bundleSummaries.remove(app.currentPackageName)
                        return@forEach
                    }
                    val selection = loadAppliedPatches(app.currentPackageName)
                    val summaries = buildBundleSummaries(app, selection, bundleInfo, sourceMap)
                    if (summaries.isEmpty()) {
                        bundleSummaries.remove(app.currentPackageName)
                    } else {
                        bundleSummaries[app.currentPackageName] = summaries
                    }
                }

                val stale = bundleSummaries.keys - packageNames
                stale.forEach { bundleSummaries.remove(it) }
            }
        }
    }

    data class AppBundleSummary(
        val title: String,
        val version: String?
    )

    fun toggleSelection(installedApp: InstalledApp) = viewModelScope.launch {
        val packageName = installedApp.currentPackageName
        val shouldSelect = packageName !in selectedApps
        setSelectionInternal(installedApp, shouldSelect)
    }

    fun setSelection(installedApp: InstalledApp, shouldSelect: Boolean) =
        viewModelScope.launch { setSelectionInternal(installedApp, shouldSelect) }

    fun clearSelection() {
        selectedApps.clear()
    }

    fun deleteSelectedApps() = viewModelScope.launch {
        if (selectedApps.isEmpty()) return@launch

        val snapshot = apps.first()
        val toDelete = snapshot.filter { it.currentPackageName in selectedApps }
        if (toDelete.isEmpty()) {
            selectedApps.clear()
            return@launch
        }

        toDelete.forEach { installedAppsRepository.delete(it) }
        withContext(Dispatchers.IO) {
            toDelete.filter { it.installType == InstallType.SAVED }.forEach { app ->
                val file = filesystem.getPatchedAppFile(app.currentPackageName, app.version)
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        val removedPackages = toDelete.map { it.currentPackageName }.toSet()
        selectedApps.removeAll(removedPackages)
        removedPackages.forEach { packageName ->
            packageInfoMap.remove(packageName)
            missingPackages.remove(packageName)
        }
    }

    private suspend fun setSelectionInternal(installedApp: InstalledApp, shouldSelect: Boolean) {
        val packageName = installedApp.currentPackageName
        if (shouldSelect && !isSelectable(installedApp)) return

        if (shouldSelect) {
            selectedApps.add(packageName)
        } else {
            selectedApps.remove(packageName)
        }
    }

    private suspend fun isSelectable(installedApp: InstalledApp): Boolean {
        if (installedApp.installType == InstallType.SAVED) return true

        val packageName = installedApp.currentPackageName
        if (packageName in missingPackages) return true

        val info = withContext(Dispatchers.IO) { pm.getPackageInfo(packageName) }
        packageInfoMap[packageName] = info

        val isMissing = info == null
        if (isMissing) {
            missingPackages.add(packageName)
        } else {
            missingPackages.remove(packageName)
        }
        return isMissing
    }

    private suspend fun resolvePackageInfo(installedApp: InstalledApp): PackageInfo? =
        withContext(Dispatchers.IO) {
            val packageName = installedApp.currentPackageName
            try {
                if (
                    installedApp.installType == InstallType.MOUNT &&
                    !rootInstaller.isAppInstalled(packageName)
                ) {
                    installedAppsRepository.delete(installedApp)
                    return@withContext null
                }
            } catch (_: RootServiceException) {
                // Ignore root service availability issues for mounted apps and fall back to package info lookup.
            }

            when (installedApp.installType) {
                InstallType.SAVED -> {
                    val savedFile = filesystem.getPatchedAppFile(packageName, installedApp.version)
                    if (!savedFile.exists()) {
                        installedAppsRepository.delete(installedApp)
                        return@withContext null
                    }
                    pm.getPackageInfo(savedFile)
                }

                else -> {
                    pm.getPackageInfo(packageName) ?: run {
                        val savedFile = filesystem.getPatchedAppFile(packageName, installedApp.version)
                        if (savedFile.exists()) pm.getPackageInfo(savedFile) else null
                    }
                }
            }
        }

    private suspend fun loadAppliedPatches(packageName: String): PatchSelection =
        withContext(Dispatchers.IO) { installedAppsRepository.getAppliedPatches(packageName) }

    private fun buildBundleSummaries(
        app: InstalledApp,
        selection: PatchSelection,
        bundleInfo: Map<Int, PatchBundleInfo.Global>,
        sourceMap: Map<Int, PatchBundleSource>
    ): List<AppBundleSummary> {
        val payloadBundles = app.selectionPayload?.bundles.orEmpty()
        val summaries = mutableListOf<AppBundleSummary>()
        val processed = mutableSetOf<Int>()

        selection.keys.forEach { uid ->
            processed += uid
            buildSummaryEntry(uid, payloadBundles, bundleInfo, sourceMap)?.let(summaries::add)
        }

        payloadBundles.forEach { bundle ->
            if (bundle.bundleUid in processed) return@forEach
            buildSummaryEntry(bundle.bundleUid, payloadBundles, bundleInfo, sourceMap)?.let(summaries::add)
        }

        return summaries
    }

    private fun buildSummaryEntry(
        uid: Int,
        payloadBundles: List<PatchProfilePayload.Bundle>,
        bundleInfo: Map<Int, PatchBundleInfo.Global>,
        sourceMap: Map<Int, PatchBundleSource>
    ): AppBundleSummary? {
        val info = bundleInfo[uid]
        val source = sourceMap[uid]
        val payloadBundle = payloadBundles.firstOrNull { it.bundleUid == uid }

        val title = source?.displayTitle
            ?: payloadBundle?.displayName
            ?: payloadBundle?.sourceName
            ?: info?.name
            ?: return null

        val version = payloadBundle?.version?.takeUnless { it.isBlank() } ?: info?.version
        return AppBundleSummary(title = title, version = version)
    }
}
