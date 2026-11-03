/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/viewmodel/ImportExportViewModel.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.domain.manager.HomeAppButtonPreferences
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.manager.SettingsSection
import app.morphe.manager.domain.manager.SigningKeyInfo
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.SelectionSetRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.StoredSelectionSet
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.*
import app.morphe.patcher.apk.ApkSigner
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.deleteExisting

@Serializable
data class ManagerSettingsExportFile(
    val version: Int = 1,
    val settings: PreferencesManager.SettingsSnapshot,
    // Null in backups written before selections could travel with the settings, and in any that
    // leave them out
    val selections: AllSelectionsExportFile? = null
)

/**
 * Export file format for patch selections and options.
 * This format stores selections (which patches are enabled) and their options (patch configuration).
 */
@Serializable
data class PatchBundleDataExportFile(
    val version: Int = 1,
    val bundleUid: Int,
    val bundleName: String? = null,
    // Normalized endpoint URL - used to remap bundleUid when importing on a different device
    val bundleSource: String? = null,
    val exportDate: String,
    // Map<PackageName, List<PatchName>>
    val selections: Map<String, List<String>>,
    // Map<PackageName, Map<PatchName, Map<OptionKey, OptionValue>>>
    // Deliberately without a default: the field has always been written out, and giving it one
    // would drop it from the file for versions that still require it
    val options: Map<String, Map<String, Map<String, String>>>?,
    // Apps kept from this source. Null in backups written before an app could be, which reads the
    // same as a backup where no app was
    val mutedPackages: List<String>? = null,
    // Apps the source has listed so far, present only when it leaves new apps out. Null in backups
    // written before a source could, which reads as a source that brings them
    val knownApps: List<String>? = null
)

/**
 * Export file format for all selections across all bundles.
 * Wraps a list of [PatchBundleDataExportFile] entries - one per bundle.
 */
@Serializable
data class AllSelectionsExportFile(
    val version: Int = 1,
    val exportDate: String,
    val bundles: List<PatchBundleDataExportFile>,
    // Null in backups written before sets could be kept, which leaves the sets on the device alone
    val sets: List<SelectionSetExportFile>? = null
)

/** A set kept for one app, see [SelectionSetRepository], as a backup carries it. */
@Serializable
data class SelectionSetExportFile(
    val packageName: String,
    val name: String,
    val sources: List<SelectionSetSourceExport>
)

/** What a [SelectionSetExportFile] holds of one source. */
@Serializable
data class SelectionSetSourceExport(
    val bundleUid: Int,
    // Normalized endpoint URL, which the source is found by on another device, as with the
    // bundleSource of a PatchBundleDataExportFile
    val bundleSource: String? = null,
    val patches: List<String>,
    // Map<PatchName, Map<OptionKey, OptionValue>>
    val options: Map<String, Map<String, String>>? = null
)

/**
 * Serializable snapshot of remote patch bundle, used for export/import
 * as part of the manager settings file [ManagerSettingsExportFile].
 */
@Serializable
data class BundleSnapshot(
    val name: String,
    val displayName: String? = null,
    val source: String,
    // Every remote source updates on its own, so this is ignored on import. Still written, as
    // older manager versions require it to read the file
    val autoUpdate: Boolean,
    val enabled: Boolean = true,
    val sortOrder: Int,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    // Prerelease toggle. Null in backups written before per-source toggles were exported
    val prerelease: Boolean? = null,
    // Experimental versions toggle. Null in backups written before per-source toggles were exported
    val experimentalVersions: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
class ImportExportViewModel(
    private val app: Application,
    private val messages: UserMessages,
    private val keystoreManager: KeystoreManager,
    private val preferencesManager: PreferencesManager,
    private val homeAppButtonPreferences: HomeAppButtonPreferences,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val sourceMuteRepository: SourceMuteRepository,
    private val patchOptionsRepository: PatchOptionsRepository,
    private val selectionSetRepository: SelectionSetRepository,
    private val patchBundleRepository: PatchBundleRepository
) : ViewModel() {
    private val contentResolver = app.contentResolver

    private var keystoreImportPath by mutableStateOf<Path?>(null)
    val showCredentialsDialog by derivedStateOf { keystoreImportPath != null }

    /** The key patching signs with, null until the first patch creates one. */
    var signingKey by mutableStateOf<SigningKeyInfo?>(null)
        private set

    /**
     * Parts of the manager settings a backup moves. One choice serves both directions, so what the
     * user ticked before exporting is what an import on the other device offers to bring back.
     */
    var settingsSections by mutableStateOf(SettingsSection.entries.toSet())
        private set

    fun toggleSettingsSection(section: SettingsSection) {
        settingsSections = if (section in settingsSections) settingsSections - section
        else settingsSections + section
    }

    /** Re-reads [signingKey], which patching and an import both change behind the screen's back. */
    fun refreshSigningKey() = viewModelScope.launch {
        signingKey = keystoreManager.signingKeyInfo()
    }

    fun startKeystoreImport(content: Uri) = viewModelScope.launch {
        messages.reportingFailure(R.string.settings_system_import_keystore_failed, "Failed to import keystore") {
            val path = withContext(Dispatchers.IO) {
                File.createTempFile("signing", "ks", app.cacheDir).toPath().also {
                    Files.copy(
                        contentResolver.openInputStream(content)!!,
                        it,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

            // No credentials open a file that is no keystore, so none are asked for
            val data = withContext(Dispatchers.IO) { Files.readAllBytes(path) }
            if (ApkSigner.KeyStoreType.of(data) == null) {
                withContext(Dispatchers.IO) { Files.deleteIfExists(path) }
                throw IllegalArgumentException("Unrecognized keystore format")
            }

            for (alias in aliases) {
                for (pass in knownPasswords) {
                    if (tryKeystoreImport(alias, pass, path)) return@launch
                }
            }

            // Auto-import failed - ask the user for credentials
            keystoreImportPath = path
        }
    }

    fun cancelKeystoreImport() {
        keystoreImportPath?.deleteExisting()
        keystoreImportPath = null
    }

    suspend fun tryKeystoreImport(alias: String, pass: String): Boolean =
        tryKeystoreImport(alias, pass, keystoreImportPath!!)

    private suspend fun tryKeystoreImport(alias: String, pass: String, path: Path): Boolean {
        val imported = keystoreManager.import(alias, pass, path.toFile())
        if (imported) {
            messages.show(R.string.settings_system_import_keystore_success)
            // The copy holds the private key, and an automatic import never recorded its path
            withContext(Dispatchers.IO) { Files.deleteIfExists(path) }
            keystoreImportPath = null
            refreshSigningKey()
        }
        return imported
    }

    fun exportKeystore(target: Uri) = viewModelScope.launch {
        messages.reportingFailure(R.string.settings_system_export_keystore_failed, "Failed to export keystore") {
            withContext(Dispatchers.IO) {
                keystoreManager.export(contentResolver.openOutputStream(target)!!)
            }
            messages.show(R.string.settings_system_export_keystore_success)
        }
    }

    fun importManagerSettings(
        source: Uri,
        mode: PatchBundleRepository.ImportMode = PatchBundleRepository.ImportMode.Merge,
    ) = viewModelScope.launch {
        messages.reportingFailure(
            R.string.settings_system_import_manager_settings_fail,
            "Failed to import manager settings"
        ) {
            val exportFile = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(source)!!.use {
                    settingsJson.decodeFromStream<ManagerSettingsExportFile>(it)
                }
            }

            val sections = settingsSections
            val settings = exportFile.settings.restrictedTo(sections)
            preferencesManager.importSettings(settings)

            // The app language and the home app buttons (categories, hidden apps, sort mode, etc.)
            // live outside PreferencesManager, so they're applied here rather than in importSettings
            settings.appLanguage?.let { AppLocale.select(app, it) }
            settings.homeAppButtons?.let(homeAppButtonPreferences::importState)

            // Called whenever sources are taken, so Replace can clear existing custom sources even
            // when the backup has none, and never otherwise, so declining them leaves them alone
            if (SettingsSection.SOURCES in sections) {
                patchBundleRepository.importCustomBundles(settings.customBundles.orEmpty(), mode)
            }

            // After the sources, so selections made against a source the backup brought back
            // find it in place
            if (SettingsSection.PATCH_SELECTIONS in sections) {
                exportFile.selections?.let { importSelectionBundles(it.bundles, it.sets, mode) }
            }

            messages.show(R.string.settings_system_import_manager_settings_success)
        }
    }

    /** Everything a settings backup holds, cut down to [settingsSections]. */
    private suspend fun managerSettingsExportFile(): ManagerSettingsExportFile {
        val sections = settingsSections
        val snapshot = preferencesManager.exportSettings()
        val bundles = withContext(Dispatchers.IO) { patchBundleRepository.exportCustomBundles() }
        val homeAppButtons = homeAppButtonPreferences.exportState()
        return ManagerSettingsExportFile(
            settings = snapshot.copy(
                appLanguage = AppLocale.selected.value,
                customBundles = bundles.ifEmpty { null },
                homeAppButtons = homeAppButtons
            ).restrictedTo(sections),
            selections = if (SettingsSection.PATCH_SELECTIONS in sections) allSelectionsExportFile() else null
        )
    }

    fun exportManagerSettings(target: Uri) = viewModelScope.launch {
        messages.reportingFailure(
            R.string.settings_system_export_manager_settings_fail,
            "Failed to export manager settings"
        ) {
            val exportFile = managerSettingsExportFile()
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    settingsJson.encodeToStream(exportFile, output)
                }
            }

            messages.show(R.string.settings_system_export_manager_settings_success)
        }
    }

    /**
     * Export patch selections and options for a specific package+bundle combination.
     */
    fun exportPackageBundleData(
        packageName: String,
        bundleUid: Int,
        bundleName: String?,
        target: Uri
    ) = viewModelScope.launch {
        messages.reportingFailure(R.string.settings_system_export_source_data_fail, "Failed to export source data") {
            val (selections, optionsData) = withContext(Dispatchers.IO) {
                val patchList = patchSelectionRepository.exportForPackageAndBundle(
                    packageName,
                    bundleUid
                )

                val rawOptions = patchOptionsRepository.exportOptionsForBundle(
                    packageName = packageName,
                    bundleUid = bundleUid
                )

                val optionsData = if (rawOptions.isNotEmpty()) {
                    mapOf(packageName to rawOptions)
                } else {
                    emptyMap()
                }

                mapOf(packageName to patchList) to optionsData
            }

            val muted = withContext(Dispatchers.IO) {
                bundleUid in sourceMuteRepository.getMutedFor(packageName)
            }

            val exportFile = PatchBundleDataExportFile(
                bundleUid = bundleUid,
                bundleName = bundleName,
                bundleSource = patchBundleRepository.getEndpointForUid(bundleUid),
                exportDate = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()),
                selections = selections,
                options = optionsData,
                mutedPackages = listOf(packageName).takeIf { muted }
            )

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    json.encodeToStream(exportFile, output)
                }
            }

            messages.show(R.string.settings_system_export_source_data_success)
        }
    }

    /**
     * Export all patch selections and options across all bundles into a single file.
     */
    fun exportAllSelections(target: Uri) = viewModelScope.launch {
        messages.reportingFailure(R.string.settings_system_export_source_data_fail, "Failed to export selections") {
            val exportFile = allSelectionsExportFile()

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    json.encodeToStream(exportFile, output)
                }
            }

            messages.show(R.string.settings_system_export_source_data_success)
        }
    }

    /** Every source's patch selections, options and kept apps, and every set, as one export. */
    private suspend fun allSelectionsExportFile(): AllSelectionsExportFile {
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

    /**
     * Filename for the all-selections export file.
     */
    fun getAllSelectionsExportFileName(): String =
        FilenameUtils.timestamped("morphe_all_selections.json")

    /**
     * Import patch selections and options from a file.
     * Supports both [PatchBundleDataExportFile] (single bundle) and
     * [AllSelectionsExportFile] (all bundles) formats, detected automatically.
     *
     * In [PatchBundleRepository.ImportMode.Merge] mode existing selections are preserved
     * and new ones are added on top. In [PatchBundleRepository.ImportMode.Replace] mode the
     * imported state fully overwrites the current state: bundles absent from the backup have
     * their selections and options cleared.
     */
    fun importAllSelections(
        source: Uri,
        mode: PatchBundleRepository.ImportMode = PatchBundleRepository.ImportMode.Merge,
    ) = viewModelScope.launch {
        messages.reportingFailure(R.string.settings_system_import_source_data_fail, "Failed to import selections") {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(source)!!.use { it.readBytes() }
            }

            // Detect format by checking for "bundles" key
            val isAllSelections = withContext(Dispatchers.IO) {
                val element = json.decodeFromString<kotlinx.serialization.json.JsonObject>(bytes.decodeToString())
                element.containsKey("bundles")
            }

            // A file of one source's selections carries no sets
            val (bundleFiles, sets) = withContext(Dispatchers.IO) {
                if (isAllSelections) {
                    json.decodeFromString<AllSelectionsExportFile>(bytes.decodeToString()).let { it.bundles to it.sets }
                } else {
                    listOf(json.decodeFromString<PatchBundleDataExportFile>(bytes.decodeToString())) to null
                }
            }

            importSelectionBundles(bundleFiles, sets, mode)

            messages.show(R.string.settings_system_import_source_data_success)
        }
    }

    /**
     * Applies exported selections and [sets] onto this device's sources, the way
     * [importAllSelections] describes for [mode]. Null [sets], from a backup older than sets, leave
     * the sets on the device alone.
     */
    private suspend fun importSelectionBundles(
        bundleFiles: List<PatchBundleDataExportFile>,
        sets: List<SelectionSetExportFile>?,
        mode: PatchBundleRepository.ImportMode
    ) {
        withContext(Dispatchers.IO) {
            // Remap exported UIDs to current-device UIDs. Bundle UIDs are randomly generated
            // per device so the raw exported value only matches on the origin device
            val remapped = bundleFiles.map { file -> file to localUid(file.bundleUid, file.bundleSource) }
            val incomingUids = remapped
                .map { it.second }
                .filter { patchBundleRepository.isUidLoaded(it) }
                .toSet()

            // Replace mode: clear selections/options for bundles that currently have data
            // but are not represented in the backup
            if (mode == PatchBundleRepository.ImportMode.Replace) {
                val existingUids = (
                        patchSelectionRepository.getAllBundleUids() +
                                sourceMuteRepository.getAllBundleUids()
                        ).toSet()
                (existingUids - incomingUids).forEach { uid ->
                    patchSelectionRepository.import(uid, emptyMap())
                    patchOptionsRepository.resetOptionsForPatchBundle(uid)
                    sourceMuteRepository.resetForBundle(uid)
                }
            }

            remapped.forEach { (exportFile, bundleUid) ->
                // Skip if the bundle is not loaded - inserting with an unknown UID would
                // violate the patch_selections FK constraint on patch_bundles.uid
                if (!patchBundleRepository.isUidLoaded(bundleUid)) return@forEach

                when (mode) {
                    PatchBundleRepository.ImportMode.Replace -> {
                        // Full per-bundle replace: reset first, then load from backup
                        patchSelectionRepository.import(bundleUid, exportFile.selections)
                        patchOptionsRepository.resetOptionsForPatchBundle(bundleUid)
                        exportFile.options?.forEach { (packageName, packageOptions) ->
                            patchOptionsRepository.importOptionsForBundle(
                                packageName = packageName,
                                bundleUid = bundleUid,
                                options = packageOptions
                            )
                        }
                        sourceMuteRepository.importForBundle(
                            bundleUid,
                            exportFile.mutedPackages.orEmpty(),
                            exportFile.knownApps
                        )
                    }
                    PatchBundleRepository.ImportMode.Merge -> {
                        exportFile.selections.forEach { (packageName, patchList) ->
                            patchSelectionRepository.importForPackageAndBundle(
                                packageName = packageName,
                                bundleUid = bundleUid,
                                patches = patchList
                            )
                        }
                        exportFile.options?.forEach { (packageName, packageOptions) ->
                            patchOptionsRepository.importOptionsForBundle(
                                packageName = packageName,
                                bundleUid = bundleUid,
                                options = packageOptions
                            )
                        }
                        sourceMuteRepository.mergeForBundle(
                            bundleUid,
                            exportFile.mutedPackages.orEmpty(),
                            exportFile.knownApps
                        )
                    }
                }
            }

            if (sets != null) importSets(sets, mode)
        }
    }

    /**
     * Restores [sets], each on the sources this device has of it. In Replace mode the sets on the
     * device go first. A set none of whose sources are here is left out rather than kept empty.
     */
    private suspend fun importSets(sets: List<SelectionSetExportFile>, mode: PatchBundleRepository.ImportMode) {
        if (mode == PatchBundleRepository.ImportMode.Replace) selectionSetRepository.reset()

        sets.forEach { set ->
            // A set refers to its sources, so one this device does not have cannot be kept
            val sources = set.sources.mapNotNull { source ->
                val uid = localUid(source.bundleUid, source.bundleSource)
                if (patchBundleRepository.isUidLoaded(uid)) uid to source else null
            }
            if (sources.isEmpty() && set.sources.isNotEmpty()) return@forEach

            selectionSetRepository.import(
                StoredSelectionSet(
                    packageName = set.packageName,
                    name = set.name,
                    patches = sources.associate { (uid, source) -> uid to source.patches },
                    options = sources.mapNotNull { (uid, source) -> source.options?.let { uid to it } }.toMap()
                )
            )
        }
    }

    /** The uid on this device of a source exported as [bundleUid] from [bundleSource]. */
    private fun localUid(bundleUid: Int, bundleSource: String?): Int =
        bundleSource?.let { patchBundleRepository.resolveUidForEndpoint(it) } ?: bundleUid

    /**
     * Get filename for package+bundle data export.
     */
    fun getPackageBundleDataExportFileName(packageName: String, bundleUid: Int, bundleName: String?): String {
        val bundle = bundleName?.let { FilenameUtils.sanitize(it).take(20) }?.ifEmpty { null } ?: "bundle_$bundleUid"
        val pkg = packageName.substringAfterLast('.').take(15)
        return FilenameUtils.timestamped("morphe_${bundle}_${pkg}.json")
    }

    val debugLogFileName: String
        get() = FilenameUtils.timestamped("morphe_logcat.log")

    /**
     * Writes the debug log content to [writer]. Returns the logcat exit code.
     * Must be called from within a [Dispatchers.IO] context.
     * Shared by [exportDebugLogs].
     */
    private suspend fun writeDebugLogContent(writer: BufferedWriter): Int = withContext(Dispatchers.IO) {
        writer.write("=== Morphe Manager Debug Log ===\n")
        writer.write("Date       : ${LocalDateTime.now()}\n")
        writer.write("Version    : ${BuildConfig.VERSION_NAME}\n")

        writer.write("\n--- Device ---\n")
        writer.write("Model      : ${Build.MANUFACTURER} ${Build.MODEL}\n")
        writer.write("Android    : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        writer.write("ABI        : ${Build.SUPPORTED_ABIS.joinToString()}\n")
        writer.write("Locale     : ${Locale.getDefault().toLanguageTag()}\n")

        val stats = app.deviceStats()
        val toMb = ::bytesToMebibytes

        writer.write("\n--- Memory ---\n")
        if (stats == null) {
            writer.write("Unavailable\n")
        } else {
            writer.write("RAM avail  : ${toMb(stats.ramAvailable)} MB / ${toMb(stats.ramTotal)} MB\n")
            writer.write("Low memory : ${stats.lowMemory}\n")
            writer.write("Low mem thr: ${toMb(stats.lowMemoryThreshold)} MB\n")
        }

        writer.write("\n--- Storage ---\n")
        if (stats != null) {
            writer.write("Internal   : ${formatBytesForReport(stats.storageAvailable)} free / ${formatBytesForReport(stats.storageTotal)} total\n")
        }
        var sdCardIndex = 1
        app.externalStorageVolumes().forEach { (isPrimary, root) ->
            if (isPrimary) {
                writer.write("External   : ${formatBytesForReport(root.freeSpace)} free / ${formatBytesForReport(root.totalSpace)} total\n")
            } else {
                writer.write("SD Card $sdCardIndex  : ${formatBytesForReport(root.freeSpace)} free / ${formatBytesForReport(root.totalSpace)} total\n")
                sdCardIndex++
            }
        }

        writer.write("\n--- Environment ---\n")
        // Read without asking, as su would raise the root prompt and wait for it unanswered
        writer.write("Root access: ${Shell.isAppGrantedRoot() ?: "not checked"}\n")

        writer.write("\n=== Logcat ===\n\n")

        // Filter logs by current process UID to include only Morphe Manager logs
        val logcat = ProcessBuilder("logcat", "-d", "--uid=${app.applicationInfo.uid}")
            .redirectErrorStream(true)
            .start()
        logcat.inputStream.bufferedReader().useLines { lines -> lines.forEach { writer.write("$it\n") } }
        logcat.waitFor()
    }

    fun exportDebugLogs(target: Uri) = viewModelScope.launch {
        val exitCode = try {
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target)!!.bufferedWriter().use { writer ->
                    writeDebugLogContent(writer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Got exception while exporting logs", e)
            messages.show(R.string.settings_system_export_debug_logs_export_failed)
            return@launch
        }

        if (exitCode == 0)
            messages.show(R.string.settings_system_export_debug_logs_export_success)
        else {
            messages.show(app.getString(R.string.settings_system_export_debug_logs_export_read_failed).format(exitCode))
        }
    }

    override fun onCleared() {
        cancelKeystoreImport()
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

        private val knownPasswords = arrayOf("Morphe", "s3cur3p@ssw0rd")
        private val aliases = arrayOf(KeystoreManager.DEFAULT, "alias", "Morphe Key")
    }
}
