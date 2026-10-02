/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.repository

import app.morphe.manager.ui.model.SelectedApp
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginalApkStagingTest {
    private val dir = Files.createTempDirectory("morphe-original-apk").toFile()
    private val target = dir.resolve("com.example_1.0_original.apk")

    private fun stagedCopies() = dir.listFiles { file -> file.name.endsWith(STAGING_SUFFIX) }.orEmpty()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a completed copy replaces the archive that was there`() {
        target.writeText("old archive")
        val source = dir.resolve("merged.apk").apply { writeText("new archive") }

        copyThroughStaging(source, target)

        assertEquals("new archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a copy that fails leaves the previous archive in place`() {
        target.writeText("old archive")
        val unreadableSource = dir.resolve("missing.apk")

        assertFails { copyThroughStaging(unreadableSource, target) }

        assertEquals("old archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a moved file replaces the archive and leaves nothing at its old path`() {
        target.writeText("old archive")
        val source = dir.resolve("merged.apk").apply { writeText("new archive") }

        moveIntoPlace(source, target)

        assertEquals("new archive", target.readText())
        assertFalse(source.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a move that fails leaves the previous archive in place`() {
        target.writeText("old archive")
        val missingSource = dir.resolve("missing.apk")

        assertFails { moveIntoPlace(missingSource, target) }

        assertEquals("old archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `a copy that fails without an archive to replace leaves nothing behind`() {
        val unreadableSource = dir.resolve("missing.apk")

        assertFails { copyThroughStaging(unreadableSource, target) }

        assertTrue(!target.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `temporary input moves the file into place and leaves no file at its old path`() {
        val temporarySource = dir.resolve("temporary.apk").apply { writeText("temporary archive") }

        retainOriginalApk(temporarySource, target, moveSource = true)

        assertEquals("temporary archive", target.readText())
        assertFalse(temporarySource.exists())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `non-temporary input copies through staging and preserves the source file intact`() {
        val persistentSource = dir.resolve("user_picked.apk").apply { writeText("persistent archive") }

        retainOriginalApk(persistentSource, target, moveSource = false)

        assertEquals("persistent archive", target.readText())
        assertTrue(persistentSource.exists())
        assertEquals("persistent archive", persistentSource.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `retention does nothing when source is already at target path`() {
        target.writeText("existing archive")

        retainOriginalApk(target, target, moveSource = true)

        assertEquals("existing archive", target.readText())
        assertTrue(stagedCopies().isEmpty())
    }

    @Test
    fun `temporary local app input is recognized for move while user file is preserved`() {
        val tempFile = dir.resolve("temp_app.apk").apply { writeText("temp app") }
        val temporaryApp: SelectedApp = SelectedApp.Local(
            packageName = "app.example.temp",
            version = "1.0",
            file = tempFile,
            temporary = true
        )
        val shouldMoveTemp = (temporaryApp as? SelectedApp.Local)?.temporary == true
        assertTrue(shouldMoveTemp)
        retainOriginalApk(tempFile, target, moveSource = shouldMoveTemp)
        assertFalse(tempFile.exists())
        assertEquals("temp app", target.readText())

        val userFile = dir.resolve("user_app.apk").apply { writeText("user app") }
        val userApp: SelectedApp = SelectedApp.Local(
            packageName = "app.example.user",
            version = "1.0",
            file = userFile,
            temporary = false
        )
        val shouldMoveUser = (userApp as? SelectedApp.Local)?.temporary == true
        assertFalse(shouldMoveUser)
        val userTarget = dir.resolve("com.example.user_1.0_original.apk")
        retainOriginalApk(userFile, userTarget, moveSource = shouldMoveUser)
        assertTrue(userFile.exists())
        assertEquals("user app", userTarget.readText())
    }

    @Test
    fun `installed app input is not marked temporary and is never moved`() {
        val installedApp: SelectedApp = SelectedApp.Installed(
            packageName = "app.example.installed",
            version = "1.0"
        )
        val shouldMoveInstalled = (installedApp as? SelectedApp.Local)?.temporary == true
        assertFalse(shouldMoveInstalled)
    }
}
