package app.revanced.manager.ui.viewmodel

import android.app.Application
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import app.morphe.manager.R
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.util.PM
import app.revanced.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.Locale

@OptIn(SavedStateHandleSaveableApi::class)
class AppSelectorViewModel(
    private val app: Application,
    private val pm: PM,
    fs: Filesystem,
    private val patchBundleRepository: PatchBundleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val inputFile = savedStateHandle.saveable(key = "inputFile") {
        File(
            fs.uiTempDir,
            "input.apk"
        ).also(File::delete)
    }
    val appList = pm.appList

    private val storageSelectionChannel = Channel<SelectedApp.Local>()
    val storageSelectionFlow = storageSelectionChannel.receiveAsFlow()

    val suggestedAppVersions = patchBundleRepository.suggestedVersions.flowOn(Dispatchers.Default)
    val bundleSuggestionsByApp =
        patchBundleRepository.bundleInfoFlow
            .combine(patchBundleRepository.suggestedVersionsByBundle) { bundleInfo, bundleVersions ->
                val result = mutableMapOf<String, MutableList<BundleVersionSuggestion>>()

                bundleInfo.forEach { (bundleUid, info) ->
                    val packageSupport = mutableMapOf<String, BundleSupportAccumulator>()

                    info.patches.forEach { patch ->
                        patch.compatiblePackages?.forEach { compatible ->
                            val accumulator =
                                packageSupport.getOrPut(compatible.packageName) {
                                    BundleSupportAccumulator(mutableSetOf(), false)
                                }
                            val versions = compatible.versions
                            if (versions.isNullOrEmpty()) {
                                accumulator.supportsAllVersions = true
                            } else {
                                accumulator.versions += versions
                            }
                        }
                    }

                    packageSupport.forEach { (packageName, support) ->
                        val recommended = bundleVersions[bundleUid]?.get(packageName)
                        if (
                            recommended == null &&
                            support.versions.isEmpty() &&
                            !support.supportsAllVersions
                        ) return@forEach

                        val otherVersions = support.versions
                            .filterNot { recommended.equals(it, ignoreCase = true) }
                            .sorted()

                        val suggestions = result.getOrPut(packageName) { mutableListOf() }
                        suggestions += BundleVersionSuggestion(
                            bundleUid = bundleUid,
                            bundleName = info.name,
                            recommendedVersion = recommended,
                            otherSupportedVersions = otherVersions,
                            supportsAllVersions = support.supportsAllVersions
                        )
                    }
                }

                result.mapValues { (_, values) ->
                    values.sortedBy { it.bundleName.lowercase(Locale.ROOT) }
                }
            }
            .flowOn(Dispatchers.Default)

    var nonSuggestedVersionDialogSubject by mutableStateOf<SelectedApp.Local?>(null)
        private set

    fun loadLabel(app: PackageInfo?) = with(pm) { app?.label() ?: "Not installed" }

    fun dismissNonSuggestedVersionDialog() {
        nonSuggestedVersionDialogSubject = null
    }

    fun handleStorageResult(uri: Uri) = viewModelScope.launch {
        val selectedApp = withContext(Dispatchers.IO) {
            loadSelectedFile(uri)
        }

        if (selectedApp == null) {
            app.toast(app.getString(R.string.failed_to_load_apk))
            return@launch
        }

        if (patchBundleRepository.isVersionAllowed(selectedApp.packageName, selectedApp.version)) {
            storageSelectionChannel.send(selectedApp)
        } else {
            nonSuggestedVersionDialogSubject = selectedApp
        }
    }

    private fun loadSelectedFile(uri: Uri) =
        app.contentResolver.openInputStream(uri)?.use { stream ->
            with(inputFile) {
                delete()
                Files.copy(stream, toPath())

                pm.getPackageInfo(this)?.let { packageInfo ->
                    SelectedApp.Local(
                        packageName = packageInfo.packageName,
                        version = packageInfo.versionName!!,
                        file = this,
                        temporary = true
                    )
                }
            }
        }
}

data class BundleVersionSuggestion(
    val bundleUid: Int,
    val bundleName: String,
    val recommendedVersion: String?,
    val otherSupportedVersions: List<String>,
    val supportsAllVersions: Boolean
)

private data class BundleSupportAccumulator(
    val versions: MutableSet<String>,
    var supportsAllVersions: Boolean
)
