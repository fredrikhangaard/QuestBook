package no.ntnu.prog2007.questbook.data.groups

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Invite codes that friends type to join a group, for example `K7MQ2X`.
 *
 * Codes are random, so they cannot be guessed from the group's name, and only use
 * characters that are hard to mix up when read aloud (no 0/O, 1/I/L).
 */
object InviteCode {

    /** Number of characters in a code. */
    const val LENGTH = 6

    /** The characters a code is made of. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val secureRandom: Random = SecureRandom().asKotlinRandom()

    /** Creates a new random code. [random] can be replaced in tests to get a fixed result. */
    fun generate(random: Random = secureRandom): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Turns what the user typed into the stored form: upper case, no spaces or dashes. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    /** Returns `true` if [code] has the right length and only allowed characters. */
    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
