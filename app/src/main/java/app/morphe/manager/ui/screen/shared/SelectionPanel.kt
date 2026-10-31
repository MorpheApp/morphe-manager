/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.repository.SelectionSetSummary
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * A (bundle + package) pair the user can copy from: its saved selection, or set [setUid] when one
 * is given. [applicableCount] is the count of source patches that survive intersection with the
 * target bundle's patch names.
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
    val isSamePackage: Boolean,
    val setUid: Int? = null,
    val setName: String? = null
)

/**
 * The sets kept for the app a selection is being made for, the selection it opened with and the
 * patches the open source recommends, for the screens that edit a selection before it is used.
 *
 * @param canApplyRecommended Whether a source is open for [onApplyRecommended] to switch to its
 *   recommended patches.
 */
@Stable
class SelectionSetActions(
    val sets: List<SelectionSetSummary>,
    val canApplyRecommended: Boolean,
    val onApplyRecommended: () -> Unit,
    val hasSavedSelection: Boolean,
    val onRestoreSaved: () -> Unit,
    val onApply: (SelectionSetSummary) -> Unit,
    val onSave: (name: String) -> Unit,
    val onDelete: (SelectionSetSummary) -> Unit,
    val onRename: (SelectionSetSummary, name: String) -> Unit
)

/**
 * Panel docked under a patch list for the selection as a whole: the recommended patches of the open
 * source, then the selection it opened with and the sets kept for the app, which switch every
 * source at once, then the selections of other apps and sources the open source can copy from.
 * Only copies that apply are offered, so one never picks up nothing.
 *
 * @param subtitle What the [headerActions] act on, such as the open source and how much of it is
 *   selected.
 * @param headerActions Buttons acting on the open source, ahead of the close button.
 * @param sets The sets kept for the app, or null where a selection is not being edited.
 * @param copyTargetName Source a copy lands in, or null while there is none to copy into.
 * @param candidates What [copyTargetName] can copy from, null while it loads.
 */
@Composable
fun SelectionPanel(
    title: String,
    sets: SelectionSetActions?,
    copyTargetName: String?,
    candidates: List<CopySelectionCandidate>?,
    onCopy: (CopySelectionCandidate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerActions: @Composable RowScope.() -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<SelectionSetSummary?>(null) }
    var pendingRename by remember { mutableStateOf<SelectionSetSummary?>(null) }

    DockedPanel(visible = true, onBack = onClose, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Defaults.SpaceSmall)) {
            PanelHeader(
                title = {
                    Column {
                        PanelTitle(text = title)
                        if (subtitle != null) PanelSubtitle(text = subtitle)
                    }
                }
            ) {
                headerActions()
                TitleAction(
                    icon = MaterialSymbols.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onClose,
                    style = TitleActionStyle.Neutral
                )
            }
            // Held short of the list it acts on, which stays in view above it, and scrolled once the
            // keyboard leaves less room than that
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = SelectionPanelMaxHeight)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScrollFade(scrollState)
                        .verticalScroll(scrollState)
                        .padding(bottom = Defaults.SpaceMedium),
                    verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                ) {
                    if (sets != null) {
                        SetsSection(
                            actions = sets,
                            onDelete = { pendingDelete = it },
                            onRename = { pendingRename = it }
                        )
                    }
                    if (copyTargetName != null) {
                        CopySection(targetName = copyTargetName, candidates = candidates, onCopy = onCopy)
                    }
                }
                ListScrollbar(scrollState = scrollState, modifier = Modifier.padding(vertical = Defaults.SpaceMedium))
            }
        }
    }

    DialogHost(target = pendingDelete) { set ->
        DeleteSelectionSetDialog(
            set = set,
            onConfirm = {
                sets?.onDelete(set)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    DialogHost(target = pendingRename) { set ->
        RenameSelectionSetDialog(
            set = set,
            takenNames = sets?.sets.orEmpty().mapTo(mutableSetOf()) { it.name },
            onConfirm = { name ->
                sets?.onRename(set, name)
                pendingRename = null
            },
            onDismiss = { pendingRename = null }
        )
    }
}

/**
 * Asks for a new name for [set], prefilled with its own. A name another set of the app goes by,
 * [takenNames], replaces that set, which the button says before it does.
 */
@Composable
fun DialogHostScope.RenameSelectionSetDialog(
    set: SelectionSetSummary,
    takenNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(set.uid) { mutableStateOf(set.name) }
    val trimmed = name.trim()
    // Its own name is no rename at all, so the button waits for another
    val canRename = trimmed.isNotEmpty() && trimmed != set.name
    val replaces = trimmed != set.name && trimmed in takenNames

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.selection_set_rename_title),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(if (replaces) R.string.selection_set_replace else R.string.rename),
                onPrimaryClick = { onConfirm(trimmed) },
                primaryEnabled = canRename,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        AppDialogTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.selection_set_name)) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Bookmark, contentDescription = null) },
            showClearButton = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (canRename) onConfirm(trimmed) }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** What can be done with a set besides applying it, for a [RowActionsMenu]. */
@Composable
fun selectionSetActions(onRename: () -> Unit, onDelete: () -> Unit): List<SelectionAction> = listOf(
    SelectionAction(
        icon = MaterialSymbols.Rounded.Edit,
        label = stringResource(R.string.rename),
        onClick = onRename
    ),
    SelectionAction(
        icon = MaterialSymbols.Rounded.Delete,
        label = stringResource(R.string.delete),
        onClick = onDelete,
        tone = ActionTone.Destructive
    )
)

/** Asks before [set] is deleted, which cannot be undone. */
@Composable
fun DialogHostScope.DeleteSelectionSetDialog(
    set: SelectionSetSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = stringResource(R.string.selection_set_delete_title),
        message = stringResource(R.string.selection_set_delete_message, set.name),
        primaryText = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * The recommended patches of the open source, the saved selection and the sets of the app, then the
 * field a new set is named in. A set is applied on a tap, and renamed or deleted from its menu, a
 * long press renaming it too.
 */
@Composable
private fun SetsSection(
    actions: SelectionSetActions,
    onDelete: (SelectionSetSummary) -> Unit,
    onRename: (SelectionSetSummary) -> Unit
) {
    val renameLabel = stringResource(R.string.rename)
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)) {
        SectionTitle(text = stringResource(R.string.selection_sets_title), icon = null, parent = null, onClick = null)
        PanelRow(
            title = stringResource(R.string.expert_mode_recommended),
            onClick = actions.onApplyRecommended,
            icon = MaterialSymbols.Rounded.Recommend,
            enabled = actions.canApplyRecommended
        )
        PanelRow(
            title = stringResource(R.string.expert_mode_saved_selection),
            onClick = actions.onRestoreSaved,
            icon = MaterialSymbols.Rounded.History,
            enabled = actions.hasSavedSelection
        )
        actions.sets.forEach { set ->
            PanelRow(
                title = set.name,
                onClick = { actions.onApply(set) },
                icon = MaterialSymbols.Rounded.Bookmark,
                onLongClick = { onRename(set) },
                onLongClickLabel = renameLabel
            ) {
                RowValue(
                    text = pluralStringResource(R.plurals.patch_count, set.patchCount, set.patchCount.toString()),
                    modifier = Modifier.weight(1f)
                )
                RowActionsMenu(selectionSetActions(onRename = { onRename(set) }, onDelete = { onDelete(set) }))
            }
        }
        SaveSetField(sets = actions.sets, onSave = actions.onSave)
    }
}

/**
 * Field a set is named in, saved from its button or the keyboard. A name the app already has a set
 * under replaces that set, which the button says before it does.
 */
@Composable
private fun SaveSetField(sets: List<SelectionSetSummary>, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val replaces = sets.any { it.name == trimmed }
    val save = {
        if (trimmed.isNotEmpty()) {
            onSave(trimmed)
            name = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppDialogTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.selection_set_name)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.weight(1f)
        )
        TitleAction(
            icon = if (replaces) MaterialSymbols.Rounded.Bookmark_added else MaterialSymbols.Rounded.Bookmark_add,
            contentDescription = stringResource(
                if (replaces) R.string.selection_set_replace else R.string.selection_set_save
            ),
            onClick = save,
            style = TitleActionStyle.Accent,
            enabled = trimmed.isNotEmpty()
        )
    }
}

/** The selections [targetName] can copy from, those it has a patch in common with. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopySection(
    targetName: String,
    candidates: List<CopySelectionCandidate>?,
    onCopy: (CopySelectionCandidate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)) {
        SectionTitle(
            text = stringResource(R.string.selection_panel_copy_into, targetName),
            icon = null,
            parent = null,
            onClick = null
        )
        when {
            candidates == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Defaults.SpaceMedium),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(Defaults.SpaceExtraExtraLarge))
            }

            candidates.isEmpty() -> Text(
                text = stringResource(R.string.copy_selection_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceSmall)
            )

            else -> candidates.forEach { candidate ->
                PanelRow(
                    title = candidate.packageDisplayName,
                    onClick = { onCopy(candidate) },
                    icon = if (candidate.setName != null) {
                        MaterialSymbols.Rounded.Bookmark
                    } else MaterialSymbols.Rounded.Content_copy
                ) {
                    // Where it comes from and how much of it lands, ending in line with the other rows
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        RowValue(text = listOfNotNull(candidate.setName, candidate.bundleName).joinToString(" · "))
                        Text(
                            text = stringResource(
                                R.string.copy_selection_available_format,
                                candidate.applicableCount,
                                candidate.sourceCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Value at the end of a [PanelRow], set like the values of the list options panel. */
@Composable
private fun RowValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Height the panel reaches up to at most, leaving the list it acts on in view above it. */
private val SelectionPanelMaxHeight = 360.dp
