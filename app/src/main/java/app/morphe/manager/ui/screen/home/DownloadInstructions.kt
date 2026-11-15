/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.domain.manager.ApkDownloadSource
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.theme.isDarkTheme
import app.morphe.manager.util.*
import app.morphe.patcher.patch.ApkFileType
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import com.composables.icons.materialsymbols.roundedfilled.*
import org.koin.compose.koinInject

/** Where the continue button leads, worded generically while the destination is still unknown. */
@Composable
private fun ApkDownloadSource.destinationLabel(): String =
    label ?: stringResource(R.string.home_download_instructions_destination)

/** Site button an instruction step points at, redrawn below the step so it can be recognized. */
private enum class SiteButton { ApkMirror, Uptodown, GitHubAssets, ApkCube }

private val UptodownBrandColor = Color(0xFF4CB050)

/** APKCube's call-to-action blue, a shade brighter on its dark theme. */
private val ApkCubeButtonColorLight = Color(0xFF0066DB)
private val ApkCubeButtonColorDark = Color(0xFF1368E9)

/**
 * One numbered instruction line.
 *
 * @param button Button to redraw below the text, when the step tells the user to press one.
 * @param note Remark about the page, shown below the step.
 * @param noteTone Warning for a caveat, neutral for a plain hint.
 */
private data class DownloadStep(
    val text: AnnotatedString,
    val button: SiteButton? = null,
    val note: String? = null,
    val noteTone: SemanticTone = SemanticTone.Warning
)

/** The step that has the user press [button] once they have scrolled down to it. */
@Composable
private fun pressSiteButton(button: SiteButton) = DownloadStep(
    text = AnnotatedString(stringResource(R.string.home_download_instructions_step2_part1)),
    button = button
)

/**
 * Builds the numbered instructions for this source.
 *
 * Only the middle of the list is site specific: every source is reached the same way and,
 * once the file is downloaded, ends in the same two steps back inside Morphe.
 */
@Composable
private fun ApkDownloadSource.instructionSteps(
    requestedVersion: String?,
    mountInstallRequired: Boolean
): List<DownloadStep> {
    val openSite = DownloadStep(
        AnnotatedString(
            stringResource(
                R.string.home_download_instructions_step1,
                stringResource(R.string.home_download_instructions_continue_to, destinationLabel())
            )
        )
    )

    val onSite: List<DownloadStep> = when (this) {
        is ApkDownloadSource.ApkMirror -> buildList {
            // A release page lists the variants of one version, and only the variant's own page
            // carries the download button the next step points at
            if (!onDownloadPage) {
                add(
                    DownloadStep(
                        htmlAnnotatedString(stringResource(R.string.home_download_instructions_step2_variant))
                    )
                )
            }
            add(pressSiteButton(SiteButton.ApkMirror))
        }

        // The browser is handed the file itself, so there is no page to find anything on
        is ApkDownloadSource.DirectFile -> emptyList()

        ApkDownloadSource.Uptodown -> listOf(pressSiteButton(SiteButton.Uptodown))

        is ApkDownloadSource.GitHub -> {
            // The list of every release leaves the right one to be found, so it is named, and
            // without a version asked for the newest one at the top will do
            val tag = release ?: requestedVersion
            listOf(
                DownloadStep(
                    text = htmlAnnotatedString(
                        if (tag == null) {
                            stringResource(R.string.home_download_instructions_step2_github_latest)
                        } else {
                            stringResource(R.string.home_download_instructions_step2_github, tag)
                        }
                    ),
                    button = SiteButton.GitHubAssets,
                    // Releases often attach one APK per architecture next to a universal one
                    note = stringResource(
                        R.string.home_download_instructions_step2_github_note,
                        Build.SUPPORTED_ABIS.first()
                    ),
                    noteTone = SemanticTone.Neutral
                )
            )
        }

        // Without a pinned version the page offers the newest build, so a required one is picked first
        is ApkDownloadSource.ApkCube -> listOf(
            if (versionPinned || requestedVersion == null) {
                pressSiteButton(SiteButton.ApkCube)
            } else {
                DownloadStep(
                    text = htmlAnnotatedString(
                        stringResource(R.string.home_download_instructions_step2_apkcube, requestedVersion)
                    ),
                    button = SiteButton.ApkCube
                )
            }
        )

        ApkDownloadSource.WebSearch -> listOf(
            DownloadStep(
                text = AnnotatedString(stringResource(R.string.home_download_instructions_step2_search)),
                note = stringResource(R.string.home_download_instructions_step2_search_note)
            ),
            DownloadStep(
                htmlAnnotatedString(
                    // Results are ordered by the website, not by version, so the one to pick has
                    // to be named again here. Without a requested version any of them will do
                    if (requestedVersion == null) {
                        stringResource(R.string.home_download_instructions_step3_search_any)
                    } else {
                        stringResource(R.string.home_download_instructions_step3_search, requestedVersion)
                    }
                )
            )
        )

        // Nothing is known about the page, so the wording stops at what every download page has
        is ApkDownloadSource.Other, ApkDownloadSource.Unresolved -> listOf(
            DownloadStep(AnnotatedString(stringResource(R.string.home_download_instructions_step2_generic)))
        )
    }

    val (waitStep, returnStep) = if (mountInstallRequired) {
        R.string.home_download_instructions_step3_mount to R.string.home_download_instructions_step4_mount
    } else {
        R.string.home_download_instructions_step3 to R.string.home_download_instructions_step4
    }
    val backInMorphe = listOf(
        DownloadStep(htmlAnnotatedString(stringResource(waitStep))),
        DownloadStep(AnnotatedString(stringResource(returnStep)))
    )

    return listOf(openSite) + onSite + backInMorphe
}

/**
 * Dialog 2: Download instructions dialog.
 *
 * @param downloadUrl Best link known so far. Null or still unresolved keeps the wording generic.
 * @param requestedVersion Version the APK has to be. Null when any version can be patched.
 * @param downloadColor App accent color, which APKMirror tints its download button with.
 * @param apkFileType File type the bundle asks for, which APKMirror and APKCube put on their buttons.
 * @param onOpenApkDownloadHelper Downloads through a trusted helper app, null while none is.
 * @param onTrustApkDownloadHelpers Opens where a helper installed but not trusted yet is let in,
 * null while there is none or nowhere to open that from.
 * @param onContinue Moves on to picking the downloaded file once the link is handed off, given how
 * to hand it off. That returns false when the link could not be passed on.
 */
@Composable
internal fun DialogHostScope.DownloadInstructionsDialog(
    appName: String,
    packageName: String?,
    downloadUrl: String?,
    requestedVersion: String?,
    usingMountInstall: Boolean,
    stockAppInstalled: Boolean,
    downloadColor: Color,
    apkFileType: ApkFileType?,
    onDismiss: () -> Unit,
    onOpenApkDownloadHelper: (() -> Unit)? = null,
    onTrustApkDownloadHelpers: (() -> Unit)? = null,
    onContinue: (handOff: (String) -> Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val copyToClipboard = rememberCopyToClipboard(stringResource(R.string.home_download_instructions_link_copied))
    val openLink = { onContinue { url -> runCatching { uriHandler.openUri(url) }.isSuccess } }
    // A copied link is opened in another browser or a download manager, and the file it fetches
    // comes back through the same picker an opened one leads to
    val copyLink = { onContinue { url -> copyToClipboard(url); true } }

    // Read off the link the caller holds rather than kept here, so a dialog the screen brings back
    // shows the steps already found instead of finding them all over again
    val source = remember(downloadUrl) { ApkDownloadSource.from(downloadUrl) }

    // Nothing can be said about the download until the redirect lands, and the link on hand
    // until then is the unfollowed one, which is exactly what must not be opened
    val resolving = source == ApkDownloadSource.Unresolved

    // Latched for the same reason as the instructions above: dismissing the dialog withdraws the
    // helper actions at once, and a dialog that drops one mid-exit reads as a glitch
    val offersHelper = latchedOnce(onOpenApkDownloadHelper != null)
    val offersTrust = latchedOnce(onTrustApkDownloadHelpers != null)

    val continueText = stringResource(
        R.string.home_download_instructions_continue_to,
        source.destinationLabel()
    )

    // What the steps fetch, since the dialog before this one named it and is gone by now. The
    // version copies on a press, for a site searched by hand
    val copyVersion = rememberCopyToClipboard(stringResource(R.string.home_version_copied))
    val description = remember(appName, requestedVersion, copyVersion) {
        buildAnnotatedString {
            append(appName)
            if (requestedVersion != null) {
                append(" · ")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "version",
                        styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { copyVersion(requestedVersion) }
                    )
                ) { append(requestedVersion.withVersionPrefix()) }
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_download_instructions_title),
        description = description,
        footer = {
            AppDialogActions(
                actions = listOfNotNull(
                    DialogAction(
                        text = continueText,
                        onClick = openLink,
                        icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                        enabled = !resolving
                    ),
                    if (offersHelper) {
                        DialogAction(
                            text = stringResource(R.string.home_apk_helper_download),
                            // Nothing to open once the action is withdrawn, which is only the case
                            // while the dialog is on its way out
                            onClick = { onOpenApkDownloadHelper?.invoke() },
                            icon = MaterialSymbols.Rounded.Download
                        )
                    } else null,
                    DialogAction(
                        text = stringResource(R.string.home_download_instructions_copy_link),
                        onClick = copyLink,
                        icon = MaterialSymbols.Rounded.Content_copy,
                        // The unfollowed link is no more fit to copy than to open
                        enabled = !resolving
                    )
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)) {
            // Waiting shows as waiting rather than as instructions that rewrite themselves once
            // the destination turns out to be a different website
            Crossfade(
                targetState = source,
                // Neither the wait nor the site specific steps are the same height, so the dialog
                // grows into whatever it ends up holding
                modifier = Modifier.animateContentSize(),
                animationSpec = tween(Defaults.ANIMATION_DURATION_SHORT),
                label = "downloadInstructions"
            ) { currentSource ->
                if (currentSource == ApkDownloadSource.Unresolved) {
                    PulsingLogoWithCaption(
                        caption = stringResource(R.string.home_download_instructions_finding),
                        size = 96.dp,
                        spacing = 12.dp
                    )
                    return@Crossfade
                }

                val mountInstallRequired = usingMountInstall && !stockAppInstalled
                val steps = currentSource.instructionSteps(
                    requestedVersion = requestedVersion,
                    mountInstallRequired = mountInstallRequired
                )

                // On a card of their own, as the version lists before them are, numbered down a rail
                SurfaceCard(
                    cornerRadius = Defaults.CornerLarge,
                    showBorder = true,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = Defaults.SpaceLarge,
                            vertical = Defaults.SpaceMedium
                        )
                    ) {
                        steps.forEachIndexed { index, step ->
                            InstructionStep(
                                number = index + 1,
                                isLast = index == steps.lastIndex
                            ) {
                                Text(
                                    text = step.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = dialogTextColor()
                                )

                                step.button?.let { button ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SiteDownloadButton(
                                            button = button,
                                            downloadColor = downloadColor,
                                            apkFileType = apkFileType
                                        )
                                    }
                                }

                                step.note?.let { note ->
                                    Footnote(
                                        text = note,
                                        tone = step.noteTone
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // A helper the user has installed but not trusted is not offered above, so this says why
            if (offersTrust) {
                Notice(
                    text = stringResource(R.string.home_apk_helper_untrusted),
                    tone = SemanticTone.Primary,
                    icon = MaterialSymbols.Rounded.Download,
                    action = NoticeAction(
                        text = stringResource(R.string.home_apk_helper_untrusted_action),
                        onClick = { onTrustApkDownloadHelpers?.invoke() }
                    )
                )
            }
        }
    }
}

/**
 * Redraws the button the user has to find on the website.
 *
 * Pressing it downloads nothing, so every copy answers with the same nudge back to the website.
 */
@Composable
private fun SiteDownloadButton(
    button: SiteButton,
    downloadColor: Color,
    apkFileType: ApkFileType?
) {
    val messages = koinInject<UserMessages>()
    val nudges = listOf(
        stringResource(R.string.home_download_instructions_download_button_toast),
        stringResource(R.string.home_download_instructions_download_button_toast_2),
        stringResource(R.string.home_download_instructions_download_button_toast_3),
        stringResource(R.string.home_download_instructions_download_button_toast_4),
        stringResource(R.string.home_download_instructions_download_button_toast_5),
        stringResource(R.string.home_download_instructions_download_button_toast_6),
    )
    var clickCount by remember { mutableIntStateOf(0) }
    val onClick = {
        clickCount++
        messages.show(nudges.getOrElse(clickCount - 1) { nudges.last() }, long = true)
    }

    when (button) {
        // APKMirror tints its download button with the app's own accent color
        SiteButton.ApkMirror -> {
            val isApkBundle = apkFileType?.isApk == false
            val buttonColor = downloadColor.ensureContrast(MaterialTheme.colorScheme.background)
            val contentColor = buttonColor.contrastingContent()

            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(1.dp),
                color = buttonColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Defaults.SpaceMedium, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.RoundedFilled.Download,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(Defaults.IconSizeSmall)
                        )
                        Text(
                            text = if (isApkBundle) "DOWNLOAD APK BUNDLE" else "DOWNLOAD APK",
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }

                    // APKMirror spells out what a bundle holds on a second line, with the file
                    // size and split count we have no way of knowing left out
                    if (isApkBundle) {
                        Text(
                            text = "Base APK and splits",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                    }
                }
            }
        }

        // Uptodown uses its own brand color for every app, so the accent color plays no part here
        SiteButton.Uptodown -> Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(UptodownButtonCornerRadius),
            color = UptodownBrandColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Free",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = MaterialSymbols.Rounded.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Defaults.IconSizeSmall)
                )
            }
        }

        // APKCube keeps its own blue for every app and follows the page theme, as its site does
        SiteButton.ApkCube -> Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(ApkCubeButtonCornerRadius),
            color = if (isDarkTheme()) ApkCubeButtonColorDark else ApkCubeButtonColorLight,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ApkCubeButtonHeight)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(ApkCubeButtonIconSize)
                )
                Text(
                    // APKCube packs every split app into an APKS, whatever archive the bundle asks for
                    text = if (apkFileType?.isApk == false) "Download APKS" else "Download APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        SiteButton.GitHubAssets -> GitHubAssets(onClick = onClick)
    }
}

/** GitHub's palette for one theme, as its release page shows the attached files. */
private class GitHubColors(
    val background: Color,
    val border: Color,
    val text: Color,
    val link: Color,
    val muted: Color
)

private val GitHubLight = GitHubColors(
    background = Color(0xFFFFFFFF),
    border = Color(0xFFD1D9E0),
    text = Color(0xFF1F2328),
    link = Color(0xFF0969DA),
    muted = Color(0xFF59636E)
)

private val GitHubDark = GitHubColors(
    background = Color(0xFF0D1117),
    border = Color(0xFF3D444D),
    text = Color(0xFFF0F6FC),
    link = Color(0xFF4493F8),
    muted = Color(0xFF9198A1)
)

/**
 * The Assets section of a GitHub release: the APK the user is after, above the source archive
 * GitHub attaches to every release, so the right row is told apart from the one always there.
 *
 * Its name is left out, as every project names its files its own way.
 */
@Composable
private fun GitHubAssets(onClick: () -> Unit) {
    // Drawn in the theme of the app, which is most often the one the browser shows GitHub in
    val colors = if (isDarkTheme()) GitHubDark else GitHubLight

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GitHubCornerRadius),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(vertical = Defaults.SpaceSmall)) {
            Row(
                modifier = Modifier.padding(horizontal = Defaults.SpaceSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Arrow_drop_down,
                    contentDescription = null,
                    tint = colors.muted
                )
                Text(
                    text = "Assets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Defaults.SpaceExtraSmall),
                color = colors.border
            )
            GitHubAssetRow(
                icon = MaterialSymbols.Rounded.Deployed_code,
                name = "*.apk",
                colors = colors,
                bold = true
            )
            GitHubAssetRow(
                icon = MaterialSymbols.Rounded.Folder_zip,
                name = "Source code (zip)",
                colors = colors
            )
        }
    }
}

@Composable
private fun GitHubAssetRow(
    icon: ImageVector,
    name: String,
    colors: GitHubColors,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(Defaults.IconSizeSmall)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else null,
            color = colors.link
        )
    }
}

/** Side of the disc a step's number sits on. */
private val StepNumberSize = 26.dp

/** Rounding Uptodown gives its own download button, which the copy of it keeps. */
private val UptodownButtonCornerRadius = 8.dp

/** Rounding of the boxes GitHub draws around a release's files. */
private val GitHubCornerRadius = 6.dp

/** Rounding of APKCube's download button, 0.6 of its 10px base radius. */
private val ApkCubeButtonCornerRadius = 6.dp

/** Height of the large download button on APKCube's download page. */
private val ApkCubeButtonHeight = 48.dp

/** Side of the download icon on APKCube's large button. */
private val ApkCubeButtonIconSize = 18.dp

/**
 * One step of the instructions: its [number] on a disc in the color of the app being patched, and a
 * rail down to the next one, so the steps read as a sequence rather than a list of remarks.
 */
@Composable
private fun InstructionStep(
    number: Int,
    isLast: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalAccent.current ?: MaterialTheme.colorScheme.primary
    val disc = accent.copy(alpha = AccentAlpha.LEAD)

    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(StepNumberSize)
                    .background(disc, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = appAccentContent(disc)
                )
            }
            if (!isLast) AccentRail(modifier = Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                // Past the last step there is no rail to leave room for
                .padding(top = Defaults.SpaceExtraSmall, bottom = if (isLast) 0.dp else Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
            content = content
        )
    }
}

/** True from the first time [present] is, as a dialog holds what it offered while it animates out. */
@Composable
private fun latchedOnce(present: Boolean): Boolean {
    // Read only as the caller recomposes for [present] anyway, so a plain field is enough
    val latch = remember { Latch() }
    if (present) latch.on = true
    return latch.on
}

private class Latch(var on: Boolean = false)
