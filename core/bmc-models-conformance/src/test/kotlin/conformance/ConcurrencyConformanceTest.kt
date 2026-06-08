package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
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
            // VarHandle memory-ordering variants: must equal their plain/strong counterpart on one thread.
            n.map { AOp("setPlain($it)", "setPlain", arrayOf(INT), arrayOf(it)) },
            n.map { AOp("setRelease($it)", "setRelease", arrayOf(INT), arrayOf(it)) },
            n.map { AOp("setOpaque($it)", "setOpaque", arrayOf(INT), arrayOf(it)) },
            Arb.constant(AOp("getPlain", "getPlain", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAcquire", "getAcquire", arrayOf(), arrayOf())),
            Arb.constant(AOp("getOpaque", "getOpaque", arrayOf(), arrayOf())),
            Arb.bind(n, n) { e, u -> AOp("compareAndExchange($e,$u)", "compareAndExchange", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndExchangeAcquire($e,$u)", "compareAndExchangeAcquire", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndExchangeRelease($e,$u)", "compareAndExchangeRelease", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCAS($e,$u)", "weakCompareAndSet", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASAcquire($e,$u)", "weakCompareAndSetAcquire", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASRelease($e,$u)", "weakCompareAndSetRelease", arrayOf(INT, INT), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASVolatile($e,$u)", "weakCompareAndSetVolatile", arrayOf(INT, INT), arrayOf(e, u)) },
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicInteger(0)
            val m = bmcref.java.util.concurrent.atomic.AtomicInteger(0)
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            assertEquivalent("get", call(r, "get", arrayOf()), call(m, "get", arrayOf()))
        }
    }

    // --- Atomic update-family (functional args: getAndUpdate/updateAndGet/getAndAccumulate/accumulateAndGet)
    // The update functions are real lambdas (IntUnaryOperator/IntBinaryOperator aren't relocated, so they
    // pass straight through the model). Drive a sequence of update-family ops on both the JDK atomic and
    // the model and compare each return + the final value. getAnd* return the PRIOR value, *AndGet the new.
    test("AtomicInteger update-family conforms (lambdas devirtualize)") {
        val n = Arb.int(-3..3)
        val op = Arb.choice(
            Arb.constant(0), Arb.constant(1),
            n.map { 2 to it }, n.map { 3 to it },
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicInteger(0)
            val m = bmcref.java.util.concurrent.atomic.AtomicInteger(0)
            for (o in ops) {
                when (o) {
                    0 -> r.getAndUpdate { it + 1 }.let { ro -> m.getAndUpdate { it + 1 } shouldBe ro }
                    1 -> r.updateAndGet { it * 2 }.let { ro -> m.updateAndGet { it * 2 } shouldBe ro }
                    is Pair<*, *> -> {
                        val (kind, x) = o
                        val xi = x as Int
                        if (kind == 2) {
                            r.getAndAccumulate(xi) { a, b -> a + b }.let { ro -> m.getAndAccumulate(xi) { a, b -> a + b } shouldBe ro }
                        } else {
                            r.accumulateAndGet(xi) { a, b -> a + b }.let { ro -> m.accumulateAndGet(xi) { a, b -> a + b } shouldBe ro }
                        }
                    }
                }
            }
            m.get() shouldBe r.get()
        }
    }

    test("AtomicLong update-family conforms (lambdas devirtualize)") {
        val n = Arb.long(-3L..3L)
        val op = Arb.choice(
            Arb.constant(0L to 0L), Arb.constant(1L to 0L),
            n.map { 2L to it }, n.map { 3L to it },
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicLong(0)
            val m = bmcref.java.util.concurrent.atomic.AtomicLong(0)
            for ((kind, x) in ops) {
                when (kind) {
                    0L -> r.getAndUpdate { it + 1 }.let { ro -> m.getAndUpdate { it + 1 } shouldBe ro }
                    1L -> r.updateAndGet { it * 2 }.let { ro -> m.updateAndGet { it * 2 } shouldBe ro }
                    2L -> r.getAndAccumulate(x) { a, b -> a + b }.let { ro -> m.getAndAccumulate(x) { a, b -> a + b } shouldBe ro }
                    else -> r.accumulateAndGet(x) { a, b -> a + b }.let { ro -> m.accumulateAndGet(x) { a, b -> a + b } shouldBe ro }
                }
            }
            m.get() shouldBe r.get()
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
            // VarHandle memory-ordering variants: must equal their plain/strong counterpart on one thread.
            n.map { AOp("setPlain($it)", "setPlain", arrayOf(LONG), arrayOf(it)) },
            n.map { AOp("setRelease($it)", "setRelease", arrayOf(LONG), arrayOf(it)) },
            n.map { AOp("setOpaque($it)", "setOpaque", arrayOf(LONG), arrayOf(it)) },
            Arb.constant(AOp("getPlain", "getPlain", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAcquire", "getAcquire", arrayOf(), arrayOf())),
            Arb.constant(AOp("getOpaque", "getOpaque", arrayOf(), arrayOf())),
            Arb.bind(n, n) { e, u -> AOp("compareAndExchange($e,$u)", "compareAndExchange", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndExchangeAcquire($e,$u)", "compareAndExchangeAcquire", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("compareAndExchangeRelease($e,$u)", "compareAndExchangeRelease", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCAS($e,$u)", "weakCompareAndSet", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASPlain($e,$u)", "weakCompareAndSetPlain", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASAcquire($e,$u)", "weakCompareAndSetAcquire", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASRelease($e,$u)", "weakCompareAndSetRelease", arrayOf(LONG, LONG), arrayOf(e, u)) },
            Arb.bind(n, n) { e, u -> AOp("weakCASVolatile($e,$u)", "weakCompareAndSetVolatile", arrayOf(LONG, LONG), arrayOf(e, u)) },
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

    // The VarHandle memory-ordering variants must equal their plain/strong counterpart on one thread.
    test("AtomicBoolean memory-ordering variants conform (sequential)") {
        val b = Arb.boolean()
        val op = Arb.choice(
            b.map { AOp("setPlain($it)", "setPlain", arrayOf(BOOL), arrayOf(it)) },
            b.map { AOp("setRelease($it)", "setRelease", arrayOf(BOOL), arrayOf(it)) },
            b.map { AOp("setOpaque($it)", "setOpaque", arrayOf(BOOL), arrayOf(it)) },
            Arb.constant(AOp("getPlain", "getPlain", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAcquire", "getAcquire", arrayOf(), arrayOf())),
            Arb.constant(AOp("getOpaque", "getOpaque", arrayOf(), arrayOf())),
            Arb.bind(b, b) { e, u -> AOp("cae($e,$u)", "compareAndExchange", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("caeAcq($e,$u)", "compareAndExchangeAcquire", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("caeRel($e,$u)", "compareAndExchangeRelease", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("weakCAS($e,$u)", "weakCompareAndSet", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("weakCASPlain($e,$u)", "weakCompareAndSetPlain", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("weakCASAcquire($e,$u)", "weakCompareAndSetAcquire", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("weakCASRelease($e,$u)", "weakCompareAndSetRelease", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
            Arb.bind(b, b) { e, u -> AOp("weakCASVolatile($e,$u)", "weakCompareAndSetVolatile", arrayOf(BOOL, BOOL), arrayOf(e, u)) },
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicBoolean(false)
            val m = bmcref.java.util.concurrent.atomic.AtomicBoolean(false)
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
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

    // The VarHandle memory-ordering variants (+ getAndAccumulate) must equal their plain/strong
    // counterpart on one thread. AtomicReference uses IDENTITY (==) for the expected value, so the CAS
    // family draws from a fixed pool of token objects (so a witnessed value can == an expected one).
    test("AtomicReference memory-ordering variants conform (sequential, identity CAS)") {
        // A small fixed pool: distinct identities reused across set/expected so CAS hits both branches.
        val pool: List<Any?> = listOf(null, Any(), Any(), Any())
        val tok = Arb.element(pool)
        val op = Arb.choice(
            tok.map { AOp("set", "set", arrayOf(OBJECT), arrayOf(it)) },
            tok.map { AOp("setPlain", "setPlain", arrayOf(OBJECT), arrayOf(it)) },
            tok.map { AOp("setRelease", "setRelease", arrayOf(OBJECT), arrayOf(it)) },
            tok.map { AOp("setOpaque", "setOpaque", arrayOf(OBJECT), arrayOf(it)) },
            Arb.constant(AOp("getPlain", "getPlain", arrayOf(), arrayOf())),
            Arb.constant(AOp("getAcquire", "getAcquire", arrayOf(), arrayOf())),
            Arb.constant(AOp("getOpaque", "getOpaque", arrayOf(), arrayOf())),
            Arb.bind(tok, tok) { e, u -> AOp("cae", "compareAndExchange", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("caeAcq", "compareAndExchangeAcquire", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("caeRel", "compareAndExchangeRelease", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("wCAS", "weakCompareAndSet", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("wCASPlain", "weakCompareAndSetPlain", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("wCASAcq", "weakCompareAndSetAcquire", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("wCASRel", "weakCompareAndSetRelease", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
            Arb.bind(tok, tok) { e, u -> AOp("wCASVol", "weakCompareAndSetVolatile", arrayOf(OBJECT, OBJECT), arrayOf(e, u)) },
        )
        // Both atomics share the SAME initial identity + the SAME pool, so identity comparisons agree.
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.atomic.AtomicReference<Any?>(pool[1])
            val m = bmcref.java.util.concurrent.atomic.AtomicReference<Any?>(pool[1])
            ops.forEachIndexed { i, o -> assertEquivalent("op[$i]=$o", o.on(r), o.on(m)) }
            // getAndAccumulate with a real lambda picking the max identity-hash (deterministic, pure).
            val acc = java.util.function.BinaryOperator<Any?> { a, b -> if (b == null) a else b }
            assertEquivalent("getAndAccumulate",
                call(r, "getAndAccumulate", arrayOf(OBJECT, refClass("java.util.function.BinaryOperator")), pool[2], acc),
                call(m, "getAndAccumulate", arrayOf(OBJECT, refClass("java.util.function.BinaryOperator")), pool[2], acc))
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
            r.isCompletedExceptionally shouldBe call(m, "isCompletedExceptionally", arrayOf()).getOrThrow()
            r.getNow(-1) shouldBe call(m, "getNow", arrayOf(OBJECT), -1).getOrThrow()
        }
    }

    // --- CompletableFuture chaining (normal path) --------------------------------------------------
    // The dependent-action combinators run their lambda on a ready value (single-threaded). Drive each
    // against a real completed future and the model and compare the realized value via join().
    // NOTE: java.util.function.* is NOT relocated (see bmc-models-conformance build script), so the
    // model methods take the REAL functional interfaces — a plain Kotlin SAM lambda passes to both sides.
    test("CompletableFuture chaining conforms on a normal completion") {
        checkAll(Arb.int(0..9)) { v ->
            val r = java.util.concurrent.CompletableFuture.completedFuture(v)
            val m = bmcref.java.util.concurrent.CompletableFuture.completedFuture(v)

            // thenApply
            val fnApply = java.util.function.Function<Int, Int> { it + 100 }
            r.thenApply(fnApply).join() shouldBe
                modelJoin(call(m, "thenApply", arrayOf(FUNCTION), fnApply))

            // thenCompose -> another completed future (the lambda must return the SAME-typed future as
            // its callee, so build a model future for the model call and a real one for the real call)
            val rComposed = r.thenCompose { java.util.concurrent.CompletableFuture.completedFuture(it * 2) }.join()
            val mCompose = java.util.function.Function<Int, Any?> { bmcref.java.util.concurrent.CompletableFuture.completedFuture(it * 2) }
            val mComposed = modelJoin(call(m, "thenCompose", arrayOf(FUNCTION), mCompose))
            mComposed shouldBe rComposed

            // thenCombine with another completed future
            val rOther = java.util.concurrent.CompletableFuture.completedFuture(7)
            val mOther = bmcref.java.util.concurrent.CompletableFuture.completedFuture(7)
            val combine = java.util.function.BiFunction<Int, Int, Int> { a, b -> a + b }
            val rComb = r.thenCombine(rOther, combine).join()
            val mComb = modelJoin(call(m, "thenCombine", arrayOf(CS, BIFUNCTION), mOther, combine))
            mComb shouldBe rComb

            // handle (normal): cause is null
            val handler = java.util.function.BiFunction<Int?, Throwable?, Int> { value, _ -> (value ?: -1) + 1 }
            val rHandled = r.handle(handler).join()
            val mHandled = modelJoin(call(m, "handle", arrayOf(BIFUNCTION), handler))
            mHandled shouldBe rHandled

            // exceptionally on a normal future passes the value through unchanged
            val recover = java.util.function.Function<Throwable, Int> { -999 }
            val rExc = r.exceptionally(recover).join()
            val mExc = modelJoin(call(m, "exceptionally", arrayOf(FUNCTION), recover))
            mExc shouldBe rExc

            // whenComplete observes without altering a normal completion
            val observer = java.util.function.BiConsumer<Int?, Throwable?> { _, _ -> }
            val rWhen = r.whenComplete(observer).join()
            val mWhen = modelJoin(call(m, "whenComplete", arrayOf(BICONSUMER), observer))
            mWhen shouldBe rWhen
        }
    }

    // --- CompletionStage interface surface (devirtualization) --------------------------------------
    // The model now `implements CompletionStage<T>`, so code typed as CompletionStage devirtualizes to
    // the CompletableFuture backing. Drive a CompletionStage-TYPED reference (the relocated interface)
    // through a stage chain and compare against a real CompletionStage. Also exercises the *Async no-arg
    // twins (which reduce to their synchronous combinator under the immediate executor) and
    // toCompletableFuture(). The real JDK CompletionStage is the differential oracle.
    test("CompletionStage-typed chaining + *Async twins + toCompletableFuture conform") {
        checkAll(Arb.int(0..9)) { v ->
            val r: java.util.concurrent.CompletionStage<Int> = java.util.concurrent.CompletableFuture.completedFuture(v)
            val m = bmcref.java.util.concurrent.CompletableFuture.completedFuture(v)  // an instance IS-A relocated CompletionStage

            // thenApplyAsync (no-arg) == thenApply under the immediate executor.
            val fn = java.util.function.Function<Int, Int> { it + 5 }
            r.thenApplyAsync(fn).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "thenApplyAsync", arrayOf(FUNCTION), fn))

            // thenComposeAsync flattens a CompletionStage-returning lambda (composes through the interface).
            val rComposed = r.thenComposeAsync { java.util.concurrent.CompletableFuture.completedFuture(it * 3) }
                .toCompletableFuture().join()
            val mCompose = java.util.function.Function<Int, Any?> { bmcref.java.util.concurrent.CompletableFuture.completedFuture(it * 3) }
            modelJoin(call(m, "thenComposeAsync", arrayOf(FUNCTION), mCompose)) shouldBe rComposed

            // thenCombineAsync over two stages (the `other` is passed as a CompletionStage).
            val rOther = java.util.concurrent.CompletableFuture.completedFuture(11)
            val mOther = bmcref.java.util.concurrent.CompletableFuture.completedFuture(11)
            val combine = java.util.function.BiFunction<Int, Int, Int> { a, b -> a * 10 + b }
            r.thenCombineAsync(rOther, combine).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "thenCombineAsync", arrayOf(CS, BIFUNCTION), mOther, combine))

            // handleAsync / whenCompleteAsync / thenAcceptAsync / thenRunAsync / exceptionallyAsync.
            val handler = java.util.function.BiFunction<Int?, Throwable?, Int> { value, _ -> (value ?: -1) + 2 }
            r.handleAsync(handler).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "handleAsync", arrayOf(BIFUNCTION), handler))
            val observer = java.util.function.BiConsumer<Int?, Throwable?> { _, _ -> }
            r.whenCompleteAsync(observer).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "whenCompleteAsync", arrayOf(BICONSUMER), observer))
            val accept = java.util.function.Consumer<Int> { }
            r.thenAcceptAsync(accept).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "thenAcceptAsync", arrayOf(CONSUMER), accept))
            val run = Runnable { }
            r.thenRunAsync(run).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "thenRunAsync", arrayOf(RUNNABLE), run))
            val recover = java.util.function.Function<Throwable, Int> { -7 }
            r.exceptionallyAsync(recover).toCompletableFuture().join() shouldBe
                modelJoin(call(m, "exceptionallyAsync", arrayOf(FUNCTION), recover))

            // toCompletableFuture() returns a future completing with the same value (backing identity).
            r.toCompletableFuture().join() shouldBe call(m, "toCompletableFuture", arrayOf()).let { modelJoin(it) }
        }
    }

    // --- CompletableFuture EXCEPTION FLOW (the trust-critical path) --------------------------------
    // A future completed exceptionally must: surface ExecutionException from get and CompletionException
    // from join/getNow; short-circuit the dependent-action combinators (propagate the cause); and let
    // exceptionally/handle RECOVER. Compared against a real exceptionally-completed CompletableFuture.
    test("CompletableFuture exceptional completion conforms (get/join wrapping)") {
        val r = java.util.concurrent.CompletableFuture<Int>()
        r.completeExceptionally(RuntimeException("boom"))
        val m = bmcref.java.util.concurrent.CompletableFuture<Int>()
        call(m, "completeExceptionally", arrayOf(THROWABLE), RuntimeException("boom")).getOrThrow()

        call(m, "isDone", arrayOf()).getOrThrow() shouldBe r.isDone
        call(m, "isCompletedExceptionally", arrayOf()).getOrThrow() shouldBe r.isCompletedExceptionally

        // get throws ExecutionException on both
        assertSameException(runCatching { r.get() }, call(m, "get", arrayOf()))
        // join throws CompletionException on both
        assertSameException(runCatching { r.join() }, call(m, "join", arrayOf()))
        // getNow on a failed future also throws CompletionException (not the absent value)
        assertSameException(runCatching { r.getNow(-1) }, call(m, "getNow", arrayOf(OBJECT), -1))
    }

    test("CompletableFuture exceptional completion: combinators short-circuit, exceptionally/handle recover") {
        val r = java.util.concurrent.CompletableFuture<Int>()
        r.completeExceptionally(IllegalStateException("x"))
        val m = bmcref.java.util.concurrent.CompletableFuture<Int>()
        call(m, "completeExceptionally", arrayOf(THROWABLE), IllegalStateException("x")).getOrThrow()

        // thenApply does NOT run the fn; the result is exceptional -> join throws CompletionException.
        val applyFn = java.util.function.Function<Int, Int> { it + 1 }
        val rApplied = r.thenApply(applyFn)
        val mApplied = call(m, "thenApply", arrayOf(FUNCTION), applyFn).getOrThrow()!!
        assertSameException(runCatching { rApplied.join() }, call(mApplied, "join", arrayOf()))

        // exceptionally RECOVERS: fn receives the raw cause, result completes normally.
        val recover = java.util.function.Function<Throwable, Int> { -1 }
        val rRecovered = r.exceptionally(recover).join()
        val mRecovered = modelJoin(call(m, "exceptionally", arrayOf(FUNCTION), recover))
        mRecovered shouldBe rRecovered

        // handle RECOVERS: receives (null value, cause), result completes normally.
        val handler = java.util.function.BiFunction<Int?, Throwable?, Int> { _, cause -> if (cause != null) -2 else 0 }
        val rHandled = r.handle(handler).join()
        val mHandled = modelJoin(call(m, "handle", arrayOf(BIFUNCTION), handler))
        mHandled shouldBe rHandled

        // whenComplete does NOT recover: result stays exceptional.
        val observer = java.util.function.BiConsumer<Int?, Throwable?> { _, _ -> }
        val rWhen = r.whenComplete(observer)
        val mWhen = call(m, "whenComplete", arrayOf(BICONSUMER), observer).getOrThrow()!!
        assertSameException(runCatching { rWhen.join() }, call(mWhen, "join", arrayOf()))
    }

    test("CompletableFuture allOf/anyOf conform (sequential)") {
        checkAll(Arb.int(0..9), Arb.int(0..9)) { a, b ->
            // allOf: all normal -> result join is null (Void)
            val r1 = java.util.concurrent.CompletableFuture.completedFuture(a)
            val r2 = java.util.concurrent.CompletableFuture.completedFuture(b)
            val m1 = bmcref.java.util.concurrent.CompletableFuture.completedFuture(a)
            val m2 = bmcref.java.util.concurrent.CompletableFuture.completedFuture(b)
            val rAll = java.util.concurrent.CompletableFuture.allOf(r1, r2).join()
            val mArr = makeRefCfArray(m1, m2)
            val mAll = modelJoin(staticCall(CF, "allOf", arrayOf(CF_ARRAY), mArr))
            mAll shouldBe rAll

            // anyOf: first arg's value
            val rAny = java.util.concurrent.CompletableFuture.anyOf(r1, r2).join()
            val mAny = modelJoin(staticCall(CF, "anyOf", arrayOf(CF_ARRAY), mArr))
            mAny shouldBe rAny
        }
    }

    test("CompletableFuture allOf surfaces a failure (sequential)") {
        val r1 = java.util.concurrent.CompletableFuture.completedFuture(1)
        val r2 = java.util.concurrent.CompletableFuture<Int>().also { it.completeExceptionally(RuntimeException("f")) }
        val m1 = bmcref.java.util.concurrent.CompletableFuture.completedFuture(1)
        val m2 = bmcref.java.util.concurrent.CompletableFuture<Int>()
        call(m2, "completeExceptionally", arrayOf(THROWABLE), RuntimeException("f")).getOrThrow()
        val rAll = java.util.concurrent.CompletableFuture.allOf(r1, r2)
        val mAll = staticCall(CF, "allOf", arrayOf(CF_ARRAY), makeRefCfArray(m1, m2)).getOrThrow()!!
        assertSameException(runCatching { rAll.join() }, call(mAll, "join", arrayOf()))
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

        // functional / bulk ops: removeIf/forEach (lambdas, FIFO), removeAll/retainAll (compact). Built
        // identically vs the JDK queue. addAll is exercised separately (capacity-sensitive) below.
        test("$label removeIf/forEach/removeAll/retainAll conform") {
            checkAll(Arb.list(Arb.int(0..6), 0..5)) { seed ->
                // removeIf / forEach via lambdas.
                run {
                    @Suppress("UNCHECKED_CAST")
                    val r = makeReal(8) as java.util.Collection<Int>
                    val m = makeModel(8)
                    for (x in seed) { r.add(x); call(m, "offer", arrayOf(OBJECT), x) }
                    val rChanged = r.removeIf { it % 2 == 0 }
                    val mChanged = call(m, "removeIf", arrayOf(refClass("java.util.function.Predicate")),
                        java.util.function.Predicate<Int> { it % 2 == 0 }).getOrThrow() as Boolean
                    mChanged shouldBe rChanged
                    val rSum = intArrayOf(0); r.forEach { rSum[0] += it }
                    val mSum = intArrayOf(0)
                    call(m, "forEach", arrayOf(refClass("java.util.function.Consumer")),
                        java.util.function.Consumer<Int> { mSum[0] += it }).getOrThrow()
                    mSum[0] shouldBe rSum[0]
                }
                // removeAll / retainAll vs a source collection.
                for (method in listOf("removeAll", "retainAll")) {
                    val r = makeReal(8); val m = makeModel(8)
                    for (x in seed) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
                    val rSrc = java.util.ArrayList<Any?>(listOf(0, 2, 4))
                    val mSrc = bmcref.java.util.ArrayList<Any?>()
                    for (x in listOf(0, 2, 4)) mSrc.add(x)
                    assertEquivalent("$method.changed",
                        call(r, method, arrayOf(java.util.Collection::class.java), rSrc),
                        call(m, method, arrayOf(bmcref.java.util.Collection::class.java), mSrc))
                    // Drain FIFO and compare.
                    repeat(9) { assertEquivalent("drain", call(r, "poll", arrayOf()), call(m, "poll", arrayOf())) }
                }
            }
        }

        // addAll enqueues via add(), honoring the LOGICAL capacity: it throws IllegalStateException when
        // the queue fills (like the JDK). Bounded ABQ at capacity 3 with a 5-element source must throw on
        // both; an unbounded/large queue accepts all.
        test("$label addAll honors logical capacity (throws when full, like the JDK)") {
            val r = makeReal(3); val m = makeModel(3)
            val rSrc = java.util.ArrayList<Any?>(listOf(1, 2, 3, 4, 5))
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in listOf(1, 2, 3, 4, 5)) mSrc.add(x)
            assertSameException(
                call(r, "addAll", arrayOf(java.util.Collection::class.java), rSrc),
                call(m, "addAll", arrayOf(bmcref.java.util.Collection::class.java), mSrc))
        }

        // stream() is a thin ListStream over the queued elements in FIFO order. count() == size, and
        // toList() yields the elements in FIFO order (order IS modeled for these queues). Some offers
        // may be rejected (bounded), so build both queues identically and compare the resulting stream.
        test("$label stream() conforms (count + FIFO toList)") {
            val v = Arb.int(0..9)
            checkAll(Arb.list(v, 0..8)) { items ->
                val r = makeReal(6)
                val m = makeModel(6)
                for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
                val rStream = call(r, "stream", arrayOf()).getOrThrow()!!
                val mStream = call(m, "stream", arrayOf()).getOrThrow()!!
                assertEquivalent("stream.count", call(rStream, "count", arrayOf()), call(mStream, "count", arrayOf()))
                val rList = (call(call(r, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow() as java.util.List<*>).toList()
                val mModelList = call(call(m, "stream", arrayOf()).getOrThrow()!!, "toList", arrayOf()).getOrThrow()!!
                val mn = call(mModelList, "size", arrayOf()).getOrThrow() as Int
                val mElems = (0 until mn).map { call(mModelList, "get", arrayOf(INT), it).getOrThrow() }
                mElems shouldBe rList   // FIFO order preserved
            }
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

    // --- ConcurrentHashMap KeySetView (keySet() / keySet(mappedValue) / newKeySet()) ----------------
    // The view returned by keySet()/keySet(mappedValue)/newKeySet() is a bounded Set snapshot of the
    // backing keys (mirroring HashMap.keySet). Differentially: the keySet() view's size + per-key
    // membership match the JDK CHM's keySet; keySet(mappedValue).add(key) installs (key, mappedValue)
    // into the backing map AND adds to the view; newKeySet() is a fresh mutable key set whose add/
    // contains/remove surface matches a real newKeySet. Iteration order isn't modeled, so membership
    // (not order) is compared.
    //
    // The JDK's ConcurrentHashMap$KeySetView resists reflective invoke of its Set methods (declaring
    // class IllegalAccessException, the Diff.publicMethod quirk), so on the REAL side we drive the view
    // through its statically-typed java.util.Set surface directly; the MODEL view is exercised
    // reflectively as usual (its relocated type is freely invokable).
    test("ConcurrentHashMap keySet() view snapshots the keys like the JDK") {
        val entry = Arb.bind(Arb.int(-3..5), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..30)) { pairs ->
            val r = java.util.concurrent.ConcurrentHashMap<Any?, Any?>()
            val m = bmcref.java.util.concurrent.ConcurrentHashMap<Any?, Any?>()
            for ((k, v) in pairs) { r.put(k, v); m.put(k, v) }
            val rKeys = r.keys
            val mKeys = call(m, "keySet", arrayOf()).getOrThrow()!!
            (call(mKeys, "size", arrayOf()).getOrThrow() as Int) shouldBe rKeys.size
            for (k in -3..5) {
                (call(mKeys, "contains", arrayOf(OBJECT), k).getOrThrow() as Boolean) shouldBe rKeys.contains(k)
            }
        }
    }

    // keySet(mappedValue): add(key) writes (key, mappedValue) THROUGH to the backing map. Add a set of
    // keys via the view on both the JDK CHM and the model, then the backing map's get(key) must equal
    // the mapped value for every added key, and the view's membership must match — pinning the
    // write-through default-mapping semantics.
    test("ConcurrentHashMap keySet(mappedValue).add writes through to the backing map like the JDK") {
        checkAll(Arb.list(Arb.int(-3..5), 0..20)) { keys ->
            val r = java.util.concurrent.ConcurrentHashMap<Int, Int>()
            val m = bmcref.java.util.concurrent.ConcurrentHashMap<Int, Int>()
            val rView = r.keySet(7)
            val mView = call(m, "keySet", arrayOf(OBJECT), 7).getOrThrow()!!
            for (k in keys) {
                (call(mView, "add", arrayOf(OBJECT), k).getOrThrow() as Boolean) shouldBe rView.add(k)
            }
            // Backing map now maps every added key to the default value 7; absent keys are null.
            for (k in -3..5) {
                assertEquivalent("backing.get($k)", runCatching { r.get(k) }, call(m, "get", arrayOf(OBJECT), k))
                (call(mView, "contains", arrayOf(OBJECT), k).getOrThrow() as Boolean) shouldBe rView.contains(k)
            }
            (call(mView, "size", arrayOf()).getOrThrow() as Int) shouldBe rView.size
        }
    }

    // newKeySet(): a fresh, mutable key set (backed by a CHM mapping keys to Boolean.TRUE). Its add/
    // contains/remove surface must match a real ConcurrentHashMap.newKeySet across an op sequence.
    test("ConcurrentHashMap.newKeySet() conforms as a mutable key set") {
        val e = Arb.int(-3..5)
        val op = Arb.choice(
            e.map { Triple("add", it, { s: java.util.concurrent.ConcurrentHashMap.KeySetView<Int, Boolean> -> s.add(it) }) },
            e.map { Triple("contains", it, { s: java.util.concurrent.ConcurrentHashMap.KeySetView<Int, Boolean> -> s.contains(it) }) },
            e.map { Triple("remove", it, { s: java.util.concurrent.ConcurrentHashMap.KeySetView<Int, Boolean> -> s.remove(it) }) },
        )
        checkAll(Arb.list(op, 0..30)) { ops ->
            val r = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
            val m = staticCall(bmcref.java.util.concurrent.ConcurrentHashMap::class.java, "newKeySet", arrayOf()).getOrThrow()!!
            ops.forEachIndexed { i, (method, arg, realOp) ->
                val rRes = realOp(r)
                val mRes = call(m, method, arrayOf(OBJECT), arg).getOrThrow()
                withClue("op[$i]=$method($arg)") { mRes shouldBe rRes }
            }
            (call(m, "size", arrayOf()).getOrThrow() as Int) shouldBe r.size
            for (k in -3..5) {
                (call(m, "contains", arrayOf(OBJECT), k).getOrThrow() as Boolean) shouldBe r.contains(k)
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

// Functional-interface arg types for reflective model calls. java.util.function.* is NOT relocated, so
// these are the REAL JDK interfaces — a plain Kotlin SAM lambda is a valid instance for both impls.
private val FUNCTION: Class<*> = java.util.function.Function::class.java
private val BIFUNCTION: Class<*> = java.util.function.BiFunction::class.java
private val BICONSUMER: Class<*> = java.util.function.BiConsumer::class.java
private val CONSUMER: Class<*> = java.util.function.Consumer::class.java
private val RUNNABLE: Class<*> = java.lang.Runnable::class.java
private val THROWABLE: Class<*> = java.lang.Throwable::class.java

// The relocated CompletableFuture type + its array (allOf/anyOf take a CompletableFuture[] vararg).
private val CF: Class<*> = bmcref.java.util.concurrent.CompletableFuture::class.java
private val CF_ARRAY: Class<*> = java.lang.reflect.Array.newInstance(CF, 0).javaClass
// The relocated CompletionStage interface — the stage combinators (thenCompose/thenCombine and the
// *Async twins) take it now (matching the real CompletableFuture surface), so reflective lookups of
// those overloads key on this type, not the concrete CompletableFuture.
private val CS: Class<*> = bmcref.java.util.concurrent.CompletionStage::class.java

/** Build a relocated-model CompletableFuture[] for the allOf/anyOf vararg parameter. */
private fun makeRefCfArray(vararg fs: bmcref.java.util.concurrent.CompletableFuture<*>): Any {
    val arr = java.lang.reflect.Array.newInstance(CF, fs.size)
    fs.forEachIndexed { i, f -> java.lang.reflect.Array.set(arr, i, f) }
    return arr
}

/** Unwrap a Result holding a relocated-model CompletableFuture and reflectively join() it. */
private fun modelJoin(result: Result<Any?>): Any? {
    val f = result.getOrThrow()!!
    return call(f, "join", arrayOf()).getOrThrow()
}

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
