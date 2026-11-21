/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import androidx.compose.runtime.Immutable

/**
 * Where the settings lead out of themselves, handed down from the navigation that owns the routes,
 * so the settings page and its pages reach them the same way.
 *
 * @param openSubpage Opens a part of the settings as a page of its own.
 * @param openPatchSelections Opens the patch selections expert mode saves.
 * @param openLicenses Opens the open-source licenses the manager is built on.
 * @param openMiniGames Opens the mini-games.
 * @param startTour Restarts the onboarding tour, null while one is running.
 */
@Immutable
class SettingsNavigation(
    val openSubpage: (SettingsSubpage) -> Unit,
    val openPatchSelections: () -> Unit,
    val openLicenses: () -> Unit,
    val openMiniGames: () -> Unit,
    val startTour: (() -> Unit)?
)
