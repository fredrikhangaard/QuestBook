package no.ntnu.prog2007.questbook.data.photo

import kotlin.math.max

/**
 * Size rules for quest photos stored as Base64 text in Firestore.
 *
 * A Firestore document can be at most 1 MiB, and Base64 makes data about 33 % larger,
 * so the JPEG must be compressed to well below that limit.
 */
object PhotoSizing {

    /** Longest side of a stored photo, in pixels. */
    const val MAX_SIDE_PX = 1080

    /**
     * Largest allowed length of the Base64 text. Leaves room below the 1 MiB
     * document limit for the other fields in the photo document.
     */
    const val MAX_ENCODED_LENGTH = 900_000

    /**
     * Number of characters Base64 produces for [byteCount] bytes (including padding).
     *
     * @throws IllegalArgumentException if [byteCount] is negative.
     */
    fun encodedLength(byteCount: Int): Int {
        require(byteCount >= 0) { "byteCount must not be negative" }
        return 4 * ((byteCount + 2) / 3)
    }

    /** Returns `true` if [byteCount] bytes still fit in a photo document after Base64 encoding. */
    fun fitsInFirestore(byteCount: Int): Boolean = encodedLength(byteCount) <= MAX_ENCODED_LENGTH

    /**
     * Returns the factor an image of [width] x [height] pixels must be scaled by so that
     * its longest side is at most [maxSide]. Never scales up, so the result is at most 1.
     *
     * @throws IllegalArgumentException if [width] or [height] is not positive.
     */
    fun scaleFactor(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Float {
        require(width > 0 && height > 0) { "Image size must be positive" }
        val longest = max(width, height)
        return if (longest <= maxSide) 1f else maxSide.toFloat() / longest
    }
}
