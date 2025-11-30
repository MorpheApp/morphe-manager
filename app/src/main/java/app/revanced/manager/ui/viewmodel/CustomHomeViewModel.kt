package app.revanced.manager.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.repository.DownloadedAppRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.util.PM
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CustomHomeViewModel(
    private val app: Application,
    private val patchBundleRepository: PatchBundleRepository,
    private val downloadedAppRepository: DownloadedAppRepository,
    private val pm: PM
) : ViewModel() {

    private val selectAppWithDownloaderChannel = Channel<String>()
    val selectAppWithDownloaderFlow = selectAppWithDownloaderChannel.receiveAsFlow()

    private val selectAppFromStorageChannel = Channel<String>()
    val selectAppFromStorageFlow = selectAppFromStorageChannel.receiveAsFlow()

    private suspend fun suggestedVersion(packageName: String) =
        patchBundleRepository.suggestedVersions.first()[packageName]

    fun selectAppWithDownloader(packageName: String) = viewModelScope.launch {
        selectAppWithDownloaderChannel.send(packageName)
    }

    fun selectAppFromStorage(packageName: String) = viewModelScope.launch {
        selectAppFromStorageChannel.send(packageName)
    }
}