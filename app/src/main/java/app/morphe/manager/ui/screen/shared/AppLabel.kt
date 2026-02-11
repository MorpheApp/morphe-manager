package app.morphe.manager.ui.screen.shared

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.OriginalApkRepository
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import app.morphe.manager.util.PM
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalResources

/**
 * Universal app label component
 *
 * Automatically resolves label from available sources:
 * installed app → original APK → patched APK → constants → package name fallback
 */
@Composable
fun AppLabel(
    packageInfo: PackageInfo? = null,
    packageName: String? = null,
    @SuppressLint("ModifierParameter")
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = stringResource(R.string.not_installed),
    preferredSource: AppDataSource = AppDataSource.INSTALLED
) {
    // If PackageInfo is provided, use the simple implementation
    if (packageInfo != null) {
        SimpleAppLabel(
            packageInfo = packageInfo,
            modifier = modifier,
            style = style,
            defaultText = defaultText
        )
        return
    }

    // If only package name is provided, resolve from multiple sources
    if (packageName != null) {
        ResolvedAppLabel(
            packageName = packageName,
            modifier = modifier,
            style = style,
            defaultText = defaultText,
            preferredSource = preferredSource
        )
        return
    }

    // Fallback: show default text if neither is provided
    Text(
        text = defaultText ?: stringResource(R.string.not_installed),
        modifier = modifier,
        style = style
    )
}

/**
 * Simple label display when PackageInfo is already available
 */
@Composable
private fun SimpleAppLabel(
    packageInfo: PackageInfo,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = null
) {
    val context = LocalContext.current
    var label: String? by rememberSaveable { mutableStateOf(null) }

    LaunchedEffect(packageInfo) {
        label = withContext(Dispatchers.IO) {
            packageInfo.applicationInfo?.loadLabel(context.packageManager)
                ?.toString()
                ?.let { raw ->
                    val cleaned = cleanWeirdLabel(raw, packageInfo.packageName)
                    cleaned.takeIf { it.isNotBlank() && cleaned != packageInfo.packageName }
                }
                ?: packageInfo.applicationInfo?.nonLocalizedLabel?.toString()
                    ?.takeIf { it.isNotBlank() }
                ?: defaultText
        }
    }

    Text(
        label ?: stringResource(R.string.loading),
        modifier = Modifier
            .placeholder(
                visible = label == null,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(100)
            )
            .then(modifier),
        style = style
    )
}

/**
 * Resolved label from any available source when only package name is known
 */
@Composable
private fun ResolvedAppLabel(
    packageName: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = null,
    preferredSource: AppDataSource = AppDataSource.INSTALLED
) {
    val context = LocalContext.current
    val pm: PM = koinInject()
    val originalApkRepository: OriginalApkRepository = koinInject()
    val installedAppRepository: InstalledAppRepository = koinInject()
    val filesystem: Filesystem = koinInject()

    val appDataResolver = remember(context, pm, originalApkRepository, installedAppRepository, filesystem) {
        AppDataResolver(context, pm, originalApkRepository, installedAppRepository, filesystem)
    }

    var label by remember(packageName) { mutableStateOf<String?>(null) }
    var isLoading by remember(packageName) { mutableStateOf(true) }

    LaunchedEffect(packageName, preferredSource) {
        // Use resolveAppData to get complete data in one call
        val resolvedData = appDataResolver.resolveAppData(packageName, preferredSource)
        // If resolved name is same as package name and we have a default, use default
        label = if (resolvedData.displayName == packageName && defaultText != null) {
            defaultText
        } else {
            resolvedData.displayName
        }
        isLoading = false
    }

    if (isLoading) {
        // Show shimmer while loading
        ShimmerText(
            modifier = modifier,
            widthFraction = 0.6f,
            height = with(LocalResources.current.displayMetrics) {
                (style.fontSize.value * density).dp
            }
        )
    } else {
        Text(
            label ?: stringResource(R.string.loading),
            modifier = modifier,
            style = style
        )
    }
}

/**
 * Clean weird labels that contain package name or other artifacts
 */
private fun cleanWeirdLabel(raw: String, packageName: String?): String {
    val trimmed = raw.trim()
    val pkg = packageName.orEmpty()
    if (pkg.isNotEmpty() && (trimmed.startsWith(pkg) || trimmed.contains(pkg))) {
        val candidate = trimmed.substringAfterLast('.')
        val withoutSuffix = candidate.removeSuffix("Application")
        return withoutSuffix.ifBlank { candidate }.ifBlank { trimmed }
    }
    if (trimmed.endsWith("Application")) {
        val withoutSuffix = trimmed.removeSuffix("Application")
        return withoutSuffix.substringAfterLast('.').ifBlank { withoutSuffix }
    }
    return trimmed
}
