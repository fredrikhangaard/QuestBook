package no.ntnu.prog2007.questbook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import no.ntnu.prog2007.questbook.model.Quest
import no.ntnu.prog2007.questbook.model.QuestStatus
import no.ntnu.prog2007.questbook.model.QuestType

/**
 * Locally stored quest.
 *
 * Enum names are stored as text. User IDs and timestamps retain
 * the same meaning as in the domain model.
 */
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val type: String,
    val title: String,
    val description: String,
    val assignedTo: String,
    val status: String,
    val requiredDistanceKm: Double?,
    val createdByUserId: String?,
    val assignedUserId: String?,
    val createdAtMillis: Long,
    val startedAtMillis: Long?
) {
    /** Converts a database row to the model used by ViewModels. */
    fun toQuest(): Quest = Quest(
        id = id,
        groupId = groupId,
        type = QuestType.valueOf(type),
        title = title,
        description = description,
        assignedTo = assignedTo,
        status = QuestStatus.valueOf(status),
        requiredDistanceKm = requiredDistanceKm,
        createdByUserId = createdByUserId,
        assignedUserId = assignedUserId,
        createdAtMillis = createdAtMillis,
        startedAtMillis = startedAtMillis
    )

    companion object {
        /** Converts a domain quest to a database row. */
        fun fromQuest(quest: Quest): QuestEntity = QuestEntity(
            id = quest.id,
            groupId = quest.groupId,
            type = quest.type.name,
            title = quest.title,
            description = quest.description,
            assignedTo = quest.assignedTo,
            status = quest.status.name,
            requiredDistanceKm = quest.requiredDistanceKm,
            createdByUserId = quest.createdByUserId,
            assignedUserId = quest.assignedUserId,
            createdAtMillis = quest.createdAtMillis,
            startedAtMillis = quest.startedAtMillis
        )
    }
}