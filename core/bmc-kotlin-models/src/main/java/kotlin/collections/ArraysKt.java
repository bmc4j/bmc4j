package kotlin.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of Kotlin's {@code ArraysKt} multifile facade for the array <b>copy/fill</b> surface.
 *
 * <p>The headline gap this closes: a {@code copyInto} call — the array-copy at the heart of the
 * kotlinx persistent-collection trie nodes ({@code persistentSetOf().add(x)} copies a child array
 * with {@code copyInto}) — binds to {@code kotlin.collections.ArraysKt.copyInto(...)} /
 * {@code copyInto$default(...)}. The real {@code ArraysKt} multifile facade ({@code ArraysKt} extends
 * {@code ArraysKt___ArraysKt} … {@code ArraysKt__ArraysJVMKt}) is far too large for JBMC to link a
 * single method out of, so it nondet-stubs {@code copyInto} — havocking the copied array and demoting
 * every proof through a persistent collection to UNKNOWN. This model class IS {@code ArraysKt} (a flat
 * class carrying the members directly), so {@code invokestatic ArraysKt.copyInto} resolves straight to
 * a sound element-copy-loop body.
 *
 * <p>Modeled surface: {@code copyInto} (Object[] + all eight primitive element types) and its
 * {@code $default} bridge that applies the {@code destinationOffset=0, startIndex=0, endIndex=size}
 * defaults; {@code copyOf}, {@code copyOfRange}, and {@code fill} (the same eight element types) — the
 * high-value array transforms whose real bodies are sound but unlinkable from the giant facade.
 * {@code copyInto} uses an explicit element-copy loop (JBMC models per-element array writes precisely
 * but treats the {@code System.arraycopy} intrinsic as a weak/havocking copy); the rest delegate to the
 * (loop-backed, separately-audited) {@link java.util.Arrays} model, which JBMC handles soundly over the
 * bounded array.
 *
 * <p><b>The exotic ~1300-member array-extension surface is fully ENUMERATED per member</b> (mirroring
 * {@link CollectionsKt}, which carries no {@code @BmcModelTail}) — there is no class-level
 * {@code @BmcModelTail} catch-all. Each undeclared real member of the facade carries an explicit,
 * audited class-level decision below, in three buckets:
 *
 * <ul>
 *   <li><b>{@code @BmcUnmodelable} (inline)</b> — the lambda-taking higher-order forms
 *       ({@code map{}}/{@code filter{}}/{@code all{}}/{@code any{}}/{@code fold{}}/{@code forEach{}}/
 *       {@code reduce{}}/{@code none{}}/{@code sumOf{}}/{@code maxOf{}}/…) are {@code public inline fun}:
 *       the body lands in the CALLER, so the facade JVM method is never invoked from a Kotlin call site.
 *       (Inline status was read off the stdlib's Kotlin {@code @Metadata}; these are the bulk of the
 *       surface, ~601 members across the nine element types.)</li>
 *   <li><b>{@code @BmcUnmodelable} (wall)</b> — the NON-inline transforms that ARE invoked from a Kotlin
 *       call site but do NOT analyze soundly through the real facade (probed UNKNOWN over a bounded
 *       array — they route through unmodeled kotlin-stdlib internals JBMC nondet-stubs):
 *       {@code sorted}/{@code sortedArray}/{@code distinct}/{@code drop}/{@code take}/{@code slice}/
 *       {@code reversed}/{@code toSet}/{@code toHashSet}/{@code toMutableSet}/{@code toCollection}/
 *       {@code asIterable}/{@code asSequence}/{@code sum}/{@code average}/{@code maxOrNull}/
 *       {@code minOrNull}/{@code binarySearch}/{@code joinTo}/{@code joinToString}/{@code toSortedSet}/
 *       {@code shuffle}/{@code intersect}/{@code union}/{@code subtract}/{@code flatten}/{@code unzip}/
 *       {@code zip}/the {@code Array<Boxed>.toXxxArray()} conversions/… The build-time loud-body
 *       synthesis pass gives each a member-named loud body, so a proof reaching one fails NAMED AND LOUD
 *       under JBMC (a member-named UNKNOWN) rather than silently nondet-stubbing.</li>
 *   <li><b>{@code @BmcNotNeeded}</b> — the NON-inline members that fall through to the real facade and
 *       DO analyze soundly over the bounded array (probed VERIFIED): {@code withIndex} (the index/value
 *       pairing the destructuring {@code for ((i, v) in a.withIndex())} emits). Green-if-reached: no
 *       model body, JBMC uses the real facade.</li>
 * </ul>
 *
 * <p>Per-bucket soundness was established by probing a representative overload of each family with a
 * {@code @BmcProof} over a bounded array (VERIFIED → green/{@code @BmcNotNeeded}; UNKNOWN → loud
 * wall/{@code @BmcUnmodelable}) and applying the family decision to every element-type overload.
 */
// ---- @BmcNotNeeded: non-inline members whose real-facade fall-through is sound (probed VERIFIED) -------
@BmcNotNeeded(member = "withIndex(java.lang.Object[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(boolean[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(byte[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(char[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(double[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(float[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(int[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(long[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "withIndex(short[])", reason = "real stdlib bytecode analyzes soundly under JBMC over the bounded array (probed VERIFIED through the real facade); no model needed (green-if-reached: JBMC falls through to the real facade)")
// ---- @BmcUnmodelable (inline): lambda-taking HOFs inline into the caller; the facade JVM method is
//      never invoked from a Kotlin call site (loud-if-reached, e.g. from a Java caller / reflection) -----
@BmcUnmodelable(member = "all(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "all(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "any(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associate(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(java.lang.Object[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(boolean[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(byte[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(char[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(double[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(float[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(int[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(long[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateBy(short[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(boolean[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(boolean[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(byte[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(byte[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(char[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(char[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(double[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(double[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(float[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(float[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(int[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(int[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(long[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(long[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(short[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateByTo(short[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(boolean[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(byte[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(char[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(double[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(float[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(int[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(long[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateTo(short[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateWith(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "associateWithTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "count(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "distinctBy(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropLastWhile(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "dropWhile(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filter(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexed(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(byte[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(char[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(double[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(float[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(int[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(long[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterIndexedTo(short[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNot(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(byte[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(char[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(double[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(float[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(int[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(long[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterNotTo(short[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(byte[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(char[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(double[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(float[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(int[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(long[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "filterTo(short[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "first(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "firstOrNull(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMap(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapSequence(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapSequenceTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(byte[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(char[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(double[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(float[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(int[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(long[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "flatMapTo(short[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(boolean[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(byte[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(char[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(double[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(float[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(int[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(long[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "fold(short[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(boolean[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(byte[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(char[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(double[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(float[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(int[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(long[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldIndexed(short[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(boolean[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(byte[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(char[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(double[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(float[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(int[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(long[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRight(short[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(boolean[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(byte[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(char[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(double[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(float[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(int[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(long[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "foldRightIndexed(short[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEach(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "forEachIndexed(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(java.lang.Object[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(boolean[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(byte[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(char[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(double[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(float[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(int[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(long[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupBy(short[], kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(java.lang.Object[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(boolean[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(boolean[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(byte[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(byte[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(char[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(char[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(double[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(double[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(float[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(float[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(int[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(int[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(long[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(long[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(short[], java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupByTo(short[], java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "groupingBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfFirst(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "indexOfLast(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "last(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "lastOrNull(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "map(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexed(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedNotNull(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedNotNullTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(byte[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(char[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(double[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(float[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(int[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(long[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapIndexedTo(short[], java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapNotNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapNotNullTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(java.lang.Object[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(boolean[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(byte[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(char[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(double[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(float[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(int[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(long[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "mapTo(short[], java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrNull(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "maxByOrThrow(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrNull(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "minByOrThrow(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "none(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "partition(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduce(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(java.lang.Object[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(boolean[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(byte[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(char[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(double[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(float[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(int[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(long[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexed(short[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(java.lang.Object[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(boolean[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(byte[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(char[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(double[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(float[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(int[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(long[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceIndexedOrNull(short[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceOrNull(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRight(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(java.lang.Object[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(boolean[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(byte[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(char[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(double[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(float[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(int[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(long[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexed(short[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(java.lang.Object[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(boolean[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(byte[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(char[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(double[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(float[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(int[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(long[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightIndexedOrNull(short[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "reduceRightOrNull(short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "runningFold(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "runningFoldIndexed(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "runningReduce(java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "runningReduceIndexed(java.lang.Object[], kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "scan(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "scanIndexed(java.lang.Object[], java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "single(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "singleOrNull(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortByDescending(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedBy(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sortedByDescending(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumBy(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "sumByDouble(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeLastWhile(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(java.lang.Object[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(boolean[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(byte[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(char[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(double[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(float[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(int[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(long[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "takeWhile(short[], kotlin.jvm.functions.Function1)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(java.lang.Object[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(java.lang.Object[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(boolean[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(boolean[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(boolean[], boolean[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(byte[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(byte[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(byte[], byte[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(char[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(char[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(char[], char[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(double[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(double[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(double[], double[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(float[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(float[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(float[], float[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(int[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(int[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(int[], int[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(long[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(long[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(long[], long[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(short[], java.lang.Iterable, kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(short[], java.lang.Object[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
@BmcUnmodelable(member = "zip(short[], short[], kotlin.jvm.functions.Function2)", reason = "inline — body lands in caller; the facade JVM method is never called from a Kotlin call site")
// ---- @BmcUnmodelable (wall): non-inline transforms that route through unmodeled stdlib internals
//      JBMC nondet-stubs (probed UNKNOWN through the real facade); loud-if-reached --------------------
@BmcUnmodelable(member = "any(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "any(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "arrayOfNulls(java.lang.Object[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asIterable(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "asSequence(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "average(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfByte(java.lang.Byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfDouble(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfFloat(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfInt(java.lang.Integer[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfLong(java.lang.Long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "averageOfShort(java.lang.Short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(java.lang.Object[], java.lang.Object, java.util.Comparator, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(java.lang.Object[], java.lang.Object, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(byte[], byte, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(char[], char, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(double[], double, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(float[], float, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(int[], int, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(long[], long, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "binarySearch(short[], short, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "contentDeepEquals(java.lang.Object[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "contentDeepHashCode(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "contentDeepToString(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "copyOfRangeToIndexCheck(int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "distinct(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(java.lang.Object[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(boolean[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(byte[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(char[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(double[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(float[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(int[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(long[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "drop(short[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(java.lang.Object[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(boolean[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(byte[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(char[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(double[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(float[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(int[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(long[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "dropLast(short[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "filterIsInstance(java.lang.Object[], java.lang.Class)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "filterIsInstanceTo(java.lang.Object[], java.util.Collection, java.lang.Class)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "filterNotNull(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "filterNotNullTo(java.lang.Object[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "firstOrNull(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "flatten(java.lang.Object[][])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getIndices(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "getLastIndex(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(java.lang.Object[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(boolean[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(byte[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(char[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(double[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(float[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(int[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(long[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intersect(short[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(java.lang.Object[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(boolean[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(byte[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(char[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(double[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(float[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(int[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(long[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinTo(short[], java.lang.Appendable, java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(java.lang.Object[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(boolean[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(byte[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(char[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(double[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(float[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(int[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(long[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "joinToString(short[], java.lang.CharSequence, java.lang.CharSequence, java.lang.CharSequence, int, java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "lastOrNull(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrNull(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxOrThrow(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(boolean[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(byte[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(char[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(double[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(float[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(int[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(long[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrNull(short[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(boolean[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(byte[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(char[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(double[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(float[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(int[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(long[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "maxWithOrThrow(short[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrNull(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minOrThrow(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(boolean[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(byte[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(char[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(double[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(float[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(int[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(long[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrNull(short[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(boolean[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(byte[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(char[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(double[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(float[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(int[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(long[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "minWithOrThrow(short[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "none(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(java.lang.Object[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(boolean[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(byte[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(char[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(double[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(float[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(int[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(long[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "random(short[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(java.lang.Object[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(boolean[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(byte[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(char[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(double[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(float[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(int[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(long[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "randomOrNull(short[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "requireNoNulls(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(java.lang.Object[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(boolean[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(byte[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(char[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(double[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(float[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(int[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(long[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reverse(short[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversed(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "reversedArray(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(java.lang.Object[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(boolean[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(byte[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(char[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(double[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(float[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(int[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(long[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shuffle(short[], kotlin.random.Random)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "singleOrNull(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(java.lang.Object[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(java.lang.Object[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(boolean[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(boolean[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(byte[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(byte[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(char[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(char[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(double[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(double[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(float[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(float[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(int[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(int[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(long[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(long[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(short[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "slice(short[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(java.lang.Object[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(java.lang.Object[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(boolean[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(boolean[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(byte[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(byte[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(char[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(char[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(double[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(double[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(float[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(float[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(int[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(int[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(long[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(long[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(short[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sliceArray(short[], kotlin.ranges.IntRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(java.lang.Comparable[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(java.lang.Object[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(byte[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(char[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(double[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(float[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(int[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(long[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sort(short[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(java.lang.Comparable[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(byte[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(char[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(double[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(float[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(int[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(long[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortDescending(short[], int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortWith(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortWith(java.lang.Object[], java.util.Comparator, int, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sorted(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArray(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayDescending(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedArrayWith(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedDescending(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(boolean[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(byte[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(char[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(double[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(float[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(int[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(long[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sortedWith(short[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(java.lang.Object[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(boolean[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(byte[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(char[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(double[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(float[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(int[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(long[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "subtract(short[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sum(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfByte(java.lang.Byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfDouble(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfFloat(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfInt(java.lang.Integer[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfLong(java.lang.Long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "sumOfShort(java.lang.Short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(java.lang.Object[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(boolean[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(byte[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(char[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(double[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(float[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(int[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(long[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "take(short[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(java.lang.Object[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(boolean[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(byte[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(char[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(double[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(float[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(int[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(long[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "takeLast(short[], int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toBooleanArray(java.lang.Boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toByteArray(java.lang.Byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCharArray(java.lang.Character[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(java.lang.Object[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(boolean[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(byte[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(char[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(double[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(float[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(int[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(long[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toCollection(short[], java.util.Collection)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toDoubleArray(java.lang.Double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toFloatArray(java.lang.Float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toHashSet(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toIntArray(java.lang.Integer[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toLongArray(java.lang.Long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toMutableSet(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSet(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toShortArray(java.lang.Short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(java.lang.Comparable[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(java.lang.Object[], java.util.Comparator)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toSortedSet(short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(java.lang.Object[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(boolean[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(byte[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(char[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(double[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(float[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(int[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(long[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "union(short[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "unzip(kotlin.Pair[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(java.lang.Object[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(java.lang.Object[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(boolean[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(boolean[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(boolean[], boolean[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(byte[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(byte[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(byte[], byte[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(char[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(char[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(char[], char[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(double[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(double[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(double[], double[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(float[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(float[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(float[], float[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(int[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(int[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(int[], int[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(long[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(long[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(long[], long[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(short[], java.lang.Iterable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(short[], java.lang.Object[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "zip(short[], short[])", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals JBMC nondet-stubs); loud-if-reached")
public final class ArraysKt {

    private ArraysKt() {
    }

    // ============================================================================================
    // copyInto: ArraysKt.copyInto:(<E>[<E>[III)<E>[ for each element type E. Kotlin's
    //   fun <T> Array<out T>.copyInto(destination, destinationOffset=0, startIndex=0, endIndex=size)
    // copies this[startIndex until endIndex] into destination starting at destinationOffset and RETURNS
    // destination. Sound model: an explicit element-copy loop. (The real facade nondet-stubs these —
    // the array-copy inside persistent-collection trie nodes — because the method can't be linked out of
    // the giant multifile facade.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyInto(T[] source, T[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyInto(byte[] source, byte[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyInto(short[] source, short[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyInto(int[] source, int[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyInto(long[] source, long[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyInto(float[] source, float[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyInto(double[] source, double[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyInto(boolean[] source, boolean[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyInto(char[] source, char[] destination, int destinationOffset, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        // Explicit element-copy loop rather than System.arraycopy: JBMC 6.9.0 models per-element array
        // writes precisely but treats the System.arraycopy intrinsic as a weak/havocking copy (a read of
        // the destination after an arraycopy is not constrained), so a loop is the sound bounded copy.
        for (int i = 0; i < n; i++) {
            destination[destinationOffset + i] = source[startIndex + i];
        }
        return destination;
    }

    // ============================================================================================
    // copyInto$default: the kotlinc-synthesized default-arguments bridge. For a call site that omits
    // destinationOffset/startIndex/endIndex, kotlinc emits
    //   copyInto$default(source, destination, destOffset, startIndex, endIndex, mask, marker)
    // where bit i of `mask` set means "argument i was defaulted". The defaults are destOffset=0,
    // startIndex=0, endIndex=source.size. These bridges are SYNTHETIC on the real facade (so the
    // per-member audit gate does not enumerate them), but JBMC links them when a Kotlin call site omits
    // arguments — exactly the persistent-collection copyInto path — so they MUST carry a sound body here
    // or the omit-args call havocs. They are not part of the audited real-member surface, so they carry
    // no @BmcModelConforms (the gate's implemented-but-unannotated check only fires for methods that
    // mirror a real, non-synthetic member).
    // ============================================================================================

    public static <T> T[] copyInto$default(T[] source, T[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static byte[] copyInto$default(byte[] source, byte[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static short[] copyInto$default(short[] source, short[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static int[] copyInto$default(int[] source, int[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static long[] copyInto$default(long[] source, long[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static float[] copyInto$default(float[] source, float[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static double[] copyInto$default(double[] source, double[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static boolean[] copyInto$default(boolean[] source, boolean[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    public static char[] copyInto$default(char[] source, char[] destination, int destinationOffset,
            int startIndex, int endIndex, int mask, Object marker) {
        // kotlinc's $default mask numbers the VALUE parameters of copyInto(destination, destinationOffset,
        // startIndex, endIndex): destination=bit0, destinationOffset=bit1, startIndex=bit2, endIndex=bit3.
        // Bit set => that argument was defaulted (destinationOffset=0, startIndex=0, endIndex=size).
        if ((mask & 2) != 0) {
            destinationOffset = 0;
        }
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            endIndex = source.length;
        }
        return copyInto(source, destination, destinationOffset, startIndex, endIndex);
    }

    // ============================================================================================
    // copyOf(array): ArraysKt.copyOf:(<E>[)<E>[ — a NEW array of the same length, element-wise copy.
    // Kotlin's `array.copyOf()`. Backed by java.util.Arrays.copyOf, sound under JBMC over the bounded
    // array. (Object[] form preserves the runtime component type via Arrays.copyOf.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOf(T[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOf(byte[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOf(short[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOf(int[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOf(long[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOf(float[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOf(double[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOf(boolean[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOf(char[] source) {
        return java.util.Arrays.copyOf(source, source.length);
    }

    // copyOf(array, newSize): ArraysKt.copyOf:(<E>[I)<E>[ — a NEW array of newSize, truncated or
    // zero/null-padded. Kotlin's `array.copyOf(newSize)`. Arrays.copyOf has exactly this contract.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOf(T[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOf(byte[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOf(short[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOf(int[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOf(long[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOf(float[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOf(double[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOf(boolean[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOf(char[] source, int newSize) {
        return java.util.Arrays.copyOf(source, newSize);
    }

    // ============================================================================================
    // copyOfRange(array, fromIndex, toIndex): ArraysKt.copyOfRange:(<E>[II)<E>[ — a NEW array of the
    // half-open [fromIndex, toIndex) slice. Kotlin's `array.copyOfRange(from, to)`. Arrays.copyOfRange
    // has exactly this contract (toIndex may exceed length, zero/null-padding the tail).
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] copyOfRange(T[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] copyOfRange(byte[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] copyOfRange(short[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] copyOfRange(int[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] copyOfRange(long[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] copyOfRange(float[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] copyOfRange(double[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] copyOfRange(boolean[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] copyOfRange(char[] source, int fromIndex, int toIndex) {
        return java.util.Arrays.copyOfRange(source, fromIndex, toIndex);
    }

    // ============================================================================================
    // fill(array, element, fromIndex, toIndex): ArraysKt.fill:(<E>[<E>II)V — fill [fromIndex, toIndex)
    // with element, IN PLACE (returns void). Kotlin's `array.fill(value, from, to)`. java.util.Arrays.fill
    // has exactly this half-open contract.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> void fill(T[] array, T element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(byte[] array, byte element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(short[] array, short element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(int[] array, int element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(long[] array, long element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(float[] array, float element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(double[] array, double element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(boolean[] array, boolean element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void fill(char[] array, char element, int fromIndex, int toIndex) {
        java.util.Arrays.fill(array, fromIndex, toIndex, element);
    }

    // ============================================================================================
    // asList: ArraysKt.asList:(<E>[)Ljava/util/List; — the array → List<E> conversion. Kotlin's
    //   fun <T> Array<out T>.asList(): List<T>
    // The headline path this unblocks: kotlinx persistent-collection vararg factories
    // (persistentListOf(a) → ArraysKt.asList(elements) → addAll(thatList)). The consumer's addAll
    // ITERATES the returned list, so the result MUST be a single CONCRETE java.util.ArrayList copy
    // (an explicit element-loop into a fresh ArrayList) rather than Arrays.asList / a custom view: a
    // lone concrete ArrayList iterator devirtualizes cleanly under JBMC (the concrete-backing rule),
    // whereas an exotic/view return reintroduces the iterator-dispatch fragility this whole model
    // exists to avoid. The real facade nondet-stubs asList (unlinkable from the giant multifile facade),
    // havocking every persistent-collection vararg-factory proof to UNKNOWN.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> asList(T[] source) {
        ArrayList<T> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> asList(byte[] source) {
        ArrayList<Byte> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> asList(short[] source) {
        ArrayList<Short> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> asList(int[] source) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> asList(long[] source) {
        ArrayList<Long> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> asList(float[] source) {
        ArrayList<Float> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> asList(double[] source) {
        ArrayList<Double> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> asList(boolean[] source) {
        ArrayList<Boolean> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> asList(char[] source) {
        ArrayList<Character> out = new ArrayList<>();
        for (int i = 0; i < source.length; i++) {
            out.add(source[i]);
        }
        return out;
    }

    // ============================================================================================
    // toList / toMutableList: ArraysKt.toList:(<E>[)Ljava/util/List; — a NEW List<E> copy of the array
    // (toList is documented read-only, toMutableList mutable, but bmc4j's single concrete ArrayList model
    // backs both: a copy either way). Same concrete-ArrayList rationale as asList.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toList(T[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> toList(byte[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> toList(short[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> toList(int[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> toList(long[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> toList(float[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> toList(double[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> toList(boolean[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toList(char[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> List<T> toMutableList(T[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Byte> toMutableList(byte[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Short> toMutableList(short[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Integer> toMutableList(int[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Long> toMutableList(long[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Float> toMutableList(float[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Double> toMutableList(double[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Boolean> toMutableList(boolean[] source) {
        return asList(source);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toMutableList(char[] source) {
        return asList(source);
    }

    // ============================================================================================
    // toTypedArray: ArraysKt.toTypedArray:(<prim>[)[Ljava/lang/<Boxed>; — box each primitive element
    // into a NEW boxed array. Kotlin's `intArrayOf(1,2).toTypedArray(): Array<Int>`. (No Object[] form:
    // an Array<T> has nothing to box. The Collection form stays in the tail.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte[] toTypedArray(byte[] source) {
        Byte[] out = new Byte[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short[] toTypedArray(short[] source) {
        Short[] out = new Short[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer[] toTypedArray(int[] source) {
        Integer[] out = new Integer[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long[] toTypedArray(long[] source) {
        Long[] out = new Long[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Float[] toTypedArray(float[] source) {
        Float[] out = new Float[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Double[] toTypedArray(double[] source) {
        Double[] out = new Double[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Boolean[] toTypedArray(boolean[] source) {
        Boolean[] out = new Boolean[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character[] toTypedArray(char[] source) {
        Character[] out = new Character[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i];
        }
        return out;
    }

    // ============================================================================================
    // plus(array, element): ArraysKt.plus:(<E>[<E>)<E>[ — a NEW array one longer, with element appended.
    // Kotlin's `array + element`. Backed by Arrays.copyOf (preserves the runtime component type for the
    // Object[] form) + a single tail write, sound under JBMC over the bounded array.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, T element) {
        T[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, byte element) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, short element) {
        short[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, int element) {
        int[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, long element) {
        long[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, float element) {
        float[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, double element) {
        double[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, boolean element) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, char element) {
        char[] out = java.util.Arrays.copyOf(source, source.length + 1);
        out[source.length] = element;
        return out;
    }

    // plus(array, array): ArraysKt.plus:(<E>[<E>[)<E>[ — a NEW array = source concatenated with the other.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, T[] elements) {
        T[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, byte[] elements) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, short[] elements) {
        short[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, int[] elements) {
        int[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, long[] elements) {
        long[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, float[] elements) {
        float[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, double[] elements) {
        double[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, boolean[] elements) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, char[] elements) {
        char[] out = java.util.Arrays.copyOf(source, source.length + elements.length);
        for (int i = 0; i < elements.length; i++) {
            out[source.length + i] = elements[i];
        }
        return out;
    }

    // plus(array, Collection): ArraysKt.plus:(<E>[Ljava/util/Collection;)<E>[ — a NEW array = source with
    // the collection's elements appended (iteration order). Object[] form preserves the runtime component
    // type via Arrays.copyOf; the primitive forms append each unboxed element.

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T[] plus(T[] source, Collection<? extends T> elements) {
        T[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (T e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte[] plus(byte[] source, Collection<Byte> elements) {
        byte[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Byte e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short[] plus(short[] source, Collection<Short> elements) {
        short[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Short e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int[] plus(int[] source, Collection<Integer> elements) {
        int[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Integer e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long[] plus(long[] source, Collection<Long> elements) {
        long[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Long e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float[] plus(float[] source, Collection<Float> elements) {
        float[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Float e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double[] plus(double[] source, Collection<Double> elements) {
        double[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Double e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean[] plus(boolean[] source, Collection<Boolean> elements) {
        boolean[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Boolean e : elements) {
            out[i++] = e;
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] plus(char[] source, Collection<Character> elements) {
        char[] out = java.util.Arrays.copyOf(source, source.length + elements.size());
        int i = source.length;
        for (Character e : elements) {
            out[i++] = e;
        }
        return out;
    }

    // ============================================================================================
    // contains(array, element): ArraysKt.contains:(<E>[<E>)Z — linear membership test. Object[] uses
    // .equals (via indexOf); primitives use ==. (NB the float[]/double[] overloads are @Deprecated(HIDDEN)
    // synthetic on the real facade — off the audited surface — and IEEE-equality unsound, so they stay in
    // the tail, like the rest of the FP-equality residue.)
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> boolean contains(T[] source, T element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(byte[] source, byte element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(short[] source, short element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(int[] source, int element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(long[] source, long element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(boolean[] source, boolean element) {
        return indexOf(source, element) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(char[] source, char element) {
        return indexOf(source, element) >= 0;
    }

    // ============================================================================================
    // indexOf(array, element): ArraysKt.indexOf:(<E>[<E>)I — first index of element, or -1. Object[]
    // compares with .equals (null-safe: a null element matches the first null slot); primitives with ==.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int indexOf(T[] source, T element) {
        if (element == null) {
            for (int i = 0; i < source.length; i++) {
                if (source[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < source.length; i++) {
                if (element.equals(source[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(byte[] source, byte element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(short[] source, short element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(int[] source, int element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(long[] source, long element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(boolean[] source, boolean element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(char[] source, char element) {
        for (int i = 0; i < source.length; i++) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================================================
    // lastIndexOf(array, element): ArraysKt.lastIndexOf:(<E>[<E>)I — last index of element, or -1. Same
    // equality discipline as indexOf, scanning from the high end.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> int lastIndexOf(T[] source, T element) {
        if (element == null) {
            for (int i = source.length - 1; i >= 0; i--) {
                if (source[i] == null) {
                    return i;
                }
            }
        } else {
            for (int i = source.length - 1; i >= 0; i--) {
                if (element.equals(source[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(byte[] source, byte element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(short[] source, short element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(int[] source, int element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(long[] source, long element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(boolean[] source, boolean element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(char[] source, char element) {
        for (int i = source.length - 1; i >= 0; i--) {
            if (source[i] == element) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================================================
    // first(array): ArraysKt.first:(<E>[)<E> — element 0, or NoSuchElementException if empty (Kotlin's
    // contract). The primitive forms return the unboxed element.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T first(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte first(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short first(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int first(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long first(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float first(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double first(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean first(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char first(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[0];
    }

    // ============================================================================================
    // last(array): ArraysKt.last:(<E>[)<E> — the final element, or NoSuchElementException if empty.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T last(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte last(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short last(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int last(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long last(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float last(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double last(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean last(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char last(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        return source[source.length - 1];
    }

    // ============================================================================================
    // single(array): ArraysKt.single:(<E>[)<E> — the sole element; NoSuchElementException if empty,
    // IllegalArgumentException if more than one (Kotlin's contract).
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T single(T[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static byte single(byte[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static short single(short[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int single(int[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long single(long[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static float single(float[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static double single(double[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean single(boolean[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char single(char[] source) {
        if (source.length == 0) {
            throw new NoSuchElementException("Array is empty.");
        }
        if (source.length != 1) {
            throw new IllegalArgumentException("Array has more than one element.");
        }
        return source[0];
    }

    // ============================================================================================
    // getOrNull(array, index): ArraysKt.getOrNull:(<E>[I)<E-or-Boxed> — the element at index, or null if
    // out of bounds. Bounds-safe (no throw). The primitive forms return the BOXED type (Kotlin returns
    // the nullable T?, so an Int? = java.lang.Integer), null when out of range.
    // ============================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T> T getOrNull(T[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte getOrNull(byte[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short getOrNull(short[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer getOrNull(int[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long getOrNull(long[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Float getOrNull(float[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Double getOrNull(double[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Boolean getOrNull(boolean[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character getOrNull(char[] source, int index) {
        if (index >= 0 && index < source.length) {
            return source[index];
        }
        return null;
    }
}
