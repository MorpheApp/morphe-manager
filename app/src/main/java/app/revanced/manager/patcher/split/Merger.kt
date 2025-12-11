package app.revanced.manager.patcher.split

import android.util.Log
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import java.io.Closeable
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ArscLogger : APKLogger {
    private const val TAG = "ARSCLib"

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
    suspend fun merge(apkDir: Path): ApkModule {
        val closeables = mutableSetOf<Closeable>()
        try {
            val merged = withContext(Dispatchers.Default) {
                with(ApkBundle()) {
                    setAPKLogger(ArscLogger)
                    loadApkDirectory(apkDir.toFile())
                    closeables.addAll(modules)
                    mergeModules().also(closeables::add)
                }
            }

            merged.androidManifest.apply {
                arrayOf(
                    AndroidManifest.ID_isSplitRequired,
                    AndroidManifest.ID_extractNativeLibs
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

                val stampPattern = Regex("^com\\.android\\.(stamp|vending)\\.")
                applicationElement.removeElementsIf { element ->
                    if (element.name != AndroidManifest.TAG_meta_data) return@removeElementsIf false
                    val nameAttr = element
                        .getAttributes { it.nameId == AndroidManifest.ID_name }
                        .asSequence()
                        .single()
                    stampPattern.containsMatchIn(nameAttr.valueString)
                }

                refresh()
            }

            return merged
        } finally {
            closeables.forEach(Closeable::close)
        }
    }
}
