/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The package name ends up in module paths and in shell commands run as root. */
class RootInstallerPackageNameTest {
    @Test
    fun `ordinary package names are accepted`() {
        assertTrue(RootInstaller.isValidPackageName("com.google.android.youtube"))
        assertTrue(RootInstaller.isValidPackageName("app.revanced.android_apps.v2"))
    }

    @Test
    fun `names with shell or path characters are rejected`() {
        listOf(
            "",
            "test;rm -rf /",
            "com.example app",
            "com.example\$(id)",
            "com.example'quoted",
            "../escape",
            "com/example",
            "com.example\n"
        ).forEach { assertFalse(RootInstaller.isValidPackageName(it), it) }
    }
}
