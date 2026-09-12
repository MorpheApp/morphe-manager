/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.patch

import app.morphe.manager.patcher.logger.Logger
import app.morphe.patcher.patch.Patch
import kotlin.reflect.KType

/**
 * Returns [value] as [type], or null when it cannot stand for one, since the patcher aborts the
 * run over a value of any other type. An unmodelled type is passed through untouched.
 */
fun coerceOptionValue(type: KType, value: Any?): Any? {
    if (value == null) return null

    if (type.classifier == List::class) {
        val elementType = type.arguments.firstOrNull()?.type ?: return value
        val elements = when (value) {
            is List<*>  -> value
            is Array<*> -> value.asList()
            // How the list editor and imported profiles carry a list
            is String   -> value.split(',').map(String::trim).filter(String::isNotEmpty)
            else        -> return null
        }

        return elements.map { element -> coerceOptionValue(elementType, element) ?: return null }
    }

    return when (type.classifier) {
        String::class  -> when (value) {
            is String             -> value
            is Number, is Boolean -> value.toString()
            else                  -> null
        }

        Boolean::class -> when (value) {
            is Boolean -> value
            is String  -> value.trim().lowercase().toBooleanStrictOrNull()
            else       -> null
        }

        Int::class     -> value.integralOrNull()?.takeIf { it in INT_RANGE }?.toInt()
        Long::class    -> value.integralOrNull()
        Float::class   -> value.decimalOrNull()?.toFloat()
        Double::class  -> value.decimalOrNull()
        else           -> value
    }
}

/**
 * Applies [options] to the patches of one bundle, each value converted to the type its option
 * declares. Covers the whole bundle, since a patch pulled in as a dependency is configured too.
 */
fun Map<String, Patch<*>>.applyPatchOptions(
    options: Map<String, Map<String, Any?>>,
    logger: Logger
) = options.forEach { (patchName, patchOptions) ->
    val patch = this[patchName] ?: return@forEach

    patchOptions.forEach setOption@{ (key, value) ->
        // Writing null would erase the default the patch falls back to
        if (value == null) return@setOption

        if (key !in patch.options) {
            return@setOption logger.warn("Patch \"$patchName\" does not have the option \"$key\"")
        }

        val option = patch.options[key]
        val coerced = coerceOptionValue(option.type, value) ?: return@setOption logger.warn(
            "Option \"$key\" of the \"$patchName\" patch does not accept \"$value\" as ${option.type}"
        )

        patch.options[key] = coerced
    }
}

private val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

/** The whole number [this] stands for, or null when it is none. */
private fun Any.integralOrNull(): Long? = when (this) {
    is Long                   -> this
    is Int, is Short, is Byte -> (this as Number).toLong()
    is Float, is Double       -> (this as Number).toDouble().toWholeOrNull()
    is String                 -> trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toWholeOrNull() }
    else                      -> null
}

/** The decimal [this] stands for, or null when it is none. */
private fun Any.decimalOrNull(): Double? = when (this) {
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else      -> null
}

/** Rejects fractions, NaN and infinities, which no integer option can hold. */
private fun Double.toWholeOrNull(): Long? = takeIf { it == it.toLong().toDouble() }?.toLong()
