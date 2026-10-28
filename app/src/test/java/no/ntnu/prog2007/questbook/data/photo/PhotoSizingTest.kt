package no.ntnu.prog2007.questbook.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSizingTest {

    @Test
    fun encodedLength_roundsUpToWholeBase64Blocks() {
        // Arrange
        val byteCounts = listOf(0, 1, 3, 4)

        // Act
        val lengths = byteCounts.map { PhotoSizing.encodedLength(it) }

        // Assert
        assertEquals(listOf(0, 4, 4, 8), lengths)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encodedLength_negativeByteCount_throws() {
        // Arrange
        val negative = -1

        // Act + Assert (expects IllegalArgumentException)
        PhotoSizing.encodedLength(negative)
    }

    @Test
    fun fitsInFirestore_typicalCompressedPhoto_returnsTrue() {
        // Arrange
        val photoSize = 300_000

        // Act
        val fits = PhotoSizing.fitsInFirestore(photoSize)

        // Assert
        assertTrue(fits)
    }

    @Test
    fun fitsInFirestore_largestAllowedSize_returnsTrue() {
        // Arrange: 675 000 bytes become exactly 900 000 Base64 characters
        val largestAllowed = 675_000

        // Act
        val fits = PhotoSizing.fitsInFirestore(largestAllowed)

        // Assert
        assertTrue(fits)
    }

    @Test
    fun fitsInFirestore_oneByteOverLimit_returnsFalse() {
        // Arrange
        val tooLarge = 675_001

        // Act
        val fits = PhotoSizing.fitsInFirestore(tooLarge)

        // Assert
        assertFalse(fits)
    }

    @Test
    fun scaleFactor_largeLandscapePhoto_scalesLongestSideTo1080() {
        // Arrange
        val width = 4000
        val height = 3000

        // Act
        val scale = PhotoSizing.scaleFactor(width, height)

        // Assert
        assertEquals(0.27f, scale, 0.0001f)
    }

    @Test
    fun scaleFactor_largePortraitPhoto_usesTheHeight() {
        // Arrange
        val width = 3000
        val height = 4000

        // Act
        val scale = PhotoSizing.scaleFactor(width, height)

        // Assert
        assertEquals(0.27f, scale, 0.0001f)
    }

    @Test
    fun scaleFactor_smallPhoto_isNeverScaledUp() {
        // Arrange
        val width = 800
        val height = 600

        // Act
        val scale = PhotoSizing.scaleFactor(width, height)

        // Assert
        assertEquals(1f, scale, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun scaleFactor_zeroWidth_throws() {
        // Arrange
        val width = 0

        // Act + Assert (expects IllegalArgumentException)
        PhotoSizing.scaleFactor(width, 100)
    }
}
