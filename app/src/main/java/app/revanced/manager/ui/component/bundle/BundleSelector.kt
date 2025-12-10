package app.revanced.manager.ui.component.bundle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.revanced.manager.domain.bundles.PatchBundleSource
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.isDefault
import app.revanced.manager.ui.component.ColumnWithScrollbar
import app.morphe.manager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleSelector(sources: List<PatchBundleSource>, onFinish: (PatchBundleSource?) -> Unit) {
    LaunchedEffect(sources) {
        if (sources.size == 1) {
            onFinish(sources[0])
        }
    }

    if (sources.size < 2) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = { onFinish(null) }
    ) {
        ColumnWithScrollbar(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.select),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            HorizontalDivider()
            sources.forEachIndexed { index, source ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFinish(source) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        source.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val hasCustomName =
                        source.displayName?.takeUnless { name -> name.isBlank() } != null && source.displayTitle != source.name
                    if (hasCustomName) {
                        Text(
                            source.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    source.version?.let { versionLabel ->
                        Text(
                            versionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(
                            when {
                                source.isDefault -> R.string.bundle_type_preinstalled
                                source.asRemoteOrNull != null -> R.string.bundle_type_remote
                                else -> R.string.bundle_type_local
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (index != sources.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
