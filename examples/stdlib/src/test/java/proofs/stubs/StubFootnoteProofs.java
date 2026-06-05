package proofs.stubs;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

import example.stubs.TokenIds;

/**
 * Nondet-stub detection, demonstrated end-to-end.
 *
 * <p>{@link TokenIds#clampedMillis(long)} calls {@code java.util.concurrent.TimeUnit.toMillis(long)},
 * which bmc4j ships no model for, so JBMC analyzes it as a nondet stub. Both proofs below VERIFY (the
 * clamp is sound for <em>any</em> value the stub returns — nondet is conservative) — the difference is
 * what bmc4j tells you:
 *
 * <ul>
 *   <li>{@link #clamped_millis_in_range()} prints a one-line footnote naming the stubbed method (visible
 *       in the test output) — the green verdict, made honest.</li>
 *   <li>{@link #clamped_millis_in_range_acknowledged()} adds {@code @BmcProof(allowStubs = …)} to say
 *       "I've reasoned about this — nondet is sound here", which silences the footnote and keeps the
 *       proof green even under {@code -Dbmc.strictStubs=true}.</li>
 * </ul>
 *
 * <p>Under {@code ./gradlew :examples:stdlib:test -Dbmc.strictStubs=true} the first proof flips to
 * UNKNOWN (the verdict rests on a havoc'd stand-in, so it isn't trustworthy) while the acknowledged one
 * stays green — the strict-mode contract.
 */
class StubFootnoteProofs {

    /** Verifies, but footnotes the TimeUnit stub: the green verdict assumes nondet is sound for it. */
    @BmcProof
    void clamped_millis_in_range() {
        long s = Bmc.anyLong();
        long m = TokenIds.clampedMillis(s);
        Bmc.check(m >= 0 && m <= 1000);
    }

    /** Same proof, but the stubs are acknowledged — no footnote, and green even in strict mode. */
    @BmcProof(allowStubs = {"java.util.concurrent.TimeUnit.*", "java.util.Objects.*"})
    void clamped_millis_in_range_acknowledged() {
        long s = Bmc.anyLong();
        long m = TokenIds.clampedMillis(s);
        Bmc.check(m >= 0 && m <= 1000);
    }
}
