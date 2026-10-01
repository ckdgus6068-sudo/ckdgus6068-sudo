package io.github.ckdgus6068.jellycalendar.core

import java.security.SecureRandom
import kotlinx.serialization.Serializable

/**
 * The hidden golden jelly (황금 젤리): grab the same jelly in the box and let it go again and
 * again, without a break, and on the [GOLDEN_GRABS]th time it turns to gold. The first find on a
 * phone writes down a code that the finder can show the maker for a small present.
 */
const val GOLDEN_GRABS = 50

/** From this many grabs in a row the jelly starts to glitter, a hint that something is coming. */
const val GOLDEN_HINT = 25

/** A pause longer than this between two grabs starts the count again. */
const val GOLDEN_PATIENCE_MS = 3_000L

/** The flavour index of a golden jelly; the ten everyday flavours are 0 to 9. */
const val GOLDEN_FLAVOR = 10

/** The first golden jelly found on this phone. */
@Serializable
data class GoldenFind(
    /** Shown to the maker, e.g. "GOLD-7K3Q-M9TX". */
    val code: String,
    /** Epoch milliseconds. */
    val foundAt: Long,
)

/** Counts how many times in a row one jelly has been grabbed and let go. */
class GrabStreak(
    private val patienceMs: Long = GOLDEN_PATIENCE_MS,
) {
    var jellyId: String? = null
        private set
    var count: Int = 0
        private set
    private var lastAt = 0L

    /** One more grab of [id] at [atMillis] (any monotonic clock); returns the count so far. */
    fun grabbed(id: String, atMillis: Long): Int {
        count = if (id == jellyId && atMillis - lastAt in 0..patienceMs) count + 1 else 1
        jellyId = id
        lastAt = atMillis
        return count
    }

    /** How much [id] glitters right now: 0 below [GOLDEN_HINT], rising to 1 at [GOLDEN_GRABS]. */
    fun glitter(id: String): Float =
        if (id != jellyId || count < GOLDEN_HINT) 0f
        else ((count - GOLDEN_HINT + 1).toFloat() / (GOLDEN_GRABS - GOLDEN_HINT)).coerceAtMost(1f)

    fun reset() {
        jellyId = null
        count = 0
    }
}

/** Letters and digits that cannot be mistaken for one another (no 0/O, 1/I/L, U/V). */
private const val CODE_LETTERS = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
private val random = SecureRandom()

/** A fresh golden code such as "GOLD-7K3Q-M9TX". */
fun newGoldenCode(): String {
    val chars = CharArray(8) { CODE_LETTERS[random.nextInt(CODE_LETTERS.length)] }
    return "GOLD-${String(chars, 0, 4)}-${String(chars, 4, 4)}"
}
