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

    // The entry-returning navigation family mirrors the *Key family but carries the value too: the
    // navigated key's CURRENT mapping. Same inclusive/exclusive bounds, same null-when-none split.
    @BmcProof
    fun ceiling_and_floor_entry_carry_the_navigated_mapping() {
        val m = TreeMap<Int, Int>()
        m[2] = 20
        m[4] = 40
        m[6] = 60
        val ceil = m.ceilingEntry(3)          // rounds up to key 4
        val floor = m.floorEntry(5)           // rounds down to key 4
        Bmc.check(ceil.key == 4 && ceil.value == 40)
        Bmc.check(floor.key == 4 && floor.value == 40)
        Bmc.check(m.ceilingEntry(4).key == 4) // inclusive: exact key qualifies
    }

    @BmcProof
    fun higher_and_lower_entry_are_strict_and_null_at_the_ends() {
        val m = TreeMap<Int, Int>()
        m[2] = 20
        m[4] = 40
        m[6] = 60
        Bmc.check(m.higherEntry(4).key == 6 && m.higherEntry(4).value == 60)  // strictly greater
        Bmc.check(m.lowerEntry(4).key == 2 && m.lowerEntry(4).value == 20)    // strictly less
        Bmc.check(m.higherEntry(6) == null && m.lowerEntry(2) == null)        // nothing past the ends
    }

    @BmcProof
    fun entry_navigation_on_empty_map_is_null() {
        val m = TreeMap<Int, Int>()
        val k = Bmc.anyInt()
        Bmc.check(m.ceilingEntry(k) == null && m.floorEntry(k) == null)
        Bmc.check(m.higherEntry(k) == null && m.lowerEntry(k) == null)
    }

    // pollFirstEntry returns the MIN entry and removes it; pollLastEntry the MAX. After polling both
    // ends of a 3-key map only the middle key remains, and a re-poll sees the new extreme.
    @BmcProof
    fun pollFirst_and_pollLast_remove_the_extremes() {
        val m = TreeMap<Int, Int>()
        m[1] = 10
        m[2] = 20
        m[3] = 30
        val first = m.pollFirstEntry()
        val last = m.pollLastEntry()
        Bmc.check(first.key == 1 && first.value == 10)
        Bmc.check(last.key == 3 && last.value == 30)
        Bmc.check(m.size == 1 && m.firstKey() == 2 && m.lastKey() == 2)
    }

    @BmcProof
    fun poll_on_empty_map_is_null() {
        val m = TreeMap<Int, Int>()
        Bmc.check(m.pollFirstEntry() == null && m.pollLastEntry() == null)
        Bmc.check(m.isEmpty())
    }
}
