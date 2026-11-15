/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R

/**
 * How far Morphe keeps itself and the patch sources up to date on its own. A pull to refresh, or
 * a tap on an update it found, always goes ahead whatever is chosen here.
 */
enum class AutoUpdates(@param:StringRes val titleRes: Int, @param:StringRes val descriptionRes: Int) {
    DOWNLOAD(R.string.settings_auto_updates_download, R.string.settings_auto_updates_download_description),
    WIFI_ONLY(R.string.settings_auto_updates_wifi_only, R.string.settings_auto_updates_wifi_only_description),
    CHECK_ONLY(R.string.settings_auto_updates_check_only, R.string.settings_auto_updates_check_only_description),
    OFF(R.string.settings_auto_updates_off, R.string.settings_auto_updates_off_description);

    /** Whether a download nobody confirmed may use mobile data. */
    val downloadsOnMobileData get() = this == DOWNLOAD

    /** Whether opening Morphe checks for updates at all. */
    val checksOnLaunch get() = this != OFF

    /** Whether opening Morphe downloads the patch updates it finds, on a [metered] network or not. */
    fun downloadsOnLaunch(metered: Boolean) = this == DOWNLOAD || this == WIFI_ONLY && !metered
}
