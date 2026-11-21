/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.games

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.morphe.manager.R
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.AppCoroutineScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Pause
import com.composables.icons.materialsymbols.rounded.Play_arrow
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Trophy
import org.koin.compose.koinInject

// Cards keep a readable width and the column count follows the space available, so the
// picker stays two-up on a phone and fills the row on a tablet or in landscape
private val GamePickerMinCardWidth = 150.dp
private val GamePickerIconTileSize = 44.dp
private val GamePickerIconTileShape = RoundedCornerShape(Defaults.CornerMedium)

/**
 * The mini-games, a page of their own opened from the settings: a picker of [MiniGame.entries],
 * and the game picked from it in its place, which the way back leaves for the picker again.
 *
 * @param onBack Leaves the page for the one it was opened from.
 */
@Composable
fun MiniGamesPage(
    onBack: () -> Unit,
    prefs: PreferencesManager = koinInject(),
    appScope: AppCoroutineScope = koinInject()
) {
    val state = remember { MiniGameState(prefs, appScope) }
    val selected = state.selectedGame
    val gridState = rememberLazyGridState()
    // A game holds still, so only the picker's scroll lays the header's band down
    val gameScrollState = rememberScrollState()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Registered inside the page so it outranks the way back out of it
    BackHandler(enabled = selected != null) { state.closeGame() }
    // Waits behind its pause overlay while the manager is away, rather than playing on the
    // moment it is back, before the player has their eyes on it again
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.pauseActiveGame() }
    // However the page is left, the round on screen keeps the record it set
    DisposableEffect(state) {
        onDispose { state.saveRecord() }
    }

    PinnedHeaderPage(
        header = {
            PageHeader(
                // The game's own name while one is open, which keeps the header as tall as the
                // picker's and the field from shifting under it
                title = stringResource(selected?.titleRes ?: R.string.mini_games),
                onBack = { if (selected != null) state.closeGame() else onBack() }
            )
        },
        scrollState = if (selected == null) gridState else gameScrollState
    ) { insets ->
        val contentPadding = PaddingValues(
            start = insets.horizontal,
            top = insets.top + Defaults.SpaceMedium,
            end = insets.horizontal,
            bottom = navigationBar + Defaults.SpaceLarge
        )

        AnimatedContent(
            targetState = selected,
            transitionSpec = Animations.fadeCrossfade(200),
            modifier = Modifier.fillMaxSize(),
            label = "mini_game"
        ) { game ->
            if (game == null) {
                GamePicker(
                    gridState = gridState,
                    contentPadding = contentPadding,
                    onSelect = state::selectGame
                )
            } else {
                GameView(
                    game = game,
                    state = state.stateOf(game),
                    canvas = { GameCanvasSlot(selected = game, state = state) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                )
            }
        }
    }
}

/** The games to pick from, in a grid running on under the page's header. */
@Composable
private fun GamePicker(
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onSelect: (MiniGame) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GamePickerMinCardWidth),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollFade(gridState),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        items(MiniGame.entries, key = { it.name }) { game ->
            GamePickerCard(
                game = game,
                onClick = { onSelect(game) }
            )
        }
    }
}

@Composable
private fun GamePickerCard(
    game: MiniGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Falls back to the theme's accent where the appearance settings keep colors off cards
    val accent = usableAppAccent(game.accent) ?: MaterialTheme.colorScheme.primary

    SurfaceCard(
        onClick = onClick,
        cornerRadius = Defaults.CornerMedium,
        showBorder = true,
        borderColor = appAccentBorder(game.accent),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Defaults.SpaceMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(GamePickerIconTileSize)
                    .background(accent.copy(alpha = AccentAlpha.STEP), GamePickerIconTileShape),
                contentAlignment = Alignment.Center
            ) {
                ThemedIcon(icon = game.icon, tint = accent)
            }
            Spacer(Modifier.height(Defaults.SpaceSmall))
            Text(
                text = stringResource(game.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Defaults.SpaceExtraSmall))
            GamePickerSubtitle(text = stringResource(game.subtitleRes))
        }
    }
}

/**
 * A picker card's subtitle, given as many lines as the longest of all the subtitles takes at this
 * width, so every card in the grid comes out as tall as the others in any language or window.
 */
@Composable
private fun GamePickerSubtitle(text: String) {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val subtitles = MiniGame.entries.map { stringResource(it.subtitleRes) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val lines = remember(subtitles, style, width) {
            subtitles.maxOf { subtitle ->
                measurer.measure(
                    text = subtitle,
                    style = style.copy(textAlign = TextAlign.Center),
                    constraints = Constraints(maxWidth = width)
                ).lineCount
            }
        }
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = lines,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * [game] in play: its score board above its [canvas], which keeps the field's aspect ratio in
 * whatever room is left, with the overlays of a lost or paused round over it, and the controls of
 * the round below, in reach of the thumb.
 */
@Composable
private fun GameView(
    game: MiniGame,
    state: MiniGameStateBase,
    canvas: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
    ) {
        GameScoreBoard(
            points = state.points,
            stats = state.stats,
            accent = usableAppAccent(game.accent) ?: MaterialTheme.colorScheme.primary
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val width = minOf(maxWidth, maxHeight * game.aspectRatio)
            Box(
                modifier = Modifier
                    .size(width, width / game.aspectRatio)
                    .align(Alignment.Center)
                    // The canvases round their own corners, and the overlays drawn over them
                    // have to stop at the same edge
                    .clip(RoundedCornerShape(Defaults.CornerMedium))
            ) {
                canvas()

                GameHapticsEffect(state)

                if (state.isGameOver) {
                    GameOverlay(
                        title = stringResource(R.string.mini_game_game_over),
                        action = CardAction(
                            icon = MaterialSymbols.Rounded.Refresh,
                            label = stringResource(R.string.mini_game_try_again),
                            onClick = state::restart
                        ),
                        modifier = Modifier.matchParentSize()
                    ) {
                        GameOverScore(points = state.points)
                    }
                }
                if (state.isPaused) {
                    GameOverlay(
                        title = stringResource(R.string.mini_game_paused),
                        action = CardAction(
                            icon = MaterialSymbols.Rounded.Play_arrow,
                            label = stringResource(R.string.mini_game_resume),
                            onClick = state::resume
                        ),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }

        ActionPillRow {
            // A turn-based game waits for the player anyway, there is nothing for a pause to hold
            if (game.realTime) {
                val pause = stringResource(R.string.mini_game_pause)
                ActionPillButton(
                    onClick = state::pause,
                    icon = MaterialSymbols.Rounded.Pause,
                    contentDescription = pause,
                    label = pause,
                    large = true,
                    enabled = state.isPlaying
                )
            }
            val restart = stringResource(R.string.mini_game_restart)
            ActionPillButton(
                onClick = state::restart,
                icon = MaterialSymbols.Rounded.Refresh,
                contentDescription = restart,
                label = restart,
                large = true
            )
        }
    }
}

/**
 * The round's score, the record it is going for and what else the game counts, one tile each.
 * The record's tile takes on the game's [accent] once the round has beaten it, and from then on
 * climbs along with the score.
 */
@Composable
private fun GameScoreBoard(points: GameScore, stats: List<GameStat>, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall)
    ) {
        val tileModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        GameStatTile(
            label = stringResource(R.string.mini_game_score_label),
            value = points.value.toString(),
            modifier = tileModifier
        )
        GameStatTile(
            label = stringResource(R.string.mini_game_best_label),
            value = points.best.toString(),
            icon = MaterialSymbols.Rounded.Trophy,
            highlight = accent.takeIf { points.isNewRecord },
            modifier = tileModifier
        )
        stats.forEach { stat ->
            GameStatTile(
                label = stringResource(stat.labelRes),
                value = stat.of?.let { of ->
                    stringResource(R.string.mini_game_stat_of, stat.value.toString(), of.toString())
                } ?: stat.value.toString(),
                modifier = tileModifier
            )
        }
    }
}

/** One figure of a [GameScoreBoard], its [label] over its [value], tinted by [highlight] while set. */
@Composable
private fun GameStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    highlight: Color? = null
) {
    val container by animateColorAsState(
        targetValue = highlight?.copy(alpha = AccentAlpha.STEP) ?: neutralVeil(),
        label = "game_stat_container"
    )
    val labelColor by animateColorAsState(
        targetValue = highlight ?: MaterialTheme.colorScheme.onSurfaceVariant,
        label = "game_stat_label"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Defaults.CornerMedium),
        color = container
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceSmall),
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceExtraSmall)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.marquee()
                )
            }
            // Scores run to five digits, which a tile among four on a phone fits only smaller
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = MaterialTheme.typography.titleLarge.fontSize
                )
            )
        }
    }
}

@Composable
private fun GameCanvasSlot(selected: MiniGame, state: MiniGameState) {
    when (selected) {
        MiniGame.GAME_2048 -> Game2048Board(state = state.game2048)
        MiniGame.FLAPPY -> FlappyBirdGame(state = state.flappy)
        MiniGame.SNAKE -> SnakeGame(state = state.snake)
        MiniGame.DINO -> DinoGame(state = state.dino)
        MiniGame.BLOCKS -> BlocksGame(state = state.blocks)
        MiniGame.BRICKS -> BricksGame(state = state.bricks)
        MiniGame.MINER -> MinerGame(state = state.miner)
        MiniGame.PAIRS -> PairsGame(state = state.pairs)
    }
}

/** Veil over the field of a round that stopped, saying why under [title] and how to go on by [action]. */
@Composable
private fun GameOverlay(
    title: String,
    action: CardAction,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            details()
            CardActionRow(actions = listOf(action))
        }
    }
}

/** The score a round ended on, then the record it set or fell short of. */
@Composable
private fun GameOverScore(points: GameScore) {
    Text(
        text = stringResource(R.string.mini_game_score, points.value.toString()),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (points.isNewRecord) {
        Text(
            text = stringResource(R.string.mini_game_new_record),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFEDC22E)
        )
    } else {
        Text(
            text = stringResource(R.string.mini_game_best, points.high.toString()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Plays the haptics of the game on screen. Its own moments go through the view, so they follow
 * the system touch feedback setting like any other tap, and a lost round ends on a double buzz.
 */
@Composable
private fun GameHapticsEffect(state: MiniGameStateBase) {
    val view = LocalView.current
    LaunchedEffect(state) {
        state.haptics.events.collect { haptic ->
            view.performHapticFeedback(
                when (haptic) {
                    GameHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                    GameHaptic.Reward ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                        else HapticFeedbackConstants.CONTEXT_CLICK
                }
            )
        }
    }

    GameOverHaptic(isGameOver = { state.isGameOver })
}

/** Fires a double-buzz haptic pattern once when [isGameOver] transitions to `true`. */
@Composable
private fun GameOverHaptic(isGameOver: () -> Boolean) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        var seenFalse = false
        snapshotFlow(isGameOver).collect { current ->
            if (!current) {
                seenFalse = true
            } else if (seenFalse) {
                val vibrator = context.getSystemService(Vibrator::class.java) ?: return@collect
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
            }
        }
    }
}
