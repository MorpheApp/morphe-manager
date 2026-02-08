package app.revanced.manager.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.RemotePatchBundle
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.util.toast
import app.revanced.manager.util.mutableStateSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class BundleListViewModel : ViewModel(), KoinComponent {
    private val app: Application = get()
    private val patchBundleRepository: PatchBundleRepository = get()
    val patchCounts = patchBundleRepository.patchCountsFlow
    var isRefreshing by mutableStateOf(false)
        private set

    val sources = patchBundleRepository.sources.onEach {
        isRefreshing = false
    }
    val manualUpdateInfo = patchBundleRepository.manualUpdateInfo

    val selectedSources = mutableStateSetOf<Int>()

    fun refresh() = viewModelScope.launch {
        isRefreshing = true
        try {
            patchBundleRepository.updateCheck()
            val progressFlow = patchBundleRepository.bundleUpdateProgress
            val started = withTimeoutOrNull(250L) {
                progressFlow.filterNotNull().first()
            }
            if (started != null) {
                progressFlow.first { it == null }
            }
        } finally {
            isRefreshing = false
        }
    }

    private suspend fun getSelectedSources() = patchBundleRepository.sources
        .first()
        .filter { it.uid in selectedSources }
        .also {
            selectedSources.clear()
        }

    fun handleEvent(event: Event) {
        when (event) {
            Event.CANCEL -> selectedSources.clear()
            Event.DELETE_SELECTED -> viewModelScope.launch {
                patchBundleRepository.remove(*getSelectedSources().toTypedArray())
            }

            Event.UPDATE_SELECTED -> viewModelScope.launch {
                patchBundleRepository.update(
                    *getSelectedSources().filterIsInstance<RemotePatchBundle>().toTypedArray(),
                    showToast = true,
                )
            }
            Event.DISABLE_SELECTED -> viewModelScope.launch {
                val targets = getSelectedSources()
                if (targets.isEmpty()) return@launch

                val disabledTargets = targets.filter { it.enabled }
                val enabledTargets = targets.filterNot { it.enabled }
                patchBundleRepository.disable(*targets.toTypedArray())
                if (disabledTargets.isNotEmpty()) showDisabledToast(disabledTargets)
                if (enabledTargets.isNotEmpty()) showEnabledToast(enabledTargets)
            }
        }
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

    fun reorder(order: List<Int>) = viewModelScope.launch {
        patchBundleRepository.reorderBundles(order)
    }

    private fun showDisabledToast(targets: List<PatchBundleSource>) {
        app.toast(app.resources.getQuantityString(R.plurals.sources_dialog_disabled_toast, targets.size, targets.size))
    }

    private fun showEnabledToast(targets: List<PatchBundleSource>) {
        app.toast(app.resources.getQuantityString(R.plurals.sources_dialog_enabled_toast, targets.size, targets.size))
    }

    enum class Event {
        DELETE_SELECTED,
        UPDATE_SELECTED,
        DISABLE_SELECTED,
        CANCEL,
    }
}
