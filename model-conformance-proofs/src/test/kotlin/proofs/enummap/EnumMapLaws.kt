package proofs.enummap

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.EnumMap

private enum class Day { MON, TUE, WED, THU, FRI }

/**
 * Model proofs (axis 2): algebraic laws the [java.util.EnumMap] model must satisfy under JBMC's own
 * semantics, over symbolic enum keys (so they hold for every key at once). All must pass.
 */
class EnumMapLaws {

    @BmcProof
    fun new_map_is_empty() {
        val m = EnumMap<Day, Int>(Day::class.java)
        Bmc.check(m.size == 0 && m.isEmpty())
    }

    @BmcProof(unwind = 8)
    fun put_then_get_over_symbolic_key() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(m[k] == v && m.size == 1 && m.containsKey(k))
    }

    @BmcProof(unwind = 8)
    fun put_overwrites_same_key_and_returns_old() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        val a = Bmc.anyInt()
        val b = Bmc.anyInt()
        m.put(k, a)
        val old = m.put(k, b)
        Bmc.check(old == a && m[k] == b && m.size == 1)
    }

    @BmcProof(unwind = 8)
    fun get_absent_key_is_null() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        Bmc.check(m[k] == null && !m.containsKey(k))
    }

    @BmcProof(unwind = 8)
    fun remove_deletes_the_mapping() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        val v = Bmc.anyInt()
        m[k] = v
        val removed = m.remove(k)
        Bmc.check(removed == v && !m.containsKey(k) && m.size == 0)
    }

    @BmcProof(unwind = 8)
    fun getOrDefault_falls_back_when_absent() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        val d = Bmc.anyInt()
        Bmc.check(m.getOrDefault(k, d) == d)
    }

    @BmcProof
    fun put_null_key_throws_npe() {
        val m = EnumMap<Day, Int>(Day::class.java)
        var threw = false
        try {
            m.put(null as Day?, 1) as Any?
        } catch (e: NullPointerException) {
            threw = true
        }
        Bmc.check(threw)
    }

    @BmcProof(unwind = 8)
    fun keyset_iterates_in_ordinal_order() {
        val m = EnumMap<Day, Int>(Day::class.java)
        // insert out of ordinal order; keySet must come back ordinal-sorted (WED < FRI by ordinal)
        m[Day.FRI] = 1
        m[Day.WED] = 2
        val it = m.keys.iterator()
        Bmc.check(it.next() == Day.WED && it.next() == Day.FRI && !it.hasNext())
    }

    @BmcProof(unwind = 8)
    fun containsValue_finds_a_put_value() {
        val m = EnumMap<Day, Int>(Day::class.java)
        val k = Bmc.anyOf(Day.values())
        val v = Bmc.anyInt()
        m[k] = v
        Bmc.check(m.containsValue(v) && !m.containsValue(v + 1))
    }
}
