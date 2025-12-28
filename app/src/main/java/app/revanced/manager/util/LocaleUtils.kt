package app.revanced.manager.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

private val supportedLanguages = setOf(
    "system",
    "en",
    "zh-cn",
    "vi",
    "ko",
    "ja",
    "ru",
    "uk"
)

private fun normalizeTag(tag: String): String =
    tag.trim()
        .replace('_', '-')
        .lowercase(Locale.ROOT)

fun resolveAppLanguageCode(preference: String): String {
    val pref = normalizeTag(preference)
    if (pref.isBlank() || pref == "system") {
        val sysLocale = LocaleListCompat.getAdjustedDefault().get(0)
        val systemTag = sysLocale?.toLanguageTag()?.let(::normalizeTag)

        // Exact tag match
        if (systemTag != null && supportedLanguages.contains(systemTag)) return systemTag

        // Language-only match (e.g., en-GB -> en)
        val systemLang = systemTag?.substringBefore('-')
        if (systemLang != null) {
            supportedLanguages.firstOrNull { it.substringBefore('-') == systemLang }?.let { return it }
        }

        // Fallback to English if the system locale is unsupported.
        return "en"
    }

    if (supportedLanguages.contains(pref)) return pref

    val prefLang = pref.substringBefore('-')
    return supportedLanguages.firstOrNull { it.substringBefore('-') == prefLang } ?: "en"
}

fun applyAppLanguage(code: String) {
    val normalized = normalizeTag(code)

    // If user picked system, try the device locale first, otherwise fall back to English.
    val target = if (normalized == "system" || normalized.isBlank()) {
        // Reset to follow the device (AppCompat will use system locales if we set an empty list).
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        LocaleListCompat.getAdjustedDefault().get(0)?.let { Locale.setDefault(it) }
        return
    } else {
        resolveAppLanguageCode(code)
    }

    val localeList = when (target.lowercase(Locale.ROOT)) {
        "zh", "zh-cn", "zh_cn", "zh-hans" -> LocaleListCompat.create(Locale.SIMPLIFIED_CHINESE)
        "en", "en-us", "en_gb" -> LocaleListCompat.create(Locale.ENGLISH)
        else -> LocaleListCompat.forLanguageTags(target)
    }

    localeList.get(0)?.let { Locale.setDefault(it) }
    AppCompatDelegate.setApplicationLocales(localeList)
}
