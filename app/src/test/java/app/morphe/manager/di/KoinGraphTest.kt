/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import app.morphe.manager.service.ForegroundHost
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.ui.viewmodel.HomeApps
import app.morphe.manager.ui.viewmodel.HomeSources
import app.morphe.manager.ui.viewmodel.InstalledAppInfoViewModel
import app.morphe.manager.ui.viewmodel.PatchFlow
import app.morphe.manager.ui.viewmodel.PatcherViewModel
import kotlinx.coroutines.CoroutineScope
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.modules.SerializersModule
import okhttp3.OkHttpClient
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Every definition the app starts can be built: each constructor parameter has a definition, or
 * is handed in where the object is created. A gap here compiles fine and fails only at runtime,
 * on the screen that first asks for the object.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinGraphTest {
    @Test
    fun `every definition can be built`() {
        module {
            includes(
                httpModule,
                preferencesModule,
                repositoryModule,
                serviceModule,
                managerModule,
                workerModule,
                viewModelModule,
                databaseModule
            )
        }.verify(
            extraTypes = listOf(
                // Provided by androidContext() when the app starts
                Application::class,
                Context::class,
                // Handed to each worker by WorkManager, and to each view model by koinViewModel
                WorkerParameters::class,
                SavedStateHandle::class,
                // Parameters of library constructors the definitions call with values of their own
                HttpClientEngine::class,
                HttpClientConfig::class,
                OkHttpClient.Builder::class,
                JsonConfiguration::class,
                SerializersModule::class,
                ForegroundHost::class
            ),
            // What the places creating these hand in with parametersOf
            injections = injectedParameters(
                definition<HomeApps>(CoroutineScope::class),
                definition<HomeSources>(CoroutineScope::class),
                definition<PatchFlow>(CoroutineScope::class, StateFlow::class),
                definition<PatcherViewModel>(Patcher.ViewModelParams::class),
                definition<InstalledAppInfoViewModel>(String::class)
            )
        )
    }
}
