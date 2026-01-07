package app.revanced.manager.util

const val tag = "Morphe Manager"

//const val JAR_MIMETYPE = "application/java-archive"
const val APK_MIMETYPE = "application/vnd.android.package-archive"

val APK_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
    APK_MIMETYPE,
    // ApkMirror split files of "app-whatever123_apkmirror.com.apk" regularly Android to misclassify
    // the file as an application or something incorrect. Renaming the file and
    // removing "apkmirror.com" from the file name fixes the issue, but that's not something the
    // end user will know or should have to do. Instead, show all files to ensure the user can
    // always select no matter what file naming ApkMirror uses.
    "application/*",
//    "application/zip",
//    "application/x-zip-compressed",
//    "application/x-apkm",
//    "application/x-apks",
//    "application/x-xapk",
//    "application/xapk",
//    "application/vnd.android.xapk",
//    "application/vnd.android.apkm",
//    "application/apkm",
//    "application/vnd.android.apks",
//    "application/apks",
)
val APK_FILE_EXTENSIONS = setOf(
    "apk",
    "apkm",
    "apks",
    "xapk",
    "zip"
)
const val JSON_MIMETYPE = "application/json"
const val BIN_MIMETYPE = "application/octet-stream"

val MPP_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
//    "application/x-zip-compressed"
    "*/*"
)
