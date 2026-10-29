package no.ntnu.prog2007.questbook.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun isValidEmail_normalAddress_returnsTrue() {
        // Arrange
        val email = "emma@example.com"

        // Act
        val valid = AuthValidator.isValidEmail(email)

        // Assert
        assertTrue(valid)
    }

    @Test
    fun isValidEmail_surroundingSpaces_areIgnored() {
        // Arrange
        val email = "  emma@example.com "

        // Act
        val valid = AuthValidator.isValidEmail(email)

        // Assert
        assertTrue(valid)
    }

    @Test
    fun isValidEmail_malformedAddresses_returnFalse() {
        // Arrange
        val malformed = listOf("", "emma", "emma@", "@example.com", "emma@example", "em ma@example.com")

        // Act
        val results = malformed.map { AuthValidator.isValidEmail(it) }

        // Assert
        results.forEachIndexed { index, valid -> assertFalse("'${malformed[index]}' was accepted", valid) }
    }

    @Test
    fun validateSignIn_validInput_returnsNull() {
        // Arrange
        val email = "emma@example.com"
        val password = "secret123"

        // Act
        val problem = AuthValidator.validateSignIn(email, password)

        // Assert
        assertNull(problem)
    }

    @Test
    fun validateSignIn_emptyPassword_returnsBlankPassword() {
        // Arrange
        val email = "emma@example.com"
        val password = ""

        // Act
        val problem = AuthValidator.validateSignIn(email, password)

        // Assert
        assertEquals(AuthValidationError.BLANK_PASSWORD, problem)
    }

    @Test
    fun validateCreateUser_validInput_returnsNull() {
        // Arrange
        val username = "Emma"
        val email = "emma@example.com"
        val password = "secret"

        // Act
        val problem = AuthValidator.validateCreateUser(username, email, password)

        // Assert
        assertNull(problem)
    }

    @Test
    fun validateCreateUser_blankUsername_returnsBlankUsername() {
        // Arrange
        val username = "   "

        // Act
        val problem = AuthValidator.validateCreateUser(username, "emma@example.com", "secret")

        // Assert
        assertEquals(AuthValidationError.BLANK_USERNAME, problem)
    }

    @Test
    fun validateCreateUser_usernameAtMaxLength_isAccepted() {
        // Arrange
        val username = "a".repeat(AuthValidator.MAX_USERNAME_LENGTH)

        // Act
        val problem = AuthValidator.validateCreateUser(username, "emma@example.com", "secret")

        // Assert
        assertNull(problem)
    }

    @Test
    fun validateCreateUser_usernameOneOverMax_returnsTooLong() {
        // Arrange
        val username = "a".repeat(AuthValidator.MAX_USERNAME_LENGTH + 1)

        // Act
        val problem = AuthValidator.validateCreateUser(username, "emma@example.com", "secret")

        // Assert
        assertEquals(AuthValidationError.USERNAME_TOO_LONG, problem)
    }

    @Test
    fun validateCreateUser_passwordOneUnderMinimum_returnsTooShort() {
        // Arrange
        val password = "a".repeat(AuthValidator.MIN_PASSWORD_LENGTH - 1)

        // Act
        val problem = AuthValidator.validateCreateUser("Emma", "emma@example.com", password)

        // Assert
        assertEquals(AuthValidationError.PASSWORD_TOO_SHORT, problem)
    }
}
