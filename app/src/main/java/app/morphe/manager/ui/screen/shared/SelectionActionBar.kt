/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.theme.MonochromeThemeDefaults
import app.morphe.manager.util.withToast

/**
 * Metrics of the [MultiSelectShell] surface. The bar floats over the content it belongs to, so
 * layouts underneath have to keep that content clear of it; the values they pad by live here
 * rather than being restated at each call site, where nothing would keep them in step.
 */
object MultiSelectBarDefaults {
    /** Padding the shell keeps above and below its surface. */
    val SurfacePadding = 8.dp

    /** Height of a bar carrying a counter line and one row of pills. */
    val Height = 100.dp

    /** Bottom padding a scrolling list needs for its last item to clear the bar. */
    val ListClearance = Height - SurfacePadding

    /** Clearance for floating controls, which keep a little more air between them and the bar. */
    val ControlClearance = 96.dp
}

/**
 * Slide-up surface used to host a multi-select action row. Keeps the surface, elevation
 * and enter/exit animations consistent between the home multi-select bar and the saved-APK
 * dialog footer.
 */
@Composable
fun MultiSelectShell(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = Animations.springSlideUpEnter,
        exit = Animations.springSlideDownExit,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MultiSelectBarDefaults.SurfacePadding),
            shape = RoundedCornerShape(16.dp),
            color = MonochromeThemeDefaults.surfaceColor(MaterialTheme.colorScheme.surfaceContainerHigh),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            content = content
        )
    }
}

private class LastVisibleValue<T>(var value: T)

/**
 * Returns [value] while [visible] is true, and the last value seen before that afterwards.
 *
 * Action handlers clear the selection in the same pass that hides the bar, so a row rendered
 * from live state loses buttons and zeroes its counter while it is still sliding out.
 *
 * This holds only while both writes land in one snapshot. A handler that hides the bar and
 * clears its state in separate frames has nothing left to freeze by the time [visible] flips.
 */
@Composable
fun <T> rememberWhileVisible(visible: Boolean, value: T): T {
    val holder = remember { LastVisibleValue(value) }
    if (visible) holder.value = value
    return holder.value
}

/**
 * Counter label ("N selected") followed by an [ActionPillRow] with SelectAll,
 * optional DeselectAll and Cancel, and caller-provided [actions]. Meant to be placed
 * inside a [MultiSelectShell].
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDeselectAll: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current

    val selectAllLabel = stringResource(R.string.select_all)
    val selectAllDone = stringResource(R.string.select_all_done)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val deselectAllDone = stringResource(R.string.deselect_all_done)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.selected).lowercase()
    val allSelected = totalCount in 1..selectedCount
    val canToggleToDeselect = allSelected && onDeselectAll != null
    val selectionToggleLabel = if (canToggleToDeselect) deselectAllLabel else selectAllLabel
    val selectionToggleDone = if (canToggleToDeselect) deselectAllDone else selectAllDone

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = selectedCount,
            transitionSpec = Animations.compactCounterTransitionSpec,
            label = "selected_count"
        ) { count ->
            Text(
                text = "$count $selectedLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (subtitle != null) {
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = Animations.compactCounterTransitionSpec,
                label = "selection_subtitle"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        ActionPillRow {
            ActionPillButton(
                onClick = context.withToast(selectionToggleDone) {
                    if (canToggleToDeselect) onDeselectAll() else onSelectAll()
                },
                icon = if (canToggleToDeselect) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                contentDescription = selectionToggleLabel,
                tooltip = selectionToggleLabel,
                enabled = canToggleToDeselect || selectedCount < totalCount
            )
            actions()
            if (onCancel != null) {
                ActionPillButton(
                    onClick = onCancel,
                    icon = Icons.Outlined.Close,
                    contentDescription = cancelLabel,
                    tooltip = cancelLabel
                )
            }
        }
    }
}
