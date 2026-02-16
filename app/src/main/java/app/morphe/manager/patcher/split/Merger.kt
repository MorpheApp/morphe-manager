package app.morphe.manager.patcher.split

import android.util.Log
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ZipEntryMap
import com.reandroid.archive.block.ApkSignatureBlock
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
import kotlin.collections.map

private object ApkEditorLogger : APKLogger {
    private const val TAG = "APKEditor"

    override fun logMessage(msg: String) {
        Log.i(TAG, msg)
    }

    override fun logError(msg: String, tr: Throwable?) {
        Log.e(TAG, msg, tr)
    }

    override fun logVerbose(msg: String) {
        Log.v(TAG, msg)
    }
}

internal object Merger {
    suspend fun merge(
        apkDir: Path,
        outputApk: File,
        skipModules: Set<String> = emptySet(),
//        onProgress: ((String) -> Unit)? = null
    ) {
        val closeables = mutableSetOf<Closeable>()
        try {
            val merged = withContext(Dispatchers.Default) {
                try {
                    val bundle = ApkBundle().apply {
                        setAPKLogger(ApkEditorLogger)
                        loadApkDirectory(apkDir.toFile())
                    }
                    val modules = bundle.apkModuleList
                    if (modules.isEmpty()) {
                        throw FileNotFoundException("Nothing to merge, empty modules")
                    }

                    closeables.addAll(modules)

                    val mergedModule = ApkModule(generateMergedModuleName(bundle), ZipEntryMap()).apply {
                        setAPKLogger(ApkEditorLogger)
                        setLoadDefaultFramework(false)
                    }
                    closeables.add(mergedModule)

                    val mergeResources = false
                    val baseModule = bundle.baseModule
                        ?: findLargestTableModule(modules)
                        ?: modules.first()
                    val mergeOrder = buildMergeOrder(modules, baseModule)
                    val skipped = skipModules
                        .map { it.lowercase() }
                        .toSet()

                    var signatureBlock: ApkSignatureBlock? = null
                    mergeOrder.forEach { module ->
                        val displayName = moduleDisplayName(module)
                        val shouldSkip =
                            module !== baseModule && skipped.contains(displayName.lowercase())
                        if (shouldSkip) return@forEach
                        val moduleSignature = module.apkSignatureBlock
                        if (module === baseModule && moduleSignature != null) {
                            signatureBlock = moduleSignature
                        }

                        if (signatureBlock == null) {
                            signatureBlock = moduleSignature
                        }

//                        onProgress?.invoke("Merging $displayName")
                        mergedModule.merge(module, mergeResources)
                    }

                    mergedModule.setApkSignatureBlock(signatureBlock)
                    if (mergedModule.hasTableBlock()) {
                        val table = mergedModule.tableBlock
                        table.sortPackages()
                        table.refresh()
                    }
                    mergedModule.zipEntryMap.autoSortApkFiles()
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
                    AndroidManifest.ID_extractNativeLibs,
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

                val splitMetaName = "com.android.vending.splits"
                val stampPattern = Regex("^com\\.android\\.(stamp|vending)\\.")
                applicationElement.removeElementsIf { element ->
                    if (element.name != AndroidManifest.TAG_meta_data) return@removeElementsIf false
                    val nameAttr = element
                        .getAttributes { it.nameId == AndroidManifest.ID_name }
                        .asSequence()
                        .singleOrNull()
                        ?: return@removeElementsIf false
                    val nameValue = nameAttr.valueString
                    when {
                        nameValue == splitMetaName -> {
                            removeSplitMetaResources(merged, element)
                            true
                        }
                        stampPattern.containsMatchIn(nameValue) -> true
                        else -> false
                    }
                }

                refresh()
            }

            outputApk.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
//                onProgress?.invoke("Writing merged APK")
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
                setAPKLogger(ApkEditorLogger)
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

    private fun removeSplitMetaResources(module: ApkModule, element: ResXmlElement) {
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
}
