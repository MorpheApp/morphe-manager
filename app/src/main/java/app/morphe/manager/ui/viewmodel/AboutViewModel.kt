/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.R
import app.morphe.manager.domain.repository.ManagerUpdateRepository
import app.morphe.manager.domain.repository.PeopleRepository
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.dto.AppUpdateCheck
import app.morphe.manager.util.MORPHE_GITHUB_URL
import app.morphe.manager.util.MORPHE_WEBSITE_URL
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One of the project's own pages, as the About dialog links to it. */
data class AboutLink(
    @StringRes val title: Int,
    val icon: ImageVector,
    val url: String,
)

class AboutViewModel(
    private val peopleRepository: PeopleRepository,
    private val managerUpdateRepository: ManagerUpdateRepository,
    private val prefs: PreferencesManager
) : ViewModel() {
    /** What the last check for a newer manager came back with, or null before the first one. */
    val updateCheck: StateFlow<AppUpdateCheck?> = managerUpdateRepository.lastCheck

    /**
     * Checks for a newer manager, so the About dialog can say the installed one is up to date.
     * Left to the checks the user starts themselves where they turned the automatic ones off.
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            if (prefs.autoUpdates.get().checksOnLaunch) managerUpdateRepository.refresh()
        }
    }

    val people: StateFlow<PeopleRepository.People?> = peopleRepository.people

    /** Whether the lists could neither be fetched nor read back from an earlier fetch. */
    var peopleUnavailable by mutableStateOf(false)
        private set

    fun loadPeople() {
        viewModelScope.launch {
            peopleUnavailable = !peopleRepository.refresh()
        }
    }

    companion object {
        val donateLink = AboutLink(
            title = R.string.about_link_donate,
            icon = MaterialSymbols.Rounded.Favorite,
            url = "$MORPHE_WEBSITE_URL/donate"
        )

        // Glyphs for what each page is rather than the marks of the services hosting them, which
        // the patched apps cannot ship and the two dialogs are meant to share
        val links: List<AboutLink> = listOf(
            AboutLink(
                title = R.string.about_link_website,
                icon = MaterialSymbols.Rounded.Public,
                url = MORPHE_WEBSITE_URL
            ),
            AboutLink(
                title = R.string.about_link_github,
                icon = MaterialSymbols.Rounded.Code,
                url = MORPHE_GITHUB_URL
            ),
            AboutLink(
                title = R.string.about_link_reddit,
                icon = MaterialSymbols.Rounded.Forum,
                url = "https://reddit.com/r/MorpheApp"
            ),
            AboutLink(
                title = R.string.about_link_translations,
                icon = MaterialSymbols.Rounded.Translate,
                url = "$MORPHE_WEBSITE_URL/translate"
            )
        )

        /** Where the supporters past the ones listed are, and every anonymous one. */
        const val SUPPORTERS_URL = "https://opencollective.com/morpheapp"
    }
}
