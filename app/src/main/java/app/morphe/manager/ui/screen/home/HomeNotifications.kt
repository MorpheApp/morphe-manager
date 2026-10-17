/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.morphe.manager.R
import app.morphe.manager.domain.batch.BatchRunState
import app.morphe.manager.domain.batch.SavedBatchQueue
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.BundleUpdateStatus
import app.morphe.manager.util.formatMegabytes
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Visibility flag paired with the tap callback of a single alert in the strip. */
@Immutable
data class AlertState(val visible: Boolean, val onShow: () -> Unit)

/**
 * The apps whose patches have moved on, and the tap that queues them. Shown from one app up,
 * which is also when the cards themselves start showing their update badge.
 */
@Immutable
data class RepatchAlertState(val count: Int, val visible: Boolean, val onShow: () -> Unit)

/**
 * The batch queue while it waits to be started, patches or holds results nobody closed yet, and
 * the tap back to it. [saved] is the queue being prepared, which outlives the process.
 */
@Immutable
data class BatchQueueAlertState(val run: BatchRunState?, val saved: SavedBatchQueue?, val onShow: () -> Unit)

/** Transient state driving the bundle update alert, from its progress to how it ended. */
@Immutable
data class BundleUpdateState(
    val visible: Boolean,
    val status: BundleUpdateStatus,
    val progress: PatchBundleRepository.BundleUpdateProgress?
)

/** Aggregate of all notification-strip inputs, grouped by alert. */
@Immutable
data class HomeNotificationsUi(
    val managerUpdate: AlertState,
    val outdatedManager: AlertState,
    val heldBackSources: AlertState,
    val blockedSources: AlertState,
    val metadataErrors: AlertState,
    val meteredSkipped: AlertState,
    val repatchAvailable: RepatchAlertState,
    val batchQueue: BatchQueueAlertState,
    val bundleUpdate: BundleUpdateState
)

/**
 * One alert of the notification strip.
 *
 * @param snoozable Whether a swipe may put it away. Alerts that are the only way back to running
 *   work, or that block patching outright, stay put.
 * @param revision Changes when the alert has something new to say, which brings a snoozed one back.
 * @param passing Whether it only reports on something under way, which a swipe puts away for good
 *   rather than behind the bell, since brought back it would have nothing left to say.
 */
private class HomeAlert(
    val key: String,
    val visible: Boolean,
    val snoozable: Boolean = true,
    val revision: String = "",
    val passing: Boolean = false,
    val card: @Composable () -> Unit
)

/**
 * The alerts of the main screen, over the top of its pages.
 *
 * Alerts stack behind the most pressing one, so however many are raised they cover no more of the
 * screen than one. A swipe snoozes an alert for the session, and the bell chip brings it back.
 *
 * The whole stack is laid out from one model: each card's height, how far it has come in, how far
 * it is swiped aside and how far the stack is unfolded. Every change moves the cards from wherever
 * they are, so a swipe, an alert coming or going and the stack folding all blend into each other.
 *
 * @param showAlerts False on a page the alerts do not lead from, which shows only the progress of
 *   work under way. Their snoozes are kept for when they show again.
 */
@Composable
fun NotificationsOverlay(
    notifications: HomeNotificationsUi,
    modifier: Modifier = Modifier,
    showAlerts: Boolean = true
) {
    val alerts = homeAlerts(notifications)
    // Revision each alert was snoozed at, kept across navigation and rotation
    val snoozed = rememberSaveable(saver = SnoozeSaver) { mutableStateMapOf() }
    // Dragged open and shut by the finger, and eased there by the chips, so either way the stack
    // follows one state. Whether it stays open is kept across navigation and rotation
    val fold = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
        AnchoredDraggableState(StackFold.Folded)
    }
    val unfolding = fold.targetValue == StackFold.Unfolded
    val scope = rememberCoroutineScope()
    fun foldTo(value: StackFold) {
        scope.launch { fold.animateTo(value, Animations.defaultTween()) }
    }

    // An alert that goes away forgets its snooze, so raised again it is shown again
    val hiddenKeys = alerts.filterNot { it.visible }.map { it.key }
    LaunchedEffect(hiddenKeys) { hiddenKeys.forEach(snoozed::remove) }

    fun HomeAlert.isSnoozed() = snoozable && snoozed[key] == revision
    val active = alerts.filter { it.visible && !it.isSnoozed() && (showAlerts || it.passing) }
    val activeKeys = active.mapTo(HashSet()) { it.key }
    val snoozedCount = if (showAlerts) alerts.count { it.visible && !it.passing && it.isSnoozed() } else 0
    val passingKeys = alerts.filter { it.passing }.mapTo(HashSet()) { it.key }
    val stacked = active.size - 1

    LaunchedEffect(stacked) { if (stacked <= 0) fold.animateTo(StackFold.Folded, Animations.defaultTween()) }

    val stackCards = remember { StackCards() }
    val cards = stackCards.update(alerts, activeKeys)

    Layout(
        contents = listOf(
            {
                // Keyed by the card itself, so one raised again after it has gone starts afresh
                cards.forEach { card ->
                    key(card) {
                        StackCardSlot(
                            card = card,
                            raised = card.key in activeKeys,
                            onSwiped = { snoozed[card.key] = card.alert.revision }
                        )
                    }
                }
            },
            {
                // Over the cards peeking out under the top one, so a tap there unfolds the stack
                // rather than opening whichever card it lands on. Left out of what a screen reader
                // goes through, which unfolds the stack from the chip offering the same
                Box(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .clickable(interactionSource = null, indication = null) {
                            foldTo(StackFold.Unfolded)
                        }
                )
            },
            {
                AlertStackControls(
                    stacked = stacked,
                    expanded = unfolding,
                    snoozedCount = snoozedCount,
                    onToggleExpanded = { foldTo(if (unfolding) StackFold.Folded else StackFold.Unfolded) },
                    onRestoreSnoozed = { snoozed.keys.retainAll(passingKeys) }
                )
            }
        ),
        modifier = modifier
            .clipAbove()
            // A drag down the strip unfolds the stack under the finger and one up folds it back,
            // settling on whichever end it got more than halfway to
            .anchoredDraggable(
                state = fold,
                orientation = Orientation.Vertical,
                enabled = stacked > 0,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state = fold,
                    animationSpec = Animations.defaultTween()
                )
            )
    ) { (cardMeasurables, peekMeasurables, controlMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val gap = AlertSpacing.roundToPx()
        val cardPlaceables = cardMeasurables.map { it.measure(loose) }
        val controls = controlMeasurables.map { it.measure(loose) }

        // The stack unfolds by as much as the raised cards past the top one take up at rest
        val restHeights = cards.indices.filter { cards[it].key in activeKeys }.map { cardPlaceables[it].height + gap }
        val unfoldDistance = (restHeights.sum() - (restHeights.firstOrNull() ?: 0)).toFloat()
        if (fold.anchors.positionOf(StackFold.Unfolded) != unfoldDistance) {
            fold.updateAnchors(
                DraggableAnchors {
                    StackFold.Folded at 0f
                    StackFold.Unfolded at unfoldDistance
                }
            )
        }
        val unfolded = if (unfoldDistance > 0f) {
            ((fold.offset.takeUnless { it.isNaN() } ?: 0f) / unfoldDistance).coerceIn(0f, 1f)
        } else 0f

        val held = FloatArray(cards.size)
        val depths = FloatArray(cards.size)
        val unfoldedTops = FloatArray(cards.size)
        // How far the cards before this one have cleared off it while folded, all of it for the top
        var clear = 1f
        // How many cards are held over this one, which sets how far down the folded stack it sits
        var depth = 0f
        var foldedHeight = 0f
        var unfoldedHeight = 0f
        cards.forEachIndexed { index, card ->
            // Each card carries the gap under it, so the gap comes and goes with it
            val height = cardPlaceables[index].height + gap
            // A card swiped aside hands its room over to the next one as it goes
            held[index] = card.presence.value * (1f - card.swiped)
            depths[index] = depth
            unfoldedTops[index] = unfoldedHeight
            foldedHeight += clear * held[index] * height
            unfoldedHeight += held[index] * height
            clear *= 1f - held[index]
            depth += held[index]
        }

        // Folded, the cards behind the top one peek out under it, each a step lower and narrower,
        // and those past the last step wait out of sight behind it
        val step = StackPeekStep.toPx()
        val inset = StackPeekInset.toPx()
        val sidePadding = Defaults.SpaceLarge.toPx()
        val leadBottom = foldedHeight - gap
        val peek = step * (depth - 1f).coerceIn(0f, StackPeekCount.toFloat()) * (1f - unfolded)
        val tops = IntArray(cards.size)
        val scales = FloatArray(cards.size)
        val alphas = FloatArray(cards.size)
        cards.forEachIndexed { index, card ->
            val placeable = cardPlaceables[index]
            val presence = card.presence.value
            val level = depths[index].coerceAtMost(StackPeekCount.toFloat())
            // The top card keeps to the top, swiped aside or not, while those under it hang off the
            // bottom of whatever leads the stack
            val foldedTop = if (depths[index] == 0f) 0f else leadBottom + level * step - placeable.height
            // Coming in and going out it slides from and to behind whatever is above it
            val shift = -(1f - presence) * (placeable.height + gap)
            tops[index] = (lerp(foldedTop, unfoldedTops[index], unfolded) + shift).roundToInt()
            val narrowed = 1f - 2f * inset * level / (placeable.width - 2f * sidePadding).coerceAtLeast(1f)
            scales[index] = lerp(narrowed, 1f, unfolded)
            val folded = (StackPeekCount + 1f - depths[index]).coerceIn(0f, 1f)
            alphas[index] = presence * lerp(folded, 1f, unfolded)
        }
        val cardsHeight = lerp(foldedHeight, unfoldedHeight, unfolded).roundToInt()
        val peekHeight = peek.roundToInt()
        val peekTargets = peekMeasurables.map { it.measure(Constraints.fixed(constraints.maxWidth, peekHeight)) }

        // Hung half over the edge of the last card, so the chips cover as little as possible of
        // the screen under the strip. They ride that edge up as the last card leaves and stop at
        // the top, where with every alert snoozed the bell is all that is left of the strip
        val controlsHeight = controls.maxOfOrNull { it.height } ?: 0
        val controlsBase = cardsHeight + (peekHeight - gap).coerceAtLeast(0)
        val controlsTop = (controlsBase - controlsHeight / 2 - gap).coerceAtLeast(0)
        val height = controlsTop + controlsHeight + gap

        layout(constraints.maxWidth, height.coerceAtLeast(0)) {
            cardPlaceables.forEachIndexed { index, placeable ->
                // A card nobody can see is left out, so it does not take taps meant for the one over it
                if (alphas[index] <= 0f) return@forEachIndexed
                val card = cards[index]
                // Earlier cards are drawn over later ones, which slide in and out behind them. The
                // swipe offset runs along the layout direction, so it is mirrored with it in RTL
                placeable.placeRelativeWithLayer(card.offset.roundToInt(), tops[index], zIndex = -index.toFloat()) {
                    alpha = alphas[index]
                    scaleX = scales[index]
                    scaleY = scales[index]
                    // Narrowed about its bottom edge, which is the part left peeking out
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
            }
            // Only while cards peek out, so it takes no taps off the screen under an empty strip
            if (peekHeight > 0) peekTargets.forEach { it.place(0, leadBottom.roundToInt(), zIndex = 1f) }
            controls.forEach { it.place(0, controlsTop, zIndex = 1f) }
        }
    }
}

/** One alert as the stack draws it, kept on while it eases out once its alert is gone. */
@Stable
private class StackCard(alert: HomeAlert, presence: Float) {
    val key = alert.key
    var alert by mutableStateOf(alert)

    /** How far it has come in, from 0 while out of the stack to 1 once it is in place. */
    val presence = Animatable(presence)

    /** Where it is swiped, settling off either side or back in its place. */
    val swipe = AnchoredDraggableState(SwipeAnchor.Rest)

    /** How far it is swiped aside, in pixels either way. */
    val offset: Float get() = swipe.offset.takeUnless { it.isNaN() } ?: 0f

    /** Width it was last laid out at, which a swipe is measured against. */
    var width by mutableIntStateOf(0)

    /** Whether it has eased all the way out, after which the stack lets it go. */
    var gone by mutableStateOf(false)

    /** Share of its width it is swiped aside by. */
    val swiped: Float get() = if (width > 0) (abs(offset) / width).coerceAtMost(1f) else 0f
}

/** The cards of the stack by alert, each kept on until it has eased out after its alert is gone. */
private class StackCards {
    private val pool = HashMap<String, StackCard>()
    private var started = false

    /**
     * The cards to draw for [alerts], in their order, with [raised] the keys of the ones raised.
     * Those up when the strip first comes up are already in place rather than easing in.
     */
    fun update(alerts: List<HomeAlert>, raised: Set<String>): List<StackCard> {
        val cards = alerts.mapNotNull { alert ->
            val card = pool[alert.key]?.takeUnless { it.gone }
            when {
                alert.key in raised -> (card ?: StackCard(alert, if (started) 0f else 1f))
                    .also { it.alert = alert; pool[alert.key] = it }
                card != null -> card
                else -> null.also { pool.remove(alert.key) }
            }
        }
        started = true
        return cards
    }
}

/** Every alert of the strip, raised or not, in order of priority. */
@Composable
private fun homeAlerts(notifications: HomeNotificationsUi): List<HomeAlert> {
    val batchRun = notifications.batchQueue.run
    val batchRunning = batchRun?.isActive == true
    val batchFinished = batchRun?.hasOutcome == true
    val batchSucceeded = (batchRun?.succeeded ?: 0) > 0
    val queuedCount = notifications.batchQueue.saved?.queuedCount ?: 0
    val repatch = notifications.repatchAvailable
    val bundleUpdate = notifications.bundleUpdate

    return listOf(
        // Leads while it lasts, so the progress of a running update stays in sight. Only how it
        // ended can be swiped, and a new status brings it back
        HomeAlert(
            key = "bundle_update",
            visible = bundleUpdate.visible,
            snoozable = bundleUpdate.status != BundleUpdateStatus.Updating,
            revision = bundleUpdate.status.name,
            passing = true
        ) {
            BundleUpdateCard(status = bundleUpdate.status, progress = bundleUpdate.progress)
        },
        // Blocked source alert takes priority and cannot be dismissed while the block persists
        alert(
            key = "blocked_sources",
            visible = notifications.blockedSources.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.Block,
            title = stringResource(R.string.home_blocked_source_title),
            subtitle = stringResource(R.string.home_blocked_source_subtitle),
            onClick = notifications.blockedSources.onShow,
            snoozable = false
        ),
        // The one alert raised by something that already went wrong rather than something
        // that might, so it sits above the rest
        alert(
            key = "held_back_sources",
            visible = notifications.heldBackSources.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.home_held_back_title),
            subtitle = stringResource(R.string.home_held_back_subtitle),
            onClick = notifications.heldBackSources.onShow
        ),
        // A source built for a newer patcher stays unusable until the app itself is updated
        alert(
            key = "outdated_manager",
            visible = notifications.outdatedManager.visible,
            tone = SemanticTone.Error,
            icon = Icons.Outlined.SystemUpdate,
            title = stringResource(R.string.home_outdated_manager_title),
            subtitle = stringResource(R.string.home_outdated_manager_subtitle),
            onClick = notifications.outdatedManager.onShow
        ),
        alert(
            key = "metered_skipped",
            visible = notifications.meteredSkipped.visible,
            tone = SemanticTone.Warning,
            icon = Icons.Outlined.SignalCellularAlt,
            title = stringResource(R.string.home_metered_skipped_title),
            subtitle = stringResource(R.string.home_metered_skipped_subtitle),
            onClick = notifications.meteredSkipped.onShow
        ),
        alert(
            key = "metadata_errors",
            visible = notifications.metadataErrors.visible,
            tone = SemanticTone.Warning,
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.home_metadata_errors_title),
            subtitle = stringResource(R.string.home_metadata_errors_subtitle),
            onClick = notifications.metadataErrors.onShow
        ),
        alert(
            key = "manager_update",
            visible = notifications.managerUpdate.visible,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.Update,
            title = stringResource(R.string.home_update_available),
            subtitle = stringResource(R.string.home_update_available_subtitle),
            onClick = notifications.managerUpdate.onShow
        ),
        // Once its screen is gone this is the only way back to a running queue, which holds
        // single-app patching back meanwhile, so it cannot be swiped away
        alert(
            key = "batch_running",
            visible = batchRunning,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.AutoFixHigh,
            progress = batchRun?.let { run -> if (run.total > 0) run.processed.toFloat() / run.total else 0f },
            title = stringResource(R.string.batch_patch_title),
            subtitle = stringResource(
                R.string.batch_patch_progress_counter,
                (batchRun?.processed ?: 0).toString(),
                (batchRun?.total ?: 0).toString()
            ),
            onClick = notifications.batchQueue.onShow,
            snoozable = false
        ),
        // The patched APKs of a finished run are only installable from its summary
        alert(
            key = "batch_finished",
            visible = batchFinished,
            tone = if (batchSucceeded) SemanticTone.Success else SemanticTone.Error,
            icon = if (batchSucceeded) Icons.Outlined.Check else Icons.Outlined.ErrorOutline,
            title = stringResource(
                if (batchSucceeded) R.string.patcher_complete_title else R.string.patcher_failed_title
            ),
            subtitle = stringResource(
                R.string.batch_patch_summary,
                (batchRun?.succeeded ?: 0).toString(),
                (batchRun?.failed ?: 0).toString(),
                (batchRun?.skipped ?: 0).toString()
            ),
            onClick = notifications.batchQueue.onShow
        ),
        alert(
            key = "batch_queued",
            visible = queuedCount > 0 && !batchRunning && !batchFinished,
            tone = SemanticTone.Primary,
            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
            title = pluralStringResource(R.plurals.batch_queue_pending_count, queuedCount, queuedCount.toString()),
            subtitle = stringResource(R.string.batch_queue_pending_subtitle),
            onClick = notifications.batchQueue.onShow,
            revision = queuedCount.toString()
        ),
        // The badges on the cards say the same thing one app at a time. This says it once
        // and queues every one of them, which is the part that is otherwise several taps.
        // While a queue is patching or awaiting review, its own alert stands in for this one
        alert(
            key = "repatch_available",
            visible = repatch.visible && repatch.count > 0 && !batchRunning && !batchFinished,
            tone = SemanticTone.Primary,
            icon = Icons.Outlined.AutoFixHigh,
            title = pluralStringResource(R.plurals.repatch_available_count, repatch.count, repatch.count.toString()),
            subtitle = stringResource(R.string.home_repatch_available_subtitle),
            onClick = repatch.onShow,
            revision = repatch.count.toString()
        )
    )
}

/**
 * An alert drawn as a [StatusCard].
 *
 * @param progress Share of the work done, which puts [icon] inside a ring filling up with it, for
 *   work still in progress.
 */
private fun alert(
    key: String,
    visible: Boolean,
    tone: SemanticTone,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    snoozable: Boolean = true,
    revision: String = "",
    progress: Float? = null
) = HomeAlert(key = key, visible = visible, snoozable = snoozable, revision = revision) {
    StatusCard(
        tone = tone,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = {
            if (progress != null) {
                ProgressRing(progress = progress) { RingIcon(icon) }
            } else {
                StatusDisc(icon = icon, tone = tone)
            }
        }
    )
}

/** Chips of the strip: one folding the stack open and shut, one bringing back what was snoozed. */
@Composable
private fun AlertStackControls(
    stacked: Int,
    expanded: Boolean,
    snoozedCount: Int,
    onToggleExpanded: () -> Unit,
    onRestoreSnoozed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The chips keep their last count while they shrink away, rather than reading 0 on the way out
    val shownStacked = rememberLatest(stacked.takeIf { it > 0 }) ?: 0
    val shownSnoozed = rememberLatest(snoozedCount.takeIf { it > 0 }) ?: 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge),
        horizontalArrangement = Arrangement.End
    ) {
        AnimatedRowPill(visible = stacked > 0, gap = AlertSpacing) {
            AlertChip(
                icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                text = if (expanded) stringResource(R.string.less) else "+$shownStacked",
                onClick = onToggleExpanded
            )
        }
        AnimatedRowPill(visible = snoozedCount > 0, gap = AlertSpacing) {
            val description = pluralStringResource(
                R.plurals.home_alerts_snoozed_count,
                shownSnoozed,
                shownSnoozed.toString()
            )
            AlertChip(
                icon = Icons.Outlined.Notifications,
                text = shownSnoozed.toString(),
                onClick = onRestoreSnoozed,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
            )
        }
    }
}

/** Small pill among the strip's controls, lifted like the alerts it belongs to. */
@Composable
private fun AlertChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        border = CardBorder.of(MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = Defaults.SpaceSmall, end = Defaults.SpaceMedium, top = Defaults.SpaceSmall, bottom = Defaults.SpaceSmall),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private val AlertSpacing = 8.dp

/** Cards that peek out under the top one while the stack is folded, like a deck seen side on. */
private const val StackPeekCount = 2
private val StackPeekStep = 6.dp
private val StackPeekInset = 12.dp

/** Share of its width a card has to be swiped by to go, short of a fling. */
private val SwipeAwayThreshold: (Float) -> Float = { distance -> distance * 0.4f }

/** Where the stack can be dragged to: folded behind its top card, or unfolded into a list. */
private enum class StackFold { Folded, Unfolded }

/** Where a card can be swiped to: off either side, or back in its place. */
private enum class SwipeAnchor { Start, Rest, End }

/**
 * Cuts off whatever is drawn above the element, as a card leaving off the top of the strip would
 * be, while a card swiped aside or folding away over its bottom is still drawn in full.
 */
private fun Modifier.clipAbove() = drawWithContent {
    clipRect(left = -size.width, top = 0f, right = size.width * 2, bottom = Float.MAX_VALUE) {
        this@drawWithContent.drawContent()
    }
}

private val SnoozeSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { map -> map.flatMap { (key, revision) -> listOf(key, revision) } },
    restore = { saved ->
        mutableStateMapOf<String, String>().apply {
            saved.chunked(2).forEach { (key, revision) -> put(key, revision) }
        }
    }
)

private val BundleUpdateStatus.tone: SemanticTone
    get() = when (this) {
        BundleUpdateStatus.Updating -> SemanticTone.Neutral
        BundleUpdateStatus.Success -> SemanticTone.Success
        BundleUpdateStatus.Warning -> SemanticTone.Warning
        BundleUpdateStatus.Error -> SemanticTone.Error
    }

/** The card of the bundle update, from its progress while it runs to how it ended. */
@Composable
private fun BundleUpdateCard(
    status: BundleUpdateStatus,
    progress: PatchBundleRepository.BundleUpdateProgress?
) {
    val sourcesByUid = rememberSourcesByUid()

    val downloadFraction = progress?.bytesTotal
        ?.takeIf { it > 0L }
        ?.let { progress.bytesRead.toFloat() / it }
    val isDownloading = progress?.phase == PatchBundleRepository.BundleUpdatePhase.Downloading &&
            downloadFraction != null && downloadFraction > 0f
    val countFraction = progress
        ?.takeIf { it.total > 1 && it.completed > 0 }
        ?.let { it.completed.toFloat() / it.total }
    // Null until there is a share to show, which spins the ring rather than parking it at zero
    val ringProgress = if (isDownloading) downloadFraction else countFraction

    val title = stringResource(
        when (status) {
            BundleUpdateStatus.Updating -> R.string.home_updating_sources
            BundleUpdateStatus.Success -> R.string.home_update_success
            BundleUpdateStatus.Warning -> R.string.home_update_skipped_metered
            BundleUpdateStatus.Error -> R.string.home_update_error
        }
    )

    // One line names the source at work and how much of it has arrived, while the badge carries
    // how far along the whole update is: its share, or how many sources are done when several run
    val activeName = progress?.activeNames?.firstOrNull() ?: progress?.currentBundleName
    val arrived = progress?.takeIf { isDownloading }?.let {
        stringResource(
            R.string.manager_update_progress_size,
            formatMegabytes(it.bytesRead),
            formatMegabytes(it.bytesTotal ?: 0L)
        )
    }
    val subtitle = when (status) {
        BundleUpdateStatus.Updating -> listOfNotNull(activeName, arrived).joinToString(DETAIL_SEPARATOR)
        BundleUpdateStatus.Success -> stringResource(R.string.home_update_success_subtitle)
        BundleUpdateStatus.Warning -> stringResource(R.string.home_update_skipped_metered_subtitle)
        BundleUpdateStatus.Error -> stringResource(R.string.home_update_error_subtitle)
    }
    val badge = when {
        status != BundleUpdateStatus.Updating || progress == null -> null
        progress.total > 1 -> stringResource(R.string.home_update_bundle_count, progress.completed, progress.total)
        isDownloading -> stringResource(R.string.patcher_percentage, (downloadFraction * 100).toInt().toString())
        else -> null
    }
    val activeSource = progress?.activeUids?.firstOrNull()?.let(sourcesByUid::get)

    StatusCard(
        tone = status.tone,
        title = title,
        subtitle = subtitle,
        badge = badge,
        leading = {
            Crossfade(targetState = status, label = "bundleUpdateLeading") { shown ->
                when (shown) {
                    BundleUpdateStatus.Updating -> ProgressRing(progress = ringProgress) {
                        if (activeSource != null) {
                            BundleIcon(bundle = activeSource, modifier = Modifier.size(24.dp))
                        } else {
                            RingIcon(Icons.Outlined.Sync)
                        }
                    }
                    BundleUpdateStatus.Success -> StatusDisc(Icons.Outlined.Check, SemanticTone.Success)
                    BundleUpdateStatus.Warning -> StatusDisc(Icons.Outlined.SignalCellularAlt, SemanticTone.Warning)
                    BundleUpdateStatus.Error -> StatusDisc(Icons.Outlined.Warning, SemanticTone.Error)
                }
            }
        }
    )
}

/**
 * One card of the stack, easing in while [raised] and out once it is not. While its alert allows,
 * it can be swiped off either side, reporting it through [onSwiped] once it is fully off.
 */
@Composable
private fun StackCardSlot(card: StackCard, raised: Boolean, onSwiped: () -> Unit) {
    LaunchedEffect(raised) {
        // One brought back while still on its way out returns from wherever it got to
        if (raised) launch { card.swipe.animateTo(SwipeAnchor.Rest, Animations.defaultTween()) }
        card.presence.animateTo(if (raised) 1f else 0f, Animations.defaultTween())
        if (!raised) card.gone = true
    }

    // Reported once the card has settled off a side, not when the finger lets go of it
    val currentOnSwiped = rememberUpdatedState(onSwiped)
    LaunchedEffect(card) {
        snapshotFlow { card.swipe.settledValue }.collect { if (it != SwipeAnchor.Rest) currentOnSwiped.value() }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { size ->
                card.width = size.width
                val width = size.width.toFloat()
                card.swipe.updateAnchors(
                    DraggableAnchors {
                        SwipeAnchor.Start at -width
                        SwipeAnchor.Rest at 0f
                        SwipeAnchor.End at width
                    }
                )
            }
            .anchoredDraggable(
                state = card.swipe,
                orientation = Orientation.Horizontal,
                enabled = raised && card.alert.snoozable,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state = card.swipe,
                    positionalThreshold = SwipeAwayThreshold,
                    animationSpec = Animations.defaultTween()
                )
            )
    ) {
        card.alert.card()
    }
}

/**
 * Card of one alert in the notification strip: [leading] beside its [title] over a [subtitle],
 * with an optional [badge], and a chevron when a tap opens more. Laid on the app's own card
 * surface, lightly tinted with [tone], and lifted just enough to stand apart from what it floats over.
 */
@Composable
private fun StatusCard(
    tone: SemanticTone,
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    val neutral = tone == SemanticTone.Neutral
    val containerColor by animateColorAsState(
        targetValue = statusContainerColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = statusBorderColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusCardBorder"
    )

    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge),
        shape = RoundedCornerShape(Defaults.CornerLarge),
        color = containerColor,
        border = CardBorder.of(borderColor),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(Defaults.SpaceLarge),
            horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CrossfadeText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    // Eased from one text to the next, but keyed by what comes before its details,
                    // so the figures of a running update tick over in place
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = Animations.fadeCrossfade(200),
                        contentKey = { it.substringBefore(DETAIL_SEPARATOR) },
                        label = "statusCardSubtitle"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (badge != null) {
                StatusBadge(text = badge, tone = if (neutral) SemanticTone.Primary else tone)
            }
            if (onClick != null) {
                ForwardChevronIcon(size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Fill of a [StatusCard] in [tone]: the app's card surface, lightly tinted unless neutral. */
@Composable
private fun statusContainerColor(tone: SemanticTone): Color {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    return if (tone == SemanticTone.Neutral) surface else tone.container.copy(alpha = 0.35f).compositeOver(surface)
}

@Composable
private fun statusBorderColor(tone: SemanticTone): Color =
    if (tone == SemanticTone.Neutral) MaterialTheme.colorScheme.outlineVariant else tone.accent.copy(alpha = 0.35f)

/** Leading icon of a [StatusCard] whose work is done, on a disc in [tone]. */
@Composable
private fun StatusDisc(icon: ImageVector, tone: SemanticTone) {
    StatusCircleIcon(
        icon = icon,
        containerColor = tone.container,
        contentColor = tone.content,
        size = StatusLeadingSize
    )
}

/**
 * Leading of a [StatusCard] whose work is still running: [content] inside a ring filling with
 * [progress], or spinning while there is no share to show yet.
 */
@Composable
private fun ProgressRing(progress: Float?, content: @Composable () -> Unit) {
    // Eased, since the share arrives in steps as the download reports back
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "statusRingProgress"
    )

    Box(
        modifier = Modifier.size(StatusLeadingSize),
        contentAlignment = Alignment.Center
    ) {
        WavyProgressRing(
            progress = progress?.let { { animated } },
            wavelength = 10.dp,
            accentColor = null,
            strokeWidth = 3.5.dp,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

/** Icon inside a [ProgressRing], small enough to leave the ring clear. */
@Composable
private fun RingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val StatusLeadingSize = 44.dp

/** Joins the parts of a [StatusCard]'s subtitle, a name and the details about it. */
private const val DETAIL_SEPARATOR = " · "
