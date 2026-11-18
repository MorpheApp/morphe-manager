/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.content.Intent
import android.content.pm.PackageInstaller

/** What a [PackageInstaller] session or uninstall request reports in the intent it sends back. */
data class InstallerStatus(val status: Int, val message: String?) {
    companion object {
        /** Reads the status [intent] carries, a failure when it carries none. */
        fun from(intent: Intent) = InstallerStatus(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        )
    }
}
