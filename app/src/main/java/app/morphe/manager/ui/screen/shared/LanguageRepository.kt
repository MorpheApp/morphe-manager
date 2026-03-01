package app.morphe.manager.ui.screen.shared

import android.content.Context
import app.morphe.manager.R
import app.morphe.manager.util.parseLocaleCode
import app.morphe.manager.util.parseLocalesConfig
import java.util.Locale

/**
 * Data class for language options
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

    // Cached language list (cleared on locale change via getSupportedLanguages)
    @Volatile
    private var cachedLanguages: List<LanguageOption>? = null
    @Volatile
    private var cachedForLocale: Locale? = null

    /**
     * Get display name for a language code with proper localization.
     * Returns the system label for "system", otherwise the language display name.
     */
    fun getLanguageDisplayName(code: String, context: Context): String {
        val currentLocale = context.resources.configuration.locales[0]

        return when (code) {
            "system" -> context.getString(R.string.settings_appearance_system)
            else -> {
                val locale = parseLocaleCode(code) ?: return context.getString(R.string.settings_appearance_system)
                locale.getDisplayLanguage(currentLocale).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString()
                }
            }
        }
    }

    /**
     * Get list of all supported languages, read from `res/xml/locales_config.xml`.
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

        val systemOption = LanguageOption(
            code = "system",
            displayName = context.getString(R.string.system),
            nativeName = context.getString(R.string.system),
            flag = "🌐"
        )

        val englishLocale = Locale("en")
        val englishOption = LanguageOption(
            code = "en",
            displayName = englishLocale.getDisplayLanguage(currentLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString()
            },
            nativeName = englishLocale.getDisplayLanguage(englishLocale).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(englishLocale) else it.toString()
            },
            flag = "🇺🇸"
        )

        // Read locale codes from locales_config.xml
        val localeCodes = parseLocalesConfig(context)

        val otherLanguages = localeCodes.mapNotNull { code ->
            val locale = parseLocaleCode(code) ?: return@mapNotNull null
            LanguageOption(
                code = code,
                displayName = getDisplayNameSmart(locale, currentLocale),
                nativeName = getDisplayNameSmart(locale, locale),
                flag = getFlagEmoji(locale)
            )
        }.sortedBy { it.displayName }

        // System → English → all others alphabetically
        val result = listOf(systemOption, englishOption) + otherLanguages

        cachedLanguages = result
        cachedForLocale = currentLocale

        return result
    }

    /**
     * Get flag emoji from a [Locale]'s country code.
     */
    private fun getFlagEmoji(locale: Locale): String {
        val country = locale.country.takeIf { it.length == 2 } ?: return "🌐"
        return try {
            val first = Character.codePointAt(country, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(country, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (_: Exception) {
            "🌐"
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
