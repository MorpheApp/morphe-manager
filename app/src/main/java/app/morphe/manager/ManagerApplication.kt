/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ManagerApplication.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.WorkManager
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.di.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.BlocklistRepository
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.notification.NotificationChannels
import app.morphe.manager.notification.PatchingNotifications
import app.morphe.manager.notification.UpdateNotifications
import app.morphe.manager.ui.screen.shared.AppIconPixels
import app.morphe.manager.util.*
import app.morphe.manager.worker.UpdateCheckWorker
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File

class ManagerApplication : Application() {
    companion object {
        /**
         * Resumed rather than started activities, because this answers "is the user looking at
         * the result right now". A started activity can sit unfocused beside another app in
         * split screen, where a completion notification is exactly what is wanted.
         */
        @Volatile var resumedActivityCount: Int = 0
            private set

        /** True while a Morphe screen is in focus, so a result needs no notification. */
        val isInForeground: Boolean get() = resumedActivityCount > 0

        /**
         * Run once the next time a Morphe screen comes into focus, for work that Android only
         * allows from the foreground. Cleared before it runs, so it never fires twice.
         */
        @Volatile var onReturnToForeground: (() -> Unit)? = null

        /** Launcher shortcut that opens the batch queue with everything worth re-patching. */
        private const val SHORTCUT_ID_REPATCH = "repatch_outdated"
        private const val SHORTCUT_ID_UPDATES = "check_updates"

        /**
         * Schedule of the automatic re-patching the re-patch alert replaced. Installs that had
         * it on still carry it in the WorkManager database, where it fails on every run.
         */
        private const val LEGACY_AUTO_PATCH_WORK = "morphe_auto_patch"
    }
    private val scope = MainScope()
    private val prefs: PreferencesManager by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val blocklistRepository: BlocklistRepository by inject()
    private val fs: Filesystem by inject()
    private val installedAppRepository: InstalledAppRepository by inject()

    /** Locales the app's own strings resolve in, null until [observeLanguage] first reads them. */
    private val stringLocales = MutableStateFlow<LocaleList?>(null)

    override fun onCreate() {
        super.onCreate()

        // The patcher process only hosts the run, none of the app is of use to it
        if (currentProcessName() != packageName) return

        startKoin {
            androidContext(this@ManagerApplication)
            androidLogger()
            workManagerFactory()
            modules(
                httpModule,
                preferencesModule,
                repositoryModule,
                serviceModule,
                managerModule,
                workerModule,
                viewModelModule,
                databaseModule
            )
        }

        // Set before anything can ask for an avatar, so the first one read already comes from disk
        AvatarCache.useDiskCache(fs.avatarCacheDir)

        // App icon loader (Coil)
        // Non-adaptive icons are drawn as they are rather than shrunk onto a white plate: icon
        // packs on One UI hand out finished bitmap icons, and the plate showed around each of them
        val shrinkNonAdaptiveIcons = false
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(AppIconPixels, shrinkNonAdaptiveIcons, this@ManagerApplication))
                }
                .build()
        )

        // LibSuperuser: always use mount master mode
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
        )

        // Create notification channels before any notification can be posted (required on API 26+)
        NotificationChannels.create(this@ManagerApplication)

        observeLanguage()
        observeLauncherShortcuts()

        // Preload preferences and kick off background worker/FCM sync
        scope.launch {
            prefs.preload()

            // A restored backup carries the token of the device it came from, and nothing here
            // can tell whose it is, so this data starts without one and the user enters theirs
            if (fs.isFirstRunForThisData) prefs.gitHubPat.update("")

            // Schedule/cancel WorkManager fallback AND sync FCM topic subscriptions.
            // FCM is the primary delivery path (bypasses Doze); WorkManager is the fallback
            // for non-GMS devices. syncFcmTopics() subscribes to the correct stable/dev
            // topics based on user preferences, or unsubscribes from all when disabled.
            val notificationsEnabled = prefs.backgroundUpdateNotifications.get()
            val useManagerPrereleases = prefs.useManagerPrereleases.get()
            // Patches FCM topic is determined by the default bundle (uid=0) prerelease toggle
            val usePatchesPrereleases = prefs.bundlePrereleasesEnabled.get().contains(DEFAULT_SOURCE_UID.toString())

            // On GMS devices FCM is the primary delivery channel - WorkManager is not needed.
            // Cancel any previously scheduled jobs on GMS devices
            val hasGms = hasGooglePlayServices()

            if (notificationsEnabled && !hasGms) {
                UpdateCheckWorker.schedule(this@ManagerApplication, prefs.updateCheckInterval.get())
            } else {
                UpdateCheckWorker.cancel(this@ManagerApplication)
            }
            WorkManager.getInstance(this@ManagerApplication).cancelUniqueWork(LEGACY_AUTO_PATCH_WORK)
            syncFcmTopics(
                notificationsEnabled = notificationsEnabled,
                useManagerPrereleases = useManagerPrereleases,
                usePatchesPrereleases = usePatchesPrereleases,
            )
        }

        // First touch of the repository builds the Ktor client, which costs seconds on a cold
        // start, so it happens here on a background dispatcher rather than in the Koin graph
        scope.launch(Dispatchers.Default) {
            patchBundleRepository.reload()
        }

        // Cache first for offline launches, then refresh from the network. Any matches are
        // logged for support diagnostics; the in-app snackbar is state-driven so it updates
        // automatically without a callback here
        scope.launch(Dispatchers.Default) {
            blocklistRepository.loadFromCache()
            blocklistRepository.refresh()
            patchBundleRepository.logBlockedSources()
        }

        // Fresh-start cleanup and the work that waits for a screen
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var firstActivityCreated = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (firstActivityCreated) return
                firstActivityCreated = true

                onFirstScreenCreated()

                // We do not want to call onFreshProcessStart() if there is state to restore.
                // This can happen on system-initiated process death
                if (savedInstanceState == null) {
                    Log.d(tag, "Fresh process created")
                    onFreshProcessStart()
                } else Log.d(tag, "System-initiated process death detected")
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                resumedActivityCount++
                PatchingNotifications.cancelResults(this@ManagerApplication)
                onReturnToForeground?.let {
                    onReturnToForeground = null
                    it()
                }
            }
            override fun onActivityPaused(activity: Activity) { resumedActivityCount-- }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Resources in the app language on Android 12 and lower, where the system does not apply it. */
    private var localizedResources: AppLocale.LocalizedResources? = null

    /**
     * Loads the app language before any Activity or Resources object is created, so strings
     * resolved through the application follow it from the first one on.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        localizedResources = AppLocale.attach(base)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun getResources(): Resources = localizedResources?.get() ?: super.getResources()

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName()
        else File("/proc/self/cmdline").readText().substringBefore('\u0000')

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLocale.onConfigurationChanged(this)
        stringLocales.value = resources.configuration.locales
    }

    /**
     * Channel names and shortcut labels reach the system as plain text, so they stay in the
     * language they were resolved in until they are handed over again.
     */
    private fun observeLanguage() {
        // A language the app switches by itself on Android 12 and lower brings no configuration
        // change. On Android 13+ the strings only change with the configuration, which is where
        // onConfigurationChanged picks them up
        scope.launch {
            AppLocale.selected.collect { stringLocales.value = resources.configuration.locales }
        }
        scope.launch {
            stringLocales.filterNotNull().drop(1).collect {
                NotificationChannels.create(this@ManagerApplication)
            }
        }
    }

    /**
     * Publishes the long-press menu on the launcher icon, relabeled whenever the language changes.
     *
     * Dynamic rather than declared in XML on purpose: the launcher entry is one of several
     * activity aliases that swap with the icon style, and a dynamic shortcut is published for
     * the app as a whole instead of per alias.
     */
    private fun observeLauncherShortcuts() {
        scope.launch(Dispatchers.IO) {
            stringLocales.filterNotNull().collect { publishLauncherShortcuts() }
        }
    }

    private fun publishLauncherShortcuts() {
        val shortcuts = listOf(
            shortcut(
                id = SHORTCUT_ID_REPATCH,
                shortLabel = getString(R.string.shortcut_repatch_short),
                longLabel = getString(R.string.shortcut_repatch_long),
                icon = IconCompat.createWithResource(this, R.drawable.ic_shortcut_repatch),
                rank = 0,
                intent = shortcutIntent(MainActivity.ACTION_BATCH_PATCH)
            ),
            shortcut(
                id = SHORTCUT_ID_UPDATES,
                shortLabel = getString(R.string.shortcut_check_updates_short),
                longLabel = getString(R.string.shortcut_check_updates_long),
                icon = IconCompat.createWithResource(this, R.drawable.ic_shortcut_updates),
                rank = 1,
                intent = shortcutIntent(MainActivity.ACTION_CHECK_UPDATES).apply {
                    putExtra(UpdateNotifications.EXTRA_TRIGGER_UPDATE_CHECK, true)
                }
            )
        )

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
        }.onFailure { Log.w(tag, "Failed to publish launcher shortcuts", it) }
    }

    private fun shortcutIntent(action: String) = Intent(this, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    private fun shortcut(
        id: String,
        shortLabel: String,
        longLabel: String,
        icon: IconCompat,
        rank: Int,
        intent: Intent
    ) = ShortcutInfoCompat.Builder(this, id)
        .setShortLabel(shortLabel)
        .setLongLabel(longLabel)
        .setIcon(icon)
        .setRank(rank)
        // Stamped here rather than at each call site so no shortcut can arrive unnamed
        .setIntent(intent.putExtra(MainActivity.EXTRA_SHORTCUT_ID, id))
        .build()

    /**
     * Work that only pays off once a screen exists. A process started by an FCM push or a boot
     * broadcast has nobody to show an update check or its failures to.
     */
    private fun onFirstScreenCreated() {
        scope.launch(Dispatchers.Default) {
            patchBundleRepository.updateCheck()
        }

        // Preload bundle avatar images into AvatarCache while the user hasn't opened the sheet yet.
        // Suspends until sources are ready, then loads them all in parallel, a fallback only for
        // a source whose own picture does not load
        scope.launch(Dispatchers.IO) {
            patchBundleRepository.sources.first { it.isNotEmpty() }.forEach { bundle ->
                launch {
                    val avatarUrls = bundle.avatarUrls
                    loadAvatar(avatarUrls.primary, avatarUrls.fallback)
                }
            }
        }
    }

    private fun onFreshProcessStart() {
        fs.uiTempDir.purgeDirectoryAsync(scope)
        // Logs all app-private directories and their contents with file sizes on fresh start,
        // once copies no record claims are gone, so the log shows what the storage screen counts
        scope.launch(Dispatchers.IO) {
            installedAppRepository.pruneUnclaimedPatchedApks()
            fs.logStorageContents()
        }
    }
}
