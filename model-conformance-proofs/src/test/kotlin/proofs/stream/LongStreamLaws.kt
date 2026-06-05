package proofs.stream

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.stream.LongStream

/**
 * Model proofs (axis 2) for the bounded, eager LongStream model. Like the int/object
 * stream models it is CProver-backed (not JVM-runnable in isolation), so these @BmcProof laws over
 * concrete small inputs are the conformance check. Shapes mirror StreamLaws and stay
 * devirtualization-friendly: concrete sources, simple lambdas, tight operands.
 */
class LongStreamLaws {

    private fun listOf3(a: Int, b: Int, c: Int): ArrayList<Int> {
        val xs = ArrayList<Int>()
        xs.add(a); xs.add(b); xs.add(c)
        return xs
    }

    @BmcProof
    fun longstream_of_sum() {
        Bmc.check(LongStream.of(10L, 20L, 5L).sum() == 35L)
    }

    @BmcProof
    fun longstream_of_count() {
        Bmc.check(LongStream.of(1L, 2L, 3L).count() == 3L)
    }

    @BmcProof
    fun longstream_range_count() {
        Bmc.check(LongStream.range(0L, 4L).count() == 4L)
    }

    @BmcProof
    fun longstream_range_sum() {
        Bmc.check(LongStream.range(1L, 5L).sum() == 10L)
    }

    @BmcProof
    fun longstream_rangeClosed_sum() {
        Bmc.check(LongStream.rangeClosed(1L, 4L).sum() == 10L)
    }

    @BmcProof
    fun longstream_map_sum() {
        Bmc.check(LongStream.of(1L, 2L, 3L).map { it * 2L }.sum() == 12L)
    }

    @BmcProof
    fun longstream_filter_count() {
        Bmc.check(LongStream.of(1L, 2L, 4L).filter { it % 2L == 0L }.count() == 2L)
    }

    @BmcProof
    fun longstream_filter_sum() {
        Bmc.check(LongStream.of(1L, 2L, 4L).filter { it % 2L == 0L }.sum() == 6L)
    }

    @BmcProof
    fun stream_mapToLong_sum() {
        Bmc.check(listOf3(10, 20, 5).stream().mapToLong { it.toLong() }.sum() == 35L)
    }
}
