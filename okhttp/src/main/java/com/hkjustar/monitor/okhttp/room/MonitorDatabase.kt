package com.hkjustar.monitor.okhttp.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hkjustar.monitor.okhttp.data.MonitorData

@Database(entities = [MonitorData::class], version = 4, exportSchema = false)
abstract class MonitorDatabase : RoomDatabase() {

    abstract fun monitorDao(): MonitorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `queueDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `dnsDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `connectDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `serverDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `downloadDuration` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `requestDuration` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `networkDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `clientProcessingDuration` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `monitor` ADD COLUMN `monitorDuration` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
