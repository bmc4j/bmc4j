package org.bmc4j.junit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the slice-to-shard selection rule [SliceShard] used by `@ShardSlices` `domainSplit`
 * proofs: the slices of one proof are partitioned across shards (every slice index selected by exactly
 * one shard), the cover runs on exactly shard 1, and the rule is inert (selects everything) when
 * sharding is off.
 */
internal class SliceShardTest {

    // --- Slice partition: disjoint + covering ---------------------------------

    @Test
    fun slicesArePartition_disjointAndCovering() {
        val count = 4
        val sliceCount = 37 // not a multiple of count, to exercise the uneven tail
        val owners = HashMap<Int, Int>() // sliceIndex -> the single shard that runs it
        for (index in 1..count) {
            val shard = SliceShard.of(count, index)
            for (i in 0 until sliceCount) {
                if (shard.runsSlice(i)) {
                    assertFalse(owners.containsKey(i),
                            "slice $i selected by more than one shard (overlap)")
                    owners[i] = index
                }
            }
        }
        assertEquals(sliceCount, owners.size, "every slice selected by exactly one shard (full coverage)")
    }

    @Test
    fun sliceSelectionRule_isFloorMod() {
        val count = 5
        for (index in 1..count) {
            val shard = SliceShard.of(count, index)
            for (i in 0 until 50) {
                assertEquals(Math.floorMod(i, count) == index - 1, shard.runsSlice(i),
                        "shard $index runs slice $i iff floorMod(i,count) == index-1")
            }
        }
    }

    @Test
    fun slicesAreRoughlyBalanced() {
        val count = 4
        val sliceCount = 400
        for (index in 1..count) {
            val shard = SliceShard.of(count, index)
            val n = (0 until sliceCount).count { shard.runsSlice(it) }
            val expected = sliceCount.toDouble() / count
            assertTrue(n in (expected * 0.8).toInt()..(expected * 1.2).toInt(),
                    "shard $index got $n slices (expected ~$expected) -- floorMod spreads evenly")
        }
    }

    // --- Cover: exactly one shard ---------------------------------------------

    @Test
    fun coverRunsOnExactlyShardOne() {
        val count = 4
        val coverShards = (1..count).filter { SliceShard.of(count, it).runsCover() }
        assertEquals(listOf(SliceShard.COVER_SHARD), coverShards,
                "the cover runs on exactly one shard (shard 1) -- never duplicated, never dropped")
        assertEquals(1, SliceShard.COVER_SHARD, "the cover is pinned to shard index 1")
    }

    // --- Inert when unsharded -------------------------------------------------

    @Test
    fun countOne_runsEverything() {
        val shard = SliceShard.of(1, 1)
        assertFalse(shard.active, "count <= 1 -> inert")
        repeat(20) { assertTrue(shard.runsSlice(it), "unsharded -> run every slice") }
        assertTrue(shard.runsCover(), "unsharded -> run the cover")
    }

    @Test
    fun outOfRangeIndex_runsEverything() {
        // index out of range fails open like ShardFilter: run all slices + the cover, never a silent skip.
        val shard = SliceShard.of(3, 5)
        assertFalse(shard.active)
        repeat(20) { assertTrue(shard.runsSlice(it)) }
        assertTrue(shard.runsCover())
    }

    @Test
    fun unionAcrossShards_coversEverySliceAndExactlyOneCover() {
        // The critical property: union over all shards runs every slice exactly once and the cover once.
        val count = 3
        val sliceCount = 20
        var coverRuns = 0
        val sliceRuns = HashMap<Int, Int>()
        for (index in 1..count) {
            val shard = SliceShard.of(count, index)
            if (shard.runsCover()) coverRuns++
            for (i in 0 until sliceCount) {
                if (shard.runsSlice(i)) sliceRuns.merge(i, 1, Int::plus)
            }
        }
        assertEquals(1, coverRuns, "cover run exactly once across the shard union")
        assertEquals(sliceCount, sliceRuns.size, "every slice run across the union")
        assertTrue(sliceRuns.values.all { it == 1 }, "every slice run exactly once across the union")
    }
}
