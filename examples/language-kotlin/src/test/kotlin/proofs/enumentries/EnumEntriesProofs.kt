package proofs.enumentries

import example.enumentries.Planet
import example.enumentries.Planets
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * `Enum.entries` (Kotlin 1.9+) in proofs. Without the `kotlin.enums.EnumEntriesKt`/`EnumEntriesList`
 * model these spuriously refuted ("no body for callee java.util.List.size()"); with it, `entries`
 * analyses over bmc4j's bounded list model — same axis as `values()` + `Bmc.anyOf`.
 */
class EnumEntriesProofs {

    /** `entries.size` and a symbolic `entries[i].ordinal` are sound. */
    @BmcProof(unwind = 8)
    fun entries_size_and_index() {
        val i = Bmc.anyInt(0, 3)
        Bmc.check(Planet.entries.size == 4 && Planet.entries[i].ordinal == i)
    }

    /** Iterating `entries` and summing a property is sound (bounded by the enum's size). */
    @BmcProof(unwind = 8)
    fun total_gravity_is_bounded() {
        Bmc.check(Planets.totalGravity() == 27)
    }

    /** `entries[ordinal]` round-trips for every constant. */
    @BmcProof(unwind = 8)
    fun at_ordinal_round_trips() {
        val p = Bmc.anyOf(Planet.values())
        Bmc.check(Planets.at(p.ordinal) === p)
    }
}
