package app.morphe.manager.di

import android.content.Context
import androidx.room.Room
import app.morphe.manager.data.room.*
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
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10
            )
            .build()

    single {
        provideAppDatabase(androidContext())
    }
}
