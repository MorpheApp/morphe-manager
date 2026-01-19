package app.revanced.manager.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.System

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patch_bundles ADD COLUMN display_name TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS patch_profiles (
                uid INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                app_version TEXT,
                name TEXT NOT NULL,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(uid)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patch_bundles ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")

        db.query("SELECT uid FROM patch_bundles ORDER BY CASE WHEN uid = 0 THEN 0 ELSE rowid END").use { cursor ->
            var index = 0
            while (cursor.moveToNext()) {
                val uid = cursor.getInt(0)
                db.execSQL("UPDATE patch_bundles SET sort_order = ? WHERE uid = ?", arrayOf(index, uid))
                index += 1
            }
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE installed_app ADD COLUMN selection_payload TEXT")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patch_bundles ADD COLUMN created_at INTEGER")
        db.execSQL("ALTER TABLE patch_bundles ADD COLUMN updated_at INTEGER")

        val now = System.currentTimeMillis()
        db.execSQL("UPDATE patch_bundles SET created_at = ? WHERE created_at IS NULL", arrayOf(now))
        db.execSQL("UPDATE patch_bundles SET updated_at = ? WHERE updated_at IS NULL", arrayOf(now))
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE patch_bundles ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
    }
}

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
