package app.morphe.manager.util

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import app.morphe.manager.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The language Morphe is shown in, picked in the app or, from Android 13 on, in the system settings.
 *
 * Android 13+ keeps the choice itself and applies it to every context of the app, the application
 * and its services included. Older versions have no notion of a per-app language, so there the
 * choice lives in [SharedPreferences], the only store readable from attachBaseContext before Koin
 * and DataStore exist, and each base context is localized by hand.
 */
object AppLocale {
    /** Follows the device language. */
    const val SYSTEM = "system"

    /** Language of the default resources, which has no translation folder of its own. */
    const val ENGLISH = "en"

    /** Every other language Morphe is translated into, as collected from its resource folders. */
    val translations: List<String> = BuildConfig.TRANSLATIONS.asList()

    /** Whether Android applies the language by itself, recreating the activities on a change. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val appliedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private const val PREFS_NAME = "morphe_locale"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_HANDED_TO_SYSTEM = "handed_to_system"

    private lateinit var prefs: SharedPreferences

    private val _selected = MutableStateFlow(SYSTEM)

    /** The chosen language as a BCP 47 tag, or [SYSTEM]. */
    val selected: StateFlow<String> = _selected.asStateFlow()

    /**
     * Loads the choice and returns the base context the application should attach, localized on
     * Android 12 and lower so that strings resolved outside an activity follow the choice too.
     */
    fun attach(base: Context): Context {
        prefs = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (appliedBySystem) {
            handToSystem(base)
            _selected.value = systemChoice(base)
            return base
        }

        val choice = normalize(prefs.getString(KEY_LANGUAGE, null))
        _selected.value = choice
        applyDefaultLocale(choice)
        return LocalizedContext(base)
    }

    /** Switches every context of the app to [code], a BCP 47 tag or [SYSTEM]. */
    fun select(context: Context, code: String) {
        val choice = normalize(code)
        if (appliedBySystem) {
            localeManager(context).applicationLocales = localeListOf(choice)
        } else {
            prefs.edit { putString(KEY_LANGUAGE, choice) }
            applyDefaultLocale(choice)
        }
        _selected.value = choice
    }

    /**
     * Picks up a change made while the process runs: a language set in the system settings on
     * Android 13+, or the default locale Android 12 and lower reset to the device language.
     */
    fun onConfigurationChanged(context: Context) {
        if (appliedBySystem) _selected.value = systemChoice(context) else applyDefaultLocale(_selected.value)
    }

    /** Adds the chosen language to [overrides], a configuration delta for a base context. */
    fun applyTo(overrides: Configuration) {
        if (appliedBySystem) return
        toLocale(_selected.value)?.let(overrides::setLocale)
    }

    /** The locale of [code], or null for [SYSTEM] and anything that does not name a language. */
    fun toLocale(code: String): Locale? =
        Locale.forLanguageTag(code).takeIf { code != SYSTEM && it.language.isNotEmpty() }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context): LocaleManager =
        context.getSystemService(LocaleManager::class.java)

    /**
     * Earlier versions kept their own copy of the choice on every Android version. On the first
     * launch on Android 13+ that copy is handed to the system, which may not have it yet after a
     * restored backup or an update from Android 12, and dropped so that only one store is left.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun handToSystem(context: Context) {
        if (prefs.getBoolean(KEY_HANDED_TO_SYSTEM, false)) return

        val stored = normalize(prefs.getString(KEY_LANGUAGE, null))
        val manager = localeManager(context)
        if (stored != SYSTEM && manager.applicationLocales.isEmpty) {
            manager.applicationLocales = localeListOf(stored)
        }
        prefs.edit {
            remove(KEY_LANGUAGE)
            putBoolean(KEY_HANDED_TO_SYSTEM, true)
        }
    }

    /**
     * The language set for the app on Android 13+, matched to a supported code. The system
     * settings offer the default resources as "en-US", while the picker names them [ENGLISH].
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun systemChoice(context: Context): String {
        val locale = localeManager(context).applicationLocales.takeUnless { it.isEmpty }?.get(0) ?: return SYSTEM
        val supported = listOf(ENGLISH) + translations
        val tag = locale.toLanguageTag()
        return supported.find { it == tag }
            ?: supported.find { toLocale(it)?.language == locale.language }
            ?: tag
    }

    /**
     * Android 12 and lower set the default locale from the device language on start and on each
     * configuration change, so it is set again for formatting to follow the choice.
     */
    private fun applyDefaultLocale(code: String) {
        LocaleList.setDefault(toLocale(code)?.let { LocaleList(it) } ?: Resources.getSystem().configuration.locales)
    }

    private fun localeListOf(code: String): LocaleList =
        toLocale(code)?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()

    private fun normalize(code: String?): String = code?.trim()?.takeIf { toLocale(it) != null } ?: SYSTEM

    /**
     * Base context of the application on Android 12 and lower. The language can change while the
     * process runs, and a base context is attached only once, so its resources follow [selected]
     * instead of being fixed at attach time.
     */
    private class LocalizedContext(base: Context) : ContextWrapper(base) {
        private class Localized(val code: String, val context: Context)

        @Volatile
        private var localized: Localized? = null

        override fun getResources(): Resources {
            val code = selected.value
            val current = localized?.takeIf { it.code == code }
                ?: Localized(code, localize(code)).also { localized = it }
            return current.context.resources
        }

        /** Overrides the locale alone, so the rest of the configuration keeps following the device. */
        private fun localize(code: String): Context {
            val locale = toLocale(code) ?: return baseContext
            return baseContext.createConfigurationContext(Configuration().apply { setLocale(locale) })
        }
    }
}
