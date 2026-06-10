package proofs.kotlinabstractcollections

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Regression proofs for the Kotlin-side devirtualization fix: a collection that `extends` one of the
 * `kotlin.collections.Abstract*` skeletal bases, overrides only the size/iteration primitives, is held
 * through the read-only `Set`/`Map`/`List`/`Collection` interface, and is proven through it.
 *
 * These mirror the `java.util.Abstract*` subclass laws but for the Kotlin bases that
 * `kotlinx.collections.immutable`'s persistent collections extend. The hard case the real bug exposed is
 * a `getSize` that DELEGATES to a backing collection's `size` (as `PersistentOrderedSet.getSize` reads
 * its backing `PersistentHashMap.size`): the inherited `size()`/`isEmpty()` JVM bridge on the abstract
 * base must devirtualize through that nested call. Before these bases were modeled and bundled, the
 * inherited `Set.size()` nondet-stubbed and a `size`/`isEmpty` property came back a false UNKNOWN.
 *
 * Each `BackedBy*` subclass holds a backing JDK collection and reports `getSize()` as `backing.size` —
 * the delegation shape — so the proofs exercise exactly the devirt path the kotlinx fix needed.
 */
class KotlinAbstractCollectionSubclassLaws {

    // ---- kotlin.collections.AbstractSet, getSize delegating to a backing Set; held as kotlin Set -----

    private class BackedSet(private val backing: Set<Int>) : kotlin.collections.AbstractSet<Int>() {
        override val size: Int get() = backing.size
        override fun iterator(): Iterator<Int> = backing.iterator()
    }

    @BmcProof
    fun abstractSet_empty_size_isEmpty_via_interface() {
        val s: Set<Int> = BackedSet(HashSet())
        Bmc.check(s.size == 0 && s.isEmpty())
    }

    @BmcProof
    fun abstractSet_singleton_size_via_interface() {
        val x = Bmc.anyInt()
        val backing = HashSet<Int>(); backing.add(x)
        val s: Set<Int> = BackedSet(backing)
        Bmc.check(s.size == 1 && !s.isEmpty())
    }

    @BmcProof(expect = Verdict.REFUTED)
    fun abstractSet_empty_claimed_size_one_refutes() {
        val s: Set<Int> = BackedSet(HashSet())
        Bmc.check(s.size == 1)
    }

    // ---- kotlin.collections.AbstractMap, getSize delegating to a backing Map; held as kotlin Map -----

    private class BackedMap(private val backing: Map<Int, Int>) : kotlin.collections.AbstractMap<Int, Int>() {
        override val entries: Set<Map.Entry<Int, Int>> get() = backing.entries
    }

    @BmcProof
    fun abstractMap_empty_size_isEmpty_via_interface() {
        val m: Map<Int, Int> = BackedMap(HashMap())
        Bmc.check(m.size == 0 && m.isEmpty())
    }

    @BmcProof
    fun abstractMap_singleton_size_via_interface() {
        val k = Bmc.anyInt()
        val v = Bmc.anyInt()
        val backing = HashMap<Int, Int>(); backing[k] = v
        val m: Map<Int, Int> = BackedMap(backing)
        Bmc.check(m.size == 1 && !m.isEmpty())
    }

    @BmcProof(expect = Verdict.REFUTED)
    fun abstractMap_empty_claimed_nonempty_refutes() {
        val m: Map<Int, Int> = BackedMap(HashMap())
        Bmc.check(!m.isEmpty())
    }
}
