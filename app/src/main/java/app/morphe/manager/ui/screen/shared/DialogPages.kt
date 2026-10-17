/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

/**
 * Shows one [page] of a dialog at a time, crossfading from one to the next within the same window.
 * Each page keeps what it saves, such as its scroll and its search, while another is shown, so
 * going back to it finds it as it was left.
 *
 * @param key Tells the pages apart and is saved with them, so a page whose data changes stays put.
 */
@Composable
fun <T> DialogPages(
    page: T,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val saved = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = page,
        contentKey = key,
        transitionSpec = Animations.fadeCrossfade(),
        modifier = modifier,
        label = "dialogPages"
    ) { shown ->
        saved.SaveableStateProvider(key(shown)) {
            content(shown)
        }
    }
}
