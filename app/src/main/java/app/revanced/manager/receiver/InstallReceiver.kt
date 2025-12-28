package app.revanced.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import app.revanced.manager.service.InstallService

@Suppress("DEPRECATION")
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val extraStatusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val extraPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        Log.d("InstallReceiver", "onReceive(status=$extraStatus, pkg=$extraPackageName, msg=${extraStatusMessage?.take(120)})")

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
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    if (!tryStartUserAction(context, fallback)) {
                        sendResultBroadcast(
                            context = context,
                            status = PackageInstaller.STATUS_FAILURE_BLOCKED,
                            statusMessage = extraStatusMessage ?: "Unable to launch installer confirmation.",
                            packageName = extraPackageName
                        )
                    }
                }
            }

            else -> {
                sendResultBroadcast(
                    context = context,
                    status = extraStatus,
                    statusMessage = extraStatusMessage,
                    packageName = extraPackageName
                )
            }
        }
    }

    private fun sendResultBroadcast(
        context: Context,
        status: Int,
        statusMessage: String?,
        packageName: String?
    ) {
        context.sendBroadcast(
            Intent().apply {
                action = InstallService.APP_INSTALL_ACTION
                setPackage(context.packageName)
                putExtra(InstallService.EXTRA_INSTALL_STATUS, status)
                putExtra(InstallService.EXTRA_INSTALL_STATUS_MESSAGE, statusMessage)
                putExtra(InstallService.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    private fun tryStartUserAction(context: Context, action: Intent?): Boolean {
        if (action == null) return false
        return runCatching {
            action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(action)
        }.onFailure {
            Log.w("InstallReceiver", "Failed to start installer user action", it)
        }.isSuccess
    }
}
