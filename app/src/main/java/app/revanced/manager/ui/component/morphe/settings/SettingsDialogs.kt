package app.revanced.manager.ui.component.morphe.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.revanced.manager.network.downloader.DownloaderPluginState
import app.revanced.manager.ui.component.PasswordField
import app.revanced.manager.ui.component.morphe.home.MorpheDialog
import app.revanced.manager.ui.viewmodel.AboutViewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.security.MessageDigest

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
        header = {
            // Fixed header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App Icon with gradient background
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
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
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                // App Name & Version
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        footer = {
            // Fixed footer - close button
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.revanced_manager_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                    )
                }
            }

            // Social Links
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                AboutViewModel.socials.forEach { link ->
                    SocialIconButton(
                        icon = AboutViewModel.getSocialIcon(link.name),
                        contentDescription = link.name,
                        onClick = { uriHandler.openUri(link.url) }
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
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isPressed) 12.dp else 4.dp,
        shadowElevation = if (isPressed) 8.dp else 2.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Plugin management dialog
 * Shows plugin details and management options
 */
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun PluginActionDialog(
    packageName: String,
    state: DownloaderPluginState?,
    onDismiss: () -> Unit,
    onTrust: () -> Unit,
    onRevoke: () -> Unit,
    onUninstall: () -> Unit,
    onViewError: () -> Unit
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }

    val signature = remember(packageName) {
        runCatching {
            val androidSignature = pm.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners?.firstOrNull()

            if (androidSignature != null) {
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(androidSignature.toByteArray())
                hash.joinToString(":") { "%02X".format(it) }
            } else {
                "Unknown"
            }
        }.getOrNull() ?: "Unknown"
    }

    MorpheDialog(
        onDismissRequest = onDismiss,
        title = when (state) {
            is DownloaderPluginState.Loaded -> stringResource(R.string.downloader_plugin_revoke_trust_dialog_title)
            is DownloaderPluginState.Failed -> stringResource(R.string.downloader_plugin_state_failed)
            is DownloaderPluginState.Untrusted -> stringResource(R.string.downloader_plugin_trust_dialog_title)
            else -> packageName
        },
        footer = {
            // Fixed footer - buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Action and Uninstall buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    // Action button (Trust/Revoke/View Error)
                    when (state) {
                        is DownloaderPluginState.Loaded -> {
                            FilledTonalButton(
                                onClick = {
                                    onRevoke()
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.continue_))
                            }
                        }
                        is DownloaderPluginState.Untrusted -> {
                            FilledTonalButton(
                                onClick = {
                                    onTrust()
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.continue_))
                            }
                        }
                        is DownloaderPluginState.Failed -> {
                            FilledTonalButton(onClick = onViewError) {
                                Text(stringResource(R.string.downloader_plugin_view_error))
                            }
                        }
                        else -> {}
                    }

                    // Uninstall button
                    OutlinedButton(
                        onClick = {
                            onUninstall()
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.uninstall))
                    }
                }

                // Dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is DownloaderPluginState.Failed -> {
                    Text(
                        text = stringResource(R.string.downloader_plugin_failed_dialog_body, packageName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    Text(
                        text = "Package:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Signature (SHA-256):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = signature,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }
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
            // Fixed footer - buttons (always in row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSubmit(alias, pass) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.import_keystore_dialog_button),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(android.R.string.cancel),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        }
    ) {
        // Scrollable content
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.import_keystore_dialog_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Alias Input
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(stringResource(R.string.import_keystore_dialog_alias_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Password Input
            PasswordField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.import_keystore_dialog_password_field)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
