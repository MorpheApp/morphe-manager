/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

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

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS apk_signatures (
                file_path TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                last_modified INTEGER NOT NULL,
                hashes TEXT NOT NULL,
                PRIMARY KEY(file_path)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing records fill their name in the next time they are written
        db.execSQL("ALTER TABLE installed_app ADD COLUMN app_label TEXT")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Records predating the flag describe the install their app has, renamed by patches or
        // not. Runs that produce a copy from here on say so themselves
        db.execSQL("ALTER TABLE installed_app ADD COLUMN is_clone INTEGER NOT NULL DEFAULT 0")

        // The patches a record was built with are the only trace left of why it was renamed, and
        // the patch that copies an app is named here because no record kept anything else of it.
        // Both stores are read: applied_patch drops patches whose source is gone, while the
        // payload keeps every name but only exists on records written since it was introduced
        db.execSQL(
            """
            UPDATE installed_app SET is_clone = 1
            WHERE current_package_name != original_package_name
                AND (
                    selection_payload LIKE '%"Clone app"%'
                    OR EXISTS (
                        SELECT 1 FROM applied_patch
                        WHERE applied_patch.package_name = installed_app.current_package_name
                            AND applied_patch.patch_name = 'Clone app'
                    )
                )
            """.trimIndent()
        )
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Only the sources an app is kept from are stored, so an empty table is the state every
        // app starts in: every source it has patches in is offered to it
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_source_mutes (
                patch_bundle INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                PRIMARY KEY(patch_bundle, package_name),
                FOREIGN KEY(patch_bundle) REFERENCES patch_bundles(uid) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Empty entries were written whenever the package manager reported no signers, archives
        // that verify included, so they are dropped and read again under the verifying check
        db.execSQL("DELETE FROM apk_signatures WHERE hashes = ''")
    }
}
