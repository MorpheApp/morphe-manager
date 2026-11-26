/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import android.content.pm.PackageInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.morphe.manager.R
import app.morphe.manager.domain.links.AppLinksManager
import app.morphe.manager.domain.links.AppLinksStatus
import app.morphe.manager.domain.links.LinkSelectionAccess
import app.morphe.manager.ui.screen.shared.*
import app.morphe.manager.ui.viewmodel.AppLinksViewModel
import app.morphe.manager.util.openAppOpenByDefaultSettings
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Lists the web links [packageName] declares and where each one opens. [onRefresh] reads the
 * status again, which also happens on every return to the app, since the system screen is where
 * the user changes it without the manager's help.
 */
@Composable
fun DialogHostScope.AppLinksDialog(
    appLabel: String,
    appInfo: PackageInfo?,
    accentColor: Color?,
    packageName: String,
    status: AppLinksStatus,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefresh()
    }
    val controls = rememberAppLinksControls(packageName, status, onRefresh)

    DetailsDialog(
        onDismissRequest = onDismiss,
        icon = { modifier ->
            AppIcon(packageInfo = appInfo, packageName = packageName, contentDescription = null, modifier = modifier)
        },
        title = appLabel,
        subtitle = stringResource(R.string.app_links_title),
        accentColor = accentColor,
        actions = controls.actions
    ) {
        AppLinksContent(status = status, controls = controls, accentColor = accentColor)
    }
}

/**
 * What can be done about an app's web links from the manager: [actions] for all of them at once,
 * and [onToggleDomain] for one, null where the device does not let the manager change them.
 * [pendingDomains] are the ones whose change is still being applied. [accessHint] says what would
 * let Shizuku change them, while that is all that stands in the way.
 */
@Immutable
class AppLinksControls(
    val actions: List<DialogAction>,
    val onToggleDomain: ((domain: String, selected: Boolean) -> Unit)?,
    val pendingDomains: Set<String>,
    @param:StringRes val accessHint: Int?
)

/**
 * The [AppLinksControls] of [packageName]: selecting every link or one at a time where Shizuku or
 * root lets the manager do it, a way to grant Shizuku access or start it when nothing else is
 * missing, and opening the system screen where the user picks them either way.
 */
@Composable
fun rememberAppLinksControls(
    packageName: String,
    status: AppLinksStatus,
    onRefresh: () -> Unit,
    viewModel: AppLinksViewModel = koinViewModel()
): AppLinksControls {
    val context = LocalContext.current
    val appLinksManager: AppLinksManager = koinInject()
    // Read again on a return to Morphe and on an answer to a Shizuku prompt, where the user
    // starts Shizuku or grants access
    var accessCheck by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accessCheck++ }
    DisposableEffect(appLinksManager) {
        val observer = appLinksManager.observeShizukuPermission { accessCheck++ }
        onDispose { observer.close() }
    }
    val access by produceState(LinkSelectionAccess.NONE, accessCheck) {
        value = appLinksManager.getSelectionAccess()
    }
    val canChange = access.canChange
    val pendingDomains = viewModel.pendingDomains(packageName)

    val actions = buildList {
        if (canChange) {
            BulkLinkChange.entries.forEach { bulk ->
                // Offered only while some link would change, and those links are what it marks pending
                val domains = if (bulk.selected) status.unhandledDomains else status.handledDomains
                if (domains.isEmpty()) return@forEach
                val inProgress = pendingDomains.containsAll(domains)
                add(
                    DialogAction(
                        text = stringResource(if (inProgress) bulk.progressLabel else bulk.label),
                        icon = bulk.icon,
                        onClick = {
                            viewModel.setAllSelected(
                                packageName = packageName,
                                domains = domains.toSet(),
                                selected = bulk.selected,
                                successMessage = bulk.successMessage,
                                failureMessage = bulk.failureMessage,
                                onChanged = onRefresh
                            )
                        },
                        enabled = pendingDomains.isEmpty(),
                        // Pressed for only while no link reaches the app, as a few left out can be on purpose
                        emphasis = if (bulk.selected && status.opensInBrowser) {
                            DialogActionEmphasis.Filled
                        } else {
                            DialogActionEmphasis.Outlined
                        }
                    )
                )
            }
        }
        // Pressed for while no link reaches the app, as the enable action it unlocks would be
        val shizukuEmphasis = if (status.opensInBrowser) DialogActionEmphasis.Filled else DialogActionEmphasis.Outlined
        when (access) {
            LinkSelectionAccess.SHIZUKU_PERMISSION_MISSING -> add(
                DialogAction(
                    text = stringResource(R.string.app_links_allow_shizuku),
                    icon = MaterialSymbols.Rounded.Key,
                    onClick = { if (!appLinksManager.requestShizukuPermission()) accessCheck++ },
                    emphasis = shizukuEmphasis
                )
            )
            LinkSelectionAccess.SHIZUKU_NOT_RUNNING -> add(
                DialogAction(
                    text = stringResource(R.string.installer_action_open_shizuku),
                    icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                    onClick = { appLinksManager.openShizuku() },
                    emphasis = shizukuEmphasis
                )
            )
            else -> Unit
        }
        add(
            DialogAction(
                text = stringResource(R.string.app_links_open_settings),
                icon = MaterialSymbols.Rounded.Open_in_new.autoMirrored,
                onClick = { context.openAppOpenByDefaultSettings(packageName) }
            )
        )
    }

    return AppLinksControls(
        actions = actions,
        onToggleDomain = if (canChange) {
            { domain, selected -> viewModel.setSelected(packageName, domain, selected, onChanged = onRefresh) }
        } else null,
        pendingDomains = pendingDomains,
        accessHint = when (access) {
            LinkSelectionAccess.SHIZUKU_PERMISSION_MISSING -> R.string.app_links_shizuku_permission_hint
            LinkSelectionAccess.SHIZUKU_NOT_RUNNING -> R.string.app_links_shizuku_not_running_hint
            else -> null
        }
    )
}

/** Selecting or deselecting every web link of an app at once, in the order the dialog offers them. */
private enum class BulkLinkChange(
    val selected: Boolean,
    @param:StringRes val label: Int,
    @param:StringRes val progressLabel: Int,
    val icon: ImageVector,
    @param:StringRes val successMessage: Int,
    @param:StringRes val failureMessage: Int
) {
    ENABLE(
        selected = true,
        label = R.string.app_links_enable_auto,
        progressLabel = R.string.app_links_enable_auto_in_progress,
        icon = MaterialSymbols.Rounded.Auto_fix_high,
        successMessage = R.string.app_links_repair_success,
        failureMessage = R.string.app_links_repair_failed
    ),
    DISABLE(
        selected = false,
        label = R.string.disable_all,
        progressLabel = R.string.app_links_disable_all_in_progress,
        icon = MaterialSymbols.Rounded.Link_off,
        successMessage = R.string.app_links_disable_success,
        failureMessage = R.string.app_links_disable_failed
    )
}

/** Each link the app declares and whether it opens in the app, then what that adds up to. */
@Composable
fun AppLinksContent(status: AppLinksStatus, controls: AppLinksControls, accentColor: Color?) {
    // Looked up once per status rather than per row, as an app can declare hundreds of domains
    val unhandled = remember(status) { status.unhandledDomains.toSet() }
    val onToggleDomain = controls.onToggleDomain
    SurfaceCard(
        cornerRadius = Defaults.CornerLarge,
        showBorder = true,
        borderColor = appAccentBorder(accentColor),
        color = cardFill(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            status.domains.forEachIndexed { index, domain ->
                if (index > 0) SettingsDivider()
                val opensInApp = domain !in unhandled
                if (onToggleDomain != null) {
                    val pending = domain in controls.pendingDomains
                    ToggleRow(
                        title = domain,
                        checked = opensInApp,
                        onCheckedChange = { onToggleDomain(domain, it) },
                        enabled = !pending,
                        isLoading = pending,
                        showDivider = false,
                        accentColor = accentColor,
                        // With the row's own inset, level with the domains of a row that cannot be changed
                        modifier = Modifier.padding(horizontal = Defaults.SpaceMedium)
                    )
                } else {
                    DomainRow(domain = domain, isEnabled = opensInApp)
                }
            }
        }
    }

    when {
        status.isFullyConfigured -> Footnote(
            text = stringResource(R.string.app_links_all_verified),
            tone = SemanticTone.Success,
            icon = MaterialSymbols.Rounded.Check_circle
        )
        status.opensInBrowser -> Footnote(
            text = stringResource(R.string.app_links_description),
            tone = SemanticTone.Warning,
            icon = MaterialSymbols.Rounded.Link_off
        )
        // Apps declare service domains next to their own, so some left to the browser is a choice
        // the user makes rather than something to fix
        else -> Footnote(
            text = stringResource(R.string.app_links_description),
            icon = MaterialSymbols.Rounded.Link
        )
    }

    // Only worth saying while some link still opens in the browser
    controls.accessHint?.takeIf { status.needsAttention }?.let { hint ->
        Footnote(text = stringResource(hint), icon = MaterialSymbols.Rounded.Key)
    }
}

@Composable
private fun DomainRow(domain: String, isEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Defaults.SpaceLarge, vertical = Defaults.SpaceMedium),
        horizontalArrangement = Arrangement.spacedBy(Defaults.SpaceSmall),
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
                if (isEnabled) R.string.enabled else R.string.disabled
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) SemanticTone.Success.content else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(Defaults.CornerMedium))
                .background(
                    if (isEnabled) SemanticTone.Success.container else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = Defaults.SpaceSmall, vertical = 2.dp)
        )
    }
}
