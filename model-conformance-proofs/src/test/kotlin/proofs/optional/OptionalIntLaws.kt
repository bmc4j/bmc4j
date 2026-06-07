package proofs.optional

import java.util.OptionalInt
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): laws the OptionalInt model must satisfy under JBMC — present/empty,
 * getAsInt, orElse/orElseGet fallback, orElseThrow(Supplier), ifPresentOrElse, and the zero/one
 * stream. Mirrors [OptionalLaws] for the primitive {@code int}. All must pass.
 */
class OptionalIntLaws {

    @BmcProof
    fun of_is_present_and_holds_value() {
        val x = Bmc.anyInt()
        val o = OptionalInt.of(x)
        Bmc.check(o.isPresent && !o.isEmpty && o.asInt == x)
    }

    @BmcProof
    fun empty_is_absent() {
        val o = OptionalInt.empty()
        Bmc.check(o.isEmpty && !o.isPresent)
    }

    @BmcProof
    fun orElse_returns_value_when_present_else_default() {
        val x = Bmc.anyInt()
        val d = Bmc.anyInt()
        Bmc.check(OptionalInt.of(x).orElse(d) == x)
        Bmc.check(OptionalInt.empty().orElse(d) == d)
    }

    @BmcProof
    fun orElseGet_returns_value_when_present_else_supplier() {
        val x = Bmc.anyInt()
        val d = Bmc.anyInt()
        Bmc.check(OptionalInt.of(x).orElseGet { d } == x)
        Bmc.check(OptionalInt.empty().orElseGet { d } == d)
    }

    @BmcProof
    fun orElseThrow_supplier_returns_value_when_present() {
        val x = Bmc.anyInt()
        Bmc.check(OptionalInt.of(x).orElseThrow { IllegalStateException() } == x)
    }

    @BmcProof
    fun ifPresentOrElse_runs_exactly_one_branch() {
        val x = Bmc.anyInt()
        val hits = intArrayOf(0, 0)
        OptionalInt.of(x).ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        OptionalInt.empty().ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        Bmc.check(hits[0] == 1 && hits[1] == 1)
    }

    @BmcProof
    fun stream_has_zero_or_one_element() {
        val x = Bmc.anyInt()
        Bmc.check(OptionalInt.of(x).stream().count() == 1L)
        Bmc.check(OptionalInt.empty().stream().count() == 0L)
    }
}
