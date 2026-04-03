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
import app.morphe.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_THEME
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.util.KnownApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Option keys used in patch configurations */
object PatchOptionKeys {
    const val DARK_THEME_COLOR = "darkThemeBackgroundColor"
    const val LIGHT_THEME_COLOR = "lightThemeBackgroundColor"
    const val DEFAULT_COLOR_BLACK = "@android:color/black"
    const val DEFAULT_COLOR_LIGHT = "@android:color/white"
    const val CUSTOM_NAME = "customName"
    const val CUSTOM_ICON = "customIcon"
    const val CUSTOM_HEADER = "custom"
    const val HIDE_SHORTS_APP_SHORTCUT = "hideShortsAppShortcut"
    const val HIDE_SHORTS_WIDGET = "hideShortsWidget"
}

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
            PATCH_THEME,
            PATCH_HIDE_SHORTS
        )
    }

    /** Package name for which the Theme Color dialog is open, or null when closed. */
    var showThemeDialogFor: String? by mutableStateOf(null)
        private set

    /** Package name for which the Custom Branding dialog is open, or null when closed. */
    var showBrandingDialogFor: String? by mutableStateOf(null)
        private set

    /** Package name for which the Custom Header dialog is open, or null when closed. */
    var showHeaderDialogFor: String? by mutableStateOf(null)
        private set

    fun openThemeDialog(packageName: String) { showThemeDialogFor = packageName }
    fun openBrandingDialog(packageName: String) { showBrandingDialogFor = packageName }
    fun openHeaderDialog(packageName: String) { showHeaderDialogFor = packageName }
    fun dismissThemeDialog() { showThemeDialogFor = null }
    fun dismissBrandingDialog() { showBrandingDialogFor = null }
    fun dismissHeaderDialog() { showHeaderDialogFor = null }

    /** True when the bundle is still fetching - kept in sync by PatchOptionsSection via [onBundleUpdatingChanged]. */
    var isBundleUpdating: Boolean by mutableStateOf(false)
        private set

    fun onBundleUpdatingChanged(updating: Boolean) { isBundleUpdating = updating }

    /** True when the bundle has loaded but contains no relevant patches. */
    val noPatchesAvailable: Boolean
        get() = !isBundleUpdating && loadError == null &&
                _youtubePatches.value.isEmpty() && _youtubeMusicPatches.value.isEmpty()

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
                    if (patch.name !in ALLOWED_PATCHES) return@forEach

                    val compatiblePackages = patch.compatiblePackages ?: return@forEach

                    // Check which apps this patch is compatible with
                    val isForYouTube = compatiblePackages.any { it.packageName == KnownApps.YOUTUBE }
                    val isForYouTubeMusic = compatiblePackages.any { it.packageName == KnownApps.YOUTUBE_MUSIC }

                    val options = patch.options?.map { option ->
                        OptionInfo(
                            key = option.key,
                            title = option.title,
                            description = option.description,
                            type = option.type.toString(),
                            default = option.default,
                            presets = option.presets,
                            required = option.required
                        )
                    } ?: emptyList()

                    val patchOptionInfo = PatchOptionInfo(
                        patchName = patch.name,
                        description = patch.description,
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

    /**
     * Get Theme patch options for a specific package
     */
    fun getThemeOptions(packageName: String): PatchOptionInfo? =
        patchesForPackage(packageName).find { it.patchName == PATCH_THEME }

    /**
     * Get Custom branding patch options for a specific package
     */
    fun getBrandingOptions(packageName: String): PatchOptionInfo? =
        patchesForPackage(packageName).find { it.patchName == PATCH_CUSTOM_BRANDING }

    /**
     * Get Change header patch options (YouTube only)
     */
    fun getHeaderOptions(): PatchOptionInfo? =
        _youtubePatches.value.find { it.patchName == PATCH_CHANGE_HEADER }

    /**
     * Get Hide Shorts components patch options (YouTube only)
     */
    fun getHideShortsOptions(): PatchOptionInfo? =
        _youtubePatches.value.find { it.patchName == PATCH_HIDE_SHORTS }

    /**
     * Get presets map from patch option info
     */
    fun getOptionPresetsMap(option: OptionInfo): Map<String, Any?> {
        return option.presets ?: emptyMap()
    }

    /**
     * Check if a patch has specific option
     */
    fun hasOption(patchInfo: PatchOptionInfo?, optionKey: String): Boolean {
        return patchInfo?.options?.any { it.key == optionKey } == true
    }

    /**
     * Get specific option from patch
     */
    fun getOption(patchInfo: PatchOptionInfo?, optionKey: String): OptionInfo? {
        return patchInfo?.options?.find { it.key == optionKey }
    }

    /**
     * Returns the default dark theme color from the preset list for [packageName],
     * falling back to the Android system black resource string.
     */
    fun defaultDarkColor(packageName: String): String {
        val option = getOption(getThemeOptions(packageName), PatchOptionKeys.DARK_THEME_COLOR)
        return option?.let { getOptionPresetsMap(it).values.firstOrNull()?.toString() }
            ?: PatchOptionKeys.DEFAULT_COLOR_BLACK
    }

    /**
     * Returns the default light theme color from the preset list for [packageName],
     * falling back to the Android system white resource string.
     */
    fun defaultLightColor(packageName: String): String {
        val option = getOption(getThemeOptions(packageName), PatchOptionKeys.LIGHT_THEME_COLOR)
        return option?.let { getOptionPresetsMap(it).values.firstOrNull()?.toString() }
            ?: PatchOptionKeys.DEFAULT_COLOR_LIGHT
    }

    /**
     * Resets both theme colors to their bundle defaults for [packageName].
     * Light color reset is performed only for YouTube.
     */
    fun resetThemeColors(
        prefs: PatchOptionsPreferencesManager,
        packageName: String,
        isYouTube: Boolean
    ) = viewModelScope.launch {
        prefs.darkThemeColor(packageName).update(defaultDarkColor(packageName))
        if (isYouTube) prefs.lightThemeColor(packageName).update(defaultLightColor(packageName))
    }

    /** Persists custom branding values atomically and calls [onDone] when finished. */
    fun saveCustomBranding(
        prefs: PatchOptionsPreferencesManager,
        packageName: String,
        appName: String,
        iconPath: String,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        prefs.edit {
            prefs.customAppName(packageName).value  = appName
            prefs.customIconPath(packageName).value = iconPath
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

/**
 * Data class representing a patch with its options
 */
data class PatchOptionInfo(
    val patchName: String,
    val description: String?,
    val options: List<OptionInfo>
)

/**
 * Data class representing a single option within a patch
 */
data class OptionInfo(
    val key: String,
    val title: String,
    val description: String,
    val type: String,
    val default: Any?,
    val presets: Map<String, Any?>?,
    val required: Boolean
)
