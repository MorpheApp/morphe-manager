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
import app.morphe.manager.domain.installer.RootInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

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
        val command = "pm set-app-links-user-selection --user cur --package $packageName true all && " +
            "pm set-app-links-allowed --user cur --package $packageName true"
        when (getRepairCapability()) {
            RepairCapability.SHIZUKU -> runCatching { runShizukuShell(command) == 0 }.getOrDefault(false)
            RepairCapability.ROOT -> rootInstaller.execute(command).isSuccess
            RepairCapability.NONE -> false
        }
    }

    private fun isShizukuGranted(): Boolean = runCatching {
        !Shizuku.isPreV11() &&
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Runs [command] as the Shizuku server's user. The library keeps `newProcess` private. */
    private fun runShizukuShell(command: String): Int {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
        val process = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        return process.waitFor()
    }
}
