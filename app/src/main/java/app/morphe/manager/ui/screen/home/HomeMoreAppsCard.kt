/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.ConfirmDialog
import app.morphe.manager.ui.screen.shared.DialogHost
import app.morphe.manager.ui.screen.shared.GlassPlaceholderIcon
import app.morphe.manager.util.AppCardColorDefaults
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * [MoreAppsCard] closing the list.
 *
 * @param visible Whether the card is turned on and the list shows every app as it is, since the
 *   card belongs under the whole list rather than under a search, a filter or a selection.
 */
internal fun LazyListScope.moreAppsCard(
    visible: Boolean,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    keyPrefix: String = ""
) {
    if (!visible) return

    item(key = "${keyPrefix}more_apps") {
        MoreAppsCard(
            onOpen = onOpen,
            onHide = onHide,
            modifier = Modifier.animateItem()
        )
    }
}

/**
 * Card after the apps that leads to the community patches, where the apps no added source brings
 * are found. Drawn as an app card with a glyph in place of an icon, so it reads as one more way to
 * the list's apps rather than one of them.
 *
 * Swiped toward the start or long pressed, it asks to be hidden, which the home screen settings
 * undo. It swipes no other way, as there is nothing to patch on it.
 */
@Composable
private fun MoreAppsCard(
    onOpen: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHideDialog by remember { mutableStateOf(false) }
    val hideAction = rememberHideSwipeAction()
    val gradientColors = AppCardColorDefaults.defaultGradientColors

    SwipeableCardContainer(
        start = SwipeAction(hideAction) { showHideDialog = true },
        end = null,
        modifier = modifier
    ) {
        // A long press stands in for the swipe where there is none, on a remote
        AppCardLayout(
            gradientColors = gradientColors,
            onClick = onOpen,
            onLongClick = { showHideDialog = true },
            onLongClickLabel = hideAction.label
        ) {
            AppCardContent(
                displayName = stringResource(R.string.home_more_apps_title),
                subtitle = stringResource(R.string.sources_dialog_community),
                gradientColors = gradientColors
            ) { placeholderColors ->
                GlassPlaceholderIcon(
                    gradientColors = placeholderColors,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    DialogHost(visible = showHideDialog) {
        ConfirmDialog(
            title = stringResource(R.string.home_more_apps_hide_title),
            message = stringResource(R.string.home_more_apps_hide_message),
            primaryText = hideAction.label,
            isPrimaryDestructive = false,
            onConfirm = {
                showHideDialog = false
                onHide()
            },
            onDismiss = { showHideDialog = false }
        )
    }
}
