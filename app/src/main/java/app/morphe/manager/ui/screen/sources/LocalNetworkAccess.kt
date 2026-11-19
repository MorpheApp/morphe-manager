/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.sources

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.R
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.ui.screen.shared.MessageAction
import app.morphe.manager.ui.screen.shared.UserMessages
import app.morphe.manager.util.LocalNetwork
import app.morphe.manager.util.openAppDetailsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Asks for the local network permission once a remote source is hosted on the local network, such
 * as on a home server, which Android 17 keeps Morphe from reaching without it, see [LocalNetwork].
 * Once granted, those sources are updated again, as what they fetched until then timed out.
 *
 * Each source is asked about once while Morphe runs, so a refusal is not asked again until another
 * such source is added. A refusal says what it costs and leads to the app settings to change it.
 */
@Composable
fun LocalNetworkSourceAccess(repository: PatchBundleRepository) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return

    val context = LocalContext.current
    val messages = koinInject<UserMessages>()
    val scope = rememberCoroutineScope()
    val sources by repository.sources.collectAsStateWithLifecycle()
    // Keyed by the links alone, so the lookups are not run again whenever a source changes state.
    // A disabled source is not updated, so it is asked about once it is turned back on
    val endpoints = remember(sources) {
        sources.filterIsInstance<RemotePatchBundle>().filter { it.enabled }.mapTo(mutableSetOf()) { it.endpoint }
    }

    var askedEndpoints by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var waitingUids by rememberSaveable { mutableStateOf(emptyList<Int>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val uids = waitingUids
        waitingUids = emptyList()
        if (granted) {
            scope.launch {
                val waiting = repository.sources.value
                    .filterIsInstance<RemotePatchBundle>()
                    .filter { it.uid in uids }
                repository.updateOrRepair(waiting, showMessage = true)
            }
        } else {
            messages.show(
                R.string.sources_local_network_denied,
                long = true,
                action = MessageAction(R.string.settings) { context.openAppDetailsSettings() }
            )
        }
    }

    LaunchedEffect(endpoints) {
        if (!LocalNetwork.needsPermission(context)) return@LaunchedEffect
        val unasked = endpoints - askedEndpoints.toSet()
        if (unasked.isEmpty()) return@LaunchedEffect

        val local = withContext(Dispatchers.IO) { unasked.filter(LocalNetwork::isLocalUrl) }
        askedEndpoints = askedEndpoints + unasked
        if (local.isEmpty()) return@LaunchedEffect

        waitingUids = repository.sources.value
            .filterIsInstance<RemotePatchBundle>()
            .filter { it.enabled && it.endpoint in local }
            .map { it.uid }
        permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}
