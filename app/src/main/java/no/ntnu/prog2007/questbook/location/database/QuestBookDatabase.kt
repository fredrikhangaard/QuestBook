package no.ntnu.prog2007.questbook.location.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import no.ntnu.prog2007.questbook.data.local.QuestDao
import no.ntnu.prog2007.questbook.data.local.QuestEntity

/**
 * Local QuestBook database.
 *
 * Version 1 stored GPS route points.
 * Version 2 also stores quests, preserving existing route points.
 */
@Database(
    entities = [
        RoutePointEntity::class,
        QuestEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class QuestBookDatabase : RoomDatabase() {

    abstract fun routePointDao(): RoutePointDao

    abstract fun questDao(): QuestDao

    companion object {

        @Volatile
        private var INSTANCE: QuestBookDatabase? = null

        /** Adds quest storage without deleting existing GPS data. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quests` (
                        `id` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `assignedTo` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `requiredDistanceKm` REAL,
                        `createdByUserId` TEXT,
                        `assignedUserId` TEXT,
                        `createdAtMillis` INTEGER NOT NULL,
                        `startedAtMillis` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Returns the database instance shared by the application. */
        fun getDatabase(context: Context): QuestBookDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuestBookDatabase::class.java,
                    "questbook_gps_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}