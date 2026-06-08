package proofs.arrays

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.Arrays

/**
 * Model proofs (axis 2): algebraic laws the {@link java.util.Arrays} model must satisfy under JBMC's
 * own semantics. Arrays are kept tiny (length 2-3) with symbolic elements, so a passing proof holds
 * for every value at that shape at once. The sort/binarySearch laws use small fixed lengths to keep
 * the insertion-sort unwind tractable.
 */
class ArraysLaws {

    // --- copyOf ---------------------------------------------------------------------------------

    @BmcProof
    fun copyOf_same_length_preserves_elements() {
        val a = intArrayOf(Bmc.anyInt(), Bmc.anyInt())
        val c = Arrays.copyOf(a, 2)
        Bmc.check(c.size == 2 && c[0] == a[0] && c[1] == a[1])
    }

    @BmcProof
    fun copyOf_grow_zero_pads_the_tail() {
        val a = intArrayOf(Bmc.anyInt())
        val c = Arrays.copyOf(a, 3)
        Bmc.check(c.size == 3 && c[0] == a[0] && c[1] == 0 && c[2] == 0)
    }

    @BmcProof
    fun copyOf_shrink_truncates() {
        val a = intArrayOf(Bmc.anyInt(), Bmc.anyInt(), Bmc.anyInt())
        val c = Arrays.copyOf(a, 1)
        Bmc.check(c.size == 1 && c[0] == a[0])
    }

    @BmcProof
    fun copyOf_long_preserves_elements() {
        val a = longArrayOf(Bmc.anyLong(), Bmc.anyLong())
        val c = Arrays.copyOf(a, 2)
        Bmc.check(c.size == 2 && c[0] == a[0] && c[1] == a[1])
    }

    // --- copyOfRange ----------------------------------------------------------------------------

    @BmcProof
    fun copyOfRange_interior_slice() {
        val a = intArrayOf(Bmc.anyInt(), Bmc.anyInt(), Bmc.anyInt())
        val c = Arrays.copyOfRange(a, 1, 3)
        Bmc.check(c.size == 2 && c[0] == a[1] && c[1] == a[2])
    }

    @BmcProof
    fun copyOfRange_past_end_zero_pads() {
        val a = intArrayOf(Bmc.anyInt(), Bmc.anyInt())
        val c = Arrays.copyOfRange(a, 1, 4)
        Bmc.check(c.size == 3 && c[0] == a[1] && c[1] == 0 && c[2] == 0)
    }

    // --- fill -----------------------------------------------------------------------------------

    @BmcProof
    fun fill_sets_every_element() {
        val a = intArrayOf(0, 0, 0)
        val v = Bmc.anyInt()
        Arrays.fill(a, v)
        Bmc.check(a[0] == v && a[1] == v && a[2] == v)
    }

    @BmcProof
    fun fill_range_sets_only_the_range() {
        val a = intArrayOf(0, 0, 0, 0)
        val v = Bmc.anyInt()
        Bmc.assume(v != 0)
        Arrays.fill(a, 1, 3, v)
        Bmc.check(a[0] == 0 && a[1] == v && a[2] == v && a[3] == 0)
    }

    // --- equals ---------------------------------------------------------------------------------

    @BmcProof
    fun equals_is_reflexive() {
        val a = intArrayOf(Bmc.anyInt(), Bmc.anyInt())
        Bmc.check(Arrays.equals(a, a))
    }

    @BmcProof
    fun equals_true_iff_elementwise_equal() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        val a = intArrayOf(x, y)
        val b = intArrayOf(x, y)
        Bmc.check(Arrays.equals(a, b))
    }

    @BmcProof
    fun equals_differing_element_is_false() {
        val x = Bmc.anyInt()
        val a = intArrayOf(x, x)
        val b = intArrayOf(x, x + 1)
        Bmc.check(!Arrays.equals(a, b))
    }

    @BmcProof
    fun equals_different_length_is_false() {
        val x = Bmc.anyInt()
        Bmc.check(!Arrays.equals(intArrayOf(x), intArrayOf(x, x)))
    }

    // --- hashCode -------------------------------------------------------------------------------

    @BmcProof
    fun hashCode_matches_the_31_polynomial() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        val expected = 31 * (31 * 1 + x) + y
        Bmc.check(Arrays.hashCode(intArrayOf(x, y)) == expected)
    }

    @BmcProof
    fun hashCode_equal_arrays_have_equal_hash() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        Bmc.check(Arrays.hashCode(intArrayOf(x, y)) == Arrays.hashCode(intArrayOf(x, y)))
    }

    // --- sort -----------------------------------------------------------------------------------

    @BmcProof
    fun sort_orders_two_elements() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        val a = intArrayOf(x, y)
        Arrays.sort(a)
        Bmc.check(a[0] <= a[1])
        // permutation: the multiset is preserved (min then max).
        Bmc.check(a[0] == minOf(x, y) && a[1] == maxOf(x, y))
    }

    @BmcProof
    fun sort_three_elements_is_nondecreasing() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        val z = Bmc.anyInt()
        val a = intArrayOf(x, y, z)
        Arrays.sort(a)
        Bmc.check(a[0] <= a[1] && a[1] <= a[2])
    }

    // --- binarySearch (search a sorted array) ---------------------------------------------------

    @BmcProof
    fun binarySearch_finds_a_present_key() {
        // Build a sorted 3-array, then search for its middle element: must return its index.
        val x = Bmc.anyInt()
        val a = intArrayOf(x, x + 1, x + 2)   // strictly increasing -> sorted, distinct
        Bmc.assume(x < x + 1 && x + 1 < x + 2) // exclude the +overflow wraparound
        Bmc.check(Arrays.binarySearch(a, x + 1) == 1)
    }

    @BmcProof
    fun binarySearch_absent_key_returns_negative_insertion_point() {
        val x = Bmc.anyInt(-1000, 1000)
        val a = intArrayOf(x, x + 2, x + 4)
        // key between a[0] and a[1] -> insertion point 1 -> -(1)-1 == -2
        Bmc.check(Arrays.binarySearch(a, x + 1) == -2)
    }

    // --- stream / setAll ------------------------------------------------------------------------

    @BmcProof
    fun stream_sum_equals_element_sum() {
        val x = Bmc.anyInt(-1000, 1000)
        val y = Bmc.anyInt(-1000, 1000)
        val z = Bmc.anyInt(-1000, 1000)
        Bmc.check(Arrays.stream(intArrayOf(x, y, z)).sum() == x + y + z)
    }

    @BmcProof
    fun setAll_applies_generator_at_each_index() {
        val a = intArrayOf(0, 0, 0)
        Arrays.setAll(a) { i -> i * 2 }
        Bmc.check(a[0] == 0 && a[1] == 2 && a[2] == 4)
    }

    // --- mechanical primitive clones (byte/short/float copy/store; integral sort/search) --------

    @BmcProof
    fun copyOf_byte_preserves_and_zero_pads() {
        val a = byteArrayOf(Bmc.anyByte())
        val c = Arrays.copyOf(a, 2)
        Bmc.check(c.size == 2 && c[0] == a[0] && c[1].toInt() == 0)
    }

    @BmcProof
    fun copyOfRange_short_interior_slice() {
        val a = shortArrayOf(Bmc.anyShort(), Bmc.anyShort(), Bmc.anyShort())
        val c = Arrays.copyOfRange(a, 1, 3)
        Bmc.check(c.size == 2 && c[0] == a[1] && c[1] == a[2])
    }

    @BmcProof
    fun fill_byte_sets_every_element() {
        val a = byteArrayOf(0, 0, 0)
        val v = Bmc.anyByte()
        Arrays.fill(a, v)
        Bmc.check(a[0] == v && a[1] == v && a[2] == v)
    }

    @BmcProof
    fun sort_short_orders_two_elements() {
        val x = Bmc.anyShort()
        val y = Bmc.anyShort()
        val a = shortArrayOf(x, y)
        Arrays.sort(a)
        Bmc.check(a[0] <= a[1])
        Bmc.check(a[0] == minOf(x, y) && a[1] == maxOf(x, y))
    }

    @BmcProof
    fun binarySearch_byte_finds_present_key() {
        // a sorted, distinct 3-array of small bytes; search the middle element -> index 1.
        val x = Bmc.anyByte()
        Bmc.assume(x >= -10 && x <= 10)
        val a = byteArrayOf((x - 1).toByte(), x, (x + 1).toByte())
        Bmc.check(Arrays.binarySearch(a, x) == 1)
    }

    @BmcProof
    fun mismatch_first_differing_index() {
        val x = Bmc.anyInt()
        val a = intArrayOf(x, x, x)
        val b = intArrayOf(x, x + 1, x)   // differ at index 1
        Bmc.check(Arrays.mismatch(a, b) == 1)
    }

    @BmcProof
    fun mismatch_equal_arrays_is_minus_one() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        Bmc.check(Arrays.mismatch(intArrayOf(x, y), intArrayOf(x, y)) == -1)
    }

    @BmcProof
    fun mismatch_prefix_returns_shorter_length() {
        val x = Bmc.anyInt()
        // [x] is a proper prefix of [x, x] -> mismatch at the shorter length, 1.
        Bmc.check(Arrays.mismatch(intArrayOf(x), intArrayOf(x, x)) == 1)
    }

    @BmcProof
    fun compare_equal_arrays_is_zero() {
        val x = Bmc.anyInt()
        val y = Bmc.anyInt()
        Bmc.check(Arrays.compare(intArrayOf(x, y), intArrayOf(x, y)) == 0)
    }

    @BmcProof
    fun compare_orders_by_first_difference() {
        val x = Bmc.anyInt(-1000, 1000)
        // [x, x] vs [x, x+1]: first difference at index 1, x < x+1 -> negative.
        Bmc.check(Arrays.compare(intArrayOf(x, x), intArrayOf(x, x + 1)) < 0)
    }

    @BmcProof
    fun compare_prefix_is_shorter_minus_longer() {
        val x = Bmc.anyInt()
        // [x] vs [x, x]: prefix -> a.length - b.length == 1 - 2 == -1.
        Bmc.check(Arrays.compare(intArrayOf(x), intArrayOf(x, x)) == -1)
    }
}
