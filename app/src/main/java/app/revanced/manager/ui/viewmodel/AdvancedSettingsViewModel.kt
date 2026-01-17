package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.domain.installer.RootInstaller
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.manager.hideInstallerComponent
import app.revanced.manager.domain.manager.showInstallerComponent
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.model.PatchSelectionActionKey
import app.revanced.manager.util.simpleMessage
import app.revanced.manager.util.tag
import app.revanced.manager.util.toast
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AdvancedSettingsViewModel(
    val prefs: PreferencesManager,
    private val app: Application,
    private val patchBundleRepository: PatchBundleRepository,
    private val installerManager: InstallerManager,
    private val rootInstaller: RootInstaller
) : ViewModel() {
    val hasOfficialBundle = patchBundleRepository.sources
        .map { sources -> sources.any { it.isDefault } }

    val debugLogFileName: String
        get() {
            val time = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm").format(LocalDateTime.now())

            return "morphe_logcat_$time.log"
        }

    fun setApiUrl(value: String) = viewModelScope.launch(Dispatchers.Default) {
        if (value == prefs.api.get()) return@launch

        prefs.api.update(value)
        patchBundleRepository.reloadApiBundles()
    }

    fun setGitHubPat(value: String) = viewModelScope.launch(Dispatchers.Default) {
        prefs.gitHubPat.update(value.trim())
    }

    fun setIncludeGitHubPatInExports(enabled: Boolean) = viewModelScope.launch(Dispatchers.Default) {
        prefs.includeGitHubPatInExports.update(enabled)
    }

    fun exportDebugLogs(target: Uri) = viewModelScope.launch {
        val exitCode = try {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(target)!!.bufferedWriter().use { writer ->
                    val consumer = Redirect.Consume { flow ->
                        flow.onEach {
                            writer.write("${it}\n")
                        }.flowOn(Dispatchers.IO).collect()
                    }

                    process("logcat", "-d", stdout = consumer).resultCode
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Got exception while exporting logs", e)
            app.toast(app.getString(R.string.debug_logs_export_failed))
            return@launch
        }

        if (exitCode == 0)
            app.toast(app.getString(R.string.debug_logs_export_success))
        else
            app.toast(app.getString(R.string.debug_logs_export_read_failed, exitCode))
    }

    fun setPrimaryInstaller(token: InstallerManager.Token) = viewModelScope.launch(Dispatchers.Default) {
        installerManager.updatePrimaryToken(token)
        val fallback = installerManager.getFallbackToken()
        if (fallback != InstallerManager.Token.None && tokensEqual(fallback, token)) {
            installerManager.updateFallbackToken(InstallerManager.Token.None)
        }
    }

    // TODO: Remove this sometime
    fun setFallbackInstaller(token: InstallerManager.Token) = viewModelScope.launch(Dispatchers.Default) {
        val primary = installerManager.getPrimaryToken()
        val target = if (token != InstallerManager.Token.None && tokensEqual(primary, token)) {
            InstallerManager.Token.None
        } else token
        installerManager.updateFallbackToken(target)
    }

    fun setPatchedAppExportFormat(value: String) = viewModelScope.launch(Dispatchers.Default) {
        prefs.patchedAppExportFormat.update(value)
    }

    fun resetPatchedAppExportFormat() = viewModelScope.launch(Dispatchers.Default) {
        prefs.patchedAppExportFormat.update(prefs.patchedAppExportFormat.default)
    }

    fun setPatchSelectionActionOrder(order: List<PatchSelectionActionKey>) =
        viewModelScope.launch(Dispatchers.Default) {
            val serialized = order.joinToString(",") { it.storageId }
            prefs.patchSelectionActionOrder.update(serialized)
        }

    fun setPatchSelectionHiddenActions(hidden: Set<String>) =
        viewModelScope.launch(Dispatchers.Default) {
            prefs.patchSelectionHiddenActions.update(hidden)
        }

    fun restoreOfficialBundle() = viewModelScope.launch(Dispatchers.Default) {
        val hasBundle = patchBundleRepository.sources.first().any { it.isDefault }
        if (hasBundle) {
            withContext(Dispatchers.Main) {
                app.toast(app.getString(R.string.restore_official_bundle_already))
            }
            return@launch
        }

        runCatching {
            patchBundleRepository.restoreDefaultBundle()
            patchBundleRepository.refreshDefaultBundle()
        }.onSuccess {
            withContext(Dispatchers.Main) {
                app.toast(app.getString(R.string.restore_official_bundle_success))
            }
        }.onFailure { error ->
            Log.e(tag, "Failed to restore official bundle", error)
            val message = error.simpleMessage() ?: error.javaClass.simpleName.orEmpty()
            withContext(Dispatchers.Main) {
                app.toast(app.getString(R.string.restore_official_bundle_failed, message))
            }
        }
    }

    fun addCustomInstaller(component: ComponentName, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch(Dispatchers.Default) {
            val added = installerManager.addCustomInstaller(component)
            if (added) {
                prefs.showInstallerComponent(component)
            }
            withContext(Dispatchers.Main) {
                onResult(added)
            }
        }

    fun removeCustomInstaller(component: ComponentName, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch(Dispatchers.Default) {
        val removed = installerManager.removeCustomInstaller(component)
        if (removed) {
            prefs.hideInstallerComponent(component)
            val removedPackage = component.packageName
            val currentPrimary = installerManager.getPrimaryToken()
            val currentFallback = installerManager.getFallbackToken()
            val primaryMatchesRemoved =
                currentPrimary is InstallerManager.Token.Component &&
                    currentPrimary.componentName.packageName == removedPackage
            val fallbackMatchesRemoved =
                currentFallback is InstallerManager.Token.Component &&
                    currentFallback.componentName.packageName == removedPackage

            if (primaryMatchesRemoved) {
                installerManager.updatePrimaryToken(InstallerManager.Token.Internal)
            }
            if (fallbackMatchesRemoved) {
                installerManager.updateFallbackToken(InstallerManager.Token.None)
            }

            val componentAvailable = installerManager.isComponentAvailable(component)
            if (!componentAvailable) {
                if (currentPrimary is InstallerManager.Token.Component &&
                    currentPrimary.componentName == component
                ) {
                    installerManager.updatePrimaryToken(InstallerManager.Token.Internal)
                }
                if (currentFallback is InstallerManager.Token.Component &&
                    currentFallback.componentName == component
                ) {
                    installerManager.updateFallbackToken(InstallerManager.Token.None)
                }
            }
        }
            withContext(Dispatchers.Main) {
                onResult(removed)
            }
        }

    fun searchInstallerEntries(
        query: String,
        target: InstallerManager.InstallTarget
    ): List<InstallerManager.Entry> = installerManager.searchInstallerEntries(query, target)
}

private fun tokensEqual(a: InstallerManager.Token, b: InstallerManager.Token): Boolean = when {
    a === b -> true
    a is InstallerManager.Token.Component && b is InstallerManager.Token.Component ->
        a.componentName == b.componentName
    else -> false
}
