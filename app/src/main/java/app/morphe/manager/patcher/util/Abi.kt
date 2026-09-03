/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.util

import app.morphe.patcher.patch.ApkArchitecture
import java.util.Locale

/**
 * The ABIs native code is built for, named as Android names them: the directory under lib/, the
 * config split that ships it, and the entries of [android.os.Build.SUPPORTED_ABIS].
 *
 * One vocabulary for everything that has to recognize an ABI: reading it out of an APK, matching
 * it in the name of a split module, asking what the device itself runs, and telling patches which
 * architecture a run produces.
 */
object Abi {
    /**
     * Every ABI, ordered by the preference a run gives them when the device itself runs none.
     * A name always precedes the ones it contains, so armeabi-v7a and x86_64 are never claimed
     * by armeabi and x86.
     */
    val NAMES = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "riscv64")

    // Longest name first, so a name is matched before the shorter one it contains
    private val LONGEST_FIRST = NAMES.sortedByDescending { it.length }

    /**
     * The architecture patches declare their availability against for [name], or null for an ABI
     * that has none. Legacy armeabi counts as the 32 bit ARM target, riscv64 as no target at all.
     */
    fun architectureOf(name: String): ApkArchitecture? = when (name.lowercase(Locale.ROOT)) {
        "arm64-v8a" -> ApkArchitecture.ARM64_V8A
        "armeabi-v7a", "armeabi" -> ApkArchitecture.ARMEABI_V7A
        "x86_64" -> ApkArchitecture.X86_64
        "x86" -> ApkArchitecture.X86
        else -> null
    }

    /** The ABI [text] names, or null when it names none. The longest match wins. */
    fun namedIn(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        return LONGEST_FIRST.firstOrNull { abi -> tokensOf(abi).any { it in lower } }
    }

    /** The spellings [name] takes in the name of a split module, which differ in their separator. */
    fun tokensOf(name: String): Set<String> {
        val normalized = name.lowercase(Locale.ROOT)
        return setOf(normalized, normalized.replace('-', '_'), normalized.replace('_', '-'))
    }
}
