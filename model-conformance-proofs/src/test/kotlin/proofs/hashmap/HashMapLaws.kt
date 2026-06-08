package proofs.hashmap

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the HashMap model must satisfy under JBMC's own
 * semantics, over symbolic keys/values (so they hold for every value at once). All must pass.
 */
class HashMapLaws {

    @BmcProof
    fun new_map_is_empty() {
        val m = HashMap<Int, Int>()
        Bmc.check(m.size == 0 && m.isEmpty())
    }

    @BmcProof
    fun put_then_get() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(m[k] == v && m.size == 1 && m.containsKey(k))
    }

    @BmcProof
    fun put_overwrites_same_key_and_returns_old() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        m.put(k, a)
        val old = m.put(k, b)
        Bmc.check(old == a && m[k] == b && m.size == 1)
    }

    @BmcProof
    fun get_absent_key_is_null() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        Bmc.check(m[k] == null && !m.containsKey(k))
    }

    @BmcProof
    fun getOrDefault_falls_back_when_absent() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val d = Bmc.anyInt()
        Bmc.check(m.getOrDefault(k, d) == d)
    }

    @BmcProof
    fun remove_deletes_the_mapping() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        val removed = m.remove(k)
        Bmc.check(removed == v && !m.containsKey(k) && m.size == 0)
    }

    @BmcProof
    fun containsValue_finds_a_put_value() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(m.containsValue(v) && !m.containsValue(v + 1))
    }

    @BmcProof
    fun putIfAbsent_inserts_when_absent_then_keeps_first() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        val first = m.putIfAbsent(k, a)         // absent -> returns null, installs a
        val second = m.putIfAbsent(k, b)        // present -> returns a, leaves a in place
        Bmc.check(first == null && second == a && m[k] == a && m.size == 1)
    }

    @BmcProof
    fun keySet_and_values_snapshot_the_map() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(m.keys.size == 1 && m.keys.contains(k) && m.values.contains(v))
    }

    @BmcProof
    fun entrySet_iterates_the_mappings() {
        val m = HashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        var sum = 0
        for (e in m.entries) {
            sum += e.value
        }
        Bmc.check(sum == 30)
    }

    @BmcProof
    fun copy_constructor_preserves_mappings() {
        val src = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        src[k] = v
        val copy = HashMap<Int, Int>(src)
        Bmc.check(copy.size == 1 && copy[k] == v && copy.containsKey(k))
    }

    // --- functional-arg ops (lambdas through the model; present-but-null / null-removal traps) ------

    @BmcProof
    fun computeIfAbsent_installs_when_absent_then_leaves_present() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        val first = m.computeIfAbsent(k) { v }      // absent -> compute via lambda, install
        val second = m.computeIfAbsent(k) { v + 1 } // present -> NOT recomputed
        Bmc.check(first == v && second == v && m[k] == v && m.size == 1)
    }

    @BmcProof
    fun compute_null_result_removes_the_mapping() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        val r = m.compute(k) { _, _ -> null }       // null result removes
        Bmc.check(r == null && !m.containsKey(k) && m.size == 0)
    }

    @BmcProof
    fun computeIfPresent_recomputes_present_only() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt(-1000, 1000)
        val v = Bmc.anyInt(-1000, 1000)
        // Absent: untouched (null).
        Bmc.check(m.computeIfPresent(k) { _, x -> x + 1 } == null && !m.containsKey(k))
        // Present: recomputed via the lambda.
        m[k] = v
        val r = m.computeIfPresent(k) { _, x -> x + 1 }
        Bmc.check(r == v + 1 && m[k] == v + 1)
    }

    @BmcProof
    fun merge_absent_installs_present_combines() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt(-1000, 1000)
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        val first = m.merge(k, a) { old, value -> old + value }   // absent -> install a
        val second = m.merge(k, b) { old, value -> old + value }  // present -> a + b
        Bmc.check(first == a && second == a + b && m[k] == a + b)
    }

    @BmcProof
    fun merge_null_result_removes() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        val r = m.merge(k, v) { _, _ -> null }      // null merge result removes
        Bmc.check(r == null && !m.containsKey(k))
    }

    @BmcProof
    fun putIfAbsent_only_when_absent() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        Bmc.check(m.putIfAbsent(k, a) == null)      // absent -> installs a, returns null
        Bmc.check(m.putIfAbsent(k, b) == a)         // present -> returns existing a, unchanged
        Bmc.check(m[k] == a)
    }

    // --- bulk / compare-and-remove / replaceAll -----------------------------------------------------

    @BmcProof
    fun putAll_inserts_every_source_mapping() {
        val src = HashMap<Int, Int>()
        val k1 = Bmc.anyInt()
        val v1 = Bmc.anyInt()
        src[k1] = v1
        val dst = HashMap<Int, Int>()
        val k0 = k1 + 1                              // distinct key
        val v0 = Bmc.anyInt()
        dst[k0] = v0
        dst.putAll(src)
        Bmc.check(dst.size == 2 && dst[k0] == v0 && dst[k1] == v1)
    }

    @BmcProof
    fun remove_key_value_only_on_value_match() {
        val m = HashMap<Int, Int>()
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(!m.remove(k, v + 1))              // wrong value -> not removed
        Bmc.check(m.containsKey(k))
        Bmc.check(m.remove(k, v))                   // right value -> removed
        Bmc.check(!m.containsKey(k) && m.size == 0)
    }

    @BmcProof
    fun replaceAll_remaps_every_value_via_lambda() {
        val m = HashMap<Int, Int>()
        val k1 = Bmc.anyInt(-1000, 1000)
        val k2 = k1 + 1                             // distinct key
        m[k1] = 10
        m[k2] = 20
        m.replaceAll { _, v -> v + 1 }              // BiFunction through the model
        Bmc.check(m[k1] == 11 && m[k2] == 21 && m.size == 2)
    }

    @BmcProof
    fun forEach_visits_every_mapping_via_lambda() {
        val m = HashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val sum = intArrayOf(0)
        m.forEach { _, v -> sum[0] += v }           // lambda through the model's forEach
        Bmc.check(sum[0] == 30)
    }
}
