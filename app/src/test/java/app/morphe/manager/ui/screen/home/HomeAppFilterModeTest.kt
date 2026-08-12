/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import app.morphe.manager.data.room.apps.installed.InstallType
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.ui.model.HomeAppItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAppFilterModeTest {
    @Test
    fun `confirmed replacement is historically patched and currently installed`() {
        val item = HomeAppItem(
            packageName = "app.example",
            displayName = "Example",
            gradientColors = emptyList(),
            installedApp = InstalledApp(
                currentPackageName = "app.example",
                originalPackageName = "app.example",
                version = "1.0",
                installType = InstallType.DEFAULT
            ),
            packageInfo = null,
            isPinnedByDefault = false,
            isInstalledOnDevice = true,
            isDeleted = false,
            isInstallStateNotPatched = true,
            isInstallStateUnknown = false,
            isInstallStatePending = false,
            savedApkFile = null,
            hasUpdate = false,
            patchCount = 0
        )

        assertTrue(HomeAppFilterMode.PATCHED.matches(item))
        assertTrue(HomeAppFilterMode.INSTALLED.matches(item))
        assertFalse(HomeAppFilterMode.NOT_PATCHED.matches(item))
        assertFalse(HomeAppFilterMode.UNINSTALLED.matches(item))
    }

    @Test
    fun `pending verification stays physically installed without showing an update verdict`() {
        val item = HomeAppItem(
            packageName = "app.example",
            displayName = "Example",
            gradientColors = emptyList(),
            installedApp = InstalledApp(
                currentPackageName = "app.example",
                originalPackageName = "app.example",
                version = "1.0",
                installType = InstallType.DEFAULT
            ),
            packageInfo = null,
            isPinnedByDefault = false,
            isInstalledOnDevice = true,
            isDeleted = false,
            isInstallStateNotPatched = false,
            isInstallStateUnknown = false,
            isInstallStatePending = true,
            savedApkFile = null,
            hasUpdate = true,
            patchCount = 0
        )

        assertTrue(HomeAppFilterMode.INSTALLED.matches(item))
        assertFalse(HomeAppFilterMode.UNINSTALLED.matches(item))
        assertFalse(item.showsUpdateBadge)
    }
}
