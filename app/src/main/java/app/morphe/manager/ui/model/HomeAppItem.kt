/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import android.content.pm.PackageInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.domain.bundles.AppVersionStatus
import app.morphe.patcher.patch.FormFactor
import java.io.File

/**
 * One card on the home screen: either an app, or one of the installs it was cloned into.
 *
 * Cloning gives an app a second install under a package name of its own, so a card is identified
 * by [id] rather than by [packageName], which several cards of the same app share and which the
 * bundle data is keyed by.
 */
@Immutable
data class HomeAppItem(
    val id: String,
    val packageName: String,
    val displayName: String,
    val gradientColors: List<Color>,
    val installedApp: InstalledApp?,
    val packageInfo: PackageInfo?,
    /** Installed version from the device, else the one the record kept. */
    val version: String,
    val isPinnedByDefault: Boolean,
    val isInstalledOnDevice: Boolean,
    val isDeleted: Boolean,
    val isInstallStateNotPatched: Boolean,
    val isInstallStateUnknown: Boolean,
    val isInstallStatePending: Boolean,
    val savedApkFile: File?,
    val hasUpdate: Boolean,
    val versionStatus: AppVersionStatus?,
    val patchCount: Int,
    val isClone: Boolean,
    /** What tells the card from another app of the same name, see [withNameSuffixes]. */
    val nameSuffix: String? = null,
    /** Device the sources say the app is built for, null where they do not say. */
    val formFactor: FormFactor? = null
) {
    val hasSavedCopy: Boolean get() = savedApkFile != null

    /**
     * What the card says about the app, the most pressing state first. A record that is gone,
     * replaced or unchecked keeps its update flags but is described by the state it is in, and a
     * newer app version leads newer patches, as only it asks for a newer APK.
     *
     * An install past everything the sources cover has already lost its patches, so only the
     * rebuildable end of [versionStatus] counts here.
     *
     * Settled once per item, as cards read it on every recomposition and sorting on every comparison.
     */
    val status: HomeAppStatus = run {
        val newerVersion = versionStatus?.takeIf { it.isBehind }?.supportedVersion
        when {
            installedApp == null -> HomeAppStatus.NotPatchedYet
            isInstallStatePending -> HomeAppStatus.Pending
            isInstallStateNotPatched -> HomeAppStatus.UnpatchedInstall
            isDeleted -> HomeAppStatus.Uninstalled
            isInstallStateUnknown -> HomeAppStatus.Unverified
            newerVersion != null -> HomeAppStatus.NewerVersion(newerVersion)
            hasUpdate -> HomeAppStatus.NewerPatches
            else -> HomeAppStatus.Patched(version)
        }
    }

    /**
     * Whether newer patches wait for a settled install. Rebuilding at a version the sources still
     * cover returns the install as it went in, so queueing and sorting go by this rather than by
     * every rebuild the card offers.
     */
    val isPatchUpdatePending: Boolean get() = hasUpdate && status.offersRebuild
}

/**
 * These cards with a [HomeAppItem.nameSuffix] where different apps share a name, such as two builds
 * of Telegram. Clones of one app share its package, so they are left as they are.
 */
fun List<HomeAppItem>.withNameSuffixes(): List<HomeAppItem> {
    // One map of first packages finds the shared names, so a list with none builds nothing more
    val firstPackages = HashMap<String, String>(size * 2)
    val sharedNames = HashSet<String>()
    for ((_, packageName, displayName) in this) {
        val first = firstPackages.putIfAbsent(displayName, packageName)
        if (first != null && first != packageName) sharedNames += displayName
    }
    if (sharedNames.isEmpty()) return this

    val packagesByName = HashMap<String, MutableSet<String>>()
    for ((_, packageName, displayName) in this) {
        if (displayName in sharedNames) {
            packagesByName.getOrPut(displayName) { HashSet() } += packageName
        }
    }

    return map { item ->
        val namesakes = packagesByName[item.displayName] ?: return@map item
        item.copy(nameSuffix = packageNameSuffix(item.packageName, namesakes - item.packageName))
    }
}

/**
 * The segments of [packageName] that none of [others] has in the same place, or null where there
 * are none, as for the package the others only extend: `web` for `org.telegram.messenger.web`
 * against `org.telegram.messenger`, and nothing the other way round.
 */
internal fun packageNameSuffix(packageName: String, others: Collection<String>): String? {
    val otherSegments = others.map { it.split('.') }
    return packageName.split('.')
        .filterIndexed { index, segment -> otherSegments.none { it.getOrNull(index) == segment } }
        .joinToString(".")
        .ifEmpty { null }
}

/**
 * What a home screen card is about, before anything is read off the device to describe it.
 *
 * @param isClone Whether the card is about a copy of the app rather than about the app itself.
 */
data class HomeAppSlot(
    val id: String,
    val packageName: String,
    val installedApp: InstalledApp?,
    val isClone: Boolean
)

/**
 * The cards of every app in [packageNames], then of every app [records] were patched from that no
 * source lists anymore, so its installs stay in reach.
 *
 * A record patched from a build that was itself patched names that build's package as its app,
 * which is the package the build's own card already goes by. Such an app gets no card of its own
 * and its installs are shown by their own packages, so no two cards share an id.
 */
fun homeAppSlots(packageNames: Collection<String>, records: List<InstalledApp>): List<HomeAppSlot> {
    val recordsByApp = records.groupBy { it.originalPackageName }
    val patchedPackages = records
        .filter { it.currentPackageName != it.originalPackageName }
        .mapTo(HashSet()) { it.currentPackageName }

    return (packageNames + recordsByApp.keys).distinct().flatMap { packageName ->
        val appRecords = recordsByApp[packageName].orEmpty()
        if (packageName in patchedPackages) {
            appRecords
                .sortedBy { it.currentPackageName }
                .map { HomeAppSlot(it.currentPackageName, packageName, it, it.isClone) }
        } else {
            homeAppSlots(packageName, appRecords)
        }
    }
}

/**
 * The cards one app is shown as: the app itself, followed by every further install of it, ordered
 * by package name so the list does not move around between reads.
 *
 * A rename is not what gives a build a card of its own. Patches rename an app for reasons of
 * their own, and such a build is still the app's own install, so it belongs on the app's card,
 * which is where the user goes to rebuild it. The app keeps that card even once its only installs
 * are copies, because it is the only place another copy can be made from.
 */
fun homeAppSlots(packageName: String, records: List<InstalledApp>): List<HomeAppSlot> {
    // Two records can describe the app's own install, a mount and a renamed build side by side.
    // The card goes to the one answering to the app's name, and the other keeps a card of its own
    // rather than dropping out of reach of the actions that manage it
    val own = records.firstOrNull { !it.isClone && it.currentPackageName == packageName }
        ?: records.filterNot { it.isClone }.minByOrNull { it.currentPackageName }
    val separate = records.filter { it.currentPackageName != own?.currentPackageName }

    return buildList {
        add(HomeAppSlot(packageName, packageName, own, isClone = false))
        separate.sortedBy { it.currentPackageName }.forEach { record ->
            add(HomeAppSlot(record.currentPackageName, packageName, record, record.isClone))
        }
    }
}
