package app.revanced.manager.ui.viewmodel

import android.content.pm.PackageInfo
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.data.room.apps.installed.InstallType
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.installer.RootServiceException
import app.revanced.manager.domain.repository.InstalledAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.patcher.patch.PatchBundleInfo
import app.revanced.manager.util.PM
import app.revanced.manager.util.PatchSelection
import app.revanced.manager.util.mutableStateSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
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
                    val summaries = buildBundleSummaries(selection, bundleInfo, sourceMap)
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
                    // Try to find any saved file for this package (handles version updates)
                    val savedFile = filesystem.findPatchedAppFile(packageName)
                        ?: filesystem.getPatchedAppFile(packageName, installedApp.version)

                    if (!savedFile.exists()) {
                        installedAppsRepository.delete(installedApp)
                        return@withContext null
                    }
                    pm.getPackageInfo(savedFile)
                }

                else -> {
                    pm.getPackageInfo(packageName) ?: run {
                        // Try to find any saved file as fallback
                        val savedFile = filesystem.findPatchedAppFile(packageName)
                            ?: filesystem.getPatchedAppFile(packageName, installedApp.version)
                        if (savedFile.exists()) pm.getPackageInfo(savedFile) else null
                    }
                }
            }
        }

    private suspend fun loadAppliedPatches(packageName: String): PatchSelection =
        withContext(Dispatchers.IO) { installedAppsRepository.getAppliedPatches(packageName) }

    /**
     * Build bundle summaries from selection
     * Shows which bundles were used to patch the app
     */
    private fun buildBundleSummaries(
        selection: PatchSelection,
        bundleInfo: Map<Int, PatchBundleInfo.Global>,
        sourceMap: Map<Int, PatchBundleSource>
    ): List<AppBundleSummary> {
        if (selection.isEmpty()) return emptyList()

        return selection.keys.mapNotNull { uid ->
            val info = bundleInfo[uid]
            val source = sourceMap[uid]
            val title = source?.displayTitle ?: info?.name ?: return@mapNotNull null
            val version = info?.version
            AppBundleSummary(title = title, version = version)
        }
    }
}
