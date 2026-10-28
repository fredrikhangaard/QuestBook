package no.ntnu.prog2007.questbook.data.photo

import java.util.Base64

/**
 * Converts photo bytes to and from Base64 text, so a photo can be stored
 * in a text field in Firestore and turned back into an image later.
 *
 * Uses [java.util.Base64] (available from API 26) instead of android.util.Base64,
 * so the logic can be unit tested on the JVM without an emulator.
 */
object PhotoCodec {

    /** Encodes [bytes] as Base64 text. */
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /**
     * Decodes Base64 [text] back to bytes.
     *
     * @return the decoded bytes, or `null` if [text] is blank or not valid Base64.
     */
    fun decode(text: String): ByteArray? {
        if (text.isBlank()) return null
        return try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
