/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/component/AppLabel.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.screen.shared

import android.content.pm.PackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.morphe.manager.util.AppDataResolver
import app.morphe.manager.util.AppDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Universal app label component.
 *
 * Automatically resolves label from available sources:
 * installed app → original APK → patched APK → constants → package name fallback.
 */
@Composable
fun AppLabel(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo? = null,
    packageName: String? = null,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = stringResource(R.string.not_installed),
    preferredSource: AppDataSource = AppDataSource.INSTALLED,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    // If PackageInfo is provided, use the simple implementation
    if (packageInfo != null) {
        SimpleAppLabel(
            packageInfo = packageInfo,
            modifier = modifier,
            style = style,
            defaultText = defaultText,
            maxLines = maxLines,
            overflow = overflow
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
            preferredSource = preferredSource,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    // Fallback: show default text if neither is provided
    Text(
        text = defaultText ?: stringResource(R.string.not_installed),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Simple label display when PackageInfo is already available.
 */
@Composable
private fun SimpleAppLabel(
    packageInfo: PackageInfo,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val context = LocalContext.current

    // Attempt a cheap synchronous resolution first so we never show a shimmer
    // when the label is already available in memory
    val initialLabel = remember(packageInfo.packageName) {
        runCatching {
            packageInfo.applicationInfo?.loadLabel(context.packageManager)
                ?.toString()
                ?.let { raw ->
                    val cleaned = cleanWeirdLabel(raw, packageInfo.packageName)
                    cleaned.takeIf { it.isNotBlank() && cleaned != packageInfo.packageName }
                }
                ?: packageInfo.applicationInfo?.nonLocalizedLabel?.toString()
                    ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    var label: String? by rememberSaveable(packageInfo.packageName) {
        mutableStateOf(initialLabel)
    }

    // Only dispatch to IO when the synchronous attempt didn't produce a result
    if (label == null) {
        LaunchedEffect(packageInfo.packageName) {
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
    }

    Text(
        label ?: defaultText ?: stringResource(R.string.loading),
        modifier = Modifier
            .then(
                // A pill stands in for the text until the real label arrives
                if (label == null) {
                    Modifier
                        .background(MaterialTheme.colorScheme.inverseOnSurface, RoundedCornerShape(100))
                        .drawWithContent {}
                } else {
                    Modifier
                }
            )
            .then(modifier),
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Resolved label from any available source when only package name is known.
 */
@Composable
private fun ResolvedAppLabel(
    packageName: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = null,
    preferredSource: AppDataSource = AppDataSource.INSTALLED,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val appDataResolver: AppDataResolver = koinInject()

    var label by remember(packageName) { mutableStateOf<String?>(null) }
    var isLoading by remember(packageName) { mutableStateOf(true) }

    LaunchedEffect(packageName, preferredSource) {
        // Use resolveAppData to get complete data in one call
        val resolvedData = appDataResolver.resolveAppData(packageName, preferredSource)
        // If resolved name is same as package name, and we have a default, use default
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
            style = style,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

/**
 * Clean weird labels that contain package name or other artifacts.
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
