/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import java.text.FieldPosition
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Storage counts in SI, the way the system settings and the vendor of the disk count it. */
private const val BYTES_PER_KILOBYTE = 1_000.0
private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val BYTES_PER_GIGABYTE = 1_000_000_000.0

/** Memory counts in binary, which is what the kernel and the JVM mean by a megabyte. */
private const val KIBIBYTES_PER_MEBIBYTE = 1024

/** Whole bytes get none of them: "512.00 B" reads like a mistake. */
private const val SIZE_DECIMALS = 2

/** The unit of each scale twice: spelled out for a report, and left for the locale to name. */
private val SIZE_UNITS = arrayOf("B", "kB", "MB", "GB")
private val SIZE_MEASURE_UNITS = arrayOf(
    MeasureUnit.BYTE,
    MeasureUnit.KILOBYTE,
    MeasureUnit.MEGABYTE,
    MeasureUnit.GIGABYTE
)

/** Building one costs more than the reading it prints, and a list formats every row it shows. */
private val sizeFormats = ConcurrentHashMap<Pair<Locale, Int>, MeasureFormat>()
private val numberFormats = ConcurrentHashMap<Pair<Locale, Int>, NumberFormat>()

/**
 * Size of a file or a volume, named in the reader's language.
 *
 * The unit comes from the locale data the platform ships, so nothing here is ours to translate.
 * The count is SI, like the system settings and like the vendor of the disk. The platform would
 * format the whole reading too, but it drops the decimals above a hundred, and a list of APKs is
 * easier to compare with them kept. Memory does not follow any of this: see [bytesToMebibytes].
 */
fun Context.formatBytes(bytes: Long): String {
    val (value, unit) = scaleBytes(bytes)

    return sizeFormat(locale, if (unit == 0) 0 else SIZE_DECIMALS)
        .format(Measure(value, SIZE_MEASURE_UNITS[unit]))
}

/**
 * The same size for a log line or a report the user copies. It stays English and free of the
 * reader's locale so every report that reaches the tracker reads alike.
 */
fun formatBytesForReport(bytes: Long): String {
    val (value, unit) = scaleBytes(bytes)

    return if (unit == 0) {
        "$bytes ${SIZE_UNITS[unit]}"
    } else {
        String.format(Locale.ROOT, "%.${SIZE_DECIMALS}f %s", value, SIZE_UNITS[unit])
    }
}

/** The number scaled to the largest unit it fills, paired with the index of that unit. */
private fun scaleBytes(bytes: Long): Pair<Double, Int> = when {
    bytes < BYTES_PER_KILOBYTE -> bytes.toDouble() to 0
    bytes < BYTES_PER_MEGABYTE -> bytes / BYTES_PER_KILOBYTE to 1
    bytes < BYTES_PER_GIGABYTE -> bytes / BYTES_PER_MEGABYTE to 2
    else -> bytes / BYTES_PER_GIGABYTE to 3
}

private fun sizeFormat(locale: Locale, decimals: Int): MeasureFormat =
    sizeFormats.getOrPut(locale to decimals) {
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, numberFormat(locale, decimals))
    }

private fun numberFormat(locale: Locale, decimals: Int): NumberFormat =
    numberFormats.getOrPut(locale to decimals) {
        NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }

/**
 * The conversion behind [formatMegabytes], counted like [formatBytes] because what it measures is
 * a download on its way to being a file.
 */
private fun bytesToMegabytes(bytes: Long): Float =
    if (bytes <= 0) 0f else (bytes / BYTES_PER_MEGABYTE).toFloat()

/**
 * The same figure ready for a progress line, at the one decimal those lines show. Formatting it
 * here keeps a precision specifier out of the hands of translators.
 */
fun formatMegabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f", bytesToMegabytes(bytes))

/** Bytes for the free-storage warning, formatted here for the reason [formatMegabytes] gives. */
fun formatGigabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.2f", bytesToGigabytes(bytes))

private fun bytesToGigabytes(bytes: Long): Float =
    if (bytes <= 0) 0f else (bytes / BYTES_PER_GIGABYTE).toFloat()

/**
 * The unit amounts of memory are named in. Readings shown together share one, picked by the largest
 * of them through [of], so a reading and the limit it climbs towards never name different units.
 */
enum class MemoryUnit(
    internal val measureUnit: MeasureUnit,
    internal val mebibytes: Int,
    internal val decimals: Int
) {
    Megabytes(MeasureUnit.MEGABYTE, 1, 0),
    // A whole number of gigabytes would hide most of what a heap does
    Gigabytes(MeasureUnit.GIGABYTE, 1024, 1);

    companion object {
        fun of(largestMebibytes: Int) =
            if (largestMebibytes >= Gigabytes.mebibytes) Gigabytes else Megabytes
    }
}

/**
 * An amount of memory, named the way a device names it. The label reads megabyte where the count
 * is mebibytes, and gigabyte for 1024 of them, because those are the words on every RAM spec and
 * in every heap setting.
 */
fun Context.formatMebibytes(mebibytes: Int, unit: MemoryUnit = MemoryUnit.Megabytes): String =
    sizeFormat(locale, unit.decimals).format(Measure(mebibytes.inUnit(unit), unit.measureUnit))

/**
 * The count of [formatMebibytes] without its unit, for a reading that the amount following it
 * names the unit of.
 */
fun Context.formatMebibyteCount(mebibytes: Int, unit: MemoryUnit): String =
    numberFormat(locale, unit.decimals).format(mebibytes.inUnit(unit))

/**
 * A storage throughput, named the way [formatMebibytes] names memory: kibibytes as kilobytes, and
 * megabytes at one decimal once there are 1024 of them.
 */
fun Context.formatKibibytesPerSecond(kibibytes: Int): String {
    val megabytes = kibibytes >= KIBIBYTES_PER_MEBIBYTE
    val measure = if (megabytes) {
        Measure(kibibytes.toFloat() / KIBIBYTES_PER_MEBIBYTE, MeasureUnit.MEGABYTE)
    } else {
        Measure(kibibytes, MeasureUnit.KILOBYTE)
    }

    return sizeFormat(locale, if (megabytes) 1 else 0)
        .formatMeasurePerUnit(measure, MeasureUnit.SECOND, StringBuilder(), FieldPosition(0))
        .toString()
}

private fun Int.inUnit(unit: MemoryUnit): Number =
    if (unit.mebibytes == 1) this else toFloat() / unit.mebibytes

private val Context.locale: Locale
    get() = resources.configuration.locales[0]

/**
 * Get relative time string (e.g., "2 hours ago")
 */
fun getRelativeTimeString(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
