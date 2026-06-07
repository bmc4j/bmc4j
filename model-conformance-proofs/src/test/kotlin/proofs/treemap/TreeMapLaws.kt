package proofs.treemap

import java.util.TreeMap
import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2): algebraic laws the TreeMap model's NavigableMap navigation must satisfy
 * under JBMC's own semantics. The navigation ops are a bounded sorted scan over the key set, so these
 * pin the ordering semantics real proofs rely on — first/last, the inclusive/exclusive bounds of the
 * ceiling/floor/higher/lower family, and the empty-map exception/null split. Symbolic where the range
 * stays small; concrete keys where the ordering relationship is the point.
 */
class TreeMapLaws {

    @BmcProof
    fun firstKey_and_lastKey_are_the_min_and_max() {
        val m = TreeMap<Int, Int>()
        m[3] = 30
        m[1] = 10
        m[2] = 20
        Bmc.check(m.firstKey() == 1 && m.lastKey() == 3)
    }

    @BmcProof
    fun firstEntry_and_lastEntry_carry_the_min_and_max_mapping() {
        val m = TreeMap<Int, Int>()
        m[5] = 50
        m[2] = 20
        val first = m.firstEntry()
        val last = m.lastEntry()
        Bmc.check(first.key == 2 && first.value == 20)
        Bmc.check(last.key == 5 && last.value == 50)
    }

    @BmcProof
    fun ceilingKey_is_least_key_at_or_above() {
        val m = TreeMap<Int, Int>()
        m[2] = 0
        m[4] = 0
        m[6] = 0
        Bmc.check(m.ceilingKey(4) == 4)   // inclusive: exact key qualifies
        Bmc.check(m.ceilingKey(3) == 4)   // rounds up to the next key
        Bmc.check(m.ceilingKey(7) == null) // nothing at or above
    }

    @BmcProof
    fun floorKey_is_greatest_key_at_or_below() {
        val m = TreeMap<Int, Int>()
        m[2] = 0
        m[4] = 0
        m[6] = 0
        Bmc.check(m.floorKey(4) == 4)     // inclusive
        Bmc.check(m.floorKey(5) == 4)     // rounds down
        Bmc.check(m.floorKey(1) == null)  // nothing at or below
    }

    @BmcProof
    fun higherKey_and_lowerKey_are_strict() {
        val m = TreeMap<Int, Int>()
        m[2] = 0
        m[4] = 0
        m[6] = 0
        Bmc.check(m.higherKey(4) == 6)    // strictly greater
        Bmc.check(m.lowerKey(4) == 2)     // strictly less
        Bmc.check(m.higherKey(6) == null) // nothing strictly greater than the max
        Bmc.check(m.lowerKey(2) == null)  // nothing strictly less than the min
    }

    @BmcProof
    fun navigation_on_empty_map_is_null_for_the_bound_family() {
        val m = TreeMap<Int, Int>()
        val k = Bmc.anyInt()
        Bmc.check(m.firstEntry() == null && m.lastEntry() == null)
        Bmc.check(m.ceilingKey(k) == null && m.floorKey(k) == null)
        Bmc.check(m.higherKey(k) == null && m.lowerKey(k) == null)
    }

    @BmcProof
    fun comparator_is_null_for_natural_ordering() {
        val m = TreeMap<Int, Int>()
        Bmc.check(m.comparator() == null)
    }
}
