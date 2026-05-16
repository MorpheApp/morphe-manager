package app.morphe.manager.util

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
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
    private val app: Application
) {
    private companion object {
        const val TAG = "Morphe PM"
    }

    val application: Application get() = app

    fun getPackageInfo(packageName: String, flags: Int = 0): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                app.packageManager.getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
            else
                app.packageManager.getPackageInfo(packageName, flags)
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

    fun PackageInfo.label(): String {
        val raw = this.applicationInfo!!.loadLabel(app.packageManager).toString()
        return cleanLabel(raw, this.packageName)
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
     */
    fun getInstalledSignatureHashes(packageName: String): Set<String> {
        return try {
            val pkgInfo = getPackageInfo(packageName, signingFlags()) ?: return emptySet()
            pkgInfo.extractSignatures()?.toSha256Hashes() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read installed signatures for $packageName", e)
            emptySet()
        }
    }

    /**
     * Extracts SHA-256 certificate fingerprints from an APK file.
     * Returns an empty set if the file cannot be read or has no signatures.
     * Uses full signing history to handle apps with certificate rotation.
     */
    fun getApkFileSignatureHashes(file: File): Set<String> {
        return try {
            val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
                ?: return emptySet()
            info.applicationInfo?.apply {
                sourceDir = file.absolutePath
                publicSourceDir = file.absolutePath
            }
            info.extractSignatures()?.toSha256Hashes() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read APK file signatures", e)
            emptySet()
        }
    }

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

    fun isAppDeleted(packageName: String, hasSavedCopy: Boolean, wasInstalledOnDevice: Boolean): Boolean {
        val currentlyInstalled = getPackageInfo(packageName) != null
        return !currentlyInstalled && wasInstalledOnDevice && hasSavedCopy
    }

    private fun cleanLabel(raw: String, packageName: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        // If the label contains the package name or a dotted class, strip to the last segment.
        val hasDots = trimmed.contains('.')
        val pkgMatch = packageName.isNotEmpty() && (trimmed.startsWith(packageName) || trimmed.contains(packageName))
        val base = if (hasDots || pkgMatch) trimmed.substringAfterLast('.') else trimmed
        val withoutSuffix = base.removeSuffix("Application")
        val candidate = withoutSuffix.ifBlank { base }
        return candidate.ifBlank { trimmed }
    }
}

/** Opens the system screen that lets the user grant the "install unknown apps" permission. */
object RequestInstallAppsContract : ActivityResultContract<String, Boolean>(), KoinComponent {
    private val pm: PM by inject()
    override fun createIntent(context: Context, input: String) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", input, null))

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return pm.canInstallPackages()
    }
}
