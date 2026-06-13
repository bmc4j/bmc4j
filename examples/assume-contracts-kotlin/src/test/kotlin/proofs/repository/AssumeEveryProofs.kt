package proofs.repository

import example.repository.UserRepository
import example.repository.UserService
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/** A mutable top-level var; reading it inside a predicate is exactly what a purity audit would forbid. */
private var minAge = 0

/**
 * Assume-guarantee over an unanalyzable repository, driven from IDIOMATIC KOTLIN. Every assumeEvery
 * call uses a method reference (repo::findById) SAM-converted to a Bmc.Ref plus a trailing-lambda
 * predicate with `it` - the exact ergonomics the feature exists for. The verdict is flagged "VERIFIED
 * under assumed contract ... - NOT unconditional". The Kotlin counterpart of proofs.repository in
 * examples/assume-contracts.
 */
class AssumeEveryProofs {

    /**
     * VERIFIES under the output-only assumption that every findById returns null or a user with
     * age >= 0: the service's result is then always >= -1. Method ref + trailing lambda with `it`.
     */
    @BmcProof(unwind = 4)
    fun `service holds under the repository assumption`() {
        val repo: UserRepository = NondetRepository()
        Bmc.assumeEvery(repo::findById) { it == null || it.age >= 0 }
        val service = UserService(repo)
        val id = Bmc.anyInt(0, 100)
        Bmc.check(service.ageOrAbsent(id) >= -1)
    }

    /**
     * DROP THE ASSUMPTION and the same property is no longer provable: findById is nondet-stubbed (any
     * user, any age including negative), so ageOrAbsent can return an arbitrary negative age - REFUTED.
     * This is what makes the assumption above LOAD-BEARING.
     */
    @BmcProof(expect = Verdict.REFUTED)
    fun `without the assumption the property is refuted`() {
        val repo: UserRepository = NondetRepository()
        val service = UserService(repo)
        val id = Bmc.anyInt(0, 100)
        // No assumeEvery: the repository's output is unconstrained, so the age can be anything.
        Bmc.check(service.ageOrAbsent(id) >= 0)
    }

    /**
     * ARGS-AWARE: the predicate constrains the output BY the call argument - findById(id) returns null
     * or a user whose id == id. A trailing lambda with two named params (user, id); a proof that relies
     * on result.id == id then verifies.
     */
    @BmcProof
    fun `args aware assumption constrains output by argument`() {
        val repo: UserRepository = NondetRepository()
        Bmc.assumeEvery(repo::findById) { user, id -> user == null || user.id == id }
        val service = UserService(repo)
        val id = Bmc.anyInt(0, 100)
        val got = service.idOf(id)
        Bmc.check(got == -1 || got == id)
    }

    /**
     * OVER-TIGHT predicate => VACUOUS, surfaced. age >= 0 && age < 0 is satisfiable by no output, so
     * every findById path is pruned and the proof checks nothing - the existing vacuity detection flags
     * it.
     */
    @BmcProof(expect = Verdict.VACUOUS)
    fun `an over tight predicate is vacuous`() {
        val repo: UserRepository = NondetRepository()
        Bmc.assumeEvery(repo::findById) { it != null && it.age >= 0 && it.age < 0 }
        val service = UserService(repo)
        Bmc.check(service.ageOrAbsent(Bmc.anyInt(0, 100)) >= -1)
    }

    /**
     * An IMPURE predicate is ACCEPTED. assumeEvery is NOT purity-audited (unlike a dischargeable
     * @Ensures contract): it is an explicit, user-owned assertion. This predicate reads the mutable
     * top-level var minAge rather than being a pure function of its input - an impurity an annotation
     * contract audit would reject - yet here it is analysed normally and VERIFIES (under minAge == 0,
     * every returned age is >= 0, so the service result is >= -1).
     */
    @BmcProof(unwind = 4)
    fun `an impure predicate is accepted`() {
        val repo: UserRepository = NondetRepository()
        Bmc.assumeEvery(repo::findById) { it == null || it.age >= minAge }
        val service = UserService(repo)
        Bmc.check(service.ageOrAbsent(Bmc.anyInt(0, 100)) >= -1)
    }
}
