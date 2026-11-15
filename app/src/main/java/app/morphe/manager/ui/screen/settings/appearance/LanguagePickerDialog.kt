/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.MORPHE_WEBSITE_URL
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Language picker dialog with searchable list.
 */
@Composable
fun DialogHostScope.LanguagePickerDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val search = rememberSearchFieldState()

    val allLanguages = remember(context) {
        LanguageRepository.getSupportedLanguages(context)
    }

    val filteredLanguages = remember(search.query, allLanguages) {
        if (search.query.isBlank()) {
            allLanguages
        } else {
            allLanguages.filter { language ->
                language.displayName.contains(search.query, ignoreCase = true) ||
                        language.nativeName.contains(search.query, ignoreCase = true) ||
                        language.code.contains(search.query, ignoreCase = true)
            }
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    ListDialog(
        onDismissRequest = onDismiss,
        header = {
            ListDialogHeader(
                decorativeIcon = true,
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = MaterialSymbols.Rounded.Language, color = accent, modifier = modifier)
                },
                title = stringResource(R.string.settings_appearance_app_language),
                // The language in effect, so the choice reads before the list is scrolled to it
                subtitle = LanguageRepository.getLanguage(currentLanguage, context).displayName,
                search = search,
                accentColor = accent
            )
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        hideFooterWhileTyping = true
    ) {
        SearchFieldBackHandler(search)

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentGap)
        ) {
            // Offered ahead of the languages rather than in a dialog of its own before them, and
            // left out of a search, which looks for a language rather than how to add one
            if (search.query.isBlank()) {
                item(key = "translate") {
                    SettingsItem(
                        onClick = { openUrl("$MORPHE_WEBSITE_URL/translate") },
                        showBorder = true,
                        icon = MaterialSymbols.Rounded.Translate,
                        title = stringResource(R.string.settings_appearance_translate),
                        subtitle = stringResource(R.string.settings_appearance_translations_info_title),
                        // Leaves for the browser rather than going on within the dialog
                        trailingContent = { ThemedIcon(icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored) },
                        modifier = Modifier
                            .animatedListItem(this)
                            .endsBlock()
                    )
                }
            }

            if (filteredLanguages.isEmpty()) {
                item(key = "empty_state") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = MaterialSymbols.Rounded.Search_off,
                        modifier = Modifier.animatedListItem(this)
                    )
                }
            }

            // Joined into one group, as the choices of the other pickers are
            itemsIndexed(items = filteredLanguages, key = { _, language -> language.code }) { index, language ->
                LanguageItem(
                    language = language,
                    isSelected = currentLanguage == language.code,
                    onClick = { onLanguageSelected(language.code) },
                    segment = ListSegment.of(index, filteredLanguages.size),
                    modifier = Modifier.animatedListItem(this)
                )
            }
        }
    }
}

/**
 * Individual language item in the list.
 */
@Composable
private fun LanguageItem(
    language: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    segment: ListSegment,
    modifier: Modifier = Modifier
) {
    RadioSelectionCard(
        selected = isSelected,
        onSelect = onClick,
        modifier = modifier.endsBlock(segment.endsBlock),
        shape = segment.shape
    ) {
        IconTextRow(
            modifier = Modifier.weight(1f),
            // The flag sits beside the name, as an icon does in the other pickers, and leaves the
            // lead to the same radio mark they carry
            leadingContent = {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(text = language.flag, style = MaterialTheme.typography.titleLarge)
                }
            },
            title = language.displayName,
            // A language already shown in its own name has nothing more to say here
            description = language.nativeName.takeIf { it != language.displayName }
        )
    }
}
