package proofs.concurrenthashmap

import java.util.concurrent.ConcurrentHashMap
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the ConcurrentHashMap model's sequential bulk surface must
 * satisfy under JBMC's own semantics. On the single thread BMC analyzes, the parallelismThreshold is
 * irrelevant, so each bulk op is its sequential definition — these pin that equivalence: forEach
 * visits every mapping, search returns the first non-null hit, reduce folds over the elements, and the
 * primitive reductions accumulate from the supplied basis. Plus the legacy/size aliases (contains /
 * mappingCount). A threshold of 1 is passed everywhere (one thread is always below any threshold).
 */
class ConcurrentHashMapLaws {

    @BmcProof
    fun contains_is_containsValue() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        Bmc.check(m.contains(10) && !m.contains(99))
    }

    @BmcProof(unwind = 2)
    fun mappingCount_is_size() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        Bmc.check(m.mappingCount() == 2L)
    }

    @BmcProof(unwind = 4)
    fun forEach_visits_every_mapping() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val sum = intArrayOf(0)
        m.forEach(1L) { _, v -> sum[0] += v }
        Bmc.check(sum[0] == 30)
    }

    @BmcProof(unwind = 4)
    fun forEachValue_visits_every_value() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val sum = intArrayOf(0)
        m.forEachValue(1L) { v -> sum[0] += v }
        Bmc.check(sum[0] == 30)
    }

    @BmcProof(unwind = 4)
    fun search_returns_first_non_null_hit() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val hit = m.search(1L) { _, v -> if (v == 20) v else null }
        Bmc.check(hit == 20)
        val miss = m.search(1L) { _, _ -> null }
        Bmc.check(miss == null)
    }

    @BmcProof
    fun searchKeys_returns_first_non_null_hit() {
        val m = ConcurrentHashMap<Int, Int>()
        m[2] = 20
        val hit = m.searchKeys(1L) { k -> if (k == 2) k else null }
        Bmc.check(hit == 2)
    }

    @BmcProof(unwind = 4)
    fun reduceValues_folds_over_the_values() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        m[3] = 30
        val total = m.reduceValues(1L) { a, b -> a + b }
        Bmc.check(total == 60)
    }

    @BmcProof
    fun reduceValues_on_empty_map_is_null() {
        val m = ConcurrentHashMap<Int, Int>()
        Bmc.check(m.reduceValues(1L) { a, b -> a + b } == null)
    }

    @BmcProof(unwind = 4)
    fun reduceValuesToInt_accumulates_from_the_basis() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val total = m.reduceValuesToInt(1L, { v -> v }, 100) { a, b -> a + b }
        Bmc.check(total == 130)   // basis 100 + 10 + 20
    }

    @BmcProof(unwind = 4)
    fun reduceToInt_folds_a_per_entry_projection() {
        val m = ConcurrentHashMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        val total = m.reduceToInt(1L, { k, v -> k + v }, 0) { a, b -> a + b }
        Bmc.check(total == 33)    // (1+10) + (2+20)
    }
}
