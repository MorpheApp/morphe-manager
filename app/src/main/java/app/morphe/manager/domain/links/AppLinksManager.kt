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
import app.morphe.manager.util.openAppOpenByDefaultSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppLinksStatus(
    val packageName: String,
    val totalDomains: Int,
    val selectedDomains: Int,
    val domains: List<String>,
    val unhandledDomains: List<String>,
    val isLinkHandlingAllowed: Boolean
) {
    val hasSupportedLinks: Boolean get() = totalDomains > 0
    val isFullyConfigured: Boolean get() = hasSupportedLinks && isLinkHandlingAllowed && unhandledDomains.isEmpty()
    val needsAttention: Boolean get() = hasSupportedLinks && !isFullyConfigured
}

enum class RepairCapability {
    SHIZUKU,
    ROOT,
    NONE
}

class AppLinksManager(
    private val app: Application,
    private val rootInstaller: RootInstaller
) {
    /** Returns current repair capability (Shizuku, Root, or None). */
    fun getRepairCapability(): RepairCapability {
        val shizukuSupported = !runCatching { Shizuku.isPreV11() }.getOrDefault(true)
        val shizukuRunning = shizukuSupported && runCatching { Shizuku.pingBinder() }.getOrElse { false }
        val shizukuGranted = shizukuRunning && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrElse { false }

        if (shizukuGranted) return RepairCapability.SHIZUKU

        if (rootInstaller.hasRootAccess()) return RepairCapability.ROOT

        return RepairCapability.NONE
    }

    /** Returns the domain verification status for [packageName]. */
    fun getStatus(packageName: String): AppLinksStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dvm = app.getSystemService(DomainVerificationManager::class.java)
            val userState = runCatching { dvm?.getDomainVerificationUserState(packageName) }.getOrNull()
            if (userState != null) {
                val hostMap = userState.hostToStateMap
                val allDomains = hostMap.keys.toList().sorted()
                val isAllowed = userState.isLinkHandlingAllowed
                val selected = if (isAllowed) {
                    hostMap.count {
                        it.value == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                                it.value == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                    }
                } else {
                    0
                }
                val unhandled = if (isAllowed) {
                    hostMap.filter {
                        it.value == DomainVerificationUserState.DOMAIN_STATE_NONE
                    }.keys.toList().sorted()
                } else {
                    allDomains
                }

                return AppLinksStatus(
                    packageName = packageName,
                    totalDomains = hostMap.size,
                    selectedDomains = selected,
                    domains = allDomains,
                    unhandledDomains = unhandled,
                    isLinkHandlingAllowed = isAllowed
                )
            }
        }
        return AppLinksStatus(
            packageName = packageName,
            totalDomains = 0,
            selectedDomains = 0,
            domains = emptyList(),
            unhandledDomains = emptyList(),
            isLinkHandlingAllowed = true
        )
    }

    /**
     * Repairs app links by enabling user selection for all supported domains.
     * Uses Shizuku if available, otherwise Root if granted.
     * Returns true if the repair commands succeeded.
     */
    suspend fun repairAppLinks(packageName: String, domains: List<String> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        when (getRepairCapability()) {
            RepairCapability.SHIZUKU -> repairWithShizuku(packageName, domains)
            RepairCapability.ROOT -> repairWithRoot(packageName, domains)
            RepairCapability.NONE -> false
        }
    }

    private fun repairWithShizuku(packageName: String, domains: List<String>): Boolean {
        return runCatching {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val cmd = "pm set-app-links-user-selection --user cur --package $packageName true all && pm set-app-links-allowed --user cur --package $packageName true"
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val exitCode = process.waitFor()
            if (exitCode != 0 && domains.isNotEmpty()) {
                val domainArgs = domains.joinToString(" ")
                val fallbackCmd = "pm set-app-links-user-selection --user cur --package $packageName true $domainArgs && pm set-app-links-allowed --user cur --package $packageName true"
                val fallbackProc = newProcessMethod.invoke(null, arrayOf("sh", "-c", fallbackCmd), null, null) as Process
                fallbackProc.waitFor() == 0
            } else {
                exitCode == 0
            }
        }.getOrDefault(false)
    }

    private suspend fun repairWithRoot(packageName: String, domains: List<String>): Boolean {
        val cmdUser = "pm set-app-links-user-selection --user cur --package $packageName true all"
        val cmdAllowed = "pm set-app-links-allowed --user cur --package $packageName true"
        val res1 = rootInstaller.execute(cmdUser)
        val res2 = rootInstaller.execute(cmdAllowed)
        if (!res1.isSuccess && domains.isNotEmpty()) {
            val domainArgs = domains.joinToString(" ")
            val fallbackRes = rootInstaller.execute("pm set-app-links-user-selection --user cur --package $packageName true $domainArgs")
            return fallbackRes.isSuccess || res2.isSuccess
        }
        return res1.isSuccess || res2.isSuccess
    }

    /** Opens Android's native "Open by default" app links settings for [packageName]. */
    fun openSettings(packageName: String): Boolean {
        return app.openAppOpenByDefaultSettings(packageName)
    }
}
