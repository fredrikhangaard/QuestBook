
package no.ntnu.prog2007.questbook.location.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_points")
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float,
    // Stored on the accepted resume anchor so its boundary follows the same ordered write/retry.
    // Legacy rows default to false: their historical pause boundaries were never recorded.
    @ColumnInfo(defaultValue = "0") val startsSegment: Boolean = false
)
