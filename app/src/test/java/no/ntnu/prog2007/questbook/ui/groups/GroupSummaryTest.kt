package no.ntnu.prog2007.questbook.ui.groups

import no.ntnu.prog2007.questbook.model.Group
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSummaryTest {

    private val group = Group(
        id = "crew", name = "Mountain Crew", description = "Hikes", inviteCode = "K7MQ2X",
        ownerId = "emma", memberIds = listOf("emma", "me", "jonas")
    )

    @Test
    fun toSummary_signedInUser_isFirstAndCalledYou() {
        // Arrange
        val usernames = mapOf("emma" to "Emma", "me" to "Jesper", "jonas" to "Jonas")

        // Act
        val summary = group.toSummary(currentUserId = "me", usernames = usernames)

        // Assert
        assertEquals(listOf("You", "Emma", "Jonas"), summary.members)
    }

    @Test
    fun toSummary_memberIds_followTheSameOrderAsNames() {
        // Arrange
        val usernames = mapOf("emma" to "Emma", "jonas" to "Jonas")

        // Act
        val summary = group.toSummary(currentUserId = "me", usernames = usernames)

        // Assert
        assertEquals(listOf("me", "emma", "jonas"), summary.memberIds)
        assertEquals("emma", summary.ownerId)
    }

    @Test
    fun toSummary_missingUsername_showsUnknown() {
        // Arrange
        val usernames = mapOf("emma" to "Emma")

        // Act
        val summary = group.toSummary(currentUserId = "me", usernames = usernames)

        // Assert
        assertEquals("Unknown", summary.members.last())
    }

    @Test
    fun memberCountLabel_oneMember_isSingular() {
        // Arrange
        val count = 1

        // Act
        val label = memberCountLabel(count)

        // Assert
        assertEquals("1 member", label)
    }

    @Test
    fun memberCountLabel_severalMembers_isPlural() {
        // Arrange
        val count = 3

        // Act
        val label = memberCountLabel(count)

        // Assert
        assertEquals("3 members", label)
    }
}
