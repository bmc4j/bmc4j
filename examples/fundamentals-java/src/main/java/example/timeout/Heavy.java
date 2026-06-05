package example.timeout;

/**
 * A deliberately solver-heavy routine, used by the {@code timeout} concept to demonstrate the
 * <b>UNKNOWN</b> verdict. Nested loops over wide symbolic inputs blow up the bit-vector
 * formula JBMC hands the SAT solver, so with a large unwind the proof can't be decided in a small
 * time budget — which is exactly when you want a timeout instead of a hung build.
 */
public final class Heavy {

    private Heavy() {
    }

    /**
     * A quadratic accumulation whose result depends on every product {@code i * j} of two wide
     * symbolic inputs. Unwound to a high bound this produces a large, hard-to-solve formula.
     */
    public static long quadraticMix(int a, int b) {
        long acc = 0;
        for (int i = 0; i < a; i++) {
            for (int j = 0; j < b; j++) {
                acc += (long) i * j + (acc ^ (i + j));
            }
        }
        return acc;
    }
}
