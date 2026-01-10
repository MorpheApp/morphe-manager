package app.revanced.manager.ui.component.morphe.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import app.revanced.manager.domain.installer.InstallerManager
import app.revanced.manager.ui.viewmodel.AdvancedSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Installer section
 */
@Composable
fun InstallerSection(
    installerManager: InstallerManager,
    advancedViewModel: AdvancedSettingsViewModel,
    onShowInstallerDialog: (InstallerDialogTarget) -> Unit
) {
    // Get current installer preferences
    val primaryPreference by advancedViewModel.prefs.installerPrimary.getAsState()
    val fallbackPreference by advancedViewModel.prefs.installerFallback.getAsState()

    // Parse tokens
    val primaryToken = remember(primaryPreference) {
        installerManager.parseToken(primaryPreference)
    }
    val fallbackToken = remember(fallbackPreference) {
        installerManager.parseToken(fallbackPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER

    // Helper function to ensure valid selection
    fun ensureSelection(
        entries: List<InstallerManager.Entry>,
        token: InstallerManager.Token,
        includeNone: Boolean,
        blockedToken: InstallerManager.Token? = null
    ): List<InstallerManager.Entry> {
        val normalized = buildList {
            val seen = mutableSetOf<Any>()
            entries.forEach { entry ->
                val key = when (val entryToken = entry.token) {
                    is InstallerManager.Token.Component -> entryToken.componentName
                    else -> entryToken
                }
                if (seen.add(key)) add(entry)
            }
        }

        val ensured = if (
            token == InstallerManager.Token.Internal ||
            token == InstallerManager.Token.AutoSaved ||
            (token == InstallerManager.Token.None && includeNone) ||
            normalized.any { tokensEqual(it.token, token) }
        ) {
            normalized
        } else {
            val described = installerManager.describeEntry(token, installTarget)
                ?: return normalized
            normalized + described
        }

        if (blockedToken == null) return ensured

        return ensured.map { entry ->
            if (!tokensEqual(entry.token, token) && tokensEqual(entry.token, blockedToken)) {
                entry.copy(availability = entry.availability.copy(available = false))
            } else entry
        }
    }

    // Installer entries with periodic updates
    var primaryEntries by remember(primaryToken, fallbackToken) {
        mutableStateOf(
            ensureSelection(
                installerManager.listEntries(installTarget, includeNone = false),
                primaryToken,
                includeNone = false,
                blockedToken = fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
            )
        )
    }

    var fallbackEntries by remember(primaryToken, fallbackToken) {
        mutableStateOf(
            ensureSelection(
                installerManager.listEntries(installTarget, includeNone = true),
                fallbackToken,
                includeNone = true,
                blockedToken = primaryToken
            )
        )
    }

    // Periodically update installer lists
    LaunchedEffect(installTarget, primaryToken, fallbackToken) {
        while (isActive) {
            val updatedPrimary = ensureSelection(
                installerManager.listEntries(installTarget, includeNone = false),
                primaryToken,
                includeNone = false,
                blockedToken = fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
            )
            val updatedFallback = ensureSelection(
                installerManager.listEntries(installTarget, includeNone = true),
                fallbackToken,
                includeNone = true,
                blockedToken = primaryToken
            )

            primaryEntries = updatedPrimary
            fallbackEntries = updatedFallback
            delay(1_500)
        }
    }

    // Get current entries
    val primaryEntry = primaryEntries.find { it.token == primaryToken }
        ?: installerManager.describeEntry(primaryToken, installTarget)
        ?: primaryEntries.first()

    val fallbackEntry = fallbackEntries.find { it.token == fallbackToken }
        ?: installerManager.describeEntry(fallbackToken, installTarget)
        ?: fallbackEntries.first()

    Column(
        modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary Installer
        InstallerSettingsItem(
            title = stringResource(R.string.installer_primary_title),
            entry = primaryEntry,
            onClick = { onShowInstallerDialog(InstallerDialogTarget.Primary) }
        )

        // Fallback Installer
        InstallerSettingsItem(
            title = stringResource(R.string.installer_fallback_title),
            entry = fallbackEntry,
            onClick = { onShowInstallerDialog(InstallerDialogTarget.Fallback) }
        )
    }
}

/**
 * Container for installer selection dialog
 */
@Composable
fun InstallerSelectionDialogContainer(
    target: InstallerDialogTarget,
    installerManager: InstallerManager,
    advancedViewModel: AdvancedSettingsViewModel,
    onDismiss: () -> Unit
) {
    val primaryPreference by advancedViewModel.prefs.installerPrimary.getAsState()
    val fallbackPreference by advancedViewModel.prefs.installerFallback.getAsState()

    val primaryToken = remember(primaryPreference) {
        installerManager.parseToken(primaryPreference)
    }
    val fallbackToken = remember(fallbackPreference) {
        installerManager.parseToken(fallbackPreference)
    }

    val installTarget = InstallerManager.InstallTarget.PATCHER

    fun ensureSelection(
        entries: List<InstallerManager.Entry>,
        token: InstallerManager.Token,
        includeNone: Boolean,
        blockedToken: InstallerManager.Token? = null
    ): List<InstallerManager.Entry> {
        val normalized = buildList {
            val seen = mutableSetOf<Any>()
            entries.forEach { entry ->
                val key = when (val entryToken = entry.token) {
                    is InstallerManager.Token.Component -> entryToken.componentName
                    else -> entryToken
                }
                if (seen.add(key)) add(entry)
            }
        }

        val ensured = if (
            token == InstallerManager.Token.Internal ||
            token == InstallerManager.Token.AutoSaved ||
            (token == InstallerManager.Token.None && includeNone) ||
            normalized.any { tokensEqual(it.token, token) }
        ) {
            normalized
        } else {
            val described = installerManager.describeEntry(token, installTarget)
                ?: return normalized
            normalized + described
        }

        if (blockedToken == null) return ensured

        return ensured.map { entry ->
            if (!tokensEqual(entry.token, token) && tokensEqual(entry.token, blockedToken)) {
                entry.copy(availability = entry.availability.copy(available = false))
            } else entry
        }
    }

    val isPrimary = target == InstallerDialogTarget.Primary

    val options = if (isPrimary) {
        ensureSelection(
            installerManager.listEntries(installTarget, includeNone = false),
            primaryToken,
            includeNone = false,
            blockedToken = fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
        )
    } else {
        ensureSelection(
            installerManager.listEntries(installTarget, includeNone = true),
            fallbackToken,
            includeNone = true,
            blockedToken = primaryToken
        )
    }

    InstallerSelectionDialog(
        title = stringResource(
            if (isPrimary) R.string.installer_primary_title
            else R.string.installer_fallback_title
        ),
        options = options,
        selected = if (isPrimary) primaryToken else fallbackToken,
        blockedToken = if (isPrimary)
            fallbackToken.takeUnless { tokensEqual(it, InstallerManager.Token.None) }
        else
            primaryToken,
        onDismiss = onDismiss,
        onConfirm = { selection ->
            if (isPrimary) {
                advancedViewModel.setPrimaryInstaller(selection)
            } else {
                advancedViewModel.setFallbackInstaller(selection)
            }
            onDismiss()
        },
        onOpenShizuku = installerManager::openShizukuApp,
        stripRootNote = true
    )
}
