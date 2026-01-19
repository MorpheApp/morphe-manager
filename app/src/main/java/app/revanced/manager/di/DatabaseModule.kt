package app.revanced.manager.di

import android.content.Context
import androidx.room.Room
import app.revanced.manager.data.room.AppDatabase
import app.revanced.manager.data.room.MIGRATION_1_2
import app.revanced.manager.data.room.MIGRATION_2_3
import app.revanced.manager.data.room.MIGRATION_3_4
import app.revanced.manager.data.room.MIGRATION_4_5
import app.revanced.manager.data.room.MIGRATION_5_6
import app.revanced.manager.data.room.MIGRATION_6_7
import app.revanced.manager.data.room.MIGRATION_7_8
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    fun provideAppDatabase(context: Context) =
        Room.databaseBuilder(context, AppDatabase::class.java, "manager")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8
            )
            .build()

    single {
        provideAppDatabase(androidContext())
    }
}
