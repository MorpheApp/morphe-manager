/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.patcher.patch.BundleAppMetadata
import app.morphe.manager.ui.screen.home.AppCardContent
import app.morphe.manager.ui.screen.home.AppCardLayout
import app.morphe.manager.ui.screen.home.AppCardLine
import app.morphe.manager.ui.screen.home.AppCardUpdateButton
import app.morphe.manager.ui.screen.home.AppStateIcons
import app.morphe.manager.ui.screen.shared.rememberAppMetadata
import app.morphe.manager.util.KnownApps

/**
 * App the preview card stands for: YouTube where a source colors it, otherwise the first app a
 * source does. The home screen opens each card on its app's color, so a preview without one would
 * not show what [app.morphe.manager.util.AppCardColorMode.DEFAULT] looks like.
 */
@Composable
internal fun rememberPreviewApp(): BundleAppMetadata? {
    val metadata = rememberAppMetadata()
    return remember(metadata) {
        metadata[KnownApps.YOUTUBE]?.takeIf { it.gradientColors != null }
            ?: metadata.values.firstOrNull { it.gradientColors != null }
    }
}

/**
 * A home screen card for [app], drawn by the same layout as the real ones so a preview of the
 * appearance settings matches the home screen exactly. It shows a build with an update waiting,
 * which wears the card colors and the action a card offers. It stands for no app the user can open
 * from here, so the preview holding it lays it out of reach, see
 * [app.morphe.manager.ui.screen.shared.DisplayOnly].
 *
 * @param app App the card stands for, or null to show Morphe.
 * @param appColors The colors its bundle declares, which the card's color mode builds on.
 */
@Composable
internal fun PreviewAppCard(app: BundleAppMetadata?, appColors: List<Color>) {
    val displayName = app?.displayName ?: stringResource(R.string.app_name)
    AppCardLayout(
        gradientColors = appColors,
        onClick = {},
        trailing = { AppCardUpdateButton(appName = displayName, onClick = {}) }
    ) {
        AppCardContent(
            packageName = app?.packageName,
            packageInfo = null,
            displayName = displayName,
            subtitle = AppCardLine(
                text = stringResource(R.string.home_app_status_newer_patches),
                icon = AppStateIcons.NewerPatches,
                emphasized = true
            )
        )
    }
}
