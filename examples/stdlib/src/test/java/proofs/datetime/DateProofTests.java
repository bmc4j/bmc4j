package proofs.datetime;

import example.datetime.Bookings;
import java.time.Instant;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * The Instants are symbolic proof parameters. The {@code bmc-models} module models
 * {@code java.time.Instant} as an epoch-millis long, so {@code isBefore}/{@code isAfter}
 * become integer comparisons JBMC reasons about exactly.
 */
class DateProofTests {

    /** FAILS: end of the window is excluded by the buggy inclusive/exclusive mix. */
    // Expected verdict: REFUTED - the seeded range bug puts end outside its own range.
    @BmcProof(expect = Verdict.REFUTED)
    void end_is_within_its_own_range(Instant start, Instant end) {
        Bmc.assume(start != null && end != null);
        Bmc.assume(!start.isAfter(end));               // start <= end
        Bmc.check(Bookings.within(end, start, end));   // end should be in [start, end]
    }

    /** PASSES: the inclusive version contains the end for every ordered window. */
    @BmcProof
    void withinInclusive_contains_the_end(Instant start, Instant end) {
        Bmc.assume(start != null && end != null);
        Bmc.assume(!start.isAfter(end));
        Bmc.check(Bookings.withinInclusive(end, start, end));
    }
}
