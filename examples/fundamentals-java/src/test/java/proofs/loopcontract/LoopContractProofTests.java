package proofs.loopcontract;

import example.loopsunwinding.Sums;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * SPIKE end-to-end demos of LOOP CONTRACTS (loop SUMMARIZATION). Instead of UNROLLING a loop N times
 * (which the unwind bound caps), the proof attaches an inductive invariant {@code I} and bmc4j discharges
 * a one-iteration STEP check. The loop is written ONCE, straight-line, bracketed by the {@code Bmc.loop*}
 * markers (see {@link Bmc#loopInvariant} for the sequence); {@code LoopContractBytecode} lowers them into
 * the classic sound base/step/summary verification conditions.
 *
 * <p>Soundness: {@code I} is a CHECKED hint. The base case and the step preservation are ASSERTED
 * (engine-proven); only the summary (havoc + assume) uses {@code I}. A WRONG {@code I} fails the base or
 * the step assert and REFUTES — the most important demo here. The whole point is that these VERIFY at a
 * unwind bound FAR below the loop's real trip count, because the loop is never unrolled.
 */
class LoopContractProofTests {

    // --- Test 1: simple counting loop, summarized, VERIFIES without unrolling -----------------------
    //
    // The loop being summarized is:  for (int i = 0; i < n; i++) s += i;   whose result is n*(n-1)/2.
    // Inductive invariant:  I(i, s) = (0 <= i && i <= n && s == i*(i-1)/2).
    //   base (i=0,s=0): 0<=0<=n && 0==0                      -> holds for n>=0
    //   step: assume I & i<n; s+=i; i++; then I still holds  -> s' = i(i+1)/2 = i'*(i'-1)/2, 0<=i'<=n
    //   exit: assume i>=n & I  =>  i==n, s==n*(n-1)/2
    // unwind = 1: the loop is SUMMARIZED, not unrolled, so a bound of 1 suffices even though the real
    // trip count is up to n (here n <= 1000). The fill loop in anyInt has no loop; n is a scalar input.

    /** VERIFIES: the summarized counting loop's result equals the closed form, at unwind = 1 (no unroll). */
    @BmcProof(unwind = 1)
    void counting_loop_summarized_verifies_without_unrolling() {
        int n = Bmc.anyInt(0, 1000);
        int s = 0;
        int i = 0;
        Bmc.loopInvariant(i >= 0 && i <= n && s == i * (i - 1) / 2); // base: I on entry
        Bmc.loopHavoc();                                             // havoc {s, i}
        Bmc.loopAssume(i >= 0 && i <= n && s == i * (i - 1) / 2);    // inductive hypothesis
        Bmc.loopGuard(i < n);                                        // open the step under the guard
        s = s + i;                                                   // ---- loop body, once ----
        i = i + 1;
        Bmc.loopPreserve(i >= 0 && i <= n && s == i * (i - 1) / 2);  // step VC: I preserved (assert + cut)
        Bmc.loopExit(i < n);                                         // exit: assume !(i < n)
        // Summarized state: {s, i} havoc'd, (I && i >= n) assumed  =>  i == n, s == n*(n-1)/2.
        Bmc.check(s == n * (n - 1) / 2);
    }

    /**
     * CONTROL (UNKNOWN at unwind = 1): the SAME loop written as a REAL counted loop, NOT summarized. At
     * unwind = 1 the loop is truncated after one iteration, the unwinding assertion fires, and the verdict
     * is UNKNOWN — exactly the cost the summarized version above AVOIDS. Together they pin the claim:
     * test 1 VERIFIES at unwind = 1 BECAUSE the loop was summarized, not because one iteration sufficed.
     */
    @BmcProof(unwind = 1, expect = Verdict.UNKNOWN)
    void unrolled_baseline_is_undecided_at_unwind_one() {
        int n = Bmc.anyInt(0, 1000);
        Bmc.check(Sums.sumExclusive(n) == n * (n - 1) / 2); // real loop, unrolled — bound 1 truncates it
    }

    // --- Test 2: WRONG invariant on the SAME loop must REFUTE (the key soundness demo) --------------

    /**
     * REFUTES: the same loop, but the invariant's accumulator term is wrong ({@code i*(i+1)/2} instead of
     * {@code i*(i-1)/2}). The STEP preservation assert fails (the body does not preserve the bogus I), so
     * the proof is REFUTED, not a false VERIFIED. This proves the base/step VCs are really ASSERTED.
     */
    @BmcProof(unwind = 1, expect = Verdict.REFUTED)
    void wrong_invariant_refutes() {
        int n = Bmc.anyInt(0, 1000);
        int s = 0;
        int i = 0;
        Bmc.loopInvariant(i >= 0 && i <= n && s == i * (i + 1) / 2); // WRONG: i+1, not i-1
        Bmc.loopHavoc();
        Bmc.loopAssume(i >= 0 && i <= n && s == i * (i + 1) / 2);
        Bmc.loopGuard(i < n);
        s = s + i;
        i = i + 1;
        Bmc.loopPreserve(i >= 0 && i <= n && s == i * (i + 1) / 2);  // step assert FAILS here
        Bmc.loopExit(i < n);
        Bmc.check(s == n * (n - 1) / 2);
    }

    // --- Test 3: decimal accumulator (the okio readDecimalLong inner-loop shape) --------------------
    //
    // The loop being summarized is:  for (int k = 0; k < len; k++) value = value*10 + digit[k];
    // with each digit in 0..9 and a fixed length `len`. Unrolling this multiplies the value by 10 each
    // iteration -> a multiply-CHAIN the symbolic engine blows up on (exactly the okio wall). A RELATIONAL
    // closed form needs ghost state (the running prefix), which this marker spike can't express; a BOUNDING
    // invariant is what a loop contract realistically buys here:
    //   I(k, value) = (0 <= k && k <= len && value >= 0 && value <= UPPER(k))
    // where UPPER(k) is a precomputed bound on the largest value after k digits. We summarize and prove the
    // post-loop bound  value <= UPPER(len)  with NO multiply-chain unroll. (len = 4 here; UPPER(4)=9999.)

    /**
     * VERIFIES: the summarized decimal-accumulator loop stays within its digit-count bound, at unwind = 1.
     * The bound is the realistic property a loop contract proves for this shape without the multiply-chain
     * unroll (a relational exact-value invariant would need ghost prefix state, noted in the report).
     */
    @BmcProof(unwind = 1)
    void decimal_accumulator_bound_summarized_verifies() {
        int len = 4;
        int k = 0;
        long value = 0;
        // UPPER(k): the max accumulator after k digits = 10^k - 1. Encoded as a small lookup so the
        // invariant is a clean relation the engine can check in one step.
        Bmc.loopInvariant(k >= 0 && k <= len && value >= 0 && value <= upper(k));      // base
        Bmc.loopHavoc();                                                               // havoc {k, value}
        Bmc.loopAssume(k >= 0 && k <= len && value >= 0 && value <= upper(k));         // inductive hyp
        Bmc.loopGuard(k < len);                                                        // open step
        long digit = Bmc.anyLong(0, 9);                                                // ---- body ----
        value = value * 10 + digit;
        k = k + 1;
        Bmc.loopPreserve(k >= 0 && k <= len && value >= 0 && value <= upper(k));       // step VC
        Bmc.loopExit(k < len);                                                         // exit
        // Summarized: (k == len && value <= upper(len)).
        Bmc.check(value <= upper(len)); // value fits in `len` decimal digits, no multiply-chain unroll
    }

    /** UPPER(k) = 10^k - 1, the largest value representable in k decimal digits (k in 0..4). Loop-free
     *  (a lookup) so it adds no unrolling to the summarized proof. */
    private static long upper(int k) {
        switch (k) {
            case 0: return 0L;
            case 1: return 9L;
            case 2: return 99L;
            case 3: return 999L;
            default: return 9999L; // k == 4
        }
    }
}
