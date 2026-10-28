package no.ntnu.prog2007.questbook.data.photo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoCodecTest {

    @Test
    fun encodeThenDecode_returnsTheOriginalBytes() {
        // Arrange: the first bytes of a JPEG file
        val original = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70)

        // Act
        val decoded = PhotoCodec.decode(PhotoCodec.encode(original))

        // Assert
        assertArrayEquals(original, decoded)
    }

    @Test
    fun encode_producesStandardBase64Text() {
        // Arrange
        val bytes = "QuestBook".toByteArray()

        // Act
        val text = PhotoCodec.encode(bytes)

        // Assert
        assertEquals("UXVlc3RCb29r", text)
    }

    @Test
    fun decode_invalidText_returnsNull() {
        // Arrange
        val notBase64 = "this is not base64!"

        // Act
        val result = PhotoCodec.decode(notBase64)

        // Assert
        assertNull(result)
    }

    @Test
    fun decode_blankText_returnsNull() {
        // Arrange
        val blank = "   "

        // Act
        val result = PhotoCodec.decode(blank)

        // Assert
        assertNull(result)
    }
}
