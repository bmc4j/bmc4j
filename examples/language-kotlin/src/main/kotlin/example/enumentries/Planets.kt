package example.enumentries

/**
 * Kotlin 1.9+ `Enum.entries` — the recommended replacement for `values()`. An enum's `entries`
 * property returns a `kotlin.enums.EnumEntries` (a `List`), built once in the enum's `<clinit>` from
 * the `values()` array. bmc4j models it as the bounded list over `values()`, so `entries` is usable
 * in proofs (see the proofs).
 */
enum class Planet(val gravity: Int) {
    MERCURY(4),
    VENUS(9),
    EARTH(10),
    MARS(4);
}

object Planets {

    /** Sum of all planets' gravity, iterating `entries`. */
    @JvmStatic
    fun totalGravity(): Int {
        var sum = 0
        for (p in Planet.entries) {
            sum += p.gravity
        }
        return sum
    }

    /** The entry at an ordinal, via `entries[i]`. */
    @JvmStatic
    fun at(i: Int): Planet = Planet.entries[i]
}
