package proofs.env;

import example.env.Buckets;
import example.env.Environment;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * {@code Bmc.assumeStable} pins a deterministic, unanalyzable query to ONE fixed value for the whole
 * run - including the {@code <clinit>} that reads it, a call site a local {@code assume} can't reach.
 * The "environment as a fixed value" case (okio's {@code HASH_BUCKET_COUNT}, reproduced over a plain
 * unanalyzable {@link Environment} dependency).
 */
class AssumeStableProofs {

    /**
     * VERIFIES: {@code assumeStable(ENV::bucketCount, n -> n == 8)} pins the value the {@code Buckets}
     * {@code <clinit>} reads - a call site a local {@code assume} could never reach - to one fixed
     * symbol for the whole run. The captured bound is then the concrete 8, so the array is sized 8 and
     * {@code lastSlot() == count() - 1} can be proven by fully unwinding the fill loop.
     */
    @BmcProof(unwind = 10)
    void stable_pins_a_clinit_bound() {
        Environment env = new NondetEnvironment();
        Buckets.ENV = env;
        Bmc.assumeStable(env::bucketCount, n -> n == 8);
        Buckets b = new Buckets();
        Bmc.check(b.count() == 8);
        Bmc.check(b.lastSlot() == b.count() - 1);
    }

    /**
     * WITHOUT the pin the {@code <clinit>}-captured bound is SYMBOLIC (the unanalyzable
     * {@code bucketCount()} is nondet-stubbed), so {@code count()} can be anything - the claim
     * {@code count() == 8} is REFUTED. This is what makes the pin LOAD-BEARING, and it reaches a call
     * site (the static initializer) a local {@code assume} in this proof body could not.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void without_the_pin_the_value_is_symbolic() {
        Buckets.ENV = new NondetEnvironment();
        Buckets b = new Buckets();
        Bmc.check(b.count() == 8);
    }
}
