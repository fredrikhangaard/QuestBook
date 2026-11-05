package no.ntnu.prog2007.questbook.location.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import no.ntnu.prog2007.questbook.data.tracking.*
import no.ntnu.prog2007.questbook.data.local.QuestDao
import no.ntnu.prog2007.questbook.data.local.QuestEntity

/**
 * Local QuestBook database.
 *
 * Version 1 stored GPS route points.
 * Version 2 also stores quests, preserving existing route points.
 * Version 3 adds resumable tracking sessions and saved quest results.
 * Version 4 adds local photos linked to completion results.
 * Version 5 records uninterrupted route sections on accepted GPS points.
 * Version 6 adds solo daily/weekly assignments and manual activity entries.
 */
@Database(
    entities = [
        RoutePointEntity::class,
        QuestEntity::class,
        TrackingSessionEntity::class,
        QuestResultEntity::class,
        no.ntnu.prog2007.questbook.data.photo.MemoryPhotoEntity::class,
        no.ntnu.prog2007.questbook.data.personal.PersonalQuestEntity::class,
        no.ntnu.prog2007.questbook.data.personal.ActivityEntryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class QuestBookDatabase : RoomDatabase() {

    abstract fun routePointDao(): RoutePointDao

    abstract fun questDao(): QuestDao

    abstract fun trackingDao(): TrackingDao

    abstract fun memoryPhotoDao(): no.ntnu.prog2007.questbook.data.photo.MemoryPhotoDao

    abstract fun personalQuestDao(): no.ntnu.prog2007.questbook.data.personal.PersonalQuestDao

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

        /** Adds session checkpoints and results without changing route or quest rows. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tracking_sessions` (
                        `questId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `userId` TEXT NOT NULL,
                        `targetMeters` REAL NOT NULL, `startedAtMillis` INTEGER NOT NULL,
                        `distanceMeters` REAL NOT NULL, `elapsedMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`questId`))
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quest_results` (
                        `id` TEXT NOT NULL, `questId` TEXT NOT NULL, `groupId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL, `questTitle` TEXT NOT NULL, `participantName` TEXT NOT NULL,
                        `completedAtMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL,
                        `distanceMeters` REAL, `gpsSessionId` TEXT, PRIMARY KEY(`id`))
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quest_results_questId` ON `quest_results` (`questId`)")
            }
        }

        /** Adds local result-linked photos without changing existing quests, results, or routes. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `memory_photos` (
                    `id` TEXT NOT NULL, `resultId` TEXT NOT NULL, `questId` TEXT NOT NULL,
                    `groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, `caption` TEXT NOT NULL,
                    `jpegBytes` BLOB NOT NULL, `createdAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`))""".trimIndent())
            }
        }

        /** Adds explicit resume boundaries without deleting routes, results, sessions, or photos. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `route_points` ADD COLUMN `startsSegment` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds personal assignments and self-reported activities, preserving all existing data. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `personal_quests` (
                    `questId` TEXT NOT NULL, `userId` TEXT NOT NULL, `templateId` TEXT NOT NULL,
                    `cadence` TEXT NOT NULL, `periodKey` TEXT NOT NULL, `slot` TEXT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT NOT NULL, `metric` TEXT NOT NULL, `target` REAL NOT NULL,
                    `periodStartMillis` INTEGER NOT NULL, `periodEndMillis` INTEGER NOT NULL, `zoneId` TEXT NOT NULL,
                    `completedAtMillis` INTEGER, PRIMARY KEY(`questId`))""".trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_quests_userId_cadence_periodKey_slot` ON `personal_quests` (`userId`, `cadence`, `periodKey`, `slot`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `activity_entries` (
                    `id` TEXT NOT NULL, `userId` TEXT NOT NULL, `questId` TEXT NOT NULL,
                    `occurredAtMillis` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `activeMillis` INTEGER NOT NULL,
                    `pushups` INTEGER NOT NULL, `squats` INTEGER NOT NULL, `visitKind` TEXT, PRIMARY KEY(`id`))""".trimIndent())
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}