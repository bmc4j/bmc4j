package smoke.proven;

/**
 * The class under proof for the verdict-cache soundness smoke. Its single property
 * ({@code dbl(x) == 2 * x}) holds for every {@code int} (the doubling overflows in lockstep
 * with {@code 2 * x}, so they are bit-for-bit equal), so the proof verifies cold and the
 * verified verdict is cacheable.
 *
 * <p>Phase 3 of the smoke MUTATES this method to violate the property (returning {@code x + x + 1}),
 * which must force a fresh engine run that REFUTES — a stale cached green must never be served.
 */
public final class Adder {

    private Adder() {
    }

    /** Double {@code x}. Equal to {@code 2 * x} for all inputs (overflow included). */
    public static int dbl(int x) {
        return x + x;
    }
}
