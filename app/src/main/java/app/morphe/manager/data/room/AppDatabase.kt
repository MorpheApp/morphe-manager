/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/data/room/AppDatabase.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.morphe.manager.data.room.apk.ApkSignature
import app.morphe.manager.data.room.apk.ApkSignatureDao
import app.morphe.manager.data.room.apps.installed.AppliedPatch
import app.morphe.manager.data.room.apps.installed.InstalledApp
import app.morphe.manager.data.room.apps.installed.InstalledAppDao
import app.morphe.manager.data.room.apps.original.OriginalApk
import app.morphe.manager.data.room.apps.original.OriginalApkDao
import app.morphe.manager.data.room.bundles.PatchBundleDao
import app.morphe.manager.data.room.bundles.PatchBundleEntity
import app.morphe.manager.data.room.options.Option
import app.morphe.manager.data.room.options.OptionDao
import app.morphe.manager.data.room.options.OptionGroup
import app.morphe.manager.data.room.selection.AppSourceMute
import app.morphe.manager.data.room.selection.PatchSelection
import app.morphe.manager.data.room.selection.SeenPatch
import app.morphe.manager.data.room.selection.SelectedPatch
import app.morphe.manager.data.room.selection.SelectionDao
import app.morphe.manager.data.room.selection.SourceMuteDao
import kotlin.random.Random

@Database(
    entities = [
        PatchBundleEntity::class,
        PatchSelection::class,
        SelectedPatch::class,
        SeenPatch::class,
        AppSourceMute::class,
        InstalledApp::class,
        AppliedPatch::class,
        OptionGroup::class,
        Option::class,
        OriginalApk::class,
        ApkSignature::class
    ],
    version = 17
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patchBundleDao(): PatchBundleDao
    abstract fun selectionDao(): SelectionDao
    abstract fun sourceMuteDao(): SourceMuteDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun optionDao(): OptionDao
    abstract fun originalApkDao(): OriginalApkDao
    abstract fun apkSignatureDao(): ApkSignatureDao

    companion object {
        fun generateUid() = Random.nextInt()
    }
}
