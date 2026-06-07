package org.bmc4j.junit

import org.bmc4j.Shard
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource

/**
 * Unit tests for the proof-sharding [ShardFilter]: it is inert unless configured, partitions the
 * suite into disjoint, covering, roughly-balanced slices when configured, is stable run-to-run, and
 * fails OPEN (includes everything) on misconfiguration — never silently dropping a proof, which would
 * report an unproven proof as green.
 */
internal class ShardFilterTest {

    @AfterEach
    fun clearProps() {
        System.clearProperty("bmc.shard.count")
        System.clearProperty("bmc.shard.index")
    }

    /** A leaf (non-container) test descriptor whose unique id encodes a method, like a real proof. */
    private fun leaf(id: String): TestDescriptor {
        val uid = UniqueId.forEngine("junit-jupiter")
                .append("class", "proofs.Example")
                .append("method", id)
        return object : AbstractTestDescriptor(uid, id) {
            override fun getType() = TestDescriptor.Type.TEST
        }
    }

    private fun container(id: String): TestDescriptor {
        val uid = UniqueId.forEngine("junit-jupiter").append("class", id)
        return object : AbstractTestDescriptor(uid, id) {
            override fun getType() = TestDescriptor.Type.CONTAINER
        }
    }

    private fun included(d: TestDescriptor) = !ShardFilter().apply(d).excluded()

    // --- @Shard pin fixtures & helpers ----------------------------------------

    /**
     * A leaf descriptor backed by a real [MethodSource] so the filter can resolve the method/class
     * and read its [Shard] annotation. The unique id is kept distinct per (class, method) so the
     * hash fallback still varies for unpinned methods.
     */
    private fun sourced(clazz: Class<*>, method: String): TestDescriptor {
        val uid = UniqueId.forEngine("junit-jupiter")
                .append("class", clazz.name)
                .append("method", method)
        return object : AbstractTestDescriptor(uid, "${clazz.simpleName}.$method") {
            override fun getType() = TestDescriptor.Type.TEST
            override fun getSource(): java.util.Optional<TestSource> =
                    java.util.Optional.of(MethodSource.from(clazz.name, method))
        }
    }

    /** All shard indices (1-based) that select [d] under a `count`-way split. */
    private fun selectingShards(d: TestDescriptor, count: Int): List<Int> =
            (1..count).filter { index ->
                System.setProperty("bmc.shard.count", count.toString())
                System.setProperty("bmc.shard.index", index.toString())
                included(d)
            }

    @Suppress("unused") // methods are resolved reflectively by the filter
    private class Pinned {
        @Shard(1) fun pinnedToOne() {}
        @Shard(2) fun pinnedToTwo() {}
        @Shard(3) fun pinnedToThree() {}
        @Shard(5) fun pinnedOutOfRange() {} // > count in the 3-way tests
        fun unpinnedMethod() {}
    }

    @Shard(2)
    @Suppress("unused")
    private class ClassPinnedToTwo {
        fun inheritsClassPin() {}
        @Shard(3) fun overridesToThree() {}
    }

    // --- Inert unless configured ----------------------------------------------

    @Test
    fun unset_includesEverything() {
        repeat(50) { assertTrue(included(leaf("proof$it")), "no sharding props -> include all") }
    }

    @Test
    fun countOne_includesEverything() {
        System.setProperty("bmc.shard.count", "1")
        System.setProperty("bmc.shard.index", "1")
        repeat(50) { assertTrue(included(leaf("proof$it")), "count<=1 -> sharding off") }
    }

    // --- Partition: disjoint + covering ---------------------------------------

    @Test
    fun shardsArePartition_disjointAndCovering() {
        val count = 3
        val ids = (0 until 300).map { leaf("proof$it") }
        val seen = mutableSetOf<String>()
        for (index in 1..count) {
            System.setProperty("bmc.shard.count", count.toString())
            System.setProperty("bmc.shard.index", index.toString())
            for (d in ids) {
                if (included(d)) {
                    assertTrue(seen.add(d.uniqueId.toString()),
                            "each proof selected by exactly one shard (no overlap)")
                }
            }
        }
        assertEquals(ids.size, seen.size, "every proof selected by some shard (full coverage)")
    }

    @Test
    fun shardsAreRoughlyBalanced() {
        val count = 3
        val total = 600
        val ids = (0 until total).map { leaf("proof$it") }
        for (index in 1..count) {
            System.setProperty("bmc.shard.count", count.toString())
            System.setProperty("bmc.shard.index", index.toString())
            val n = ids.count { included(it) }
            val expected = total.toDouble() / count
            assertTrue(n in (expected * 0.7).toInt()..(expected * 1.3).toInt(),
                    "shard $index got $n (expected ~$expected) — buckets should be roughly even")
        }
    }

    // --- Stability ------------------------------------------------------------

    @Test
    fun selectionIsStable_acrossInstances() {
        System.setProperty("bmc.shard.count", "4")
        System.setProperty("bmc.shard.index", "2")
        val d = leaf("stableProof")
        assertEquals(included(d), included(d), "same id always lands on the same shard")
    }

    // --- Containers always pass ----------------------------------------------

    @Test
    fun containersAlwaysIncluded_evenWhenSharded() {
        System.setProperty("bmc.shard.count", "3")
        System.setProperty("bmc.shard.index", "1")
        repeat(20) { assertTrue(included(container("proofs.Class$it")),
                "container nodes are never filtered (would drop their subtree)") }
    }

    // --- Fail-open on misconfiguration ----------------------------------------

    @Test
    fun indexOutOfRange_failsOpen() {
        System.setProperty("bmc.shard.count", "3")
        System.setProperty("bmc.shard.index", "5") // > count
        repeat(30) { assertTrue(included(leaf("proof$it")), "out-of-range index -> include all (no silent skip)") }
    }

    @Test
    fun indexZero_failsOpen() {
        System.setProperty("bmc.shard.count", "3")
        System.setProperty("bmc.shard.index", "0")
        repeat(30) { assertTrue(included(leaf("proof$it")), "index 0 (not 1-based) -> include all") }
    }

    @Test
    fun unparseableValues_failOpen() {
        System.setProperty("bmc.shard.count", "abc")
        System.setProperty("bmc.shard.index", "xyz")
        repeat(30) { assertTrue(included(leaf("proof$it")), "garbage props -> include all") }
    }

    @Test
    fun countSetButIndexMissing_failsOpen() {
        System.setProperty("bmc.shard.count", "3")
        repeat(30) { assertTrue(included(leaf("proof$it")), "missing index -> include all") }
    }

    // --- @Shard: pinned proofs go to their declared shard --------------------

    @Test
    fun pinned_runsOnlyOnDeclaredShard() {
        val count = 3
        assertEquals(listOf(1), selectingShards(sourced(Pinned::class.java, "pinnedToOne"), count),
                "@Shard(1) -> exactly shard 1")
        assertEquals(listOf(2), selectingShards(sourced(Pinned::class.java, "pinnedToTwo"), count),
                "@Shard(2) -> exactly shard 2")
        assertEquals(listOf(3), selectingShards(sourced(Pinned::class.java, "pinnedToThree"), count),
                "@Shard(3) -> exactly shard 3")
    }

    @Test
    fun pinOutOfRange_foldsBackDeterministically_neverDropped() {
        val count = 3
        // @Shard(5) under 3 shards: ((5-1) % 3) + 1 = 2. Selected by exactly one shard, never zero.
        assertEquals(listOf(2), selectingShards(sourced(Pinned::class.java, "pinnedOutOfRange"), count),
                "out-of-range pin folds to ((value-1) % count)+1, not a silent skip")
    }

    @Test
    fun classLevelPin_appliesToMethods_methodOverrides() {
        val count = 3
        assertEquals(listOf(2),
                selectingShards(sourced(ClassPinnedToTwo::class.java, "inheritsClassPin"), count),
                "class @Shard(2) pins a method with no own pin")
        assertEquals(listOf(3),
                selectingShards(sourced(ClassPinnedToTwo::class.java, "overridesToThree"), count),
                "method @Shard(3) overrides the class @Shard(2)")
    }

    @Test
    fun pinIsInert_whenUnsharded() {
        // count <= 1 -> filter off; a pin must not change that (still runs everywhere/once).
        System.setProperty("bmc.shard.count", "1")
        System.setProperty("bmc.shard.index", "1")
        assertTrue(included(sourced(Pinned::class.java, "pinnedToTwo")),
                "@Shard is inert when sharding is disabled")
    }

    // --- @Shard: union-completeness (the critical property) ------------------

    @Test
    fun mixedPinnedAndHashed_unionIsExactlyOnce_noLossNoDuplication() {
        val count = 3
        // A realistic mix: several pinned (incl. class-pinned and out-of-range) plus a crowd of
        // hash-distributed proofs. The union of all shards must select each proof EXACTLY once.
        val pinned = listOf(
                sourced(Pinned::class.java, "pinnedToOne"),
                sourced(Pinned::class.java, "pinnedToTwo"),
                sourced(Pinned::class.java, "pinnedToThree"),
                sourced(Pinned::class.java, "pinnedOutOfRange"),
                sourced(Pinned::class.java, "unpinnedMethod"),
                sourced(ClassPinnedToTwo::class.java, "inheritsClassPin"),
                sourced(ClassPinnedToTwo::class.java, "overridesToThree"))
        val hashed = (0 until 300).map { leaf("hashProof$it") }
        val all = pinned + hashed

        val timesSelected = HashMap<String, Int>()
        for (index in 1..count) {
            System.setProperty("bmc.shard.count", count.toString())
            System.setProperty("bmc.shard.index", index.toString())
            for (d in all) {
                if (included(d)) {
                    timesSelected.merge(d.uniqueId.toString(), 1, Int::plus)
                }
            }
        }
        assertEquals(all.size, timesSelected.size,
                "every proof (pinned or hashed) selected by some shard — no loss")
        assertTrue(timesSelected.values.all { it == 1 },
                "every proof selected by EXACTLY one shard — no duplication across the shard union")
    }
}
