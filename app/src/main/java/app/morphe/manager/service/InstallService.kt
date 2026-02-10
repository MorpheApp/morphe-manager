package app.morphe.manager.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri

@Suppress("DEPRECATION")
class InstallService : Service() {

    override fun onStartCommand(
        intent: Intent, flags: Int, startId: Int
    ): Int {
        val extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val extraStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val extraPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (extraStatus) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userActionIntent = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }

                if (!tryStartUserAction(userActionIntent)) {
                    val fallback = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:$packageName".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    if (!tryStartUserAction(fallback)) {
                        sendBroadcast(Intent().apply {
                            action = APP_INSTALL_ACTION
                            `package` = packageName
                            putExtra(EXTRA_INSTALL_STATUS, PackageInstaller.STATUS_FAILURE_BLOCKED)
                            putExtra(
                                EXTRA_INSTALL_STATUS_MESSAGE,
                                extraStatusMessage ?: "Unable to launch installer confirmation."
                            )
                            putExtra(EXTRA_PACKAGE_NAME, extraPackageName)
                        })
                    }
                }
            }

            else -> {
                sendBroadcast(Intent().apply {
                    action = APP_INSTALL_ACTION
                    `package` = packageName
                    putExtra(EXTRA_INSTALL_STATUS, extraStatus)
                    putExtra(EXTRA_INSTALL_STATUS_MESSAGE, extraStatusMessage)
                    putExtra(EXTRA_PACKAGE_NAME, extraPackageName)
                })
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tryStartUserAction(action: Intent?): Boolean {
        if (action == null) return false
        return runCatching {
            action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(action)
        }.onFailure {
            Log.w("InstallService", "Failed to start installer user action", it)
        }.isSuccess
    }

    companion object {
        const val APP_INSTALL_ACTION = "APP_INSTALL_ACTION"

        const val EXTRA_INSTALL_STATUS = "EXTRA_INSTALL_STATUS"
        const val EXTRA_INSTALL_STATUS_MESSAGE = "EXTRA_INSTALL_STATUS_MESSAGE"
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    }

}
