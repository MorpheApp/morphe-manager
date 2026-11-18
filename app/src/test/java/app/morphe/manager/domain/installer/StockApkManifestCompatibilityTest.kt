/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The signed fixture uses HEX integer fields and a resource reference for its version name. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class StockApkManifestCompatibilityTest {
    @Test
    fun `Android resolves the version name of a fully verified private base copy`() {
        val directory = Files.createTempDirectory("stock-manifest-compatibility-").toFile()
        try {
            val apk = File(directory, "base.apk")
            checkNotNull(javaClass.getResourceAsStream("/stock-apk-integrity/hex-resource-version.apk")).use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
            val verified = assertNotNull(verifiedStockSnapshot(apk, 36))
            assertEquals((1L shl 32) or 42L, verified.identity.versionCode)
            // The raw manifest reader cannot resolve an app resource table's string reference.
            assertNull(verified.identity.versionName)
            val metadata = assertNotNull(RuntimeEnvironment.getApplication().packageManager.getPackageArchiveInfo(apk.path, 0))
            assertEquals("compatibility 1.0", metadata.versionName)
            assertEquals(verified.identity.versionCode, metadata.longVersionCode)

            val identity = assertNotNull(resolvedStockBaseIdentity(
                verified.identity, metadata.packageName, metadata.longVersionCode, metadata.versionName
            ))
            assertTrue(matchesInstalledStockIdentity(
                identity, metadata.packageName, metadata.longVersionCode, "compatibility 1.0", null
            ))
            assertNull(resolvedStockBaseIdentity(
                verified.identity, "another.package", metadata.longVersionCode, metadata.versionName
            ))
            assertNull(resolvedStockBaseIdentity(
                verified.identity, metadata.packageName, metadata.longVersionCode + 1, metadata.versionName
            ))
        } finally {
            directory.deleteRecursively()
        }
    }
}
