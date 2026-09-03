/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import android.content.Intent
import android.os.DeadObjectException
import android.os.Parcel
import android.os.RemoteException
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui

/**
 * Resolves the privileged provider backing Shizuku IPC and performs the binder bootstrap
 * [SessionInstaller] and [ShizukuInstaller] depend on.
 *
 * Nothing here looks up the canonical package name directly, since none of the providers reliably
 * owns it: Sui has no package, Shizuku's stealth mode renames its own, and Shizuku+ keeps a
 * separate one so it can sit next to stock Shizuku.
 */
class ShizukuEnvironment(private val app: Application) {

    /** Privileged provider serving the binder, used for labeling and for opening its UI. */
    enum class Flavor {
        Shizuku,
        ShizukuPlus,
        Sui
    }

    init {
        // Sui injects the binder into every process itself; Shizuku only pushes it to processes
        // hosting a ShizukuProvider, so anything else has to ask for it explicitly.
        val isSui = Sui.init(app.packageName)
        if (!isSui) {
            runCatching { ShizukuProvider.requestBinderForNonProviderProcess(app) }
        }
    }

    val isSui: Boolean
        get() = runCatching { Sui.isSui() }.getOrDefault(false)

    /** Returns true when Shizuku, Shizuku+ or Sui is present on the device. */
    fun isInstalled(): Boolean = isSui || candidatePackages().isNotEmpty()

    /** Returns the [Flavor] serving this device. */
    fun flavor(): Flavor = if (isSui) Flavor.Sui else flavorOf(candidatePackages())

    /** Returns the package whose UI represents the provider, or null when none is installed. */
    fun providerPackageName(flavor: Flavor = flavor()): String? =
        if (isSui) SHIZUKU_PACKAGE else orderedFor(flavor, candidatePackages()).firstOrNull()

    /** Opens the provider's own UI. Returns false when nothing installed can be launched. */
    fun launchManager(flavor: Flavor = flavor()): Boolean {
        val intent = orderedFor(flavor, candidatePackages())
            .firstNotNullOfOrNull { app.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return true
    }

    /** Packages that could be serving this device, in no particular order. */
    private fun candidatePackages(): List<String> = listOfNotNull(
        SHIZUKU_PLUS_PACKAGE.takeIf { isPackageInstalled(it) },
        permissionInfo()?.packageName
    ).distinct()

    /**
     * Settles which of [candidates] is serving. Two managers can be installed while only one of
     * their servers runs, and nothing but the server itself can say which - so that case, and only
     * that case, is worth a round trip to ask.
     */
    private fun flavorOf(candidates: List<String>): Flavor {
        val installed = if (SHIZUKU_PLUS_PACKAGE in candidates || isPlusDropIn()) {
            Flavor.ShizukuPlus
        } else {
            Flavor.Shizuku
        }
        if (candidates.size < 2) return installed
        return when (isServerShizukuPlus()) {
            true -> Flavor.ShizukuPlus
            false -> Flavor.Shizuku
            null -> installed
        }
    }

    /**
     * Orders [candidates] with the one representing [flavor] first. The canonical name can belong
     * to a Shizuku+ companion, a stub with no launcher and no icon, so it is not always the better
     * answer even when it is the package declaring the permission.
     */
    private fun orderedFor(flavor: Flavor, candidates: List<String>): List<String> {
        val (plus, rest) = candidates.partition { it == SHIZUKU_PLUS_PACKAGE }
        return if (flavor == Flavor.ShizukuPlus) plus + rest else rest + plus
    }

    /**
     * Asks the connected server for its Shizuku+ patch version, a transaction stock Shizuku does
     * not implement. Returns null only when no server is connected, leaving the caller to guess.
     */
    private fun isServerShizukuPlus(): Boolean? {
        val binder = runCatching { Shizuku.getBinder() }.getOrNull() ?: return null
        if (!binder.isBinderAlive) return null

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SHIZUKU_SERVICE_DESCRIPTOR)
            // A handled code answers true even if the reply carries an error, and only Shizuku+
            // handles this one, so the return value alone identifies the server.
            binder.transact(TRANSACTION_SERVER_PATCH_VERSION, data, reply, 0)
        } catch (_: DeadObjectException) {
            null
        } catch (_: RemoteException) {
            false
        } catch (_: RuntimeException) {
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * Returns the permission declared by the active provider, or null when none is installed.
     * Whichever package defines it is serving the device, stealth mode's random name included.
     */
    @Suppress("DEPRECATION")
    private fun permissionInfo() = runCatching {
        app.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
    }.getOrNull()

    /**
     * Detects the Shizuku+ drop-in build, which is otherwise indistinguishable from stock Shizuku.
     * Only it claims the bare canonical authority alongside the suffixed one stock declares.
     */
    @Suppress("DEPRECATION")
    private fun isPlusDropIn(): Boolean = runCatching {
        app.packageManager.resolveContentProvider(SHIZUKU_PACKAGE, 0) != null
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        app.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)

    companion object {
        /** Canonical Shizuku package, also claimed by the Shizuku+ drop-in build and its companion. */
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        /** Shizuku+ manager, kept under its own name so it can coexist with stock Shizuku. */
        const val SHIZUKU_PLUS_PACKAGE = "af.shizuku.plus.api"

        /** Descriptor every Shizuku-compatible server answers to, Shizuku+ included. */
        private const val SHIZUKU_SERVICE_DESCRIPTOR = "moe.shizuku.server.IShizukuService"

        /** Shizuku+ `ServerConstants.BINDER_TRANSACTION_getServerPatchVersion`. */
        private const val TRANSACTION_SERVER_PATCH_VERSION = 10004
    }
}
