/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.media.MediaPlayer
import android.view.Surface
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import app.morphe.manager.util.PresetPreviewResolver
import java.io.File
import app.morphe.manager.R

@Composable
private fun morpheDialogTextFieldColors(textColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textColor,
    unfocusedTextColor = textColor,
    disabledTextColor = textColor.copy(alpha = 0.6f),
    focusedBorderColor = textColor.copy(alpha = 0.5f),
    unfocusedBorderColor = textColor.copy(alpha = 0.2f),
    disabledBorderColor = textColor.copy(alpha = 0.1f),
    cursorColor = textColor,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedLeadingIconColor = textColor.copy(alpha = 0.7f),
    unfocusedLeadingIconColor = textColor.copy(alpha = 0.5f),
    focusedTrailingIconColor = textColor.copy(alpha = 0.7f),
    unfocusedTrailingIconColor = textColor.copy(alpha = 0.5f),
    focusedLabelColor = textColor.copy(alpha = 0.7f),
    unfocusedLabelColor = textColor.copy(alpha = 0.5f),
    focusedPlaceholderColor = textColor.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = textColor.copy(alpha = 0.4f)
)

/**
 * Styled [OutlinedTextField] for dialogs with proper theming.
 * Supports password visibility toggle and clear button.
 */
@Composable
fun AppDialogTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    showClearButton: Boolean = false,
    onFolderPickerClick: (() -> Unit)? = null,
    onFilePickerClick: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (isPassword || showClearButton || onFolderPickerClick != null || onFilePickerClick != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                    modifier = Modifier.padding(end = Defaults.SpaceExtraSmall)
                ) {
                    // Password visibility toggle
                    if (isPassword) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.settings_system_hide_password_field)
                                } else {
                                    stringResource(R.string.settings_system_show_password_field)
                                }
                            )
                        }
                    }

                    // Clear button
                    if (showClearButton && value.isNotBlank()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }

                    // Folder picker button
                    if (onFolderPickerClick != null) {
                        IconButton(
                            onClick = onFolderPickerClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.select_folder)
                            )
                        }
                    }

                    // File picker button
                    if (onFilePickerClick != null) {
                        IconButton(
                            onClick = onFilePickerClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                contentDescription = stringResource(R.string.select_file)
                            )
                        }
                    }
                }
            } else {
                trailingIcon?.invoke()
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Defaults.CornerMedium),
        colors = morpheDialogTextFieldColors(dialogTextColor())
    )
}

/**
 * Styled [OutlinedTextField] with dropdown menu support for dialogs.
 * Combines text input, folder picker, clear button, and dropdown selection.
 *
 * The field takes typed input and the arrow opens the presets, each announced on its own to a
 * screen reader. With [allowCustomValue] off the field is read-only and opens the presets itself.
 *
 * Until the field is focused it shows the name of the preset its value matches. Focusing it on a
 * preset starts an empty input, so a custom value replaces the preset rather than being typed
 * onto it, and an empty field shows the value in effect as its placeholder. A caller may turn an
 * empty value back into a default, so the typed text is kept by the field rather than read back
 * from [value], and clearing it leaves it empty.
 *
 * @param dropdownItems Map of display name to value shown in the dropdown menu.
 * @param allowCustomValue Whether a value besides [dropdownItems] can be typed in.
 */
@Composable
fun AppDialogDropdownTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    dropdownItems: Map<String, String>,
    allowCustomValue: Boolean = true,
    thumbnailFor: ((String) -> Any?)? = null,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    showClearButton: Boolean = false,
    onFolderPickerClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var expanded by remember { mutableStateOf(false) }
    // Text typed while the field is focused, null otherwise
    var input by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val presetName = dropdownItems.entries.find { it.value == value }?.key
    val typed = input
    val displayValue = when {
        typed == null -> presetName ?: value
        // A cleared field the caller filled back with a preset stays cleared
        value == typed || (typed.isBlank() && presetName != null) -> typed
        // Set from outside while typing, such as by the folder picker
        else -> value
    }
    val inEffect = value.takeIf { it.isNotBlank() && it != displayValue } ?: presetName
    val changeInput: (String) -> Unit = { text ->
        if (input != null) input = text
        onValueChange(text)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = changeInput,
            readOnly = !allowCustomValue,
            label = label,
            placeholder = placeholder ?: inEffect?.let { text ->
                { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            leadingIcon = leadingIcon,
            singleLine = singleLine,
            trailingIcon = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = Defaults.SpaceExtraSmall)
                ) {
                    // Folder picker button
                    if (onFolderPickerClick != null) {
                        IconButton(
                            onClick = onFolderPickerClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = stringResource(R.string.select_folder)
                            )
                        }
                    }

                    // Clear button, hidden while the field already shows nothing
                    if (showClearButton && displayValue.isNotBlank()) {
                        IconButton(
                            onClick = { changeInput("") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.clear)
                            )
                        }
                    }

                    // Dropdown arrow. On an editable field it is the only anchor, so opening the
                    // presets leaves the field unfocused and the keyboard closed
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (allowCustomValue) {
                                    Modifier.menuAnchor(
                                        ExposedDropdownMenuAnchorType.SecondaryEditable,
                                        enabled
                                    )
                                } else Modifier
                            )
                    ) {
                        Icon(
                            imageVector = if (expanded)
                                Icons.Outlined.ExpandLess
                            else
                                Icons.Outlined.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            },
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (allowCustomValue) {
                        Modifier.onFocusChanged { state ->
                            input = when {
                                !state.isFocused -> null
                                input != null -> input
                                presetName != null -> ""
                                else -> value
                            }
                        }
                    } else {
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    }
                ),
            shape = RoundedCornerShape(Defaults.CornerMedium),
            colors = morpheDialogTextFieldColors(dialogTextColor())
        )

        AppExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dropdownItems.forEach { (displayName, itemValue) ->
                AppDropdownMenuItem(
                    text = displayName,
                    selected = itemValue == value,
                    onClick = {
                        onValueChange(itemValue)
                        expanded = false
                        // A picked preset ends any typing, so the field shows its name again
                        focusManager.clearFocus()
                    },
                    leading = thumbnailFor?.invoke(itemValue)?.let { model -> { PresetThumbnail(model) } }
                )
            }
        }
    }
}

/**
 * Optional preset thumbnail beside a dropdown entry. The model is anything Coil
 * renders (file, uri, drawable resource id). Video files play muted loops.
 * Null means the row stays text only, exactly as it does without this slot.
 */
@Composable
internal fun PresetThumbnail(model: Any?) {
    if (model is File && PresetPreviewResolver.isVideoPreview(model)) {
        PresetVideoThumbnail(model)
        return
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
    )
}

/**
 * Muted players prepared ahead of the menu opening, keyed by file path, so
 * rows start instantly instead of paying decode init on open. Must be touched
 * from the main thread: MediaPlayer delivers its prepared callback there.
 * Released when the options dialog closes.
 */
internal object PresetVideoPrewarmer {
    private data class Entry(var player: MediaPlayer?, var prepared: Boolean)

    private val lock = Any()
    private val players = mutableMapOf<String, Entry>()
    var preparedVersion by mutableStateOf(0)
        private set

    fun prewarm(file: File) {
        val key = file.absolutePath
        synchronized(lock) {
            if (players.containsKey(key)) return
            val entry = Entry(null, false)
            players[key] = entry
            try {
                val player = MediaPlayer()
                entry.player = player
                player.setDataSource(key)
                player.setVolume(0f, 0f)
                player.isLooping = true
                player.setOnPreparedListener {
                    synchronized(lock) { entry.prepared = true }
                    preparedVersion++
                }
                player.prepareAsync()
            } catch (e: Exception) {
                players.remove(key)
                try {
                    entry.player?.release()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun prepared(file: File): MediaPlayer? = synchronized(lock) {
        players[file.absolutePath]?.takeIf { it.prepared }?.player
    }

    fun releaseAll() = synchronized(lock) {
        players.values.forEach {
            try {
                it.player?.release()
            } catch (ignored: Exception) {
            }
        }
        players.clear()
    }
}

/** Muted looping preview clip on a pre-warmed player, composited in window
 * so it animates with the menu instead of punching through it. Shows nothing
 * until its player is prepared. Failures stay silent and render nothing. */
@Composable
private fun PresetVideoThumbnail(file: File) {
    PresetVideoPrewarmer.preparedVersion.let {
        val player = PresetVideoPrewarmer.prepared(file) ?: run {
            Spacer(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            return
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                val tv = TextureView(ctx)
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        try {
                            player.setSurface(Surface(surface))
                            if (!player.isPlaying) player.start()
                        } catch (e: Exception) {
                        }
                    }
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            if (player.isPlaying) player.pause()
                            player.setSurface(null)
                        } catch (e: Exception) {
                        }
                        return true
                    }
                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
                tv
            },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            onRelease = { tv -> tv.surfaceTextureListener = null }
        )
    }
}
