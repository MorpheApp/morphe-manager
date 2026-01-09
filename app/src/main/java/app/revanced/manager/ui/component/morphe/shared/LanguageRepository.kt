package app.revanced.manager.ui.component.morphe.shared

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.R
import app.revanced.manager.util.parseLocaleCode
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

    /**
     * Get display name for a language code with proper localization
     */
    fun getLanguageDisplayName(code: String, context: Context): String {
        return when (code) {
            "system" -> context.getString(R.string.system)
            "en" -> "English"
            else -> {
                val locale = parseLocaleCode(code)
                val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
                getDisplayNameSmart(locale, currentLocale)
            }
        }
    }

    /**
     * Get list of all supported languages
     */
    fun getSupportedLanguages(context: Context): List<LanguageOption> {
        val systemOption = LanguageOption(
            code = "system",
            displayName = context.getString(R.string.system),
            nativeName = context.getString(R.string.system),
            flag = "🌐"
        )

        val englishOption = LanguageOption(
            code = "en",
            displayName = "English",
            nativeName = "English",
            flag = "🇺🇸"
        )

        val languageCodes = listOf(
            "af-rZA","am-rET","ar-rSA","as-rIN","az-rAZ","be-rBY","bg-rBG",
            "bn-rBD","bs-rBA","ca-rES","cs-rCZ","da-rDK","de-rDE","el-rGR",
            "es-rES","et-rEE","eu-rES","fa-rIR","fi-rFI","fil-rPH","fr-rFR",
            "ga-rIE","gl-rES","gn-rPY","gu-rIN","hi-rIN","hr-rHR","hu-rHU",
            "hy-rAM","in-rID","is-rIS","it-rIT","iw-rIL","ja-rJP","ka-rGE",
            "kk-rKZ","km-rKH","kn-rIN","ko-rKR","ky-rKG","lo-rLA","lt-rLT",
            "lv-rLV","mai-rIN","mk-rMK","ml-rIN","mn-rMN","mr-rIN","ms-rMY",
            "my-rMM","nb-rNO","ne-rNP","nl-rNL","or-rIN","pa-rIN","pl-rPL",
            "pt-rBR","pt-rPT","ro-rRO","ru-rRU","si-rLK","sk-rSK","sl-rSI",
            "sq-rAL","sr-rCS","sr-rSP","sv-rSE","sw-rKE","ta-rIN","te-rIN",
            "th-rTH","tr-rTR","uk-rUA","ur-rIN","uz-rUZ","vi-rVN",
            "zh-rCN","zh-rTW","zu-rZA"
        )

        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        val otherLanguages = languageCodes.map { code ->
            val locale = parseLocaleCode(code)
            LanguageOption(
                code = code,
                displayName = getDisplayNameSmart(locale, currentLocale),
                nativeName = getDisplayNameSmart(locale, locale),
                flag = getFlagEmoji(code)
            )
        }.sortedBy { it.displayName }

        // System → English → all others alphabetically
        return listOf(systemOption, englishOption) + otherLanguages
    }

    private fun String.capitalize(locale: Locale) =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /**
     * Get flag emoji for language code
     */
    private fun getFlagEmoji(code: String): String {
        val country = when {
            code.contains("-r") -> code.substringAfter("-r")
            code.contains("_") -> code.substringAfter("_")
            else -> "US"
        }

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
        val language = locale.language

        val baseName = locale.getDisplayLanguage(displayLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }

        // Show country only if language requires it and country is present
        if (language !in languagesRequiringRegion || locale.country.isEmpty()) {
            return baseName
        }

        val country = locale.getDisplayCountry(displayLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }

        return "$baseName ($country)"
    }
}

@Composable
fun rememberSelectedLanguageLabel(code: String): String {
    val context = LocalContext.current
    val languages = remember(context) {
        LanguageRepository.getSupportedLanguages(context)
    }

    return remember(code, languages) {
        languages.firstOrNull { it.code == code }
            ?.displayName
            ?: code
    }
}
