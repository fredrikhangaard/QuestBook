
package no.ntnu.prog2007.questbook.location.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RoutePointEntity::class],
    version = 1,
    exportSchema = true
)
abstract class QuestBookDatabase : RoomDatabase() {

    abstract fun routePointDao(): RoutePointDao

    companion object {

        @Volatile
        private var INSTANCE: QuestBookDatabase? = null

        fun getDatabase(context: Context): QuestBookDatabase {
            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuestBookDatabase::class.java,
                    "questbook_gps_database"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
