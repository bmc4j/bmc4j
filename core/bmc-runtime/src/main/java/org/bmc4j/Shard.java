package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pins a {@link BmcProof} (or a whole proof class) to a specific CI shard, overriding the
 * {@code ShardFilter}'s default hash-based distribution for that proof.
 *
 * <p>Sharding fans a proof leg across N runners (see {@code org.bmc4j.junit.ShardFilter}); by
 * default each proof lands on a shard chosen by a hash of its unique id. That balances by
 * <em>count</em>, but it is blind to <em>cost</em>: nothing stops two of the heaviest proofs from
 * hashing into the same shard, which then becomes the leg's long pole. {@code @Shard} lets you place
 * a known-slow proof on a chosen shard so the expensive ones are deliberately spread one-per-shard
 * instead of left to chance.
 *
 * <pre>{@code
 * @Shard(1)            // pin this slow proof to shard 1
 * @BmcProof
 * void equal_users_with_string_component_have_equal_hashCode() { ... }
 * }</pre>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>Index is 1-based</b>, matching {@code bmc.shard.index} ({@code 1..count}).</li>
 *   <li><b>Method beats class.</b> A {@code @Shard} on the method wins over one on its declaring
 *       class; a class-level {@code @Shard} pins every proof in the class that doesn't override it.
 *       (The annotation is {@link Inherited}, so a class-level pin also reaches subclasses.)</li>
 *   <li><b>Inert when unsharded.</b> With {@code bmc.shard.count <= 1} (or sharding off) the
 *       annotation does nothing — local/IDE runs are unaffected.</li>
 *   <li><b>Out-of-range fails open, deterministically.</b> If {@code value} exceeds the active shard
 *       count (e.g. {@code @Shard(5)} under a 3-shard run), the proof is NOT dropped — it is folded
 *       back into range as {@code ((value - 1) % count) + 1}. A pin is never a silent skip; a stale
 *       or over-large index just degrades to a deterministic in-range shard.</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Shard {

    /** 1-based shard index to pin this proof (or class of proofs) to. */
    int value();
}
