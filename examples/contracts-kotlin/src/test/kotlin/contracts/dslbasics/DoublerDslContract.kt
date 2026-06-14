package contracts.dslbasics

import example.dslbasics.Doubler
import org.bmc4j.BmcContracts
import org.bmc4j.Verdict
import org.bmc4j.contracts.contractFor

/**
 * The contracts DSL - typed pre/post-condition authoring, the first increment. A contract is written
 * inline as typed lambdas over the unbound method reference `Doubler::twice`: `self` is the receiver, the
 * call argument and `ret` are typed from the reference signature, refactor-safe and IDE-checked - no
 * stringly-typed `@Ensures("...")` and no separately-named predicate methods.
 *
 * The build's DSL-lowering pass decodes this registration (reading `Doubler::twice` and the predicate
 * lambdas statically from bytecode, the same indy-bootstrap-handle read `Bmc.assumeEvery` uses) and
 * generates `DoublerDslContract__BmcDslEnforce.enforce__twice` - a `@BmcProof` that assumes the
 * precondition, runs the REAL `twice` body with a symbolic receiver, and asserts the postcondition. It
 * VERIFIES, exactly like the annotation form's `__BmcEnforce`.
 *
 * This first increment covers a single-argument instance method whose predicates relate the call argument
 * and the result; a postcondition that reads a RECEIVER FIELD (`before.field`) is the next increment.
 *
 * `init` (the constructor) is where the `contractFor` call lives - authoring, never executed at proof time
 * (the lowering reads it; JUnit runs only the generated proof).
 */
@BmcContracts
class DoublerDslContract {
    init {
        contractFor(Doubler::twice) {
            whenPrecondition("amount in range") { self, amount -> amount in 0..8 }
                    .thenPostCondition("result is non-negative and even") { before, after, amount, ret ->
                        ret >= 0 && ret % 2 == 0
                    }
        }
    }
}

/**
 * A deliberately-FALSE DSL contract, pinned [Verdict.REFUTED]: it claims the result is STRICTLY greater
 * than the doubled argument, which is false (`twice(amount) == amount * 2`, never strictly greater). Its
 * generated enforce-proof passes BY refutation (a counterexample exists), proving the DSL catches a false
 * postcondition - the "annotating is not asserting" guarantee, carried over to the typed-lambda form.
 */
@BmcContracts(expectEnforce = Verdict.REFUTED)
class DoublerDslContractWrong {
    init {
        contractFor(Doubler::twice) {
            whenPrecondition("amount in range") { self, amount -> amount in 0..8 }
                    .thenPostCondition("result strictly exceeds twice the amount") { before, after, amount, ret ->
                        ret > amount * 2
                    }
        }
    }
}
