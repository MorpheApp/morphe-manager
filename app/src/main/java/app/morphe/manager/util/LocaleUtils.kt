package app.morphe.manager.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.morphe.manager.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Parse a BCP 47 locale code into a [Locale].
 *
 * Expected format:
 *  - `"uk-UA"` → `Locale("uk", "UA")`
 *  - `"en"`    → `Locale("en")`
 *  - `"system"` / blank → `null` (caller should use empty LocaleList)
 */
fun parseLocaleCode(code: String): Locale? {
    val normalized = code.trim()
    if (normalized.isBlank() || normalized == "system") return null

    return if (normalized.contains("-")) {
        val parts = normalized.split("-", limit = 2)
        Locale(parts[0], parts[1])
    } else {
        Locale(normalized)
    }
}

/**
 * Convert a legacy Android resource locale code to BCP 47 format.
 *
 * Examples:
 *  - `"uk-rUA"` → `"uk-UA"`
 *  - `"in-rID"` → `"id-ID"`
 *  - `"iw-rIL"` → `"he-IL"`
 *  - `"uk-UA"`  → `"uk-UA"` (already BCP 47 — no change)
 *  - `"system"` → `"system"`
 */
fun migrateLegacyLocaleCode(code: String): String {
    if (code.isBlank() || code == "system") return code

    // Legacy Android resource format: "uk-rUA" → "uk-UA"
    val normalized = if (code.contains("-r")) {
        code.replace("-r", "-")
    } else {
        code
    }

    // Legacy language codes: in → id, iw → he
    return when {
        normalized.startsWith("in-") -> normalized.replaceFirst("in-", "id-")
        normalized.startsWith("iw-") -> normalized.replaceFirst("iw-", "he-")
        normalized == "in" -> "id"
        normalized == "iw" -> "he"
        else -> normalized
    }
}

/**
 * Read supported locale codes from `res/xml/locales_config.xml`.
 *
 * Returns BCP 47 codes (e.g. "uk-UA", "pt-BR") excluding the base "en"
 * which is handled separately as the default language.
 */
fun parseLocalesConfig(context: Context): List<String> {
    val codes = mutableListOf<String>()
    try {
        val parser = context.resources.getXml(R.xml.locales_config)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                val name = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android", "name"
                )
                // Skip base English — it's added separately as the default
                if (!name.isNullOrBlank() && name != "en") {
                    codes.add(name)
                }
            }
        }
        parser.close()
    } catch (_: Exception) {
        // Fallback: return empty list, only System + English will be shown
    }
    return codes
}

/**
 * Apply the app language to the entire application process via
 * [AppCompatDelegate.setApplicationLocales].
 */
fun applyAppLanguage(code: String) {
    val locale = parseLocaleCode(code)
    val localeList = if (locale != null) {
        LocaleListCompat.create(locale)
    } else {
        LocaleListCompat.getEmptyLocaleList() // revert to system
    }
    AppCompatDelegate.setApplicationLocales(localeList)
}
