package proofs.purity;

import example.repository.UserRepository;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * An assumed-contract predicate MUST be pure (it is the only thing constraining an otherwise-free
 * symbol; an impure predicate would not be a function of its inputs). bmc4j certifies it with the same
 * purity audit the annotation contracts use, and REJECTS an impure one with an unconditional
 * {@code ContractPurityError} — NOT a verdict {@code @BmcProof(expect = …)} could swallow.
 *
 * <p>This proof's predicate performs a WALL-CLOCK read ({@code System.nanoTime()}), so the audit rejects
 * it at proof time. The build would go red with the audit's message naming the impure call, so this
 * class is EXCLUDED from the test run in {@code build.gradle.kts}; removing that exclusion is itself a
 * regression check that the purity gate still fires for assumed-contract predicates.
 */
class ImpurePredicateDemo {

    @BmcProof
    void impure_predicate_is_rejected() {
        UserRepository repo = Bmc.anyRef(UserRepository.class);
        // Impure: the predicate reads the wall clock — not a function of its input. REJECTED by the
        // purity audit (ContractPurityError), never analysed.
        Bmc.assumeEvery(repo::findById, u -> u == null || System.nanoTime() > u.age());
        Bmc.check(true);
    }
}
