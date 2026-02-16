package app.morphe.manager

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.morphe.manager.data.platform.Filesystem
import app.morphe.manager.di.*
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.applyAppLanguage
import app.morphe.manager.util.tag
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
import androidx.core.content.edit

class ManagerApplication : Application() {
    private val scope = MainScope()
    private val prefs: PreferencesManager by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val fs: Filesystem by inject()

    override fun onCreate() {
        super.onCreate()

        // ============================================================================
        // TEMPORARY MIGRATION CODE - Remove after sufficient adoption period (e.g., 3-6 months)
        // TODO: Remove this migration after most users have migrated
        // ============================================================================
        // Migrate app icons BEFORE Koin initialization
        migrateAppIcons()
        // ============================================================================

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

        // Preload preferences + initialize repositories
        scope.launch {
            prefs.preload()
            val storedLanguage = prefs.appLanguage.get().ifBlank { "system" }
            if (storedLanguage != prefs.appLanguage.get()) {
                prefs.appLanguage.update(storedLanguage)
            }
            applyAppLanguage(storedLanguage)
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

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        // Apply stored app language as early as possible using DataStore, but never crash startup.
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

    // ============================================================================
    // TEMPORARY MIGRATION CODE - Remove after sufficient adoption period (e.g., 3-6 months)
    // ============================================================================

    /**
     * Disable old icon components from previous package name.
     * This prevents the app from having duplicate launcher icons.
     *
     * TODO: Remove this entire function after most users have migrated (recommended: 3-6 months after release)
     */
    private fun migrateAppIcons() {
        val pm = packageManager
        val oldPackage = "app.revanced.manager"

        // Check if migration is needed
        val migrationKey = "app_icon_components_disabled"
        val prefs = getSharedPreferences("migration", MODE_PRIVATE)
        if (prefs.getBoolean(migrationKey, false)) {
            return // Migration already done
        }

        try {
            // List of old icon component names
            val oldIcons = listOf(
                "MainActivity_Default",
                "MainActivity_Light_2",
                "MainActivity_Light_3",
                "MainActivity_Dark_1",
                "MainActivity_Dark_2",
                "MainActivity_Dark_3"
            )

            // Disable all old components to prevent duplicate icons
            for (iconName in oldIcons) {
                val oldComponent = ComponentName(oldPackage, "$oldPackage.$iconName")
                try {
                    pm.setComponentEnabledSetting(
                        oldComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    Log.d(tag, "Disabled old icon component: $oldComponent")
                } catch (_: Exception) {
                    // Component doesn't exist or already disabled, continue
                    Log.d(tag, "Old icon component not found or already disabled: $oldComponent")
                }
            }

            // Mark migration as complete
            prefs.edit { putBoolean(migrationKey, true) }
            Log.d(tag, "Old app icon components migration completed - users will see default icon")

        } catch (e: Exception) {
            Log.e(tag, "Failed to disable old icon components", e)
            // Don't crash the app, just log the error
        }
    }

    // ============================================================================
    // END OF TEMPORARY MIGRATION CODE
    // ============================================================================
}
