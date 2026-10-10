/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(target.packageName),
        footer = {
            AppDialogButtonRow(
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
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
        ) {
            // Headed by the app the selection lands on, with the source it lands in, while the
            // question the list below answers sits right above it
            ListDialogHeader(
                icon = { modifier ->
                    AppIcon(packageName = target.packageName, contentDescription = null, modifier = modifier)
                },
                title = target.appDisplayName,
                subtitle = target.bundleName,
                modifier = Modifier.padding(bottom = Defaults.ContentPaddingSmall)
            )
            Text(
                text = stringResource(R.string.copy_selection_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = dialogTextColor()
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
    val sourcesByUid = rememberSourcesByUid()
    DialogLazyList(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
    ) {
        items(
            items = candidates.withIndex().toList(),
            key = { (_, c) -> "${c.bundleUid}:${c.packageName}" }
        ) { (index, candidate) ->
            CandidateRow(
                candidate = candidate,
                selected = index == selectedIndex,
                onSelect = { onSelect(index) },
                // Named after the source it copies from, so the card wears that source's color once picked
                accentColor = sourcesByUid[candidate.bundleUid]?.let { rememberBundleAccent(it) }
            )
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: CopySelectionCandidate,
    selected: Boolean,
    onSelect: () -> Unit,
    accentColor: Color? = null
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
        contentDescription = "${candidate.packageDisplayName}, ${candidate.bundleName}, $availableText",
        accentColor = accentColor
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
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
                    StatusBadge(
                        text = stringResource(R.string.copy_selection_same_source),
                        tone = SemanticTone.Primary
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
            StatusBadge(
                text = availableText,
                tone = if (enabled) SemanticTone.Primary else SemanticTone.Neutral,
                icon = Icons.Outlined.CheckCircle
            )
        }
    }
}

