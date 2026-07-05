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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.util.toast

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
        enter = MorpheAnimations.springSlideUpEnter,
        exit = MorpheAnimations.springSlideDownExit,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            tonalElevation = 4.dp,
            content = content
        )
    }
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
    onDeselectAll: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    fun withToast(doneMessage: String, action: () -> Unit): () -> Unit = {
        context.toast(doneMessage)
        action()
    }

    val selectAllLabel = stringResource(R.string.select_all)
    val selectAllDone = stringResource(R.string.select_all_done)
    val deselectAllLabel = stringResource(R.string.deselect_all)
    val deselectAllDone = stringResource(R.string.deselect_all_done)
    val cancelLabel = stringResource(android.R.string.cancel)
    val selectedLabel = stringResource(R.string.selected).lowercase()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = selectedCount,
            transitionSpec = MorpheAnimations.compactCounterTransitionSpec,
            label = "selected_count"
        ) { count ->
            Text(
                text = "$count $selectedLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ActionPillRow {
            ActionPillButton(
                onClick = withToast(selectAllDone, onSelectAll),
                icon = Icons.Outlined.DoneAll,
                contentDescription = selectAllLabel,
                tooltip = selectAllLabel,
                enabled = selectedCount < totalCount
            )
            if (onDeselectAll != null) {
                ActionPillButton(
                    onClick = withToast(deselectAllDone, onDeselectAll),
                    icon = Icons.Outlined.RemoveDone,
                    contentDescription = deselectAllLabel,
                    tooltip = deselectAllLabel,
                    enabled = selectedCount > 0
                )
            }
            if (onCancel != null) {
                ActionPillButton(
                    onClick = onCancel,
                    icon = Icons.Outlined.Close,
                    contentDescription = cancelLabel,
                    tooltip = cancelLabel
                )
            }
            actions()
        }
    }
}
