/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ApkFileStampTest {
    @Test
    fun `rewriting an APK invalidates the stamp captured before verification`() {
        val file = Files.createTempFile("morphe-apk-stamp", ".apk").toFile()
        try {
            file.writeText("old")
            val before = file.apkFileStampOrNull()

            file.writeText("new archive contents")
            val after = file.apkFileStampOrNull()

            assertNotEquals(before, after)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing files cannot identify a cache entry`() {
        val missing = Files.createTempDirectory("morphe-apk-stamp").resolve("missing.apk").toFile()
        try {
            assertNull(missing.apkFileStampOrNull())
        } finally {
            missing.parentFile?.delete()
        }
    }
}
