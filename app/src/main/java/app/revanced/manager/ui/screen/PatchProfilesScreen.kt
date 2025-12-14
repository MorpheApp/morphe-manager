package app.revanced.manager.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.revanced.manager.ui.component.LazyColumnWithScrollbar
import app.revanced.manager.ui.component.TextInputDialog
import app.revanced.manager.ui.component.haptics.HapticCheckbox
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.ui.viewmodel.BundleSourceType
import app.revanced.manager.ui.viewmodel.BundleOptionDisplay
import app.revanced.manager.ui.viewmodel.PatchProfileLaunchData
import app.revanced.manager.ui.viewmodel.PatchProfileListItem
import app.revanced.manager.ui.viewmodel.PatchProfilesViewModel
import app.revanced.manager.ui.viewmodel.PatchProfilesViewModel.RenameResult
import app.revanced.manager.util.relativeTime
import app.revanced.manager.util.toast
import app.morphe.manager.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PatchProfilesScreen(
    onProfileClick: (PatchProfileLaunchData) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatchProfilesViewModel
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = koinInject<PreferencesManager>()
    val allowUniversal by prefs.disableUniversalPatchCheck.flow.collectAsStateWithLifecycle(
        initialValue = prefs.disableUniversalPatchCheck.default
    )
    var loadingProfileId by remember { mutableStateOf<Int?>(null) }
    var blockedProfile by remember { mutableStateOf<PatchProfileLaunchData?>(null) }
    var renameProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
    var renameProfileName by rememberSaveable { mutableStateOf("") }
    var versionDialogProfile by remember { mutableStateOf<PatchProfileListItem?>(null) }
    var versionDialogValue by rememberSaveable { mutableStateOf("") }
    var versionDialogAllVersions by rememberSaveable { mutableStateOf(false) }
    var versionDialogSaving by remember { mutableStateOf(false) }
    data class ChangeUidTarget(val profileId: Int, val bundleUid: Int, val bundleName: String?)
    var changeUidTarget by remember { mutableStateOf<ChangeUidTarget?>(null) }
    val expandedProfiles = remember { mutableStateMapOf<Int, Boolean>() }
    val selectionActive = viewModel.selectedProfiles.isNotEmpty()
    data class OptionDialogData(val patchName: String, val entries: List<BundleOptionDisplay>)
    var optionDialogData by remember { mutableStateOf<OptionDialogData?>(null) }

    BackHandler(enabled = selectionActive) { viewModel.handleEvent(PatchProfilesViewModel.Event.CANCEL) }

    renameProfileId?.let { targetId ->
        TextInputDialog(
            initial = renameProfileName,
            title = stringResource(R.string.patch_profile_rename_title),
            onDismissRequest = { renameProfileId = null },
            onConfirm = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isEmpty()) return@TextInputDialog
                scope.launch {
                    when (viewModel.renameProfile(targetId, trimmed)) {
                        RenameResult.SUCCESS -> {
                            context.toast(context.getString(R.string.patch_profile_updated_toast, trimmed))
                            renameProfileId = null
                        }
                        RenameResult.DUPLICATE_NAME -> {
                            context.toast(context.getString(R.string.patch_profile_duplicate_toast, trimmed))
                            renameProfileName = trimmed
                        }
                        RenameResult.FAILED -> {
                            context.toast(context.getString(R.string.patch_profile_save_failed_toast))
                            renameProfileId = null
                        }
                    }
                }
            },
            validator = { it.isNotBlank() }
        )
    }

    changeUidTarget?.let { target ->
        TextInputDialog(
            initial = target.bundleUid.toString(),
            title = stringResource(
                R.string.patch_profile_bundle_change_uid_title,
                target.bundleName ?: target.bundleUid.toString()
            ),
            onDismissRequest = { changeUidTarget = null },
            onConfirm = { newValue ->
                val trimmed = newValue.trim()
                val newUid = trimmed.toIntOrNull()
                if (newUid == null) {
                    context.toast(context.getString(R.string.patch_profile_bundle_change_uid_invalid))
                    return@TextInputDialog
                }
                scope.launch {
                    val result = viewModel.changeLocalBundleUid(target.profileId, target.bundleUid, newUid)
                    when (result) {
                        PatchProfilesViewModel.ChangeUidResult.SUCCESS -> context.toast(
                            context.getString(R.string.patch_profile_bundle_change_uid_success, newUid)
                        )

                        PatchProfilesViewModel.ChangeUidResult.PROFILE_OR_BUNDLE_NOT_FOUND,
                        PatchProfilesViewModel.ChangeUidResult.TARGET_NOT_FOUND -> context.toast(
                            context.getString(R.string.patch_profile_bundle_change_uid_not_found, newUid)
                        )
                    }
                    changeUidTarget = null
                }
            },
            validator = { it.trim().toIntOrNull() != null }
        )
    }

    if (profiles.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.patch_profile_empty_state),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    LazyColumnWithScrollbar(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            val bundleCountText = pluralStringResource(
                R.plurals.patch_profile_bundle_count,
                profile.bundleCount,
                profile.bundleCount
            )

            val detailLine = buildList {
                when (val version = profile.appVersion) {
                    null -> add(stringResource(R.string.bundle_version_all_versions))
                    else -> {
                        val formatted = if (version.startsWith("v", ignoreCase = true)) version else "v$version"
                        add(formatted)
                    }
                }
                add(bundleCountText)
            }.joinToString(" • ")
            val creationText = profile.createdAt.takeIf { it > 0 }?.relativeTime(context)?.let {
                stringResource(R.string.patch_profile_created_at, it)
            }
            val expanded = expandedProfiles[profile.id] == true
            val isSelected = profile.id in viewModel.selectedProfiles
            val cardShape = RoundedCornerShape(16.dp)
            val cardColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(cardShape)
                    .combinedClickable(
                        enabled = loadingProfileId == null,
                        onClick = {
                            if (selectionActive) {
                                viewModel.toggleSelection(profile.id)
                                return@combinedClickable
                            }
                            if (loadingProfileId != null) return@combinedClickable
                            loadingProfileId = profile.id
                            scope.launch {
                                try {
                                    val launchData = viewModel.resolveProfile(profile.id)
                                    if (launchData != null) {
                                        if (launchData.availableBundleCount == 0) {
                                            context.toast(
                                                context.getString(R.string.patch_profile_no_available_bundles_toast)
                                            )
                                            return@launch
                                        }
                                        if (launchData.missingBundles.isNotEmpty()) {
                                            context.toast(
                                                context.getString(R.string.patch_profile_missing_bundles_toast)
                                            )
                                        }
                                        if (launchData.changedBundles.isNotEmpty()) {
                                            context.toast(
                                                context.getString(R.string.patch_profile_changed_patches_toast)
                                            )
                                        }
                                        if (!allowUniversal && launchData.containsUniversalPatches) {
                                            blockedProfile = launchData
                                            return@launch
                                        }
                                        onProfileClick(launchData)
                                    } else {
                                        context.toast(
                                            context.getString(R.string.patch_profile_launch_error)
                                        )
                                    }
                                } finally {
                                    loadingProfileId = null
                                }
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(profile.id) }
                    ),
                shape = cardShape,
                tonalElevation = if (isSelected) 4.dp else 2.dp,
                color = cardColor
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (selectionActive) {
                            HapticCheckbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.setSelection(profile.id, it) }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = profile.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (detailLine.isNotEmpty()) {
                                Text(
                                    text = detailLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            creationText?.let { created ->
                                Text(
                                    text = created,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (loadingProfileId == profile.id) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    if (profile.bundleDetails.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            profile.bundleDetails.forEach { detail ->
                                val countText = pluralStringResource(
                                    R.plurals.patch_profile_bundle_patch_count,
                                    detail.patchCount,
                                    detail.patchCount
                                )
                                val name = detail.displayName
                                    ?: stringResource(R.string.patches_name_fallback)
                                Text(
                                    text = stringResource(R.string.patch_profile_bundle_header, name, countText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileActionText(
                            text = stringResource(
                                if (expanded) R.string.patch_profile_show_less
                                else R.string.patch_profile_show_more
                            ),
                            enabled = !selectionActive
                        ) {
                            expandedProfiles[profile.id] = !expanded
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!selectionActive) {
                            ProfileActionText(
                                text = stringResource(R.string.patch_profile_rename)
                            ) {
                                renameProfileId = profile.id
                                renameProfileName = profile.name
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.patch_profile_version_override_action),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        versionDialogProfile = profile
                                        versionDialogValue = profile.appVersion.orEmpty()
                                        versionDialogAllVersions = profile.appVersion.isNullOrBlank()
                                    }
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            profile.bundleDetails.forEach { detail ->
                                val baseName = detail.displayName
                                    ?: stringResource(R.string.patches_name_fallback)
                                val displayName = if (detail.isAvailable) {
                                    baseName
                                } else {
                                    stringResource(
                                        R.string.patch_profile_bundle_unavailable_suffix,
                                        baseName
                                    )
                                }
                                val typeLabel = stringResource(
                                    when (detail.type) {
                                        BundleSourceType.Preinstalled -> R.string.bundle_type_preinstalled
                                        BundleSourceType.Remote -> R.string.bundle_type_remote
                                        else -> R.string.bundle_type_local
                                    }
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (detail.type == BundleSourceType.Local) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.patch_profile_bundle_uid_label,
                                                    detail.uid
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!selectionActive) {
                                                ProfileActionText(
                                                    text = stringResource(R.string.patch_profile_bundle_change_uid)
                                                ) {
                                                    changeUidTarget = ChangeUidTarget(
                                                        profileId = profile.id,
                                                        bundleUid = detail.uid,
                                                        bundleName = detail.displayName
                                                            ?: detail.uid.toString()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                detail.patches.forEachIndexed { index, patchName ->
                                    val optionList = detail.options[patchName]
                                        ?: detail.options[patchName.trim()]
                                        ?: detail.options.entries.firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value
                                        ?: emptyList()
                                    val hasOptions = optionList.isNotEmpty()
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .then(
                                                if (hasOptions) Modifier.clickable {
                                                    optionDialogData = OptionDialogData(
                                                        patchName = patchName,
                                                        entries = optionList
                                                    )
                                                } else Modifier
                                            )
                                    ) {
                                        Text(
                                            text = patchName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (hasOptions) {
                                            Text(
                                                text = stringResource(R.string.patch_profile_view_options),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (index != detail.patches.lastIndex) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    optionDialogData?.let { data ->
        AlertDialog(
            onDismissRequest = { optionDialogData = null },
            confirmButton = {
                TextButton(onClick = { optionDialogData = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.patch_profile_patch_options_title, data.patchName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.entries.forEach { entry ->
                        Text(
                            text = "${entry.label}: ${entry.displayValue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    versionDialogProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                if (versionDialogSaving) return@AlertDialog
                versionDialogProfile = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (versionDialogSaving) return@TextButton
                        versionDialogSaving = true
                        scope.launch {
                            val versionToSave =
                                if (versionDialogAllVersions) null else versionDialogValue.trim().takeUnless { it.isBlank() }
                            if (versionToSave == null && !versionDialogAllVersions) {
                                val quoted = "\"${context.getString(R.string.bundle_version_all_versions)}\""
                                context.toast(context.getString(R.string.patch_profile_version_override_set_to_all, quoted))
                            }
                            try {
                                when (viewModel.updateProfileVersion(profile.id, versionToSave)) {
                                    PatchProfilesViewModel.VersionUpdateResult.SUCCESS -> context.toast(
                                        context.getString(R.string.patch_profile_version_override_saved_toast)
                                    )

                                    PatchProfilesViewModel.VersionUpdateResult.PROFILE_NOT_FOUND -> context.toast(
                                        context.getString(R.string.patch_profile_launch_error)
                                    )

                                    PatchProfilesViewModel.VersionUpdateResult.FAILED -> context.toast(
                                        context.getString(R.string.patch_profile_version_override_failed_toast)
                                    )
                                }
                            } finally {
                                versionDialogSaving = false
                                versionDialogProfile = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (versionDialogSaving) return@TextButton
                        versionDialogProfile = null
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.patch_profile_version_override_title, profile.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.patch_profile_version_override_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = versionDialogValue,
                        onValueChange = {
                            versionDialogValue = it
                            if (versionDialogAllVersions && it.isNotBlank()) {
                                versionDialogAllVersions = false
                            }
                        },
                        label = { Text(stringResource(R.string.patch_profile_version_override_label)) },
                        placeholder = { Text(stringResource(R.string.patch_profile_version_override_hint)) },
                        singleLine = true,
                        enabled = !versionDialogAllVersions
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HapticCheckbox(
                            checked = versionDialogAllVersions,
                            onCheckedChange = { versionDialogAllVersions = it }
                        )
                        Text(text = stringResource(R.string.patch_profile_version_override_all_versions))
                    }
                }
            }
        )
    }

    blockedProfile?.let {
        AlertDialog(
            onDismissRequest = { blockedProfile = null },
            confirmButton = {
                TextButton(onClick = { blockedProfile = null }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(stringResource(R.string.universal_patches_profile_blocked_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.universal_patches_profile_blocked_description,
                        stringResource(R.string.universal_patches_safeguard)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}


@Composable
private fun ProfileActionText(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
