package org.bmc4j.concurrent;

import org.cprover.CProver;

/**
 * A completion barrier for concurrency proofs.
 *
 * <p>JBMC does not model {@code Thread.join()} as a happens-before barrier, so the
 * intuitive "spawn → join → check final state" proof doesn't work directly. A
 * {@code Latch} restores that: a worker calls {@link #complete()} as its last action,
 * and the proof calls {@link #await()} before reading shared results. {@code await()}
 * restricts JBMC to interleavings in which the worker has finished — which (because a
 * fresh {@code Latch} starts not-completed) it can only do by running to completion.
 *
 * <p>This is the same shape as structured concurrency's {@code await}/{@code coroutineScope},
 * which is the step toward modelling Kotlin coroutines.
 *
 * <pre>{@code
 * Latch latch = new Latch();
 * Thread t = new Thread(() -> { result[0] = compute(); latch.complete(); });
 * t.start();
 * latch.await();                 // barrier
 * Bmc.check(result[0] == expected);
 * }</pre>
 */
public final class Latch {

    private boolean completed = false;

    /** Mark the work done. A worker calls this as its final action. */
    public void complete() {
        completed = true;
    }

    /** Proceed only on interleavings where {@link #complete()} has run (a barrier). */
    public void await() {
        CProver.assume(completed);
    }
}
