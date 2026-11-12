package com.refluxcare.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MealLog::class,
        SleepRecord::class,
        WorkoutLog::class,
        SymptomLog::class,
        HabitCheck::class,
        RuleBreak::class,
        CustomFood::class,
        MeditationSession::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meditations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`plannedMin` INTEGER NOT NULL, " +
                        "`actualSec` INTEGER NOT NULL, " +
                        "`sound` TEXT NOT NULL, " +
                        "`completed` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "refluxcare.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
