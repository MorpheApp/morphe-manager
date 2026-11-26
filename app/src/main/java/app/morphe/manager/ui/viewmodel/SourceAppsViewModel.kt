/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.repository.SourceMuteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Which apps each source is offered to, as the dialogs asking it read and change it.
 *
 * The dialogs often close the moment the answer is given, so the writes run here rather than in
 * them, where closing one would cut a list off half applied.
 */
class SourceAppsViewModel(private val sourceMuteRepository: SourceMuteRepository) : ViewModel() {
    /** The sources each app is kept from, keyed by app. */
    val keptFrom: Flow<Map<String, Set<Int>>> = sourceMuteRepository.mutedSources

    /** Apps left out on arrival and not looked at yet, see [SourceMuteRepository.unseenNewApps]. */
    val unseenNewApps: Flow<Map<Int, Set<String>>> = sourceMuteRepository.unseenNewApps

    /** Offers [bundleUid] to every one of [packageNames] again, or keeps it from all of them. */
    fun setOffered(bundleUid: Int, packageNames: Collection<String>, offered: Boolean) {
        viewModelScope.launch {
            if (offered) sourceMuteRepository.unmuteApps(bundleUid, packageNames)
            else sourceMuteRepository.muteApps(bundleUid, packageNames)
        }
    }

    /** Whether the apps [bundleUid] adds later come in on their own, [listedApps] being those it has now. */
    fun setBringsNewApps(bundleUid: Int, bringsNewApps: Boolean, listedApps: Set<String>) {
        viewModelScope.launch { sourceMuteRepository.setBringsNewApps(bundleUid, bringsNewApps, listedApps) }
    }

    fun markNewAppsSeen(bundleUid: Int) {
        viewModelScope.launch { sourceMuteRepository.markNewAppsSeen(bundleUid) }
    }
}
