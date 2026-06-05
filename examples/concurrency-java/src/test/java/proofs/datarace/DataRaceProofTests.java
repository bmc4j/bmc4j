package proofs.datarace;

import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;
import org.bmc4j.concurrent.Latch;

/**
 * Concurrency proofs ({@code concurrent = true} → JBMC explores thread interleavings).
 *
 * <p>Idiom: assert the safety property at the point of interest and let the threads
 * race — do not rely on {@code Thread.join()} to sequence (JBMC doesn't model it as a
 * barrier). JBMC then searches for an interleaving that violates the assertion.
 */
class DataRaceProofTests {

    int shared = 0;

    /**
     * FAILS: a read-after-write race. The other thread's {@code shared = 10} can
     * interleave between this thread's write and its read.
     */
    // Expected verdict: REFUTED - under interleavings another thread's write can land between.
    @BmcProof(concurrent = true, expect = Verdict.REFUTED)
    void read_sees_its_own_write() {
        Thread t = new Thread() {
            public void run() {
                shared = 44;
                int seen = shared;
                assert seen == 44;
            }
        };
        t.start();
        shared = 10; // races with the read above
    }

    /**
     * PASSES: both accesses are guarded by the same monitor, so no interleaving can
     * slip between the write and the read.
     */
    @BmcProof(concurrent = true)
    void synchronized_read_sees_its_own_write() {
        Thread t = new Thread() {
            public void run() {
                synchronized (DataRaceProofTests.this) {
                    shared = 44;
                    int seen = shared;
                    assert seen == 44;
                }
            }
        };
        t.start();
        synchronized (this) {
            shared = 10;
        }
    }

    /**
     * PASSES: a {@link Latch} gives the barrier {@code Thread.join()} doesn't, so we
     * can check the worker's final result. The worker completes the latch last; the
     * proof awaits before reading. (This is the await/structured-concurrency shape.)
     */
    @BmcProof(concurrent = true)
    void final_result_is_visible_after_latch() {
        final int[] result = new int[1];
        final Latch latch = new Latch();
        Thread t = new Thread() {
            public void run() {
                result[0] = 42;
                latch.complete();
            }
        };
        t.start();
        latch.await();
        assert result[0] == 42;
    }
}
