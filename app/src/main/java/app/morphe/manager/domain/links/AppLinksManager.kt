/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.links

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import android.util.Log
import app.morphe.manager.domain.installer.RootInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Where the web links an app declares stand for the current user. [unhandledDomains] are the ones
 * that open in the browser, which is every declared domain while link handling is off.
 */
data class AppLinksStatus(
    val domains: List<String>,
    val unhandledDomains: List<String>
) {
    val hasSupportedLinks: Boolean get() = domains.isNotEmpty()
    val isFullyConfigured: Boolean get() = hasSupportedLinks && unhandledDomains.isEmpty()
    val needsAttention: Boolean get() = hasSupportedLinks && unhandledDomains.isNotEmpty()

    /**
     * Whether no link at all reaches the app. Apps routinely declare service domains next to their
     * own, so a few of those left unselected is not worth warning about.
     */
    val opensInBrowser: Boolean get() = hasSupportedLinks && unhandledDomains.size == domains.size

    companion object {
        val None = AppLinksStatus(emptyList(), emptyList())
    }
}

/** The privileged channel able to change the link selection on the user's behalf. */
enum class RepairCapability {
    SHIZUKU,
    ROOT,
    NONE
}

/**
 * Reads and restores the web link selection of installed apps. A re-signed app loses the domain
 * verification of its original publisher on Android 12+, so its links open in the browser until
 * the user selects the domains for it.
 */
class AppLinksManager(
    private val app: Application,
    private val rootInstaller: RootInstaller
) {
    /** Returns the channel [repairAppLinks] would use. Probes root, so never call it on the main thread. */
    suspend fun getRepairCapability(): RepairCapability = withContext(Dispatchers.IO) {
        when {
            isShizukuGranted() -> RepairCapability.SHIZUKU
            rootInstaller.hasRootAccess() -> RepairCapability.ROOT
            else -> RepairCapability.NONE
        }
    }

    /** Returns the link selection of [packageName], or [AppLinksStatus.None] before Android 12. */
    fun getStatus(packageName: String): AppLinksStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return AppLinksStatus.None
        val userState = runCatching {
            app.getSystemService(DomainVerificationManager::class.java)
                ?.getDomainVerificationUserState(packageName)
        }.getOrNull() ?: return AppLinksStatus.None

        val hostStates = userState.hostToStateMap
        val domains = hostStates.keys.sorted()
        return AppLinksStatus(
            domains = domains,
            unhandledDomains = if (userState.isLinkHandlingAllowed) {
                domains.filter { hostStates[it] == DomainVerificationUserState.DOMAIN_STATE_NONE }
            } else {
                domains
            }
        )
    }

    /**
     * Selects every declared domain of [packageName] and turns its link handling on. Returns true
     * only when both took effect.
     */
    suspend fun repairAppLinks(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val commands = listOf(
            listOf("set-app-links-user-selection", "--user", "cur", "--package", packageName, "true", "all"),
            listOf("set-app-links-allowed", "--user", "cur", "--package", packageName, "true")
        )
        when (getRepairCapability()) {
            RepairCapability.SHIZUKU -> runCatching { commands.all(::runShizukuPackageCommand) }
                .onFailure { Log.e(TAG, "Could not repair app links of $packageName through Shizuku", it) }
                .getOrDefault(false)
            RepairCapability.ROOT -> {
                val result = rootInstaller.execute(
                    commands.joinToString(" && ") { "pm " + it.joinToString(" ") }
                )
                if (!result.isSuccess) Log.e(TAG, "Could not repair app links of $packageName as root: ${result.err}")
                result.isSuccess
            }
            RepairCapability.NONE -> false
        }
    }

    private fun isShizukuGranted(): Boolean = runCatching {
        !Shizuku.isPreV11() &&
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Runs a `pm` command as the Shizuku server's user by handing it straight to the package
     * service, the way `cmd package` does. Avoids spawning a process, which some Shizuku forks
     * refuse or rewrite. Returns whether the command exited with 0.
     */
    private fun runShizukuPackageCommand(args: List<String>): Boolean {
        val binder = ShizukuBinderWrapper(
            SystemServiceHelper.getSystemService("package") ?: error("Package service unavailable")
        )
        val exitCode = CompletableFuture<Int>()
        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                exitCode.complete(resultCode)
            }
        }
        val (errorRead, errorWrite) = ParcelFileDescriptor.createPipe()
        val nullInput = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
        val nullOutput = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            // Laid out as Binder.onTransact reads a shell command: the three streams, the
            // arguments, a ShellCallback (none) and the receiver of the exit code
            data.writeFileDescriptor(nullInput.fileDescriptor)
            data.writeFileDescriptor(nullOutput.fileDescriptor)
            data.writeFileDescriptor(errorWrite.fileDescriptor)
            data.writeStringArray(args.toTypedArray())
            data.writeStrongBinder(null)
            resultReceiver.writeToParcel(data, 0)
            binder.transact(SHELL_COMMAND_TRANSACTION, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
            nullInput.close()
            nullOutput.close()
            errorWrite.close()
        }

        val code = exitCode.get(SHELL_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (code != 0) {
            val error = ParcelFileDescriptor.AutoCloseInputStream(errorRead).bufferedReader().use { it.readText() }
            Log.e(TAG, "pm ${args.first()} exited with $code: $error")
        } else {
            errorRead.close()
        }
        return code == 0
    }

    private companion object {
        const val TAG = "Morphe AppLinksManager"

        /** `IBinder.SHELL_COMMAND_TRANSACTION`, hidden from the SDK. */
        const val SHELL_COMMAND_TRANSACTION =
            ('_'.code shl 24) or ('C'.code shl 16) or ('M'.code shl 8) or 'D'.code

        const val SHELL_COMMAND_TIMEOUT_SECONDS = 10L
    }
}
