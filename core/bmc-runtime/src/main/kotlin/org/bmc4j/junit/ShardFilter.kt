package org.bmc4j.junit

import org.bmc4j.Shard
import org.bmc4j.ShardSlices
import org.junit.platform.engine.FilterResult
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
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
 *
 * ## Pinning (cost-aware override)
 * Hashing balances by *count*, not *cost*: two of the heaviest proofs can hash into the same shard
 * and make it the leg's long pole. A proof (or its declaring class) annotated [@Shard][Shard] is
 * routed to its declared 1-based shard instead of its hash bucket, so known-slow proofs can be
 * deliberately spread one-per-shard. A method-level pin wins over a class-level one; an out-of-range
 * pin folds back into range as `((value - 1) % count) + 1` (a pin never silently drops a proof — see
 * the fail-open philosophy above). Unpinned proofs keep the hash distribution, and the two sets stay
 * a partition of the suite (every proof is selected by exactly one shard).
 *
 * ## Slice-sharding ([@ShardSlices][ShardSlices])
 * A proof annotated [@ShardSlices][ShardSlices] opts its `domainSplit` slices into being spread
 * across shards instead of being pinned (with the whole method) to one. Such a method is INCLUDED on
 * EVERY shard here -- neither hashed nor pinned -- so each shard can run its slice subset (the actual
 * slice-to-shard partition + the single-shard cover live in the split fan-out; see [SliceShard] /
 * `BmcProofExtension.runSplitProof`). This inclusion overrides any `@Shard` pin on the same method: a
 * pin to one shard would defeat the cross-shard fan-out, so the two annotations must not be combined
 * and the pin is ignored for a slice-sharded method.
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
        // A @ShardSlices proof shards at the SLICE level, not the method level: it must run on EVERY
        // shard so each can verify its slice subset (the partition + the single-shard cover are applied
        // later in the split fan-out). So include it here unconditionally, overriding any hash bucket
        // or @Shard pin (a pin to one shard would defeat slice distribution).
        if (slicesSharded(descriptor)) {
            return FilterResult.included("slice-sharded (runs on every shard) $index/$count")
        }
        // A @Shard pin overrides the hash distribution; an out-of-range pin folds back into range
        // (never a silent skip). An unpinned proof keeps its hash bucket. Both are 0-based here.
        val pinned = pinnedBucket(descriptor)
        val bucket = pinned ?: Math.floorMod(descriptor.uniqueId.toString().hashCode(), count)
        // index is 1-based, buckets are 0-based.
        return if (bucket == index - 1) {
            FilterResult.included(if (pinned != null) "pinned shard $index/$count" else "shard $index/$count")
        } else {
            FilterResult.excluded("other shard (bucket ${bucket + 1}/$count)")
        }
    }

    /**
     * The 0-based shard bucket a proof is pinned to by [Shard] (method annotation, else its declaring
     * class), or `null` if unpinned. The declared value is 1-based; an out-of-range value is folded
     * back into `0 until count` deterministically so a pin never drops a proof.
     */
    private fun pinnedBucket(descriptor: TestDescriptor): Int? {
        val pin = shardAnnotation(descriptor) ?: return null
        // 1-based -> 0-based, wrapped into range. floorMod keeps a (defensive) value <= 0 in range too.
        return Math.floorMod(pin.value - 1, count)
    }

    /** True iff the proof method carries [ShardSlices] (slice-level sharding). Only meaningful on the
     *  method (slices belong to a single fan-out), so unlike [Shard] there is no class-level fallback. */
    private fun slicesSharded(descriptor: TestDescriptor): Boolean =
            proofMethod(descriptor)?.isAnnotationPresent(ShardSlices::class.java) == true

    /** The [Shard] annotation on the proof method, else on its declaring class, else `null`. */
    private fun shardAnnotation(descriptor: TestDescriptor): Shard? {
        val method = proofMethod(descriptor) ?: return null
        // Method pin wins; fall back to the declaring class pin (Shard is @Inherited, reaching subclasses).
        return method.getAnnotation(Shard::class.java) ?: method.declaringClass.getAnnotation(Shard::class.java)
    }

    /** Resolve the [java.lang.reflect.Method] backing a leaf proof descriptor, or `null` (not a method
     *  source / class not loadable / no such method). Resolves via the context classloader without
     *  initializing the class — reading an annotation needs no static init — and matches on name only:
     *  a parameterless or symbolic-parameter @BmcProof has a unique simple name within its class, and
     *  resolving exact parameter types would couple the filter to the proof's signature for no benefit.
     *  Any failure yields null -> the proof keeps its hash bucket / no slice-sharding, fail-open. */
    private fun proofMethod(descriptor: TestDescriptor): java.lang.reflect.Method? {
        val source = descriptor.source.orElse(null) as? MethodSource ?: return null
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        val clazz = runCatching { Class.forName(source.className, false, loader) }.getOrNull()
                ?: return null
        return clazz.declaredMethods.firstOrNull { it.name == source.methodName }
    }

    private companion object {
        const val COUNT_PROP = "bmc.shard.count"
        const val INDEX_PROP = "bmc.shard.index"

        /** A system property as an int, or 0 when absent/blank/unparseable (→ filter stays inert). */
        fun intProp(key: String): Int =
                System.getProperty(key)?.trim()?.toIntOrNull() ?: 0
    }
}
