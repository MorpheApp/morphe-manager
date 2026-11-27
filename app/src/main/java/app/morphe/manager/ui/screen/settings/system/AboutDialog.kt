/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings.system

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.BuildConfig
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.network.dto.AppUpdateCheck
import app.morphe.manager.ui.screen.home.ManagerChangelogDialog
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.AboutLink
import app.morphe.manager.ui.viewmodel.AboutViewModel
import app.morphe.manager.ui.viewmodel.ThemeSettingsViewModel
import app.morphe.manager.util.isolateLtr
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

// Taps on the version card needed to be offered the pills, the count Android itself asks for
private const val PILL_TAP_COUNT = 7

// Counting stops after a pause, so the seventh tap has to belong to one deliberate run
private val PILL_TAP_TIMEOUT = 1500.milliseconds

private val LogoTileSize = 64.dp

// Tiles stand two to a row, the layout the patched apps give the same links
private const val TILE_COLUMNS = 2

/**
 * The manager itself: what it is, its version with the update there is for it, the project's
 * links and the people behind it. Laid out as the About dialog of the patched apps is, so the two
 * read as one.
 */
@Composable
fun DialogHostScope.AboutDialog(
    onDismiss: () -> Unit,
    themeViewModel: ThemeSettingsViewModel = koinViewModel(),
    aboutViewModel: AboutViewModel = koinViewModel()
) {
    val openUrl = rememberOpenUrl()
    val matrixUnlocked by themeViewModel.prefs.matrixBackgroundUnlocked.getAsState()
    val updateCheck by aboutViewModel.updateCheck.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { aboutViewModel.checkForUpdate() }
    var showPillDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var peopleList by rememberSaveable { mutableStateOf<PeopleList?>(null) }

    DialogHost(visible = showPillDialog) {
        RedPillDialog(
            alreadyUnlocked = matrixUnlocked,
            onRedPill = { themeViewModel.unlockMatrixBackground() },
            // Waking up puts the rain back out of sight, and the pills stay where they were found
            onBluePill = { if (matrixUnlocked) themeViewModel.forgetMatrixBackground() },
            onDismiss = { showPillDialog = false }
        )
    }

    DialogHost(visible = showUpdateDialog) {
        ManagerChangelogDialog(
            onDismiss = { showUpdateDialog = false },
            expectsUpdate = true
        )
    }

    DialogHost(target = peopleList) { list ->
        PeopleDialog(list = list, onDismiss = { peopleList = null })
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
        ) {
            AboutHeader()

            VersionCard(
                updateCheck = updateCheck,
                onUpdateClick = { showUpdateDialog = true },
                onPillsFound = { showPillDialog = true }
            )

            Column(verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)) {
                Text(
                    text = stringResource(R.string.about_links),
                    style = MaterialTheme.typography.labelLarge,
                    color = dialogSecondaryTextColor(),
                    modifier = Modifier.padding(horizontal = Defaults.SpaceExtraSmall)
                )
                // Leads the links on a row of its own, as what keeps the project going
                AboutTile(
                    link = AboutViewModel.donateLink,
                    onClick = { openUrl(AboutViewModel.donateLink.url) },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
                AboutViewModel.links.chunked(TILE_COLUMNS).forEach { row ->
                    TileRow {
                        row.forEach { link ->
                            AboutTile(
                                link = link,
                                onClick = { openUrl(link.url) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keeps the last tile of an odd count as wide as the ones above it
                        repeat(TILE_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                // The people behind the project close the same block the links are in
                TileRow {
                    AboutTile(
                        title = stringResource(R.string.contributors),
                        icon = MaterialSymbols.Rounded.Group,
                        onClick = { peopleList = PeopleList.CONTRIBUTORS },
                        modifier = Modifier.weight(1f)
                    )
                    AboutTile(
                        title = stringResource(R.string.supporters),
                        icon = MaterialSymbols.Rounded.Volunteer_activism,
                        onClick = { peopleList = PeopleList.SUPPORTERS },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** The logo beside the name, on a plain tile as the manager sets off every other object it shows. */
@Composable
private fun AboutHeader() {
    val textColor = dialogTextColor()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceLarge)
    ) {
        Box(
            modifier = Modifier
                .size(LogoTileSize)
                // The logo is drawn past its bounds to fill them, see MorpheLauncherLogo
                .clip(RoundedCornerShape(Defaults.CornerLargeIncreased))
                .background(neutralVeil(textColor)),
            contentAlignment = Alignment.Center
        ) {
            MorpheLauncherLogo(modifier = Modifier.fillMaxSize())
        }

        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            // Names which part of Morphe this is, as the patched apps name the patches
            Text(
                text = stringResource(R.string.about_component),
                style = MaterialTheme.typography.bodyMedium,
                color = dialogSecondaryTextColor()
            )
        }
    }
}

/**
 * The version on a card of its own, over what the last check for a newer one came back with.
 *
 * @param updateCheck Result of that check, or null before the first one.
 * @param onPillsFound Called once the card has been tapped often enough.
 */
@Composable
private fun VersionCard(
    updateCheck: AppUpdateCheck?,
    onUpdateClick: () -> Unit,
    onPillsFound: () -> Unit
) {
    val view = LocalView.current
    val currentOnPillsFound by rememberUpdatedState(onPillsFound)
    var versionTaps by remember { mutableIntStateOf(0) }

    LaunchedEffect(versionTaps) {
        if (versionTaps == 0) return@LaunchedEffect

        delay(PILL_TAP_TIMEOUT)
        versionTaps = 0
    }

    SurfaceCard(
        cornerRadius = Defaults.CornerLargeIncreased,
        // The whole card counts the taps, silently: nothing about it says there is anything to find.
        // A touch gesture alone, so neither a remote nor a screen reader stops on it on the way to the update
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures {
                versionTaps++
                when {
                    versionTaps >= PILL_TAP_COUNT -> {
                        versionTaps = 0
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        currentOnPillsFound()
                    }
                    // The halfway point is where the taps start answering back,
                    // enough of a hint to keep going without naming what is coming
                    versionTaps > PILL_TAP_COUNT / 2 ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(Defaults.SpaceLarge),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
            ) {
                Text(
                    text = stringResource(R.string.version),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (PreferencesManager.isDevVersion()) {
                    StatusBadge(
                        text = stringResource(R.string.changelog_badge_prerelease),
                        tone = SemanticTone.Warning
                    )
                }
            }

            Text(
                text = BuildConfig.VERSION_NAME.isolateLtr(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Nothing stands here before a check or after one that failed: neither says the
            // manager is up to date
            if (updateCheck is AppUpdateCheck.UpToDate) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.about_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (updateCheck is AppUpdateCheck.Available) {
                val updateVersion = updateCheck.asset.version
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
                ) {
                    ThemedIcon(icon = MaterialSymbols.Rounded.Download, size = Defaults.IconSizeSmall)
                    Text(
                        text = stringResource(R.string.about_update_available, updateVersion.isolateLtr()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                        text = stringResource(R.string.update),
                        tone = SemanticTone.Primary,
                        onClick = onUpdateClick
                    )
                }
            }
        }
    }
}

/** Tiles side by side, each as wide as the next. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
        content = content
    )
}

@Composable
private fun AboutTile(
    link: AboutLink,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = cardFill()
) = AboutTile(
    title = stringResource(link.title),
    icon = link.icon,
    onClick = onClick,
    modifier = modifier,
    color = color
)

/**
 * One thing the About dialog leads to, a link or a list of people, named in words so that no tile
 * has to be guessed from its icon.
 */
@Composable
private fun AboutTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = cardFill()
) {
    SurfaceCard(
        onClick = onClick,
        color = color,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Defaults.TallTouchTarget)
                .padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
        ) {
            ThemedIcon(icon = icon)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                // A second line for the languages a name runs long in, rather than cutting it short
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
