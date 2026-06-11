package proofs.enumset

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.EnumSet

private enum class Planet { MERCURY, VENUS, EARTH, MARS }

/**
 * Model proofs (axis 2): algebraic laws the [java.util.EnumSet] model's EXPLICIT-element surface must
 * satisfy under JBMC's own semantics, over symbolic enum elements (so they hold for every element at
 * once). The Class-universe factories (allOf/noneOf/range/complementOf) are loud `@BmcUnmodelable` and
 * are not exercised here. All proofs must pass.
 */
class EnumSetLaws {

    @BmcProof(unwind = 8)
    fun of_single_contains_only_that_element() {
        val e = Bmc.anyOf(Planet.values())
        val s = EnumSet.of(e)
        Bmc.check(s.contains(e) && s.size == 1)
    }

    @BmcProof(unwind = 8)
    fun of_two_dedups_equal_elements() {
        val e = Bmc.anyOf(Planet.values())
        val s = EnumSet.of(e, e)   // duplicate
        Bmc.check(s.size == 1 && s.contains(e))
    }

    @BmcProof(unwind = 8)
    fun of_two_distinct_holds_both() {
        val a = Bmc.anyOf(Planet.values())
        val b = Bmc.anyOf(Planet.values())
        Bmc.assume(a != b)
        val s = EnumSet.of(a, b)
        Bmc.check(s.size == 2 && s.contains(a) && s.contains(b))
    }

    @BmcProof(unwind = 8)
    fun add_then_contains_then_remove() {
        val s = EnumSet.of(Planet.EARTH)
        val e = Bmc.anyOf(Planet.values())
        s.add(e)
        Bmc.check(s.contains(e))
        s.remove(e)
        Bmc.check(!s.contains(e))
    }

    @BmcProof(unwind = 8)
    fun add_is_idempotent_for_equal_elements() {
        val s = EnumSet.of(Planet.MARS)
        val addedAgain = s.add(Planet.MARS)   // duplicate: rejected
        Bmc.check(!addedAgain && s.size == 1)
    }

    @BmcProof(unwind = 8)
    fun of_five_with_duplicates_dedups() {
        val s = EnumSet.of(Planet.MERCURY, Planet.VENUS, Planet.EARTH, Planet.MARS, Planet.MERCURY)
        Bmc.check(s.size == 4)
    }

    @BmcProof(unwind = 8)
    fun copyOf_collection_holds_distinct_elements() {
        val src = ArrayList<Planet>()
        val e = Bmc.anyOf(Planet.values())
        src.add(e)
        src.add(e)   // duplicate in source
        val s = EnumSet.copyOf(src)
        Bmc.check(s.size == 1 && s.contains(e))
    }

    @BmcProof(unwind = 8)
    fun varargs_of_first_plus_rest() {
        val a = Bmc.anyOf(Planet.values())
        val b = Bmc.anyOf(Planet.values())
        Bmc.assume(a != b)
        val s = EnumSet.of(a, b)   // resolves to of(E, E...) only for >2; here of(e1,e2) — still a model law
        Bmc.check(s.contains(a) && s.contains(b))
    }
}
