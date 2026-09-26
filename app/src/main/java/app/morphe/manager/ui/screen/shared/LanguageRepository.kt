/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import android.content.Context
import app.morphe.manager.R
import app.morphe.manager.util.AppLocale
import java.util.Locale

/**
 * Data class for language options.
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String
)

object LanguageRepository {
    // Languages that require region/country to be displayed
    private val languagesRequiringRegion = setOf(
        "pt", // Portuguese: pt-BR / pt-PT
        "zh", // Chinese: zh-CN / zh-TW
        "sr", // Serbian: sr-CS / sr-SP
    )

    private const val GLOBE_FLAG = "🌐"

    // The default resources carry no region, and they are written in US English
    private const val ENGLISH_FLAG = "🇺🇸"

    // Cached language list (cleared on locale change via getSupportedLanguages)
    @Volatile
    private var cachedLanguages: List<LanguageOption>? = null
    @Volatile
    private var cachedForLocale: Locale? = null

    /**
     * The option for [code], built on the spot for a language the list does not name, and the
     * system default for a code that names no language at all.
     */
    fun getLanguage(code: String, context: Context): LanguageOption =
        getSupportedLanguages(context).find { it.code == code }
            ?: languageOption(code, context.resources.configuration.locales[0])
            ?: systemOption(context)

    /**
     * System default, then English, then every translation alphabetically.
     *
     * The result is cached per display locale - the cache is invalidated automatically
     * when the device or app locale changes.
     */
    fun getSupportedLanguages(context: Context): List<LanguageOption> {
        val currentLocale = context.resources.configuration.locales[0]

        // Return cache if the display locale hasn't changed
        cachedLanguages?.let { cached ->
            if (cachedForLocale == currentLocale) return cached
        }

        val otherLanguages = AppLocale.translations
            .mapNotNull { languageOption(it, currentLocale) }
            .sortedBy { it.displayName }

        // System → English → all others alphabetically
        val result = listOfNotNull(systemOption(context), languageOption(AppLocale.ENGLISH, currentLocale)) +
                otherLanguages

        cachedLanguages = result
        cachedForLocale = currentLocale

        return result
    }

    private fun systemOption(context: Context) = LanguageOption(
        code = AppLocale.SYSTEM,
        displayName = context.getString(R.string.system),
        nativeName = context.getString(R.string.system),
        flag = GLOBE_FLAG
    )

    private fun languageOption(code: String, displayLocale: Locale): LanguageOption? {
        val locale = AppLocale.toLocale(code) ?: return null
        return LanguageOption(
            code = code,
            displayName = getDisplayNameSmart(locale, displayLocale),
            nativeName = getDisplayNameSmart(locale, locale),
            flag = if (code == AppLocale.ENGLISH) ENGLISH_FLAG else getFlagEmoji(locale)
        )
    }

    /**
     * Get flag emoji from a [Locale]'s country code.
     */
    private fun getFlagEmoji(locale: Locale): String {
        val country = locale.country.takeIf { it.length == 2 } ?: return GLOBE_FLAG
        return try {
            val first = Character.codePointAt(country, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(country, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (_: Exception) {
            GLOBE_FLAG
        }
    }

    /**
     * Shows the country/region only for languages with multiple regional variants.
     * For all other languages, only the language name is shown.
     */
    private fun getDisplayNameSmart(locale: Locale, displayLocale: Locale): String {
        val baseName = locale.getDisplayLanguage(displayLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }

        // Show country only if language requires it and country is present
        if (locale.language !in languagesRequiringRegion || locale.country.isEmpty()) {
            return baseName
        }

        val country = locale.getDisplayCountry(displayLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }

        return "$baseName ($country)"
    }
}
