package app.revanced.manager.util

import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.name

fun isAllowedApkFile(path: Path): Boolean {
    val extension = path.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension in APK_FILE_EXTENSIONS
}

fun isAllowedRvpFile(path: Path): Boolean {
    val extension = path.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension == "rvp"
}
