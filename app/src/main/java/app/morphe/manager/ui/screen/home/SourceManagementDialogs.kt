/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.appIconColorOf
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Gitlab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import org.koin.compose.koinInject

private val ColorValid = Color(0xFF4CAF50)

/**
 * Dialog for adding patch bundles.
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onLocalSubmit: (chooseApps: Boolean) -> Unit,
    onRemoteSubmit: (url: String, chooseApps: Boolean) -> Unit,
    onLocalPick: () -> Unit,
    selectedLocalPath: String?,
    selectedLocalUri: Uri?,
    onValidateUrl: (String) -> Boolean
) {
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local
    var chooseApps by rememberSaveable { mutableStateOf(false) }

    val urlValidation = rememberUrlValidation(remoteUrl, onValidateUrl)
    val isRemoteValid = remoteUrl.isNotBlank() && urlValidation != FieldValidation.Invalid
    val localFileValidation = rememberLocalFileValidation(selectedLocalPath)
    val isLocalValid = localFileValidation == FieldValidation.Valid

    val uriHandler = LocalUriHandler.current
    var showCommunityNotice by rememberSaveable { mutableStateOf(false) }

    if (showCommunityNotice) {
        ConfirmDialog(
            title = stringResource(R.string.sources_dialog_community_notice_title),
            message = stringResource(R.string.sources_dialog_community_notice_message),
            primaryText = stringResource(R.string.open),
            isPrimaryDestructive = false,
            onDismiss = { showCommunityNotice = false },
            onConfirm = {
                showCommunityNotice = false
                uriHandler.openUri(COMMUNITY_PATCHES_URL)
            }
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sources_dialog_add_source),
        footer = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // The website hands out remote URLs, so it has nothing to offer the local tab
                AnimatedVisibility(
                    visible = selectedTab == 0,
                    enter = Animations.expandFadeEnter,
                    exit = Animations.shrinkFadeExit
                ) {
                    Column {
                        // Nothing that website lists is reviewed by Morphe, so the disclaimer comes first
                        AppDialogOutlinedButton(
                            text = stringResource(R.string.sources_dialog_community),
                            onClick = { showCommunityNotice = true },
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Defaults.ContentPadding / 2))
                    }
                }
                AppDialogButtonRow(
                    primaryText = stringResource(R.string.add),
                    onPrimaryClick = {
                        when (selectedTab) {
                            0 -> if (isRemoteValid) onRemoteSubmit(normalizeUrl(remoteUrl), chooseApps)
                            1 -> if (isLocalValid) onLocalSubmit(chooseApps)
                        }
                    },
                    primaryEnabled = if (selectedTab == 0) isRemoteValid else isLocalValid,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = onDismiss
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            // Type selector cards
            CardSelectorRow(
                options = listOf(
                    CardSelectorOption(
                        label = stringResource(R.string.sources_dialog_remote),
                        icon = Icons.Outlined.Language
                    ),
                    CardSelectorOption(
                        label = stringResource(R.string.sources_dialog_local),
                        icon = Icons.AutoMirrored.Outlined.InsertDriveFile
                    )
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            // Tab content
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = Animations.fadeCrossfade()
            ) { tab ->
                when (tab) {
                    0 -> RemoteTabContent(
                        remoteUrl = remoteUrl,
                        onUrlChange = { remoteUrl = it },
                        urlValidation = urlValidation
                    )
                    1 -> LocalTabContent(
                        selectedPath = selectedLocalPath,
                        selectedUri = selectedLocalUri,
                        onPickFile = onLocalPick,
                        validation = localFileValidation
                    )
                }
            }

            // What a source holds is only known once it has loaded, so this asks now and the
            // list opens then
            ChooseAppsToggle(
                checked = chooseApps,
                onCheckedChange = { chooseApps = it }
            )
        }
    }
}

/**
 * Asks, while a source is being added, whether to open [SourceAppsDialog] once it has loaded.
 */
@Composable
internal fun ChooseAppsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSwitchItem(
        checked = checked,
        onToggle = { onCheckedChange(!checked) },
        icon = Icons.Outlined.Apps,
        title = stringResource(R.string.sources_dialog_choose_apps),
        subtitle = stringResource(R.string.sources_dialog_choose_apps_description),
        showBorder = true,
        modifier = modifier
    )
}

private enum class FieldValidation { Empty, Valid, Invalid }

@Composable
private fun rememberUrlValidation(url: String, validate: (String) -> Boolean): FieldValidation =
    remember(url) {
        when {
            url.isBlank() -> FieldValidation.Empty
            validate(normalizeUrl(url)) -> FieldValidation.Valid
            else -> FieldValidation.Invalid
        }
    }

@Composable
private fun UrlFormatRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun RemoteTabContent(
    remoteUrl: String,
    onUrlChange: (String) -> Unit,
    urlValidation: FieldValidation,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppDialogTextField(
            value = remoteUrl,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.sources_dialog_remote_url)) },
            placeholder = { Text("https://github.com/owner/repo") },
            showClearButton = true,
            isError = urlValidation == FieldValidation.Invalid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )

        // Live validation feedback
        AnimatedVisibility(visible = urlValidation != FieldValidation.Empty) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (icon, color, text) = when (urlValidation) {
                    FieldValidation.Valid -> Triple(
                        Icons.Outlined.CheckCircle,
                        ColorValid,
                        stringResource(R.string.sources_dialog_url_valid)
                    )
                    FieldValidation.Invalid -> Triple(
                        Icons.Outlined.ErrorOutline,
                        MaterialTheme.colorScheme.error,
                        stringResource(R.string.sources_dialog_url_invalid)
                    )
                    FieldValidation.Empty -> Triple(Icons.Outlined.Info, Color.Transparent, "")
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }

        // URL format hint
        StatusBadge(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_remote_url_formats_title),
            tone = SemanticTone.Neutral
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UrlFormatRow(
                icon = FontAwesomeIcons.Brands.Github,
                text = "github.com/owner/repo"
            )
            UrlFormatRow(
                icon = FontAwesomeIcons.Brands.Gitlab,
                text = "gitlab.com/owner/repo"
            )
            UrlFormatRow(
                icon = Icons.Outlined.Link,
                text = "example.com/patches-bundle.json"
            )
        }
    }
}

@Composable
private fun rememberLocalFileValidation(path: String?): FieldValidation = remember(path) {
    if (path == null) return@remember FieldValidation.Empty
    if (!path.endsWith(".mpp", ignoreCase = true)) FieldValidation.Invalid
    else FieldValidation.Valid
}

@Composable
private fun LocalTabContent(
    selectedPath: String?,
    selectedUri: Uri?,
    onPickFile: () -> Unit,
    validation: FieldValidation
) {
    val textColor = LocalDialogTextColor.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectedPath == null) {
            // Drop zone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFile() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    1.dp, textColor.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = textColor.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.sources_dialog_local_file),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                    Text(
                        text = stringResource(R.string.sources_dialog_local_file_tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            // Selected file
            val isValid = validation == FieldValidation.Valid
            Surface(
                shape = RoundedCornerShape(Defaults.CompactCornerRadius),
                color = if (isValid)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isValid) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(Defaults.IconSizeSmall),
                        tint = if (isValid) ColorValid else MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (validation) {
                                FieldValidation.Invalid -> stringResource(R.string.sources_dialog_local_invalid_extension)
                                else -> selectedUri?.toFilePath()?.takeIf { it.startsWith("/") }?.substringBeforeLast("/") ?: ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValid) textColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onPickFile,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.sources_dialog_local_change_file),
                            modifier = Modifier.size(24.dp),
                            tint = textColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Description
        UrlFormatRow(
            icon = Icons.Outlined.Info,
            text = stringResource(R.string.sources_dialog_local_file_description)
        )
    }
}

/**
 * Dialog for renaming a bundle.
 */
@Composable
fun RenameBundleDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sources_dialog_display_name),
        dismissOnClickOutside = false,
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = {
                    keyboardController?.hide()
                    onConfirm(textValue)
                },
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = {
                    keyboardController?.hide()
                    onDismissRequest()
                }
            )
        }
    ) {
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            Text(
                text = stringResource(R.string.sources_dialog_rename),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center
            )

            AppDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.patch_option_enter_value),
                        color = secondaryColor.copy(alpha = 0.5f)
                    )
                },
                leadingIcon = {
                    ThemedIcon(
                        icon = Icons.Outlined.Edit,
                        tint = secondaryColor
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onConfirm(textValue)
                    }
                )
            )
        }
    }
}

/**
 * Dialog listing the patches of a source, one block per app they patch and the universal ones last.
 *
 * @param initialQuery Query to open filtered by, carried over from the search that found the source.
 */
@Composable
fun BundlePatchesDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource,
    initialQuery: String = ""
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val context = LocalContext.current
    // Read across every source rather than the enabled ones alone: a disabled source is one the
    // user is still deciding about, and what it holds is what that decision is made on
    val patches by remember(src.uid) {
        patchBundleRepository.allBundlesInfoFlow.mapNotNull { it[src.uid]?.patches }
    }.collectAsStateWithLifecycle(emptyList())

    val universalTitle = stringResource(R.string.expert_mode_universal_patches)
    val sections = remember(patches, universalTitle) { patchesByApp(patches, universalTitle) }
    val appCount = sections.count { it.packageName != null }
    val expertBadgeTooltip = stringResource(R.string.sources_patch_expert_badge_tooltip)

    PatchListDialog(
        icon = { modifier -> BundleIcon(bundle = src, modifier = modifier) },
        title = src.displayTitle,
        subtitle = listOfNotNull(
            pluralStringResource(R.plurals.patch_count, patches.size, patches.size.toString()),
            pluralStringResource(R.plurals.home_category_app_count, appCount, appCount.toString())
                .takeIf { appCount > 1 }
        ).joinToString(" · "),
        sections = sections,
        isLoading = patches.isEmpty(),
        saveStateKey = "bundle_${src.uid}",
        onDismiss = onDismissRequest,
        initialQuery = initialQuery,
        accentColor = rememberSourceHeaderColor(src),
        // The list is reachable while the source is off, so it says so up front rather than
        // reading as patches that are ready to be applied
        notice = if (src.enabled) null else {
            {
                Notice(
                    text = stringResource(R.string.sources_patches_source_disabled_hint),
                    icon = Icons.Outlined.VisibilityOff,
                    tone = SemanticTone.Warning,
                    density = NoticeDensity.Compact
                )
            }
        },
        onExpertBadgeClick = { context.toast(expertBadgeTooltip) }
    )
}

/**
 * [patches] as one block per app, by name, then the universal ones. A patch for several apps joins
 * the block of each, so every block holds all that its app can be patched with.
 */
private fun patchesByApp(patches: List<PatchInfo>, universalTitle: String): List<PatchListSection> {
    val sorted = patches.sortedBy { it.displayName }
    val (universal, specific) = sorted.partition { it.isUniversal }
    val apps = specific
        .flatMap { it.compatiblePackages.orEmpty() }
        .filter { it.packageName != null }
        .distinctBy { it.packageName }
        .sortedBy { (it.displayName ?: it.packageName)?.lowercase() }

    return buildList {
        apps.forEach { app ->
            val packageName = app.packageName ?: return@forEach
            add(
                PatchListSection(
                    key = packageName,
                    title = app.displayName ?: packageName,
                    patches = specific.filter { patch ->
                        patch.compatiblePackages?.any { it.packageName == packageName } == true
                    },
                    packageName = packageName,
                    // Tinted after the app's own icon, so each block reads as that app at a glance
                    accentColor = app.appIconColor?.let(::appIconColorOf),
                    icon = { modifier ->
                        AppIcon(packageName = packageName, contentDescription = null, modifier = modifier)
                    }
                )
            )
        }
        if (universal.isNotEmpty()) {
            add(
                PatchListSection(
                    key = UNIVERSAL_GROUP_KEY,
                    title = universalTitle,
                    patches = universal,
                    packageName = null,
                    icon = { modifier ->
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = modifier
                        )
                    }
                )
            )
        }
    }
}

/**
 * What a changelog dialog shows: the source it reads, the version "new" is measured from,
 * and the scopes the entries are narrowed to.
 */
data class BundleChangelogRequest(
    val bundleUid: Int,
    val sinceVersion: String? = null,
    val appNames: Set<String> = emptySet()
)

/**
 * Hosts [BundleChangelogDialog] for the source a [request] names, so every entry point opens
 * it the same way. A missing source, deleted under an open dialog included, shows nothing.
 */
@Composable
fun BundleChangelogHost(
    request: BundleChangelogRequest?,
    sources: List<PatchBundleSource>,
    onDismissRequest: () -> Unit
) {
    if (request == null) return
    val bundle = sources.filterIsInstance<RemotePatchBundle>()
        .find { it.uid == request.bundleUid } ?: return

    // The notes are read against the version installed when the dialog opened, so only another
    // request starts the fetch over: an update landing underneath must not reset it
    key(request) {
        BundleChangelogDialog(
            src = bundle,
            onDismissRequest = onDismissRequest,
            sinceVersion = request.sinceVersion,
            appNames = request.appNames
        )
    }
}

/**
 * Changelog dialog for a bundle.
 *
 * Prerelease channel: entries from the last stable release onwards.
 * Stable: entries newer than the installed version, plus the installed version itself.
 * A [sinceVersion] replaces both baselines with the caller's own, and [appNames] narrows
 * every entry to the bullets scoped to one app.
 *
 * Fetched once and cached; cache invalidated on channel switch.
 * Falls back to GitHub Release info if CHANGELOG.md is unavailable.
 */
@Composable
fun BundleChangelogDialog(
    src: RemotePatchBundle,
    onDismissRequest: () -> Unit,
    sinceVersion: String? = null,
    appNames: Set<String> = emptySet()
) {
    val generalChangesHeading = stringResource(R.string.changelog_general_changes)
    var state: BundleChangelogState by remember { mutableStateOf(BundleChangelogState.Loading) }
    var olderState: OlderBundleState by remember { mutableStateOf(OlderBundleState.Idle) }
    val scope = rememberCoroutineScope()
    // 0 = waiting for dialog enter; incremented to trigger fetch, again on retry
    var fetchTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(fetchTrigger) {
        if (fetchTrigger == 0) return@LaunchedEffect
        state = BundleChangelogState.Loading
        state = withContext(Dispatchers.Default) {
            try {
                val usePrerelease = src.usesPrerelease

                val allEntries = src.fetchChangelogEntries(sinceVersion = null)

                val shownEntries = when {
                    // A caller's baseline asks what changed since it, not including it
                    sinceVersion != null ->
                        ChangelogParser.entriesNewerThan(allEntries, sinceVersion)

                    usePrerelease -> {
                        // Prerelease: from the last stable release onwards
                        val lastStable = allEntries.firstOrNull { !it.version.contains("-") }
                        if (lastStable != null)
                            ChangelogParser.entriesNewerThan(allEntries, lastStable.version) + lastStable
                        else allEntries.take(30)
                    }

                    else -> {
                        // Stable: from the installed version onwards
                        val installed = src.installedVersionSignature
                        val installedEntry = installed?.let {
                            ChangelogParser.findVersion(allEntries, it)
                        }
                        val newer = if (installed != null)
                            ChangelogParser.entriesNewerThan(allEntries, installed)
                        else allEntries
                        if (installedEntry != null) newer + installedEntry else newer
                    }
                }
                val entries = ChangelogParser.entriesFor(shownEntries, appNames, generalChangesHeading)

                // APIPatchBundle has endpoint="api" - use SOURCE_REPO_URL directly
                val repoUrl = when (src) {
                    is APIPatchBundle -> SOURCE_REPO_URL
                    else -> RemotePatchBundle.inferPageUrlFromEndpoint(src.endpoint)
                }
                val latestPageUrl = entries.firstOrNull()?.version?.let { version ->
                    repoUrl?.let { releasePageUrl(it, version) }
                }

                if (entries.isNotEmpty() || appNames.isNotEmpty()) {
                    BundleChangelogState.Entries(
                        entries = entries,
                        latestPageUrl = latestPageUrl
                    )
                } else {
                    // Fallback: CHANGELOG.md unavailable - use latest release info from API
                    val asset = src.fetchLatestReleaseInfo()
                    val fallbackEntries = listOf(
                        ChangelogEntry(
                            version = asset.version,
                            date = null,
                            content = asset.description.sanitizePatchChangelogMarkdown()
                        )
                    )
                    BundleChangelogState.Entries(
                        entries = fallbackEntries,
                        latestPageUrl = asset.pageUrl
                    )
                }
            } catch (t: Throwable) {
                BundleChangelogState.Error(t)
            }
        }
    }

    val loadOlder: () -> Unit = load@{
        if (olderState is OlderBundleState.Loading || olderState is OlderBundleState.Loaded) return@load
        val shownVersions = (state as? BundleChangelogState.Entries)
            ?.entries
            ?.map { it.version.removePrefix("v").trim() }
            ?.toSet()
            .orEmpty()
        olderState = OlderBundleState.Loading
        scope.launch {
            olderState = withContext(Dispatchers.Default) {
                runCatching {
                    val all = src.fetchFullChangelogEntries()
                    val filtered = all.filter {
                        // Skip versions already shown above and any pre-release leftovers;
                        // history is meaningful only as the stable timeline
                        it.version.removePrefix("v").trim() !in shownVersions
                                && !it.version.contains("-")
                    }
                    OlderBundleState.Loaded(
                        ChangelogParser.entriesFor(filtered, appNames, generalChangesHeading)
                    )
                }.getOrElse {
                    // Kept apart from Idle, so the list waits for a retry instead of loading again
                    OlderBundleState.Failed
                }
            }
        }
    }

    val older = OlderReleases(
        entries = (olderState as? OlderBundleState.Loaded)?.entries,
        isLoading = olderState is OlderBundleState.Loading,
        isFailed = olderState is OlderBundleState.Failed,
        onLoad = loadOlder
    )

    AppDialog(
        onDismissRequest = onDismissRequest,
        // Start fetch only after the dialog enter animation completes so the shimmer
        // is always visible first, even when data is cached and would resolve instantly
        onEntered = { if (fetchTrigger == 0) fetchTrigger = 1 },
        scrollable = false,
        // The timeline gives up its leading edge to the rail, so the list takes the wider layout.
        // The header holds the top, and an error sits centered in the room below it
        padding = DialogPadding.Compact,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        footer = {
            when (val current = state) {
                is BundleChangelogState.Entries -> {
                    // An empty changelog has nothing to translate
                    val hasList = current.entries.isNotEmpty()
                    ChangelogFooter(
                        actions = listOf(
                            DialogAction(
                                text = stringResource(R.string.close),
                                onClick = onDismissRequest,
                                emphasis = DialogActionEmphasis.Outlined
                            )
                        ),
                        translatable = hasList,
                        pageUrl = current.latestPageUrl
                    )
                }
                is BundleChangelogState.Error -> {
                    AppDialogActions(
                        actions = listOf(
                            DialogAction(
                                text = stringResource(R.string.retry),
                                onClick = { fetchTrigger++ }
                            ),
                            DialogAction(
                                text = stringResource(R.string.close),
                                onClick = onDismissRequest,
                                emphasis = DialogActionEmphasis.Outlined
                            )
                        ),
                        layout = DialogButtonLayout.Vertical
                    )
                }
                BundleChangelogState.Loading -> {
                    AppDialogOutlinedButton(
                        text = stringResource(R.string.close),
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        // Releases show only their versions, so the header names whose they are, and which app
        // they were narrowed to. It stays the same through loading, so nothing shifts once the
        // entries arrive
        ListDialogHeader(
            icon = { modifier -> BundleIcon(bundle = src, modifier = modifier) },
            title = src.displayTitle,
            subtitle = appNames.firstOrNull()?.let { stringResource(R.string.changelog_for_app, it) }
                ?: src.installedVersionSignature?.withVersionPrefix()?.isolateLtr().orEmpty(),
            accentColor = rememberSourceHeaderColor(src)
        )

        BundleChangelogContent(
            state = state,
            installedVersion = src.installedVersionSignature,
            older = older,
            modifier = Modifier.weight(1f)
        )
    }

    TranslationOverlays()
}

@Composable
private fun BundleChangelogContent(
    state: BundleChangelogState,
    installedVersion: String?,
    older: OlderReleases,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = state,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        modifier = modifier.fillMaxWidth(),
        label = "changelog_state"
    ) { current ->
        when (current) {
            BundleChangelogState.Loading -> ChangelogListLoading(
                modifier = Modifier.padding(top = Defaults.ItemSpacing)
            )
            is BundleChangelogState.Error -> BundleChangelogError(error = current.throwable)
            is BundleChangelogState.Entries -> {
                if (current.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.changelog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Defaults.ItemSpacing)
                    )
                } else {
                    ChangelogList(
                        entries = current.entries,
                        older = older,
                        currentVersion = installedVersion,
                        // The gap under the header is the list's own, so releases scroll up to its edge
                        contentPadding = PaddingValues(top = Defaults.ItemSpacing),
                        // The version a source holds is the one it patches with, nothing on the device
                        currentBadge = ChangelogBadge.IN_USE
                    )
                }
            }
        }
    }
}

@Composable
private fun BundleChangelogError(
    error: Throwable
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Error icon with circular background
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Error details
            Text(
                text = stringResource(
                    R.string.changelog_download_fail,
                    error.simpleMessage().orEmpty()
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = LocalDialogTextColor.current
            )
        }
    }
}

private sealed interface BundleChangelogState {
    data object Loading : BundleChangelogState
    /** [entries] are already filtered to "missed" versions, newest-first. */
    data class Entries(
        val entries: List<ChangelogEntry>,
        val latestPageUrl: String?
    ) : BundleChangelogState
    data class Error(val throwable: Throwable) : BundleChangelogState
}

private sealed interface OlderBundleState {
    data object Idle : OlderBundleState
    data object Loading : OlderBundleState
    data object Failed : OlderBundleState
    /** [entries] are full-history stable entries, already filtered to exclude what's shown above. */
    data class Loaded(val entries: List<ChangelogEntry>) : OlderBundleState
}

private val doubleBracketLinkRegex = Regex("""\[\[([^]]+)]\(([^)]+)\)]""")

private fun String.sanitizePatchChangelogMarkdown(): String =
    doubleBracketLinkRegex.replace(this) { match ->
        val label = match.groupValues[1]
        val link = match.groupValues[2]
        "[\\[$label\\]]($link)"
    }

/**
 * Normalizes a URL by adding https:// if no protocol is specified.
 */
private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()

    return when {
        // Already has protocol
        trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed

        // Add https:// by default
        else -> "https://$trimmed"
    }
}

/**
 * Which of the apps a source brings the user wants from it.
 *
 * A source with hundreds of apps is usually added for one or two of them. An app left out here is
 * kept from this source, the same exclusion the per-app source choice records: the source is no
 * longer offered when the app is patched, and no longer brings it to the home screen. An app
 * another source still brings stays there.
 *
 * Laid out like the hidden apps list: a tap answers for one app, and a long press picks several
 * for the bar to answer at once, so the few wanted out of hundreds take a select all and a few taps.
 */
@Composable
fun SourceAppsDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val itemSpacing = rememberWindowSize().itemSpacing

    // Every source, so a disabled one still lists what it would bring once switched back on
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val appMetadata by patchBundleRepository.allAppMetadata.collectAsStateWithLifecycle()
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())

    val apps = remember(bundleInfo, appMetadata, src.uid) {
        bundleInfo[src.uid]?.listedApps().orEmpty()
            .map { packageName -> packageName to (appMetadata[packageName]?.displayName ?: packageName) }
            .sortedBy { (_, label) -> label.lowercase(Locale.ROOT) }
    }

    val search = rememberSearchFieldState(searchable = apps.size > 1)
    val filtered = remember(apps, search.query) {
        if (search.query.isBlank()) apps
        else apps.filter { (packageName, label) ->
            label.contains(search.query, ignoreCase = true) ||
                    packageName.contains(search.query, ignoreCase = true)
        }
    }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selection = rememberSelectionState<String>()
    fun exitMultiSelect() {
        isMultiSelectMode = false
        selection.clear()
    }

    // Written in full even if the dialog is gone before it finishes, so a list never lands half
    // applied
    fun bring(packageNames: Collection<String>, brought: Boolean) {
        scope.launch {
            withContext(NonCancellable) {
                if (brought) sourceMuteRepository.unmuteApps(src.uid, packageNames)
                else sourceMuteRepository.muteApps(src.uid, packageNames)
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismissRequest,
        dismissOnClickOutside = !isMultiSelectMode,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        },
        bottomBar = if (isMultiSelectMode) {
            {
                MultiSelectShell(visible = true, onBack = ::exitMultiSelect) {
                    SelectionActionBar(
                        selectedCount = selection.size,
                        // Scoped to the search so "select all" never reaches apps out of view
                        totalCount = filtered.size,
                        onSelectAll = { selection.setAll(filtered.map { (packageName, _) -> packageName }) },
                        onDeselectAll = { selection.clear() },
                        actions = listOf(
                            SelectionAction(
                                icon = Icons.Outlined.VisibilityOff,
                                label = stringResource(R.string.sources_apps_leave_out),
                                onClick = context.withToast(stringResource(R.string.sources_apps_leave_out_done)) {
                                    bring(selection.keys.toList(), brought = false)
                                    exitMultiSelect()
                                },
                                tone = ActionTone.Destructive
                            ),
                            SelectionAction(
                                icon = Icons.Outlined.Visibility,
                                label = stringResource(R.string.sources_apps_bring_back),
                                onClick = {
                                    bring(selection.keys.toList(), brought = true)
                                    exitMultiSelect()
                                },
                                tone = ActionTone.Tertiary
                            )
                        ),
                        onCancel = ::exitMultiSelect
                    )
                }
            }
        } else null,
        padding = DialogPadding.Compact,
        scrollable = false,
        contentArrangement = Arrangement.Top,
        fillContentHeight = true,
        hideFooterWhileTyping = true
    ) {
        SearchFieldBackHandler(search)

        // Headed by the source, as its patches are
        ListDialogHeader(
            icon = { modifier -> BundleIcon(bundle = src, modifier = modifier) },
            title = src.displayTitle,
            subtitle = pluralStringResource(R.plurals.home_category_app_count, apps.size, apps.size.toString()),
            search = search,
            searchLabel = stringResource(R.string.home_search_apps),
            // A lone app leaves nothing to search through
            searchEnabled = apps.size > 1,
            accentColor = rememberSourceHeaderColor(src),
            modifier = Modifier.padding(bottom = Defaults.ItemSpacing)
        )

        Text(
            text = stringResource(R.string.sources_apps_description),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalDialogSecondaryTextColor.current,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Defaults.ContentPaddingSmall)
        )

        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                stickyHeader(key = "search") {
                    AppDialogSearchHeader(
                        visible = search.visible,
                        value = search.query,
                        onValueChange = { search.query = it },
                        label = stringResource(R.string.home_search_apps)
                    )
                }

                if (filtered.isEmpty() && search.query.isNotBlank()) {
                    item(key = "empty_state") {
                        EmptyState(
                            message = stringResource(R.string.search_no_results),
                            icon = Icons.Outlined.SearchOff,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                items(items = filtered, key = { (packageName, _) -> packageName }) { (packageName, label) ->
                    val brought = src.uid !in keptFrom[packageName].orEmpty()
                    val gradientColors = appMetadata[packageName]?.gradientColors
                        ?: AppCardColorDefaults.defaultGradientColors

                    SelectableCard(
                        modifier = Modifier
                            .animatedListItem(this)
                            // An app left out reads as one this source no longer brings, the
                            // same way the selection dims what it leaves unpicked
                            .alpha(if (brought || isMultiSelectMode) 1f else 0.55f),
                        isSelected = selection.contains(packageName),
                        isSelectionMode = isMultiSelectMode
                    ) {
                        AppCardLayout(
                            gradientColors = gradientColors,
                            onClick = {
                                if (isMultiSelectMode) selection.toggle(packageName)
                                else bring(listOf(packageName), brought = !brought)
                            },
                            onLongClick = {
                                isMultiSelectMode = true
                                selection.toggle(packageName)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppCardContent(
                                packageName = packageName,
                                packageInfo = null,
                                displayName = label,
                                subtitle = stringResource(
                                    if (brought) R.string.sources_apps_brought
                                    else R.string.sources_apps_left_out
                                ),
                                gradientColors = gradientColors
                            )
                        }
                    }
                }
            }

            ListScrollbar(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )

            ScrollToTopButton(
                listState = listState,
                modifier = Modifier.offset(x = LocalDialogHorizontalInset.current)
            )
        }
    }
}
