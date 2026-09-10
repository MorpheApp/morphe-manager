package app.morphe.manager.util

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Storage counts in SI, the way the system settings and the vendor of the disk count it. */
private const val BYTES_PER_KILOBYTE = 1_000.0
private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val BYTES_PER_GIGABYTE = 1_000_000_000.0

/** Memory counts in binary, which is what the kernel and the JVM mean by a megabyte. */
private const val BYTES_PER_MEBIBYTE = 1024 * 1024

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

/**
 * Format a "used / free" pair as `"{used} / {free}"`. If [free] is zero or negative,
 * returns just [used] so callers do not have to guard against unavailable device stats.
 */
fun Context.formatUsedFree(used: Long, free: Long): String =
    if (free <= 0L) formatBytes(used) else "${formatBytes(used)} / ${formatBytes(free)}"

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
    val locale = resources.configuration.locales[0]

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
        val numbers = NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }

        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, numbers)
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
 * Bytes as whole mebibytes: megabytes counted in binary, which is what a heap limit, the RAM of
 * a device and every memory reading in the log mean by a megabyte. Files never use this.
 */
fun bytesToMebibytes(bytes: Long): Long =
    if (bytes <= 0) 0L else bytes / BYTES_PER_MEBIBYTE

/**
 * An amount of memory, named the way a device names it. The label reads megabyte where the count
 * is mebibytes, because that is the word on every RAM spec and in every heap setting.
 */
fun Context.formatMebibytes(mebibytes: Int): String {
    val locale = resources.configuration.locales[0]

    return sizeFormat(locale, 0).format(Measure(mebibytes, MeasureUnit.MEGABYTE))
}

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
