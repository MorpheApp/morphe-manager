/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.*

class FileUtilsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test
    fun `purgeDirectoryAsync renames and clears existing directory immediately`() = runBlocking {
        val dir = tempFolder.newFolder("ephemeral")
        val oldFile = File(dir, "old_patch.apk").apply { writeText("old data") }
        val oldSubDir = File(dir, "cache").apply { mkdirs() }
        File(oldSubDir, "old_dex.dex").writeText("old dex")

        assertTrue(oldFile.exists())

        val job = dir.purgeDirectoryAsync(testScope)

        // Directory must exist and be empty immediately on the calling thread
        assertTrue(dir.exists(), "Purged directory must immediately exist")
        val immediateFiles = dir.listFiles()
        assertNotNull(immediateFiles)
        assertTrue(immediateFiles.isEmpty(), "Purged directory must be empty immediately")

        job.join()

        // After background deletion finishes, directory is still valid and empty
        assertTrue(dir.exists())
        assertEquals(0, dir.listFiles()?.size)
    }

    @Test
    fun `purgeDirectoryAsync preserves new files written immediately after call`() = runBlocking {
        val dir = tempFolder.newFolder("installer")
        File(dir, "previous_run.apk").writeText("old")

        val job = dir.purgeDirectoryAsync(testScope)

        // Simulate immediate downstream write into the clean directory
        val newFile = File(dir, "new_output.apk")
        newFile.writeText("fresh payload")

        job.join()

        // The newly written file must survive background deletion of the old directory
        assertTrue(newFile.exists(), "New file written into purged directory must not be deleted")
        assertEquals("fresh payload", newFile.readText())
    }

    @Test
    fun `purgeDirectoryAsync handles non-existent directory cleanly`() = runBlocking {
        val nonExistent = File(tempFolder.root, "non_existent_dir")
        assertFalse(nonExistent.exists())

        val job = nonExistent.purgeDirectoryAsync(testScope)

        assertTrue(nonExistent.exists(), "Non-existent directory should be created")
        job.join()
        assertTrue(nonExistent.exists())
    }

    @Test
    fun `purgeDirectoryAsync cleans up stale trash directories from previous crashed runs`() = runBlocking {
        val dir = tempFolder.newFolder("target_dir")
        val parent = dir.parentFile!!
        val staleTrash = File(parent, "${dir.name}_trash_12345678").apply {
            mkdirs()
            File(this, "leaked_payload.apk").writeText("leaked")
        }
        assertTrue(staleTrash.exists())

        val job = dir.purgeDirectoryAsync(testScope)
        job.join()

        assertFalse(staleTrash.exists(), "Stale trash directories should be swept during purge")
    }
}
