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
                MIGRATION_11_12
            )
            .build()

    single {
        provideAppDatabase(androidContext())
    }
}
