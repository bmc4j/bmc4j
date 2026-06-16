package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a {@code domainSplit} {@link BmcProof} into <b>slice-level sharding</b>: instead of the whole
 * proof (and all its slices) running on the single shard its method hashes to, the proof runs on
 * EVERY shard, and each shard verifies a disjoint subset of its slices.
 *
 * <p>The ordinary {@code org.bmc4j.junit.ShardFilter} balances a proof leg at the <em>method</em>
 * level (see {@link Shard}): each proof lands on one shard chosen by a hash of its id. That is the
 * right granularity for a suite of many independent proofs, but it cannot scale a SINGLE proof that
 * fans out into dozens or hundreds of slices (a hard numeric range tiled into value windows) — all
 * N slices run in the one shard the method hashed to, which becomes the leg's long pole.
 * {@code @ShardSlices} pushes sharding down a level: the N slices of one proof are spread across the
 * shards.
 *
 * <pre>{@code
 * @ShardSlices         // fan this proof's slices out across the shards
 * @BmcProof
 * void product_never_overflows_over_the_full_int_range() {
 *     Bmc.domainSplit(true);
 *     Bmc.slice(window0); Bmc.slice(window1); ... // many value-window slices
 *     ... // the body, proven once per slice under that window's assume
 * }
 * }</pre>
 *
 * <h2>Why an explicit marker is required</h2>
 * The {@code ShardFilter} runs at JUnit <em>discovery</em> time, before any proof is analysed, so it
 * cannot know a proof will fan out into slices. And slice-sharding changes a method's shard semantics
 * (it must run on every shard, not be hashed to one), so it has to be opted into explicitly. A proof
 * WITHOUT this annotation behaves exactly as today: it is hashed to one shard and all its slices run
 * there.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>Runs on every shard.</b> {@code ShardFilter} INCLUDES a {@code @ShardSlices} method on all
 *       shards (it is never hashed to one), so each shard can execute its slice subset.</li>
 *   <li><b>Slice distribution.</b> With {@code bmc.shard.count = N > 1}, shard {@code index} (1-based)
 *       runs only the slices whose 0-based index {@code i} satisfies
 *       {@code Math.floorMod(i, count) == index - 1}; the other slices are SKIPPED on this shard
 *       (not failed, not havoc'd). Across all shards this is a partition: every slice runs on exactly
 *       one shard.</li>
 *   <li><b>Cover on one shard.</b> The cover run (the {@code overall => union(slices)} soundness gate)
 *       runs on exactly ONE shard, <b>shard index 1</b> — never duplicated, never dropped.</li>
 *   <li><b>Inert when unsharded.</b> With {@code bmc.shard.count} unset or {@code <= 1} the annotation
 *       does nothing: the proof runs all its slices plus the cover locally, exactly as today. Local
 *       and IDE runs are unaffected.</li>
 *   <li><b>Aggregation.</b> A cross-shard summary union concludes the proof VERIFIED iff EVERY slice
 *       index {@code 0..N-1} (across all shards) reported VERIFIED AND the cover reported VERIFIED. A
 *       missing slice (a lost or failed shard) is a gap, not a pass — the per-proof summary records
 *       carry the slice index, slice count, and an is-cover flag so the aggregator can verify
 *       completeness.</li>
 * </ul>
 *
 * <h2>Relationship to {@link Shard}</h2>
 * {@code @ShardSlices} and {@code @Shard} are mutually exclusive in intent: {@code @Shard} pins a proof
 * to ONE shard, whereas {@code @ShardSlices} requires it to run on EVERY shard. If both are present on
 * the same method, {@code @ShardSlices} wins — the {@code ShardFilter} includes the method on every
 * shard and ignores the pin (a pin to a single shard would defeat slice distribution). Do not combine
 * them; the pin is silently overridden, never honoured.
 *
 * <p>This annotation only changes WHICH slices a shard runs; the within-shard fan-out cap
 * ({@code bmc.parallelism}) is unaffected, so each shard still fans its own slice subset out across
 * its cores.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardSlices {
}
