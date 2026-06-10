package proofs.kotlinarrays

import org.bmc4j.Bmc
import org.bmc4j.BmcProof

/**
 * Model proofs (axis 2) for the `kotlin.collections.ArraysKt` copy/fill model — the facade whose real
 * multifile body JBMC can't link a method out of, so `copyInto`/`copyInto$default` (the array-copy
 * inside kotlinx persistent-collection trie nodes), `copyOf`, `copyOfRange`, and `fill` nondet-stubbed
 * until this model. These laws pin the modeled surface symbolically over bounded arrays: a wrong (or
 * nondet) body is caught by the concrete element/length assertions.
 */
class KotlinArraysLaws {

    /** copyInto with explicit offsets: dst receives src[startIndex until endIndex] at destinationOffset. */
    @BmcProof
    fun copyInto_explicit_range() {
        val src = intArrayOf(10, 20, 30, 40)
        val dst = IntArray(4)
        src.copyInto(dst, destinationOffset = 1, startIndex = 1, endIndex = 3)
        Bmc.check(dst[0] == 0 && dst[1] == 20 && dst[2] == 30 && dst[3] == 0)
    }

    /** copyInto with defaulted args (the $default bridge): whole src copied to the start of dst. */
    @BmcProof
    fun copyInto_defaults_whole_array() {
        val src = intArrayOf(1, 2, 3)
        val dst = IntArray(3)
        src.copyInto(dst)
        Bmc.check(dst[0] == 1 && dst[1] == 2 && dst[2] == 3)
    }

    /** copyInto over an Object[] (the reference-element form persistent collections actually hit). */
    @BmcProof
    fun copyInto_object_array_symbolic_element() {
        val x = Bmc.anyInt()
        val src = arrayOf(x, x + 1)
        val dst = arrayOfNulls<Int>(2)
        src.copyInto(dst)
        Bmc.check(dst[0] == x && dst[1] == x + 1)
    }

    /** copyOf returns an independent copy of the same length and contents. */
    @BmcProof
    fun copyOf_same_contents() {
        val src = intArrayOf(5, 6, 7)
        val c = src.copyOf()
        Bmc.check(c.size == 3 && c[0] == 5 && c[1] == 6 && c[2] == 7)
    }

    /** copyOf(newSize) truncates / zero-pads. */
    @BmcProof
    fun copyOf_resize_pads_with_zero() {
        val src = intArrayOf(5, 6)
        val c = src.copyOf(4)
        Bmc.check(c.size == 4 && c[0] == 5 && c[1] == 6 && c[2] == 0 && c[3] == 0)
    }

    /** copyOfRange returns the half-open [from, to) slice. */
    @BmcProof
    fun copyOfRange_half_open_slice() {
        val src = intArrayOf(1, 2, 3, 4, 5)
        val c = src.copyOfRange(1, 4)
        Bmc.check(c.size == 3 && c[0] == 2 && c[1] == 3 && c[2] == 4)
    }

    /** fill writes the value across the half-open [from, to) range, leaving the rest untouched. */
    @BmcProof
    fun fill_range_in_place() {
        val a = IntArray(4)
        a.fill(9, 1, 3)
        Bmc.check(a[0] == 0 && a[1] == 9 && a[2] == 9 && a[3] == 0)
    }

    // ---- read / convert / transform surface --------------------------------------------------------

    /** asList returns a concrete list copy with the same length and contents (the vararg-factory path). */
    @BmcProof
    fun asList_concrete_copy_contents() {
        val a = Bmc.anyInt()
        val src = intArrayOf(a, a + 1, a + 2)
        val l = src.asList()
        Bmc.check(l.size == 3 && l[0] == a && l[1] == a + 1 && l[2] == a + 2)
    }

    /** asList over an Object[] (the reference-element form the persistent-collection factory hits). */
    @BmcProof
    fun asList_object_array_copy() {
        val x = Bmc.anyInt()
        val src = arrayOf(x, x + 5)
        val l = src.asList()
        Bmc.check(l.size == 2 && l[0] == x && l[1] == x + 5)
    }

    /** toList / toMutableList likewise produce a same-contents copy. */
    @BmcProof
    fun toList_and_toMutableList_copy_contents() {
        val src = intArrayOf(7, 8)
        val a = src.toList()
        val b = src.toMutableList()
        Bmc.check(a.size == 2 && a[0] == 7 && a[1] == 8 && b.size == 2 && b[0] == 7 && b[1] == 8)
    }

    /** toTypedArray boxes each primitive element into a same-length Array<Int>. */
    @BmcProof
    fun toTypedArray_boxes_elements() {
        val src = intArrayOf(3, 4, 5)
        val boxed = src.toTypedArray()
        Bmc.check(boxed.size == 3 && boxed[0] == 3 && boxed[1] == 4 && boxed[2] == 5)
    }

    /** plus(element) appends one element to a new, one-longer array. */
    @BmcProof
    fun plus_element_appends() {
        val src = intArrayOf(1, 2)
        val out = src + 9
        Bmc.check(out.size == 3 && out[0] == 1 && out[1] == 2 && out[2] == 9)
    }

    /** plus(array) concatenates two arrays. */
    @BmcProof
    fun plus_array_concatenates() {
        val a = intArrayOf(1, 2)
        val b = intArrayOf(3, 4, 5)
        val out = a + b
        Bmc.check(out.size == 5 && out[0] == 1 && out[2] == 3 && out[4] == 5)
    }

    /** plus(element) over an Object[] preserves element references (the reference-element form the
     *  kotlinx vararg factory hits). Identity (===) avoids the FP/String content-equality machinery. */
    @BmcProof
    fun plus_object_element_appends() {
        val p = Any()
        val q = Any()
        val src = arrayOf(p)
        val out = src + q
        Bmc.check(out.size == 2 && out[0] === p && out[1] === q)
    }

    /** contains is a sound linear membership test (true for a present element, false for an absent one). */
    @BmcProof
    fun contains_present_and_absent() {
        val src = intArrayOf(10, 20, 30)
        Bmc.check(src.contains(20) && !src.contains(25))
    }

    /** indexOf returns the first matching index, or -1 when absent. */
    @BmcProof
    fun indexOf_first_match_or_minus_one() {
        val src = intArrayOf(5, 6, 5, 7)
        Bmc.check(src.indexOf(5) == 0 && src.indexOf(7) == 3 && src.indexOf(99) == -1)
    }

    /** lastIndexOf returns the highest matching index, or -1 when absent. */
    @BmcProof
    fun lastIndexOf_last_match_or_minus_one() {
        val src = intArrayOf(5, 6, 5, 7)
        Bmc.check(src.lastIndexOf(5) == 2 && src.lastIndexOf(99) == -1)
    }

    /** indexOf over an Object[] uses equals (boxed Integer elements: Integer.equals, modeled soundly —
     *  not the String/CProverString path). A present value matches at its index, an absent one gives -1. */
    @BmcProof
    fun indexOf_object_array_uses_equals() {
        val src = arrayOf(10, 20, 30)
        Bmc.check(src.indexOf(20) == 1 && src.indexOf(99) == -1)
    }

    /** first / last read the endpoints of a non-empty array. */
    @BmcProof
    fun first_and_last_endpoints() {
        val a = Bmc.anyInt()
        val src = intArrayOf(a, a + 1, a + 2)
        Bmc.check(src.first() == a && src.last() == a + 2)
    }

    /** getOrNull yields the element in bounds and null out of bounds. */
    @BmcProof
    fun getOrNull_in_and_out_of_bounds() {
        val src = intArrayOf(11, 12)
        Bmc.check(src.getOrNull(0) == 11 && src.getOrNull(1) == 12 &&
            src.getOrNull(2) == null && src.getOrNull(-1) == null)
    }

    // ---- inline higher-order forms: the lambda body inlines INTO this caller and analyzes over the
    //      bounded array; the ArraysKt facade JVM method (now an enumerated @BmcUnmodelable, inline)
    //      is never invoked, so these are NOT a forced loud UNKNOWN. -------------------------------------

    /** map { } over a bounded IntArray: the inlined transform builds the result list element-by-element. */
    @BmcProof
    fun map_inline_over_bounded_array() {
        val src = intArrayOf(1, 2, 3)
        val out = src.map { it + 1 }
        Bmc.check(out.size == 3 && out[0] == 2 && out[1] == 3 && out[2] == 4)
    }

    /** all { } / any { } over a bounded IntArray: inlined predicate folds soundly. */
    @BmcProof
    fun all_any_inline_over_bounded_array() {
        val src = intArrayOf(2, 4, 6)
        Bmc.check(src.all { it > 0 } && src.any { it == 4 } && !src.all { it > 4 })
    }

    /** filter { } over a bounded IntArray keeps the matching elements. */
    @BmcProof
    fun filter_inline_over_bounded_array() {
        val src = intArrayOf(1, 2, 3, 4)
        val out = src.filter { it % 2 == 0 }
        Bmc.check(out.size == 2 && out[0] == 2 && out[1] == 4)
    }

    /** fold { } over a bounded IntArray accumulates the inlined combiner. */
    @BmcProof
    fun fold_inline_over_bounded_array() {
        val src = intArrayOf(1, 2, 3, 4)
        Bmc.check(src.fold(0) { acc, x -> acc + x } == 10)
    }
}
