/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.patcher.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Grid4x4
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.ui.screen.shared.GradientCircleIcon
import app.morphe.manager.ui.screen.shared.MorpheCard
import app.morphe.manager.ui.screen.shared.MorpheDefaults
import app.morphe.manager.ui.screen.shared.SectionTitle

enum class MiniGame {
    GAME_2048,
    FLAPPY
}

/**
 * Holds the state for every available mini-game.
 * Add new game states here as new games are introduced.
 */
@Stable
class MiniGameState {
    val game2048 = Game2048State()
    val flappy = FlappyGameState()
    var selectedGame by mutableStateOf<MiniGame?>(null)

    fun selectGame(game: MiniGame) {
        if (selectedGame != game) {
            when (selectedGame) {
                MiniGame.GAME_2048 -> game2048.restart()
                MiniGame.FLAPPY -> flappy.restart()
                null -> {}
            }
        }
        selectedGame = game
    }
}

/**
 * Reusable chip used in the header row of every mini-game.
 */
@Composable
internal fun GameChip(
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding)) { content() }
        }
    } else {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = verticalPadding)) { content() }
        }
    }
}

/**
 * Game selection screen shown when no game is active yet.
 * [onClose] is optional; pass null when the caller's navigation already provides
 * a way to dismiss (e.g. Expert mode, clicking the Logs tab suffices).
 */
@Composable
internal fun GamePickerContent(
    onSelect: (MiniGame) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(text = stringResource(R.string.mini_game_picker_title), icon = Icons.Outlined.SportsEsports)
            if (onClose != null) {
                GameChip(onClick = onClose) {
                    Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GamePickerGridCard(
                icon = Icons.Outlined.Grid4x4,
                title = stringResource(R.string.mini_game_2048),
                subtitle = stringResource(R.string.mini_game_2048_picker_subtitle),
                onClick = { onSelect(MiniGame.GAME_2048) },
                modifier = Modifier.weight(1f)
            )
            GamePickerGridCard(
                icon = Icons.Outlined.Air,
                title = stringResource(R.string.mini_game_flappy),
                subtitle = stringResource(R.string.mini_game_flappy_picker_subtitle),
                onClick = { onSelect(MiniGame.FLAPPY) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun GamePickerGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MorpheCard(
        onClick = onClick,
        cornerRadius = MorpheDefaults.SectionCornerRadius,
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GradientCircleIcon(icon = icon, size = 44.dp, iconSize = 24.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Unified slot that shows either the game picker or the active game.
 * Handles all picker/game switching internally so callers only need to pass state.
 *
 * [onBackToHost] adds a BarChart chip that exits the game slot entirely (Simple mode only).
 * Pass null in Expert mode where the Logs tab serves that purpose.
 */
@Composable
internal fun MiniGameContent(
    state: MiniGameState,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    gameContentPadding: Dp = 16.dp,
    onBackToHost: (() -> Unit)? = null
) {
    when (val selected = state.selectedGame) {
        null -> GamePickerContent(
            onSelect = { state.selectGame(it) },
            modifier = modifier,
            onClose = onBackToHost
        )
        else -> {
            val extraActions: @Composable () -> Unit = {
                GameChip(onClick = { state.selectedGame = null }) {
                    Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                if (onBackToHost != null) {
                    GameChip(onClick = onBackToHost) {
                        Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
            when (selected) {
                MiniGame.GAME_2048 -> Game2048Board(
                    state = state.game2048,
                    modifier = modifier.padding(gameContentPadding),
                    progress = progress,
                    extraActions = extraActions
                )
                MiniGame.FLAPPY -> FlappyBirdGame(
                    state = state.flappy,
                    modifier = modifier.padding(gameContentPadding),
                    progress = progress,
                    extraActions = extraActions
                )
            }
        }
    }
}

@Composable
internal fun GameOverOverlay(score: Int, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.mini_game_game_over),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.mini_game_score, score),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onRestart) {
                Text(stringResource(R.string.mini_game_try_again))
            }
        }
    }
}
