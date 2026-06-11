package proofs.stream

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the bounded, eager Stream models. Differential-via-relocation
 * doesn't apply (the streams are CProver-backed, not JVM-runnable in isolation), so these @BmcProof
 * laws over concrete small lists are the conformance check. Pipelines mirror the shapes JBMC can
 * devirtualize (concrete source list, simple lambdas).
 */
class StreamLaws {

    private fun listOf3(a: Int, b: Int, c: Int): ArrayList<Int> {
        val xs = ArrayList<Int>()
        xs.add(a); xs.add(b); xs.add(c)
        return xs
    }

    @BmcProof(unwind = 4)
    fun stream_mapToInt_sum() {
        Bmc.check(listOf3(10, 20, 5).stream().mapToInt { it }.sum() == 35)
    }

    @BmcProof
    fun stream_count() {
        Bmc.check(listOf3(1, 2, 3).stream().count() == 3L)
    }

    @BmcProof(unwind = 4)
    fun stream_mapToInt_map_sum() {
        Bmc.check(listOf3(1, 2, 3).stream().mapToInt { it * 2 }.sum() == 12)
    }

    @BmcProof(unwind = 4)
    fun stream_filter_count() {
        Bmc.check(listOf3(1, 2, 4).stream().filter { it % 2 == 0 }.count() == 2L)
    }

    @BmcProof(unwind = 4)
    fun stream_collect_toList_preserves_elements() {
        val out = listOf3(7, 8, 9).stream().collect(java.util.stream.Collectors.toList())
        Bmc.check(out.size == 3 && out[0] == 7 && out[2] == 9)
    }
}
