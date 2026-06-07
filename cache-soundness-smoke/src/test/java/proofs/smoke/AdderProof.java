package proofs.smoke;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import smoke.proven.Adder;

/**
 * The single proof exercised by the verdict-cache soundness smoke. Verifies cold, then is served
 * from the cache on an unchanged re-run. The smoke asserts on the per-proof progress line the
 * plugin emits ("... (cached verdict, ...)" on a HIT vs no marker on a live solve).
 */
class AdderProof {

    /** PASSES cold: {@code dbl(x)} equals {@code 2 * x} for every int. Phase 3 breaks {@code Adder.dbl}. */
    @BmcProof
    void dbl_equals_two_x() {
        int x = Bmc.anyInt();
        Bmc.check(Adder.dbl(x) == 2 * x);
    }
}
