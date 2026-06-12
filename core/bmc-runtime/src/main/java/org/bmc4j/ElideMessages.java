package org.bmc4j;

/**
 * Per-proof control over <b>exception-message elision</b> — whether bmc4j may drop the construction of
 * a thrown exception's message (passing {@code null} instead) so an unobserved, expensive message build
 * never poisons the proof. Declared via {@link BmcProof#elideMessages()}; the build-wide default is
 * {@code bmc { elideMessages = "auto"|"on"|"off" }} / {@code -Dbmc.elideMessages}.
 *
 * <p>See {@link BmcProof#elideMessages()} for the full semantics. In short: a {@code throw new T(<expensive
 * message>)} forces BMC to symbolically execute the message construction even on a branch the proof never
 * takes; if that construction is intractable (a byte&rarr;String materialization, an unbounded concat) it
 * sinks an otherwise-valid proof. Eliding the message — while still constructing and throwing the
 * exception — removes that cost without changing the verdict, as long as the message is never read.
 */
public enum ElideMessages {

    /**
     * Elide an exception message <b>iff</b> the proof's reachable cone observes no {@code Throwable}
     * message anywhere (a coarse, all-or-nothing soundness gate). When AUTO elides, the elided value was
     * provably dead — no caveat. The default.
     */
    AUTO,

    /**
     * <b>Force</b> elision even if the cone contains a message observer — a <em>user assertion</em> that
     * the elided messages don't affect what this proof checks. A VERIFIED reached this way is surfaced
     * with a footnote, so it is never read as an unconditional proof.
     */
    ON,

    /** Never elide — the pre-feature behaviour (the message construction is analysed as written). */
    OFF
}
