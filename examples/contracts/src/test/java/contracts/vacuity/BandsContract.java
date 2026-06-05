package contracts.vacuity;

import example.vacuity.Bands;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Verdict;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;

/**
 * The vacuity guard for contracts: an <b>unsatisfiable</b> {@code @Requires} would let an
 * enforce proof "verify" over an empty input domain — discharging the {@code @Ensures} vacuously, then
 * weakening every caller that reuses the (never-actually-checked) summary. That is the highest-value
 * place for the check. bmc4j injects a reachability marker into each generated enforce proof, so an
 * empty precondition fails as <b>VACUOUS</b> ({@code "assumptions are unsatisfiable - this proof
 * checks nothing"}) instead of passing.
 *
 * <p>There is no hand-written test here — the generated {@code enforce__*} proofs are the test.
 */
// Expected enforce verdict: VACUOUS - this contract's @Requires is unsatisfiable, proving the
// vacuity guard catches an empty precondition instead of blessing an unchecked @Ensures.
@BmcContractsFor(value = Bands.class, expectEnforce = Verdict.VACUOUS)
interface BandsContract {

    /**
     * VACUOUS: {@code impossible} can never hold ({@code x} cannot be both {@code < 0} and {@code > 0}),
     * so {@code enforce__clamp} has an empty domain. Without the guard it would pass and bless the
     * unchecked {@code @Ensures}; with it, the proof fails VACUOUS.
     */
    @Requires("impossible")
    @Ensures("inRange")
    int clamp(int x);

    static boolean impossible(int x) {
        return x < 0 && x > 0;          // empty: no int is both
    }

    static boolean inRange(int result, int x) {
        return result >= 0 && result <= 100;
    }
}
