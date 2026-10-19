/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/split/Merger.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.split

import app.morphe.manager.patcher.logger.Logger
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ZipEntryMap
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.container.SpecTypePair
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.CoderMalfunctionError
import java.nio.file.Path
import java.util.Locale

// What APKEditor says while merging is detail kept at trace level, out of the log the user reads
private class ApkEditorLogger(
    private val logger: Logger,
    private val onEvent: ((SplitPreparationEvent) -> Unit)? = null
) : APKLogger {
    private companion object {
        val MERGE_PATTERN = Regex("Merging\\s*:?\\s*(.+)", RegexOption.IGNORE_CASE)
    }

    override fun logMessage(msg: String) {
        logger.trace(msg)
        emitMergeProgress(msg)
    }

    override fun logError(msg: String, tr: Throwable?) {
        logger.trace(tr?.let { "$msg\n${it.stackTraceToString()}" } ?: msg)
    }

    override fun logVerbose(msg: String) {
        logger.trace(msg)
        emitMergeProgress(msg)
    }

    private fun emitMergeProgress(message: String) {
        val match = MERGE_PATTERN.find(message) ?: return
        val moduleName = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val normalized = normalizeMergeModuleName(moduleName)
        if (normalized.isBlank()) return
        onEvent?.invoke(SplitPreparationEvent.Merging(normalized))
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
        logger: Logger,
        onEvent: ((SplitPreparationEvent) -> Unit)? = null
    ) {
        val closeables = mutableSetOf<Closeable>()
        try {
            val apkEditorLogger = ApkEditorLogger(logger, onEvent)
            val merged = runInterruptible(Dispatchers.Default) {
                try {
                    val bundle = ApkBundle().apply {
                        setAPKLogger(apkEditorLogger)
                        loadApkDirectory(apkDir.toFile())
                    }
                    val modules = bundle.apkModuleList
                    if (modules.isEmpty()) {
                        throw FileNotFoundException("Nothing to merge, empty modules")
                    }

                    closeables.add(bundle)

                    val mergedModule = bundle.mergeModules(false).apply {
                        @Suppress("UsePropertyAccessSyntax")
                        setAPKLogger(apkEditorLogger)
                        setLoadDefaultFramework(false)
                    }
                    closeables.add(mergedModule)
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
                    removeSplitMetaResources(merged, element, nameValue, logger)
                    true
                }

                refresh()
            }
            merged.refreshTable()
            merged.refreshManifest()
            applyExtractNativeLibs(merged, logger)

            outputApk.parentFile?.mkdirs()
            runInterruptible(Dispatchers.IO) {
                onEvent?.invoke(SplitPreparationEvent.Writing)
                merged.writeApk(outputApk)
            }
        } finally {
            closeables.forEach(Closeable::close)
        }
    }

    private fun removeSplitMetaResources(
        module: ApkModule,
        element: ResXmlElement,
        nameValue: String,
        logger: Logger
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
        removeResourceEntryFiles(resourceEntry, zipEntryMap, logger)
        table.refresh()
    }

    private fun removeResourceEntryFiles(
        resourceEntry: ResourceEntry,
        zipEntryMap: ZipEntryMap,
        logger: Logger
    ) {
        for (entry in resourceEntry) {
            val resEntry = entry ?: continue
            val resValue = resEntry.resValue ?: continue
            val path = resValue.valueAsString
            if (!path.isNullOrBlank()) {
                zipEntryMap.remove(path)
                logger.trace("Removed table entry $path")
            }
            resEntry.isNull = true
            val specTypePair: SpecTypePair = resEntry.typeBlock.parentSpecTypePair
            specTypePair.removeNullEntries(resEntry.id)
        }
    }

    private fun applyExtractNativeLibs(module: ApkModule, logger: Logger) {
        val value: Boolean? = if (module.hasAndroidManifest()) {
            module.androidManifest.isExtractNativeLibs
        } else {
            null
        }
        logger.trace("Applying: extractNativeLibs=$value")
        module.setExtractNativeLibs(value)
    }
}
