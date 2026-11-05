package no.ntnu.prog2007.questbook.data.photo

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A durable local photo linked to its completion result, never to a display name. */
@Entity(tableName = "memory_photos")
data class MemoryPhotoEntity(
    @PrimaryKey val id: String,
    val resultId: String,
    val questId: String,
    val groupId: String,
    val userId: String,
    val caption: String,
    val jpegBytes: ByteArray,
    val createdAtMillis: Long
)

/** Photos stay local until a separate cloud synchronization implementation is available. */
@Dao
interface MemoryPhotoDao {
    @Insert suspend fun insert(photo: MemoryPhotoEntity)

    @Query("SELECT * FROM memory_photos WHERE userId = :userId ORDER BY createdAtMillis DESC")
    fun observePhotos(userId: String): Flow<List<MemoryPhotoEntity>>
}
