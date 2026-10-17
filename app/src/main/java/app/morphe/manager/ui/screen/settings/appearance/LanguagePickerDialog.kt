/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

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
                icon = { modifier ->
                    ListDialogHeaderIcon(icon = Icons.Outlined.Language, color = accent, modifier = modifier)
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
            verticalArrangement = Arrangement.spacedBy(DialogListSpacing)
        ) {
            if (filteredLanguages.isEmpty()) {
                item(key = "empty_state") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = Icons.Outlined.SearchOff,
                        modifier = Modifier.animatedListItem(this)
                    )
                }
            }

            items(items = filteredLanguages, key = { it.code }) { language ->
                LanguageItem(
                    language = language,
                    isSelected = currentLanguage == language.code,
                    onClick = { onLanguageSelected(language.code) },
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
    modifier: Modifier = Modifier
) {
    val stateDescription = stringResource(
        if (isSelected) R.string.selected else R.string.not_selected
    )
    RadioSelectionCard(
        selected = isSelected,
        onSelect = onClick,
        modifier = modifier,
        stateDescription = stateDescription,
        leadingContent = {
            SelectionLeadingBox(selected = isSelected, size = 40.dp) {
                Text(
                    text = language.flag,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = dialogTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.bodyMedium,
                color = dialogSecondaryTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
