package kotlin.ranges;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Clean model of the {@code kotlin.ranges.RangesKt} facade members bmc4j needs. Kotlin's INLINE
 * {@code associate}/{@code associateBy}/{@code associateWith} emit
 * {@code RangesKt.coerceAtLeast(mapCapacity(size), 16)} to compute their {@code LinkedHashMap}
 * initial capacity. The real facade reaches kotlin-stdlib internals JBMC stubs to nondet, which then
 * poisons the {@code LinkedHashMap(int)} ctor; this models the trivial {@code max(a, b)} semantics so
 * the (capacity-ignoring) bounded map model is sized soundly.
 *
 * <p>{@code coerceAtMost}/{@code coerceIn} are modeled for the same reason consumers reach them
 * directly: this class REPLACES the stdlib facade on the analysis path, so any member it lacks is a
 * JBMC nondet stub — a call to un-modeled {@code coerceIn} produced a spurious counterexample
 * ({@code coerceIn(0, 0, 95) == 96}) the moment an example used it. The {@code Int}/{@code Long}
 * {@code range.random(rng)} draws are MODELED (sound nondet-in-range, delegating to the modeled
 * {@code Random.nextInt/nextLong} with the stdlib overflow handling). The nondeterministic random draws
 * that earn nothing over the modeled Int/Long path (the {@code Char} random draw, every
 * {@code randomOrNull}) stay loud {@link BmcUnmodelable} method stubs (true RNG walls).
 *
 * <p>The remaining facade surface is split per-member by an empirical probe of the real bytecode:
 * the integer progression/coerce members ({@code until}/{@code downTo}/{@code step}/{@code rangeTo}/
 * {@code reversed} over Int/Long/Char progressions, and the non-FP scalar {@code coerceAtLeast}/
 * {@code coerceAtMost}/{@code coerceIn}) are class-level {@code @BmcNotNeeded} — green-if-reached, their
 * real bytecode analyzes soundly so JBMC falls through to the real facade. The {@code *RangeContains}
 * cross-type membership family, the range-object-form {@code coerceIn(_, ClosedRange…)}, every FP
 * (double/float) overload, the {@code rangeUntil(Comparable)} open-end form, the {@code to*ExactOrNull}
 * internal helpers, and {@code checkStepIsPositive} are class-level {@code @BmcUnmodelable} loud walls —
 * each was probed and does NOT analyze soundly when reached (it routes through unmodeled range-object /
 * FP / internal kotlin-stdlib machinery JBMC nondet-stubs). The whole real facade surface is thus
 * per-member accounted and no {@code @BmcModelTail} catch-all is needed.
 */
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.ClosedRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.ClosedRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.ClosedRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.OpenEndRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.OpenEndRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "byteRangeContains(kotlin.ranges.OpenEndRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "checkStepIsPositive(boolean, java.lang.Number)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "coerceAtLeast(byte, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "coerceAtLeast(double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceAtLeast(float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "coerceAtLeast(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "coerceAtLeast(short, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "coerceAtMost(byte, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "coerceAtMost(double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceAtMost(float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "coerceAtMost(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "coerceAtMost(short, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "coerceIn(byte, byte, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "coerceIn(double, double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceIn(float, float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceIn(int, kotlin.ranges.ClosedRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "coerceIn(java.lang.Comparable, java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "coerceIn(java.lang.Comparable, kotlin.ranges.ClosedFloatingPointRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceIn(java.lang.Comparable, kotlin.ranges.ClosedRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "coerceIn(long, kotlin.ranges.ClosedRange)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "coerceIn(short, short, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "doubleRangeContains(kotlin.ranges.ClosedRange, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "doubleRangeContains(kotlin.ranges.OpenEndRange, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "downTo(byte, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(byte, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(byte, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(byte, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(char, char)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(int, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(int, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(int, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(int, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(long, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(long, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(long, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(long, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(short, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(short, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(short, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "downTo(short, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "floatRangeContains(kotlin.ranges.ClosedRange, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.ClosedRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.ClosedRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.ClosedRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.OpenEndRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.OpenEndRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "intRangeContains(kotlin.ranges.OpenEndRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.ClosedRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.ClosedRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.ClosedRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.OpenEndRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.OpenEndRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "longRangeContains(kotlin.ranges.OpenEndRange, short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "rangeTo(double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "rangeTo(float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "rangeTo(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "rangeUntil(double, double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "rangeUntil(float, float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "rangeUntil(java.lang.Comparable, java.lang.Comparable)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "reversed(kotlin.ranges.CharProgression)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "reversed(kotlin.ranges.IntProgression)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "reversed(kotlin.ranges.LongProgression)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.ClosedRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.ClosedRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.ClosedRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.OpenEndRange, byte)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.OpenEndRange, int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "shortRangeContains(kotlin.ranges.OpenEndRange, long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "step(kotlin.ranges.CharProgression, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "step(kotlin.ranges.IntProgression, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "step(kotlin.ranges.LongProgression, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcUnmodelable(member = "toByteExactOrNull(double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toByteExactOrNull(float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toByteExactOrNull(int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toByteExactOrNull(long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toByteExactOrNull(short)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toIntExactOrNull(double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toIntExactOrNull(float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toIntExactOrNull(long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toLongExactOrNull(double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toLongExactOrNull(float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toShortExactOrNull(double)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toShortExactOrNull(float)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toShortExactOrNull(int)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcUnmodelable(member = "toShortExactOrNull(long)", reason = "real stdlib bytecode does NOT analyze soundly when reached (probed REFUTED/UNKNOWN through the real facade — routes through unmodeled kotlin-stdlib internals / FP / reflection that JBMC nondet-stubs); loud-if-reached")
@BmcNotNeeded(member = "until(byte, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(byte, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(byte, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(byte, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(char, char)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(int, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(int, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(int, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(int, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(long, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(long, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(long, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(long, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(short, byte)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(short, int)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(short, long)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
@BmcNotNeeded(member = "until(short, short)", reason = "real stdlib bytecode analyzes soundly under JBMC over the modeled surface; no facade model needed (green-if-reached: JBMC falls through to the real facade)")
public final class RangesKt {

    private RangesKt() {
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceAtLeast(int value, int minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceAtLeast(long value, long minimumValue) {
        return value < minimumValue ? minimumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceAtMost(int value, int maximumValue) {
        return value > maximumValue ? maximumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceAtMost(long value, long maximumValue) {
        return value > maximumValue ? maximumValue : value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int coerceIn(int value, int minimumValue, int maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum "
                    + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) {
            return minimumValue;
        }
        if (value > maximumValue) {
            return maximumValue;
        }
        return value;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long coerceIn(long value, long minimumValue, long maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum "
                    + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) {
            return minimumValue;
        }
        if (value > maximumValue) {
            return maximumValue;
        }
        return value;
    }

    // ---- first / last over an Int/Long/Char progression: the Kotlin compiler emits
    //   RangesKt.first:(Lkotlin/ranges/IntProgression;)I   (and Long/Char twins)
    //   RangesKt.last:(Lkotlin/ranges/IntProgression;)I     (and Long/Char twins)
    // Kotlin contract: first() returns the progression's start (getFirst), last() its end (getLast),
    // each throwing NoSuchElementException on an empty progression. The progression itself is unmodeled
    // (real stdlib IntProgression is a tiny int-field class JBMC analyzes), so we read its start/end
    // accessors directly. The real facade routes through internal builders nondet-stubbed — probed
    // REFUTED — so these stay modeled, NOT @BmcUnmodelable. (random/randomOrNull stay tail: a Random draw
    // is nondeterministic by nature — no sound bounded model exists.)
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int first(kotlin.ranges.IntProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long first(kotlin.ranges.LongProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char first(kotlin.ranges.CharProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int last(kotlin.ranges.IntProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getLast();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long last(kotlin.ranges.LongProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getLast();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char last(kotlin.ranges.CharProgression progression) {
        if (progression.isEmpty()) {
            throw new java.util.NoSuchElementException("Progression is empty.");
        }
        return progression.getLast();
    }

    // ---- firstOrNull / lastOrNull over a progression: same as first/last but return a boxed null on
    //   RangesKt.firstOrNull:(Lkotlin/ranges/IntProgression;)Ljava/lang/Integer;  (and Long/Char twins)
    // an empty progression instead of throwing.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer firstOrNull(kotlin.ranges.IntProgression progression) {
        return progression.isEmpty() ? null : progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long firstOrNull(kotlin.ranges.LongProgression progression) {
        return progression.isEmpty() ? null : progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character firstOrNull(kotlin.ranges.CharProgression progression) {
        return progression.isEmpty() ? null : progression.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer lastOrNull(kotlin.ranges.IntProgression progression) {
        return progression.isEmpty() ? null : progression.getLast();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long lastOrNull(kotlin.ranges.LongProgression progression) {
        return progression.isEmpty() ? null : progression.getLast();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character lastOrNull(kotlin.ranges.CharProgression progression) {
        return progression.isEmpty() ? null : progression.getLast();
    }

    // --- random draws: modeled Int/Long, genuinely-loud Char/randomOrNull (true RNG walls) ---
    // (every other facade member is a class-level @BmcNotNeeded declaration above — green-if-reached.)
    // random over an Int/Long range: a SOUND nondet-in-range draw — delegate to the modeled
    // Random.nextInt(from, until) / nextLong(from, until) with the stdlib's exact last==MAX_VALUE overflow
    // handling, throwing NoSuchElementException on an empty range. (This RangesKt.random is the entry point
    // the Kotlin compiler emits for `(a..b).random(rng)`.) The Char overload + every randomOrNull stay loud
    // walls: a char draw / boxed-null draw earns nothing over the modeled Int/Long path.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int random(kotlin.ranges.IntRange range, kotlin.random.Random random) {
        if (range.isEmpty()) {
            throw new java.util.NoSuchElementException("Cannot get random in empty range: " + range);
        }
        int first = range.getFirst();
        int last = range.getLast();
        if (last < Integer.MAX_VALUE) {
            return random.nextInt(first, last + 1);
        }
        if (first > Integer.MIN_VALUE) {
            return random.nextInt(first - 1, last) + 1;
        }
        return random.nextInt();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static long random(kotlin.ranges.LongRange range, kotlin.random.Random random) {
        if (range.isEmpty()) {
            throw new java.util.NoSuchElementException("Cannot get random in empty range: " + range);
        }
        long first = range.getFirst();
        long last = range.getLast();
        if (last < Long.MAX_VALUE) {
            return random.nextLong(first, last + 1);
        }
        if (first > Long.MIN_VALUE) {
            return random.nextLong(first - 1, last) + 1;
        }
        return random.nextLong();
    }

    // random(CharRange,...) + every randomOrNull(...) over a range stay loud (a reach demotes to a
    // member-named UNKNOWN): a char draw / boxed-null-on-empty draw earns nothing over the modeled
    // Int/Long path, and modeling them would only add CharRange/boxing surface the proofs don't exercise.
    @BmcUnmodelable(reason = "ranged Random draw over CharRange — use the modeled Int/Long range draws")
    public static char random(kotlin.ranges.CharRange range, kotlin.random.Random random) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.random(kotlin.ranges.CharRange,"
                + "kotlin.random.Random) — use the modeled Int/Long range.random(rng) draws");
    }

    @BmcUnmodelable(reason = "ranged Random draw over CharRange — use the modeled Int/Long range draws")
    public static Character randomOrNull(kotlin.ranges.CharRange range, kotlin.random.Random random) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.randomOrNull(kotlin.ranges.CharRange,"
                + "kotlin.random.Random) — a Random draw is nondeterministic by nature; no sound bounded model");
    }

    @BmcUnmodelable(reason = "ranged Random draw over IntRange — nondeterministic by nature; no sound bounded model")
    public static Integer randomOrNull(kotlin.ranges.IntRange range, kotlin.random.Random random) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.randomOrNull(kotlin.ranges.IntRange,"
                + "kotlin.random.Random) — a Random draw is nondeterministic by nature; no sound bounded model");
    }

    @BmcUnmodelable(reason = "ranged Random draw over LongRange — nondeterministic by nature; no sound bounded model")
    public static Long randomOrNull(kotlin.ranges.LongRange range, kotlin.random.Random random) {
        throw fail("bmc4j: unmodelled member kotlin.ranges.RangesKt.randomOrNull(kotlin.ranges.LongRange,"
                + "kotlin.random.Random) — a Random draw is nondeterministic by nature; no sound bounded model");
    }

}
