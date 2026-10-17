/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.view.HapticFeedbackConstants
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.AboutViewModel
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.isolateLtr
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

// Taps on the version needed to be offered the pills, the count Android itself asks for
private const val PILL_TAP_COUNT = 7

// Counting stops after a pause, so the seventh tap has to belong to one deliberate run
private val PILL_TAP_TIMEOUT = 1500.milliseconds

/**
 * About dialog: the manager's icon, version, description and social links, with its credits, and
 * the licenses past them, opening in place of it.
 */
@Composable
fun DialogHostScope.AboutDialog(
    onDismiss: () -> Unit,
    themeViewModel: ThemeSettingsViewModel = koinViewModel()
) {
    // The credits, and the licenses past them, open in place of the about page in the same dialog
    var page by rememberSaveable { mutableStateOf(AboutPage.About) }
    val showPillDialog = remember { mutableStateOf(false) }
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()

    DialogHost(visible = showPillDialog.value) {
        RedPillDialog(
            alreadyUnlocked = matrixUnlocked,
            onRedPill = { themeViewModel.unlockMatrixBackground() },
            // Waking up puts the rain back out of sight, and the pills stay where they were found
            onBluePill = { if (matrixUnlocked) themeViewModel.forgetMatrixBackground() },
            onDismiss = { showPillDialog.value = false }
        )
    }

    DialogPages(page = page, key = { it }, modifier = Modifier.fillMaxSize()) { shown ->
        when (shown) {
            AboutPage.About -> AboutOverview(
                onDismiss = onDismiss,
                onOpenCredits = { page = AboutPage.Credits },
                onOpenPill = { showPillDialog.value = true }
            )
            AboutPage.Credits -> CreditsPage(
                onBack = { page = AboutPage.About },
                onOpenLicenses = { page = AboutPage.Licenses }
            )
            AboutPage.Licenses -> LicensesPage(onBack = { page = AboutPage.Credits })
        }
    }
}

/** The pages of the about dialog, each going back to the one before it. */
private enum class AboutPage { About, Credits, Licenses }

/** The manager itself: its icon, version, description and social links. */
@Composable
private fun DialogHostScope.AboutOverview(
    onDismiss: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenPill: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    var versionTaps by remember { mutableIntStateOf(0) }

    LaunchedEffect(versionTaps) {
        if (versionTaps == 0) return@LaunchedEffect

        delay(PILL_TAP_TIMEOUT)
        versionTaps = 0
    }

    AppDialog(
        onDismissRequest = onDismiss,
        footer = {
            AppDialogActions(
                actions = listOf(
                    DialogAction(
                        text = stringResource(R.string.credits),
                        onClick = onOpenCredits,
                        icon = Icons.Outlined.People,
                        emphasis = DialogActionEmphasis.Outlined
                    ),
                    DialogAction(
                        text = stringResource(R.string.close),
                        onClick = onDismiss
                    )
                ),
                layout = DialogButtonLayout.Vertical
            )
        }
    ) {
        val textColor = dialogTextColor()
        val secondaryColor = dialogSecondaryTextColor()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraLarge)
        ) {
            // App icon on a plain tile, the way the manager sets off every other object it shows
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(neutralVeil(dialogTextColor()), RoundedCornerShape(Defaults.CornerExtraLarge)),
                contentAlignment = Alignment.Center
            ) {
                val icon = rememberDrawablePainter(
                    drawable = remember {
                        AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_default)
                    }
                )
                Image(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(Defaults.CornerLargeIncreased))
                )
            }

            // App Name & Version
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = stringResource(R.string.version) + " " + BuildConfig.VERSION_NAME.isolateLtr(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Defaults.CornerSmall))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            versionTaps++
                            when {
                                versionTaps >= PILL_TAP_COUNT -> {
                                    versionTaps = 0
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onOpenPill()
                                }
                                // The halfway point is where the taps start answering back,
                                // enough of a hint to keep going without naming what is coming
                                versionTaps > PILL_TAP_COUNT / 2 ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                        .padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceExtraSmall)
                )
            }

            // Description
            Text(
                text = stringResource(R.string.settings_system_manager_description),
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            // Social Links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AboutViewModel.socials.forEach { link ->
                    SocialIconButton(
                        icon = AboutViewModel.getSocialIcon(link.name),
                        contentDescription = link.name,
                        onClick = { uriHandler.openUri(link.url) },
                        textColor = textColor
                    )
                }
            }
        }
    }
}

/**
 * Social link button.
 * Styled button for opening social media links.
 */
@Composable
private fun SocialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = rememberPressShape(interactionSource, Defaults.PillShape),
        color = textColor.copy(alpha = 0.1f),
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
