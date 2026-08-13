package app.morphe.manager.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import app.morphe.manager.domain.apk.ApkSignatureCache
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.security.MessageDigest

@Immutable
@Parcelize
data class AppInfo(
    val packageName: String,
    val patches: Int?,
    val packageInfo: PackageInfo?
) : Parcelable

@SuppressLint("QueryPermissionsNeeded")
class PM(
    private val app: Application,
    // Certificate extraction verifies the whole archive, so repeating it for every home refresh
    // is the single most expensive thing this class can do
    private val signatureCache: ApkSignatureCache
) {
    private companion object {
        const val TAG = "Morphe PM"

        // Other managers whose installs are always patched.
        // Morphe's own id comes from the application at runtime so debug builds are covered too.
        val PATCH_MANAGER_PACKAGES = setOf(
            "app.revanced.manager",
            "app.revanced.manager.flutter",
            "app.rvx.manager",
            "app.rvx.manager.flutter",
            "app.universal.revanced.manager"
        )
    }

    val application: Application get() = app

    @Suppress("DEPRECATION")
    fun getPackageInfo(packageName: String, flags: Int = 0): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                app.packageManager.getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
            else
                app.packageManager.getPackageInfo(packageName, flags)
        } catch (_: NameNotFoundException) {
            null
        }

    @Suppress("DEPRECATION")
    fun getApplicationInfo(packageName: String, flags: Int = 0): ApplicationInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                app.packageManager.getApplicationInfo(packageName, ApplicationInfoFlags.of(flags.toLong()))
            else
                app.packageManager.getApplicationInfo(packageName, flags)
        } catch (_: NameNotFoundException) {
            null
        }

    fun getPackageInfo(file: File): PackageInfo? {
        val path = file.absolutePath
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
        val pkgInfo = app.packageManager.getPackageArchiveInfo(path, flags) ?: return null

        // This is needed in order to load label and icon.
        pkgInfo.applicationInfo!!.apply {
            sourceDir = path
            publicSourceDir = path
        }

        return pkgInfo
    }

    @Suppress("DEPRECATION", "QueryPermissionsNeeded")
    fun getInstalledPackages(flags: Int = 0): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            app.packageManager.getInstalledPackages(PackageInfoFlags.of(flags.toLong()))
        else
            app.packageManager.getInstalledPackages(flags)

    fun PackageInfo.label(): String {
        val raw = this.applicationInfo!!.loadLabel(app.packageManager).toString()
        return cleanPackageLabel(raw, this.packageName)
    }

    fun getVersionCode(packageInfo: PackageInfo) = PackageInfoCompat.getLongVersionCode(packageInfo)

    fun launch(pkg: String) = app.packageManager.getLaunchIntentForPackage(pkg)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(it)
    }

    fun canInstallPackages() = app.packageManager.canRequestPackageInstalls()

    /**
     * Returns the first signing certificate of an installed package, or null if not found.
     */
    @Suppress("DEPRECATION")
    fun getSignature(packageName: String): Signature? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = getPackageInfo(packageName, flags) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
                ?: info.signatures?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        }
    }

    /**
     * Returns the first signing certificate of an APK file, or null if not found.
     */
    @Suppress("DEPRECATION")
    fun getArchiveSignature(file: File): Signature? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
                ?: info.signatures?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        }
    }

    /**
     * Returns true if the signing certificate of [file] differs from the installed package.
     * Returns false if either signature cannot be read.
     */
    fun hasSignatureMismatch(packageName: String, file: File): Boolean {
        val installed = getSignature(packageName)?.toByteArray() ?: return false
        val archive = getArchiveSignature(file)?.toByteArray() ?: return false
        return !installed.contentEquals(archive)
    }

    /**
     * Extracts SHA-256 certificate fingerprints from the installed [packageName].
     * Returns an empty set if the package is not found or signatures cannot be read.
     * Uses full signing history to handle apps with certificate rotation.
     *
     * Falls back to parsing the APK on disk: the package manager query travels through binder
     * and comes back empty on transaction failures, while the archive is parsed in-process.
     */
    fun getInstalledSignatureHashes(packageName: String): Set<String> {
        val recorded = recordedSignatureHashes(packageName)
        if (recorded.isNotEmpty()) return recorded

        val sourceDir = getApplicationInfo(packageName)?.sourceDir ?: return emptySet()
        return getApkFileSignatureHashes(File(sourceDir))
    }

    /**
     * Returns true if the APK on disk is signed differently from what the system recorded for
     * [packageName]. A mounted install looks exactly like that: the package manager keeps reporting
     * the stock certificate while [ApplicationInfo.sourceDir] is bind-mounted onto the patched APK.
     * Returns false when either side cannot be read, so an unreadable certificate is never a
     * mismatch on its own.
     */
    fun hasSourceApkSignatureMismatch(packageName: String): Boolean {
        val recorded = recordedSignatureHashes(packageName)
        if (recorded.isEmpty()) return false
        val sourceDir = getApplicationInfo(packageName)?.sourceDir ?: return false
        val onDisk = getApkFileSignatureHashes(File(sourceDir))
        if (onDisk.isEmpty()) return false
        return onDisk.none { it in recorded }
    }

    /** Certificate fingerprints the package manager holds for [packageName], without disk access. */
    private fun recordedSignatureHashes(packageName: String): Set<String> =
        try {
            getPackageInfo(packageName, signingFlags())?.let(::signatureHashes).orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read installed signatures for $packageName", e)
            emptySet()
        }

    /**
     * Package that installed [packageName], or null when unknown.
     * Unlike the installed-app database this survives clearing Morphe's data, so it is the last
     * remaining hint that an app was put there by a patch manager.
     */
    @Suppress("DEPRECATION")
    fun getInstallerPackageName(packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            app.packageManager.getInstallSourceInfo(packageName).installingPackageName
        else
            app.packageManager.getInstallerPackageName(packageName)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read install source for $packageName", e)
        null
    }

    /**
     * Whether [packageName] was installed by a patch manager, as those only install patched APKs.
     * Reinstalling from another source (Play, a store, adb) replaces the installer, so this does
     * not linger after the user goes back to the original app.
     */
    fun isInstalledByPatchManager(packageName: String): Boolean =
        isPatchManagerInstaller(getInstallerPackageName(packageName))

    /** [isInstalledByPatchManager] for callers that already resolved the installer. */
    fun isPatchManagerInstaller(installer: String?): Boolean {
        if (installer == null) return false
        return installer == app.packageName || installer in PATCH_MANAGER_PACKAGES
    }

    /**
     * Extracts SHA-256 certificate fingerprints from an APK file.
     * Returns an empty set if the file cannot be read or has no signatures.
     * Uses full signing history to handle apps with certificate rotation.
     */
    fun getApkFileSignatureHashes(file: File): Set<String> {
        val stamp = signatureCache.stamp(file) ?: return emptySet()
        signatureCache.get(stamp)?.let { return it }

        return try {
            val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
                ?: return emptySet()
            info.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }
            signatureHashes(info).also { signatureCache.putIfUnchanged(file, stamp, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read APK file signatures", e)
            emptySet()
        }
    }

    /**
     * Parsed [file] when it is the signed APK the record describes, or null otherwise.
     *
     * A path alone is not a usable saved copy: an interrupted write, corrupt archive or a file
     * left behind by another package or version must not enable install, export or mount actions,
     * and must never stand in as the certificate that proves an install is the patched build.
     *
     * The identity is read from the manifest alone, so an archive that is not the recorded
     * artifact is rejected before its certificate is ever extracted.
     */
    fun readSavedApkInfo(file: File, version: String, vararg packageNames: String): PackageInfo? {
        if (!file.isFile) return null
        return try {
            val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null

            val matches = matchesSavedApkRecord(
                archivePackageName = info.packageName,
                archiveVersionName = info.versionName,
                trackedPackageNames = packageNames.asList(),
                trackedVersion = version,
                isSigned = { getApkFileSignatureHashes(file).isNotEmpty() }
            )
            if (!matches) return null

            // Needed by callers that read the label or icon straight off the archive
            info.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }
            info
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate APK file: ${file.absolutePath}", e)
            null
        }
    }

    /** SHA-256 certificate fingerprints of an already parsed [packageInfo]. */
    private fun signatureHashes(packageInfo: PackageInfo): Set<String> =
        packageInfo.extractSignatures()?.toSha256Hashes().orEmpty()

    @Suppress("DEPRECATION")
    private fun signingFlags() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        PackageManager.GET_SIGNING_CERTIFICATES
    else
        PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun PackageInfo.extractSignatures(): Array<Signature>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            signatures
        }
    }

    private fun Array<Signature>.toSha256Hashes(): Set<String> {
        val digest = MessageDigest.getInstance("SHA-256")
        return mapTo(mutableSetOf()) { sig ->
            digest.reset()
            digest.digest(sig.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }
    }
}

/**
 * Whether a label is an identifier the app never meant to show, rather than a name it chose.
 *
 * Apps without a real label fall back to their package or a launcher class, and only those are
 * worth reducing to a last segment. A brand that simply contains a dot must survive, so a dotted
 * label only qualifies with the shape of a package: no spaces, three or more segments, and a
 * lowercase top-level domain in front.
 */
private fun looksLikeIdentifierLabel(label: String, packageName: String): Boolean {
    if (label.any(Char::isWhitespace)) return false
    if (packageName.isNotEmpty() && label.contains(packageName)) return true
    if (label.count { it == '.' } < 2) return false
    if (!label.all { it.isLetterOrDigit() || it == '.' || it == '_' }) return false
    return label.substringBefore('.').none(Char::isUpperCase)
}

/**
 * Reduces a launcher label to the part worth showing.
 * Kept free of Android APIs so the identifier rules can be tested directly.
 */
internal fun cleanPackageLabel(raw: String, packageName: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    if (!looksLikeIdentifierLabel(trimmed, packageName)) return trimmed

    val base = trimmed.substringAfterLast('.')
    val withoutSuffix = base.removeSuffix("Application")
    val candidate = withoutSuffix.ifBlank { base }
    return candidate.ifBlank { trimmed }
}

/**
 * Whether a parsed archive is the artifact a saved-APK record describes.
 *
 * The version is compared rather than inferred from the file name: the patcher persists the
 * version it produced both as the record's version and in the retained file name, so an archive
 * that reports a different one is not the build the record was written for. Accepting it would
 * hand its certificate to the tracked-install check as proof that an install is patched.
 *
 * [isSigned] is evaluated last and only when the identity already matches, because reading a
 * certificate means verifying the entire archive.
 */
internal fun matchesSavedApkRecord(
    archivePackageName: String?,
    archiveVersionName: String?,
    trackedPackageNames: Collection<String>,
    trackedVersion: String,
    isSigned: () -> Boolean
): Boolean =
    archivePackageName in trackedPackageNames &&
            archiveVersionName == trackedVersion &&
            isSigned()

fun File.sha256OrNull(): String? = runCatching {
    if (!isFile) return@runCatching null
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (!Thread.currentThread().isInterrupted) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    if (Thread.currentThread().isInterrupted) return@runCatching null
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}.getOrNull()

/** Opens the system screen that lets the user grant the "install unknown apps" permission. */
object RequestInstallAppsContract : ActivityResultContract<String, Boolean>(), KoinComponent {
    private val pm: PM by inject()
    override fun createIntent(context: Context, input: String) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", input, null))

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return pm.canInstallPackages()
    }
}
