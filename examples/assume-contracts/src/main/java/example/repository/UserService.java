package example.repository;

/**
 * A small service over {@link UserRepository}. Its correctness is COMPOSITIONAL: it holds only if the
 * repository upholds an output property (a non-null user has a non-negative age, and {@code findById(id)}
 * returns a user whose {@code id} is {@code id}). The proofs supply that property with
 * {@code Bmc.assumeEvery(repo::findById, …)} — assume-guarantee — instead of a model of the repository.
 */
public final class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    /**
     * The user's age, or {@code -1} when absent. Under the assumption that every {@code findById}
     * returns {@code null} or a user with {@code age >= 0}, the result is always {@code >= -1}.
     */
    public int ageOrAbsent(int id) {
        UserRepository.User u = repo.findById(id);
        if (u == null) {
            return -1;
        }
        return u.age();
    }

    /**
     * The id of the looked-up user, or {@code -1} when absent. Under the args-aware assumption that
     * {@code findById(id)} returns {@code null} or a user whose {@code id == id}, this returns either
     * {@code -1} or exactly {@code id}.
     */
    public int idOf(int id) {
        UserRepository.User u = repo.findById(id);
        if (u == null) {
            return -1;
        }
        return u.id();
    }
}
