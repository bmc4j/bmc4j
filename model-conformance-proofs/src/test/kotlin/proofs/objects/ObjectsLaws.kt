package proofs.objects

import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import java.util.Objects

/**
 * Model proofs (axis 2): algebraic laws the [java.util.Objects] model must satisfy under JBMC's own
 * semantics, over symbolic inputs (so they hold for every value at once). All must pass.
 */
class ObjectsLaws {

    @BmcProof
    fun equals_is_null_safe_and_reflexive() {
        val x = Bmc.anyInt()
        // both null -> equal; one null -> not equal; reflexive on a boxed int
        Bmc.check(Objects.equals(null, null))
        Bmc.check(!Objects.equals(x, null))
        Bmc.check(!Objects.equals(null, x))
        Bmc.check(Objects.equals(x, x))
    }

    @BmcProof
    fun hashCode_of_null_is_zero() {
        Bmc.check(Objects.hashCode(null) == 0)
    }

    @BmcProof
    fun isNull_nonNull_are_complementary() {
        val x = Bmc.anyInt()
        Bmc.check(Objects.isNull(null) && !Objects.nonNull(null))
        Bmc.check(!Objects.isNull(x) && Objects.nonNull(x))
    }

    @BmcProof
    fun requireNonNull_returns_on_non_null() {
        val x = Bmc.anyInt()
        Bmc.check(Objects.requireNonNull(x) == x)
    }

    @BmcProof
    fun requireNonNull_throws_on_null() {
        var threw = false
        try {
            Objects.requireNonNull<Any?>(null)
        } catch (e: NullPointerException) {
            threw = true
        }
        Bmc.check(threw)
    }

    @BmcProof
    fun requireNonNullElse_falls_back_when_null() {
        val d = Bmc.anyInt()
        Bmc.check(Objects.requireNonNullElse(null, d) == d)
        val x = Bmc.anyInt()
        Bmc.check(Objects.requireNonNullElse(x, d) == x)
    }

    @BmcProof
    fun compare_same_reference_is_zero_without_comparator() {
        val x = Bmc.anyInt()
        val boxed: Int? = x
        // a == b short-circuits to 0; the comparator (which would throw) is never invoked.
        Bmc.check(Objects.compare(boxed, boxed) { _, _ -> throw AssertionError() } == 0)
    }

    @BmcProof
    fun compare_delegates_to_the_comparator() {
        val a = Bmc.anyInt(-1000, 1000)
        val b = Bmc.anyInt(-1000, 1000)
        Bmc.assume(a != b)
        val r = Objects.compare(a, b) { x, y -> if (x < y) -1 else if (x > y) 1 else 0 }
        Bmc.check((r < 0) == (a < b))
    }

    @BmcProof
    fun checkIndex_returns_in_range_throws_out_of_range() {
        val len = Bmc.anyInt(1, 10)
        val i = Bmc.anyInt(0, len - 1)
        Bmc.check(Objects.checkIndex(i, len) == i)
        // out of range (>= len) throws IndexOutOfBoundsException
        var threw = false
        try {
            Objects.checkIndex(len, len)
        } catch (e: IndexOutOfBoundsException) {
            threw = true
        }
        Bmc.check(threw)
    }

    @BmcProof
    fun checkFromToIndex_returns_from_when_valid() {
        val len = Bmc.anyInt(0, 10)
        val from = Bmc.anyInt(0, len)
        val to = Bmc.anyInt(from, len)
        Bmc.check(Objects.checkFromToIndex(from, to, len) == from)
    }

    @BmcProof
    fun checkFromIndexSize_returns_from_when_valid() {
        val len = Bmc.anyInt(0, 10)
        val from = Bmc.anyInt(0, len)
        val size = Bmc.anyInt(0, len - from)
        Bmc.check(Objects.checkFromIndexSize(from, size, len) == from)
    }
}
