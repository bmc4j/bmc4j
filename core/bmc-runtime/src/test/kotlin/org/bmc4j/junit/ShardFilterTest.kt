package org.bmc4j.junit

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor

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
}
