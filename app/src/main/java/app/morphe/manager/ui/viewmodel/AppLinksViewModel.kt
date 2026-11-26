/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.ui.screen.shared.UserMessages
import kotlinx.coroutines.launch

/**
 * Changes to which web links an app opens. Applied here rather than in the page asking for them,
 * so closing it does not cut a change to every link off with only some of them switched.
 */
class AppLinksViewModel(
    private val appLinksManager: AppLinksManager,
    private val messages: UserMessages
) : ViewModel() {
    // Keyed by app, as the pages showing them may stand for different ones
    private var pending by mutableStateOf(emptyMap<String, Set<String>>())

    /** The domains of [packageName] whose change is still being applied. */
    fun pendingDomains(packageName: String): Set<String> = pending[packageName].orEmpty()

    /** Selects or deselects every link of [packageName], [domains] being the ones it changes. */
    fun setAllSelected(
        packageName: String,
        domains: Set<String>,
        selected: Boolean,
        @StringRes successMessage: Int,
        @StringRes failureMessage: Int,
        onChanged: () -> Unit
    ) = change(packageName, domains, successMessage, failureMessage, onChanged) {
        appLinksManager.setAllDomainsSelected(packageName, selected)
    }

    fun setSelected(packageName: String, domain: String, selected: Boolean, onChanged: () -> Unit) =
        change(packageName, setOf(domain), null, R.string.app_links_change_failed, onChanged) {
            appLinksManager.setDomainsSelected(packageName, listOf(domain), selected)
        }

    // Marked before launching, so a second tap in the same frame starts nothing. The rows show the
    // status read once the change lands rather than what was asked for, which [onChanged] reads
    private fun change(
        packageName: String,
        domains: Set<String>,
        @StringRes successMessage: Int?,
        @StringRes failureMessage: Int,
        onChanged: () -> Unit,
        apply: suspend () -> Boolean
    ) {
        val current = pendingDomains(packageName)
        if (domains.isEmpty() || domains.any { it in current }) return
        pending = pending + (packageName to current + domains)
        viewModelScope.launch {
            val success = apply()
            pending = pending + (packageName to pendingDomains(packageName) - domains)
            onChanged()
            (if (success) successMessage else failureMessage)?.let { messages.show(it) }
        }
    }
}
