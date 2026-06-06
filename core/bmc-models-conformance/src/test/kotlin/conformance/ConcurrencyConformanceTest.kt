package conformance

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

private val LONG = Long::class.javaPrimitiveType!!
private val BOOL = Boolean::class.javaPrimitiveType!!

private class AOp(val desc: String, val method: String, val types: Array<Class<*>>, val args: Array<Any?>) {
    fun on(t: Any) = call(t, method, types, *args)
    override fun toString() = desc
}

/**
 * Differential conformance for the concurrency models, under SEQUENTIAL use (their documented
 * semantics — bmc4j proves logic, not interleavings; Lincheck does the latter). Single-threaded, an
 * atomic is a plain holder and a completed future is "a ready value", so the model must match the
 * real class exactly on one thread; the small CAS arg domain makes compareAndSet hit both branches.
 */
class ConcurrencyConformanceTest : FunSpec({

    test("AtomicInteger conforms (sequential)") {
        val n = Arb.int(-3..3)
        val op = Arb.choice(
            n.map { AOp("set($it)", "set", arrayOf(INT), arrayOf(it)) },
            n.map { AOp("getAndSet($it)", "getAndSet", arrayOf(INT), arrayOf(it)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndSet($e,$u)", "compareAndSet", arrayOf(INT, INT), arrayOf(e, u)) },
            n.map { AOp("addAndGet($it)", "addAndGet", arrayOf(INT), arrayOf(it)) },
            n.map { AOp("getAndAdd($it)", "getAndAdd", arrayOf(INT), arrayOf(it)) },
            Arb.constant(AOp("incrementAndGet", "incrementAndGet", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAndIncrement", "getAndIncrement", arrayOf(), arrayOf())),
            Arb.constant(AOp("decrementAndGet", "decrementAndGet", arrayOf(), arrayOf())),
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicInteger(0)
            val m = bmcref.java.util.concurrent.atomic.AtomicInteger(0)
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))
        }
    }

    test("AtomicLong conforms (sequential)") {
        val n = Arb.long(-3L..3L)
        val op = Arb.choice(
            n.map { AOp("set($it)", "set", arrayOf(LONG), arrayOf(it)) },
            n.map { AOp("getAndSet($it)", "getAndSet", arrayOf(LONG), arrayOf(it)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndSet($e,$u)", "compareAndSet", arrayOf(LONG, LONG), arrayOf(e, u)) },
            n.map { AOp("addAndGet($it)", "addAndGet", arrayOf(LONG), arrayOf(it)) },
            Arb.constant(AOp("incrementAndGet", "incrementAndGet", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAndDecrement", "getAndDecrement", arrayOf(), arrayOf())),
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicLong(0)
            val m = bmcref.java.util.concurrent.atomic.AtomicLong(0)
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))
        }
    }

    test("AtomicBoolean conforms (sequential)") {
        checkAll(Arb.list(Arb.boolean(), 0..10)) { vals ->
            val r = java.util.concurrent.atomic.AtomicBoolean(false)
            val m = bmcref.java.util.concurrent.atomic.AtomicBoolean(false)
            for (b in vals) {
                assertEquivalent("getAndSet($b)", call(r, "getAndSet", arrayOf(BOOL), b), call(m, "getAndSet", arrayOf(BOOL), b))
                assertEquivalent("cas", call(r, "compareAndSet", arrayOf(BOOL, BOOL), b, !b), call(m, "compareAndSet", arrayOf(BOOL, BOOL), b, !b))
            }
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))
        }
    }

    test("AtomicReference conforms on value ops (sequential)") {
        checkAll(Arb.list(Arb.int(0..5).orNull(0.2), 0..10)) { vals ->
            val r = java.util.concurrent.atomic.AtomicReference<Any?>()
            val m = bmcref.java.util.concurrent.atomic.AtomicReference<Any?>()
            for (v in vals) {
                assertEquivalent("getAndSet($v)", call(r, "getAndSet", arrayOf(OBJECT), v), call(m, "getAndSet", arrayOf(OBJECT), v))
            }
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))
        }
    }

    test("CompletableFuture (completed) conforms") {
        checkAll(Arb.int(0..9)) { v ->
            val r = java.util.concurrent.CompletableFuture.completedFuture(v)
            val m = bmcref.java.util.concurrent.CompletableFuture.completedFuture(v)
            r.get() shouldBe call(m, "get", arrayOf()).getOrThrow()
            r.join() shouldBe call(m, "join", arrayOf()).getOrThrow()
            r.isDone shouldBe call(m, "isDone", arrayOf()).getOrThrow()
            r.getNow(-1) shouldBe call(m, "getNow", arrayOf(OBJECT), -1).getOrThrow()
        }
    }

    // --- CountDownLatch (sequential) ---------------------------------------------------------------
    // Sound, JVM-runnable surface: getCount + countDown (floors at 0). The blocking await() is NOT
    // exercised here: its model now prunes the not-counted-down path via CProver.assume (a JBMC-only
    // primitive that is a no-op on a real JVM), so it lives on the @BmcProof axis (proofs.concurrent),
    // not this differential axis. See ConcurrentLaws for the await logic proofs (+ the vacuity check).
    test("CountDownLatch conforms (sequential: countDown sequence + getCount)") {
        val start = Arb.int(0..6)
        checkAll(start, Arb.list(Arb.constant(Unit), 0..10)) { n, downs ->
            val r = java.util.concurrent.CountDownLatch(n)
            val m = bmcref.java.util.concurrent.CountDownLatch(n)
            assertEquivalent("getCount@start", call(r, "getCount", arrayOf()), call(m, "getCount", arrayOf()))
            for (i in downs.indices) {
                assertEquivalent("countDown[$i]", call(r, "countDown", arrayOf()), call(m, "countDown", arrayOf()))
                assertEquivalent("getCount[$i]", call(r, "getCount", arrayOf()), call(m, "getCount", arrayOf()))
            }
            assertEquivalent("getCount@end", call(r, "getCount", arrayOf()), call(m, "getCount", arrayOf()))
        }
    }

    test("CountDownLatch negative count throws (both)") {
        val real = runCatching { java.util.concurrent.CountDownLatch(-1) }
        val model = runCatching {
            bmcref.java.util.concurrent.CountDownLatch::class.java
                .getConstructor(INT).newInstance(-1)
        }.recoverCatching { throw (it as? java.lang.reflect.InvocationTargetException)?.targetException ?: it }
        assertSameException(real, model)
    }

    // --- Semaphore (sequential) --------------------------------------------------------------------
    // Sound, JVM-runnable surface: availablePermits, tryAcquire (false when none), release,
    // drainPermits. The blocking acquire*() is NOT exercised here: its model now prunes the no-permit
    // path via CProver.assume (JBMC-only, a no-op on a real JVM), so it lives on the @BmcProof axis
    // (proofs.concurrent), not this differential axis. See ConcurrentLaws for the acquire logic proofs.
    test("Semaphore conforms (sequential: tryAcquire/release/availablePermits)") {
        val n = Arb.int(0..4)
        val op = Arb.choice(
            Arb.constant(SOp("tryAcquire") { call(it, "tryAcquire", arrayOf()) }),
            Arb.constant(SOp("release") { call(it, "release", arrayOf()) }),
            n.map { k -> SOp("release($k)") { call(it, "release", arrayOf(INT), k) } },
            n.map { k -> SOp("tryAcquire($k)") { call(it, "tryAcquire", arrayOf(INT), k) } },
            Arb.constant(SOp("availablePermits") { call(it, "availablePermits", arrayOf()) }),
            Arb.constant(SOp("drainPermits") { call(it, "drainPermits", arrayOf()) }),
        )
        checkAll(Arb.int(0..4), Arb.list(op, 0..20)) { init, ops ->
            val r = java.util.concurrent.Semaphore(init)
            val m = bmcref.java.util.concurrent.Semaphore(init)
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            assertEquivalent("availablePermits", call(r, "availablePermits", arrayOf()), call(m, "availablePermits", arrayOf()))
        }
    }

    // --- BlockingQueue impls (sequential) ----------------------------------------------------------
    // Sound, JVM-runnable surface: offer/poll/peek/add/remove/element/size/isEmpty/remainingCapacity/
    // contains, FIFO. The blocking put/take are NOT exercised here: their models now prune the
    // would-block path (full/empty) via CProver.assume (JBMC-only, a no-op on a real JVM), so they
    // live on the @BmcProof axis (proofs.concurrent), not this differential axis. See ConcurrentLaws
    // for the producer/consumer-through-put/take logic proofs.
    for ((label, makeReal, makeModel) in blockingQueueFactories()) {
        test("$label conforms (sequential non-blocking surface, FIFO)") {
            val v = Arb.int(0..9)
            val op = Arb.choice(
                v.map { x -> QOp("offer($x)") { call(it, "offer", arrayOf(OBJECT), x) } },
                Arb.constant(QOp("poll") { call(it, "poll", arrayOf()) }),
                Arb.constant(QOp("peek") { call(it, "peek", arrayOf()) }),
                Arb.constant(QOp("size") { call(it, "size", arrayOf()) }),
                Arb.constant(QOp("isEmpty") { call(it, "isEmpty", arrayOf()) }),
                Arb.constant(QOp("remainingCapacity") { call(it, "remainingCapacity", arrayOf()) }),
                v.map { x -> QOp("contains($x)") { call(it, "contains", arrayOf(OBJECT), x) } },
            )
            checkAll(Arb.list(op, 0..30)) { ops ->
                val r = makeReal(5)
                val m = makeModel(5)
                ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
                // Drain via poll to compare full FIFO order + emptiness.
                repeat(6) { assertEquivalent("drain", call(r, "poll", arrayOf()), call(m, "poll", arrayOf())) }
            }
        }

        test("$label add/remove/element throwing surface conforms") {
            // add throws when full (IllegalStateException), remove/element throw when empty
            // (NoSuchElementException). Exercise a bounded queue of capacity 3.
            val v = Arb.int(0..9)
            val op = Arb.choice(
                v.map { x -> QOp("add($x)") { call(it, "add", arrayOf(OBJECT), x) } },
                Arb.constant(QOp("remove") { call(it, "remove", arrayOf()) }),
                Arb.constant(QOp("element") { call(it, "element", arrayOf()) }),
            )
            checkAll(Arb.list(op, 0..15)) { ops ->
                val r = makeReal(3)
                val m = makeModel(3)
                ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            }
        }

        test("$label offer rejects when full (bounded capacity)") {
            val r = makeReal(2)
            val m = makeModel(2)
            assertEquivalent("offer1", call(r, "offer", arrayOf(OBJECT), 1), call(m, "offer", arrayOf(OBJECT), 1))
            assertEquivalent("offer2", call(r, "offer", arrayOf(OBJECT), 2), call(m, "offer", arrayOf(OBJECT), 2))
            assertEquivalent("offer3(full)", call(r, "offer", arrayOf(OBJECT), 3), call(m, "offer", arrayOf(OBJECT), 3))
        }
    }

    // The DEFAULT (no-arg) LinkedBlockingQueue is unbounded in the JDK: logical capacity
    // Integer.MAX_VALUE, offer never rejects, add never throws, remainingCapacity counts down
    // from MAX_VALUE. The model used to default to its 64-slot storage bound instead, ADMITTING
    // rejections the real default queue cannot produce — a silent-false-green vector this case
    // regression-pins (remainingCapacity is the within-model-domain discriminator: the old model
    // reported 64 - n). Ops stay far below the model's storage bound, the documented domain.
    test("LinkedBlockingQueue() no-arg default is unbounded (logical contract conforms)") {
        val v = Arb.int(0..9)
        val op = Arb.choice(
            v.map { x -> QOp("offer($x)") { call(it, "offer", arrayOf(OBJECT), x) } },
            v.map { x -> QOp("add($x)") { call(it, "add", arrayOf(OBJECT), x) } },
            Arb.constant(QOp("poll") { call(it, "poll", arrayOf()) }),
            Arb.constant(QOp("size") { call(it, "size", arrayOf()) }),
            Arb.constant(QOp("isEmpty") { call(it, "isEmpty", arrayOf()) }),
            Arb.constant(QOp("remainingCapacity") { call(it, "remainingCapacity", arrayOf()) }),
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.LinkedBlockingQueue<Int>()
            val m = bmcref.java.util.concurrent.LinkedBlockingQueue<Int>()
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            repeat(31) { assertEquivalent("drain", call(r, "poll", arrayOf()), call(m, "poll", arrayOf())) }
        }
    }

    // --- Immediate ExecutorService (sequential) ----------------------------------------------------
    // The model runs tasks synchronously and returns a completed Future. On ONE thread the real JDK
    // single-thread executor is observably identical for submit->get. We compare the realized value.
    test("immediate ExecutorService submit(Callable)->get conforms to a real single-thread executor") {
        checkAll(Arb.int(0..100)) { x ->
            val real = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val rf = real.submit(java.util.concurrent.Callable { x * 2 })
                val realResult = rf.get()
                val m = bmcref.java.util.concurrent.ImmediateExecutorService()
                // model: submit returns a done Future; reflectively drive submit(Callable)->get.
                val callable = makeRefCallable(x * 2)
                val mf = call(m, "submit", arrayOf(refClass("java.util.concurrent.Callable")), callable).getOrThrow()!!
                val modelResult = call(mf, "get", arrayOf()).getOrThrow()
                modelResult shouldBe realResult
            } finally {
                real.shutdownNow()
            }
        }
    }

    test("immediate ExecutorService submit(Runnable, result)->get conforms") {
        checkAll(Arb.int(0..50)) { x ->
            val real = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val rf = real.submit(Runnable { }, x)
                val realResult = rf.get()
                val m = bmcref.java.util.concurrent.ImmediateExecutorService()
                val noop = makeRefRunnable()
                val mf = call(m, "submit", arrayOf(refClass("java.lang.Runnable"), OBJECT), noop, x).getOrThrow()!!
                call(mf, "get", arrayOf()).getOrThrow() shouldBe realResult
            } finally {
                real.shutdownNow()
            }
        }
    }
})

private class SOp(val desc: String, val invoke: (Any) -> Result<Any?>) {
    fun on(t: Any) = invoke(t)
    override fun toString() = desc
}

private class QOp(val desc: String, val invoke: (Any) -> Result<Any?>) {
    fun on(t: Any) = invoke(t)
    override fun toString() = desc
}

private fun refClass(name: String): Class<*> = Class.forName(name)

/** (label, realFactory(capacity), modelFactory(capacity)) for each BlockingQueue impl. */
private fun blockingQueueFactories(): List<Triple<String, (Int) -> Any, (Int) -> Any>> = listOf(
    Triple(
        "ArrayBlockingQueue",
        { cap: Int -> java.util.concurrent.ArrayBlockingQueue<Int>(cap) },
        { cap: Int -> bmcref.java.util.concurrent.ArrayBlockingQueue<Int>(cap) },
    ),
    Triple(
        "LinkedBlockingQueue",
        { cap: Int -> java.util.concurrent.LinkedBlockingQueue<Int>(cap) },
        { cap: Int -> bmcref.java.util.concurrent.LinkedBlockingQueue<Int>(cap) },
    ),
)

/** A relocated-model Callable that returns a fixed value (so submit(Callable) drives synchronously). */
private fun makeRefCallable(value: Int): Any {
    val callable = refClass("java.util.concurrent.Callable")
    return java.lang.reflect.Proxy.newProxyInstance(callable.classLoader, arrayOf(callable)) { _, _, _ -> value }
}

private fun makeRefRunnable(): Any {
    val runnable = refClass("java.lang.Runnable")
    return java.lang.reflect.Proxy.newProxyInstance(runnable.classLoader, arrayOf(runnable)) { _, _, _ -> null }
}
