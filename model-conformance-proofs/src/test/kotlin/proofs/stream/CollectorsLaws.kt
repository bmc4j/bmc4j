package proofs.stream

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the map-producing Collectors: toMap and groupingBy. Like the
 * other stream laws these are CProver-backed (not JVM-runnable in isolation), so @BmcProof laws over
 * concrete small lists are the conformance check. Shapes mirror what JBMC devirtualizes: a concrete
 * source list and simple non-capturing lambdas. The collectors fold into the bounded HashMap /
 * ArrayList models via ListStream.collect.
 */
class CollectorsLaws {

    private fun listOf3(a: Int, b: Int, c: Int): ArrayList<Int> {
        val xs = ArrayList<Int>()
        xs.add(a); xs.add(b); xs.add(c)
        return xs
    }

    @BmcProof(unwind = 4)
    fun collect_toMap_size_and_lookups() {
        val m = listOf3(1, 2, 3).stream()
            .collect(java.util.stream.Collectors.toMap({ k -> k }, { v -> v * 10 }))
        Bmc.check(m.size == 3 && m[1] == 10 && m[2] == 20 && m[3] == 30)
    }

    @BmcProof(unwind = 4)
    fun collect_toMap_distinct_keys_from_value() {
        // key = value, value = value: a faithful identity map over distinct elements.
        val m = listOf3(5, 6, 7).stream()
            .collect(java.util.stream.Collectors.toMap({ k -> k }, { v -> v }))
        Bmc.check(m.size == 3 && m.containsKey(5) && m[7] == 7 && !m.containsKey(8))
    }

    @BmcProof(unwind = 4)
    fun collect_groupingBy_by_parity_group_sizes() {
        // 1,2,4 -> even:{2,4}, odd:{1}
        val m = listOf3(1, 2, 4).stream()
            .collect(java.util.stream.Collectors.groupingBy { it % 2 })
        val evens = m[0]!!
        val odds = m[1]!!
        Bmc.check(m.size == 2 && evens.size == 2 && odds.size == 1)
    }

    @BmcProof(unwind = 4)
    fun collect_groupingBy_preserves_group_contents() {
        // 2,4,6 all even -> single group {2,4,6} in encounter order.
        val m = listOf3(2, 4, 6).stream()
            .collect(java.util.stream.Collectors.groupingBy { it % 2 })
        val evens = m[0]!!
        Bmc.check(m.size == 1 && evens.size == 3 && evens[0] == 2 && evens[2] == 6)
    }

    // ---- Collectors.joining() / joining(sep). Sound now: ListStream.collect builds the result with
    // an explicit StringBuilder (JBMC models append/toString soundly) — no invokedynamic string
    // concat, no regex/format machinery. The result is compared by String.equals to an expected
    // literal, which a nondet stub could not satisfy.

    private fun listOfStr(a: String, b: String, c: String): ArrayList<String> {
        val xs = ArrayList<String>()
        xs.add(a); xs.add(b); xs.add(c)
        return xs
    }

    @BmcProof(unwind = 4)
    fun joining_no_delimiter_concatenates() {
        val s = listOfStr("a", "b", "c").stream()
            .collect(java.util.stream.Collectors.joining())
        Bmc.check(s == "abc")
    }

    @BmcProof(unwind = 8)
    fun joining_with_delimiter_inserts_between() {
        val s = listOfStr("a", "b", "c").stream()
            .collect(java.util.stream.Collectors.joining(","))
        Bmc.check(s == "a,b,c")
    }

    @BmcProof
    fun joining_empty_stream_is_empty_string() {
        val empty = ArrayList<String>()
        val s = empty.stream().collect(java.util.stream.Collectors.joining(","))
        Bmc.check(s == "")
    }

    @BmcProof(unwind = 8)
    fun joining_single_element_has_no_delimiter() {
        val one = ArrayList<String>()
        one.add("solo")
        val s = one.stream().collect(java.util.stream.Collectors.joining(","))
        Bmc.check(s == "solo")
    }
}
