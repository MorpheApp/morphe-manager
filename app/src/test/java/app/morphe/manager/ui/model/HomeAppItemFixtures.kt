/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.model

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp

/** A card with every flag down, to be adjusted through [HomeAppItem.copy] to what a test is about. */
internal fun homeAppItem(
    id: String = "app.example",
    packageName: String = "app.example",
    displayName: String = "Example",
    installedApp: InstalledApp? = null
) = HomeAppItem(
    id = id,
    packageName = packageName,
    displayName = displayName,
    gradientColors = emptyList(),
    installedApp = installedApp,
    packageInfo = null,
    version = "",
    isPinnedByDefault = false,
    isInstalledOnDevice = false,
    isDeleted = false,
    isInstallStateNotPatched = false,
    isInstallStateUnknown = false,
    isInstallStatePending = false,
    savedApkFile = null,
    hasUpdate = false,
    versionStatus = null,
    patchCount = 0,
    isClone = false
)

/** The card of a settled install of `app.example` under [id], a copy of it where [isClone]. */
internal fun patchedAppItem(id: String = "app.example", isClone: Boolean = false) = homeAppItem(
    id = id,
    installedApp = InstalledApp(
        currentPackageName = id,
        originalPackageName = "app.example",
        version = "1.0",
        installType = InstallType.DEFAULT
    )
).copy(isInstalledOnDevice = true, isClone = isClone)
