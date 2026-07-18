/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.appearance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*

/**
 * Language picker dialog with searchable list.
 */
@Composable
fun LanguagePickerDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val allLanguages = remember(context) {
        LanguageRepository.getSupportedLanguages(context)
    }

    val filteredLanguages = remember(searchQuery, allLanguages) {
        if (searchQuery.isBlank()) {
            allLanguages
        } else {
            allLanguages.filter { language ->
                language.displayName.contains(searchQuery, ignoreCase = true) ||
                        language.nativeName.contains(searchQuery, ignoreCase = true) ||
                        language.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_appearance_app_language),
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        scrollable = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MorpheDefaults.ItemSpacing)
        ) {
            // Search field
            MorpheDialogTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(
                        stringResource(R.string.search),
                        color = LocalDialogSecondaryTextColor.current
                    )
                },
                leadingIcon = {
                    MorpheIcon(
                        icon = Icons.Outlined.Search,
                        tint = LocalDialogSecondaryTextColor.current
                    )
                },
                showClearButton = true
            )

            // Language list
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLanguages) { language ->
                    LanguageItem(
                        language = language,
                        isSelected = currentLanguage == language.code,
                        onClick = { onLanguageSelected(language.code) }
                    )
                }

                if (filteredLanguages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalDialogSecondaryTextColor.current.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
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
    onClick: () -> Unit
) {
    val stateDescription = stringResource(
        if (isSelected) R.string.selected else R.string.not_selected
    )
    RadioSelectionCard(
        selected = isSelected,
        onSelect = onClick,
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
                color = LocalDialogTextColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalDialogSecondaryTextColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
