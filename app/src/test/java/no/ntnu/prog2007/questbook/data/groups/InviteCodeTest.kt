package no.ntnu.prog2007.questbook.data.groups

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {

    @Test
    fun generate_createsSixAllowedCharacters() {
        // Arrange
        val random = Random(42)

        // Act
        val code = InviteCode.generate(random)

        // Assert
        assertEquals(InviteCode.LENGTH, code.length)
        assertTrue(code.all { it in InviteCode.ALPHABET })
        assertTrue(InviteCode.isValid(code))
    }

    @Test
    fun generate_sameSeed_givesSameCode() {
        // Arrange
        val first = Random(7)
        val second = Random(7)

        // Act
        val a = InviteCode.generate(first)
        val b = InviteCode.generate(second)

        // Assert
        assertEquals(a, b)
    }

    @Test
    fun generate_manyCodes_areAlmostAlwaysDifferent() {
        // Arrange
        val count = 1_000

        // Act
        val codes = List(count) { InviteCode.generate() }.toSet()

        // Assert: about 887 million possible codes, so collisions are very rare
        assertTrue(codes.size > count - 10)
    }

    @Test
    fun normalize_lowercaseWithSpacesAndDash_becomesStoredForm() {
        // Arrange
        val typed = " k7m q2-x "

        // Act
        val normalized = InviteCode.normalize(typed)

        // Assert
        assertEquals("K7MQ2X", normalized)
    }

    @Test
    fun isValid_tooShort_returnsFalse() {
        // Arrange
        val code = "K7MQ2"

        // Act
        val valid = InviteCode.isValid(code)

        // Assert
        assertFalse(valid)
    }

    @Test
    fun isValid_confusableCharacters_returnsFalse() {
        // Arrange: 1, 0 and O are never used in codes
        val code = "AB10OX"

        // Act
        val valid = InviteCode.isValid(code)

        // Assert
        assertFalse(valid)
    }
}
