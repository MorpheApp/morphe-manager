package app.revanced.manager.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

fun applyAppLanguage(code: String) {
    val target = code.ifBlank { "en" }
    val localeList = when (target.lowercase(Locale.ROOT)) {
        "zh", "zh-cn", "zh_cn", "zh-hans" -> LocaleListCompat.create(Locale.SIMPLIFIED_CHINESE)
        "en", "en-us", "en_gb" -> LocaleListCompat.create(Locale.ENGLISH)
        else -> LocaleListCompat.forLanguageTags(target)
    }

    // Ensure the process default locale reflects the selected app locale (helps widgets/text refresh).
    localeList.get(0)?.let { Locale.setDefault(it) }
    AppCompatDelegate.setApplicationLocales(localeList)
}
