package example.vacuity;

/**
 * A plain leaf method. Its contract lives test-side in {@code contracts.vacuity.BandsContract},
 * where the precondition is deliberately <em>unsatisfiable</em> to demonstrate the vacuity guard
 *: an enforce proof with an empty {@code @Requires} domain must fail VACUOUS, not pass.
 */
public final class Bands {

    private Bands() {
    }

    public static int clamp(int x) {
        if (x < 0) {
            return 0;
        }
        if (x > 100) {
            return 100;
        }
        return x;
    }
}
