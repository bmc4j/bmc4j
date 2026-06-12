package proofs.repository;

import example.repository.UserRepository;
import org.bmc4j.Bmc;

/**
 * A concrete {@link UserRepository} the proofs instantiate so they hold a NON-NULL, concretely typed
 * dependency handle (a symbolic interface object trips JBMC's dynamic-cast check).
 *
 * <p>Its {@code findById} returns an UNCONSTRAINED user — any id, any age — a stand-in for a real
 * repository whose output the proof can't predict. With an {@code Bmc.assumeEvery(repo::findById, …)} the
 * call site is redirected to the assumed-output stub and this body is dead (the assumption constrains the
 * output); WITHOUT one, this unconstrained body is analysed, so a property that depends on the
 * repository's behaviour is no longer provable — which is exactly what makes the assumption load-bearing.
 */
final class NondetRepository implements UserRepository {

    @Override
    public User findById(int id) {
        return new User(Bmc.anyInt(), Bmc.anyInt());
    }
}
