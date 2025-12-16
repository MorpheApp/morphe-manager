package app.revanced.manager.ui.component.morphe.shared

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Apply appropriate width constraints based on orientation
 * Portrait: fill width with horizontal padding
 * Landscape: limited width for better readability
 */
@Composable
private fun Modifier.dialogWidth(): Modifier {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) {
        this
            .widthIn(max = 600.dp)
            .padding(horizontal = 24.dp)
    } else {
        this
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    }
}

/**
 * Apply appropriate height constraints based on orientation
 * Portrait: expand to content size, max 90% of screen height
 * Landscape: limit height for better readability
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun Modifier.dialogHeight(): Modifier {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) {
        this.heightIn(max = 600.dp)
    } else {
        this.wrapContentHeight().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f)
    }
}

/**
 * Standardized dialog wrapper for Morphe UI
 * Provides consistent styling and responsive width across all dialogs
 * Supports scrollable content with fixed header and footer
 *
 * @param onDismissRequest Called when user dismisses the dialog
 * @param title Optional title - stays fixed at top
 * @param header Optional header content (icon + title) - stays fixed at top
 * @param footer Optional footer content (buttons) - stays fixed at bottom
 * @param content Scrollable dialog content
 */
@Composable
fun MorpheDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .dialogWidth()
                .dialogHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Fixed header
                Box(modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) {
                    when {
                        header != null -> {
                            Box(modifier = Modifier.padding(top = 24.dp)) {
                                header()
                            }
                        }
                        title != null -> {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Scrollable content
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(
                            top = if ((header == null) && (title == null)) 24.dp else 16.dp,
                            bottom = if (footer == null) 24.dp else 16.dp
                        )
                ) {
                    content()
                }

                // Fixed footer
                footer?.let {
                    Box(modifier = Modifier.padding(bottom = 24.dp, start = 24.dp, end = 24.dp)) {
                        footer()
                    }
                }
            }
        }
    }
}
