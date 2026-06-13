package proofs.env;

import example.env.Environment;
import org.bmc4j.Bmc;

/**
 * A concrete {@link Environment} the proofs instantiate so they hold a NON-NULL, concretely typed
 * dependency handle (a symbolic interface object trips JBMC's dynamic-cast check). Its
 * {@code bucketCount} returns an UNCONSTRAINED value - the unpredictable external query. With an
 * {@code Bmc.assumeStable(env::bucketCount, ...)} the call site (including the {@code <clinit>} read) is
 * pinned to one fixed value; without one, this unconstrained body is analysed and the captured bound is
 * symbolic.
 */
final class NondetEnvironment implements Environment {

    @Override
    public int bucketCount() {
        return Bmc.anyInt();
    }
}
