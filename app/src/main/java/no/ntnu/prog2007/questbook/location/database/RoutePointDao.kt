
package no.ntnu.prog2007.questbook.location.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePointDao {

    @Insert
    suspend fun insertPoint(point: RoutePointEntity)

    @Query(
        "SELECT * FROM route_points " +
                "WHERE sessionId = :sessionId " +
                "ORDER BY timestamp ASC, id ASC"
    )
    fun getPointsForSession(
        sessionId: String
    ): Flow<List<RoutePointEntity>>

    @Query(
        "SELECT DISTINCT sessionId FROM route_points"
    )
    fun getSessionIds(): Flow<List<String>>

    @Query(
        "DELETE FROM route_points WHERE sessionId = :sessionId"
    )
    suspend fun deleteSession(sessionId: String)
}
