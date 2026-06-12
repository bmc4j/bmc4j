package proofs.repository;

import example.repository.UserRepository;
import example.repository.UserService;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Assume-guarantee over an unanalyzable repository. {@code UserService} is correct only IF the
 * repository upholds an output property; {@code Bmc.assumeEvery(repo::findById, …)} supplies that
 * property — no model of the repository, no annotation. The verdict is flagged "VERIFIED under assumed
 * contract … — NOT unconditional".
 */
class AssumeEveryProofs {

    /**
     * VERIFIES under the output-only assumption that every {@code findById} returns {@code null} or a
     * user with {@code age >= 0}: the service's result is then always {@code >= -1}. The repository has
     * no analyzed body — the proof rests entirely on the assumption.
     */
    @BmcProof(unwind = 4)
    void service_holds_under_the_repository_assumption() {
        UserRepository repo = new NondetRepository();
        Bmc.assumeEvery(repo::findById, u -> u == null || u.age() >= 0);
        UserService service = new UserService(repo);
        int id = Bmc.anyInt(0, 100);
        Bmc.check(service.ageOrAbsent(id) >= -1);
    }

    /**
     * DROP THE ASSUMPTION and the same property is no longer provable: {@code findById} is nondet-stubbed
     * (any user, any age including negative), so {@code ageOrAbsent} can return an arbitrary negative
     * age — REFUTED. This is what makes the assumption above LOAD-BEARING.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void without_the_assumption_the_property_is_refuted() {
        UserRepository repo = new NondetRepository();
        UserService service = new UserService(repo);
        int id = Bmc.anyInt(0, 100);
        // No assumeEvery: the repository's output is unconstrained, so the age can be anything.
        Bmc.check(service.ageOrAbsent(id) >= 0);
    }

    /**
     * ARGS-AWARE: the predicate constrains the output BY the call argument — {@code findById(id)} returns
     * {@code null} or a user whose {@code id == id}. A proof that relies on {@code result.id == id} then
     * verifies.
     */
    @BmcProof
    void args_aware_assumption_constrains_output_by_argument() {
        UserRepository repo = new NondetRepository();
        Bmc.assumeEvery(repo::findById, (u, id) -> u == null || u.id() == id);
        UserService service = new UserService(repo);
        int id = Bmc.anyInt(0, 100);
        int got = service.idOf(id);
        Bmc.check(got == -1 || got == id);
    }

    /**
     * OVER-TIGHT predicate => VACUOUS, surfaced. {@code age >= 0 && age < 0} is satisfiable by no
     * output, so every {@code findById} path is pruned and the proof checks nothing — the existing
     * vacuity detection flags it.
     */
    @BmcProof(expect = Verdict.VACUOUS)
    void an_over_tight_predicate_is_vacuous() {
        UserRepository repo = new NondetRepository();
        Bmc.assumeEvery(repo::findById, u -> u != null && u.age() >= 0 && u.age() < 0);
        UserService service = new UserService(repo);
        Bmc.check(service.ageOrAbsent(Bmc.anyInt(0, 100)) >= -1);
    }
}
