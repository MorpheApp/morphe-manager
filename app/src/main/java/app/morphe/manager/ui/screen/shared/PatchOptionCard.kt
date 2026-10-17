/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.morphe.manager.R
import app.morphe.manager.util.rememberFolderPickerWithPermission
import app.morphe.manager.util.toFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the card of an option says about it besides the control that edits it. Shared by the
 * options edited while patching and the ones kept in settings, so an option reads the same in both.
 *
 * @param missing Whether the option is required and still holds nothing.
 * @param changed Whether the option holds something other than the patch's own value.
 * @param onReset Puts the option back on the patch's own value.
 */
data class OptionHeading(
    val title: String,
    val description: String,
    val required: Boolean,
    val missing: Boolean,
    val changed: Boolean = false,
    val onReset: () -> Unit = {}
)

/** Room past the heading's bounds that badges taller than its first line can spill into. */
private val HeadingBleed = 8.dp

/**
 * Lays the content out [bleed] larger on each side than it takes up, so what it draws there is
 * inside its bounds, and so inside any clip applied within, while its neighbors see its own size.
 */
private fun Modifier.bleedVertically(bleed: Dp): Modifier = layout { measurable, constraints ->
    val bleedPx = bleed.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = bleedPx * 2))
    layout(placeable.width, (placeable.height - bleedPx * 2).coerceAtLeast(0)) {
        placeable.place(0, -bleedPx)
    }
}

/**
 * Card of one option, set like the patch cards: the same card and border, the option's title and
 * description as a patch's name and description with [OptionStatus] after the title, and the
 * control that edits it below.
 *
 * @param showDescription Whether the description goes under the title. An option whose
 *   description is instructions long enough to fold away hands it to [OptionInstructions] instead.
 * @param showStatus Whether [OptionStatus] follows the title. A switch shows its state itself, and
 *   switching it back is all a reset would do.
 * @param onClick Makes the whole card the control, for an option edited from the card itself.
 * @param trailing Sits at the end of the title row, for a control as small as a switch.
 */
@Composable
fun OptionCard(
    heading: OptionHeading,
    showDescription: Boolean = true,
    showStatus: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    SettingsItemCard(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        showBorder = true,
        borderColor = if (heading.missing) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        // No size animation of its own: what grows inside, such as the instructions or the exact
        // value input of a slider, already animates, and a second one trails behind it
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eases the badges onto a line of their own and back, as an edit shows or hides one.
                // The animation clips to its bounds, which the badges spill past, so it is given
                // room around them that the layout takes straight back
                CardHeadingText(
                    name = heading.title,
                    description = heading.description.takeIf { showDescription },
                    modifier = Modifier
                        .weight(1f)
                        .bleedVertically(HeadingBleed)
                        .animateContentSize()
                        .padding(vertical = HeadingBleed),
                    badges = { if (showStatus) OptionStatus(heading) }
                )
                trailing?.invoke()
            }
            content?.invoke(this)
        }
    }
}

/**
 * Badges for what sets an option apart, after its title: required, in the error tone while it is
 * still empty, and changed, which puts the option back on the patch's own value when tapped.
 */
@Composable
private fun RowScope.OptionStatus(heading: OptionHeading) {
    if (heading.required) {
        StatusBadge(
            text = stringResource(R.string.patch_option_required),
            tone = if (heading.missing) SemanticTone.Error else SemanticTone.Neutral
        )
    }
    // Comes and goes with every edit, so it widens into place instead of shoving the title's
    // line about in a single frame
    AnimatedVisibility(
        visible = heading.changed,
        enter = Animations.expandHorizFadeIn,
        exit = Animations.shrinkHorizFadeOut
    ) {
        StatusBadge(
            text = stringResource(R.string.patch_option_changed),
            icon = Icons.Outlined.Restore,
            tone = SemanticTone.Primary,
            onClick = heading.onReset
        )
    }
}

/** An option's description folded away under a header, for instructions too long to read inline. */
@Composable
fun OptionInstructions(description: String) {
    if (description.isBlank()) return
    ExpandableSurface(
        title = stringResource(R.string.patch_option_instructions),
        content = {
            ScrollableInstruction(description = description, maxHeight = 280.dp)
        }
    )
}

/** What the creator of an option that asks for a picture of the app makes. */
enum class OptionAsset { Icon, Header }

/**
 * Button that makes [asset] for an option asking for one, with the dialog it opens. Hands the
 * folder the picture was made in to [onCreated].
 */
@Composable
fun AssetCreatorAction(
    asset: OptionAsset,
    packageName: String,
    onCreated: (String) -> Unit
) {
    var showCreator by remember { mutableStateOf(false) }

    AppDialogOutlinedButton(
        text = stringResource(
            when (asset) {
                OptionAsset.Icon -> R.string.adaptive_icon_create
                OptionAsset.Header -> R.string.header_creator_create
            }
        ),
        onClick = { showCreator = true },
        icon = when (asset) {
            OptionAsset.Icon -> Icons.Outlined.AutoAwesome
            OptionAsset.Header -> Icons.Outlined.Image
        },
        modifier = Modifier.fillMaxWidth()
    )

    DialogHost(visible = showCreator) {
        val onDone: (String) -> Unit = { path ->
            onCreated(path)
            showCreator = false
        }
        when (asset) {
            OptionAsset.Icon -> AdaptiveIconCreatorDialog(
                packageName = packageName,
                onDismiss = { showCreator = false },
                onIconCreated = onDone
            )
            OptionAsset.Header -> HeaderCreatorDialog(
                packageName = packageName,
                onDismiss = { showCreator = false },
                onHeaderCreated = onDone
            )
        }
    }
}

/**
 * Option that points at a folder, with its instructions folded away below. A typed folder option
 * takes a picker button; a plain string one, as older patch bundles declare, keeps an editable path
 * with the picker beside it. A folder set but no longer there is flagged, as the patcher fails a
 * run over it and this is where it can still be fixed.
 *
 * @param typed Whether the option is declared as a folder rather than as a plain string.
 * @param asset What the option's creator makes, or null where it offers none.
 * @param instructions Shown folded away, the option's own description unless given another.
 */
@Composable
fun FolderOptionCard(
    heading: OptionHeading,
    value: String,
    typed: Boolean,
    asset: OptionAsset?,
    packageName: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "/storage/emulated/0/folder",
    instructions: String = heading.description
) {
    val isGone = rememberPathIsGone(value)
    // Icon and header creation write into the folder, so the picker asks for storage access
    val folderPicker = rememberFolderPickerWithPermission { uri -> onValueChange(uri.toFilePath()) }

    OptionCard(heading.copy(missing = heading.missing || isGone), showDescription = false) {
        if (typed) {
            PickerButtonRow(
                label = stringResource(R.string.select_folder),
                selectedPath = value,
                icon = Icons.Outlined.Folder,
                onPick = { folderPicker() },
                onClear = { onValueChange("") }
            )
        } else {
            AppDialogTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                isError = heading.missing || isGone,
                showClearButton = true,
                onFolderPickerClick = { folderPicker() }
            )
        }

        if (isGone) {
            Notice(
                text = stringResource(R.string.settings_advanced_patch_options_path_gone),
                tone = SemanticTone.Error,
                density = NoticeDensity.Compact
            )
        }

        if (asset != null) AssetCreatorAction(asset, packageName, onCreated = onValueChange)
        OptionInstructions(instructions)
    }
}

/**
 * Whether [path] is set but nothing can be read there anymore. Checked off the main thread, and
 * again whenever the path changes.
 */
@Composable
private fun rememberPathIsGone(path: String): Boolean {
    var isGone by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        // Only an absolute path can be checked, anything else is for the patch to make sense of
        isGone = path.startsWith("/") && withContext(Dispatchers.IO) { !File(path).canRead() }
    }

    return isGone
}
