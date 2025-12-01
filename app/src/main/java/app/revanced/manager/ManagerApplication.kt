package app.revanced.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.revanced.manager.data.platform.Filesystem
import app.revanced.manager.di.*
import app.revanced.manager.domain.bundles.PatchBundleSource.Extensions.asRemoteOrNull
import app.revanced.manager.domain.manager.PreferencesManager
import app.revanced.manager.domain.repository.DownloaderPluginRepository
import app.revanced.manager.domain.repository.PatchBundleRepository
import app.revanced.manager.util.tag
import kotlinx.coroutines.Dispatchers
import coil.Coil
import coil.ImageLoader
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.BuilderImpl
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
    private val scope = MainScope()
    private val prefs: PreferencesManager by inject()
    private val patchBundleRepository: PatchBundleRepository by inject()
    private val downloaderPluginRepository: DownloaderPluginRepository by inject()
    private val fs: Filesystem by inject()

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

        val pixels = 512
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(pixels, true, this@ManagerApplication))
                }
                .build()
        )

        val shellBuilder = BuilderImpl.create().setFlags(Shell.FLAG_MOUNT_MASTER)
        Shell.setDefaultBuilder(shellBuilder)

        scope.launch {
            prefs.preload()
            val currentApi = prefs.api.get()
            if (currentApi == LEGACY_MANAGER_REPO_URL || currentApi == LEGACY_MANAGER_REPO_API_URL) {
                prefs.api.update(DEFAULT_API_URL)
            }
        }
        scope.launch(Dispatchers.Default) {
            downloaderPluginRepository.reload()
        }
        scope.launch(Dispatchers.Default) {
            with(patchBundleRepository) {
                reload()
                updateCheck()
            }
        }
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

    private companion object {
        private const val DEFAULT_API_URL = "https://api.revanced.app"
        private const val LEGACY_MANAGER_REPO_URL = "https://github.com/Jman-Github/universal-revanced-manager"
        private const val LEGACY_MANAGER_REPO_API_URL = "https://api.github.com/repos/Jman-Github/universal-revanced-manager"
    }
}
