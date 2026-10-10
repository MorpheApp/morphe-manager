/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.apk.InstalledApkInfo
import app.morphe.manager.domain.apk.SavedApkInfo
import app.morphe.manager.domain.bundles.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.androidVersionName
import app.morphe.manager.util.htmlAnnotatedString
import app.morphe.manager.util.withVersionPrefix
import app.morphe.patcher.patch.AppTarget

/**
 * Dialog 1: Initial "Do you have the APK?" dialog.
 *
 * In expert mode the version list is selectable: the user can tap any version to set it as the
 * download target. [selectedDownloadVersion] reflects the current selection (defaults to
 * [recommendedVersion]); [onVersionSelect] propagates the change to the ViewModel.
 * In simple mode there is only one version and no selection UI is shown.
 */
@Composable
internal fun ApkAvailabilityDialog(
    appName: String,
    packageName: String?,
    recommendedVersion: AppTarget?,
    compatibleVersions: List<BundledAppTarget>,
    selectedDownloadVersion: AppTarget?,
    onVersionSelect: (AppTarget) -> Unit,
    usingMountInstall: Boolean,
    stockAppInstalled: Boolean,
    isExpertMode: Boolean,
    savedApkInfo: SavedApkInfo?,
    installedApkInfo: InstalledApkInfo?,
    installedAppVersion: String?,
    onDismiss: () -> Unit,
    onHaveApk: () -> Unit,
    onNeedApk: () -> Unit,
    onUseSaved: () -> Unit,
    onUseInstalled: () -> Unit
) {
    // Every check below still runs against the full set: an APK already on the device stays
    // usable whatever the experimental toggle says, it is only the picker that narrows
    val offeredVersions = remember(compatibleVersions) { compatibleVersions.offered() }

    // The installed APK is dropped upstream when the patches do not target it, but a saved
    // copy is offered whatever it is: it may be the only APK the user still has
    val savedApkMatchesTargets = remember(savedApkInfo, compatibleVersions) {
        savedApkInfo == null ||
            compatibleVersions.patchableAt(savedApkInfo.version, savedApkInfo.versionCode)
    }

    // The build code is worth printing only where it is the whole difference: the targets name
    // this version, and refuse this build of it. A version they do not name at all is already
    // visible in the version beside the button
    val savedApkBuildRefused = remember(savedApkInfo, savedApkMatchesTargets, compatibleVersions) {
        savedApkInfo != null && !savedApkMatchesTargets &&
            compatibleVersions.any { it.target.version == savedApkInfo.version }
    }

    // What the list below prints: the version on the device is called out under it only when
    // the list is not already showing that version
    val listedVersions = remember(isExpertMode, offeredVersions, recommendedVersion) {
        if (isExpertMode && offeredVersions.isNotEmpty()) {
            offeredVersions.mapNotNullTo(mutableSetOf()) { it.target.version }
        } else {
            setOfNotNull(recommendedVersion?.version)
        }
    }
    val unlistedInstalledVersion = installedAppVersion?.takeIf { it !in listedVersions }

    // Versions whose minSdk exceeds the current device - shown greyed-out and non-selectable
    val incompatibleSdkVersions: Set<String> = remember(offeredVersions) {
        offeredVersions
            .filterNot { it.installableOnDevice() }
            .mapNotNullTo(mutableSetOf()) { it.target.version }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_apk_availability_dialog_title),
        padding = DialogPadding.Compact,
        footer = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main action buttons
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.home_apk_availability_yes),
                    onPrimaryClick = onNeedApk,
                    primaryIcon = Icons.Outlined.Download,
                    secondaryText = stringResource(R.string.home_apk_availability_no),
                    onSecondaryClick = onHaveApk,
                    secondaryIcon = Icons.Outlined.Check,
                    layout = DialogButtonLayout.Vertical
                )

                // When saved and installed APKs share the same version, prefer the saved copy.
                // Hide the installed button in that case to avoid showing two equivalent sources
                val preferSavedOverInstalled = savedApkInfo != null &&
                    savedApkInfo.version == installedApkInfo?.version

                // Saved APK button - always shown when a saved APK exists
                if (savedApkInfo != null) {
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.home_apk_use_saved),
                        textSuffix = if (savedApkBuildRefused) {
                            buildVersionSuffix(savedApkInfo.version, savedApkInfo.versionCode)
                        } else {
                            "v${savedApkInfo.version}"
                        },
                        onClick = onUseSaved,
                        icon = Icons.Outlined.History,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Taking it leads to the unsupported-version dialog, which is a wasted tap
                    // unless the user is told here what is wrong with the copy they kept
                    if (!savedApkMatchesTargets) {
                        Notice(
                            text = stringResource(R.string.home_apk_use_saved_unsupported),
                            tone = SemanticTone.Warning,
                            icon = Icons.Outlined.Warning,
                            density = NoticeDensity.Compact
                        )
                    }
                }

                // Installed APK button - hidden when saved mono-APK covers the same split version
                if (installedApkInfo != null && !preferSavedOverInstalled) {
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.home_apk_use_installed),
                        // Never the wrong build: an installed APK the patches do not target is
                        // dropped before it reaches this dialog
                        textSuffix = "v${installedApkInfo.version}",
                        onClick = onUseInstalled,
                        icon = Icons.Outlined.PhoneAndroid,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // The certificate check could not run, so the installed app may already be patched
                    if (installedApkInfo.patchStateUnknown) {
                        Notice(
                            text = stringResource(R.string.home_apk_use_installed_unverified),
                            tone = SemanticTone.Warning,
                            icon = Icons.Outlined.Warning,
                            density = NoticeDensity.Compact
                        )
                    }
                }
            }
        }
    ) {
        val secondaryColor = dialogSecondaryTextColor()
        val anyString = stringResource(R.string.any_version)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isExpertMode && offeredVersions.isNotEmpty()) {
                // Expert mode: selectable version list
                Text(
                    text = htmlAnnotatedString(stringResource(
                        R.string.home_apk_availability_dialog_expert,
                        appName
                    )),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                if (offeredVersions.size > 1) {
                    SelectableVersionListCard(
                        versions = offeredVersions,
                        selectedVersion = selectedDownloadVersion,
                        onVersionSelect = onVersionSelect,
                        anyString = anyString,
                        hasMultipleBundles = offeredVersions.map { it.bundleUid }.distinct().size > 1,
                        incompatibleSdkVersions = incompatibleSdkVersions,
                        savedVersion = savedApkInfo?.version,
                        installedVersion = installedAppVersion,
                    )
                } else {
                    VersionListCard(
                        versions = offeredVersions.map { it.target.version ?: anyString },
                        experimentalVersions = offeredVersions.experimentalVersions(),
                        descriptions = offeredVersions
                            .mapNotNull { b -> b.target.version?.let { v -> b.target.description?.let { d -> v to d } } }
                            .toMap(),
                        incompatibleSdkVersions = incompatibleSdkVersions,
                        versionCodes = offeredVersions
                            .mapNotNull { b ->
                                val v = b.target.version ?: return@mapNotNull null
                                val codes = b.buildCodes ?: return@mapNotNull null
                                v to codes
                            }
                            .toMap(),
                        savedVersion = savedApkInfo?.version,
                        installedVersion = installedAppVersion,
                    )
                }
            } else {
                // Simple mode: single static version, no selection
                Text(
                    text = htmlAnnotatedString(stringResource(
                        R.string.home_apk_availability_dialog_simple,
                        appName
                    )),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                    textAlign = TextAlign.Center
                )

                VersionListCard(
                    versions = listOf(recommendedVersion?.version ?: anyString),
                    showUnpatchedBadge = true,
                    versionCodes = compatibleVersions
                        .firstOrNull { it.target.version == recommendedVersion?.version }
                        ?.let { b -> b.target.version?.let { v -> b.buildCodes?.let { mapOf(v to it) } } }
                        ?: emptyMap(),
                    savedVersion = savedApkInfo?.version,
                    installedVersion = installedAppVersion
                )
            }

            // For reference only, so neutral rather than a warning
            unlistedInstalledVersion?.let {
                Notice(
                    text = stringResource(
                        R.string.home_apk_availability_installed_version,
                        it.withVersionPrefix()
                    ),
                    tone = SemanticTone.Neutral,
                    icon = Icons.Outlined.InstallMobile,
                    density = NoticeDensity.Compact
                )
            }

            // Root mode warning - only when there is no app on the device to mount over
            if (usingMountInstall && !stockAppInstalled) {
                Notice(
                    text = stringResource(R.string.root_install_apk_required),
                    tone = SemanticTone.Warning,
                    icon = Icons.Outlined.Warning
                )
            }
        }
    }
}

/**
 * Dialog 3: File picker prompt dialog.
 */
@Composable
internal fun FilePickerPromptDialog(
    appName: String,
    packageName: String?,
    isOtherApps: Boolean,
    isLoadingInstalledApps: Boolean,
    onDismiss: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onUseInstalledApp: (() -> Unit)?
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(
            if (isOtherApps) {
                R.string.home_select_apk_title
            } else {
                R.string.home_file_picker_prompt_title
            }
        ),
        description = if (isOtherApps) {
            stringResource(R.string.home_select_any_apk_description)
        } else {
            htmlAnnotatedString(stringResource(R.string.home_file_picker_prompt_description, appName))
        },
        footer = {
            AppDialogActions(
                actions = buildList {
                    if (isOtherApps && onUseInstalledApp != null) {
                        add(
                            DialogAction(
                                text = stringResource(R.string.home_use_installed_app),
                                onClick = onUseInstalledApp,
                                icon = Icons.Outlined.PhoneAndroid,
                                enabled = !isLoadingInstalledApps
                            )
                        )
                    }
                    add(
                        DialogAction(
                            text = stringResource(R.string.home_file_picker_prompt_open_apk),
                            onClick = onOpenFilePicker,
                            icon = Icons.Outlined.FolderOpen
                        )
                    )
                    add(
                        DialogAction(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss
                        )
                    )
                },
                layout = DialogButtonLayout.Vertical
            )
        }
    )
}

/**
 * Unsupported version warning dialog.
 */
@Composable
internal fun UnsupportedVersionWarningDialog(
    packageName: String?,
    version: String,
    versionCode: Long? = null,
    recommendedVersion: String?,
    allCompatibleVersions: List<String>,
    versionDescriptions: Map<String, String> = emptyMap(),
    compatibleVersionCodes: Map<String, Set<Int>> = emptyMap(),
    experimentalVersions: Set<String> = emptySet(),
    isExperimental: Boolean = false,
    isExpertMode: Boolean,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val versionCodeMismatch = !isExperimental && versionCode != null && version == recommendedVersion
    val tags = versionTagsOf(isExperimental = isExperimental, isUnsupported = !isExperimental)
    // The card is tinted by the same tag it is badged with, so it cannot read as two verdicts
    val tone = tags.firstOrNull()?.tone ?: SemanticTone.Error
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_dialog_unsupported_version_dialog_title),
        description = stringResource(
            when {
                isExperimental -> R.string.home_dialog_unsupported_version_experimental_description
                versionCodeMismatch -> R.string.home_dialog_unsupported_version_build_mismatch_description
                else -> R.string.home_dialog_unsupported_version_dialog_description
            }
        ),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
        ) {
            VersionPanel(
                color = tone.container.copy(alpha = 0.3f),
                border = CardBorder.tinted(tone.accent),
                header = {
                    CardHeader(
                        title = stringResource(R.string.home_selected_version),
                        accentColor = tone.accent,
                        icon = Icons.Outlined.CheckCircle
                    )
                }
            ) {
                VersionRow(
                    version = version,
                    tags = tags,
                    buildCode = versionCode,
                    emphasized = true,
                    versionColor = tone.accent
                )
            }

            if (isExpertMode && allCompatibleVersions.isNotEmpty()) {
                // Expert mode: every compatible version
                VersionListCard(
                    title = stringResource(R.string.home_dialog_unsupported_version_compatible_versions),
                    icon = Icons.Outlined.Checklist,
                    versions = allCompatibleVersions,
                    recommendedIndex = allCompatibleVersions
                        .indexOfFirst { it !in experimentalVersions }
                        .takeIf { it >= 0 } ?: 0,
                    experimentalVersions = experimentalVersions,
                    descriptions = versionDescriptions,
                    versionCodes = compatibleVersionCodes
                )
            } else if (recommendedVersion != null) {
                // Simple mode or single version: the recommended one alone
                VersionListCard(
                    title = stringResource(R.string.home_recommended_version),
                    icon = VersionTag.Recommended.icon,
                    versions = listOf(recommendedVersion),
                    recommendedIndex = 0,
                    experimentalVersions = experimentalVersions,
                    versionCodes = compatibleVersionCodes
                )
            }
        }
    }
}

/**
 * Warning dialog shown when the selected APK's signing certificate does not match
 * the expected signatures declared in the patch bundle.
 */
@Composable
fun InvalidSignatureDialog(
    appName: String,
    packageName: String?,
    onPickAnother: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_invalid_signature_title),
        description = htmlAnnotatedString(
            stringResource(R.string.home_invalid_signature_message, appName)
        ),
        footer = {
            AppDialogActions(
                actions = listOf(
                    DialogAction(
                        text = stringResource(R.string.home_split_apk_warning_pick_another),
                        onClick = onPickAnother,
                        icon = Icons.Outlined.FolderOpen
                    ),
                    DialogAction(
                        text = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                        onClick = onProceed
                    ),
                    DialogAction(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss
                    )
                ),
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Notice(
                text = stringResource(R.string.home_invalid_signature_badge),
                tone = SemanticTone.Error,
                icon = Icons.Outlined.Warning
            )
        }
    }
}

/**
 * Warning dialog shown when the user selects a split APK archive (.apks / .apkm / .xapk)
 * for an app that requires a full APK.
 */
@Composable
fun SplitApkWarningDialog(
    appName: String,
    packageName: String?,
    onProceed: () -> Unit,
    onPickAnother: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_split_apk_warning_title),
        description = htmlAnnotatedString(
            stringResource(R.string.home_split_apk_warning_message, appName)
        ),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                secondaryText = stringResource(R.string.home_split_apk_warning_pick_another),
                onSecondaryClick = onPickAnother,
                secondaryIcon = Icons.Outlined.FolderOpen
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        }
    }
}

/**
 * Warning dialog shown when the user selects an APK version that is marked experimental
 * in the patch bundle AND experimental-version mode is enabled for that bundle.
 */
@Composable
fun ExperimentalVersionWarningDialog(
    appName: String,
    packageName: String?,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.morphe_experimental_app_version_dialog_title),
        description = htmlAnnotatedString(
            stringResource(R.string.morphe_experimental_app_version_dialog_message, appName)
        ),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.home_dialog_unsupported_version_dialog_proceed),
                onPrimaryClick = onProceed,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        }
    }
}

/**
 * Wrong package dialog.
 */
@Composable
fun WrongPackageDialog(
    expectedPackage: String,
    actualPackage: String,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(expectedPackage),
        title = stringResource(R.string.home_dialog_wrong_package_title),
        description = stringResource(R.string.home_dialog_wrong_package_description),
        padding = DialogPadding.Compact,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
            ) {
                // The two are read against each other, so the tone carries which is which
                MonospaceValuePanel(
                    value = expectedPackage,
                    label = stringResource(R.string.home_dialog_expected_package),
                    tone = SemanticTone.Success
                )

                MonospaceValuePanel(
                    value = actualPackage,
                    label = stringResource(R.string.home_dialog_selected_package),
                    tone = SemanticTone.Error
                )
            }
        }
    }
}

/**
 * Shown when the device SDK is lower than the minSdk of every declared AppTarget for this app.
 * Informs the user that their device does not meet the requirements for any supported version.
 */
@Composable
internal fun NoCompatibleVersionsDialog(
    appName: String,
    packageName: String?,
    onDismiss: () -> Unit
) {
    val deviceSdk = Build.VERSION.SDK_INT

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_apk_no_compatible_versions_title),
        description = htmlAnnotatedString(
            stringResource(
                R.string.home_apk_no_compatible_versions_message,
                appName,
                deviceSdk.androidVersionName(),
                deviceSdk
            )
        ),
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        }
    }
}

/**
 * Version list card where each row is tappable.
 * The selected version gets a checkmark; the recommended version is labeled when not selected.
 * Experimental versions are always labeled regardless of selection state.
 * Versions whose [AppTarget.minSdk] exceeds the current device SDK are shown greyed-out
 * and cannot be selected.
 */
@Composable
private fun SelectableVersionListCard(
    modifier: Modifier = Modifier,
    versions: List<BundledAppTarget>,
    selectedVersion: AppTarget?,
    onVersionSelect: (AppTarget) -> Unit,
    anyString: String,
    hasMultipleBundles: Boolean,
    incompatibleSdkVersions: Set<String> = emptySet(),
    savedVersion: String? = null,
    installedVersion: String? = null
) {
    if (versions.isEmpty()) return

    // The version each source stands behind, experimental ones aside: a source does not
    // recommend a version it marks experimental, whatever the toggle promotes above it
    val recommendedByBundle = remember(versions) {
        versions.groupBy { it.bundleUid }
            .mapValues { (_, section) ->
                section.installable().firstOrNull { !it.target.isExperimental }?.target?.version
            }
    }
    val sourcesByUid = rememberSourcesByUid()
    val selectedLabel = stringResource(R.string.home_selected_version)

    // A card of its own for each source, headed by it and edged in its color, as the patch list
    // blocks out its sources. A lone source needs no heading, the dialog is about it already
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
    ) {
        versions.groupBy { it.bundleUid }.forEach { (bundleUid, section) ->
            key(bundleUid) {
                val source = sourcesByUid[bundleUid]
                val sourceColor = source?.let { rememberBundleAccent(it) }
                // Shared by the edge and the header band
                val edgeColor = usableAppAccent(sourceColor).takeIf { hasMultipleBundles }

                VersionPanel(
                    border = if (edgeColor != null) {
                        CardBorder.of(appAccentBorder(sourceColor))
                    } else {
                        CardBorder.neutral
                    },
                    header = if (hasMultipleBundles) {
                        {
                            CardHeader(
                                title = section.first().bundleName,
                                accentColor = edgeColor,
                                leading = source?.let {
                                    { BundleIcon(bundle = it, modifier = Modifier.size(24.dp)) }
                                }
                            )
                        }
                    } else null
                ) {
                    section.forEachIndexed { index, bundled ->
                        val target = bundled.target
                        val versionString = target.version ?: anyString
                        val isIncompatibleSdk = target.version != null && target.version in incompatibleSdkVersions
                        val isSelected = !isIncompatibleSdk && target.version != null &&
                                target.version == selectedVersion?.version
                        val tags = versionTagsOf(
                            requiresAndroidSdk = target.minSdk.takeIf { isIncompatibleSdk },
                            isIncompatible = isIncompatibleSdk && target.minSdk == null,
                            isExperimental = target.isExperimental,
                            isRecommended = !isIncompatibleSdk && target.version != null &&
                                    target.version == recommendedByBundle[bundleUid],
                            isSaved = target.version != null && target.version == savedVersion,
                            isInstalled = target.version != null && target.version == installedVersion
                        )
                        val rowContentDescription = buildString {
                            append(versionString)
                            tags.labels().forEach { append(", $it") }
                            if (isSelected) append(", $selectedLabel")
                            target.description?.let { append(", $it") }
                            if (hasMultipleBundles) append(", ${bundled.bundleName}")
                        }

                        VersionRow(
                            version = versionString,
                            tags = tags,
                            description = target.description,
                            selected = isSelected,
                            enabled = !isIncompatibleSdk,
                            onClick = { onVersionSelect(target) },
                            contentDescription = rowContentDescription
                        )
                        if (index < section.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Versions only read rather than picked from, in one [VersionPanel] with an optional [title]. */
@Composable
private fun VersionListCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    versions: List<String>,
    recommendedIndex: Int = 0,
    showUnpatchedBadge: Boolean = false,
    experimentalVersions: Set<String> = emptySet(),
    descriptions: Map<String, String> = emptyMap(),
    incompatibleSdkVersions: Set<String> = emptySet(),
    versionCodes: Map<String, Set<Int>> = emptyMap(),
    savedVersion: String? = null,
    installedVersion: String? = null
) {
    if (versions.isEmpty()) return

    VersionPanel(
        modifier = modifier,
        // Neutral edge, so a neutral header band
        header = title?.let { { CardHeader(title = it, accentColor = null, icon = icon) } }
    ) {
        versions.forEachIndexed { index, version ->
            val tags = versionTagsOf(
                isIncompatible = version in incompatibleSdkVersions,
                isExperimental = version in experimentalVersions,
                isUnpatched = showUnpatchedBadge && versions.size == 1,
                isRecommended = index == recommendedIndex && !showUnpatchedBadge,
                isSaved = version == savedVersion,
                isInstalled = version == installedVersion
            )
            VersionRow(
                version = version,
                tags = tags,
                buildCode = versionCodes[version]?.firstOrNull()?.toLong(),
                description = descriptions[version],
                enabled = version !in incompatibleSdkVersions,
                emphasized = index == recommendedIndex
            )
            if (index < versions.lastIndex) {
                SettingsDivider()
            }
        }
    }
}

/**
 * A card holding versions, one [VersionRow] after another: a source's own, in the neutral card or
 * edged in its color, or [color] and [border] for a card that stands for a verdict, such as a warning.
 * [header] is usually a [CardHeader].
 */
@Composable
private fun VersionPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
    border: BorderStroke? = CardBorder.neutral,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Defaults.SettingsCornerRadius),
        color = color,
        tonalElevation = 1.dp,
        border = border
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            header?.invoke()
            content()
        }
    }
}

/**
 * One version of a [VersionPanel]: the version, its build and description, and its tags. A lone
 * badge sits beside the version, where most rows carry theirs, and several go on a line of their
 * own under it, since beside it, they would squeeze the version out of sight. The build and the
 * APKs on hand, see [isOnHand], are no badges but one quiet line under the version.
 *
 * @param selected Whether it is the pick, for a list picked from, which checks it in the app's
 *   color. Null for a list only read, which keeps no room for a check.
 * @param emphasized Sets the version in bold, as the pick or the recommended one is.
 * @param contentDescription What a screen reader announces the row as, in place of its texts.
 */
@Composable
private fun VersionRow(
    version: String,
    tags: List<VersionTag>,
    modifier: Modifier = Modifier,
    buildCode: Long? = null,
    description: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    emphasized: Boolean = selected == true,
    versionColor: Color = dialogTextColor(),
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val (onHandTags, badgeTags) = tags.partition { it.isOnHand }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected != null && onClick != null && enabled) {
                    Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                } else Modifier
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The app's own round check, in its color, as the other pickers mark their pick
        if (selected != null) {
            SelectionCheckIndicator(
                state = if (selected) ToggleableState.On else ToggleableState.Off,
                enabled = enabled
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (enabled) Modifier else Modifier.alpha(Defaults.DISABLED_ALPHA)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    color = versionColor,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (badgeTags.size == 1) VersionTagBadge(badgeTags.single())
            }
            if (buildCode != null || onHandTags.isNotEmpty()) {
                VersionDetailsLine(buildCode = buildCode, onHandTags = onHandTags)
            }
            if (badgeTags.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
                    verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall)
                ) {
                    badgeTags.forEach { VersionTagBadge(it) }
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = dialogSecondaryTextColor()
                )
            }
        }
    }
}

/**
 * A version's build and the APKs on hand at it, parted by dots in the secondary text color. Only the
 * build number is set in monospace, lining up with the version above it, and its label reads as text.
 */
@Composable
private fun VersionDetailsLine(buildCode: Long?, onHandTags: List<VersionTag>) {
    val color = dialogSecondaryTextColor()
    val buildText = buildCode?.let { stringResource(R.string.home_dialog_unsupported_version_build, it) }
    val buildLabel = remember(buildText, buildCode) {
        buildText?.let { text ->
            buildAnnotatedString {
                append(text)
                val number = buildCode.toString()
                val start = text.indexOf(number)
                if (start >= 0) {
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, start + number.length)
                }
            }
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        buildLabel?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = color)
        }
        onHandTags.forEachIndexed { index, tag ->
            if (index > 0 || buildLabel != null) {
                Text(text = "·", style = MaterialTheme.typography.bodySmall, color = color)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tag.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(text = tag.label(), style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

private fun buildVersionSuffix(version: String, versionCode: Long?): String =
    if (versionCode != null) "v$version ($versionCode)" else "v$version"

