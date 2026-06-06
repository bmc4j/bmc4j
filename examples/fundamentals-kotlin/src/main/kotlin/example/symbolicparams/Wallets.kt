package example.symbolicparams

/**
 * A plain Kotlin class taken as a SYMBOLIC PROOF PARAMETER — the most natural way to write a
 * proof ("for every Wallet…"). kotlinc guards every non-null-typed parameter with an
 * `Intrinsics.checkNotNullParameter` prologue; bmc4j rewrites that prologue (in `@BmcProof`
 * methods only) into `assume(p != null)`, so the proof ranges over the inputs the Kotlin type
 * system admits instead of refuting on `p = null` — an input no Kotlin caller can construct.
 * The wallet's FIELDS stay fully symbolic: every `cents` value is explored.
 */
class Wallet(val cents: Int) {

    fun isOverdrawn(): Boolean = cents < 0

    /** BUG: negation overflows at Int.MIN_VALUE — the "absolute value" comes back negative. */
    fun absCents(): Int = if (cents < 0) -cents else cents

    /** Fixed: saturate the one unrepresentable magnitude. */
    fun safeAbsCents(): Int = if (cents == Int.MIN_VALUE) Int.MAX_VALUE else absCents()
}
