package proofs.optional

import java.util.Optional
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): laws the Optional model must satisfy under JBMC — present/empty,
 * orElse fallback, and map/filter over a symbolic value. All must pass.
 */
class OptionalLaws {

    @BmcProof
    fun of_is_present_and_holds_value() {
        val x = Bmc.anyInt()
        val o = Optional.of(x)
        Bmc.check(o.isPresent && !o.isEmpty && o.get() == x)
    }

    @BmcProof
    fun empty_is_absent() {
        val o = Optional.empty<Int>()
        Bmc.check(o.isEmpty && !o.isPresent)
    }

    @BmcProof
    fun orElse_returns_value_when_present() {
        val x = Bmc.anyInt()
        val d = Bmc.anyInt()
        Bmc.check(Optional.of(x).orElse(d) == x)
    }

    @BmcProof
    fun orElse_returns_default_when_empty() {
        val d = Bmc.anyInt()
        Bmc.check(Optional.empty<Int>().orElse(d) == d)
    }

    @BmcProof
    fun map_transforms_present_value() {
        val x = Bmc.anyInt(-1000, 1000)
        val o = Optional.of(x).map { it + 1 }
        Bmc.check(o.isPresent && o.get() == x + 1)
    }

    @BmcProof
    fun filter_keeps_matching_else_empties() {
        val x = Bmc.anyInt()
        Bmc.assume(x > 0)
        Bmc.check(Optional.of(x).filter { it > 0 }.isPresent)
        Bmc.check(Optional.of(x).filter { it < 0 }.isEmpty)
    }
}
