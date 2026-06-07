package org.bmc4j.analysis;

/**
 * COMPILE-ONLY source-compatible stub of bmc-runtime's unmodelled-member sentinel, present here only
 * so the kotlin.* loud stubs in this module can route their bodies through
 * {@code BmcUnmodelledReached.fail(...)} — the same hand-written loud-body shape the JDK models in
 * bmc-models use. This module cannot depend on bmc-runtime (bmc-runtime already consumes this module's
 * compiled classes, so the dependency would be a cycle), and this stub is NOT bundled into the model
 * jar (only the {@code main} source set is). At verification time JBMC loads bmc-runtime's REAL
 * {@code BmcUnmodelledReached} from the analysis classpath; this stub never reaches it.
 *
 * <p>The real class is the authority — see {@code org.bmc4j.analysis.BmcUnmodelledReached} in
 * bmc-runtime. Keep this signature in lockstep with it.
 */
public final class BmcUnmodelledReached {

    private BmcUnmodelledReached() {
    }

    /** Trips the recognized sentinel assertion; mirrors the real signature. Never returns normally. */
    public static void reached(String member) {
        assert false : member;
    }

    /**
     * Loud-body helper for hand-written model stubs. The {@code member} string MUST start with
     * {@code "bmc4j: unmodelled member "} — the audit gate checks every stub body for exactly this.
     */
    public static AssertionError fail(String member) {
        reached(member);
        return new AssertionError(member);
    }
}
