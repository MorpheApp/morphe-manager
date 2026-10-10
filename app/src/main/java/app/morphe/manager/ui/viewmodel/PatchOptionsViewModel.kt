/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_CHANGE_HEADER
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_CUSTOM_BRANDING
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_HIDE_SHORTS
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.ExplicitOptionKind
import app.morphe.manager.util.KnownApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Managing patch options dynamically loaded from bundle repository.
 */
class PatchOptionsViewModel : ViewModel(), KoinComponent {
    private val bundleRepository: PatchBundleRepository by inject()
    val patchOptionsPrefs: PatchOptionsPreferencesManager by inject()

    companion object {
        // Patch names to show options for
        private val ALLOWED_PATCHES = setOf(
            PATCH_CUSTOM_BRANDING,
            PATCH_CHANGE_HEADER,
            PATCH_HIDE_SHORTS
        )
    }

    /** Package name for which the Custom Branding dialog is open, or null when closed. */
    var showBrandingDialogFor: String? by mutableStateOf(null)
        private set

    /** Package name for which the Custom Header dialog is open, or null when closed. */
    var showHeaderDialogFor: String? by mutableStateOf(null)
        private set

    fun openBrandingDialog(packageName: String) { showBrandingDialogFor = packageName }
    fun openHeaderDialog(packageName: String) { showHeaderDialogFor = packageName }
    fun dismissBrandingDialog() { showBrandingDialogFor = null }
    fun dismissHeaderDialog() { showHeaderDialogFor = null }

    // State for loading
    var isLoading by mutableStateOf(true)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    // Patch options state
    private val _youtubePatches = MutableStateFlow<List<PatchOptionInfo>>(emptyList())
    val youtubePatches: StateFlow<List<PatchOptionInfo>> = _youtubePatches.asStateFlow()

    private val _youtubeMusicPatches = MutableStateFlow<List<PatchOptionInfo>>(emptyList())
    val youtubeMusicPatches: StateFlow<List<PatchOptionInfo>> = _youtubeMusicPatches.asStateFlow()

    init {
        loadPatchOptions()
    }

    fun refresh() {
        loadPatchOptions()
    }

    private fun loadPatchOptions() {
        viewModelScope.launch {
            isLoading = true
            loadError = null

            try {
                val bundleInfo = bundleRepository.bundleInfoFlow.first()
                val defaultBundle = bundleInfo[PatchBundleRepository.DEFAULT_SOURCE_UID]

                if (defaultBundle == null) {
                    loadError = "No patch bundle available"
                    isLoading = false
                    return@launch
                }

                val youtubeOptions = mutableListOf<PatchOptionInfo>()
                val youtubeMusicOptions = mutableListOf<PatchOptionInfo>()

                defaultBundle.patches.forEach { patch ->
                    // Only process allowed patches
                    if (patch.displayName !in ALLOWED_PATCHES) return@forEach

                    val compatiblePackages = patch.compatiblePackages ?: return@forEach

                    // Check which apps this patch is compatible with
                    val isForYouTube = compatiblePackages.any { it.packageName == KnownApps.YOUTUBE }
                    val isForYouTubeMusic = compatiblePackages.any { it.packageName == KnownApps.YOUTUBE_MUSIC }

                    val options = patch.options?.map { option ->
                        OptionInfo(
                            key = option.key,
                            title = option.title,
                            description = option.description,
                            required = option.required,
                            explicitKind = option.explicitKind,
                            presets = option.presets
                        )
                    } ?: emptyList()

                    val patchOptionInfo = PatchOptionInfo(
                        patchName = patch.name,
                        options = options
                    )

                    if (isForYouTube) youtubeOptions.add(patchOptionInfo)
                    if (isForYouTubeMusic) youtubeMusicOptions.add(patchOptionInfo)
                }

                _youtubePatches.value = youtubeOptions
                _youtubeMusicPatches.value = youtubeMusicOptions

            } catch (e: Exception) {
                loadError = e.message ?: "Failed to load patch options"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Returns the patch list for the given package name.
     */
    private fun patchesForPackage(packageName: String): List<PatchOptionInfo> = when (packageName) {
        KnownApps.YOUTUBE -> _youtubePatches.value
        KnownApps.YOUTUBE_MUSIC -> _youtubeMusicPatches.value
        else -> emptyList()
    }

    /** Get Custom branding patch options for a specific package. */
    fun getBrandingOptions(packageName: String): PatchOptionInfo? =
        patchesForPackage(packageName).find { it.patchName == PATCH_CUSTOM_BRANDING }

    /** Get Change header patch options. */
    fun getHeaderOptions(packageName: String): PatchOptionInfo? =
        patchesForPackage(packageName).find { it.patchName == PATCH_CHANGE_HEADER }

    /**
     * Get Hide Shorts components patch options (YouTube only).
     */
    fun getHideShortsOptions(): PatchOptionInfo? =
        _youtubePatches.value.find { it.patchName == PATCH_HIDE_SHORTS }

    /** Check if a patch has specific option. */
    fun hasOption(patchInfo: PatchOptionInfo?, optionKey: String): Boolean {
        return patchInfo?.options?.any { it.key == optionKey } == true
    }

    /** Get specific option from patch. */
    fun getOption(patchInfo: PatchOptionInfo?, optionKey: String): OptionInfo? {
        return patchInfo?.options?.find { it.key == optionKey }
    }

    /** Persists custom branding values atomically and calls [onDone] when finished. */
    fun saveCustomBranding(
        prefs: PatchOptionsPreferencesManager,
        packageName: String,
        appName: String,
        iconPath: String,
        appIconStyle: String,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        prefs.edit {
            prefs.customAppName(packageName).value   = appName
            prefs.customIconPath(packageName).value  = iconPath
            prefs.appIconStyle(packageName).value    = appIconStyle
        }
        onDone()
    }

    /** Persists the custom header path and calls [onDone] when finished. */
    fun saveCustomHeader(
        prefs: PatchOptionsPreferencesManager,
        packageName: String,
        headerPath: String,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        prefs.customHeaderPath(packageName).update(headerPath)
        onDone()
    }

    fun toggleHideShortsAppShortcut(prefs: PatchOptionsPreferencesManager, current: Boolean) =
        viewModelScope.launch { prefs.hideShortsAppShortcut.update(!current) }

    fun toggleHideShortsWidget(prefs: PatchOptionsPreferencesManager, current: Boolean) =
        viewModelScope.launch { prefs.hideShortsWidget.update(!current) }
}

/** Data class representing a patch with its options */
data class PatchOptionInfo(
    val patchName: String,
    val options: List<OptionInfo>
)

/** Data class representing a single option within a patch. */
data class OptionInfo(
    val key: String,
    val title: String,
    val description: String,
    val required: Boolean,
    val explicitKind: ExplicitOptionKind? = null,
    /** Values the option declares, as display name to value. Null when it accepts free input. */
    val presets: Map<String, Any?>? = null
)
