package proofs.optional

import java.util.OptionalLong
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): laws the OptionalLong model must satisfy under JBMC — present/empty,
 * getAsLong, orElse/orElseGet fallback, orElseThrow(Supplier), ifPresentOrElse, and the zero/one
 * stream. Mirrors [OptionalLaws] for the primitive {@code long}. All must pass.
 */
class OptionalLongLaws {

    @BmcProof
    fun of_is_present_and_holds_value() {
        val x = Bmc.anyLong()
        val o = OptionalLong.of(x)
        Bmc.check(o.isPresent && !o.isEmpty && o.asLong == x)
    }

    @BmcProof
    fun empty_is_absent() {
        val o = OptionalLong.empty()
        Bmc.check(o.isEmpty && !o.isPresent)
    }

    @BmcProof
    fun orElse_returns_value_when_present_else_default() {
        val x = Bmc.anyLong()
        val d = Bmc.anyLong()
        Bmc.check(OptionalLong.of(x).orElse(d) == x)
        Bmc.check(OptionalLong.empty().orElse(d) == d)
    }

    @BmcProof
    fun orElseGet_returns_value_when_present_else_supplier() {
        val x = Bmc.anyLong()
        val d = Bmc.anyLong()
        Bmc.check(OptionalLong.of(x).orElseGet { d } == x)
        Bmc.check(OptionalLong.empty().orElseGet { d } == d)
    }

    @BmcProof
    fun orElseThrow_supplier_returns_value_when_present() {
        val x = Bmc.anyLong()
        Bmc.check(OptionalLong.of(x).orElseThrow { IllegalStateException() } == x)
    }

    @BmcProof
    fun ifPresentOrElse_runs_exactly_one_branch() {
        val x = Bmc.anyLong()
        val hits = intArrayOf(0, 0)
        OptionalLong.of(x).ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        OptionalLong.empty().ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        Bmc.check(hits[0] == 1 && hits[1] == 1)
    }

    @BmcProof
    fun stream_has_zero_or_one_element() {
        val x = Bmc.anyLong()
        Bmc.check(OptionalLong.of(x).stream().count() == 1L)
        Bmc.check(OptionalLong.empty().stream().count() == 0L)
    }
}
