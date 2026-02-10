package app.morphe.manager.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.network.api.MorpheAPI
import app.morphe.manager.network.dto.MorpheAsset
import app.morphe.manager.network.utils.getOrThrow
import app.morphe.manager.util.uiSafe
import kotlinx.coroutines.launch

class ChangelogsViewModel(
    private val api: MorpheAPI,
    private val app: Application,
) : ViewModel() {
    var releaseInfo: MorpheAsset? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            uiSafe(app, R.string.changelog_download_fail, "Failed to download changelog") {
                releaseInfo = api.getLatestAppInfo().getOrThrow()
            }
        }
    }
}
