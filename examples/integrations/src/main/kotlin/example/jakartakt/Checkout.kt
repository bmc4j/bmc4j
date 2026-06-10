package example.jakartakt

/** Business logic over a validated Kotlin [Req]. */
object Checkout {

    /**
     * Provably safe for every valid [Req]: `qty >= 1` and `cents >= 0` (from the Jakarta bounds), so
     * the line total is non-negative and never divides by a zero quantity.
     */
    fun unitPriceFloor(r: Req): Int {
        // qty >= 1, so this never divides by zero — provable only because @Min(1) is in the domain.
        return r.cents / r.qty
    }

    /**
     * BUG: dereferences [Req.ageOrNull], which is a boxed `Int?` WITHOUT `@NotNull` — so `null` is a
     * perfectly VALID value (only `@NotNull` would reject it). Unboxing null throws. The refutation
     * only exists because the generated assume keeps valid-null objects in the proof domain.
     */
    fun ageBucket(r: Req): Int = r.ageOrNull!! / 30
}
