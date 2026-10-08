/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/LibraryResolver.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher

import android.content.Context
import java.io.File

abstract class LibraryResolver {
    protected fun findPropOverrideLibrary(context: Context): File? =
        File(context.applicationInfo.nativeLibraryDir).run {
            list { _, f -> !File(f).isDirectory && f.contains("prop_override") }?.firstOrNull()
                ?.let { resolve(it) }
        }
}
