/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.Animations
import app.morphe.manager.ui.screen.shared.Defaults
import app.morphe.manager.ui.screen.shared.HeaderBar
import app.morphe.manager.ui.screen.shared.LocalAccent
import app.morphe.manager.ui.screen.shared.ListOptionsButton
import app.morphe.manager.ui.screen.shared.ListReorder
import app.morphe.manager.ui.screen.shared.MorpheWordmark
import app.morphe.manager.ui.screen.shared.PageSubtitle
import app.morphe.manager.ui.screen.shared.ReorderHeaderActions
import app.morphe.manager.ui.screen.shared.SearchFieldState
import app.morphe.manager.ui.screen.shared.TitleAction
import app.morphe.manager.ui.screen.shared.TitleActionStyle
import app.morphe.manager.ui.screen.shared.rememberLatest
import app.morphe.manager.util.contrastingContent
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Heads the apps page with the Morphe wordmark. Its controls bring back the alerts swiped aside,
 * while there are any, open the list's order and filter, patch an app the list does not hold from
 * its APK and search the list. While [reorder] is under way they give way to its own, see
 * [ReorderHeaderActions].
 *
 * @param sortEnabled Whether the list's options can be opened, which a selection under way holds off.
 * @param sortOpen Whether the list's options are docked under the list, which the button then closes.
 */
@Composable
internal fun HomeHeader(
    apps: HomeAppListUi,
    search: SearchFieldState,
    alerts: HomeAlerts,
    sortEnabled: Boolean,
    sortOpen: Boolean,
    onSortClick: () -> Unit,
    onPatchFileClick: () -> Unit,
    reorder: ListReorder? = null,
    // Set while the tour runs, which points at the button for other apps
    tour: OnboardingState? = null
) {
    val appName = stringResource(R.string.app_name)
    HeaderBar(
        title = {
            // At the start of the room the controls leave it, as a page's title is
            Column(modifier = Modifier.fillMaxWidth()) {
                MorpheWordmark(
                    modifier = Modifier
                        .height(WordmarkHeight)
                        .semantics {
                            heading()
                            contentDescription = appName
                        }
                )
                if (reorder != null) PageSubtitle(stringResource(R.string.reorder_list_hint))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        // A query would narrow the list the order is being dragged on
        search = search.takeIf { reorder == null },
        searchLabel = stringResource(R.string.home_search_apps),
        actions = {
            if (reorder != null) {
                ReorderHeaderActions(reorder)
                return@HeaderBar
            }

            // Comes and goes with what it brings back, as the alerts it stands for do
            val snoozedCount = alerts.snoozedCount
            AnimatedVisibility(
                visible = snoozedCount > 0,
                enter = Animations.headerActionEnter,
                exit = Animations.headerActionExit
            ) {
                val shownCount = rememberLatest(snoozedCount.takeIf { it > 0 }) ?: 0
                val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
                // How many wait behind it, as they pile up across launches. Read out with the bell
                BadgedBox(
                    badge = {
                        Badge(containerColor = accent, contentColor = accent.contrastingContent()) {
                            Text(text = shownCount.toString(), modifier = Modifier.clearAndSetSemantics {})
                        }
                    },
                    modifier = Modifier.ringOnArrival(this)
                ) {
                    TitleAction(
                        icon = MaterialSymbols.Rounded.Notifications,
                        contentDescription = pluralStringResource(
                            R.plurals.home_alerts_snoozed_count,
                            shownCount,
                            shownCount.toString()
                        ),
                        onClick = alerts::restoreSnoozed,
                        style = TitleActionStyle.Neutral
                    )
                }
            }

            ListOptionsButton(
                sortRes = apps.sortMode.labelRes,
                filterRes = apps.filterMode.labelRes.takeIf { apps.filterMode.isActive },
                open = sortOpen,
                onClick = onSortClick,
                enabled = sortEnabled
            )

            TitleAction(
                icon = MaterialSymbols.Rounded.File_open,
                contentDescription = stringResource(R.string.home_other_apps_action),
                onClick = onPatchFileClick,
                style = TitleActionStyle.Accent,
                modifier = Modifier.tourTarget(TourTarget.OTHER_APPS, tour)
            )
        }
    )
}

/** Height of the wordmark, its descender included, which sets its letters at about a page title's size. */
private val WordmarkHeight = 26.dp

/**
 * Rings the bell as it arrives in [visibility], a swing to either side that dies away once it has
 * popped in, so an alert swiped aside is seen going somewhere. Turned about its middle, which
 * leaves the circle of the button as it is and swings only the bell drawn in it.
 */
@Composable
private fun Modifier.ringOnArrival(visibility: AnimatedVisibilityScope): Modifier {
    // Only an arrival rings. Shown again as its page comes back, the bell was there all along
    val arriving = remember { visibility.transition.currentState == EnterExitState.PreEnter }
    val swing = remember { Animatable(0f) }
    LaunchedEffect(arriving) {
        if (!arriving) return@LaunchedEffect
        swing.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = BELL_RING_DURATION
                delayMillis = Defaults.ANIMATION_DURATION_SHORT
                // Bracketed, as a leading minus on its own line would negate the keyframe's result
                // rather than its angle
                18f at 90
                (-14f) at 190
                9f at 290
                (-5f) at 390
                2f at 480
            }
        )
    }
    return graphicsLayer { rotationZ = swing.value }
}

private const val BELL_RING_DURATION = 600
