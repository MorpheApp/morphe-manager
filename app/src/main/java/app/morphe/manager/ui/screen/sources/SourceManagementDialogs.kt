/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.sources

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.APIPatchBundle
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.usesPrerelease
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.RemoteUrlCheck
import app.morphe.manager.domain.repository.SourceMuteRepository
import app.morphe.manager.domain.repository.appsBrought
import app.morphe.manager.patcher.patch.PatchInfo
import app.morphe.manager.patcher.patch.appIconColorOf
import app.morphe.manager.ui.screen.home.*
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
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
 * Dialog for changing the link a source downloads from, checked as it is typed the way adding a
 * source checks it, see [PatchBundleRepository.changeRemoteLink].
 */
@Composable
fun DialogHostScope.ChangeSourceLinkDialog(
    initialValue: String,
    onCheckUrl: (String) -> RemoteUrlCheck,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(initialValue) }
    val check = remember(textValue) { textValue.trim().takeIf { it.isNotEmpty() }?.let(onCheckUrl) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val confirm = {
        if (check is RemoteUrlCheck.Accepted) {
            keyboardController?.hide()
            onConfirm(textValue.trim())
        }
    }

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.sources_dialog_remote_url),
        description = stringResource(R.string.sources_dialog_change_url),
        footer = {
            AppDialogButtonRow(
                primaryText = stringResource(android.R.string.ok),
                onPrimaryClick = confirm,
                primaryEnabled = check is RemoteUrlCheck.Accepted,
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
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            AppDialogTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = { Text(REMOTE_URL_PLACEHOLDER) },
                leadingIcon = {
                    Icon(imageVector = MaterialSymbols.Rounded.Link, contentDescription = null)
                },
                singleLine = true,
                isError = check is RemoteUrlCheck.Rejected,
                keyboardOptions = RemoteUrlKeyboardOptions,
                keyboardActions = KeyboardActions(onDone = { confirm() })
            )
            RemoteUrlCheckHint(check = check, showHint = false)
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
    val expertBadgeTooltip = stringResource(
        R.string.sources_patch_expert_badge_tooltip,
        settingsPath(R.string.settings_advanced_expert_mode)
    )

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
    // Null until read, so the list is never ordered or counted as if nothing were left out
    val keptFrom by sourceMuteRepository.mutedSources.collectAsStateWithLifecycle(null)
    // Null until read, so the switch never shows the default before the stored answer
    val unseenNewApps by sourceMuteRepository.unseenNewApps.collectAsStateWithLifecycle(null)
    val newApps = unseenNewApps?.get(src.uid).orEmpty()
    // A patched app keeps its home card whichever sources bring it, so leaving it out does not move it
    val installedApps by remember { installedAppRepository.getAll() }.collectAsStateWithLifecycle(emptyList())
    val patchedApps = remember(installedApps) { installedApps.mapTo(mutableSetOf()) { it.originalPackageName } }

    val sourceInfo = bundleInfo[src.uid]
    val listedApps = remember(sourceInfo) { sourceInfo?.listedApps().orEmpty() }
    // By the rule the home screen applies, so a tick here is a card there
    val broughtApps = remember(sourceInfo, keptFrom) {
        keptFrom?.let { sourceInfo?.appsBrought(it) }.orEmpty()
    }
    // The apps brought when the list opened, taken once both are read. A snapshot rather than a
    // live read, so an app ticked or unticked here keeps its place instead of jumping away
    val broughtAtOpen = remember(sourceInfo != null && keptFrom != null) { broughtApps }
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
    // New apps lead, as they are what the user came back for, then the brought ones, which a source
    // of hundreds of apps is usually opened again to change
    val apps = remember(listedApps, appMetadata, newApps, broughtAtOpen) {
        listedApps
            .map { packageName -> packageName to (appMetadata[packageName]?.displayName ?: packageName) }
            .sortedWith(
                compareBy(
                    { (packageName, _) -> packageName !in newApps },
                    { (packageName, _) -> packageName !in broughtAtOpen },
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
                subtitle = if (keptFrom == null) {
                    pluralStringResource(R.plurals.home_category_app_count, apps.size, apps.size.toString())
                } else {
                    stringResource(R.string.expert_mode_selected_count, broughtApps.size, apps.size)
                },
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
                                pluralStringResource(R.plurals.patch_count, patchCount, patchCount.toString()),
                                // Left out, it would be gone from the home screen but for its install
                                stringResource(R.string.sources_apps_stays_patched)
                                    .takeIf { !brought && packageName in patchedApps }
                            ).joinToString(" · "),
                            gradientColors = gradientColors
                        ) {
                            // Shown until the list is closed once, so it has to catch the eye
                            if (packageName in newApps) {
                                StatusBadge(
                                    text = stringResource(R.string.sources_apps_new),
                                    tone = SemanticTone.Primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
