/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.morphe.manager.ManagerApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Short messages for the user, shown as a snackbar on whichever surface is on top, see
 * [MessageHost]. A screen or the work behind it raises one without holding a [Context], as a
 * string resource is resolved by the surface that shows it. One raised before any surface is up
 * waits for the first, and a surface closing with messages left hands them to the one below.
 */
class UserMessages {
    private val hosts = mutableListOf<MessageHostSlot>()
    private val waiting = mutableListOf<Message>()

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
        val dismissed = CompletableDeferred<Unit>()
        post(Message({ it.getString(text) }, long = true, dismissed = dismissed))
        return MessageHandle(dismissed)
    }

    /** [action], confirming itself with [done], the feedback every selection gives. */
    fun confirming(done: String, action: () -> Unit): () -> Unit = {
        show(done)
        action()
    }

    private fun post(message: Message) {
        synchronized(hosts) {
            val host = hosts.lastOrNull()
            if (host != null) host.post(message) else waiting += message
        }
    }

    internal fun attach(host: MessageHostSlot) {
        synchronized(hosts) {
            hosts += host
            waiting.forEach(host::post)
            waiting.clear()
        }
    }

    internal fun detach(host: MessageHostSlot) {
        synchronized(hosts) { hosts -= host }
        host.takeUnshown().forEach(::post)
    }
}

/** A button on a message, such as one undoing what the message reports. */
class MessageAction(@param:StringRes val label: Int, val onClick: () -> Unit)

/** How long a long message stays up, unless accessibility settings ask for longer. */
private val LongMessageDuration = 6.seconds

/**
 * [LongMessageDuration], stretched to what the user's accessibility settings recommend for a
 * message that holds text and, with [hasAction], a control, as Material stretches its own.
 */
private fun longMessageTimeout(accessibility: AccessibilityManager?, hasAction: Boolean): Duration {
    if (accessibility == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return LongMessageDuration
    val millis = LongMessageDuration.inWholeMilliseconds.toInt()
    return accessibility.getRecommendedTimeoutMillis(
        millis,
        if (hasAction) {
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or
                    AccessibilityManager.FLAG_CONTENT_CONTROLS
        } else {
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS
        }
    ).milliseconds
}

/** Takes down a message shown by [UserMessages.showUntilDismissed], wherever it has got to. */
class MessageHandle internal constructor(private val dismissed: CompletableDeferred<Unit>) {
    fun dismiss() {
        dismissed.complete(Unit)
    }
}

internal class Message(
    val text: (Context) -> String,
    val long: Boolean,
    val action: MessageAction? = null,
    val dismissed: CompletableDeferred<Unit>? = null
) {
    val isDismissed get() = dismissed?.isCompleted == true
}

/** A surface's place in [UserMessages], holding what it has yet to show. */
internal class MessageHostSlot {
    private val queue = Channel<Message>(Channel.UNLIMITED)
    private var current: Message? = null

    fun post(message: Message) {
        queue.trySend(message)
    }

    suspend fun showAll(state: SnackbarHostState, context: Context) = coroutineScope {
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        for (message in queue) {
            if (message.isDismissed) continue
            current = message
            // Material's long duration keeps a message up for ten seconds, longer than one takes
            // to read, so a long one is timed here and only a short one is left to Material
            val timeout = if (message.long && message.dismissed == null) {
                longMessageTimeout(accessibility, hasAction = message.action != null)
            } else null
            val shown = launch {
                val result = state.showSnackbar(
                    message = message.text(context),
                    actionLabel = message.action?.let { context.getString(it.label) },
                    // One that stays up a while can be closed once read, a short one goes before
                    // a close button would be reached
                    withDismissAction = message.long,
                    duration = if (message.long) SnackbarDuration.Indefinite else SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.onClick?.invoke()
            }
            // Cancelling the call takes the snackbar down with it
            val watcher = launch {
                message.dismissed?.await() ?: delay(timeout ?: return@launch)
                shown.cancel()
            }
            shown.join()
            watcher.cancel()
            current = null
        }
    }

    /** The message cut short, if any, then those still waiting, in the order they came. */
    fun takeUnshown(): List<Message> = buildList {
        current?.let(::add)
        current = null
        while (true) add(queue.tryReceive().getOrNull() ?: break)
    }.filterNot { it.isDismissed }
}

/** How far a message floats above the navigation bar or the keyboard, clear of the screen's bottom edge. */
private val MessageBottomInset = 64.dp

/**
 * [content] with the app's messages shown over its bottom edge while it is the top surface. The
 * snackbar keeps one place on every surface, just clear of the navigation bar or the keyboard,
 * over whatever bar the surface has there, rather than moving with the bars of each.
 */
@Composable
fun MessageHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val messages = koinInject<UserMessages>()
    val context = LocalContext.current
    val slot = remember { MessageHostSlot() }
    val state = remember { SnackbarHostState() }
    DisposableEffect(messages, slot) {
        messages.attach(slot)
        onDispose { messages.detach(slot) }
    }
    LaunchedEffect(slot) { slot.showAll(state, context) }

    Box(modifier = modifier) {
        content()
        SnackbarHost(
            hostState = state,
            // Rounded off whole, as the pill floating over the screens is
            snackbar = { data -> Snackbar(snackbarData = data, shape = CircleShape) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
                .widthIn(max = Defaults.ContentMaxWidth)
                .fillMaxWidth()
                .padding(
                    start = Defaults.SpaceLarge,
                    top = Defaults.SpaceSmall,
                    end = Defaults.SpaceLarge,
                    bottom = MessageBottomInset
                )
        )
    }
}
