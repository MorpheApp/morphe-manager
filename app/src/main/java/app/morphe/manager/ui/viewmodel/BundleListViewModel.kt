package app.morphe.manager.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.toast
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class BundleListViewModel : ViewModel(), KoinComponent {
    private val app: Application = get()
    private val patchBundleRepository: PatchBundleRepository = get()
    var isRefreshing by mutableStateOf(false)
        private set

    val sources = patchBundleRepository.sources.onEach {
        isRefreshing = false
    }

    fun delete(src: PatchBundleSource) =
        viewModelScope.launch { patchBundleRepository.remove(src) }

    fun update(src: PatchBundleSource) = viewModelScope.launch {
        if (src !is RemotePatchBundle) return@launch

        patchBundleRepository.update(src, showToast = true)
    }

    fun disable(src: PatchBundleSource) =
        viewModelScope.launch {
            patchBundleRepository.disable(src)
            if (src.enabled) {
                showDisabledToast(listOf(src))
            } else {
                showEnabledToast(listOf(src))
            }
        }

    private fun showDisabledToast(targets: List<PatchBundleSource>) {
        app.toast(app.resources.getQuantityString(R.plurals.sources_dialog_disabled_toast, targets.size, targets.size))
    }

    private fun showEnabledToast(targets: List<PatchBundleSource>) {
        app.toast(app.resources.getQuantityString(R.plurals.sources_dialog_enabled_toast, targets.size, targets.size))
    }
}
