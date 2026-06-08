package proofs.stream

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.stream.DoubleStream

/**
 * Model proofs (axis 2) for the bounded, eager DoubleStream model and the DoubleSummaryStatistics
 * accumulator. Like the int/object/long stream models it is CProver-backed (not JVM-runnable in
 * isolation), so these @BmcProof laws over concrete small inputs are the conformance check.
 *
 * <p>The "no-double convention" is dead: double ADDITION (sum/reduce), the DIVISION in average, and
 * primitive double comparison (filter predicates) are all bit-precise sound under JBMC, so all these
 * laws verify. NOT covered here on purpose: min/max/sorted (DoubleStream) and getMin/getMax
 * (DoubleSummaryStatistics) — those are loud @BmcUnmodelable (Double.compare total order via
 * doubleToLongBits), so reaching them is an honest UNKNOWN, not a law to assert.
 *
 * <p>Operands use exactly-representable small doubles, and ranges/divisions are kept tight (the division
 * in average is range-sensitive — the range-reduction lever).
 */
class DoubleStreamLaws {

    private fun listOf3(a: Int, b: Int, c: Int): ArrayList<Int> {
        val xs = ArrayList<Int>()
        xs.add(a); xs.add(b); xs.add(c)
        return xs
    }

    @BmcProof
    fun doublestream_of_sum() {
        Bmc.check(DoubleStream.of(10.0, 20.0, 5.0).sum() == 35.0)
    }

    @BmcProof
    fun doublestream_of_count() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 3.0).count() == 3L)
    }

    @BmcProof
    fun doublestream_map_sum() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 3.0).map { it * 2.0 }.sum() == 12.0)
    }

    @BmcProof
    fun doublestream_filter_count() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 4.0).filter { it >= 2.0 }.count() == 2L)
    }

    @BmcProof
    fun doublestream_filter_sum() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 4.0).filter { it >= 2.0 }.sum() == 6.0)
    }

    @BmcProof
    fun doublestream_reduce_identity_sum() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 3.0).reduce(0.0) { a, b -> a + b } == 6.0)
    }

    @BmcProof
    fun doublestream_reduce_no_identity_present() {
        val o = DoubleStream.of(1.0, 2.0, 3.0, 4.0).reduce { a, b -> a + b }
        Bmc.check(o.isPresent && o.asDouble == 10.0)
    }

    @BmcProof
    fun doublestream_reduce_no_identity_empty_is_absent() {
        Bmc.check(DoubleStream.empty().reduce { a, b -> a + b }.isEmpty)
    }

    @BmcProof
    fun doublestream_average_present() {
        // 2+4+6 = 12, /3 = 4.0 — exact in binary FP. Tight range keeps the division cheap.
        val o = DoubleStream.of(2.0, 4.0, 6.0).average()
        Bmc.check(o.isPresent && o.asDouble == 4.0)
    }

    @BmcProof
    fun doublestream_average_empty_is_absent() {
        Bmc.check(DoubleStream.empty().average().isEmpty)
    }

    @BmcProof
    fun doublestream_findFirst_head() {
        val o = DoubleStream.of(7.0, 8.0, 9.0).findFirst()
        Bmc.check(o.isPresent && o.asDouble == 7.0)
    }

    @BmcProof
    fun doublestream_findFirst_empty_absent() {
        Bmc.check(DoubleStream.empty().findFirst().isEmpty)
    }

    @BmcProof
    fun stream_mapToObj_then_count() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 5.0).mapToObj { it }.count() == 3L)
    }

    @BmcProof
    fun doublestream_boxed_count() {
        Bmc.check(DoubleStream.of(1.0, 2.0).boxed().count() == 2L)
    }

    @BmcProof
    fun doublestream_takeWhile_count() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 9.0, 3.0).takeWhile { it < 5.0 }.count() == 2L)
    }

    @BmcProof
    fun doublestream_dropWhile_sum() {
        Bmc.check(DoubleStream.of(1.0, 2.0, 9.0, 3.0).dropWhile { it < 5.0 }.sum() == 12.0)
    }

    /**
     * Symbolic: the stream sum equals the direct fold for all (small, integer-valued) doubles. This proves
     * the model's sum() does the correct fold/identity — an algebraic property independent of FP rounding.
     * Inputs are integer-valued on purpose: full-width symbolic FP addition (symbolic exponents force the
     * adder's variable-shift exponent alignment) is SAT-pathological and times out, whereas integer-valued
     * doubles keep the bit-vector FP adder tractable while still proving the identity for all such values.
     */
    @BmcProof
    fun symbolic_sum() {
        val a = Bmc.anyInt(-100, 100).toDouble()
        val b = Bmc.anyInt(-100, 100).toDouble()
        val c = Bmc.anyInt(-100, 100).toDouble()
        Bmc.check(DoubleStream.of(a, b, c).sum() == a + b + c)
    }

    // ---- DoubleSummaryStatistics: the SOUND surface (accept/getCount/getSum/getAverage) ------------
    // getMin/getMax are loud @BmcUnmodelable (FP total order) — deliberately NOT asserted here.

    @BmcProof
    fun summarystatistics_count_and_sum() {
        val stats = DoubleStream.of(1.0, 2.0, 3.0).summaryStatistics()
        Bmc.check(stats.count == 3L && stats.sum == 6.0)
    }

    @BmcProof
    fun summarystatistics_average() {
        // 2+4+6 = 12, /3 = 4.0 exactly.
        val stats = DoubleStream.of(2.0, 4.0, 6.0).summaryStatistics()
        Bmc.check(stats.average == 4.0)
    }

    @BmcProof
    fun summarystatistics_empty_count_zero() {
        val stats = DoubleStream.empty().summaryStatistics()
        Bmc.check(stats.count == 0L && stats.sum == 0.0)
    }
}
