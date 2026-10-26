/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.sources

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.LocalFileCheck
import app.morphe.manager.domain.repository.PatchBundleRepository.RemoteSourceRejection
import app.morphe.manager.domain.repository.PatchBundleRepository.RemoteUrlCheck
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.appsBrought
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.appIconColorOf
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.HomeViewModel.PickedBundle
import app.morphe.manager.util.*
import com.composables.icons.fontawesome.FontAwesome
import com.composables.icons.fontawesome.brands.*
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
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
fun DialogHostScope.AddSourceDialog(
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sources_dialog_add_source),
        footer = {
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
    ) {
        SegmentedTabs(
            options = listOf(
                SegmentedTab(
                    label = stringResource(R.string.sources_dialog_remote),
                    icon = MaterialSymbols.Rounded.Language
                ),
                SegmentedTab(
                    label = stringResource(R.string.sources_dialog_local),
                    icon = MaterialSymbols.Rounded.Draft.autoMirrored
                )
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            below = {
                // What a source holds is only known once it has loaded, so this asks now and
                // the list opens then
                SelectionCheckRow(
                    text = stringResource(R.string.sources_dialog_choose_apps_description),
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
        "github.com" to FontAwesome.Brands.Github,
        "raw.githubusercontent.com" to FontAwesome.Brands.Github,
        "gitlab.com" to FontAwesome.Brands.Gitlab
    )
}

/** The link without its scheme, the way it reads in the address bar. */
internal fun String.bareLink() = substringAfter("://").removePrefix("www.")

private fun String.linkHost() = bareLink().substringBefore('/').lowercase(Locale.US)

/** A repository as owner/repo, anything else as its bare link. */
internal fun remoteLinkTitle(link: String): String {
    val bare = link.bareLink()
    val segments = bare.substringAfter('/', "").split('/').filter { it.isNotBlank() }
    return if (link.linkHost() in REPOSITORY_HOST_ICONS && segments.size >= 2) "${segments[0]}/${segments[1]}" else bare
}

internal fun remoteLinkIcon(link: String): ImageVector = REPOSITORY_HOST_ICONS[link.linkHost()] ?: MaterialSymbols.Rounded.Link

@Composable
private fun RemoteTabContent(state: RemoteLinksState) {
    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val inputCheck = state.inputCheck
    val noLinksMessage = stringResource(R.string.sources_dialog_paste_no_links)

    Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)) {
        AppDialogTextField(
            value = state.input,
            onValueChange = state::onInputChange,
            label = { Text(stringResource(R.string.sources_dialog_remote_url)) },
            placeholder = { Text("github.com/owner/repo") },
            trailingIcon = {
                if (state.input.isNotEmpty()) {
                    IconButton(onClick = state::clearInput) {
                        Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(R.string.clear))
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (text == null || !state.paste(text)) {
                                    messages.show(noLinksMessage)
                                }
                            }
                        }
                    ) {
                        Icon(MaterialSymbols.Rounded.Content_paste, contentDescription = stringResource(R.string.paste))
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
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                val (icon, color, text) = when (inputCheck) {
                    is RemoteUrlCheck.Rejected -> Triple(
                        MaterialSymbols.Rounded.Error,
                        MaterialTheme.colorScheme.error,
                        stringResource(inputCheck.reason.hintRes)
                    )
                    is RemoteUrlCheck.Accepted -> Triple(
                        MaterialSymbols.Rounded.Check_circle,
                        SemanticTone.Success.accent,
                        stringResource(R.string.sources_dialog_url_valid)
                    )
                    // Cleared while the row fades out
                    null -> Triple(MaterialSymbols.Rounded.Info, Color.Transparent, "")
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
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                    modifier = Modifier.padding(horizontal = Defaults.SpaceLarge)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Info,
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
                UrlFormatRow(icon = FontAwesome.Brands.Github, text = "github.com/owner/repo")
                UrlFormatRow(icon = FontAwesome.Brands.Gitlab, text = "gitlab.com/owner/repo")
                UrlFormatRow(icon = MaterialSymbols.Rounded.Link, text = "example.com/patches-bundle.json")
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
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        modifier = Modifier.padding(horizontal = Defaults.SpaceLarge)
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
    // Spaced as the remote tab is, so swiping between the two does not shift what they share. The
    // remote tab's field keeps room above its outline for its label to rise into, so this page
    // starts as far down for both to open at one line
    Column(
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
        modifier = Modifier.padding(top = OutlinedFieldLabelRoom)
    ) {
        if (entries.isEmpty()) {
            LocalFilesDropZone(onPickFiles = onPickFiles)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(CompactCardSpacing)) {
                entries.forEach { entry ->
                    val check = entry.check
                    PendingSourceRow(
                        icon = if (check is LocalFileCheck.Update) MaterialSymbols.Rounded.Update else MaterialSymbols.Rounded.Draft.autoMirrored,
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
            }
            AppDialogOutlinedButton(
                text = stringResource(R.string.sources_dialog_local_add_more),
                onClick = onPickFiles,
                icon = MaterialSymbols.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Room an [OutlinedTextField] with a label keeps above its outline, which Material does not expose. */
private val OutlinedFieldLabelRoom = 8.dp

/**
 * What the local tab starts out as: the empty state the file picker shows, framed in dashes, the
 * usual mark of a place files go, and opening the file picker on a tap.
 */
@Composable
private fun LocalFilesDropZone(onPickFiles: () -> Unit) {
    val edgeColor = dialogSecondaryTextColor().copy(alpha = 0.5f)
    // Filled and rounded as the remote tab's hint card is, so the two tabs open alike
    val shape = RoundedCornerShape(Defaults.CornerLargeIncreased)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardFill())
            // Drawn whatever the outline setting, since the dashes are what make it read as a zone
            .drawBehind {
                val stroke = dashedGuideStroke()
                val inset = stroke.width / 2
                drawRoundRect(
                    color = edgeColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke.width, size.height - stroke.width),
                    cornerRadius = CornerRadius(Defaults.CornerLargeIncreased.toPx() - inset),
                    style = stroke
                )
            }
            .clickable(
                interactionSource = null,
                indication = shapedIndication(shape),
                role = Role.Button,
                onClick = onPickFiles
            )
    ) {
        EmptyState(
            message = stringResource(R.string.sources_dialog_local_files),
            subtitle = stringResource(R.string.sources_dialog_local_file_tap_hint),
            icon = MaterialSymbols.Rounded.Upload_file,
            // About as tall as the remote tab's hint card, so switching tabs barely moves the dialog
            contentPadding = PaddingValues(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceMedium),
            iconSize = 40.dp
        )
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
                Icon(MaterialSymbols.Rounded.Error, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
            }
        } else {
            CompactCardIconTile {
                Icon(icon, contentDescription = null, modifier = Modifier.size(CompactCardGlyphSize))
            }
        }
        CardHeadingText(name = title, description = detail, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Close,
                contentDescription = stringResource(R.string.remove),
                tint = dialogSecondaryTextColor()
            )
        }
    }
}

/**
 * Asks before a source takes a release that is not signed by the key it trusts.
 *
 * @param trustedKey Fingerprint of the key the source trusts.
 * @param newKey Fingerprint of the key the release is signed with, or null if it is not signed.
 */
@Composable
fun DialogHostScope.SigningKeyTrustDialog(
    bundle: PatchBundleSource,
    trustedKey: String?,
    newKey: String?,
    onTrust: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberSourceHeaderColor(bundle),
        title = stringResource(R.string.sources_signing_key_trust_title),
        description = stringResource(
            if (newKey != null) R.string.sources_signing_key_trust_message
            else R.string.sources_signing_key_trust_message_unsigned
        ),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(R.string.trust),
                onPrimaryClick = onTrust,
                isPrimaryDestructive = true,
                secondaryText = stringResource(android.R.string.cancel),
                onSecondaryClick = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConfirmSubject(name = bundle.displayTitle) { modifier ->
                BundleIcon(bundle = bundle, modifier = modifier)
            }

            // The two are read against each other, so the tone carries which is which
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
            ) {
                trustedKey?.let {
                    MonospaceValuePanel(
                        value = it,
                        label = stringResource(R.string.sources_signing_key_trusted),
                        tone = SemanticTone.Success,
                        textAlign = TextAlign.Center
                    )
                }
                newKey?.let {
                    MonospaceValuePanel(
                        value = it,
                        label = stringResource(R.string.sources_signing_key_new),
                        tone = SemanticTone.Error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Dialog for renaming a bundle.
 */
@Composable
fun DialogHostScope.RenameBundleDialog(
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
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            AppDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text(stringResource(R.string.patch_option_enter_value)) },
                leadingIcon = {
                    Icon(imageVector = MaterialSymbols.Rounded.Edit, contentDescription = null)
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
fun DialogHostScope.BundlePatchesDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource,
    initialQuery: String = ""
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val messages = koinInject<UserMessages>()
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
                    icon = MaterialSymbols.Rounded.Visibility_off,
                    tone = SemanticTone.Warning,
                    density = NoticeDensity.Compact
                )
            }
        },
        onExpertBadgeClick = { messages.show(expertBadgeTooltip) }
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
                            imageVector = MaterialSymbols.Rounded.Public,
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
    val shown = request?.let {
        sources.filterIsInstance<RemotePatchBundle>().find { it.uid == request.bundleUid }?.let { request to it }
    }
    DialogHost(target = shown) { (request, bundle) ->
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
fun DialogHostScope.BundleChangelogDialog(
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

    ListDialog(
        onDismissRequest = onDismissRequest,
        accentColor = rememberSourceHeaderColor(src),
        // Start fetch only after the dialog enter animation completes so the shimmer
        // is always visible first, even when data is cached and would resolve instantly
        onEntered = { if (fetchTrigger == 0) fetchTrigger = 1 },
        // Releases show only their versions, so the header names whose they are, and which app
        // they were narrowed to. It stays the same through loading, so nothing shifts once the
        // entries arrive
        header = {
            ListDialogHeader(
                icon = { modifier -> BundleIcon(bundle = src, modifier = modifier) },
                title = src.displayTitle,
                subtitle = subject?.appNames?.firstOrNull()?.let { stringResource(R.string.changelog_for_app, it) }
                    ?: src.installedVersionSignature?.withVersionPrefix()?.isolateLtr().orEmpty()
            )
        },
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
                                icon = MaterialSymbols.Rounded.Refresh
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
                modifier = Modifier.padding(LocalDialogBarInsets.current)
            )
            is BundleChangelogState.Error -> ChangelogError(error = current.throwable)
            is BundleChangelogState.Entries -> {
                // Releases with nothing for the app still leave its history below them to read
                if (current.entries.isEmpty() && older.entries?.isEmpty() == true) {
                    EmptyState(
                        message = stringResource(R.string.changelog_empty),
                        icon = MaterialSymbols.Rounded.History,
                        modifier = Modifier.padding(LocalDialogBarInsets.current)
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
                        }
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
 * Laid out like a patch list: a ticked app is brought, a tap answers for one app and the header
 * answers for everything the search left, so the few wanted out of hundreds take one tap there and
 * a few on them. Leaving out the apps the source adds later keeps that cut as it grows.
 */
@Composable
fun DialogHostScope.SourceAppsDialog(
    onDismissRequest: () -> Unit,
    src: PatchBundleSource
) {
    val patchBundleRepository: PatchBundleRepository = koinInject()
    val sourceMuteRepository: SourceMuteRepository = koinInject()
    val installedAppRepository: InstalledAppRepository = koinInject()
    val scope = rememberCoroutineScope()

    // Every source, so a disabled one still lists what it would bring once switched back on
    val bundleInfo by patchBundleRepository.allBundlesInfoFlow.collectAsStateWithLifecycle(emptyMap())
    val appMetadata by patchBundleRepository.allAppMetadata.collectAsStateWithLifecycle()
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(emptyMap())
    // Null until read, so the switch never shows the default before the stored answer
    val unseenNewApps by sourceMuteRepository.unseenNewApps.collectAsStateWithLifecycle(null)
    val newApps = unseenNewApps?.get(src.uid).orEmpty()
    // A patched app keeps its home card whichever sources bring it, so leaving it out does not move it
    val installedApps by remember { installedAppRepository.getAll() }.collectAsStateWithLifecycle(emptyList())
    val patchedApps = remember(installedApps) { installedApps.mapTo(mutableSetOf()) { it.originalPackageName } }

    val sourceInfo = bundleInfo[src.uid]
    val listedApps = remember(sourceInfo) { sourceInfo?.listedApps().orEmpty() }
    // By the rule the home screen applies, so a tick here is a card there
    val broughtApps = remember(sourceInfo, keptFrom) { sourceInfo?.appsBrought(keptFrom).orEmpty() }
    // What each app gets from this source, so the choice is not made on a name alone
    val patchCounts = remember(sourceInfo) {
        buildMap {
            sourceInfo?.patches?.forEach { patch ->
                patch.compatiblePackages.orEmpty()
                    .mapNotNullTo(mutableSetOf()) { it.packageName }
                    .forEach { merge(it, 1, Int::plus) }
            }
        }
    }
    // New apps lead, as they are what the user came back for
    val apps = remember(listedApps, appMetadata, newApps) {
        listedApps
            .map { packageName -> packageName to (appMetadata[packageName]?.displayName ?: packageName) }
            .sortedWith(
                compareBy(
                    { (packageName, _) -> packageName !in newApps },
                    { (_, label) -> label.lowercase(Locale.ROOT) }
                )
            )
    }

    val search = rememberSearchFieldState(searchable = apps.size > 1)
    val filtered = remember(apps, search.query) {
        if (search.query.isBlank()) apps
        else apps.filter { (packageName, label) ->
            label.contains(search.query, ignoreCase = true) ||
                    packageName.contains(search.query, ignoreCase = true)
        }
    }

    // Scoped to the search, so bringing or leaving out all never reaches apps out of view
    val allFilteredBrought = remember(filtered, broughtApps) {
        filtered.all { (packageName, _) -> packageName in broughtApps }
    }

    // Written in full even if the dialog is gone before it finishes, so a list never lands half
    // applied
    fun persist(write: suspend () -> Unit) {
        scope.launch { withContext(NonCancellable) { write() } }
    }

    fun bring(packageNames: Collection<String>, brought: Boolean) = persist {
        if (brought) sourceMuteRepository.unmuteApps(src.uid, packageNames)
        else sourceMuteRepository.muteApps(src.uid, packageNames)
    }

    // The new apps have been seen once the list closes, so the source card stops counting them
    fun dismiss() {
        if (newApps.isNotEmpty()) persist { sourceMuteRepository.markNewAppsSeen(src.uid) }
        onDismissRequest()
    }

    ListDialog(
        onDismissRequest = ::dismiss,
        accentColor = rememberSourceHeaderColor(src),
        // Headed by the source, as its patches are
        header = {
            ListDialogHeader(
                icon = { modifier -> BundleIcon(bundle = src, modifier = modifier) },
                title = src.displayTitle,
                subtitle = pluralStringResource(R.plurals.home_category_app_count, apps.size, apps.size.toString()),
                search = search,
                searchLabel = stringResource(R.string.home_search_apps),
                // A lone app leaves nothing to search through
                searchEnabled = apps.size > 1,
                // Answers for everything in view at once, so keeping a few apps out of hundreds is
                // one tap here and a few on the apps wanted
                actions = {
                    TitleAction(
                        icon = if (allFilteredBrought) MaterialSymbols.Rounded.Remove_done else MaterialSymbols.Rounded.Done_all,
                        contentDescription = stringResource(
                            if (allFilteredBrought) R.string.deselect_all else R.string.select_all
                        ),
                        // Only the apps that change, so a few left out of hundreds write a few rows
                        onClick = {
                            bring(
                                filtered.mapNotNull { (packageName, _) ->
                                    packageName.takeIf { (it in broughtApps) == allFilteredBrought }
                                },
                                brought = !allFilteredBrought
                            )
                        },
                        style = TitleActionStyle.Toggle,
                        active = allFilteredBrought,
                        enabled = filtered.isNotEmpty()
                    )
                }
            )
        },
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = ::dismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
        hideFooterWhileTyping = true
    ) {
        SearchFieldBackHandler(search)

        DialogLazyList(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DialogListSpacing)
        ) {
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

            unseenNewApps?.let { lists ->
                item(key = "new_apps") {
                    val bringsNewApps = src.uid !in lists
                    SettingsSwitchItem(
                        checked = bringsNewApps,
                        onToggle = {
                            persist { sourceMuteRepository.setBringsNewApps(src.uid, !bringsNewApps, listedApps) }
                        },
                        icon = MaterialSymbols.Rounded.Auto_awesome,
                        title = stringResource(R.string.sources_apps_new_apps),
                        subtitle = stringResource(R.string.sources_apps_new_apps_description),
                        showBorder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (filtered.isEmpty() && search.query.isNotBlank()) {
                item(key = "empty_state") {
                    EmptyState(
                        message = stringResource(R.string.search_no_results),
                        icon = MaterialSymbols.Rounded.Search_off,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            items(items = filtered, key = { (packageName, _) -> packageName }) { (packageName, label) ->
                val brought = packageName in broughtApps
                val gradientColors = appMetadata[packageName]?.gradientColors
                    ?: AppCardColorDefaults.defaultGradientColors

                // The card's mark and dimming tell whether the source brings it, so they mean nothing else
                SelectableCard(
                    modifier = Modifier.animatedListItem(this),
                    isSelected = brought,
                    isSelectionMode = true
                ) {
                    AppCardLayout(
                        gradientColors = gradientColors,
                        onClick = { bring(listOf(packageName), brought = !brought) },
                        selected = brought,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val patchCount = patchCounts[packageName] ?: 0
                        AppCardContent(
                            packageName = packageName,
                            packageInfo = null,
                            displayName = label,
                            subtitle = listOfNotNull(
                                stringResource(R.string.sources_apps_new).takeIf { packageName in newApps },
                                pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString()),
                                // Left out, it would be gone from the home screen but for its install
                                stringResource(R.string.sources_apps_stays_patched)
                                    .takeIf { !brought && packageName in patchedApps }
                            ).joinToString(" · "),
                            gradientColors = gradientColors
                        )
                    }
                }
            }
        }
    }
}
