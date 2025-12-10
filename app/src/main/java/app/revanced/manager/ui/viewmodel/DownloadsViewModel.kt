package app.revanced.manager.ui.viewmodel

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.data.room.apps.downloaded.DownloadedApp
import app.revanced.manager.domain.repository.DownloadedAppRepository
import app.revanced.manager.domain.repository.DownloaderPluginRepository
import app.revanced.manager.util.PM
import app.revanced.manager.util.mutableStateSetOf

import app.revanced.manager.util.toast
import app.morphe.manager.R
import app.revanced.manager.service.UninstallService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadsViewModel(
    private val downloadedAppRepository: DownloadedAppRepository,
    private val downloaderPluginRepository: DownloaderPluginRepository,
    val pm: PM
) : ViewModel() {
    val downloaderPluginStates = downloaderPluginRepository.pluginStates
    val downloadedApps = downloadedAppRepository.getAll().map { downloadedApps ->
        downloadedApps.sortedWith(
            compareBy<DownloadedApp> {
                it.packageName
            }.thenBy { it.version }
        )
    }
    val appSelection = mutableStateSetOf<DownloadedApp>()

    var isRefreshingPlugins by mutableStateOf(false)
        private set
    private val pendingUninstalls = mutableSetOf<String>()
    private val appContext = pm.application

    private val uninstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val packageName = intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_PACKAGE_NAME)
                ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                ?: intent.data?.schemeSpecificPart
                ?: return
            if (!pendingUninstalls.remove(packageName)) return

            val status = intent.getIntExtra(
                UninstallService.EXTRA_UNINSTALL_STATUS,
                PackageInstaller.STATUS_FAILURE
            )
            val statusMessage =
                intent.getStringExtra(UninstallService.EXTRA_UNINSTALL_STATUS_MESSAGE)

            if (status == PackageInstaller.STATUS_SUCCESS) {
                viewModelScope.launch {
                    downloaderPluginRepository.removePlugin(packageName)
                    reloadPlugins()
                }
                appContext.toast(
                    appContext.getString(
                        R.string.downloader_plugin_uninstall_success,
                        packageName
                    )
                )
            } else {
                appContext.toast(
                    statusMessage ?: appContext.getString(
                        R.string.downloader_plugin_uninstall_failed,
                        packageName
                    )
                )
            }
        }
    }

    init {
        val filter = IntentFilter(UninstallService.APP_UNINSTALL_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(uninstallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(uninstallReceiver, filter)
        }
    }

    fun toggleApp(downloadedApp: DownloadedApp) {
        if (appSelection.contains(downloadedApp))
            appSelection.remove(downloadedApp)
        else
            appSelection.add(downloadedApp)
    }

    fun deleteApps() {
        viewModelScope.launch(NonCancellable) {
            downloadedAppRepository.delete(appSelection)

            withContext(Dispatchers.Main) {
                appSelection.clear()
            }
        }
    }

    fun refreshPlugins() = viewModelScope.launch {
        reloadPlugins()
    }

    fun trustPlugin(packageName: String) = viewModelScope.launch {
        downloaderPluginRepository.trustPackage(packageName)
    }

    fun revokePluginTrust(packageName: String) = viewModelScope.launch {
        downloaderPluginRepository.revokeTrustForPackage(packageName)
    }

    fun uninstallPlugin(packageName: String) = viewModelScope.launch {
        pendingUninstalls += packageName
        withContext(Dispatchers.IO) {
            pm.uninstallPackage(packageName)
        }
    }

    fun exportSelectedApps(context: Context, uri: Uri, asArchive: Boolean) =
        viewModelScope.launch {
            val selection = appSelection.toList()
            if (selection.isEmpty()) return@launch

            val resolver = context.contentResolver

            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { output ->
                        if (asArchive) {
                            ZipOutputStream(output).use { zipStream ->
                                selection.forEach { app ->
                                    val apkFile =
                                        downloadedAppRepository.getApkFileForApp(app)
                                    val baseName =
                                        "${app.packageName}_${app.version}".replace('/', '_')
                                    val entry = ZipEntry("$baseName.apk")
                                    zipStream.putNextEntry(entry)
                                    apkFile.inputStream().use { it.copyTo(zipStream) }
                                    zipStream.closeEntry()
                                }
                            }
                        } else {
                            val app = selection.first()
                            val apkFile =
                                downloadedAppRepository.getApkFileForApp(app)
                            apkFile.inputStream().use { input -> input.copyTo(output) }
                        }
                    } ?: error("Could not open output stream for export")
                }
            }.onSuccess {
                context.toast(
                    context.getString(
                        R.string.downloaded_apps_export_success,
                        selection.size
                    )
                )
            }.onFailure {
                Log.e(TAG, "Failed to export downloaded apps", it)
                context.toast(context.getString(R.string.downloaded_apps_export_failed))
            }
        }

    companion object {
        private val TAG = DownloadsViewModel::class.java.simpleName ?: "DownloadsViewModel"
    }

    private suspend fun reloadPlugins() {
        isRefreshingPlugins = true
        downloaderPluginRepository.reload()
        isRefreshingPlugins = false
    }

    override fun onCleared() {
        super.onCleared()
        appContext.unregisterReceiver(uninstallReceiver)
    }
}
