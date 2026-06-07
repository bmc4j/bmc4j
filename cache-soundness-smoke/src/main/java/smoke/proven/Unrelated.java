package smoke.proven;

/**
 * A class the proof never touches, present on the same analysis classpath as {@link Adder}.
 *
 * <p>Phase 4 of the smoke EDITS this class and re-runs: because it is outside the proof's
 * reachable cone, the cone-scoped cache key must be unchanged, so the proof must still be served
 * from the cache (a HIT). This is the cone-key promise — touching an unrelated class on the
 * classpath does not invalidate a proof that cannot reach it.
 */
public final class Unrelated {

    private Unrelated() {
    }

    /** Returns a constant. The proof never calls this; the smoke perturbs the literal. */
    public static int unusedConstant() {
        return 0;
    }
}
