package app.morphe.manager.ui.screen.shared

import android.content.pm.PackageInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import app.morphe.manager.R
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppLabel(
    packageInfo: PackageInfo?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    defaultText: String? = stringResource(R.string.not_installed)
) {
    val context = LocalContext.current

    var label: String? by rememberSaveable { mutableStateOf(null) }

    LaunchedEffect(packageInfo) {
        label = withContext(Dispatchers.IO) {
            packageInfo?.applicationInfo?.loadLabel(context.packageManager)
                ?.toString()
                ?.let { raw ->
                    val cleaned = cleanWeirdLabel(raw, packageInfo.packageName)
                    cleaned.takeIf { it.isNotBlank() && cleaned != packageInfo.packageName }
                }
                ?: packageInfo?.applicationInfo?.nonLocalizedLabel?.toString()
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
