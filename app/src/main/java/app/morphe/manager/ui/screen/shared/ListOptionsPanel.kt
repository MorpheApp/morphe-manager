/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.SortModeSpec
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/** One way of arranging a list, its options and the one picked, as a row of a [ListOptionsPanel]. */
class ListOptionSection<T>(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val current: T,
    val default: T,
    val options: List<SortModeOption<T>>,
    val onSelect: (T) -> Unit
) {
    val currentOption: SortModeOption<T> get() = options.first { it.value == current }
    val isChanged: Boolean get() = current != default
    fun reset() = onSelect(default)
}

/** A [ListOptionSection] offering every mode of [T]. */
@Composable
inline fun <reified T> listOptionSection(
    @StringRes titleRes: Int,
    icon: ImageVector,
    current: T,
    default: T,
    noinline onSelect: (T) -> Unit
): ListOptionSection<T> where T : Enum<T>, T : SortModeSpec =
    ListOptionSection(titleRes, icon, current, default, sortModeOptions(default), onSelect)

/**
 * What the button opening a [ListOptionsPanel] says it holds: the order alone while nothing is
 * filtered, and the filter with it once one narrows the list.
 */
@Composable
fun listOptionsStateDescription(@StringRes sortRes: Int, @StringRes filterRes: Int?): String =
    if (filterRes != null) {
        stringResource(R.string.list_options_state_description, stringResource(sortRes), stringResource(filterRes))
    } else {
        stringResource(sortRes)
    }

/**
 * How a list is sorted and filtered, and grouped where it can be: a row for each [sections] entry
 * with the option picked, which opens its options in a menu. Docked under the list rather than over
 * it, so the list rearranges itself above the panel with each change.
 */
@Composable
fun ListOptionsPanel(
    visible: Boolean,
    sections: List<ListOptionSection<*>>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Index of the section whose menu is open, if any
    var openSection by remember { mutableStateOf<Int?>(null) }

    DockedPanel(visible = visible, onBack = onClose, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Defaults.SpaceSmall)) {
            PanelHeader(title = { PanelTitle(text = stringResource(R.string.list_options_title)) }) {
                TitleAction(
                    icon = MaterialSymbols.Rounded.History,
                    contentDescription = stringResource(R.string.reset),
                    onClick = { sections.forEach { it.reset() } },
                    style = TitleActionStyle.Accent,
                    enabled = sections.any { it.isChanged }
                )
                TitleAction(
                    icon = MaterialSymbols.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onClose,
                    style = TitleActionStyle.Neutral
                )
            }
            sections.forEachIndexed { index, section ->
                SectionRow(
                    section = section,
                    menuOpen = openSection == index,
                    onOpenMenu = { openSection = index },
                    onCloseMenu = { openSection = null }
                )
            }
        }
    }
}

/**
 * [section] as a row with the option picked at its end, marked when it is not the default, which
 * opens the section's options in a menu beside it.
 */
@Composable
private fun <T> SectionRow(
    section: ListOptionSection<T>,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit
) {
    PanelRow(
        title = stringResource(section.titleRes),
        onClick = onOpenMenu,
        icon = section.icon
    ) {
        Text(
            text = section.currentOption.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (section.isChanged) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // The menu hangs off the chevron, so it opens at the row's end, under the value it changes
        Box {
            ExpandChevron(expanded = menuOpen, modifier = Modifier.size(20.dp))
            SortModeMenu(
                expanded = menuOpen,
                onDismissRequest = onCloseMenu,
                current = section.current,
                options = section.options,
                onSelect = section.onSelect
            )
        }
    }
}
