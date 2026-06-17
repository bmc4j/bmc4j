package proofs.loopcontract;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * The decimal read-accumulation loop SUMMARIZED with a RELATIONAL loop-contract invariant that ties the
 * accumulator to the source value, so the multiply-by-10 CHAIN is NEVER unrolled. This is the mechanism the
 * okio readDecimalLong(writeDecimalLong(v)) roundtrip needs: a BOUNDING invariant only proves the result
 * fits in N digits; only a RELATIONAL one proves the value is exactly recovered.
 *
 * <h2>The relation (arithmetic, no ghost loop)</h2>
 * Reading digits most-significant-first, after k digits the accumulator equals a prefix of v:
 * <pre>   value == v / pow10(L - k)        (integer division)</pre>
 * The digit read at step k is v's k-th most-significant digit -- this IS the write loop's postcondition
 * (writeDecimalLong emits exactly v's base-10 digits), substituted in arithmetically:
 * <pre>   digit_k == (v / pow10(L - 1 - k)) % 10</pre>
 * The step VC then reduces to the closed identity
 * <pre>   (v / 10^(L-k)) * 10 + (v / 10^(L-1-k)) % 10  ==  v / 10^(L-1-k)</pre>
 * which the solver discharges in ONE iteration with no multiply chain. At k == L: value == v / 1 == v.
 *
 * <h2>pow10 is a loop-free lookup</h2>
 * so the invariant adds zero unrolling; the only multiply the proof sees is the single body `value*10`.
 */
class DecimalRoundtripRelationalProof {

    /** pow10(e) = 10^e for e in 0..18 (covers every Long decimal width), a loop-free lookup. */
    private static long pow10(int e) {
        switch (e) {
            case 0: return 1L;
            case 1: return 10L;
            case 2: return 100L;
            case 3: return 1000L;
            case 4: return 10000L;
            case 5: return 100000L;
            case 6: return 1000000L;
            case 7: return 10000000L;
            case 8: return 100000000L;
            case 9: return 1000000000L;
            case 10: return 10000000000L;
            case 11: return 100000000000L;
            case 12: return 1000000000000L;
            case 13: return 10000000000000L;
            case 14: return 100000000000000L;
            case 15: return 1000000000000000L;
            case 16: return 10000000000000000L;
            case 17: return 100000000000000000L;
            default: return 1000000000000000000L; // e == 18
        }
    }

    // --- 3-digit roundtrip: small, fast sanity that the relation discharges ---------------------------

    /** VERIFIES: 3-digit roundtrip via summarization, unwind = 1 (multiply chain NOT unrolled). */
    @BmcProof(unwind = 1)
    void roundtrip_3_digits_relational_summarized() {
        int len = 3;
        long v = Bmc.anyLong(0, 999L);
        long value = 0;
        int k = 0;
        Bmc.loopInvariant(k >= 0 && k <= len && value == v / pow10(len - k));   // base: value == v/10^len == 0
        Bmc.loopHavoc();                                                        // havoc {value, k, digit}
        Bmc.loopAssume(k >= 0 && k <= len && value == v / pow10(len - k));      // inductive hyp
        Bmc.loopGuard(k < len);                                                 // open step
        long digit = (v / pow10(len - 1 - k)) % 10;                            // v's k-th MSB digit
        value = value * 10 + digit;                                            // ---- body, once ----
        k = k + 1;
        Bmc.loopPreserve(k >= 0 && k <= len && value == v / pow10(len - k));    // step VC (arithmetic id)
        Bmc.loopExit(k < len);                                                  // exit: k == len
        Bmc.check(value == v);                                                  // roundtrip recovered
    }

    // --- 6-digit roundtrip: a multiply chain whose UNROLLED form is undecided at the same bound -------

    /**
     * VERIFIES: 6-digit roundtrip via summarization, unwind = 1. The companion
     * {@link #roundtrip_6_digits_unrolled_baseline_is_undecided} runs the SAME accumulation as a real loop
     * and is UNDECIDED at unwind = 1 -- so the green here comes from summarizing the multiply chain, not from
     * one iteration sufficing. (The relational arithmetic -- integer division of v by a power of ten -- is
     * what makes the solver work grow with the digit count even when summarized; see the report. Six digits
     * is a fast, decisive demonstration of the relational mechanism.)
     */
    @BmcProof(unwind = 1)
    void roundtrip_6_digits_relational_summarized() {
        int len = 6;
        long v = Bmc.anyLong(0, 999999L);
        long value = 0;
        int k = 0;
        Bmc.loopInvariant(k >= 0 && k <= len && value == v / pow10(len - k));   // base: value == v/10^len == 0
        Bmc.loopHavoc();                                                        // havoc {value, k, digit}
        Bmc.loopAssume(k >= 0 && k <= len && value == v / pow10(len - k));      // inductive hyp
        Bmc.loopGuard(k < len);                                                 // open step
        long digit = (v / pow10(len - 1 - k)) % 10;                            // v's k-th MSB digit
        value = value * 10 + digit;                                            // ---- body, once ----
        k = k + 1;
        Bmc.loopPreserve(k >= 0 && k <= len && value == v / pow10(len - k));    // step VC (arithmetic id)
        Bmc.loopExit(k < len);                                                  // exit: k == len
        Bmc.check(value == v);                                                  // roundtrip recovered
    }

    /**
     * CONTROL (UNKNOWN at unwind = 1): the SAME 6-digit accumulation as a REAL counted loop, NOT summarized.
     * At unwind = 1 the loop truncates after one iteration and the unwinding assertion fires -> UNKNOWN.
     * Together with the summarized test above this pins the claim: the green comes from summarization, not
     * from one iteration sufficing.
     */
    @BmcProof(unwind = 1, expect = Verdict.UNKNOWN)
    void roundtrip_6_digits_unrolled_baseline_is_undecided() {
        int len = 6;
        long v = Bmc.anyLong(0, 999999L);
        long value = 0;
        for (int k = 0; k < len; k++) {            // real loop, unrolled -- bound 1 truncates it
            long digit = (v / pow10(len - 1 - k)) % 10;
            value = value * 10 + digit;
        }
        Bmc.check(value == v);
    }

    // --- SOUNDNESS: a WRONG relational invariant must REFUTE ------------------------------------------

    /**
     * REFUTES: the same loop with a WRONG relation ({@code value == v / pow10(len - k) + 1}, off by one).
     * The step preservation assert fails -> REFUTED, never a false VERIFIED. Pins that the relational
     * invariant is a CHECKED hint, not a trusted assume.
     */
    @BmcProof(unwind = 1, expect = Verdict.REFUTED)
    void wrong_relational_invariant_refutes() {
        int len = 3;
        long v = Bmc.anyLong(0, 999);
        long value = 0;
        int k = 0;
        Bmc.loopInvariant(k >= 0 && k <= len && value == v / pow10(len - k) + 1); // WRONG: +1
        Bmc.loopHavoc();
        Bmc.loopAssume(k >= 0 && k <= len && value == v / pow10(len - k) + 1);
        Bmc.loopGuard(k < len);
        long digit = (v / pow10(len - 1 - k)) % 10;
        value = value * 10 + digit;
        k = k + 1;
        Bmc.loopPreserve(k >= 0 && k <= len && value == v / pow10(len - k) + 1); // step assert FAILS
        Bmc.loopExit(k < len);
        Bmc.check(value == v);
    }
}
