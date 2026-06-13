package example.repository

/**
 * A small service over [UserRepository]. Its correctness is COMPOSITIONAL: it holds only if the
 * repository upholds an output property (a non-null user has a non-negative age, and findById(id)
 * returns a user whose id is id). The proofs supply that property with
 * `Bmc.assumeEvery(repo::findById) { ... }` - assume-guarantee - instead of a model of the repository.
 */
class UserService(private val repo: UserRepository) {

    /**
     * The user's age, or -1 when absent. Under the assumption that every findById returns null or a
     * user with age >= 0, the result is always >= -1.
     */
    fun ageOrAbsent(id: Int): Int {
        val u = repo.findById(id) ?: return -1
        return u.age
    }

    /**
     * The id of the looked-up user, or -1 when absent. Under the args-aware assumption that
     * findById(id) returns null or a user whose id == id, this returns either -1 or exactly id.
     */
    fun idOf(id: Int): Int {
        val u = repo.findById(id) ?: return -1
        return u.id
    }
}
