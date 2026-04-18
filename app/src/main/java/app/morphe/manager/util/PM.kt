package app.morphe.manager.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.parcelize.Parcelize
import java.io.File

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
