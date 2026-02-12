package app.morphe.manager.ui.viewmodel

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
    val options: Map<String, Map<String, Map<String, String>>>
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
     * Export patch selections and options for a specific bundle
     */
    fun exportPatchBundleData(bundleUid: Int, bundleName: String?, target: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_export_bundle_data_fail, "Failed to export bundle data") {
            val (selections, optionsData) = withContext(Dispatchers.IO) {
                val selections = patchSelectionRepository.export(bundleUid)

                // Get options directly from database for all packages in selections
                val optionsData = mutableMapOf<String, Map<String, Map<String, String>>>()

                selections.keys.forEach { packageName ->
                    // Get raw options from database for this package/bundle
                    val rawOptions = patchOptionsRepository.getOptionsForBundle(
                        packageName = packageName,
                        bundleUid = bundleUid,
                        bundlePatchInfo = emptyMap() // Empty map to get raw serialized values
                    )

                    if (rawOptions.isNotEmpty()) {
                        // Convert to simple string format for JSON export
                        val serializedOptions = rawOptions.mapValues { (_, patchOptions) ->
                            patchOptions.mapValues { (_, value) ->
                                // Serialize value to string representation
                                when (value) {
                                    null -> "null"
                                    is Boolean -> value.toString()
                                    is Number -> value.toString()
                                    is String -> value
                                    is List<*> -> value.joinToString("|")
                                    else -> value.toString()
                                }
                            }
                        }
                        optionsData[packageName] = serializedOptions
                    }
                }

                selections to optionsData
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

            app.toast(app.getString(R.string.settings_system_export_bundle_data_success))
        }
    }

    /**
     * Import patch selections and options for a specific bundle
     */
    fun importPatchBundleData(targetBundleUid: Int, source: Uri) = viewModelScope.launch {
        uiSafe(app, R.string.settings_system_import_bundle_data_fail, "Failed to import bundle data") {
            val exportFile = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(source)!!.use {
                    json.decodeFromStream<PatchBundleDataExportFile>(it)
                }
            }

            withContext(Dispatchers.IO) {
                // Import selections
                patchSelectionRepository.import(targetBundleUid, exportFile.selections)

                // Import options for each package
                exportFile.options.forEach { (packageName, packageOptions) ->
                    // Convert string values back to proper types
                    val deserializedOptions = packageOptions.mapValues { (_, patchOptions) ->
                        patchOptions.mapValues { (_, value) ->
                            // Parse string back to appropriate type
                            when {
                                value == "null" -> null
                                value == "true" -> true
                                value == "false" -> false
                                value.contains("|") -> value.split("|").map { item ->
                                    when {
                                        item.toIntOrNull() != null -> item.toInt()
                                        item.toLongOrNull() != null -> item.toLong()
                                        item.toFloatOrNull() != null -> item.toFloat()
                                        item == "true" -> true
                                        item == "false" -> false
                                        else -> item
                                    }
                                }
                                value.toIntOrNull() != null -> value.toInt()
                                value.toLongOrNull() != null -> value.toLong()
                                value.toFloatOrNull() != null -> value.toFloat()
                                value.toDoubleOrNull() != null -> value.toDouble()
                                else -> value
                            }
                        }
                    }

                    patchOptionsRepository.saveOptionsForBundle(
                        packageName = packageName,
                        bundleUid = targetBundleUid,
                        patchOptions = deserializedOptions
                    )
                }
            }

            app.toast(app.getString(R.string.settings_system_import_bundle_data_success))
        }
    }

    /**
     * Get filename for bundle data export
     */
    fun getBundleDataExportFileName(bundleUid: Int, bundleName: String?): String {
        val time = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
        val name = bundleName?.replace(" ", "_")?.take(20) ?: "bundle_$bundleUid"
        return "morphe_${name}_$time.json"
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
                    val consumer = Redirect.Consume { flow ->
                        flow
                            .onEach { line ->
                                writer.write("$line\n")
                            }
                            .flowOn(Dispatchers.IO)
                            .collect { }
                    }

                    process("logcat", "-d", stdout = consumer).resultCode
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
        else
            app.toast(app.getString(R.string.settings_system_export_debug_logs_export_read_failed, exitCode))
    }

    override fun onCleared() {
        super.onCleared()
        cancelKeystoreImport()
    }

    private companion object {
        // Reusable Json instances to avoid redundant creation
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true // Make exports human-readable
        }

        val knownPasswords = arrayOf("Morphe", "s3cur3p@ssw0rd")
        val aliases = arrayOf(KeystoreManager.DEFAULT, "alias", "Morphe Key")
    }
}
