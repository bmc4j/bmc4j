package proofs.repository

import example.repository.User
import example.repository.UserRepository
import org.bmc4j.Bmc

/**
 * A concrete [UserRepository] the proofs instantiate so they hold a NON-NULL, concretely typed
 * dependency handle (a symbolic interface object trips JBMC's dynamic-cast check). Mirrors the Java
 * module's NondetRepository (a concrete impl behind a UserService, NOT a nondet interface receiver).
 *
 * Its findById returns an UNCONSTRAINED user - any id, any age - a stand-in for a real repository
 * whose output the proof can't predict. With an `Bmc.assumeEvery(repo::findById) { ... }` the call
 * site is redirected to the assumed-output stub and this body is dead; WITHOUT one, this unconstrained
 * body is analysed, so a property that depends on the repository's behaviour is no longer provable -
 * which is exactly what makes the assumption load-bearing.
 */
class NondetRepository : UserRepository {
    override fun findById(id: Int): User? = User(Bmc.anyInt(), Bmc.anyInt())
}
