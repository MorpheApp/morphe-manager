/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import android.os.Build
import app.morphe.manager.domain.apk.ApkSignatureCheck.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The rule that decides whether an APK may be patched unasked. It is the only thing standing
 * between a file handed over by another app and the patcher, so a wrong answer either strands a
 * legitimate download behind a warning or lets a modified APK through unremarked.
 */
class ApkSignatureCheckTest {
    private val declared = setOf("aa", "bb")
    private val current = Build.VERSION_CODES.TIRAMISU

    private fun check(sdkInt: Int = current, declared: Set<String>? = this.declared, hashes: Set<String>?) =
        apkSignatureCheck(sdkInt, declared) { hashes }

    @Test
    fun `an APK signed with one of the declared certificates is taken as is`() {
        assertEquals(ACCEPTED, check(hashes = setOf("bb")))
    }

    @Test
    fun `an APK signed by anyone else is held back as another signer`() {
        assertEquals(OTHER_SIGNER, check(hashes = setOf("cc")))
    }

    @Test
    fun `an archive whose signature does not verify is held back as broken`() {
        assertEquals(BROKEN, check(hashes = emptySet()))
    }

    @Test
    fun `an archive that could not be opened is not held against it`() {
        assertEquals(ACCEPTED, check(hashes = null))
    }

    @Test
    fun `an app no source declares certificates for has nothing to be checked against`() {
        assertEquals(ACCEPTED, check(declared = null, hashes = setOf("cc")))
        assertEquals(ACCEPTED, check(declared = emptySet(), hashes = setOf("cc")))
    }

    @Test
    fun `Android 10 and below cannot read certificates from an archive, so nothing is claimed`() {
        assertEquals(ACCEPTED, check(sdkInt = Build.VERSION_CODES.Q, hashes = setOf("cc")))
        assertEquals(ACCEPTED, check(sdkInt = Build.VERSION_CODES.P, hashes = emptySet()))
    }

    @Test
    fun `certificates are not read where nothing is checked`() {
        // Reading them unpacks a split archive, which is wasted where the answer is already known
        apkSignatureCheck(Build.VERSION_CODES.Q, declared) { fail("read on Android 10") }
        apkSignatureCheck(current, null) { fail("read without declared certificates") }
    }
}
