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

@OptIn(ExperimentalSerializationApi::class)
class ImportExportViewModel(
    private val app: Application,
    private val keystoreManager: KeystoreManager,
    private val preferencesManager: PreferencesManager
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
            prettyPrint = false
        }

        val knownPasswords = arrayOf("Morphe", "s3cur3p@ssw0rd")
        val aliases = arrayOf(KeystoreManager.DEFAULT, "alias", "Morphe Key")
    }
}
