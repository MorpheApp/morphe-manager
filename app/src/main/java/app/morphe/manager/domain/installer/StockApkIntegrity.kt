/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.installer

import app.morphe.manager.patcher.split.SplitApkPreparer
import com.android.apksig.ApkVerifier
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.value.DataType
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Fully verifies the APK contents, without platform archive metadata or the signature cache. */
internal fun verifiedStockApkSigners(file: File, sdkInt: Int): Set<String> =
    runCatching {
        val result = ApkVerifier.Builder(file)
            .setMinCheckedPlatformVersion(sdkInt)
            .setMaxCheckedPlatformVersion(sdkInt)
            .build()
            .verify()
        // Historical lineage certificates are not the signer PackageManager currently records.
        if (result.isVerified) result.signerCertificates.mapTo(mutableSetOf()) { certificate ->
                MessageDigest.getInstance("SHA-256").digest(certificate.encoded).hexDigest()
            }
        else emptySet()
    }.getOrDefault(emptySet())

/** A signed copy whose contents stayed unchanged throughout verification. */
internal data class StockApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val splitName: String?
)

internal data class VerifiedStockSnapshot(
    val sha256: String,
    val signers: Set<String>,
    val identity: StockApkIdentity
)

/**
 * The package manager's parallel split names and source paths describe one installed set.
 * Keep their identities attached to each path so a stale root view cannot substitute another
 * validly signed split, or cause two snapshots to overwrite the same backup file.
 */
internal fun installedStockSourceSplits(
    basePath: String,
    splitNames: List<String>,
    splitPaths: List<String>
): Map<String, String?>? {
    if (basePath.isBlank() || splitNames.size != splitPaths.size ||
        splitNames.any { it.isBlank() } || splitNames.size != splitNames.toSet().size ||
        splitPaths.any { it.isBlank() || it == basePath } ||
        splitPaths.size != splitPaths.toSet().size ||
        splitPaths.map { File(it).name }.distinct().size != splitPaths.size
    ) return null
    return linkedMapOf<String, String?>(basePath to null).apply {
        splitNames.zip(splitPaths).forEach { (name, path) -> put(path, name) }
    }
}

/** Matches the captured manifest to the particular installed base or split it stands for. */
internal fun matchesInstalledStockIdentity(
    identity: StockApkIdentity,
    packageName: String,
    versionCode: Long,
    versionName: String,
    splitName: String?
): Boolean = identity.packageName == packageName && identity.versionCode == versionCode &&
        identity.splitName == splitName &&
        (splitName != null || identity.versionName == versionName)

/** Android resolves resource-valued versionName only after the private copy has verified. */
internal fun resolvedStockBaseIdentity(
    identity: StockApkIdentity,
    packageName: String,
    versionCode: Long,
    versionName: String?
): StockApkIdentity? = identity.takeIf {
    it.splitName == null && it.packageName == packageName && it.versionCode == versionCode
}?.copy(versionName = versionName)

/** Split variants of one release may use different codes; capture checks the installed code. */
internal fun canCaptureInstalledStockRelease(
    installed: StockApkIdentity?,
    original: StockApkIdentity?
): Boolean = installed != null && (original == null ||
        installed.packageName == original.packageName && installed.versionName == original.versionName)

internal fun verifiedStockSnapshot(file: File, sdkInt: Int): VerifiedStockSnapshot? =
    runCatching {
        val before = stockApkSha256(file)
        val signers = verifiedStockApkSigners(file, sdkInt)
        val identity = readStockApkIdentity(file, sdkInt)
        if (signers.isEmpty() || identity == null || before != stockApkSha256(file)) null
        else VerifiedStockSnapshot(before, signers, identity)
    }.getOrNull()

/** Reads only manifest identity; certificate trust always comes from the full verifier above. */
private fun readStockApkIdentity(file: File, sdkInt: Int): StockApkIdentity? = ZipFile(file).use { zip ->
    val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
    zip.getInputStream(entry).use manifestInput@{ input ->
        val manifest = AndroidManifestBlock.load(input)
        val packageName = manifest.packageName ?: return@manifestInput null
        val element = manifest.manifestElement ?: return@manifestInput null
        val codeAttribute = element.searchAttributeByResourceId(0x0101021b) ?: return@manifestInput null
        if (codeAttribute.dataType != DataType.DEC && codeAttribute.dataType != DataType.HEX) return@manifestInput null
        val code = codeAttribute.data
        val majorAttribute = element.searchAttributeByResourceId(0x01010576)
        // Older Android versions do not interpret versionCodeMajor when recording a package.
        val major = if (sdkInt < 28 || majorAttribute == null) 0 else {
            if (majorAttribute.dataType != DataType.DEC && majorAttribute.dataType != DataType.HEX) return@manifestInput null
            majorAttribute.data
        }
        StockApkIdentity(
            packageName, (major.toLong() shl 32) or (code.toLong() and 0xffffffffL),
            manifest.versionName, manifest.split.takeIf { manifest.isSplit }
        )
    }
}

internal fun stockApkSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().hexDigest()
}

private fun ByteArray.hexDigest() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** A matching version is not proof that a root namespace still exposes the original APK. */
internal suspend fun <T : Any> ensureVerifiedInstalledStock(
    canRestore: Boolean,
    capture: suspend () -> T?,
    restore: suspend () -> Unit
): T {
    capture()?.let { return it }
    if (!canRestore) throw StockApkIntegrityException()
    restore()
    return capture() ?: throw StockApkIntegrityException()
}

class StockApkIntegrityException : Exception(
    "Cannot verify the original APK for this root install. Choose the original APK or APK bundle, " +
            "or reinstall the matching unpatched app, then try again."
)

/**
 * A root restore needs every original APK, not just readable metadata on the base module.
 * Merged and stripped patching inputs keep an old signing block and cannot serve as stock.
 */
internal fun isVerifiedStockArchive(file: File, sdkInt: Int, tempParent: File): Boolean =
    runCatching {
        if (!file.isFile) return@runCatching false
        if (!SplitApkPreparer.isSplitArchive(file)) {
            return@runCatching verifiedStockSnapshot(file, sdkInt) != null
        }

        tempParent.mkdirs()
        val directory = Files.createTempDirectory(tempParent.toPath(), "stock-apks-").toFile()
        try {
            ZipFile(file).use { zip ->
                val entries = SplitApkPreparer.splitModuleEntries(zip).toList()
                if (entries.isEmpty()) return@use false
                val verified = entries.withIndex().map { (index, entry) ->
                    // The output name is ours, not a path supplied by the archive.
                    val apk = File(directory, "$index.apk")
                    zip.getInputStream(entry).use { input ->
                        apk.outputStream().use { output -> input.copyTo(output) }
                    }
                    verifiedStockSnapshot(apk, sdkInt) ?: return@use false
                }
                val base = verified.singleOrNull { it.identity.splitName == null } ?: return@use false
                val splits = verified.mapNotNull { it.identity.splitName }
                splits.size == splits.toSet().size && verified.all {
                    it.signers == base.signers &&
                            it.identity.packageName == base.identity.packageName &&
                            it.identity.versionCode == base.identity.versionCode &&
                            (base.identity.versionName == null || it.identity.versionName.isNullOrBlank() ||
                                    it.identity.versionName == base.identity.versionName)
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }.getOrDefault(false)

/** The boot scripts check only these verified stock copies, never the patched APK. */
internal fun stockApkHashManifest(hashes: Map<String, String>): String {
    require(hashes.isNotEmpty()) { "No verified stock APKs" }
    return hashes.entries.joinToString("\n", postfix = "\n") { (name, hash) ->
        require(name.matches(Regex("[A-Za-z0-9_.-]+\\.apk"))) { "Invalid stock APK file name" }
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid stock APK digest" }
        "$hash  $name"
    }
}

/**
 * Checks the staged bytes again in the same shell command that starts replacing the module.
 * A changed copy fails before any move, including when persistence took time after verification.
 */
internal fun verifiedStockCommitCommand(stagedFiles: Map<String, String>, stockHashes: Map<String, String>): String {
    require(stockHashes.isNotEmpty() && stockHashes.keys.all { it in stagedFiles })
    val checks = stockHashes.entries.joinToString("\n", postfix = "\n") { (target, hash) ->
        val path = stagedFiles.getValue(target)
        require(path.none { it == '\n' || it == '\r' || it == '\\' })
        require(hash.matches(Regex("[0-9a-f]{64}")))
        "$hash  $path"
    }
    val moves = stagedFiles.entries.joinToString(" && ") { (target, staging) ->
        "mv -f ${staging.stockShellQuote()} ${target.stockShellQuote()}"
    }
    return "printf '%s' ${checks.stockShellQuote()} | sha256sum -c && $moves"
}

private fun String.stockShellQuote() = "'${replace("'", "'\"'\"'")}'"
