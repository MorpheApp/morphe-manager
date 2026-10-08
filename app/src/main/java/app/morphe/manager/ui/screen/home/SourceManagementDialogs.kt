/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.domain.repository.PatchBundleRepository.RemoteSourceRejection
import app.morphe.manager.domain.repository.PatchBundleRepository.RemoteUrlCheck
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.appIconColorOf
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel.PickedBundle
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
import org.koin.compose.koinInject
import java.util.Locale

/**
 * Dialog for adding patch sources, several at once: links pasted in any form on the remote tab,
 * or any number of picked files on the local one. Each pending source says whether it will be
 * added, and only those that will are counted and submitted.
 *
 * @param localFiles Files picked so far with what importing each would do, held by the caller
 * since the picker opens outside.
 * @param onCheckUrl Checks a link the way the add will, so a refusal shows before submitting.
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onRemoteSubmit: (urls: List<String>, chooseApps: Boolean) -> Unit,
    onLocalSubmit: (chooseApps: Boolean) -> Unit,
    onLocalPick: () -> Unit,
    onLocalRemove: (Uri) -> Unit,
    localFiles: List<PickedBundle>,
    onCheckUrl: (String) -> RemoteUrlCheck
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Remote, 1 = Local
    var chooseApps by rememberSaveable { mutableStateOf(false) }
    val remote = rememberRemoteLinksState(onCheckUrl)

    val readyCount = if (selectedTab == 0) {
        remote.readyLinks.size
    } else {
        localFiles.count { it.check is LocalFileCheck.New || it.check is LocalFileCheck.Update }
    }

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
                    primaryText = if (readyCount > 1) {
                        pluralStringResource(R.plurals.sources_dialog_add_count, readyCount, readyCount.toString())
                    } else {
                        stringResource(R.string.add)
                    },
                    onPrimaryClick = {
                        when (selectedTab) {
                            0 -> onRemoteSubmit(remote.readyLinks, chooseApps)
                            1 -> onLocalSubmit(chooseApps)
                        }
                    },
                    primaryEnabled = readyCount > 0,
                    secondaryText = stringResource(android.R.string.cancel),
                    onSecondaryClick = onDismiss
                )
            }
        }
    ) {
        SegmentedTabs(
            options = listOf(
                SegmentedTab(
                    label = stringResource(R.string.sources_dialog_remote),
                    icon = Icons.Outlined.Language
                ),
                SegmentedTab(
                    label = stringResource(R.string.sources_dialog_local),
                    icon = Icons.AutoMirrored.Outlined.InsertDriveFile
                )
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            below = {
                // What a source holds is only known once it has loaded, so this asks now and
                // the list opens then
                ChooseAppsToggle(
                    checked = chooseApps,
                    onCheckedChange = { chooseApps = it }
                )
            }
        ) { tab ->
            when (tab) {
                0 -> RemoteTabContent(state = remote)
                1 -> LocalTabContent(
                    entries = localFiles,
                    onPickFiles = onLocalPick,
                    onRemove = onLocalRemove
                )
            }
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

/** Links in pasted text however they came: one per line, in a list, or inside prose or markdown. */
private val LINK_PATTERN = Regex(
    """(?:https?://)?(?:[\w-]+\.)+[a-z]{2,}(?::\d+)?(?:/[^\s,;<>()\[\]"'`]*)?""",
    RegexOption.IGNORE_CASE
)

private fun extractLinks(text: String): List<String> =
    LINK_PATTERN.findAll(text).map { it.value.trimEnd('.') }.toList()

/** A link waiting in the remote tab, with what adding it would do. */
private class RemoteLinkEntry(val link: String, val check: RemoteUrlCheck) {
    /** Two links reading the same bundle are the same source, however each was written. */
    val key = ((check as? RemoteUrlCheck.Accepted)?.endpoint ?: link).lowercase(Locale.US)
}

/**
 * Links of the remote tab: the ones taken into the list, and the ones still in the field, which
 * count as well. A paste of several links or the keyboard's Done moves the field into the list.
 *
 * Typing is never rewritten. A field reset under the keyboard races the keys still on their way,
 * and whichever lands in between is lost, so links typed one after another simply stay in the
 * field until Done.
 */
@Stable
private class RemoteLinksState(
    private val check: (String) -> RemoteUrlCheck,
    linksState: MutableState<List<String>>,
    inputState: MutableState<String>
) {
    private var links by linksState
    var input by inputState
        private set

    val entries by derivedStateOf { links.map { RemoteLinkEntry(it, check(it)) } }

    /** Links in the field. Text that holds none is still checked, to say why it is not one. */
    private val inputEntries by derivedStateOf {
        if (input.isBlank()) emptyList()
        else extractLinks(input).ifEmpty { listOf(input.trim()) }.map { RemoteLinkEntry(it, check(it)) }
    }

    /** What the field holds, checked: the first link it would leave out, or null while it is empty. */
    val inputCheck by derivedStateOf {
        inputEntries.firstOrNull { it.check is RemoteUrlCheck.Rejected }?.check
            ?: inputEntries.firstOrNull()?.check
    }

    /** Links that will become sources, the ones in the field included. */
    val readyLinks by derivedStateOf {
        (entries + inputEntries)
            .distinctBy { it.key }
            .filter { it.check is RemoteUrlCheck.Accepted }
            .map { it.link }
    }

    fun onInputChange(text: String) {
        // Grown by more than a key at once is a paste, which leaves nothing in flight to race
        val pastedLinks = extractLinks(text).takeIf { text.length - input.length > 1 && it.size > 1 }
        input = if (pastedLinks != null && add(pastedLinks)) "" else text
    }

    fun clearInput() {
        input = ""
    }

    fun commitInput() {
        if (add(extractLinks(input))) input = ""
    }

    /** @return Whether [text] held any link. */
    fun paste(text: String): Boolean = add(extractLinks(text))

    fun remove(link: String) {
        links = links - link
    }

    private fun add(newLinks: List<String>): Boolean {
        if (newLinks.isEmpty()) return false
        val taken = entries.mapTo(mutableSetOf()) { it.key }
        links = links + newLinks.filter { taken.add(RemoteLinkEntry(it, check(it)).key) }
        return true
    }
}

@Composable
private fun rememberRemoteLinksState(check: (String) -> RemoteUrlCheck): RemoteLinksState {
    val links = rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
    ) { mutableStateOf(emptyList()) }
    val input = rememberSaveable { mutableStateOf("") }
    return remember(check) { RemoteLinksState(check, links, input) }
}

private val RemoteSourceRejection.hintRes: Int
    get() = when (this) {
        // Says what a link has to be rather than only that it is not
        RemoteSourceRejection.Invalid -> R.string.sources_dialog_url_invalid
        else -> messageRes
    }

/** Hosts whose links name a repository, by the icon they show. Any other host serves a bundle file. */
private val REPOSITORY_HOST_ICONS: Map<String, ImageVector> by lazy {
    mapOf(
        "github.com" to FontAwesomeIcons.Brands.Github,
        "raw.githubusercontent.com" to FontAwesomeIcons.Brands.Github,
        "gitlab.com" to FontAwesomeIcons.Brands.Gitlab
    )
}

/** The link without its scheme, the way it reads in the address bar. */
private fun String.bareLink() = substringAfter("://").removePrefix("www.")

private fun String.linkHost() = bareLink().substringBefore('/').lowercase(Locale.US)

/** A repository as owner/repo, anything else as its bare link. */
private fun remoteLinkTitle(link: String): String {
    val bare = link.bareLink()
    val segments = bare.substringAfter('/', "").split('/').filter { it.isNotBlank() }
    return if (link.linkHost() in REPOSITORY_HOST_ICONS && segments.size >= 2) "${segments[0]}/${segments[1]}" else bare
}

private fun remoteLinkIcon(link: String): ImageVector = REPOSITORY_HOST_ICONS[link.linkHost()] ?: Icons.Outlined.Link

@Composable
private fun RemoteTabContent(state: RemoteLinksState) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val inputCheck = state.inputCheck
    val noLinksMessage = stringResource(R.string.sources_dialog_paste_no_links)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppDialogTextField(
            value = state.input,
            onValueChange = state::onInputChange,
            label = { Text(stringResource(R.string.sources_dialog_remote_url)) },
            placeholder = { Text("github.com/owner/repo") },
            trailingIcon = {
                if (state.input.isNotEmpty()) {
                    IconButton(onClick = state::clearInput) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear))
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (text == null || !state.paste(text)) {
                                    context.toast(noLinksMessage)
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.paste))
                    }
                }
            },
            isError = inputCheck is RemoteUrlCheck.Rejected,
            keyboardOptions = KeyboardOptions(
                // A keyboard fixing "github" into "GitHub" or capitalizing a pasted link rewrites it
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { state.commitInput() })
        )

        // Live validation feedback on the link being typed
        AnimatedVisibility(visible = inputCheck != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (icon, color, text) = when (inputCheck) {
                    is RemoteUrlCheck.Rejected -> Triple(
                        Icons.Outlined.ErrorOutline,
                        MaterialTheme.colorScheme.error,
                        stringResource(inputCheck.reason.hintRes)
                    )
                    is RemoteUrlCheck.Accepted -> Triple(
                        Icons.Outlined.CheckCircle,
                        SemanticTone.Success.accent,
                        stringResource(R.string.sources_dialog_url_valid)
                    )
                    // Cleared while the row fades out
                    null -> Triple(Icons.Outlined.Info, Color.Transparent, "")
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }

        // What a link can be, shown until the first one is in the list to point at
        if (state.entries.isEmpty()) {
            // A sentence rather than a name, so it leads the card as text instead of heading it
            LabeledSection {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = Defaults.ContentPadding)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = dialogSecondaryTextColor(),
                        modifier = Modifier.padding(top = 1.dp).size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.sources_dialog_remote_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = dialogSecondaryTextColor()
                    )
                }
                UrlFormatRow(icon = FontAwesomeIcons.Brands.Github, text = "github.com/owner/repo")
                UrlFormatRow(icon = FontAwesomeIcons.Brands.Gitlab, text = "gitlab.com/owner/repo")
                UrlFormatRow(icon = Icons.Outlined.Link, text = "example.com/patches-bundle.json")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CompactCardSpacing)) {
                state.entries.forEach { entry ->
                    val rejection = (entry.check as? RemoteUrlCheck.Rejected)?.reason
                    PendingSourceRow(
                        icon = remoteLinkIcon(entry.link),
                        title = remoteLinkTitle(entry.link),
                        detail = rejection?.let { stringResource(it.hintRes) } ?: entry.link.bareLink(),
                        isError = rejection != null,
                        onRemove = { state.remove(entry.link) }
                    )
                }
            }
        }
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
        modifier = Modifier.padding(horizontal = Defaults.ContentPadding)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = dialogSecondaryTextColor(),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = dialogSecondaryTextColor(),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LocalTabContent(
    entries: List<PickedBundle>,
    onPickFiles: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    val textColor = dialogTextColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) {
            // Drop zone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFiles() },
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
                        text = stringResource(R.string.sources_dialog_local_files),
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
            entries.forEach { entry ->
                val check = entry.check
                PendingSourceRow(
                    icon = if (check is LocalFileCheck.Update) Icons.Outlined.Update else Icons.AutoMirrored.Outlined.InsertDriveFile,
                    title = entry.name,
                    detail = when (check) {
                        is LocalFileCheck.Update -> stringResource(R.string.sources_dialog_local_updates, check.title)
                        LocalFileCheck.Duplicate -> stringResource(R.string.sources_management_already_exists)
                        LocalFileCheck.NotBundle -> stringResource(R.string.sources_dialog_local_invalid_extension)
                        // Where it is read from, while it is checked and once it is known to be new
                        else -> entry.uri.toFilePath().takeIf { it.startsWith("/") }?.substringBeforeLast("/").orEmpty()
                    },
                    isError = check == LocalFileCheck.Duplicate || check == LocalFileCheck.NotBundle,
                    onRemove = { onRemove(entry.uri) }
                )
            }
            AppDialogOutlinedButton(
                text = stringResource(R.string.sources_dialog_local_add_more),
                onClick = onPickFiles,
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * One source waiting to be added, as either tab lists it: what it is, and under it where it comes
 * from or why it will be left out.
 */
@Composable
private fun PendingSourceRow(
    icon: ImageVector,
    title: String,
    detail: String,
    isError: Boolean,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    CompactListCard(onClick = null) {
        if (isError) {
            CompactCardIconTile(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
            }
        } else {
            CompactCardIconTile {
                Icon(icon, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
            }
        }
        CardHeadingText(name = title, description = detail, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.remove),
                tint = dialogSecondaryTextColor()
            )
        }
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
        description = stringResource(R.string.sources_dialog_rename),
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPadding)
        ) {
            AppDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text(stringResource(R.string.patch_option_enter_value)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
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
 * and the app the entries are narrowed to.
 */
data class BundleChangelogRequest(
    val bundleUid: Int,
    val sinceVersion: String? = null,
    val subject: ChangelogSubject? = null
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
            subject = request.subject
        )
    }
}

/**
 * Changelog dialog for a bundle.
 *
 * Prerelease channel: entries from the last stable release onwards.
 * Stable: entries newer than the installed version, plus the installed version itself, with
 * no prerelease builds, as each release already sums up the builds that led to it.
 * A [sinceVersion] replaces both baselines with the caller's own, see
 * [RemotePatchBundle.fetchChangelogSince], and [subject] narrows every entry to the bullets
 * for one app.
 *
 * Fetched once and cached; cache invalidated on channel switch.
 * Falls back to GitHub Release info if CHANGELOG.md is unavailable.
 */
@Composable
fun BundleChangelogDialog(
    src: RemotePatchBundle,
    onDismissRequest: () -> Unit,
    sinceVersion: String? = null,
    subject: ChangelogSubject? = null
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
                val shownEntries = if (sinceVersion != null) {
                    // A caller's baseline asks what changed since it, not including it
                    src.fetchChangelogSince(sinceVersion)
                } else {
                    val allEntries = src.fetchChannelChangelogEntries()
                    if (src.usesPrerelease) {
                        // Prerelease: from the last stable release onwards
                        val lastStable = allEntries.firstOrNull { !it.isPrerelease }
                        if (lastStable != null)
                            ChangelogParser.entriesNewerThan(allEntries, lastStable.version) + lastStable
                        else allEntries.take(30)
                    } else {
                        // Stable: from the installed version onwards
                        val installed = src.installedVersionSignature
                        ChangelogParser.entriesNewerThan(allEntries, installed) +
                                listOfNotNull(installed?.let { ChangelogParser.findVersion(allEntries, it) })
                    }
                }
                val entries = ChangelogParser.entriesFor(shownEntries, subject, generalChangesHeading)

                // APIPatchBundle has endpoint="api" - use SOURCE_REPO_URL directly
                val repoUrl = when (src) {
                    is APIPatchBundle -> SOURCE_REPO_URL
                    else -> RemotePatchBundle.inferPageUrlFromEndpoint(src.endpoint)
                }
                val latestPageUrl = entries.firstOrNull()?.version?.let { version ->
                    repoUrl?.let { releasePageUrl(it, version) }
                }

                if (entries.isNotEmpty() || subject != null) {
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
        val shownEntries = (state as? BundleChangelogState.Entries)?.entries.orEmpty()
        val shownVersions = shownEntries.mapTo(HashSet()) { it.version.normalizeVersion() }
        val oldestShown = shownEntries.lastOrNull()?.version
        olderState = OlderBundleState.Loading
        scope.launch {
            olderState = withContext(Dispatchers.Default) {
                runCatching {
                    // History is the stable timeline, led on the prerelease channel by the dev
                    // builds of the cycle under way, which only its own changelog lists. The stable
                    // one keeps every merged dev build too, and those are left out
                    val history = src.fetchChannelChangelogEntries().filter { it.isPrerelease } +
                            src.fetchFullChangelogEntries().filterNot { it.isPrerelease }
                    val filtered = history.filter {
                        // Skip versions already shown above
                        it.version.normalizeVersion() !in shownVersions
                                // A dev changelog lagging behind the stable one must not put newer
                                // releases under the earlier ones
                                && (oldestShown == null || !isNewerVersion(oldestShown, it.version))
                    }
                    OlderBundleState.Loaded(
                        ChangelogParser.entriesFor(filtered, subject, generalChangesHeading)
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
        accentColor = rememberSourceHeaderColor(src),
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
                                onClick = { fetchTrigger++ },
                                icon = Icons.Outlined.Refresh
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
            subtitle = subject?.appNames?.firstOrNull()?.let { stringResource(R.string.changelog_for_app, it) }
                ?: src.installedVersionSignature?.withVersionPrefix()?.isolateLtr().orEmpty()
        )

        BundleChangelogContent(
            state = state,
            installedVersion = src.installedVersionSignature,
            patchedVersion = sinceVersion,
            older = older,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BundleChangelogContent(
    state: BundleChangelogState,
    installedVersion: String?,
    patchedVersion: String?,
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
            is BundleChangelogState.Error -> ChangelogError(error = current.throwable)
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
                    // The release the app was patched with, the point its changes are counted from,
                    // marked where it is listed and named over the history where it is not, as a dev
                    // build or a release with no changes for the app is not
                    val isPatchedListed = patchedVersion != null &&
                            (current.entries + older.entries.orEmpty()).any {
                                it.version.normalizeVersion() == patchedVersion.normalizeVersion()
                            }
                    ChangelogList(
                        entries = current.entries,
                        older = older,
                        // The version a source holds is the one it patches with, nothing on the device
                        badges = buildMap {
                            patchedVersion?.let { put(it, ChangelogBadge.INSTALLED) }
                            installedVersion?.let { put(it, ChangelogBadge.DOWNLOADED) }
                        },
                        olderLabel = patchedVersion?.takeUnless { isPatchedListed }?.let {
                            stringResource(R.string.changelog_patched_with, it.withVersionPrefix().isolateLtr())
                        },
                        // The gap under the header is the list's own, so releases scroll up to its edge
                        contentPadding = PaddingValues(top = Defaults.ItemSpacing)
                    )
                }
            }
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
        accentColor = rememberSourceHeaderColor(src),
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
            searchEnabled = apps.size > 1
        )

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            pinnedFirstRow = true
        ) {
            // Kept while the field is closed, so its share of the spacing makes the gap under
            // the header
            stickyHeader(key = "search") {
                AppDialogSearchHeader(
                    visible = search.visible,
                    value = search.query,
                    onValueChange = { search.query = it },
                    label = stringResource(R.string.home_search_apps),
                    // Opaque, so rows scrolled under the gap stay hidden
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = Defaults.ItemSpacing)
                )
            }

            // Scrolls with the apps, so it gives the list its room back once read
            item(key = "description") {
                Text(
                    text = stringResource(R.string.sources_apps_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dialogSecondaryTextColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
    }
}
