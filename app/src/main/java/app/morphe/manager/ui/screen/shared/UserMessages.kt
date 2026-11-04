/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.morphe.manager.ManagerApplication
import app.morphe.manager.R
import app.morphe.manager.util.AppCoroutineScope
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Short messages for the user, shown as a snackbar on whichever surface is on top, see
 * [MessageHost]. A screen or the work behind it raises one without holding a [Context], as a
 * string resource is resolved by the surface that shows it.
 *
 * The messages are played here, one at a time and on a clock of their own, while the surfaces only
 * draw the one playing. A surface opening or closing therefore moves the message that is up to
 * another surface without starting it over, and one raised before any surface is up waits for the
 * first.
 */
class UserMessages(
    context: Context,
    scope: AppCoroutineScope
) {
    private val appContext = context.applicationContext
    private val incoming = Channel<Message>(Channel.UNLIMITED)
    private val surfaces = MutableStateFlow<List<MessageSurface>>(emptyList())
    private val playing = MutableStateFlow<Playing?>(null)

    init {
        scope.launch {
            for (message in incoming) play(message)
        }
    }

    fun show(text: String, long: Boolean = false, action: MessageAction? = null) =
        post(Message({ text }, long, action))

    fun show(@StringRes text: Int, vararg args: Any?, long: Boolean = false, action: MessageAction? = null) =
        post(Message({ it.getString(text, *args) }, long, action))

    /**
     * Shows [text] only while a Morphe screen is in focus, for work that also runs without one: an
     * update check woken by an FCM push would otherwise leave it waiting for the app to come back.
     */
    fun showIfInForeground(@StringRes text: Int, vararg args: Any?) {
        if (ManagerApplication.isInForeground) show(text, *args)
    }

    /** Shows [text] until the returned handle takes it down, for work whose end is not known up front. */
    fun showUntilDismissed(@StringRes text: Int): MessageHandle {
        val closed = CompletableDeferred<Unit>()
        post(Message({ it.getString(text) }, long = true, closed = closed, untilClosed = true))
        return MessageHandle(closed)
    }

    /** [action], confirming itself with [done], the feedback every selection gives. */
    fun confirming(done: String, action: () -> Unit): () -> Unit = {
        show(done)
        action()
    }

    private fun post(message: Message) {
        incoming.trySend(message)
    }

    private suspend fun play(message: Message) {
        if (message.closed.isCompleted) return
        val first = surfaces.first { it.isNotEmpty() }.last()
        playing.value = Playing(message, cameOn = first)
        val timeout = message.timeout()
        if (timeout == null) message.closed.await() else withTimeoutOrNull(timeout) { message.closed.await() }
        // Over, so a press landing on it as it fades no longer acts
        message.closed.complete(Unit)
        playing.value = null
    }

    /** How long [this] stays up, or null for one that stays until it is closed. */
    private fun Message.timeout(): Duration? {
        if (untilClosed) return null
        val base = if (long) LongMessageDuration else ShortMessageDuration
        val accessibility = appContext.getSystemService(AccessibilityManager::class.java)
        return readableTimeout(base, accessibility, hasAction = action != null)
    }

    /** The message playing, while [surface] is the one on top, and nothing while another is. */
    internal fun playingOn(surface: MessageSurface): Flow<Message?> =
        combine(playing, surfaces) { up, open -> up?.message?.takeIf { open.lastOrNull() === surface } }

    /** The message up, on whichever surface, as it changes. */
    internal val current: Flow<Message?> = playing.map { it?.message }

    /** The message up right now, on whichever surface. */
    internal val currentNow: Message?
        get() = playing.value?.message

    /** Whether [message] is still up, on whichever surface. */
    internal fun isPlaying(message: Message): Boolean = currentNow === message

    /**
     * Whether [message] comes up on [surface] as it starts, rather than handed over from another
     * surface it was showing on.
     */
    internal fun startsOn(message: Message, surface: MessageSurface): Boolean =
        playing.value.let { up -> up != null && up.message === message && up.cameOn === surface }

    internal fun attach(surface: MessageSurface) {
        surfaces.update { it + surface }
        handOver()
    }

    internal fun detach(surface: MessageSurface) {
        surfaces.update { it - surface }
        handOver()
    }

    // A message moving to another surface has come up already, so it carries on there as it is
    private fun handOver() {
        val top = surfaces.value.lastOrNull()
        playing.update { up -> up?.takeIf { it.cameOn === top } ?: up?.copy(cameOn = null) }
    }

    /** Takes [message] down, as the user swiping or closing it does. */
    internal fun dismiss(message: Message) {
        message.closed.complete(Unit)
    }

    /** Runs the action on [message], which takes it down. Once, however many times it is pressed. */
    internal fun perform(message: Message) {
        if (message.closed.complete(Unit)) message.action?.onClick?.invoke()
    }
}

/** A button on a message, such as one undoing what the message reports. */
class MessageAction(@param:StringRes val label: Int, val onClick: () -> Unit)

/** How long a short message stays up, as long as Material keeps its own. */
private val ShortMessageDuration = 4.seconds

/** How long a long message stays up, unless accessibility settings ask for longer. */
private val LongMessageDuration = 6.seconds

/**
 * [base], stretched to what the user's accessibility settings recommend for a message that holds
 * text and, with [hasAction], a control, as Material stretches its own.
 */
private fun readableTimeout(base: Duration, accessibility: AccessibilityManager?, hasAction: Boolean): Duration {
    if (accessibility == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return base
    return accessibility.getRecommendedTimeoutMillis(
        base.inWholeMilliseconds.toInt(),
        if (hasAction) {
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or
                    AccessibilityManager.FLAG_CONTENT_CONTROLS
        } else {
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS
        }
    ).milliseconds
}

/** Takes down a message shown by [UserMessages.showUntilDismissed], wherever it has got to. */
class MessageHandle internal constructor(private val closed: CompletableDeferred<Unit>) {
    fun dismiss() {
        closed.complete(Unit)
    }
}

internal class Message(
    val text: (Context) -> String,
    val long: Boolean,
    val action: MessageAction? = null,
    /** Completed when the message is over, however it ended. */
    val closed: CompletableDeferred<Unit> = CompletableDeferred(),
    /** Whether it stays until [closed], rather than for a time. */
    val untilClosed: Boolean = false
)

/** A place messages are drawn, which is the top one of those open. */
internal class MessageSurface

/** The message up, and the surface it came up on while it is still the one showing it. */
private data class Playing(val message: Message, val cameOn: MessageSurface?)

/** How far a message floats above the navigation bar or the keyboard, clear of the screen's bottom edge. */
private val MessageBottomInset = 64.dp

/**
 * [content] with the app's messages shown over its bottom edge while it is the top surface. The
 * snackbar keeps one place on every surface, just clear of the navigation bar or the keyboard.
 * It sits over whatever bar the surface has there rather than moving with the bars of each, and
 * is only as wide as its text needs.
 *
 * A message only fades as it comes up and as it goes. Moving between surfaces it stays as it is,
 * so it stands still while the screen under it changes.
 *
 * @param accepting Whether the surface still takes messages. One on its way out gives them up at
 * once, so the surface below carries on with the message that is up. It goes on showing the one it
 * had until it is gone, rather than leaving a hole in it as it fades.
 */
@Composable
fun MessageHost(
    modifier: Modifier = Modifier,
    accepting: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val messages = koinInject<UserMessages>()
    val surface = remember { MessageSurface() }
    if (accepting) {
        DisposableEffect(messages, surface) {
            messages.attach(surface)
            onDispose { messages.detach(surface) }
        }
    }
    // Opening on top of the others, it shows the message up from its first frame, before it has
    // taken its place among them and that reaches it, so its window never covers it for a frame
    val playing by remember(messages, surface) { messages.playingOn(surface) }
        .collectAsState(initial = if (accepting) messages.currentNow else null)
    val current by messages.current.collectAsState(initial = messages.currentNow)
    val held = remember { HeldMessage() }
    if (accepting) held.message = playing
    val shown = if (accepting) playing else held.message?.takeIf { it === current }

    Box(modifier = modifier) {
        content()
        AnimatedContent(
            targetState = shown,
            // Faded only, as the room it takes growing up from the bottom edge would read as a slide
            transitionSpec = {
                val enter = targetState?.takeIf { messages.startsOn(it, surface) }
                    ?.let { fadeIn(tween(MessageFadeDuration)) } ?: EnterTransition.None
                val exit = initialState?.takeUnless(messages::isPlaying)
                    ?.let { fadeOut(tween(MessageFadeDuration)) } ?: ExitTransition.None
                (enter togetherWith exit).using(null)
            },
            contentAlignment = Alignment.BottomCenter,
            label = "message",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .widthIn(max = Defaults.ContentMaxWidth)
                .padding(
                    start = Defaults.SpaceLarge,
                    top = Defaults.SpaceSmall,
                    end = Defaults.SpaceLarge,
                    bottom = MessageBottomInset
                )
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) { message ->
            if (message != null) {
                key(message) { MessageBubble(message, messages) }
            }
        }
    }
}

/**
 * [message] as a pill as wide as what it holds, in the colors Material gives a snackbar. Drawn here
 * rather than as a Material [androidx.compose.material3.Snackbar], which always takes the whole
 * width it is offered.
 */
@Composable
private fun MessageBubble(message: Message, messages: UserMessages) {
    val hasControls = message.action != null || message.long
    // Swiped off either side, as a message on a phone can be, the button staying for whoever does
    // not swipe
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = {},
        onDismiss = { messages.dismiss(message) }
    ) {
        Surface(
            // Rounded off whole, as the pill floating over the screens is
            shape = CircleShape,
            color = SnackbarDefaults.color,
            contentColor = SnackbarDefaults.contentColor
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = MessageMinHeight)
                    .padding(
                        start = Defaults.SpaceLarge,
                        // The buttons bring their own room, as they do in a Material snackbar
                        end = if (hasControls) Defaults.SpaceExtraSmall else Defaults.SpaceLarge
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.text(LocalContext.current),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = Defaults.SpaceMedium)
                )
                message.action?.let { action ->
                    TextButton(
                        onClick = { messages.perform(message) },
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
                    ) { Text(stringResource(action.label)) }
                }
                // One that stays up a while can be closed once read, a short one goes before a
                // close button would be reached
                if (message.long) {
                    TooltipIconButton(
                        onClick = { messages.dismiss(message) },
                        icon = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = SnackbarDefaults.dismissActionContentColor
                    )
                }
            }
        }
    }
}

/** The message a [MessageHost] showed while it took them, held as it goes. */
private class HeldMessage(var message: Message? = null)

private const val MessageFadeDuration = 200

/** The height Material gives a snackbar, kept by the pill drawn in its place. */
private val MessageMinHeight = 48.dp
