package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * Differential conformance for the *SummaryStatistics accumulator models. The real
 * {@code java.util.*SummaryStatistics} and the relocated {@code bmcref.java.util.*SummaryStatistics} are
 * fed the same value sequence; the observable getters are compared.
 *
 * <p>Int/Long are fully checked (integer min/max, double-division average — all sound). For
 * DoubleSummaryStatistics, getMin/getMax are loud @BmcUnmodelable (Double.compare FP total order), so
 * they are NOT exercised differentially; count/sum/average ARE, over exactly-representable
 * (integer-valued) doubles where the model's plain running sum equals the JDK's compensated (Kahan) sum.
 */
class SummaryStatisticsConformanceTest : FunSpec({

    test("IntSummaryStatistics: count/sum/min/max/average conform over an accepted sequence") {
        checkAll(Arb.list(Arb.int(-100_000..100_000), 1..20)) { xs ->
            val r = java.util.IntSummaryStatistics()
            val m = bmcref.java.util.IntSummaryStatistics()
            for (x in xs) { r.accept(x); m.accept(x) }
            r.count shouldBe m.count
            r.sum shouldBe m.sum
            r.min shouldBe m.min
            r.max shouldBe m.max
            r.average shouldBe m.average
        }
    }

    test("IntSummaryStatistics: empty getters match the JDK sentinels (MAX_VALUE/MIN_VALUE/0)") {
        val r = java.util.IntSummaryStatistics()
        val m = bmcref.java.util.IntSummaryStatistics()
        r.count shouldBe m.count
        r.sum shouldBe m.sum
        r.min shouldBe m.min
        r.max shouldBe m.max
        r.average shouldBe m.average
    }

    test("IntSummaryStatistics: combine merges two accumulators identically") {
        checkAll(Arb.list(Arb.int(-1000..1000), 1..8), Arb.list(Arb.int(-1000..1000), 1..8)) { a, b ->
            val r1 = java.util.IntSummaryStatistics(); val r2 = java.util.IntSummaryStatistics()
            val m1 = bmcref.java.util.IntSummaryStatistics(); val m2 = bmcref.java.util.IntSummaryStatistics()
            for (x in a) { r1.accept(x); m1.accept(x) }
            for (x in b) { r2.accept(x); m2.accept(x) }
            r1.combine(r2); m1.combine(m2)
            r1.count shouldBe m1.count
            r1.sum shouldBe m1.sum
            r1.min shouldBe m1.min
            r1.max shouldBe m1.max
        }
    }

    test("LongSummaryStatistics: count/sum/min/max/average conform; accept(int) widens like the JDK") {
        checkAll(Arb.list(Arb.long(-100_000L..100_000L), 1..20)) { xs ->
            val r = java.util.LongSummaryStatistics()
            val m = bmcref.java.util.LongSummaryStatistics()
            for (x in xs) { r.accept(x); m.accept(x) }
            r.count shouldBe m.count
            r.sum shouldBe m.sum
            r.min shouldBe m.min
            r.max shouldBe m.max
            r.average shouldBe m.average
        }
    }

    test("LongSummaryStatistics: accept(int) overload widens and conforms") {
        checkAll(Arb.list(Arb.int(-1000..1000), 1..10)) { xs ->
            val r = java.util.LongSummaryStatistics()
            val m = bmcref.java.util.LongSummaryStatistics()
            for (x in xs) { r.accept(x); m.accept(x) }
            r.count shouldBe m.count
            r.sum shouldBe m.sum
            r.min shouldBe m.min
            r.max shouldBe m.max
        }
    }

    test("DoubleSummaryStatistics: count/sum/average conform (getMin/getMax are loud @BmcUnmodelable)") {
        // Integer-valued doubles: the model's plain running sum equals the JDK's Kahan sum exactly, and
        // count/average are well-defined. getMin/getMax are NOT called — they wall off the FP total order.
        checkAll(Arb.list(Arb.int(-100_000..100_000), 1..20)) { xs ->
            val r = java.util.DoubleSummaryStatistics()
            val m = bmcref.java.util.DoubleSummaryStatistics()
            for (x in xs) { r.accept(x.toDouble()); m.accept(x.toDouble()) }
            r.count shouldBe m.count
            r.sum shouldBe m.sum
            r.average shouldBe m.average
        }
    }

    test("DoubleSummaryStatistics: empty count/sum/average conform") {
        val r = java.util.DoubleSummaryStatistics()
        val m = bmcref.java.util.DoubleSummaryStatistics()
        r.count shouldBe m.count
        r.sum shouldBe m.sum
        r.average shouldBe m.average
    }
})
