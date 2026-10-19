/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/util/Util.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.util

import android.graphics.Typeface
import android.text.Html
import android.text.style.StyleSpan
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.ui.screen.shared.UserMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** bundleUid → set of selected patch names. */
typealias PatchSelection = Map<Int, Set<String>>
/** bundleUid → patchName → optionKey → value. */
typealias Options = Map<Int, Map<String, Map<String, Any?>>>

/**
 * Runs [block], and should it fail, logs the error and tells the user, so a failed action never
 * crashes the app.
 *
 * @param failure The message to show if [block] throws, given the error's message.
 * @param logMsg The log message.
 */
inline fun UserMessages.reportingFailure(@StringRes failure: Int, logMsg: String, block: () -> Unit) {
    try {
        block()
    } catch (error: Exception) {
        // Cancellation is how a screen that goes away stops its own work, so reporting it would
        // put an error in front of the user for something they did on purpose
        if (error is CancellationException) throw error

        show(failure, error.simpleMessage())
        Log.e(tag, logMsg, error)
    }
}

/** Returns the most meaningful available message from this throwable. */
fun Throwable.simpleMessage() = this.message ?: this.cause?.message ?: this::class.simpleName

/**
 * Collects [flow] as a one-shot event stream, respecting the [lifecycleOwner][LocalLifecycleOwner]
 * and only collecting while the lifecycle is at least [state].
 * Re-launches automatically if [flow] or [keys] change.
 */
@Composable
fun <T> EventEffect(flow: Flow<T>, vararg keys: Any?, state: Lifecycle.State = Lifecycle.State.STARTED, block: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(flow, state, *keys) {
        lifecycleOwner.repeatOnLifecycle(state) {
            flow.collect {
                currentBlock(it)
            }
        }
    }
}

/**
 * Supports bold and italic HTML tags. Can be improved as needed to support more HTML functions.
 */
fun htmlAnnotatedString(html: String): AnnotatedString {
    val prepared = html.replace("\n", "<br>")
    val spanned = Html.fromHtml(prepared, Html.FROM_HTML_MODE_LEGACY)

    return buildAnnotatedString {
        append(spanned.toString())

        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)

            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD ->
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        Typeface.ITALIC ->
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                }
            }
        }
    }
}


/**
 * Returns a human-readable Android version name for this SDK integer.
 */
fun Int.androidVersionName(): String = when (this) {
    37 -> "17"
    36 -> "16"
    35 -> "15"
    34 -> "14"
    33 -> "13"
    32 -> "12L"
    31 -> "12"
    30 -> "11"
    29 -> "10"
    28 -> "9"
    27 -> "8.1"
    26 -> "8.0"
    else -> "$this" // future or very old SDK - just use the number
}

/**
 * Property delegate that stores a non-null value of type [T] in [SavedStateHandle],
 * initializing it with [init] on first access so it survives process death.
 */
@MainThread
fun <T : Any> SavedStateHandle.saveableVar(init: () -> T): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> =
    PropertyDelegateProvider { _: Any?, property ->
        val name = property.name
        if (name !in this) this[name] = init()
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T = get(name)!!
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
                set(name, value)
        }
    }

/** The [E] entry named [name], or null when nothing matches, as for a stale persisted value. */
inline fun <reified E : Enum<E>> enumByNameOrNull(name: String?): E? =
    enumValues<E>().firstOrNull { it.name == name }
