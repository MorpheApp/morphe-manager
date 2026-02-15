package app.morphe.manager.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Parse locale code from Android resource format
 * Examples:
 * - "uk-rUA" -> Locale("uk", "UA")
 * - "uk_UA" -> Locale("uk", "UA")
 * - "en" -> Locale("en")
 */
fun parseLocaleCode(code: String): Locale =
    when {
        code.contains("-r") -> {
            val (l, c) = code.split("-r")
            Locale(l, c)
        }
        code.contains("_") -> {
            val (l, c) = code.split("_")
            Locale(l, c)
        }
        else -> Locale(code)
    }

/**
 * Apply app language setting to the entire application
 * @param code Language code in Android resource format (e.g., "uk-rUA", "en", "system")
 */
fun applyAppLanguage(code: String) {
    val normalized = code.trim()

    // System default
    if (normalized.isBlank() || normalized == "system") {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        return
    }

    // Parse and apply locale
    val locale = parseLocaleCode(normalized)
    Locale.setDefault(locale)
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
}
