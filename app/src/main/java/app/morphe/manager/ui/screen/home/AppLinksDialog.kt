/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.morphe.manager.R
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.domain.links.RepairCapability
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.util.openAppOpenByDefaultSettings
import app.morphe.manager.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Lists the web links [packageName] declares and where each one opens. [onRefresh] reads the
 * status again, which happens on every return to the app since the system screen is where the
 * user changes it.
 */
@Composable
fun AppLinksDialog(
    appLabel: String,
    appInfo: PackageInfo?,
    accentColor: Color?,
    packageName: String,
    status: AppLinksStatus,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val appLinksManager: AppLinksManager = koinInject()
    val repairCapability by produceState(RepairCapability.NONE) {
        value = appLinksManager.getRepairCapability()
    }
    var isRepairing by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefresh()
    }

    val dialogActions = buildList {
        if (repairCapability != RepairCapability.NONE && status.needsAttention) {
            add(
                DialogAction(
                    text = stringResource(
                        if (isRepairing) R.string.app_links_enable_auto_in_progress
                        else R.string.app_links_enable_auto
                    ),
                    icon = Icons.Outlined.AutoFixHigh,
                    onClick = {
                        scope.launch {
                            isRepairing = true
                            val success = appLinksManager.repairAppLinks(packageName)
                            isRepairing = false
                            onRefresh()
                            context.toast(
                                resources.getString(
                                    if (success) R.string.app_links_repair_success
                                    else R.string.app_links_repair_failed
                                )
                            )
                        }
                    },
                    enabled = !isRepairing,
                    emphasis = DialogActionEmphasis.Filled
                )
            )
        }
        add(
            DialogAction(
                text = stringResource(R.string.app_links_open_settings),
                icon = Icons.AutoMirrored.Outlined.Launch,
                onClick = { context.openAppOpenByDefaultSettings(packageName) }
            )
        )
    }

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier ->
            AppIcon(packageInfo = appInfo, packageName = packageName, contentDescription = null, modifier = modifier)
        },
        title = appLabel,
        subtitle = stringResource(R.string.app_links_title),
        accentColor = accentColor,
        actions = dialogActions
    ) {
        if (status.needsAttention) {
            Notice(
                text = stringResource(R.string.app_links_description),
                tone = SemanticTone.Warning,
                icon = Icons.Outlined.LinkOff
            )
        } else {
            Notice(
                text = stringResource(R.string.app_links_all_verified),
                tone = SemanticTone.Success,
                icon = Icons.Outlined.CheckCircleOutline
            )
        }

        Spacer(Modifier.height(Defaults.ItemSpacing))

        SurfaceCard(
            cornerRadius = Defaults.CardCornerRadius,
            showBorder = true,
            borderColor = appAccentBorder(accentColor),
            color = cardFill(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                status.domains.forEachIndexed { index, domain ->
                    if (index > 0) SettingsDivider()
                    DomainRow(domain = domain, isEnabled = domain !in status.unhandledDomains)
                }
            }
        }
    }
}

@Composable
private fun DomainRow(domain: String, isEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
        horizontalArrangement = Arrangement.spacedBy(Defaults.ContentPaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                if (isEnabled) R.string.app_links_badge_enabled else R.string.app_links_badge_disabled
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) SemanticTone.Success.content else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                .background(
                    if (isEnabled) SemanticTone.Success.container else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
