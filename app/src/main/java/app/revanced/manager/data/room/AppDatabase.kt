package app.revanced.manager.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.revanced.manager.data.room.apps.downloaded.DownloadedAppDao
import app.revanced.manager.data.room.apps.downloaded.DownloadedApp
import app.revanced.manager.data.room.apps.installed.AppliedPatch
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.data.room.apps.installed.InstalledAppDao
import app.revanced.manager.data.room.apps.original.OriginalApk
import app.revanced.manager.data.room.apps.original.OriginalApkDao
import app.revanced.manager.data.room.selection.PatchSelection
import app.revanced.manager.data.room.selection.SelectedPatch
import app.revanced.manager.data.room.selection.SelectionDao
import app.revanced.manager.data.room.bundles.PatchBundleDao
import app.revanced.manager.data.room.bundles.PatchBundleEntity
import app.revanced.manager.data.room.options.Option
import app.revanced.manager.data.room.options.OptionDao
import app.revanced.manager.data.room.options.OptionGroup
import app.revanced.manager.data.room.plugins.TrustedDownloaderPlugin
import app.revanced.manager.data.room.plugins.TrustedDownloaderPluginDao
import app.revanced.manager.data.room.profile.PatchProfileDao
import app.revanced.manager.data.room.profile.PatchProfileEntity
import kotlin.random.Random

@Database(
    entities = [
        PatchBundleEntity::class,
        PatchSelection::class,
        SelectedPatch::class,
        DownloadedApp::class,
        InstalledApp::class,
        AppliedPatch::class,
        OptionGroup::class,
        Option::class,
        TrustedDownloaderPlugin::class,
        PatchProfileEntity::class,
        OriginalApk::class
    ],
    version = 8
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patchBundleDao(): PatchBundleDao
    abstract fun selectionDao(): SelectionDao
    abstract fun downloadedAppDao(): DownloadedAppDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun optionDao(): OptionDao
    abstract fun trustedDownloaderPluginDao(): TrustedDownloaderPluginDao
    abstract fun patchProfileDao(): PatchProfileDao
    abstract fun originalApkDao(): OriginalApkDao

    companion object {
        fun generateUid() = Random.Default.nextInt()
    }
}
