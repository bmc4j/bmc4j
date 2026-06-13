package example.repository

/**
 * An external dependency with NO analyzed implementation - a repository the proof can't see through
 * (a database/network call behind an interface). Its output is exactly what an
 * `Bmc.assumeEvery(repo::findById) { ... }` assumption constrains: "every findById returns a value
 * satisfying this predicate". Without an assumption JBMC nondet-stubs the call (the result is
 * unconstrained), so a property that depends on the repository's behaviour can't be proven - which is
 * the point of the assume-guarantee.
 */
interface UserRepository {

    /** Look up a user by id; null when absent. No analyzed body exists. */
    fun findById(id: Int): User?
}

/** A user record. Constructed only behind the repository, never directly in a proof. */
class User(val id: Int, val age: Int)
