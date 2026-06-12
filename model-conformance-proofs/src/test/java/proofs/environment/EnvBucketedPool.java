package proofs.environment;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A minimal SYNTHETIC analog of {@code okio.SegmentPool}'s thread-sharded free list, distilled to the
 * exact shape that made mutable {@code okio.Buffer} proofs come back REFUTED: a per-thread bucket index
 * computed from {@code Thread.currentThread().getId()} masked by {@code (availableProcessors()*2 - 1)},
 * indexing a {@code static AtomicReference<Cell>[]} free list, then APPENDING to the pulled cell — so
 * the cell's (pre-existing) length feeds the observable {@code size}, exactly as okio's pooled Segment's
 * {@code pos}/{@code limit} feed {@code Buffer.size}.
 *
 * <p>The proof over {@link #writeOneIntoFreshPool()} is the litmus test for the empirical model matrix.
 * The pool is reset so every bucket holds a length-0 cell; the only observable behaviour is "after one
 * write, size == 1". With NEITHER {@code Thread.getId()} nor {@code Runtime.availableProcessors()}
 * modeled, both feed a SYMBOLIC bucket index into the static array; jbmc must treat the symbolic-indexed
 * array read as able to alias ANY element, so the pulled cell's length — and hence {@code size} — goes
 * nondet and the proof is conservatively REFUTED even though concretely it is always 1. Modeling the two
 * environmental values as constants makes the index CONCRETE, the read resolves to one known cell, and
 * the proof VERIFIES. This standalone analog keeps the repo from taking an okio test dependency while
 * exercising exactly the same {@code Thread.getId() & (procs*2-1)}-into-static-array code path.
 */
public final class EnvBucketedPool {

    /** A free-list cell — the okio.Segment analog (its length plays the role of Segment pos/limit). */
    static final class Cell {
        int length;
    }

    private static final int HASH_BUCKET_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    @SuppressWarnings("unchecked")
    private static final AtomicReference<Cell>[] hashBuckets = new AtomicReference[HASH_BUCKET_COUNT];

    static {
        for (int i = 0; i < hashBuckets.length; i++) {
            hashBuckets[i] = new AtomicReference<>(new Cell());
        }
    }

    /** Reset every bucket to a fresh length-0 cell (the "empty pool" precondition for the litmus). */
    public static void resetToEmpty() {
        for (int i = 0; i < hashBuckets.length; i++) {
            hashBuckets[i] = new AtomicReference<>(new Cell());
        }
    }

    /**
     * Seed each bucket with a cell whose length is its index — a NON-uniform pool, the okio-realistic
     * case where pooled segments carry differing pos/limit. Here the loaded cell's length genuinely
     * depends on WHICH bucket the thread-id selects, so a symbolic (even bounded) index keeps the result
     * nondet; only a CONCRETE index resolves it.
     */
    public static void seedDistinct() {
        for (int i = 0; i < hashBuckets.length; i++) {
            Cell c = new Cell();
            c.length = i;
            hashBuckets[i] = new AtomicReference<>(c);
        }
    }

    /**
     * Over a DISTINCT-seeded pool, pull this thread's cell and append one. With a concrete bucket index
     * (constants for both env values) the selected cell is bucket 1 (thread id 1, mask 15), length 1 →
     * result 2; with a symbolic/bounded index the loaded length is unknown, so the claim "result == 2"
     * is not provable. This is what makes constant-vs-bounded-nondet observably different.
     */
    public static int writeOneIntoDistinctPool() {
        seedDistinct();
        Cell c = take();
        c.length += 1;
        return c.length;
    }

    /** okio.SegmentPool.firstRef() analog: pick the bucket for the current thread. */
    private static AtomicReference<Cell> firstRef() {
        int bucket = (int) (Thread.currentThread().getId() & (HASH_BUCKET_COUNT - 1));
        return hashBuckets[bucket];
    }

    /** okio.SegmentPool.take() analog: pull this thread's pooled cell. */
    private static Cell take() {
        return firstRef().get();
    }

    /**
     * okio.Buffer.writeByte analog over a FRESH pool: reset so every bucket is a length-0 cell, then
     * pull this thread's cell and append one. Concretely the pulled cell is always length 0, so the
     * returned size is always 1 — unless the symbolic bucket index makes the array read nondet.
     */
    public static int writeOneIntoFreshPool() {
        resetToEmpty();
        Cell c = take();
        c.length += 1;
        return c.length;
    }
}
