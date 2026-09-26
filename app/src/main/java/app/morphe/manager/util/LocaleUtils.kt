package app.morphe.manager.util

private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

/**
 * Wraps the string in Unicode isolate marks so a left-to-right token (version name, URL,
 * package name) keeps its internal order when placed inside right-to-left UI text.
 */
fun String.isolateLtr(): String = "$LEFT_TO_RIGHT_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"
