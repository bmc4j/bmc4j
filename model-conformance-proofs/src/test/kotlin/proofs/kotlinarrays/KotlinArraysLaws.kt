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
}
