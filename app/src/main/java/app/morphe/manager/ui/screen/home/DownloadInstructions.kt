/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 */
@Composable
internal fun DownloadInstructionsDialog(
    downloadUrl: String?,
    requestedVersion: String?,
    usingMountInstall: Boolean,
    targetAppInstalled: Boolean,
    downloadColor: Color,
    isApkBundle: Boolean,
    onDismiss: () -> Unit,
    onOpenApkDownloadHelper: (() -> Unit)? = null,
    onContinue: () -> Unit
) {
    // Never falls back to unresolved once the destination is known, so the instructions stay
    // put while the dialog animates out and the pending download data is already cleared
    var source by remember { mutableStateOf<ApkDownloadSource>(ApkDownloadSource.Unresolved) }
    LaunchedEffect(downloadUrl) {
        ApkDownloadSource.from(downloadUrl)
            .takeIf { it != ApkDownloadSource.Unresolved }
            ?.let { source = it }
    }

    val continueText = stringResource(
        R.string.home_download_instructions_continue_to,
        source.destinationLabel()
    )

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_download_instructions_title),
        footer = {
            if (onOpenApkDownloadHelper != null) {
                AppDialogButtonRow(
                    primaryText = continueText,
                    onPrimaryClick = onContinue,
                    primaryIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    secondaryText = stringResource(R.string.home_apk_helper_download),
                    onSecondaryClick = onOpenApkDownloadHelper,
                    secondaryIcon = Icons.Outlined.Download,
                    layout = DialogButtonLayout.Vertical
                )
            } else {
                AppDialogButton(
                    text = continueText,
                    onClick = onContinue,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        val textColor = LocalDialogTextColor.current
        val secondaryColor = LocalDialogSecondaryTextColor.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_download_instructions_steps_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            // The redirect resolves while the dialog is already up, so the steps are swapped
            // for the site specific ones as soon as the destination is known
            Crossfade(
                targetState = source,
                // Site specific steps are not all the same height, so the dialog grows into them
                modifier = Modifier.animateContentSize(),
                animationSpec = tween(Defaults.ANIMATION_DURATION_SHORT),
                label = "downloadInstructions"
            ) { currentSource ->
                val mountInstallRequired = usingMountInstall && !targetAppInstalled
                val steps = currentSource.instructionSteps(
                    requestedVersion = requestedVersion,
                    mountInstallRequired = mountInstallRequired
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InstructionStep(
                                number = "${index + 1}",
                                text = step.text,
                                textColor = textColor,
                                secondaryColor = secondaryColor
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
            val contentColor = if (buttonColor.requiresLightContent()) Color.White else Color.Black

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
            shape = RoundedCornerShape(8.dp),
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

@Composable
private fun InstructionStep(
    number: String,
    text: AnnotatedString,
    textColor: Color,
    secondaryColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.6f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
            modifier = Modifier.weight(1f)
        )
    }
}
