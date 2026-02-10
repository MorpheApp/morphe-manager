package app.morphe.manager.patcher

import android.content.Context
import java.io.File

abstract class LibraryResolver {
    protected fun findLibrary(context: Context, searchTerm: String): File? =
        File(context.applicationInfo.nativeLibraryDir).run {
            list { _, f -> !File(f).isDirectory && f.contains(searchTerm) }?.firstOrNull()
                ?.let { resolve(it) }
        }

    protected fun findLibraryExact(context: Context, fileName: String): File? =
        File(context.applicationInfo.nativeLibraryDir).run {
            list { _, f -> !File(f).isDirectory && f == fileName }?.firstOrNull()
                ?.let { resolve(it) }
        }
}
