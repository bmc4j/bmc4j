package org.bmc4j.engine;

/**
 * A user-supplied model declared with its <em>intent</em> — the trust metadata bmc4j needs to put
 * provenance on a verdict that rests on it.
 *
 * <p>Shadowing a class on JBMC's analysis classpath already works (see {@code src/bmcModel}); what's
 * missing without this is <em>declared intent</em>. A model is either:
 * <ul>
 *   <li>{@link Intent#CONFORMANT} — it claims JDK fidelity (a faithful stand-in for a real class bmc4j
 *       doesn't model). It can be checked the same way bundled models are (differential-vs-JDK + laws),
 *       and a green proof that rests on it needs no caveat beyond naming it.</li>
 *   <li>{@link Intent#DOMAIN} — it <em>intentionally diverges</em> from the JDK to encode a domain
 *       constraint ("keys never collide", "lists bounded to 32"). This is {@code Bmc.assume()} at
 *       classpath altitude: legitimate, but invisible at the proof site — so it carries a one-line
 *       {@link #rationale()} and is surfaced on every green proof that used it.</li>
 * </ul>
 *
 * <p>This is the <em>declaration</em> only; {@link ModelPolicy} turns a set of declarations plus the
 * models actually on a proof's classpath into the footnote / override-warning / strict-UNKNOWN decision,
 * mirroring how {@code StubPolicy} turns the stub fact into a policy decision.
 */
public final class UserModel {

    /** What a user model claims about its relationship to the real JDK class it shadows. */
    public enum Intent {
        /** Claims JDK fidelity; verifiable by the same conformance harness as bundled models. */
        CONFORMANT,
        /** Intentionally diverges to encode a domain constraint; requires a {@link #rationale()}. */
        DOMAIN
    }

    private final String className;
    private final Intent intent;
    private final String rationale; // non-blank for DOMAIN; null/blank for CONFORMANT

    private UserModel(String className, Intent intent, String rationale) {
        this.className = className;
        this.intent = intent;
        this.rationale = rationale;
    }

    /** A conformant model — claims JDK fidelity, no rationale needed. */
    public static UserModel conformant(String className) {
        return new UserModel(normalize(className), Intent.CONFORMANT, null);
    }

    /**
     * A domain model — intentional divergence. {@code rationale} is required (a one-line explanation of
     * the assumed constraint, e.g. {@code "keys are UUIDs, collision-free"}); a blank rationale is a
     * declaration bug and fails loudly, because the whole point of a domain model is that its divergence
     * is stated, not silent.
     */
    public static UserModel domain(String className, String rationale) {
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException(
                    "domain model " + className + " must declare a one-line rationale "
                            + "(it intentionally diverges from the JDK — say how, e.g. "
                            + "\"keys are UUIDs, collision-free\")");
        }
        return new UserModel(normalize(className), Intent.DOMAIN, rationale.trim());
    }

    /** The fully-qualified class name this model shadows (e.g. {@code acme.NoCollisionMap}). */
    public String className() {
        return className;
    }

    public Intent intent() {
        return intent;
    }

    /** The domain rationale; {@code null} for a conformant model. */
    public String rationale() {
        return rationale;
    }

    public boolean isDomain() {
        return intent == Intent.DOMAIN;
    }

    private static String normalize(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("model class name must be non-blank");
        }
        // Accept either binary (a/b/C) or source (a.b.C) form; store source form for matching FQNs.
        return className.trim().replace('/', '.');
    }
}
