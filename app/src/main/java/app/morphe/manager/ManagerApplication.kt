package app.morphe.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.di.*
import app.morphe.manager.domain.bundles.PatchBundleSource.Extensions.avatarUrls
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.BlocklistRepository
import app.morphe.manager.domain.repository.InstalledAppRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.repository.PatchBundleRepository.Companion.DEFAULT_SOURCE_UID
import app.morphe.manager.util.*
import app.morphe.manager.worker.UpdateCheckWorker
import coil.Coil
import coil.ImageLoader
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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

        /** Launcher shortcut that opens the batch queue with everything worth re-patching. */
        private const val SHORTCUT_ID_REPATCH = "repatch_outdated"
        private const val SHORTCUT_ID_UPDATES = "check_updates"
        private const val SHORTCUT_ID_PATCH_PREFIX = "patch_"

        /** Launchers show about four entries in the long-press menu. */
        private const val MIN_SHORTCUT_SLOTS = 2
        private const val MAX_SHORTCUT_SLOTS = 4
        private const val SHORTCUT_ICON_PX = 192
    }
    private val scope = MainScope()
    private val prefs: PreferencesManager by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val blocklistRepository: BlocklistRepository by inject()
    private val fs: Filesystem by inject()
    private val updateNotificationManager: UpdateNotificationManager by inject()
    private val installedAppRepository: InstalledAppRepository by inject()
    private val appDataResolver: AppDataResolver by inject()

    override fun onCreate() {
        super.onCreate()

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

        // App icon loader (Coil)
        val pixels = 512
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(pixels, true, this@ManagerApplication))
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
        updateNotificationManager.createNotificationChannels()

        observeLauncherShortcuts()

        // Preload preferences and kick off background worker/FCM sync
        scope.launch {
            prefs.preload()

            // A restored backup carries the token of the device it came from, and nothing here
            // can tell whose it is, so this data starts without one and the user enters theirs
            if (fs.isFirstRunForThisData) prefs.gitHubPat.update("")

            // Keep SharedPreferences in sync with DataStore so that attachBaseContext
            // (Application + Activity) can read the language without touching DataStore
            saveLanguageToPrefs(this@ManagerApplication, prefs.appLanguage.get().ifBlank { "system" })

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
            val hasGms = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this@ManagerApplication) == ConnectionResult.SUCCESS

            if (notificationsEnabled && !hasGms) {
                UpdateCheckWorker.schedule(this@ManagerApplication, prefs.updateCheckInterval.get())
            } else {
                UpdateCheckWorker.cancel(this@ManagerApplication)
            }
            syncFcmTopics(
                notificationsEnabled = notificationsEnabled,
                useManagerPrereleases = useManagerPrereleases,
                usePatchesPrereleases = usePatchesPrereleases,
            )
        }

        // First touch of the repository builds the Ktor client, which costs seconds on a cold
        // start, so it happens here on a background dispatcher rather than in the Koin graph
        scope.launch(Dispatchers.Default) {
            with(patchBundleRepository) {
                reload()
                updateCheck()
            }
        }

        // Cache first for offline launches, then refresh from the network. Any matches are
        // logged for support diagnostics; the in-app snackbar is state-driven so it updates
        // automatically without a callback here
        scope.launch(Dispatchers.Default) {
            blocklistRepository.loadFromCache()
            blocklistRepository.refresh()
            patchBundleRepository.logBlockedSources()
        }

        // Preload bundle avatar images into AvatarCache while the user hasn't opened the sheet yet.
        // Suspends until sources are ready, then fetches all URLs in parallel on IO threads
        scope.launch(Dispatchers.IO) {
            patchBundleRepository.sources.first { it.isNotEmpty() }.forEach { bundle ->
                launch {
                    val avatarUrls = bundle.avatarUrls
                    avatarUrls.primary?.let { loadRemoteAvatar(it) }
                    avatarUrls.fallback?.let { loadRemoteAvatar(it) }
                }
            }
        }

        // Clean temp dir on fresh start
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var firstActivityCreated = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (firstActivityCreated) return
                firstActivityCreated = true

                // We do not want to call onFreshProcessStart() if there is state to restore.
                // This can happen on system-initiated process death
                if (savedInstanceState == null) {
                    Log.d(tag, "Fresh process created")
                    onFreshProcessStart()
                } else Log.d(tag, "System-initiated process death detected")
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) { resumedActivityCount++ }
            override fun onActivityPaused(activity: Activity) { resumedActivityCount-- }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Apply the stored app language as early as possible - before any Activity or
     * Resources object is created. This is the **single place** where locale is applied
     * on cold start.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }

        val storedLang = base?.let { readLanguageFromPrefs(it) } ?: return
        applyAppLanguage(storedLang)
    }

    /**
     * Keeps the long-press menu on the launcher icon in sync with the patched apps.
     *
     * Dynamic rather than declared in XML on purpose: the launcher entry is one of several
     * activity aliases that swap with the icon style, and a dynamic shortcut is published for
     * the app as a whole instead of per alias.
     */
    private fun observeLauncherShortcuts() {
        scope.launch(Dispatchers.IO) {
            installedAppRepository.getAll().collect { apps -> publishLauncherShortcuts(apps) }
        }
    }

    private suspend fun publishLauncherShortcuts(installedApps: List<InstalledApp>) {
        // The system allows far more than a launcher ever shows, so publish only what fits in
        // the long-press menu instead of turning every patched app into a shortcut
        val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(this)
            .coerceIn(MIN_SHORTCUT_SLOTS, MAX_SHORTCUT_SLOTS)

        val shortcuts = mutableListOf(
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
                    putExtra(UpdateNotificationManager.EXTRA_TRIGGER_UPDATE_CHECK, true)
                }
            )
        )

        // The apps patched most recently are the ones most likely to be patched again
        installedApps
            .sortedByDescending { it.patchedAt ?: 0L }
            .take(maxShortcuts - shortcuts.size)
            .forEachIndexed { index, app ->
                // Patching renames packages, so an app that was saved but never installed can
                // only be named from its saved APK, the same source the home screen reads
                val appData = appDataResolver.resolveAppData(
                    packageName = app.originalPackageName,
                    preferredSource = AppDataSource.ORIGINAL_APK
                )

                shortcuts += shortcut(
                    id = "$SHORTCUT_ID_PATCH_PREFIX${app.originalPackageName}",
                    shortLabel = appData.displayName,
                    longLabel = getString(R.string.shortcut_patch_app, appData.displayName),
                    icon = appIcon(appData.icon),
                    rank = shortcuts.size + index,
                    intent = shortcutIntent(MainActivity.ACTION_PATCH_APP).apply {
                        putExtra(MainActivity.EXTRA_PATCH_PACKAGE, app.originalPackageName)
                    }
                )
            }

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts.take(maxShortcuts))
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
        .setIntent(intent)
        .build()

    /**
     * Real app icon so the shortcut reads like the app it patches, with the wand as a
     * fallback for apps whose icon cannot be resolved.
     */
    private fun appIcon(icon: Drawable?): IconCompat = runCatching {
        icon?.let { IconCompat.createWithBitmap(it.toBitmap(SHORTCUT_ICON_PX, SHORTCUT_ICON_PX)) }
    }.getOrNull()
        ?: IconCompat.createWithResource(this, R.drawable.ic_shortcut_repatch)

    private fun onFreshProcessStart() {
        fs.uiTempDir.apply {
            deleteRecursively()
            mkdirs()
        }
        // Logs all app-private directories and their contents with file sizes on fresh start
        scope.launch(Dispatchers.IO) {
            fs.logStorageContents()
        }
    }
}
