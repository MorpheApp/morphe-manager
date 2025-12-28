package app.revanced.manager.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log

@Suppress("DEPRECATION")
class UninstallService : Service() {

    override fun onStartCommand(
        intent: Intent,
        flags: Int,
        startId: Int
    ): Int {
        val extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val extraStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        val targetPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        when (extraStatus) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userActionIntent = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }

                if (!tryStartUserAction(userActionIntent)) {
                    val fallbackTarget = targetPackage.orEmpty()
                    val fallback = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$fallbackTarget")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    if (!tryStartUserAction(fallback)) {
                        sendBroadcast(Intent().apply {
                            action = APP_UNINSTALL_ACTION
                            `package` = packageName
                            putExtra(EXTRA_UNINSTALL_PACKAGE_NAME, targetPackage)
                            putExtra(EXTRA_UNINSTALL_STATUS, PackageInstaller.STATUS_FAILURE_BLOCKED)
                            putExtra(
                                EXTRA_UNINSTALL_STATUS_MESSAGE,
                                extraStatusMessage ?: "Unable to launch uninstall confirmation."
                            )
                        })
                    }
                }
            }

            else -> {
                sendBroadcast(Intent().apply {
                    action = APP_UNINSTALL_ACTION
                    `package` = packageName
                    putExtra(EXTRA_UNINSTALL_PACKAGE_NAME, targetPackage)
                    putExtra(EXTRA_UNINSTALL_STATUS, extraStatus)
                    putExtra(EXTRA_UNINSTALL_STATUS_MESSAGE, extraStatusMessage)
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
            Log.w("UninstallService", "Failed to start uninstall user action", it)
        }.isSuccess
    }

    companion object {
        const val APP_UNINSTALL_ACTION = "APP_UNINSTALL_ACTION"

        const val EXTRA_UNINSTALL_STATUS = "EXTRA_UNINSTALL_STATUS"
        const val EXTRA_UNINSTALL_STATUS_MESSAGE = "EXTRA_INSTALL_STATUS_MESSAGE"
        const val EXTRA_UNINSTALL_PACKAGE_NAME = "EXTRA_UNINSTALL_PACKAGE_NAME"
    }

}
