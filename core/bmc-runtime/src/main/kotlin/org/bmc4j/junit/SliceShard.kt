package org.bmc4j.junit

/**
 * The pure slice-to-shard selection rule for `@ShardSlices` proofs, shared by the runtime split
 * fan-out ([BmcProofExtension.runSplitProof]) and its unit tests so there is one authoritative
 * definition of "which slices/cover does THIS shard run".
 *
 * A `@ShardSlices` `domainSplit` proof runs on EVERY shard (see [ShardFilter]); each shard then
 * executes only a disjoint subset of the proof's slices, plus — on exactly one shard — the cover.
 *
 * The rules:
 * - **Slice i (0-based)** runs on the shard whose 1-based [index] satisfies
 *   `Math.floorMod(i, count) == index - 1`. Every slice is selected by EXACTLY one shard, so the
 *   union over `index = 1..count` is a partition of `0 until sliceCount`.
 * - **The cover** runs on exactly ONE shard, [COVER_SHARD] (shard index 1) — never duplicated across
 *   shards, never dropped.
 *
 * When sharding is off ([active] is false: `count <= 1`, or `index` out of range) every method here
 * selects everything, so an unsharded run executes all slices and the cover exactly as today.
 */
internal class SliceShard private constructor(
        /** 1-based total shard count (`bmc.shard.count`). */
        val count: Int,
        /** 1-based index of THIS shard (`bmc.shard.index`). */
        val index: Int,
) {

    /** True iff sharding is configured to an active, in-range selection (mirrors [ShardFilter]). */
    val active: Boolean = count > 1 && index in 1..count

    /** Does THIS shard run slice [sliceIndex] (0-based)? Always true when sharding is off. */
    fun runsSlice(sliceIndex: Int): Boolean =
            !active || Math.floorMod(sliceIndex, count) == index - 1

    /** Does THIS shard run the cover? The cover is pinned to [COVER_SHARD]; always true when off. */
    fun runsCover(): Boolean = !active || index == COVER_SHARD

    companion object {
        /** The single 1-based shard the cover run is pinned to under slice-sharding. */
        const val COVER_SHARD: Int = 1

        private const val COUNT_PROP = "bmc.shard.count"
        private const val INDEX_PROP = "bmc.shard.index"

        /** Read the current shard configuration from the `bmc.shard.*` system properties. */
        fun fromProperties(): SliceShard = SliceShard(intProp(COUNT_PROP), intProp(INDEX_PROP))

        /** Exposed for unit tests: build a selector from explicit count/index. */
        internal fun of(count: Int, index: Int): SliceShard = SliceShard(count, index)

        /** A system property as an int, or 0 when absent/blank/unparseable (-> selector stays inert). */
        private fun intProp(key: String): Int =
                System.getProperty(key)?.trim()?.toIntOrNull() ?: 0
    }
}
