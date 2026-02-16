package app.morphe.manager.patcher.split

import android.util.Log
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ZipEntryMap
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.container.SpecTypePair
import com.reandroid.arsc.header.TableHeader
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.CoderMalfunctionError
import java.nio.file.Path
import java.util.Locale

private class ApkEditorLogger(
    private val onProgress: ((String) -> Unit)? = null
) : APKLogger {
    private companion object {
        const val TAG = "APKEditor"
        val MERGE_PATTERN = Regex("Merging\\s*:?\\s*(.+)", RegexOption.IGNORE_CASE)
    }

    override fun logMessage(msg: String) {
        Log.i(TAG, msg)
        emitMergeProgress(msg)
    }

    override fun logError(msg: String, tr: Throwable?) {
        Log.e(TAG, msg, tr)
    }

    override fun logVerbose(msg: String) {
        Log.v(TAG, msg)
        emitMergeProgress(msg)
    }

    private fun emitMergeProgress(message: String) {
        val match = MERGE_PATTERN.find(message) ?: return
        val moduleName = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val normalized = normalizeMergeModuleName(moduleName)
        if (normalized.isBlank()) return
        onProgress?.invoke("Merging $normalized")
    }

    private fun normalizeMergeModuleName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.lowercase(Locale.ROOT).endsWith(".apk")) {
            trimmed
        } else {
            "$trimmed.apk"
        }
    }
}

internal object Merger {
    suspend fun merge(
        apkDir: Path,
        outputApk: File,
        skipModules: Set<String> = emptySet(),
        onProgress: ((String) -> Unit)? = null,
        sortApkEntries: Boolean = false
    ) {
        val closeables = mutableSetOf<Closeable>()
        try {
            val merged = withContext(Dispatchers.Default) {
                try {
                    val logger = ApkEditorLogger(onProgress)
                    val bundle = ApkBundle().apply {
                        setAPKLogger(logger)
                        loadApkDirectory(apkDir.toFile())
                    }
                    val modules = bundle.apkModuleList
                    if (modules.isEmpty()) {
                        throw FileNotFoundException("Nothing to merge, empty modules")
                    }

                    val skipped = skipModules
                        .map { it.lowercase() }
                        .toSet()
                    if (skipped.isNotEmpty()) {
                        val skipLookup = skipped.map(::normalizeModuleName).toSet()
                        val baseModule = bundle.baseModule
                        bundle.apkModuleList.toList().forEach { module ->
                            if (module === baseModule) return@forEach
                            val normalized = normalizeModuleName(module.moduleName)
                            if (skipLookup.contains(normalized)) {
                                bundle.removeApkModule(module.moduleName)
                            }
                        }
                    }

                    closeables.add(bundle)

                    val mergedModule = bundle.mergeModules(false).apply {
                        setAPKLogger(logger)
                        setLoadDefaultFramework(false)
                    }
                    closeables.add(mergedModule)

                    if (sortApkEntries && mergedModule.hasTableBlock()) {
                        val table = mergedModule.tableBlock
                        table.sortPackages()
                        table.refresh()
                    }
                    if (sortApkEntries) {
                        mergedModule.zipEntryMap.autoSortApkFiles()
                    }
                    mergedModule
                } catch (error: Throwable) {
                    val cause = error.cause
                    if (error is CoderMalfunctionError ||
                        error is IllegalArgumentException && error.message?.contains("newPosition > limit") == true ||
                        cause is CoderMalfunctionError ||
                        cause is IllegalArgumentException && cause.message?.contains("newPosition > limit") == true
                    ) {
                        throw IOException(
                            "Failed to merge split APK resources. The split set may be incomplete, corrupted, or unsupported.",
                            error
                        )
                    }
                    throw error
                }
            }

            merged.androidManifest.apply {
                arrayOf(
                    AndroidManifest.ID_isSplitRequired,
                    AndroidManifest.ID_requiredSplitTypes,
                    AndroidManifest.ID_splitTypes
                ).forEach { id ->
                    applicationElement.removeAttributesWithId(id)
                    manifestElement.removeAttributesWithId(id)
                }

                arrayOf(
                    AndroidManifest.NAME_requiredSplitTypes,
                    AndroidManifest.NAME_splitTypes
                ).forEach { attrName ->
                    manifestElement.removeAttributeIf { attribute ->
                        attribute.name == attrName
                    }
                }

                // Remove split requirements so the merged APK installs as a single package.
                manifestElement.removeElementsIf { element ->
                    element.name == "uses-split"
                }
                arrayOf("splitName", "split").forEach { attrName ->
                    manifestElement.removeAttributeIf { attribute ->
                        attribute.name == attrName
                    }
                    applicationElement.removeAttributeIf { attribute ->
                        attribute.name == attrName
                    }
                }

                applicationElement.removeElementsIf { element ->
                    if (element.name != AndroidManifest.TAG_meta_data) return@removeElementsIf false
                    val nameAttr = element
                        .getAttributes { it.nameId == AndroidManifest.ID_name }
                        .asSequence()
                        .singleOrNull()
                        ?: return@removeElementsIf false
                    val nameValue = nameAttr.valueString ?: return@removeElementsIf false
                    val shouldRemove = when {
                        nameValue == "com.android.dynamic.apk.fused.modules" -> {
                            val valueAttr = element
                                .getAttributes { it.nameId == AndroidManifest.ID_value }
                                .asSequence()
                                .firstOrNull()
                            valueAttr?.valueString == "base"
                        }
                        nameValue.startsWith("com.android.vending.") -> true
                        nameValue.startsWith("com.android.stamp.") -> true
                        else -> false
                    }
                    if (!shouldRemove) return@removeElementsIf false
                    removeSplitMetaResources(merged, element, nameValue)
                    true
                }

                refresh()
            }
            merged.refreshTable()
            merged.refreshManifest()
            applyExtractNativeLibs(merged)

            outputApk.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                onProgress?.invoke("Writing merged APK")
                merged.writeApk(outputApk)
            }
        } finally {
            closeables.forEach(Closeable::close)
        }
    }

    fun listMergeOrder(apkDir: Path): List<String> {
        val closeables = mutableSetOf<Closeable>()
        try {
            val bundle = ApkBundle().apply {
                setAPKLogger(ApkEditorLogger())
                loadApkDirectory(apkDir.toFile())
            }
            val modules = bundle.apkModuleList
            if (modules.isEmpty()) {
                throw FileNotFoundException("Nothing to merge, empty modules")
            }
            closeables.addAll(modules)

            val baseModule = bundle.baseModule
                ?: findLargestTableModule(modules)
                ?: modules.first()
            return buildMergeOrder(modules, baseModule).map(::moduleDisplayName)
        } finally {
            closeables.forEach(Closeable::close)
        }
    }

    private fun generateMergedModuleName(bundle: ApkBundle): String {
        val moduleNames = bundle.listModuleNames().toSet()
        val baseName = "merged"
        var candidate = baseName
        var index = 1
        while (moduleNames.contains(candidate)) {
            candidate = "${baseName}_$index"
            index += 1
        }
        return candidate
    }

    private fun buildMergeOrder(
        modules: List<ApkModule>,
        baseModule: ApkModule
    ): List<ApkModule> {
        val order = ArrayList<ApkModule>(modules.size)
        order.add(baseModule)
        modules.forEach { module ->
            if (module !== baseModule) {
                order.add(module)
            }
        }
        return order
    }

    private fun findLargestTableModule(modules: List<ApkModule>): ApkModule? {
        var candidate: ApkModule? = null
        var largestSize = 0
        modules.forEach { module ->
            if (!module.hasTableBlock()) return@forEach
            val header = module.tableBlock.headerBlock as? TableHeader ?: return@forEach
            val size = header.chunkSize
            if (candidate == null || size > largestSize) {
                largestSize = size
                candidate = module
            }
        }
        return candidate
    }

    private fun moduleDisplayName(module: ApkModule): String {
        val name = module.moduleName
        return if (name.endsWith(".apk", ignoreCase = true)) name else "$name.apk"
    }

    private fun normalizeModuleName(name: String): String =
        name.lowercase(Locale.ROOT).removeSuffix(".apk")

    private fun removeSplitMetaResources(
        module: ApkModule,
        element: ResXmlElement,
        nameValue: String
    ) {
        if (nameValue != "com.android.vending.splits") return
        if (!module.hasTableBlock()) return
        val valueAttr = element
            .getAttributes {
                it.nameId == AndroidManifest.ID_value || it.nameId == AndroidManifest.ID_resource
            }
            .asSequence()
            .firstOrNull()
            ?: return
        if (valueAttr.valueType != ValueType.REFERENCE) return

        val table = module.tableBlock
        val resourceEntry = table.getResource(valueAttr.data) ?: return
        val zipEntryMap = module.zipEntryMap
        removeResourceEntryFiles(resourceEntry, zipEntryMap)
        table.refresh()
    }

    private fun removeResourceEntryFiles(
        resourceEntry: ResourceEntry,
        zipEntryMap: ZipEntryMap
    ) {
        for (entry in resourceEntry) {
            val resEntry = entry ?: continue
            val resValue = resEntry.resValue ?: continue
            val path = resValue.valueAsString
            if (!path.isNullOrBlank()) {
                zipEntryMap.remove(path)
                Log.i("APKEditor", "Removed table entry $path")
            }
            resEntry.setNull(true)
            val specTypePair: SpecTypePair = resEntry.typeBlock.parentSpecTypePair
            specTypePair.removeNullEntries(resEntry.id)
        }
    }

    private fun applyExtractNativeLibs(module: ApkModule) {
        val value: Boolean? = if (module.hasAndroidManifest()) {
            module.androidManifest.isExtractNativeLibs
        } else {
            null
        }
        Log.i("APKEditor", "Applying: extractNativeLibs=$value")
        module.setExtractNativeLibs(value)
    }
}
