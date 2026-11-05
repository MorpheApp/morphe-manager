/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import app.morphe.manager.R

/**
 * Where a setting is, as the steps leading to it from the settings tab, each named by the same
 * string as on screen so a message reads exactly what the user taps in every language.
 */
fun Resources.settingsPath(@StringRes vararg steps: Int): String =
    (intArrayOf(R.string.settings) + steps).joinToString(" > ") { getString(it) }

/** [settingsPath] for a message put together away from the UI. */
fun Context.settingsPath(@StringRes vararg steps: Int): String = resources.settingsPath(*steps)

/** [settingsPath] in the language the UI is shown in. */
@Composable
fun settingsPath(@StringRes vararg steps: Int): String = LocalResources.current.settingsPath(*steps)
