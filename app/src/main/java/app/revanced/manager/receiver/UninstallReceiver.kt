package app.revanced.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.revanced.manager.service.UninstallService

@Suppress("DEPRECATION")
class UninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val extraStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val targetPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        Log.d("UninstallReceiver", "onReceive(status=$extraStatus, pkg=$targetPackage, msg=${extraStatusMessage?.take(120)})")

        when (extraStatus) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userActionIntent = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }

                if (!tryStartUserAction(context, userActionIntent)) {
                    val fallback = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${targetPackage.orEmpty()}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    if (!tryStartUserAction(context, fallback)) {
                        sendResultBroadcast(
                            context = context,
                            status = PackageInstaller.STATUS_FAILURE_BLOCKED,
                            statusMessage = extraStatusMessage ?: "Unable to launch uninstall confirmation.",
                            targetPackage = targetPackage
                        )
                    }
                }
            }

            else -> {
                sendResultBroadcast(
                    context = context,
                    status = extraStatus,
                    statusMessage = extraStatusMessage,
                    targetPackage = targetPackage
                )
            }
        }
    }

    private fun sendResultBroadcast(
        context: Context,
        status: Int,
        statusMessage: String?,
        targetPackage: String?
    ) {
        context.sendBroadcast(
            Intent().apply {
                action = UninstallService.APP_UNINSTALL_ACTION
                setPackage(context.packageName)
                putExtra(UninstallService.EXTRA_UNINSTALL_PACKAGE_NAME, targetPackage)
                putExtra(UninstallService.EXTRA_UNINSTALL_STATUS, status)
                putExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE, statusMessage)
            }
        )
    }

    private fun tryStartUserAction(context: Context, action: Intent?): Boolean {
        if (action == null) return false
        return runCatching {
            action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(action)
        }.onFailure {
            Log.w("UninstallReceiver", "Failed to start uninstall user action", it)
        }.isSuccess
    }
}
