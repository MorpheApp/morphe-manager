package app.revanced.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.DownloadedAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.domain.repository.PatchSelectionRepository
import app.revanced.manager.ui.model.SelectedApp
import app.revanced.manager.ui.model.navigation.SelectedApplicationInfo
import app.revanced.manager.util.PatchSelection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val patchBundleRepository: PatchBundleRepository,
    private val patchSelectionRepository: PatchSelectionRepository,
    private val downloadedAppRepository: DownloadedAppRepository,
    private val app: Application,
    val prefs: PreferencesManager
) : ViewModel() {
    private val appSelectChannel = Channel<SelectedApplicationInfo.ViewModelParams>()
    val appSelectFlow = appSelectChannel.receiveAsFlow()

    private suspend fun suggestedVersion(packageName: String) =
        patchBundleRepository.suggestedVersions.first()[packageName]

    private suspend fun findDownloadedApp(app: SelectedApp): SelectedApp.Local? {
        if (app !is SelectedApp.Search) return null

        val suggestedVersion = suggestedVersion(app.packageName) ?: return null

        val downloadedApp =
            downloadedAppRepository.get(app.packageName, suggestedVersion, markUsed = true)
                ?: return null
        return SelectedApp.Local(
            downloadedApp.packageName,
            downloadedApp.version,
            downloadedAppRepository.getApkFileForApp(downloadedApp),
            false
        )
    }

    fun selectApp(app: SelectedApp, patches: PatchSelection? = null) = viewModelScope.launch {
        val resolved = findDownloadedApp(app) ?: app
        appSelectChannel.send(
            SelectedApplicationInfo.ViewModelParams(
                app = resolved,
                patches = patches
            )
        )
    }

    fun selectApp(app: SelectedApp) = selectApp(app, null)

    fun selectApp(packageName: String, patches: PatchSelection? = null) = viewModelScope.launch {
        selectApp(SelectedApp.Search(packageName, suggestedVersion(packageName)), patches)
    }

    fun selectApp(packageName: String) = selectApp(packageName, null)

    fun selectAppWithSourceSelection(packageName: String, patches: PatchSelection? = null) = viewModelScope.launch {
        val app = SelectedApp.Search(packageName, suggestedVersion(packageName))
        appSelectChannel.send(
            SelectedApplicationInfo.ViewModelParams(
                app = app,
                patches = patches,
                profileId = null,
                requiresSourceSelection = false
            )
        )
    }

    fun selectAppWithSourceSelection(packageName: String) = selectAppWithSourceSelection(packageName, null)
}
