package java.lang;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Minimal sequential BMC model of {@link java.lang.Thread} — just the identity surface a single-threaded
 * proof actually reaches, so an environmental thread id stops poisoning otherwise-deterministic logic.
 *
 * <p>A bmc4j proof verifies ONE symbolic thread of execution; there is no second thread to interleave
 * with (interleavings are out of scope — that is Lincheck's job). On that single thread the value of
 * {@code Thread.currentThread().getId()} / {@code threadId()} is an opaque, fixed sharding key — its only
 * legitimate use is as a hash/bucket selector (e.g. a per-thread free-list bucket {@code threadId & mask}).
 * Returning a fixed positive constant is therefore FAITHFUL for that single thread, and — crucially —
 * keeps the bucket index CONCRETE so a symbolic id can't turn a deterministic computation (a static-array
 * read) into a conservative nondet/REFUTED. Without this model {@code currentThread()} stubs to a nondet
 * {@code Thread} and {@code getId()} to a nondet {@code long}, which is enough to false-REFUTE an
 * otherwise-deterministic thread-sharded data structure.
 *
 * <p>The model is deliberately tiny: only {@code currentThread()}, {@code getId()}, and {@code threadId()}
 * are modeled. Every other member of the real {@code Thread} surface (start/join/interrupt/state/naming/
 * priority/daemon/the static sleep/yield/holdsLock helpers, …) is the open concurrency-control surface
 * that bmc4j does not model on a single thread; those are absorbed by the class-level {@code @BmcModelTail}
 * with LOUD synthesized bodies, so reaching one is an honest member-named UNKNOWN, never a silent nondet.
 */
@org.bmc4j.models.audit.BmcModelTail(
    reason = "java.lang.Thread's lifecycle/scheduling/naming surface (start/join/interrupt/sleep/state/…) "
        + "is the open concurrency-control surface bmc4j does not model on a single thread; only the "
        + "identity accessors currentThread()/getId()/threadId() are modeled (a fixed sharding key)")
public class Thread {

    /** The single, fixed thread a bmc4j proof executes as — an opaque positive sharding key. */
    private static final Thread CURRENT = new Thread();

    private Thread() {
    }

    @BmcModelConforms("constant environmental stand-in — deterministic representative on one symbolic thread; no behavioral surface to differentially test")
    public static Thread currentThread() {
        return CURRENT;
    }

    /**
     * A fixed positive id. On one symbolic thread the id is an opaque sharding key; pinning it keeps any
     * {@code id & mask} bucket index concrete (so a thread-sharded structure's read stays deterministic).
     */
    @BmcModelConforms("constant environmental stand-in — deterministic representative on one symbolic thread; no behavioral surface to differentially test")
    public long getId() {
        return 1L;
    }

    /**
     * The JDK 19+ rename of {@link #getId()}; modeled WITHOUT {@code @Override} so the source compiles on
     * the Java 17 floor toolchain (where {@code Thread.threadId()} does not yet exist), exactly as the
     * post-17 SequencedCollection head/tail members are carried on the collection models. Same fixed id.
     */
    @BmcModelConforms("constant environmental stand-in — deterministic representative on one symbolic thread; no behavioral surface to differentially test")
    public final long threadId() {
        return 1L;
    }
}
