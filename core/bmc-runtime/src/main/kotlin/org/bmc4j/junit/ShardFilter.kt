package org.bmc4j.junit

import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.launcher.PostDiscoveryFilter

/**
 * Splits a proof suite across CI runners by assigning each test to one of `count` shards by a
 * deterministic hash of its unique id, so N runners each verify a balanced ~1/N of the suite.
 *
 * Proofs are embarrassingly parallel — each spawns its own engine process and its verdict is an
 * independent pure function of its own inputs — but a flat per-proof process cost dominates, so the
 * single lever that scales a full leg's wall-clock is more cores. Sharding hands each runner a
 * disjoint slice; the per-proof verdict cache makes the slices' results trivially mergeable (each
 * entry is keyed by content, so the union of two shards' caches is sound by construction).
 *
 * ## Activation
 * Registered as a [PostDiscoveryFilter] via the JUnit Platform `ServiceLoader` SPI, so it is
 * consulted on **every** test run on a classpath that carries bmc-runtime — but it is **inert**
 * unless explicitly turned on:
 * - `bmc.shard.count` (1-based total shard count) must be `> 1`, AND
 * - `bmc.shard.index` (1-based index of THIS shard, `1..count`) must be in range.
 *
 * With either unset or `count <= 1` the filter includes everything, so ordinary local/IDE runs and
 * any test task that doesn't pass the sharding properties are completely unaffected. Out-of-range or
 * unparseable values fail **open** (include everything) — a misconfigured shard runs the whole suite
 * rather than silently skipping proofs, which would be a soundness hole (an unproven proof reported
 * as green).
 *
 * ## Balance & stability
 * The bucket is `floorMod(uniqueId.hashCode(), count)`. Hashing the unique id (which encodes the
 * method, not just the class) shards at the **method** level, so a 55-proof class and a 1-proof
 * class distribute evenly instead of pinning a whole heavy class to one runner. The mapping is a
 * pure function of the id, so a given proof always lands on the **same** shard run-to-run — its
 * verdict-cache lineage stays continuous, and the slices stay stable as proofs are added.
 */
class ShardFilter : PostDiscoveryFilter {

    private val count: Int = intProp(COUNT_PROP)
    private val index: Int = intProp(INDEX_PROP)

    /** True iff sharding is configured to an active, in-range selection. */
    private val active: Boolean = count > 1 && index in 1..count

    override fun apply(descriptor: TestDescriptor): FilterResult {
        // Inert unless explicitly sharded; never filter container nodes (only their leaf tests
        // carry the proof, and excluding a container would drop its whole subtree).
        if (!active || descriptor.isContainer) {
            return FilterResult.included("not sharded")
        }
        val bucket = Math.floorMod(descriptor.uniqueId.toString().hashCode(), count)
        // index is 1-based, buckets are 0-based.
        return if (bucket == index - 1) {
            FilterResult.included("shard $index/$count")
        } else {
            FilterResult.excluded("other shard (bucket ${bucket + 1}/$count)")
        }
    }

    private companion object {
        const val COUNT_PROP = "bmc.shard.count"
        const val INDEX_PROP = "bmc.shard.index"

        /** A system property as an int, or 0 when absent/blank/unparseable (→ filter stays inert). */
        fun intProp(key: String): Int =
                System.getProperty(key)?.trim()?.toIntOrNull() ?: 0
    }
}
