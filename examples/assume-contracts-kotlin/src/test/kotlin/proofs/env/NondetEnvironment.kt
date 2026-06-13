package proofs.env

import example.env.Environment
import org.bmc4j.Bmc

/**
 * A concrete [Environment] the proofs instantiate so they hold a NON-NULL, concretely typed dependency
 * handle (a symbolic interface object trips JBMC's dynamic-cast check). Its bucketCount returns an
 * UNCONSTRAINED value - the unpredictable external query. With an
 * `Bmc.assumeStable(env::bucketCount) { ... }` the call site (including the static-initializer read)
 * is pinned to one fixed value; without one, this unconstrained body is analysed and the captured
 * bound is symbolic.
 */
class NondetEnvironment : Environment {
    override fun bucketCount(): Int = Bmc.anyInt()
}
