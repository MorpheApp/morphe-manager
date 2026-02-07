package app.revanced.manager.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.revanced.manager.data.room.apps.installed.AppliedPatch
import app.revanced.manager.data.room.apps.installed.InstalledApp
import app.revanced.manager.data.room.apps.installed.InstalledAppDao
import app.revanced.manager.data.room.apps.original.OriginalApk
import app.revanced.manager.data.room.apps.original.OriginalApkDao
import app.revanced.manager.data.room.bundles.PatchBundleDao
import app.revanced.manager.data.room.bundles.PatchBundleEntity
import app.revanced.manager.data.room.options.Option
import app.revanced.manager.data.room.options.OptionDao
import app.revanced.manager.data.room.options.OptionGroup
import app.revanced.manager.data.room.selection.PatchSelection
import app.revanced.manager.data.room.selection.SelectedPatch
import app.revanced.manager.data.room.selection.SelectionDao
import kotlin.random.Random

@Database(
    entities = [
        PatchBundleEntity::class,
        PatchSelection::class,
        SelectedPatch::class,
        InstalledApp::class,
        AppliedPatch::class,
        OptionGroup::class,
        Option::class,
        OriginalApk::class
    ],
    version = 10
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patchBundleDao(): PatchBundleDao
    abstract fun selectionDao(): SelectionDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun optionDao(): OptionDao
    abstract fun originalApkDao(): OriginalApkDao

    companion object {
        fun generateUid() = Random.nextInt()
    }
}
