package example.whenexpr

enum class Suit { HEARTS, DIAMONDS, CLUBS, SPADES }

/**
 * Kotlin `when` over an enum compiles to a `$WhenMappings` table + `tableswitch` — the same
 * shape as a Java `switch`. JBMC handles it soundly (see the proofs).
 */
object Suits {

    @JvmStatic
    fun rank(s: Suit): Int = when (s) {       // exhaustive: no `else` needed
        Suit.HEARTS -> 1
        Suit.DIAMONDS -> 2
        Suit.CLUBS -> 3
        Suit.SPADES -> 4
    }

    @JvmStatic
    fun isRed(s: Suit): Boolean = when (s) {
        Suit.HEARTS, Suit.DIAMONDS -> true
        else -> false
    }
}
