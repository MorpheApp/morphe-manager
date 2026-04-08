package app.morphe.manager.ui.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.manager.KeystoreManager
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.util.tag
import app.morphe.manager.util.toast
import app.morphe.manager.util.uiSafe
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.deleteExisting
import kotlin.io.path.inputStream

@Serializable
data class ManagerSettingsExportFile(
    val version: Int = 1,
    val settings: PreferencesManager.SettingsSnapshot
)

/**
 * Export file format for patch selections and options
 * This format stores selections (which patches are enabled) and their options (patch configuration)
 */
@Serializable
data class PatchBundleDataExportFile(
    val version: Int = 1,
    val bundleUid: Int,
    val bundleName: String? = null,
    val exportDate: String,
    // Map<PackageName, List<PatchName>>
    val selections: Map<String, List<String>>,
    // Map<PackageName, Map<PatchName, Map<OptionKey, OptionValue>>>
    val options: Map<String, Map<String, Map<String, String>>>?
)

/**
 * Export file format for all selections across all bundles.
 * Wraps a list of [PatchBundleDataExportFile] entries - one per bundle.
 */
@Serializable
data class AllSelectionsExportFile(
    val version: Int = 1,
    val exportDate: String,
    val bundles: List<PatchBundleDataExportFile>
)

@OptIn(ExperimentalSerializationApi::class)
class ImportExportViewModel(
    private val app: Application,
    private val keystoreManager: KeystoreManager,
    private val preferencesManager: PreferencesManager,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val patchOptionsRepository: PatchOptionsRepository
) : ViewModel() {
    private val contentResolver = app.contentResolver

    private var keystoreImportPath by mutableStateOf<Path?>(null)
    val showCredentialsDialog by derivedStateOf { keystoreImportPath != null }

    fun startKeystoreImport(content: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_import_keystore_failed, "Failed to import keystore") {
            val path = withContext(Dispatchers.IO) {
                File.createTempFile("signing", "ks", app.cacheDir).toPath().also {
                    Files.copy(
                        contentResolver.openInputStream(content)!!,
                        it,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

            // Try known aliases and passwords first
            aliases.forEach { alias ->
                knownPasswords.forEach { pass ->
                    if (tryKeystoreImport(alias, pass, path)) {
                        return@launch
                    }
                }
            }

            // If automatic import fails, prompt user for credentials
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
        path.inputStream().use { stream ->
            if (keystoreManager.import(alias, pass, stream)) {
                app.toast(app.getString(R.string.settings_system_import_keystore_success))
                cancelKeystoreImport()
                return true
            }
        }
        return false
    }

    fun canExport() = keystoreManager.hasKeystore()

    fun exportKeystore(target: Uri) = viewModelScope.launch {
        keystoreManager.export(contentResolver.openOutputStream(target)!!)
        app.toast(app.getString(R.string.settings_system_export_keystore_success))
    }

    fun importManagerSettings(source: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_import_manager_settings_fail, "Failed to import manager settings") {
            val exportFile = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(source)!!.use {
                    json.decodeFromStream<ManagerSettingsExportFile>(it)
                }
            }

            preferencesManager.importSettings(exportFile.settings)
            app.toast(app.getString(R.string.settings_system_import_manager_settings_success))
        }
    }

    fun exportManagerSettings(target: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_export_manager_settings_fail, "Failed to export manager settings") {
            val snapshot = preferencesManager.exportSettings()

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    json.encodeToStream(
                        ManagerSettingsExportFile(settings = snapshot),
                        output
                    )
                }
            }

            app.toast(app.getString(R.string.settings_system_export_manager_settings_success))
        }
    }

    /**
     * Export patch selections and options for a specific package+bundle combination
     */
    fun exportPackageBundleData(
        packageName: String,
        bundleUid: Int,
        bundleName: String?,
        target: Uri
    ) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_export_source_data_fail, "Failed to export source data") {
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

            val exportFile = PatchBundleDataExportFile(
                bundleUid = bundleUid,
                bundleName = bundleName,
                exportDate = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()),
                selections = selections,
                options = optionsData
            )

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    json.encodeToStream(exportFile, output)
                }
            }

            app.toast(app.getString(R.string.settings_system_export_source_data_success))
        }
    }

    /**
     * Export all patch selections and options across all bundles into a single file.
     */
    fun exportAllSelections(target: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_export_source_data_fail, "Failed to export selections") {
            val exportDate = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())

            val bundles = withContext(Dispatchers.IO) {
                val packagesByBundle = patchSelectionRepository.getAllBundleUids()

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
                        exportDate = exportDate,
                        selections = selections,
                        options = options.ifEmpty { null }
                    )
                }.filter { it.selections.isNotEmpty() }
            }

            val exportFile = AllSelectionsExportFile(
                exportDate = exportDate,
                bundles = bundles
            )

            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target, "wt")!!.use { output ->
                    json.encodeToStream(exportFile, output)
                }
            }

            app.toast(app.getString(R.string.settings_system_export_source_data_success))
        }
    }

    /**
     * Filename for the all-selections export file.
     */
    fun getAllSelectionsExportFileName(): String {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
        return "morphe_all_selections_$time.json"
    }

    /**
     * Import patch selections and options from a file.
     * Supports both [PatchBundleDataExportFile] (single bundle) and
     * [AllSelectionsExportFile] (all bundles) formats, detected automatically.
     * Merges into existing selections without clearing them first.
     */
    fun importAllSelections(source: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_import_source_data_fail, "Failed to import selections") {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(source)!!.use { it.readBytes() }
            }

            // Detect format by checking for "bundles" key
            val isAllSelections = withContext(Dispatchers.IO) {
                val element = json.decodeFromString<kotlinx.serialization.json.JsonObject>(bytes.decodeToString())
                element.containsKey("bundles")
            }

            val bundleFiles: List<PatchBundleDataExportFile> = withContext(Dispatchers.IO) {
                if (isAllSelections) {
                    json.decodeFromString<AllSelectionsExportFile>(bytes.decodeToString()).bundles
                } else {
                    listOf(json.decodeFromString<PatchBundleDataExportFile>(bytes.decodeToString()))
                }
            }

            withContext(Dispatchers.IO) {
                bundleFiles.forEach { exportFile ->
                    val bundleUid = exportFile.bundleUid
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
                }
            }

            app.toast(app.getString(R.string.settings_system_import_source_data_success))
        }
    }

    /**
     * Get filename for package+bundle data export
     */
    fun getPackageBundleDataExportFileName(packageName: String, bundleUid: Int, bundleName: String?): String {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
        val bundle = bundleName?.replace(" ", "_")?.take(20) ?: "bundle_$bundleUid"
        val pkg = packageName.substringAfterLast('.').take(15)
        return "morphe_${bundle}_${pkg}_$time.json"
    }

    val debugLogFileName: String
        get() {
            val time = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm").format(LocalDateTime.now())
            return "morphe_logcat_$time.log"
        }

    fun exportDebugLogs(target: Uri) = viewModelScope.launch {
        val exitCode = try {
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(target)!!.bufferedWriter().use { writer ->

                    val versionName = runCatching {
                        app.packageManager.getPackageInfo(app.packageName, 0).versionName
                    }.getOrDefault("unknown")

                    writer.write("=== Morphe Manager Debug Log ===\n")
                    writer.write("Date       : ${LocalDateTime.now()}\n")
                    writer.write("Version    : $versionName\n")

                    writer.write("\n--- Device ---\n")
                    writer.write("Model      : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                    writer.write("Android    : ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
                    writer.write("ABI        : ${android.os.Build.SUPPORTED_ABIS.joinToString()}\n")
                    writer.write("Locale     : ${java.util.Locale.getDefault().toLanguageTag()}\n")

                    writer.write("\n--- Memory ---\n")
                    val activityManager = app.getSystemService(ActivityManager::class.java)
                    val memInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
                    val toMb = { bytes: Long -> bytes / 1024 / 1024 }
                    writer.write("RAM avail  : ${toMb(memInfo.availMem)} MB / ${toMb(memInfo.totalMem)} MB\n")
                    writer.write("Low memory : ${memInfo.lowMemory}\n")
                    writer.write("Low mem thr: ${toMb(memInfo.threshold)} MB\n")

                    writer.write("\n--- Storage ---\n")
                    val internalDir = app.filesDir
                    val toMbL = { bytes: Long -> bytes / 1024 / 1024 }
                    writer.write("Internal   : ${toMbL(internalDir.freeSpace)} MB free / ${toMbL(internalDir.totalSpace)} MB total\n")
                    val externalDir = app.getExternalFilesDir(null)
                    if (externalDir != null) {
                        writer.write("External   : ${toMbL(externalDir.freeSpace)} MB free / ${toMbL(externalDir.totalSpace)} MB total\n")
                    }

                    writer.write("\n--- Environment ---\n")
                    val hasRoot = runCatching {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
                    }.getOrDefault(false)
                    writer.write("Root access: $hasRoot\n")

                    writer.write("\n=== Logcat ===\n\n")

                    val consumer = Redirect.Consume { flow ->
                        flow
                            .onEach { line -> writer.write("$line\n") }
                            .flowOn(Dispatchers.IO)
                            .collect { }
                    }

                    // Filter logs by current process UID to include only Morphe Manager logs
                    process("logcat", "-d", "--uid=${app.applicationInfo.uid}", stdout = consumer).resultCode
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Got exception while exporting logs", e)
            app.toast(app.getString(R.string.settings_system_export_debug_logs_export_failed))
            return@launch
        }

        if (exitCode == 0)
            app.toast(app.getString(R.string.settings_system_export_debug_logs_export_success))
        else {
            app.toast(app.getString(R.string.settings_system_export_debug_logs_export_read_failed).format(exitCode))
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelKeystoreImport()
    }

    private companion object {
        // Reusable JSON instances to avoid redundant creation
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true // Make exports human-readable
        }

        val knownPasswords = arrayOf("Morphe", "s3cur3p@ssw0rd")
        val aliases = arrayOf(KeystoreManager.DEFAULT, "alias", "Morphe Key")
    }
}
