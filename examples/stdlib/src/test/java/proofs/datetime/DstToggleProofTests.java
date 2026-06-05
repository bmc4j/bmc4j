package proofs.datetime;

import example.datetime.Toggle;
import example.datetime.Zone;
import java.time.Instant;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * "Off then on" across a DST fall-back. We take three instants in real-time order
 * (t1 &lt; t2 &lt; t3) and look for the pattern active / inactive / active again — the
 * toggle re-firing. The DST transition is a symbolic instant, so a proof holds for
 * <em>any</em> transition, not a hardcoded zone.
 */
class DstToggleProofTests {

    private static final long OFFSET_BEFORE = 3_600_000L; // +1h (summer / DST)
    private static final long OFFSET_AFTER = 0L;          // +0h (winter / STD)

    /**
     * FAILS: keyed on local wall-clock. Because local time repeats the hour after a
     * fall-back, JBMC finds advancing instants where the toggle goes on, off, on.
     */
    // Expected verdict: REFUTED - the local-time comparison reactivates across the DST fold.
    @BmcProof(expect = Verdict.REFUTED)
    void toggle_never_reactivates_using_local_time(
            Instant t1, Instant t2, Instant t3, Instant transition,
            long windowStart, long windowEnd) {
        Bmc.assume(t1 != null && t2 != null && t3 != null && transition != null);
        Bmc.assume(t1.isBefore(t2) && t2.isBefore(t3)); // real time advances
        Bmc.assume(windowStart < windowEnd);

        Zone zone = new Zone(OFFSET_BEFORE, OFFSET_AFTER, transition);
        boolean a1 = Toggle.activeByLocalTime(t1, zone, windowStart, windowEnd);
        boolean a2 = Toggle.activeByLocalTime(t2, zone, windowStart, windowEnd);
        boolean a3 = Toggle.activeByLocalTime(t3, zone, windowStart, windowEnd);

        Bmc.check(!(a1 && !a2 && a3)); // must never re-activate
    }

    /**
     * PASSES: keyed on the instant (UTC). Time is monotonic, so once the window is
     * left it is never re-entered.
     */
    @BmcProof
    void toggle_never_reactivates_using_instant(
            Instant t1, Instant t2, Instant t3,
            long windowStart, long windowEnd) {
        Bmc.assume(t1 != null && t2 != null && t3 != null);
        Bmc.assume(t1.isBefore(t2) && t2.isBefore(t3));
        Bmc.assume(windowStart < windowEnd);

        boolean a1 = Toggle.activeByInstant(t1, windowStart, windowEnd);
        boolean a2 = Toggle.activeByInstant(t2, windowStart, windowEnd);
        boolean a3 = Toggle.activeByInstant(t3, windowStart, windowEnd);

        Bmc.check(!(a1 && !a2 && a3));
    }
}
