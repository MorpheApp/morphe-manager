/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.backup

import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SelectionSetRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.ui.viewmodel.AllSelectionsExportFile
import app.morphe.manager.ui.viewmodel.ManagerSettingsExportFile
import app.morphe.manager.ui.viewmodel.PatchBundleDataExportFile
import app.morphe.manager.ui.viewmodel.SelectionSetExportFile
import app.morphe.manager.ui.viewmodel.SelectionSetSourceExport
import app.morphe.manager.util.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds the backups of the manager's settings and patch selections, for both the files the user
 * saves by hand and the copies [AutoBackup] keeps.
 */
class SettingsExporter(
    private val preferencesManager: PreferencesManager,
    private val homeAppButtonPreferences: HomeAppButtonPreferences,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val patchOptionsRepository: PatchOptionsRepository,
    private val selectionSetRepository: SelectionSetRepository,
    private val patchBundleRepository: PatchBundleRepository
) {
    /** Everything a settings backup holds, cut down to [sections]. */
    suspend fun managerSettings(sections: Set<SettingsSection>): ManagerSettingsExportFile {
        val snapshot = preferencesManager.exportSettings()
        val bundles = withContext(Dispatchers.IO) { patchBundleRepository.exportCustomBundles() }
        val homeAppButtons = homeAppButtonPreferences.exportState()
        return ManagerSettingsExportFile(
            settings = snapshot.copy(
                appLanguage = AppLocale.selected.value,
                customBundles = bundles.ifEmpty { null },
                homeAppButtons = homeAppButtons
            ).restrictedTo(sections),
            selections = if (SettingsSection.PATCH_SELECTIONS in sections) allSelections() else null
        )
    }

    /** Every source's patch selections, options and kept apps, and every set, as one export. */
    suspend fun allSelections(): AllSelectionsExportFile {
        val exportDate = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())

        val bundles = withContext(Dispatchers.IO) {
            // A source can be one an app is kept from without anything ever being selected
            // from it, and that decision is worth restoring on its own
            val packagesByBundle = (
                    patchSelectionRepository.getAllBundleUids() +
                            sourceMuteRepository.getAllBundleUids()
                    ).distinct()

            packagesByBundle.map { bundleUid ->
                val selections = patchSelectionRepository.exportAllForBundle(bundleUid)
                val options = buildMap {
                    selections.keys.forEach { packageName ->
                        val rawOptions = patchOptionsRepository.exportOptionsForBundle(packageName, bundleUid)
                        if (rawOptions.isNotEmpty()) put(packageName, rawOptions)
                    }
                }
                PatchBundleDataExportFile(
                    bundleUid = bundleUid,
                    bundleName = patchBundleRepository.getNameForUid(bundleUid),
                    bundleSource = patchBundleRepository.getEndpointForUid(bundleUid),
                    exportDate = exportDate,
                    selections = selections,
                    options = options.ifEmpty { null },
                    mutedPackages = sourceMuteRepository.exportForBundle(bundleUid).ifEmpty { null },
                    knownApps = sourceMuteRepository.exportKnownApps(bundleUid)
                )
            }.filter { it.selections.isNotEmpty() || it.mutedPackages != null || it.knownApps != null }
        }

        val sets = withContext(Dispatchers.IO) {
            selectionSetRepository.exportAll().map { set ->
                SelectionSetExportFile(
                    packageName = set.packageName,
                    name = set.name,
                    sources = (set.patches.keys + set.options.keys).map { bundleUid ->
                        SelectionSetSourceExport(
                            bundleUid = bundleUid,
                            bundleSource = patchBundleRepository.getEndpointForUid(bundleUid),
                            patches = set.patches[bundleUid].orEmpty(),
                            options = set.options[bundleUid]
                        )
                    }
                )
            }
        }

        return AllSelectionsExportFile(
            exportDate = exportDate,
            bundles = bundles,
            // Written even when empty, so a backup restored in Replace mode clears the sets it lacks
            sets = sets
        )
    }

    companion object {
        // Reusable JSON instances to avoid redundant creation. Both are internal so the export
        // format stays under test instead of the tests rebuilding their own configuration
        internal val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true // Make exports human-readable
        }

        /**
         * Settings exports omit unset fields instead of writing a wall of nulls. Every field in
         * that file has a default, so older manager versions still read the trimmed form.
         */
        internal val settingsJson = Json(json) { explicitNulls = false }
    }
}
