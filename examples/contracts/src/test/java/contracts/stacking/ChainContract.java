package contracts.stacking;

import example.stacking.Chain;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;

/**
 * Contracts for the {@link Chain} stack, declared test-side. Each closed form is discharged
 * <em>using the one below it</em> — {@code g}'s enforce proof relies on {@code h}'s contract,
 * {@code f}'s on {@code g}'s. Cost is additive in the number of contracts, not multiplicative
 * with depth.
 */
@BmcContractsFor(Chain.class)
interface ChainContract {

    @Requires("inRange") @Ensures("eqF") int f(int n);
    @Requires("inRange") @Ensures("eqG") int g(int n);
    @Requires("inRange") @Ensures("eqH") int h(int n);

    static boolean inRange(int n) {
        return n >= 0 && n <= 10;
    }

    static boolean eqH(int result, int n) {
        return result == n * (n + 1) / 2;
    }

    static boolean eqG(int result, int n) {
        return result == n * (n + 1) * (n + 2) / 6;
    }

    static boolean eqF(int result, int n) {
        return result == n * (n + 1) * (n + 2) * (n + 3) / 24;
    }
}
