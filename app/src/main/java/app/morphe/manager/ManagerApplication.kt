package app.morphe.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.di.*
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.UpdateNotificationManager
import app.morphe.manager.util.applyAppLanguage
import app.morphe.manager.util.tag
import app.morphe.manager.worker.UpdateCheckWorker
import app.morphe.manager.util.syncFcmTopics
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ManagerApplication : Application() {
    private val scope = MainScope()
    private val prefs: PreferencesManager by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val fs: Filesystem by inject()
    private val updateNotificationManager: UpdateNotificationManager by inject()

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
                databaseModule,
                rootModule
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

        // Preload preferences and kick off background worker/FCM sync.
        scope.launch {
            prefs.preload()

            // Schedule/cancel WorkManager fallback AND sync FCM topic subscriptions.
            // FCM is the primary delivery path (bypasses Doze); WorkManager is the fallback
            // for non-GMS devices. syncFcmTopics() subscribes to the correct stable/dev
            // topics based on user preferences, or unsubscribes from all when disabled.
            val notificationsEnabled = prefs.backgroundUpdateNotifications.get()
            val useManagerPrereleases = prefs.useManagerPrereleases.get()
            // Patches FCM topic is determined by the default bundle (uid=0) prerelease toggle.
            val usePatchesPrereleases = prefs.bundlePrereleasesEnabled.get().contains("0")

            // On GMS devices FCM is the primary delivery channel - WorkManager is not needed.
            // Cancel any previously scheduled jobs on GMS devices.
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

        scope.launch(Dispatchers.Default) {
            with(patchBundleRepository) {
                reload()
                updateCheck()
            }
        }

        // Clean temp dir on fresh start
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var firstActivityCreated = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (firstActivityCreated) return
                firstActivityCreated = true

                // We do not want to call onFreshProcessStart() if there is state to restore.
                // This can happen on system-initiated process death.
                if (savedInstanceState == null) {
                    Log.d(tag, "Fresh process created")
                    onFreshProcessStart()
                } else Log.d(tag, "System-initiated process death detected")
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
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

        val storedLang = runCatching {
            base?.let {
                runBlocking { PreferencesManager(it).appLanguage.get() }.ifBlank { "system" }
            }
        }.getOrNull() ?: "system"

        applyAppLanguage(storedLang)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    private fun onFreshProcessStart() {
        fs.uiTempDir.apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
