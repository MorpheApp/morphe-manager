/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.repository.StorageStats
import app.morphe.manager.domain.repository.StorageStatsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StorageManagementViewModel(
    private val storageStatsRepository: StorageStatsRepository
) : ViewModel() {

    val stats: StateFlow<StorageStats> = storageStatsRepository.stats

    fun refresh() = storageStatsRepository.refresh()

    fun clearHttpCache(onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(storageStatsRepository.clearHttpCache())
    }

    fun clearInstallerShareCache(onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(storageStatsRepository.clearInstallerShareCache())
    }

    fun clearPatcherWorkspace(onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(storageStatsRepository.clearPatcherWorkspace())
    }

    fun clearTemporary(onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(storageStatsRepository.clearTemporary())
    }

    fun clearAllCaches(onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(storageStatsRepository.clearAllCaches())
    }
}
