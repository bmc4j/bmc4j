package org.bmc4j.analysis;

/**
 * The unmodelled-member trap. The bmc-models build's loud-body synthesis pass gives every real JDK
 * member a model deliberately does NOT implement — a per-member {@code @BmcNotModelled} /
 * {@code @BmcNotNeeded} stub, or any member absorbed by {@code @BmcModelTail} — a body that calls
 * {@link #reached(String)} (then throws, so the method still type-checks for a non-void return).
 *
 * <p>{@link #reached(String)}'s body is an {@code assert false}, so a proof that REACHES an
 * unmodelled member trips an assertion <em>inside this sentinel</em> — JBMC reports the violated
 * property with {@code function == java::org.bmc4j.analysis.BmcUnmodelledReached.reached:...}. The
 * verdict interpreter ({@code BmcProofExtension}) recognizes that exact function and DEMOTES the
 * refutation to {@code UNKNOWN}: reaching an unmodelled member is bmc4j's own modeling gap, NOT a
 * counterexample in the user's code. (Contrast a raw {@code throw new AssertionError} in the model
 * method, whose constant-string message JBMC discards and whose violated function is the model
 * member itself — indistinguishable from a user assertion.)
 *
 * <p>The {@code member} argument names the offending member for the message; JBMC does not preserve
 * the string into its trace, so the member is recovered by the interpreter from the property's
 * recorded source location / the synthesized message the loud body still throws.
 *
 * <p>This class is on every proof's analysis classpath (bmc-runtime). It is never loaded by a real
 * JVM in the model path — the models only ever run under JBMC.
 */
public final class BmcUnmodelledReached {

    /** Source-location marker recognized by the verdict interpreter. */
    public static final String FUNCTION_FQN = "org.bmc4j.analysis.BmcUnmodelledReached.reached";

    private BmcUnmodelledReached() {
    }

    /**
     * Trip an assertion JBMC reports against THIS function, so the interpreter can recognize an
     * unmodelled-member reach and demote it to UNKNOWN. Never returns.
     *
     * @param member the offending {@code Class.member(params) — reason} label (for the thrown message)
     */
    public static void reached(String member) {
        assert false : member;
    }

    /**
     * Loud-body helper for HAND-WRITTEN model stubs: trips the recognized sentinel assertion (so a
     * reach demotes to UNKNOWN naming {@code member}) and returns an {@link AssertionError} for the
     * caller to {@code throw} — keeping the stub well-formed for any (incl. non-void) return type and
     * giving a loud fallback if ever run on a real JVM. Use as:
     *
     * <pre>{@code
     * @BmcNotModelled(reason = "...")
     * public void sort(Comparator<? super E> c) {
     *     throw BmcUnmodelledReached.fail("java.util.ArrayList.sort(Comparator) — ...");
     * }
     * }</pre>
     *
     * The {@code member} string MUST start with {@code "bmc4j: unmodelled member "} — the gate checks
     * every hand-written NotModelled/NotNeeded stub body for exactly this call so no real logic hides
     * under a not-modeled annotation.
     */
    public static AssertionError fail(String member) {
        reached(member);
        return new AssertionError(member);
    }
}
