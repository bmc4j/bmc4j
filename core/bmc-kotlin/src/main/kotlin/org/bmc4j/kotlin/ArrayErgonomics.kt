package org.bmc4j.kotlin

import org.bmc4j.Bmc

/**
 * Idiomatic Kotlin wrappers over the symbolic-array helpers on [Bmc]. Kotlin's `IntArray` / `LongArray`
 * ARE the JVM `int[]` / `long[]`, so these delegate straight through to [Bmc] (single source of truth)
 * — same concrete-length rule, same unwind-budget cost as the Java methods they call.
 *
 * ```
 * val a = anyIntArray(4, -3..3)   // symbolic int[4], each element in -3..3
 * a.assumeSorted()                // a[i-1] <= a[i]
 * Bmc.check(a[0] <= a[3])         // holds for every such array
 * ```
 */

/**
 * A symbolic [IntArray] of exactly [length] elements, each an unconstrained `anyInt()`.
 * Delegates to [Bmc.anyArrayOfInts]. [length] must be a concrete literal per proof.
 */
fun anyIntArray(length: Int): IntArray = Bmc.anyArrayOfInts(length)

/**
 * A symbolic [IntArray] of exactly [length] elements, each constrained to [range].
 * Delegates to [Bmc.anyArrayOfInts] with `range.first`/`range.last` — the ranged form to prefer
 * (bounded elements keep the proof tractable). [length] must be a concrete literal per proof.
 */
fun anyIntArray(length: Int, range: IntRange): IntArray =
    Bmc.anyArrayOfInts(length, range.first, range.last)

/**
 * A symbolic [LongArray] of exactly [length] elements, each an unconstrained `anyLong()`.
 * Delegates to [Bmc.anyArrayOfLongs]. [length] must be a concrete literal per proof.
 */
fun anyLongArray(length: Int): LongArray = Bmc.anyArrayOfLongs(length)

/**
 * A symbolic [LongArray] of exactly [length] elements, each constrained to [range].
 * Delegates to [Bmc.anyArrayOfLongs] with `range.first`/`range.last`. [length] must be a concrete
 * literal per proof.
 */
fun anyLongArray(length: Int, range: LongRange): LongArray =
    Bmc.anyArrayOfLongs(length, range.first, range.last)

/**
 * Assume this array is sorted non-strictly ascending (`a[i-1] <= a[i]`, duplicates allowed).
 * Delegates to [Bmc.assumeSorted]. A vacuous no-op for length 0/1; the pairwise loop costs unwind
 * budget.
 */
fun IntArray.assumeSorted(): Unit = Bmc.assumeSorted(this)

/**
 * Assume this array is sorted strictly ascending (`a[i-1] < a[i]`, all elements distinct).
 * Delegates to [Bmc.assumeStrictlySorted]. Prefer [assumeSorted] unless you need distinct keys —
 * the `<` vs `<=` choice is load-bearing.
 */
fun IntArray.assumeStrictlySorted(): Unit = Bmc.assumeStrictlySorted(this)

/**
 * Assume this array is sorted non-strictly ascending (`a[i-1] <= a[i]`, duplicates allowed).
 * Delegates to [Bmc.assumeSorted]. A vacuous no-op for length 0/1.
 */
fun LongArray.assumeSorted(): Unit = Bmc.assumeSorted(this)

/**
 * Assume this array is sorted strictly ascending (`a[i-1] < a[i]`, all elements distinct).
 * Delegates to [Bmc.assumeStrictlySorted]. Prefer [assumeSorted] unless you need distinct keys.
 */
fun LongArray.assumeStrictlySorted(): Unit = Bmc.assumeStrictlySorted(this)
