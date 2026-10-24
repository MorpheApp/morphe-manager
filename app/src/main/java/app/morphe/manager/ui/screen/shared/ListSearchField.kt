/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*

/**
 * Search field for the app's lists, a pill as tall as the title actions beside it and filled with
 * the same glass, so the search toggle reads as drawn out into it.
 *
 * @param requestFocus Opens the keyboard on first composition, for fields revealed by a toggle.
 * @param trailing Laid out after the field's end, such as room for the toggle closing it.
 */
@Composable
fun ListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requestFocus: Boolean = false,
    container: Color = HeaderControlDefaults.container(),
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val textColor = dialogTextColor()
    val iconColor = textColor.copy(alpha = 0.7f)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)

    TextFieldLine {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            modifier = modifier
                .fillMaxWidth()
                .height(HeaderControlDefaults.Size)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(container),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The placeholder already names the field, so the icon stays decorative
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Search,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .padding(start = Defaults.SpaceSmall, end = Defaults.SpaceSmall)
                            .size(Defaults.IconSize)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = label,
                                style = textStyle,
                                color = textColor.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.size(HeaderControlDefaults.Size)
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = iconColor
                            )
                        }
                    } else {
                        // Leaves the text the same room from the end as the icon leaves it from the start
                        Spacer(Modifier.width(Defaults.SpaceSmall))
                    }
                    trailing?.invoke(this)
                }
            }
        )
    }
}
