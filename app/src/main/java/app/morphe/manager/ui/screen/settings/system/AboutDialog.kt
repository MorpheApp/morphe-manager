/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

/** The manager itself: its icon, version, description and social links. */
@Composable
fun DialogHostScope.AboutDialog(
    onDismiss: () -> Unit,
    themeViewModel: ThemeSettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val view = LocalView.current
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()
    var versionTaps by remember { mutableIntStateOf(0) }
    var showPillDialog by remember { mutableStateOf(false) }

    LaunchedEffect(versionTaps) {
        if (versionTaps == 0) return@LaunchedEffect

        delay(PILL_TAP_TIMEOUT)
        versionTaps = 0
    }

    DialogHost(visible = showPillDialog) {
        RedPillDialog(
            alreadyUnlocked = matrixUnlocked,
            onRedPill = { themeViewModel.unlockMatrixBackground() },
            // Waking up puts the rain back out of sight, and the pills stay where they were found
            onBluePill = { if (matrixUnlocked) themeViewModel.forgetMatrixBackground() },
            onDismiss = { showPillDialog = false }
        )
    }

    AppDialog(
        onDismissRequest = onDismiss,
        footer = {
            AppDialogOutlinedButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
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
                    .background(neutralVeil(textColor), RoundedCornerShape(Defaults.CornerExtraLarge)),
                contentAlignment = Alignment.Center
            ) {
                val icon = rememberDrawablePainter(
                    drawable = remember {
                        ContextCompat.getDrawable(context, R.mipmap.ic_launcher_default)
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
                                    showPillDialog = true
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
                        onClick = { openUrl(link.url) },
                        textColor = textColor
                    )
                }
            }
        }
    }
}

/** A round button opening one of the manager's social links. */
@Composable
private fun SocialIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    textColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconTooltip(text = contentDescription) {
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
}
