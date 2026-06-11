package proofs.stream;

import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@code IntStream} terminal ops that return {@link OptionalInt} —
 * {@code min()}, {@code max()}, {@code reduce(IntBinaryOperator)}, {@code findFirst()}, {@code
 * findAny()}. These were the tail members PR #136 documented as blocked purely on the unmodeled
 * {@code OptionalInt} type; now that it's an audited model they're pulled onto the bounded backing.
 * Each pins both the present (non-empty stream) and the empty case ({@code isEmpty}). {@code findAny}
 * is deterministic in this eager model (returns the first element), a valid choice under the JDK's
 * "any element" contract. {@code average}/{@code summaryStatistics} stay in the tail (double).
 */
class IntStreamOptionalLaws {

    @BmcProof(unwind = 4)
    void min_of_present() {
        OptionalInt o = IntStream.of(3, 1, 2).min();
        Bmc.check(o.isPresent() && o.getAsInt() == 1);
    }

    @BmcProof(unwind = 4)
    void max_of_present() {
        OptionalInt o = IntStream.of(3, 1, 2).max();
        Bmc.check(o.isPresent() && o.getAsInt() == 3);
    }

    @BmcProof
    void min_max_of_empty_are_absent() {
        Bmc.check(IntStream.empty().min().isEmpty());
        Bmc.check(IntStream.empty().max().isEmpty());
    }

    @BmcProof(unwind = 8)
    void reduce_no_identity_sums_when_present() {
        OptionalInt o = IntStream.of(1, 2, 3, 4).reduce((a, b) -> a + b);
        Bmc.check(o.isPresent() && o.getAsInt() == 10);
    }

    @BmcProof
    void reduce_no_identity_empty_is_absent() {
        Bmc.check(IntStream.empty().reduce((a, b) -> a + b).isEmpty());
    }

    @BmcProof
    void findFirst_returns_head() {
        OptionalInt o = IntStream.of(7, 8, 9).findFirst();
        Bmc.check(o.isPresent() && o.getAsInt() == 7);
    }

    @BmcProof
    void findAny_returns_an_element() {
        OptionalInt o = IntStream.of(7, 8, 9).findAny();
        Bmc.check(o.isPresent() && o.getAsInt() == 7);
    }

    @BmcProof
    void findFirst_findAny_of_empty_are_absent() {
        Bmc.check(IntStream.empty().findFirst().isEmpty());
        Bmc.check(IntStream.empty().findAny().isEmpty());
    }

    /** Symbolic: min over three symbolic values is <= each element and equals one of them. */
    @BmcProof(unwind = 4)
    void symbolic_min_is_a_lower_bound() {
        int a = Bmc.anyInt(-1000, 1000);
        int b = Bmc.anyInt(-1000, 1000);
        int c = Bmc.anyInt(-1000, 1000);
        int m = IntStream.of(a, b, c).min().getAsInt();
        Bmc.check(m <= a && m <= b && m <= c && (m == a || m == b || m == c));
    }

    /** Symbolic: reduce(+) without identity equals the sum of the three elements. */
    @BmcProof
    void symbolic_reduce_sum() {
        int a = Bmc.anyInt(-1000, 1000);
        int b = Bmc.anyInt(-1000, 1000);
        int c = Bmc.anyInt(-1000, 1000);
        Bmc.check(IntStream.of(a, b, c).reduce((x, y) -> x + y).getAsInt() == a + b + c);
    }
}
