package app.revanced.manager.ui.component.morphe.settings

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.ui.component.PasswordField
import app.revanced.manager.ui.component.morphe.shared.*
import app.revanced.manager.ui.viewmodel.AboutViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * About dialog
 * Shows app icon, version, description, and social links
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    MorpheDialog(
        onDismissRequest = onDismiss,
        footer = {
            MorpheDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Icon with gradient background
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            )
                    )
                }
                val icon = rememberDrawablePainter(
                    drawable = remember {
                        AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
                    }
                )
                Image(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            // App Name & Version
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
            }

            // Description
            Text(
                text = stringResource(R.string.revanced_manager_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            // Social Links
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                AboutViewModel.socials.forEach { link ->
                    SocialIconButton(
                        icon = AboutViewModel.getSocialIcon(link.name),
                        contentDescription = link.name,
                        onClick = { uriHandler.openUri(link.url) },
                        textColor = textColor
                    )
                }
            }
        }
    }
}

/**
 * Social link button
 * Styled button for opening social media links
 */
@Composable
private fun SocialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = textColor.copy(alpha = 0.1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Keystore Credentials Dialog
 * Allows entering alias and password for keystore import
 */
@Composable
fun KeystoreCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var alias by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.import_keystore_dialog_title),
        footer = {
            MorpheDialogButtonRow(
                primaryText = stringResource(R.string.import_keystore_dialog_button),
                onPrimaryClick = { onSubmit(alias, pass) },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.import_keystore_dialog_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            // Alias Input
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = {
                    Text(
                        stringResource(R.string.import_keystore_dialog_alias_field),
                        color = secondaryColor
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = textColor.copy(alpha = 0.5f),
                    unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                    cursorColor = textColor
                )
            )

            // Password Input
            PasswordField(
                value = pass,
                onValueChange = { pass = it },
                label = {
                    Text(
                        stringResource(R.string.import_keystore_dialog_password_field),
                        color = secondaryColor
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
