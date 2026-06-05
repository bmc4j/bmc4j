package java.util.concurrent;

import org.cprover.CProver;

/**
 * Sequential BMC model of {@link java.util.concurrent.CountDownLatch} — a counter that
 * {@link #countDown()} decrements (floored at 0) and {@link #getCount()} reads. bmc4j proves logic,
 * not interleavings (that's Lincheck's job), so the latch is modeled single-threaded: the count is
 * plain mutable state.
 *
 * <p><b>Blocking idealization (assume-prune):</b> a real {@code await()} blocks the calling thread
 * until the count reaches 0. We idealize that the standard BMC way: {@code await()} <em>assumes</em>
 * the latch has actually reached zero (the blocking precondition the thread waits for) and then
 * proceeds, pruning the not-yet-counted-down path from the analysis. So the <em>logic</em> after an
 * {@code await()} is fully testable — exactly the "set up N, count down N, then proceed" pattern —
 * while remaining sound: it is NOT an unconditional no-op (that would let code proceed even though
 * the latch never fired, which is unsound). A proof that can ONLY block — an {@code await()} with no
 * matching {@code countDown()} sequence to reach zero — has its single feasible path pruned, so it
 * becomes <b>vacuous</b> (the vacuity check flags "this proof checks nothing"), which is the correct
 * outcome: code that can only deadlock proves nothing.
 *
 * <p><b>Axis note:</b> {@code await()} uses the {@link CProver#assume} prune primitive, which only
 * has meaning under JBMC — so it is exercised on the {@code @BmcProof} (proof) axis only, never on
 * the JVM-runnable differential axis. The non-blocking surface ({@link #countDown()} /
 * {@link #getCount()}) stays pure Java and is differential-tested against the real JDK.
 */
public class CountDownLatch {

    private long count;

    public CountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
    }

    /** Decrement the count toward 0; counting down a latch already at 0 is a no-op (matches the JDK). */
    public void countDown() {
        if (count > 0) {
            count--;
        }
    }

    public long getCount() {
        return count;
    }

    /**
     * Blocking await, idealized as assume-prune (see class javadoc): assume the count has actually
     * reached 0 (the blocking precondition the calling thread waits for), then proceed. The
     * not-counted-down path is pruned, so logic after {@code await()} is testable yet sound (await is
     * NOT an unconditional no-op). An {@code await()} with no way to reach zero leaves no feasible
     * path, so the proof is correctly flagged vacuous.
     */
    public void await() {
        CProver.assume(count == 0);
    }

    /**
     * Timed await. bmc4j models no time, so the blocking form is idealized the same way: assume the
     * count reached 0 and return {@code true}. (A real timed await could also time out and return
     * {@code false}; modeling that disjunction would let an un-counted-down proof proceed on the
     * false branch, which is unsound — so we prune to the success precondition, consistent with
     * {@link #await()}.)
     */
    public boolean await(long timeout, TimeUnit unit) {
        CProver.assume(count == 0);
        return true;
    }

    @Override
    public String toString() {
        return super.toString() + "[Count = " + count + "]";
    }
}
