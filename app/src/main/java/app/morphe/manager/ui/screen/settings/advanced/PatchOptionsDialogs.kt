/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.advanced

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PatchOptionKeys
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_HEADER_INSTRUCTION
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.CUSTOM_ICON_INSTRUCTION
import app.morphe.manager.domain.manager.getLocalizedOrCustomText
import app.morphe.manager.patcher.patch.ExplicitOptionKind
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.OptionInfo
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import app.morphe.manager.util.KnownApps

/**
 * Custom branding dialog: the name and icon the patched app is built with, with the adaptive icon
 * creator for an icon of one's own.
 */
@Composable
fun DialogHostScope.CustomBrandingDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    packageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Get current values from preferences
    val appName = remember { mutableStateOf(patchOptionsPrefs.customAppName(packageName).getBlocking()) }
    val iconPath = remember { mutableStateOf(patchOptionsPrefs.customIconPath(packageName).getBlocking()) }
    val appIconStyle = remember { mutableStateOf(patchOptionsPrefs.appIconStyle(packageName).getBlocking()) }

    // Get branding options from bundle
    val brandingOptions = patchOptionsViewModel.getBrandingOptions(packageName)
    val appNameOption = patchOptionsViewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_NAME)
    val iconOption = patchOptionsViewModel.getOption(brandingOptions, PatchOptionKeys.CUSTOM_ICON)
    val appIconStyles = patchOptionsViewModel
        .getOption(brandingOptions, PatchOptionKeys.APP_ICON)
        ?.presets
        .orEmpty()

    // The custom icon style has no images of its own, so the patch fails the run without a folder
    val missingCustomIcon = appIconStyle.value == PatchOptionKeys.APP_ICON_CUSTOM &&
            iconPath.value.isBlank()

    SettingsOptionsDialog(
        packageName = packageName,
        title = stringResource(R.string.settings_advanced_patch_options_custom_branding),
        hasOptions = appNameOption != null || iconOption != null || appIconStyles.isNotEmpty(),
        saveEnabled = !missingCustomIcon,
        onSave = {
            patchOptionsViewModel.saveCustomBranding(
                prefs = patchOptionsPrefs,
                packageName = packageName,
                appName = appName.value,
                iconPath = iconPath.value,
                appIconStyle = appIconStyle.value,
                onDone = onDismiss
            )
        },
        onDismiss = onDismiss
    ) {
        // App name field
        if (appNameOption != null) {
            OptionCard(
                OptionHeading(
                    title = stringResource(R.string.settings_advanced_patch_options_custom_branding_app_name),
                    description = rememberTranslated(appNameOption.description),
                    required = appNameOption.required,
                    missing = appNameOption.required && appName.value.isBlank()
                )
            ) {
                AppDialogTextField(
                    value = appName.value,
                    onValueChange = { appName.value = it },
                    placeholder = { Text(stringResource(R.string.settings_advanced_patch_options_custom_branding_app_name_hint)) },
                    showClearButton = true
                )
            }
        }

        // App icon style, the icon the patched app is built with
        if (appIconStyles.isNotEmpty()) {
            OptionCard(
                OptionHeading(
                    title = stringResource(R.string.settings_advanced_patch_options_custom_branding_app_icon),
                    description = stringResource(R.string.settings_advanced_patch_options_custom_branding_app_icon_description),
                    required = false,
                    missing = missingCustomIcon
                )
            ) {
                DropdownOptionField(
                    value = appIconStyle.value,
                    presets = appIconStyles,
                    // The patch builds only the icon styles it ships
                    allowCustomValue = false,
                    onValueChange = { appIconStyle.value = it?.toString().orEmpty() }
                )

                if (missingCustomIcon) {
                    Notice(
                        text = stringResource(R.string.settings_advanced_patch_options_custom_branding_app_icon_needs_folder),
                        tone = SemanticTone.Error,
                        density = NoticeDensity.Compact
                    )
                }
            }
        }

        // Icon folder, with the creator for an icon of one's own
        if (iconOption != null) {
            FolderOptionCard(
                heading = folderHeading(
                    option = iconOption,
                    title = stringResource(R.string.settings_advanced_patch_options_custom_branding_custom_icon),
                    value = iconPath.value,
                    missing = missingCustomIcon
                ),
                value = iconPath.value,
                typed = iconOption.explicitKind == ExplicitOptionKind.Folder,
                asset = OptionAsset.Icon,
                packageName = packageName,
                onValueChange = { iconPath.value = it },
                placeholder = "/storage/emulated/0/icons",
                instructions = getLocalizedOrCustomText(
                    context,
                    iconOption.description,
                    CUSTOM_ICON_INSTRUCTION,
                    R.string.settings_advanced_patch_options_custom_branding_custom_icon_instruction
                )
            )
        }
    }
}

/**
 * Custom header dialog: the header the patched app shows, with the header creator for one of
 * one's own.
 */
@Composable
fun DialogHostScope.CustomHeaderDialog(
    patchOptionsPrefs: PatchOptionsPreferencesManager,
    patchOptionsViewModel: PatchOptionsViewModel,
    packageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val headerPath = remember {
        mutableStateOf(patchOptionsPrefs.customHeaderPath(packageName).getBlocking())
    }

    // Get header options from bundle
    val headerOptions = patchOptionsViewModel.getHeaderOptions(packageName)
    val customOption = patchOptionsViewModel.getOption(headerOptions, PatchOptionKeys.CUSTOM_HEADER)

    SettingsOptionsDialog(
        packageName = packageName,
        title = stringResource(R.string.settings_advanced_patch_options_custom_header),
        hasOptions = customOption != null,
        onSave = {
            patchOptionsViewModel.saveCustomHeader(
                prefs = patchOptionsPrefs,
                packageName = packageName,
                headerPath = headerPath.value,
                onDone = onDismiss
            )
        },
        onDismiss = onDismiss
    ) {
        if (customOption != null) {
            FolderOptionCard(
                heading = folderHeading(
                    option = customOption,
                    title = stringResource(R.string.settings_advanced_patch_options_custom_header),
                    value = headerPath.value
                ),
                value = headerPath.value,
                typed = customOption.explicitKind == ExplicitOptionKind.Folder,
                asset = OptionAsset.Header,
                packageName = packageName,
                onValueChange = { headerPath.value = it },
                placeholder = "/storage/emulated/0/header",
                instructions = getLocalizedOrCustomText(
                    context,
                    customOption.description,
                    CUSTOM_HEADER_INSTRUCTION,
                    R.string.settings_advanced_patch_options_custom_header_instruction
                )
            )
        }
    }
}

/**
 * Heading of a folder option kept in settings, which folds its description away as instructions.
 *
 * @param missing Whether the folder is wanted even where the option itself is not required.
 */
private fun folderHeading(option: OptionInfo, title: String, value: String, missing: Boolean = false) =
    OptionHeading(
        title = title,
        description = "",
        required = option.required,
        missing = missing || (option.required && value.isBlank())
    )

/**
 * Dialog of the options kept in settings for one app, headed by the app as the options edited
 * while patching are, with its options as cards. Saved as a whole rather than one edit at a time,
 * as they are settings the next patch run reads.
 *
 * @param hasOptions Whether the app's patches offer any of the options, which it says instead.
 */
@Composable
private fun DialogHostScope.SettingsOptionsDialog(
    packageName: String,
    title: String,
    hasOptions: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ListDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        header = {
            ListDialogHeader(
                icon = { modifier ->
                    AppIcon(packageName = packageName, contentDescription = null, modifier = modifier)
                },
                title = title,
                subtitle = KnownApps.getAppName(packageName),
                alwaysShowBand = true
            )
        },
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.save),
                onPrimaryClick = onSave,
                primaryEnabled = saveEnabled && hasOptions,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        },
        hideFooterWhileTyping = true
    ) {
        DialogScrollColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Spaced as the options edited while patching
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            if (hasOptions) {
                content()
            } else {
                EmptyState(
                    message = stringResource(R.string.settings_advanced_patch_options_no_available),
                    icon = Icons.Outlined.Tune
                )
            }
        }
    }
}
