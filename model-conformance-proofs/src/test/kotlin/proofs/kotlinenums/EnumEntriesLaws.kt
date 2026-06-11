package proofs.kotlinenums

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

private enum class Suit { HEARTS, DIAMONDS, CLUBS, SPADES }

/**
 * Model proofs (axis 2) for the `Enum.entries` model (`kotlin.enums.EnumEntriesKt` +
 * `EnumEntriesList`, Kotlin 1.9+). An enum's `<clinit>` builds its `$ENTRIES` via
 * `EnumEntriesKt.enumEntries($VALUES)`; the real `EnumEntriesList` stubs its members to nondet, so
 * `Suit.entries.size`/`Suit.entries[i]`/iteration spuriously refute today. These laws pin the model
 * against `values()` — same size, same element at each index, ordinal/index correspondence, symbolic
 * `get`, `indexOf`/`contains`, and iteration order — proved symbolically over the bounded list model.
 *
 * Differential-via-relocation isn't used: the model just wraps `values()` in the (separately
 * conformance-tested) bounded `ArrayList` model, so its laws are the right axis here.
 */
class EnumEntriesLaws {

    /** The headline probe from the issue: size + symbolic-index ordinal correspondence. */
    @BmcProof(unwind = 8)
    fun entries_size_and_symbolic_index_ordinal() {
        val i = Bmc.anyInt(0, 3)
        Bmc.check(Suit.entries.size == 4 && Suit.entries[i].ordinal == i)
    }

    /** entries has the same size as values(). */
    @BmcProof(unwind = 8)
    fun entries_size_matches_values() {
        Bmc.check(Suit.entries.size == Suit.values().size)
    }

    /** entries[i] is the same instance as values()[i], for a symbolic index. */
    @BmcProof(unwind = 8)
    fun entries_get_matches_values() {
        val i = Bmc.anyInt(0, 3)
        Bmc.check(Suit.entries[i] === Suit.values()[i])
    }

    /** entries[ordinal] round-trips: the entry at an enum value's ordinal is that value. */
    @BmcProof(unwind = 8)
    fun entries_indexed_by_ordinal_is_identity() {
        val s = Bmc.anyOf(Suit.values())
        Bmc.check(Suit.entries[s.ordinal] === s)
    }

    /** indexOf/contains agree with ordinal for every constant. */
    @BmcProof(unwind = 8)
    fun entries_indexOf_is_ordinal() {
        val s = Bmc.anyOf(Suit.values())
        Bmc.check(Suit.entries.indexOf(s) == s.ordinal && Suit.entries.contains(s))
    }

    /** Iteration visits the constants in declaration (ordinal) order. */
    @BmcProof(unwind = 8)
    fun entries_iterates_in_ordinal_order() {
        var i = 0
        for (s in Suit.entries) {
            Bmc.check(s.ordinal == i)
            i++
        }
        Bmc.check(i == 4)
    }
}
