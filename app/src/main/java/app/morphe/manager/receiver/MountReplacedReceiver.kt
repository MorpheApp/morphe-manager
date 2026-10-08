/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.util.AppCoroutineScope
import app.morphe.manager.util.PM
import app.morphe.manager.util.UpdateNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Told by a mount module at boot that the installed app is no longer the version it mounts, which
 * nothing else would notice. The module only names the package, so the record decides the rest.
 */
class MountReplacedReceiver : BroadcastReceiver(), KoinComponent {
    private val installedAppRepository: InstalledAppRepository by inject()
    private val pm: PM by inject()
    private val notificationManager: UpdateNotificationManager by inject()
    private val applicationScope: AppCoroutineScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val pending = goAsync()
        applicationScope.launch(Dispatchers.IO) {
            try {
                val app = installedAppRepository.get(packageName)
                    ?.takeIf { it.installType == InstallType.MOUNT }
                    ?: return@launch
                val installed = pm.getPackageInfo(packageName) ?: return@launch
                val installedVersion = installed.versionName ?: return@launch
                if (installedVersion == app.version) return@launch

                notificationManager.showMountReplacedNotification(
                    packageName = packageName,
                    label = with(pm) { installed.label() },
                    installedVersion = installedVersion,
                    patchedVersion = app.version
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Set by the module's service script, which knows the package but nothing of Morphe. */
        const val EXTRA_PACKAGE = "package"
    }
}
