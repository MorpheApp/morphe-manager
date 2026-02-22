package app.morphe.manager.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Html
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.compose.material3.ListItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.morphe.manager.R
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

typealias PatchSelection = Map<Int, Set<String>>
typealias Options = Map<Int, Map<String, Map<String, Any?>>>

fun isArmV7(): Boolean {
    val abis = Build.SUPPORTED_ABIS.map { it.lowercase() }
    return abis.any { it.contains("armeabi-v7a") }
}

fun Context.toastHandle(string: String, duration: Int = Toast.LENGTH_SHORT): Toast =
    Toast.makeText(this, string, duration).apply { show() }

fun Context.toast(string: String, duration: Int = Toast.LENGTH_SHORT) {
    toastHandle(string, duration)
}

/**
 * Safely perform an operation that may fail to avoid crashing the app.
 * If [block] fails, the error will be logged and a toast will be shown to the user to inform them that the action failed.
 *
 * @param context The android [Context].
 * @param toastMsg The toast message to show if [block] throws.
 * @param logMsg The log message.
 * @param block The code to execute.
 */
@OptIn(DelicateCoroutinesApi::class)
inline fun uiSafe(context: Context, @StringRes toastMsg: Int, logMsg: String, block: () -> Unit) {
    try {
        block()
    } catch (error: Exception) {
        // You can only toast on the main thread.
        GlobalScope.launch(Dispatchers.Main) {
            context.toast(
                context.getString(
                    toastMsg,
                    error.simpleMessage()
                )
            )
        }

        Log.e(tag, logMsg, error)
    }
}

fun Throwable.simpleMessage() = this.message ?: this.cause?.message ?: this::class.simpleName

fun LocalDateTime.relativeTime(context: Context): String {
    try {
        val now = Clock.System.now()
        val duration = now - this.toInstant(TimeZone.UTC)

        return when {
            duration.inWholeMinutes < 1 -> context.getString(R.string.just_now)
            duration.inWholeMinutes < 60 -> context.getString(
                R.string.minutes_ago,
                duration.inWholeMinutes.toString()
            )

            duration.inWholeHours < 24 -> context.getString(
                R.string.hours_ago,
                duration.inWholeHours.toString()
            )

            duration.inWholeHours < 30 -> context.getString(
                R.string.days_ago,
                duration.inWholeDays.toString()
            )

            else -> LocalDateTime.Format {
                monthName(MonthNames.ENGLISH_ABBREVIATED)
                char(' ')
                dayOfMonth()
                if (now.toLocalDateTime(TimeZone.UTC).year != this@relativeTime.year) {
                    chars(", ")
                    year()
                }
            }.format(this)
        }
    } catch (_: IllegalArgumentException) {
        return context.getString(R.string.invalid_date)
    }
}

fun Long.relativeTime(context: Context): String {
    if (this <= 0L) return context.getString(R.string.invalid_date)
    return DateUtils.getRelativeTimeSpanString(
        this,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}

private var transparentListItemColorsCached: ListItemColors? = null

fun resetListItemColorsCached() {
    transparentListItemColorsCached = null
}

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
 * Supports bold and italic html tags. Can be improved as needed to support more html functions.
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


fun Modifier.enabled(condition: Boolean) = if (condition) this else alpha(0.5f)

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

fun <T : Any> SavedStateHandle.saveableVar(): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? = get(property.name)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) =
            set(property.name, value)
    }
