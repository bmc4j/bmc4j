package proofs.optional

import java.util.OptionalDouble
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): laws the OptionalDouble model must satisfy under JBMC — present/empty,
 * getAsDouble, orElse/orElseGet fallback, orElseThrow(Supplier), ifPresentOrElse, and the zero/one
 * stream. Mirrors [OptionalLongLaws] for the primitive {@code double}. A double value + present flag and
 * primitive {@code ==}/{@code <} on it are bit-precise sound under JBMC (no Double.compare / total order
 * anywhere), so all these verify. Operands are exactly-representable small doubles.
 */
class OptionalDoubleLaws {

    @BmcProof
    fun of_is_present_and_holds_value() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        val o = OptionalDouble.of(x)
        Bmc.check(o.isPresent && !o.isEmpty && o.asDouble == x)
    }

    @BmcProof
    fun empty_is_absent() {
        val o = OptionalDouble.empty()
        Bmc.check(o.isEmpty && !o.isPresent)
    }

    @BmcProof
    fun orElse_returns_value_when_present_else_default() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        val d = Bmc.anyDouble(-100.0, 100.0)
        Bmc.check(OptionalDouble.of(x).orElse(d) == x)
        Bmc.check(OptionalDouble.empty().orElse(d) == d)
    }

    @BmcProof
    fun orElseGet_returns_value_when_present_else_supplier() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        val d = Bmc.anyDouble(-100.0, 100.0)
        Bmc.check(OptionalDouble.of(x).orElseGet { d } == x)
        Bmc.check(OptionalDouble.empty().orElseGet { d } == d)
    }

    @BmcProof
    fun orElseThrow_supplier_returns_value_when_present() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        Bmc.check(OptionalDouble.of(x).orElseThrow { IllegalStateException() } == x)
    }

    @BmcProof
    fun ifPresentOrElse_runs_exactly_one_branch() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        val hits = intArrayOf(0, 0)
        OptionalDouble.of(x).ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        OptionalDouble.empty().ifPresentOrElse({ hits[0]++ }, { hits[1]++ })
        Bmc.check(hits[0] == 1 && hits[1] == 1)
    }

    @BmcProof
    fun stream_has_zero_or_one_element() {
        val x = Bmc.anyDouble(-100.0, 100.0)
        Bmc.check(OptionalDouble.of(x).stream().count() == 1L)
        Bmc.check(OptionalDouble.empty().stream().count() == 0L)
    }
}
