package app.revanced.manager.ui.component.bundle

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.ui.component.AlertDialogExtended
import app.revanced.manager.ui.component.TextHorizontalPadding
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.ui.component.haptics.HapticRadioButton
import app.revanced.manager.util.openUrl
import app.revanced.manager.util.transparentListItemColors

private enum class BundleType {
    Local,
    Remote
}

@Composable
fun ImportPatchBundleDialog(
    onDismiss: () -> Unit,
    onRemoteSubmit: (String, Boolean) -> Unit,
    onLocalSubmit: (String) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var bundleType by rememberSaveable { mutableStateOf(BundleType.Remote) }
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var autoUpdate by rememberSaveable { mutableStateOf(true) }

    val steps = listOf<@Composable () -> Unit>(
        {
            SelectBundleTypeStep(bundleType) { selectedType ->
                bundleType = selectedType
            }
        },
        {
            ImportBundleStep(
                bundleType,
                selectedLocalPath,
                remoteUrl,
                autoUpdate,
                onLocalPick,
                { remoteUrl = it },
                { autoUpdate = it }
            )
        }
    )

    val inputsAreValid by remember(bundleType, selectedLocalPath, remoteUrl) {
        derivedStateOf {
            (bundleType == BundleType.Local && !selectedLocalPath.isNullOrBlank()) ||
                (bundleType == BundleType.Remote && remoteUrl.isNotBlank())
        }
    }

    AlertDialogExtended(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (currentStep == 0) R.string.select else R.string.add_patches))
        },
        text = {
            steps[currentStep]()
        },
        confirmButton = {
            if (currentStep == steps.lastIndex) {
                TextButton(
                    enabled = inputsAreValid,
                    onClick = {
                        when (bundleType) {
                            BundleType.Local -> selectedLocalPath?.let(onLocalSubmit)
                            BundleType.Remote -> onRemoteSubmit(remoteUrl, autoUpdate)
                        }
                    }
                ) {
                    Text(stringResource(R.string.add))
                }
            } else {
                TextButton(onClick = { currentStep++ }) {
                    Text(stringResource(R.string.next))
                }
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep-- }) {
                    Text(stringResource(R.string.back))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
        textHorizontalPadding = PaddingValues(0.dp)
    )
}

@Composable
private fun SelectBundleTypeStep(
    bundleType: BundleType,
    onBundleTypeSelected: (BundleType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.select_patches_type_dialog_description)
        )
        Column {
            ListItem(
                modifier = Modifier.clickable(
                    role = Role.RadioButton,
                    onClick = { onBundleTypeSelected(BundleType.Remote) }
                ),
                headlineContent = { Text(stringResource(R.string.enter_url)) },
                overlineContent = { Text(stringResource(R.string.recommended)) },
                supportingContent = { Text(stringResource(R.string.remote_patches_description)) },
                leadingContent = {
                    HapticRadioButton(
                        selected = bundleType == BundleType.Remote,
                        onClick = null
                    )
                },
                colors = transparentListItemColors
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                modifier = Modifier.clickable(
                    role = Role.RadioButton,
                    onClick = { onBundleTypeSelected(BundleType.Local) }
                ),
                headlineContent = { Text(stringResource(R.string.select_from_storage)) },
                supportingContent = { Text(stringResource(R.string.local_patches_description)) },
                overlineContent = { },
                leadingContent = {
                    HapticRadioButton(
                        selected = bundleType == BundleType.Local,
                        onClick = null
                    )
                },
                colors = transparentListItemColors
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportBundleStep(
    bundleType: BundleType,
    patchBundlePath: String?,
    remoteUrl: String,
    autoUpdate: Boolean,
    launchPatchActivity: () -> Unit,
    onRemoteUrlChange: (String) -> Unit,
    onAutoUpdateChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Column {
        when (bundleType) {
            BundleType.Local -> {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.patch_bundle))
                        },
                        supportingContent = {
                            Text(text = patchBundlePath ?: stringResource(R.string.file_field_not_set))
                        },
                        trailingContent = {
                            IconButton(onClick = launchPatchActivity) {
                                Icon(imageVector = Icons.Default.Topic, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { launchPatchActivity() },
                        colors = transparentListItemColors
                    )
                }
            }

            BundleType.Remote -> {
                Column(
                    modifier = Modifier.padding(TextHorizontalPadding)
                ) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = onRemoteUrlChange,
                        label = { Text(stringResource(R.string.patches_url)) }
                    )
                    // Morphe
//                    Spacer(modifier = Modifier.height(10.dp))
//                    PatchBundleUrlsCard(
//                        onClick = {
//                            context.openUrl(
//                                "https://github.com/Jman-Github/ReVanced-Patch-Bundles/tree/bundles#-patch-bundles-urls"
//                            )
//                        }
//                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    ListItem(
                        modifier = Modifier.clickable(
                            role = Role.Checkbox,
                            onClick = { onAutoUpdateChange(!autoUpdate) }
                        ),
                        headlineContent = { Text(stringResource(R.string.auto_update)) },
                        leadingContent = {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                HapticCheckbox(
                                    checked = autoUpdate,
                                    onCheckedChange = {
                                        onAutoUpdateChange(!autoUpdate)
                                    }
                                )
                            }
                        },
                        colors = transparentListItemColors
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchBundleUrlsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = ButtonDefaults.outlinedButtonBorder,
        modifier = modifier
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // Morphe
                text = "", // stringResource(R.string.patch_bundle_urls_link),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null
            )
        }
    }
}
