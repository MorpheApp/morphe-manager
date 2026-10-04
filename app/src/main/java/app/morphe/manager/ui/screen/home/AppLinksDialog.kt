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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.morphe.manager.R
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.domain.links.RepairCapability
import app.morphe.manager.ui.screen.shared.*

@Composable
fun AppLinksDialog(
    appLabel: String,
    appInfo: PackageInfo?,
    accentColor: Color,
    packageName: String,
    status: AppLinksStatus,
    repairCapability: RepairCapability,
    isRepairing: Boolean,
    onRepair: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefresh()
    }

    val dialogActions = buildList {
        if (repairCapability != RepairCapability.NONE && status.needsAttention) {
            add(
                DialogAction(
                    text = if (isRepairing) {
                        stringResource(R.string.app_links_enable_auto_in_progress)
                    } else {
                        stringResource(R.string.app_links_enable_auto)
                    },
                    icon = Icons.Outlined.AutoFixHigh,
                    onClick = onRepair,
                    enabled = !isRepairing,
                    emphasis = DialogActionEmphasis.Filled
                )
            )
        }
        add(
            DialogAction(
                text = stringResource(R.string.app_links_open_settings),
                icon = Icons.AutoMirrored.Outlined.Launch,
                onClick = onOpenSettings
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
                    val isEnabled = domain !in status.unhandledDomains
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Defaults.ContentPadding, vertical = Defaults.ItemSpacing),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        val badgeBg = if (isEnabled) {
                            SemanticTone.Success.container
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        val badgeColor = if (isEnabled) {
                            SemanticTone.Success.content
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val badgeText = if (isEnabled) {
                            stringResource(R.string.app_links_badge_enabled)
                        } else {
                            stringResource(R.string.app_links_badge_unverified)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Defaults.CompactCornerRadius))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}
