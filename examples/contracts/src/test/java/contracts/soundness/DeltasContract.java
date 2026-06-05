package contracts.soundness;

import example.soundness.Deltas;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.ExpectEnforce;
import org.bmc4j.Requires;
import org.bmc4j.Verdict;

/**
 * The soundness guard, declared test-side: <b>annotating is not proving</b>. Both mirror
 * methods carry the same {@code @Ensures("neverNegative")}. The processor generates an enforce
 * proof for each; {@code absDelta} honours it, {@code delta} does not — so its generated proof
 * goes red and turns the build red. A contract that was never discharged cannot be reused.
 *
 * <p>There is no hand-written test here — the generated enforce proofs are the test.
 */
@BmcContractsFor(Deltas.class)
interface DeltasContract {

    /** FALSE: {@code a - b} is negative whenever {@code a < b}; enforce__delta refutes it. */
    // Expected enforce verdict: REFUTED - this @Ensures is deliberately FALSE, proving
    // "annotating is not asserting": the generated enforce-proof passes only by refuting it.
    @ExpectEnforce(Verdict.REFUTED)
    @Requires("bounded")
    @Ensures("neverNegative")
    int delta(int a, int b);

    /** TRUE: an absolute value really is non-negative; enforce__absDelta verifies (and must). */
    @Requires("bounded")
    @Ensures("neverNegative")
    int absDelta(int a, int b);

    static boolean bounded(int a, int b) {
        return a >= 0 && b >= 0 && a <= 1000 && b <= 1000;
    }

    static boolean neverNegative(int result, int a, int b) {
        return result >= 0;
    }
}
