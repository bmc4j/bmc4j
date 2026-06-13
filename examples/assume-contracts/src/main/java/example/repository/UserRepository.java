package example.repository;

/**
 * An external dependency with NO analyzed implementation - a repository the proof can't see through
 * (a database/network call behind an interface). Its output is exactly what an
 * {@code Bmc.assumeEvery(repo::findById, ...)} assumption constrains: "every {@code findById} returns a
 * value satisfying this predicate". Without an assumption JBMC nondet-stubs the call (the result is
 * unconstrained), so a property that depends on the repository's behaviour can't be proven - which is
 * the point of the assume-guarantee.
 */
public interface UserRepository {

    /** Look up a user by id; {@code null} when absent. No analyzed body exists. */
    User findById(int id);

    /** A user record. Constructed only behind the repository, never directly in a proof. */
    final class User {
        private final int id;
        private final int age;

        public User(int id, int age) {
            this.id = id;
            this.age = age;
        }

        public int id() {
            return id;
        }

        public int age() {
            return age;
        }
    }
}
