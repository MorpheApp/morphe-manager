package app.morphe.manager.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS original_apks (
                package_name TEXT NOT NULL,
                version TEXT NOT NULL,
                file_path TEXT NOT NULL,
                last_used INTEGER NOT NULL,
                file_size INTEGER NOT NULL,
                PRIMARY KEY(package_name)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop tables related to downloader plugins, patch profiles and downloaded apps
        db.execSQL("DROP TABLE IF EXISTS trusted_downloader_plugins")
        db.execSQL("DROP TABLE IF EXISTS patch_profiles")
        db.execSQL("DROP TABLE IF EXISTS downloaded_app")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add bundle_version column to applied_patch table
        db.execSQL("ALTER TABLE applied_patch ADD COLUMN bundle_version TEXT")

        // Add patched_at column to installed_app table
        db.execSQL("ALTER TABLE installed_app ADD COLUMN patched_at INTEGER")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Clean up duplicate/legacy data where package_name is a patched name
        // Keep only records where package_name matches original_package_name in installed_app

        // For patch_selections table
        db.execSQL("""
            DELETE FROM patch_selections 
            WHERE package_name IN (
                SELECT ia.current_package_name
                FROM installed_app ia 
                WHERE ia.current_package_name != ia.original_package_name
            )
        """)

        // For option_groups table
        db.execSQL("""
            DELETE FROM option_groups 
            WHERE package_name IN (
                SELECT ia.current_package_name
                FROM installed_app ia 
                WHERE ia.current_package_name != ia.original_package_name
            )
        """)
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS seen_patches (
                patch_bundle INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                patch_name TEXT NOT NULL,
                PRIMARY KEY(patch_bundle, package_name, patch_name),
                FOREIGN KEY(patch_bundle) REFERENCES patch_bundles(uid) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}
