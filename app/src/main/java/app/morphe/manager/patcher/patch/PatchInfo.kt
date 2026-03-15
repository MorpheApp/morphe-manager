package app.morphe.manager.patcher.patch

import androidx.compose.runtime.Immutable
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.resourcePatch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.reflect.KType
import app.morphe.patcher.patch.Option as PatchOption

data class PatchInfo(
    val name: String,
    val description: String?,
    val include: Boolean,
    val compatiblePackages: List<Compatibility>?,
    val options: ImmutableList<Option<*>>?
) {
    constructor(patch: Patch<*>) : this(
        patch.name.orEmpty(),
        patch.description,
        patch.use,
        patch.compatibility,
        patch.options.map { (_, option) -> Option(option) }.ifEmpty { null }?.toImmutableList()
    )

    fun compatibleWith(packageName: String) =
        compatiblePackages?.any { it.packageName == packageName } != false

    fun supports(packageName: String, versionName: String?): Boolean {
        val packages = compatiblePackages ?: return true // Universal patch

        return packages.any { pkg ->
            pkg.packageName == null || pkg.packageName == packageName ||
                    pkg.targets.any { it.version == versionName }
        }
    }

    /**
     * Create a fake [Patch] with the same metadata as the [PatchInfo] instance.
     * The resulting patch cannot be executed.
     * This is necessary because some functions in Morphe Library only accept full [Patch] objects.
     */
    fun toPatcherPatch(): Patch<*> =
        resourcePatch(name = name, description = description, use = include) {
            compatiblePackages?.let { pkgs ->
                compatibleWith(*pkgs.toTypedArray())
            }
        }
}

@Immutable
data class Option<T>(
    val title: String,
    val key: String,
    val description: String,
    val required: Boolean,
    val type: KType,
    val default: T?,
    val presets: Map<String, T?>?,
    val validator: (T?) -> Boolean,
) {
    constructor(option: PatchOption<T>) : this(
        option.title ?: option.key,
        option.key,
        option.description.orEmpty(),
        option.required,
        option.type,
        option.default,
        option.values,
        { option.validator(option, it) },
    )
}