package app.revanced.manager.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PACKAGE_YOUTUBE_MUSIC
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_CHANGE_HEADER
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_CUSTOM_BRANDING
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_HIDE_SHORTS
import app.revanced.manager.domain.manager.PatchOptionsPreferencesManager.Companion.PATCH_THEME
import app.revanced.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Option keys used in patch configurations
 */
object PatchOptionKeys {
    const val DARK_THEME_COLOR = "darkThemeBackgroundColor"
    const val LIGHT_THEME_COLOR = "lightThemeBackgroundColor"
    const val CUSTOM_NAME = "customName"
    const val CUSTOM_ICON = "customIcon"
    const val CUSTOM_HEADER = "custom"
    const val HIDE_SHORTS_APP_SHORTCUT = "hideShortsAppShortcut"
    const val HIDE_SHORTS_WIDGET = "hideShortsWidget"
}

/**
 * ViewModel for managing patch options dynamically loaded from bundle repository.
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

                    // Check if patch is for YouTube
                    val isForYouTube = compatiblePackages.any { it.packageName == PACKAGE_YOUTUBE }
                    val isForYouTubeMusic = compatiblePackages.any { it.packageName == PACKAGE_YOUTUBE_MUSIC }

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

                    if (isForYouTube) {
                        youtubeOptions.add(patchOptionInfo)
                    }
                    if (isForYouTubeMusic) {
                        youtubeMusicOptions.add(patchOptionInfo)
                    }
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
     * Get Theme patch options for a specific package
     */
    fun getThemeOptions(packageName: String): PatchOptionInfo? {
        val patches = when (packageName) {
            PACKAGE_YOUTUBE -> _youtubePatches.value
            PACKAGE_YOUTUBE_MUSIC -> _youtubeMusicPatches.value
            else -> emptyList()
        }
        return patches.find { it.patchName == "Theme" }
    }

    /**
     * Get Custom branding patch options for a specific package
     */
    fun getBrandingOptions(packageName: String): PatchOptionInfo? {
        val patches = when (packageName) {
            PACKAGE_YOUTUBE -> _youtubePatches.value
            PACKAGE_YOUTUBE_MUSIC -> _youtubeMusicPatches.value
            else -> emptyList()
        }
        return patches.find { it.patchName == "Custom branding" }
    }

    /**
     * Get Change header patch options (YouTube only)
     */
    fun getHeaderOptions(): PatchOptionInfo? {
        return _youtubePatches.value.find { it.patchName == "Change header" }
    }

    /**
     * Get Hide Shorts components patch options (YouTube only)
     */
    fun getHideShortsOptions(): PatchOptionInfo? {
        return _youtubePatches.value.find { it.patchName == "Hide Shorts components" }
    }

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
