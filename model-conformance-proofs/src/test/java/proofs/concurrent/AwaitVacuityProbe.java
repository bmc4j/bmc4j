package proofs.concurrent;

import java.util.concurrent.CountDownLatch;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Soundness probe for the assume-prune blocking idealization (the vacuity check) — a LIVE
 * regression test in the green suite: it declares {@code expect = VACUOUS}, so it PASSES while the
 * vacuity check fires correctly and goes red if the check ever stops firing. (It was previously
 * {@code @Disabled} and manual-only, which meant the property it guards was unwatched in CI.)
 *
 * <p>A {@code CountDownLatch.await()} with NO matching {@code countDown()} can only block: the model's
 * {@code await()} does {@code CProver.assume(count == 0)}, and since the count is still {@code 1} the
 * single feasible path is pruned. The reachability check then reports the proof as
 * <b>VACUOUS</b> ("assumptions are unsatisfiable - this proof checks nothing") rather than verifying
 * vacuously or throwing. That is the correct outcome: code that can only deadlock proves nothing.
 */
class AwaitVacuityProbe {

    /** await() with no countDown(): count never reaches 0, await prunes the only path -> VACUOUS. */
    @BmcProof(expect = Verdict.VACUOUS)
    void await_without_countdown_is_vacuous() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();              // assume(count == 0), but count == 1 -> path pruned
        Bmc.check(latch.getCount() == 0); // unreachable assertion: nothing is actually checked
    }
}
