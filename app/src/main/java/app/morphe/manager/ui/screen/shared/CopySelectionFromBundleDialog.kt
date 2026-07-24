/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R

/**
 * Package + bundle the user is copying INTO.
 * Also used to filter the target itself out of the candidate list.
 */
@Immutable
data class CopySelectionTarget(
    val packageName: String,
    val bundleUid: Int,
    val bundleName: String,
    val appDisplayName: String
)

/**
 * A (bundle + package) pair the user can copy from.
 * [applicableCount] is the count of source patches that survive intersection
 * with the target bundle's patch names.
 */
@Immutable
data class CopySelectionCandidate(
    val bundleUid: Int,
    val bundleName: String,
    val packageName: String,
    val packageDisplayName: String,
    val sourceCount: Int,
    val applicableCount: Int,
    val isSameSource: Boolean,
    val isSamePackage: Boolean
)

/**
 * Picker for the "copy selection from another bundle" flow. Renders a radio list
 * of candidates and previews the intersection count so the user can see how many
 * patches will actually apply.
 *
 * Pass `candidates = null` while the list is loading to render a branded loader.
 */
@Composable
fun CopySelectionFromBundleDialog(
    target: CopySelectionTarget,
    candidates: List<CopySelectionCandidate>?,
    onConfirm: (CopySelectionCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val visibleCandidates = candidates.orEmpty()

    val selectedCandidate = visibleCandidates.getOrNull(selectedIndex)
    val canConfirm = selectedCandidate != null && selectedCandidate.applicableCount > 0
    val confirmLabel = if (selectedCandidate != null && selectedCandidate.applicableCount > 0) {
        stringResource(R.string.copy_selection_confirm_with_count, selectedCandidate.applicableCount)
    } else {
        stringResource(R.string.copy)
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.copy_selection_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = confirmLabel,
                onPrimaryClick = { selectedCandidate?.let(onConfirm) },
                primaryEnabled = canConfirm,
                primaryIcon = Icons.Outlined.ContentCopy,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            HeroInfoCard(
                icon = Icons.Outlined.Extension,
                title = target.appDisplayName,
                subtitle = {
                    Text(
                        text = target.bundleName,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalDialogSecondaryTextColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            when {
                candidates == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PulsingLogoWithCaption(caption = stringResource(R.string.loading))
                }
                visibleCandidates.isEmpty() -> EmptyState(
                    message = stringResource(R.string.copy_selection_empty),
                    icon = Icons.Outlined.FolderOff
                )
                else -> CandidateList(
                    candidates = visibleCandidates,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CandidateList(
    candidates: List<CopySelectionCandidate>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
        ) {
            items(
                items = candidates.withIndex().toList(),
                key = { (_, c) -> "${c.bundleUid}:${c.packageName}" }
            ) { (index, candidate) ->
                CandidateRow(
                    candidate = candidate,
                    selected = index == selectedIndex,
                    onSelect = { onSelect(index) }
                )
            }
        }
        ScrollToTopButton(listState = listState)
    }
}

@Composable
private fun CandidateRow(
    candidate: CopySelectionCandidate,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val enabled = candidate.applicableCount > 0
    val availableText = stringResource(
        R.string.copy_selection_available_format,
        candidate.applicableCount,
        candidate.sourceCount
    )

    RadioSelectionCard(
        selected = selected,
        onSelect = onSelect,
        enabled = enabled,
        contentDescription = "${candidate.packageDisplayName}, ${candidate.bundleName}, $availableText"
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MorpheDefaults.ContentPaddingSmall)
            ) {
                Text(
                    text = candidate.bundleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (candidate.isSameSource) {
                    InfoBadge(
                        text = stringResource(R.string.copy_selection_same_source),
                        style = InfoBadgeStyle.Primary,
                        isCompact = true
                    )
                }
            }
            Text(
                text = candidate.packageDisplayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            InfoBadge(
                text = availableText,
                style = if (enabled) InfoBadgeStyle.Primary else InfoBadgeStyle.Default,
                icon = Icons.Outlined.CheckCircle,
                isCompact = true
            )
        }
    }
}

