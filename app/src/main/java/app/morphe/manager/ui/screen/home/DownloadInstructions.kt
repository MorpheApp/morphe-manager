/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.*
import java.net.URI

/**
 * The site a download link leads to, and the instructions that match what it puts on screen.
 *
 * The API answers the version lookup with a single redirect and picks the host itself, so the
 * destination is read off the resolved URL instead of assuming everything comes from APKMirror.
 *
 * @param label Site name shown to the user. Null while the destination is not known yet.
 */
sealed class ApkDownloadSource(val label: String?) {
    /**
     * @param onDownloadPage True for the page carrying the download button. A release page stops
     * short of it, with the APK variants still to be picked from.
     */
    data class ApkMirror(val onDownloadPage: Boolean) : ApkDownloadSource("APKMirror.com")

    data object Uptodown : ApkDownloadSource("Uptodown.com")

    /** Search results rather than one app page, used when the API knows no direct link. */
    data object WebSearch : ApkDownloadSource("Google")

    /** A link to the APK itself, which the browser downloads without putting up a page. */
    data class DirectFile(val host: String) : ApkDownloadSource(host)

    /** Any other host the API points at, named after itself so the button never promises the wrong site. */
    data class Other(val host: String) : ApkDownloadSource(host)

    /** The redirect has not been followed yet, so nothing site specific can be shown. */
    data object Unresolved : ApkDownloadSource(null)

    companion object {
        private val DOWNLOADABLE_EXTENSIONS = setOf("apk", "apkm", "apks", "xapk")

        /**
         * Works out where [url] leads.
         *
         * Until the redirect is followed the URL still points at the API, which says nothing
         * about where the user will end up, so that case resolves to [Unresolved].
         */
        fun from(url: String?): ApkDownloadSource {
            if (url == null || url.startsWith(MORPHE_API_URL)) return Unresolved

            val uri = runCatching { URI(url) }.getOrNull() ?: return Unresolved
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return Unresolved
            val path = uri.path.orEmpty().trimEnd('/')
            val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

            return when {
                extension in DOWNLOADABLE_EXTENSIONS -> DirectFile(host)
                host.endsWith("apkmirror.com") ->
                    ApkMirror(onDownloadPage = path.endsWith("-android-apk-download"))
                host.endsWith("uptodown.com") -> Uptodown
                host.endsWith("google.com") -> WebSearch
                else -> Other(host)
            }
        }
    }
}

/** Where the continue button leads, worded generically while the destination is still unknown. */
@Composable
private fun ApkDownloadSource.destinationLabel(): String =
    label ?: stringResource(R.string.home_download_instructions_destination)

/** Site button an instruction step points at, redrawn below the step so it can be recognized. */
private enum class SiteButton { ApkMirror, Uptodown }

private val UptodownBrandColor = Color(0xFF4CB050)

/**
 * One numbered instruction line.
 *
 * @param button Button to redraw below the text, when the step tells the user to press one.
 * @param note Caveat about the page, shown below the step.
 */
private data class DownloadStep(
    val text: AnnotatedString,
    val button: SiteButton? = null,
    val note: String? = null
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
            add(
                DownloadStep(
                    text = AnnotatedString(stringResource(R.string.home_download_instructions_step2_part1)),
                    button = SiteButton.ApkMirror
                )
            )
        }

        // The browser is handed the file itself, so there is no page to find anything on
        is ApkDownloadSource.DirectFile -> emptyList()

        ApkDownloadSource.Uptodown -> listOf(
            DownloadStep(
                text = AnnotatedString(stringResource(R.string.home_download_instructions_step2_part1)),
                button = SiteButton.Uptodown
            )
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

    val backInMorphe = listOf(
        DownloadStep(
            htmlAnnotatedString(
                stringResource(
                    if (mountInstallRequired) {
                        R.string.home_download_instructions_step3_mount
                    } else {
                        R.string.home_download_instructions_step3
                    }
                )
            )
        ),
        DownloadStep(
            AnnotatedString(
                stringResource(
                    if (mountInstallRequired) {
                        R.string.home_download_instructions_step4_mount
                    } else {
                        R.string.home_download_instructions_step4
                    }
                )
            )
        )
    )

    return listOf(openSite) + onSite + backInMorphe
}

/**
 * Dialog 2: Download instructions dialog.
 *
 * @param downloadUrl Best link known so far. Null or still unresolved keeps the wording generic.
 * @param requestedVersion Version the APK has to be. Null when any version can be patched.
 * @param downloadColor App accent color, which APKMirror tints its download button with.
 * @param isApkBundle Whether the bundle requires a split archive, which APKMirror labels differently.
 * @param onContinue Moves on to picking the downloaded file once the link is handed off, given how
 * to hand it off. That returns false when the link could not be passed on.
 */
@Composable
internal fun DownloadInstructionsDialog(
    appName: String,
    packageName: String?,
    downloadUrl: String?,
    requestedVersion: String?,
    usingMountInstall: Boolean,
    stockAppInstalled: Boolean,
    downloadColor: Color,
    isApkBundle: Boolean,
    onDismiss: () -> Unit,
    onOpenApkDownloadHelper: (() -> Unit)? = null,
    onContinue: (handOff: (String) -> Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val copyToClipboard = rememberCopyToClipboard(stringResource(R.string.home_download_instructions_link_copied))
    val openLink = { onContinue { url -> runCatching { uriHandler.openUri(url) }.isSuccess } }
    // A copied link is opened in another browser or a download manager, and the file it fetches
    // comes back through the same picker an opened one leads to
    val copyLink = { onContinue { url -> copyToClipboard(url); true } }

    // Never falls back to unresolved once the destination is known, so the instructions stay
    // put while the dialog animates out and the pending download data is already cleared
    var source by remember { mutableStateOf<ApkDownloadSource>(ApkDownloadSource.Unresolved) }
    LaunchedEffect(downloadUrl) {
        ApkDownloadSource.from(downloadUrl)
            .takeIf { it != ApkDownloadSource.Unresolved }
            ?.let { source = it }
    }

    // Nothing can be said about the download until the redirect lands, and the link on hand
    // until then is the unfollowed one, which is exactly what must not be opened
    val resolving = source == ApkDownloadSource.Unresolved

    // Latched for the same reason as the instructions above: dismissing the dialog withdraws the
    // helper action at once, and a footer that drops a button mid-exit reads as a glitch
    var offersHelper by remember { mutableStateOf(false) }
    LaunchedEffect(onOpenApkDownloadHelper != null) {
        if (onOpenApkDownloadHelper != null) offersHelper = true
    }

    val continueText = stringResource(
        R.string.home_download_instructions_continue_to,
        source.destinationLabel()
    )

    AppDialog(
        onDismissRequest = onDismiss,
        accentColor = rememberAppColor(packageName),
        title = stringResource(R.string.home_download_instructions_title),
        // What the steps fetch, since the dialog before this one named it and is gone by now
        description = listOfNotNull(appName, requestedVersion?.withVersionPrefix()).joinToString(" · "),
        footer = {
            AppDialogActions(
                actions = listOfNotNull(
                    DialogAction(
                        text = continueText,
                        onClick = openLink,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        enabled = !resolving
                    ),
                    if (offersHelper) {
                        DialogAction(
                            text = stringResource(R.string.home_apk_helper_download),
                            // Nothing to open once the action is withdrawn, which is only the case
                            // while the dialog is on its way out
                            onClick = { onOpenApkDownloadHelper?.invoke() },
                            icon = Icons.Outlined.Download
                        )
                    } else null,
                    DialogAction(
                        text = stringResource(R.string.home_download_instructions_copy_link),
                        onClick = copyLink,
                        icon = Icons.Outlined.ContentCopy,
                        // The unfollowed link is no more fit to copy than to open
                        enabled = !resolving
                    )
                )
            )
        }
    ) {
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
                cornerRadius = Defaults.SettingsCornerRadius,
                showBorder = true,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = Defaults.ContentPadding,
                        vertical = Defaults.ItemSpacing
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
                                color = LocalDialogTextColor.current
                            )

                            step.button?.let { button ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SiteDownloadButton(
                                        button = button,
                                        downloadColor = downloadColor,
                                        isApkBundle = isApkBundle
                                    )
                                }
                            }

                            step.note?.let { note ->
                                Notice(
                                    text = note,
                                    tone = SemanticTone.Warning,
                                    icon = Icons.Outlined.Warning,
                                    density = NoticeDensity.Compact
                                )
                            }
                        }
                    }
                }
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
    isApkBundle: Boolean
) {
    val context = LocalContext.current
    val toasts = listOf(
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
        context.toast(
            string = toasts.getOrElse(clickCount - 1) { toasts.last() },
            duration = Toast.LENGTH_LONG
        )
    }

    when (button) {
        // APKMirror tints its download button with the app's own accent color
        SiteButton.ApkMirror -> {
            val buttonColor = downloadColor.ensureContrast(MaterialTheme.colorScheme.background)
            val contentColor = buttonColor.contrastingContent()

            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(1.dp),
                color = buttonColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
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
                    imageVector = Icons.Outlined.SaveAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Defaults.IconSizeSmall)
                )
            }
        }
    }
}

/** Side of the disc a step's number sits on. */
private val StepNumberSize = 26.dp

/** Rounding Uptodown gives its own download button, which the copy of it keeps. */
private val UptodownButtonCornerRadius = 8.dp

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
        horizontalArrangement = Arrangement.spacedBy(Defaults.ItemSpacing)
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
                .padding(top = 3.dp, bottom = if (isLast) 0.dp else Defaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
            content = content
        )
    }
}
