package contracts.recursion;

import example.recursion.Recursive;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;

/**
 * Recursion as induction, declared test-side. {@code @Ensures} is the loop invariant in
 * closed form. When the enforce proof analyzes {@code Recursive.sumTo}, the recursive call is
 * summarized by this same contract — so the proof is exactly the inductive step (assume it
 * holds for {@code n-1}; show it holds for {@code n}), not a full unroll.
 */
@BmcContractsFor(Recursive.class)
interface RecursiveContract {

    @Requires("inRange")
    @Ensures("closedForm")
    int sumTo(int n);

    static boolean inRange(int n) {
        return n >= 0 && n <= 12;
    }

    /** The closed form — tight enough to be inductive: n + (n-1)n/2 == n(n+1)/2. */
    static boolean closedForm(int result, int n) {
        return result == n * (n + 1) / 2;
    }
}
