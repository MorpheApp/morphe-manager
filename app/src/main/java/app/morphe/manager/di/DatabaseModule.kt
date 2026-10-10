/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/di/DatabaseModule.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.di

import android.content.Context
import androidx.room.Room
import app.morphe.manager.data.room.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    fun provideAppDatabase(context: Context) =
        Room.databaseBuilder(context, AppDatabase::class.java, "manager")
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6)
            .addMigrations(
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17
            )
            .build()

    single {
        provideAppDatabase(androidContext())
    }
}
