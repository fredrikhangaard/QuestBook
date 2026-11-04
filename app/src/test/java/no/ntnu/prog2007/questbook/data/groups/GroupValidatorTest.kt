package no.ntnu.prog2007.questbook.data.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupValidatorTest {

    @Test
    fun validate_nameWithoutDescription_isAccepted() {
        // Arrange
        val name = "Mountain Crew"

        // Act
        val problem = GroupValidator.validate(name, "")

        // Assert
        assertNull(problem)
    }

    @Test
    fun validate_blankName_returnsBlankName() {
        // Arrange
        val name = "   "

        // Act
        val problem = GroupValidator.validate(name, "")

        // Assert
        assertEquals(GroupValidationError.BLANK_NAME, problem)
    }

    @Test
    fun validate_nameAtMaxLength_isAccepted() {
        // Arrange
        val name = "a".repeat(GroupValidator.MAX_NAME_LENGTH)

        // Act
        val problem = GroupValidator.validate(name, "")

        // Assert
        assertNull(problem)
    }

    @Test
    fun validate_nameOneOverMax_returnsTooLong() {
        // Arrange
        val name = "a".repeat(GroupValidator.MAX_NAME_LENGTH + 1)

        // Act
        val problem = GroupValidator.validate(name, "")

        // Assert
        assertEquals(GroupValidationError.NAME_TOO_LONG, problem)
    }

    @Test
    fun validate_descriptionOneOverMax_returnsTooLong() {
        // Arrange
        val description = "a".repeat(GroupValidator.MAX_DESCRIPTION_LENGTH + 1)

        // Act
        val problem = GroupValidator.validate("Mountain Crew", description)

        // Assert
        assertEquals(GroupValidationError.DESCRIPTION_TOO_LONG, problem)
    }
}
