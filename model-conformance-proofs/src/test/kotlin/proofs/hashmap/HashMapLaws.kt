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
}
