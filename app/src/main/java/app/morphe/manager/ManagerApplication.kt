package app.morphe.manager

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.morphe.manager.BuildConfig
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
     * Migrate app icons from old package name to new one
     * This is a one-time migration for existing users
     *
     * TODO: Remove this entire function after most users have migrated (recommended: 3-6 months after release)
     */
    private fun migrateAppIcons() {
        val pm = packageManager
        val oldPackage = "app.revanced.manager"
        val newPackage = packageName // Current package name (app.morphe.manager)

        // Check if migration is needed
        val migrationKey = "app_icon_migration_completed"
        val prefs = getSharedPreferences("migration", Context.MODE_PRIVATE)
        if (prefs.getBoolean(migrationKey, false)) {
            return // Migration already done
        }

        try {
            // Find which old icon was enabled
            val oldIcons = listOf(
                "MainActivity_Default",
                "MainActivity_Light_2",
                "MainActivity_Light_3",
                "MainActivity_Dark_1",
                "MainActivity_Dark_2",
                "MainActivity_Dark_3"
            )

            var enabledOldIcon: String? = null

            for (iconName in oldIcons) {
                val oldComponent = ComponentName(oldPackage, "$oldPackage.$iconName")
                try {
                    val state = pm.getComponentEnabledSetting(oldComponent)
                    if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                        enabledOldIcon = iconName
                        // Disable old component
                        pm.setComponentEnabledSetting(
                            oldComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                        Log.d(tag, "Disabled old icon component: $oldComponent")
                    }
                } catch (_: Exception) {
                    // Component doesn't exist, continue
                    Log.d(tag, "Old icon component not found: $oldComponent")
                }
            }

            // If a custom icon was enabled, enable the corresponding new one
            if (enabledOldIcon != null && enabledOldIcon != "MainActivity_Default") {
                val newComponent = ComponentName(newPackage, "$newPackage.$enabledOldIcon")

                // Disable default new icon
                val defaultComponent = ComponentName(newPackage, "$newPackage.MainActivity_Default")
                pm.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )

                // Enable the migrated icon
                pm.setComponentEnabledSetting(
                    newComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0 // Will restart app
                )

                Log.d(tag, "Successfully migrated app icon from $enabledOldIcon to new package")
            } else {
                Log.d(tag, "No custom icon migration needed (using default icon)")
            }

            // Mark migration as complete
            prefs.edit { putBoolean(migrationKey, true) }
            Log.d(tag, "App icon migration completed")

        } catch (e: Exception) {
            Log.e(tag, "Failed to migrate app icons", e)
            // Don't crash the app, just log the error
        }
    }

    // ============================================================================
    // END OF TEMPORARY MIGRATION CODE
    // ============================================================================
}
