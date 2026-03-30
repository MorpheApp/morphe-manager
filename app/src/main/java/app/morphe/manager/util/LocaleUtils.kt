package app.morphe.manager.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Parse a BCP 47 locale code into a [Locale].
 *
 * Expected format:
 *  - `"uk-UA"` → `Locale.forLanguageTag("uk-UA")`
 *  - `"en"`    → `Locale.forLanguageTag("en")`
 *  - `"system"` / blank → `null` (caller should use empty LocaleList)
 */
fun parseLocaleCode(code: String): Locale? {
    val normalized = code.trim()
    if (normalized.isBlank() || normalized == "system") return null

    return if (normalized.contains("-")) {
        val parts = normalized.split("-", limit = 2)
        Locale.forLanguageTag("${parts[0]}-${parts[1]}")
    } else {
        Locale.forLanguageTag(normalized)
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
 * Returns the list of supported locale codes, excluding "en" which is
 * handled separately as the default language.
 *
 * Hardcoded to match `res/xml/locales_config.xml` - parsing that file at runtime
 * is not possible because arsclib (which replaces xmlpull:xmlpull) causes
 * [android.content.res.Resources.getXml] to return null.
 *
 * Keep in sync with `res/xml/locales_config.xml` when adding/removing languages.
 */
fun parseLocalesConfig(): List<String> = listOf(
    "af-ZA", "am-ET", "ar-SA", "as-IN", "az-AZ", "be-BY", "bg-BG", "bn-BD",
    "bs-BA", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "es-ES", "et-EE",
    "eu-ES", "fa-IR", "fi-FI", "fil-PH", "fr-FR", "ga-IE", "gl-ES", "gu-IN",
    "hi-IN", "hr-HR", "hu-HU", "hy-AM", "id-ID", "is-IS", "it-IT", "he-IL",
    "ja-JP", "kmr-TR", "ka-GE", "kk-KZ", "km-KH", "kn-IN", "ko-KR", "ky-KG",
    "lo-LA", "lt-LT", "lv-LV", "mai-IN", "mk-MK", "mn-MN", "ms-MY", "my-MM",
    "nb-NO", "ne-IN", "nl-NL", "or-IN", "pa-IN", "pl-PL", "pt-BR", "pt-PT",
    "ro-RO", "ru-RU", "si-LK", "sk-SK", "sl-SI", "sr-CS", "sr-SP", "sv-SE",
    "sw-KE", "ta-IN", "te-IN", "th-TH", "tr-TR", "uk-UA", "ur-IN", "uz-UZ",
    "vi-VN", "zh-CN", "zh-TW", "zu-ZA"
)

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
