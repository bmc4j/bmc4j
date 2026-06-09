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
            // Number narrowing inherited from Number: byteValue/shortValue truncate the stored int.
            assertEquivalent("byteValue", call(r, "byteValue", arrayOf()), call(m, "byteValue", arrayOf()))
            assertEquivalent("shortValue", call(r, "shortValue", arrayOf()), call(m, "shortValue", arrayOf()))
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
            // Number narrowing inherited from Number: byteValue/shortValue truncate the stored long.
            assertEquivalent("byteValue", call(r, "byteValue", arrayOf()), call(m, "byteValue", arrayOf()))
            assertEquivalent("shortValue", call(r, "shortValue", arrayOf()), call(m, "shortValue", arrayOf()))
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

        // Timed offer/poll modeled as a finite TWO-OUTCOME state machine (BMC has no wall-clock, so the
        // timeout duration is dropped): timed offer enqueues when there is room (true) and rejects when
        // full (false); timed poll dequeues the head when non-empty and returns null when empty — exactly
        // the non-blocking offer()/poll() outcomes the JDK timed forms also produce here (the timeout=0
        // probe). TimeUnit IS relocated, so the real call takes the JDK enum and the model call takes the
        // relocated enum constant (timedOp* picks the right argTypes per side).
        test("$label timed offer/poll are the two-outcome state machine (timeout dropped)") {
            // op kinds: 0=timedOffer(x), 1=timedPoll, 2=size
            val op = Arb.choice(
                Arb.int(0..9).map { 0 to it }, Arb.constant(1 to 0), Arb.constant(2 to 0),
            )
            checkAll(Arb.list(op, 0..30)) { ops ->
                val r = makeReal(3)   // bounded so timed offer's "full -> false" outcome is exercised
                val m = makeModel(3)
                ops.forEachIndexed { i, (kind, x) ->
                    when (kind) {
                        0 -> assertEquivalent("op[$i]=timedOffer($x)",
                            call(r, "offer", arrayOf(OBJECT, LONG, TIMEUNIT), x, 0L, REAL_MILLIS),
                            call(m, "offer", arrayOf(OBJECT, LONG, MODEL_TIMEUNIT), x, 0L, MODEL_MILLIS))
                        1 -> assertEquivalent("op[$i]=timedPoll",
                            call(r, "poll", arrayOf(LONG, TIMEUNIT), 0L, REAL_MILLIS),
                            call(m, "poll", arrayOf(LONG, MODEL_TIMEUNIT), 0L, MODEL_MILLIS))
                        else -> assertEquivalent("op[$i]=size", call(r, "size", arrayOf()), call(m, "size", arrayOf()))
                    }
                }
                repeat(4) { assertEquivalent("drain", call(r, "poll", arrayOf()), call(m, "poll", arrayOf())) }
            }
        }

        // Bounded drainTo(Collection, max): a fully-sequential transfer of at most `max` elements in FIFO
        // order into a sink collection. Compare the returned count, the queue's residual FIFO, and the
        // drained contents. drainTo(Collection) (drain-all) is exercised implicitly by the full drain.
        test("$label drainTo(Collection, max) moves at most max in FIFO order") {
            checkAll(Arb.list(Arb.int(0..9), 0..6), Arb.int(0..7)) { items, max ->
                val r = makeReal(8); val m = makeModel(8)
                for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }
                val rSink = java.util.ArrayList<Any?>()
                val mSink = bmcref.java.util.ArrayList<Any?>()
                assertEquivalent("drainTo.count",
                    call(r, "drainTo", arrayOf(java.util.Collection::class.java, INT), rSink, max),
                    call(m, "drainTo", arrayOf(bmcref.java.util.Collection::class.java, INT), mSink, max))
                // The sink got the same FIFO prefix.
                val mn = call(mSink, "size", arrayOf()).getOrThrow() as Int
                val mElems = (0 until mn).map { call(mSink, "get", arrayOf(INT), it).getOrThrow() }
                mElems shouldBe rSink.toList()
                // And the residual queue drains identically.
                repeat(7) { assertEquivalent("residual", call(r, "poll", arrayOf()), call(m, "poll", arrayOf())) }
            }
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

    // --- ConcurrentHashMap legacy keys()/elements() Enumerations -----------------------------------
    // The Hashtable-style enumerations walk a bounded snapshot of the keys/values. Iteration ORDER is
    // not part of the CHM contract, so we compare the enumerated MULTISET (drained via hasMoreElements/
    // nextElement) against the JDK's, plus the count. Driven on both the JDK CHM and the model.
    test("ConcurrentHashMap keys()/elements() enumerate the same multiset as the JDK") {
        val entry = Arb.bind(Arb.int(-3..5), Arb.int(-9..9)) { k, v -> k to v }
        checkAll(Arb.list(entry, 0..20)) { pairs ->
            val r = java.util.concurrent.ConcurrentHashMap<Int, Int>()
            val m = bmcref.java.util.concurrent.ConcurrentHashMap<Int, Int>()
            for ((k, v) in pairs) { r.put(k, v); m.put(k, v) }
            fun drain(e: Any): List<Any?> {
                val out = ArrayList<Any?>()
                while (call(e, "hasMoreElements", arrayOf()).getOrThrow() as Boolean) {
                    out.add(call(e, "nextElement", arrayOf()).getOrThrow())
                }
                return out
            }
            // keys()
            val rKeys = drain(r.keys())
            val mKeys = drain(call(m, "keys", arrayOf()).getOrThrow()!!)
            mKeys.sortedBy { (it as Int) } shouldBe rKeys.sortedBy { (it as Int) }
            // elements() (values)
            val rVals = drain(r.elements())
            val mVals = drain(call(m, "elements", arrayOf()).getOrThrow()!!)
            mVals.sortedBy { (it as Int) } shouldBe rVals.sortedBy { (it as Int) }
        }
    }

    // --- Semaphore extended surface (n-permit uninterruptible/timed, reducePermits) ----------------
    // acquireUninterruptibly(n) is assume-prune (proof axis); here we exercise the SOUND non-blocking
    // surface: tryAcquire(n, timeout, unit) (the timeout-dropped two-outcome probe) and reducePermits(n)
    // (protected — drives the permit count down, may go negative like the JDK). Compared via subclasses
    // that expose reducePermits publicly so the same op sequence runs on both.
    test("Semaphore tryAcquire(n, timeout, unit) is the timed two-outcome probe") {
        // op kinds: 0=tryAcquire(k, t), 1=release(k), 2=availablePermits. TimeUnit IS relocated, so the
        // real and model calls take their respective enum types.
        val op = Arb.choice(
            Arb.int(0..4).map { 0 to it }, Arb.int(0..4).map { 1 to it }, Arb.constant(2 to 0),
        )
        checkAll(Arb.int(0..4), Arb.list(op, 0..20)) { init, ops ->
            val r = java.util.concurrent.Semaphore(init)
            val m = bmcref.java.util.concurrent.Semaphore(init)
            ops.forEachIndexed { i, (kind, k) ->
                when (kind) {
                    0 -> assertEquivalent("op[$i]=tryAcquire($k,t)",
                        call(r, "tryAcquire", arrayOf(INT, LONG, TIMEUNIT), k, 0L, REAL_MILLIS),
                        call(m, "tryAcquire", arrayOf(INT, LONG, MODEL_TIMEUNIT), k, 0L, MODEL_MILLIS))
                    1 -> assertEquivalent("op[$i]=release($k)", call(r, "release", arrayOf(INT), k), call(m, "release", arrayOf(INT), k))
                    else -> assertEquivalent("op[$i]=availablePermits", call(r, "availablePermits", arrayOf()), call(m, "availablePermits", arrayOf()))
                }
            }
            assertEquivalent("availablePermits", call(r, "availablePermits", arrayOf()), call(m, "availablePermits", arrayOf()))
        }
    }

    test("Semaphore reducePermits(n) drives the count down (may go negative) like the JDK") {
        checkAll(Arb.int(0..6), Arb.list(Arb.int(0..3), 0..6)) { init, reductions ->
            val r = ReducibleReal(init)
            val m = bmcref.java.util.concurrent.Semaphore(init)  // exercised via reflective protected call
            for (k in reductions) {
                r.reduce(k)
                // reducePermits is protected on both; invoke via the declared method (publicMethod
                // resolves the model's relocated type, which exposes it for reflection).
                bmcref.java.util.concurrent.Semaphore::class.java
                    .getDeclaredMethod("reducePermits", INT).also { it.isAccessible = true }.invoke(m, k)
            }
            (m.availablePermits()) shouldBe r.availablePermits()
        }
    }

    // --- CompletableFuture either/both combinators + exceptionallyCompose (immediate semantics) ----
    // Either-combinators complete on the receiver (deterministic sequential winner); both-combinators
    // need both ready and short-circuit on either exceptional source; exceptionallyCompose flattens a
    // recovery stage on the failure path. The real JDK CompletableFuture is the differential oracle.
    test("CompletableFuture either/both combinators conform (sequential winner = receiver)") {
        checkAll(Arb.int(0..9), Arb.int(0..9)) { a, b ->
            val r = java.util.concurrent.CompletableFuture.completedFuture(a)
            val rOther = java.util.concurrent.CompletableFuture.completedFuture(b)
            val m = bmcref.java.util.concurrent.CompletableFuture.completedFuture(a)
            val mOther = bmcref.java.util.concurrent.CompletableFuture.completedFuture(b)

            // applyToEither: applies fn to the receiver's value.
            val apply = java.util.function.Function<Int, Int> { it + 1 }
            r.applyToEither(rOther, apply).join() shouldBe
                modelJoin(call(m, "applyToEither", arrayOf(CS, FUNCTION), mOther, apply))

            // acceptEither: side-effect on the receiver's value; result is null Void.
            val accept = java.util.function.Consumer<Int> { }
            r.acceptEither(rOther, accept).join() shouldBe
                modelJoin(call(m, "acceptEither", arrayOf(CS, CONSUMER), mOther, accept))

            // runAfterEither / runAfterBoth: run a Runnable; result is null Void.
            val run = Runnable { }
            r.runAfterEither(rOther, run).join() shouldBe
                modelJoin(call(m, "runAfterEither", arrayOf(CS, RUNNABLE), mOther, run))
            r.runAfterBoth(rOther, run).join() shouldBe
                modelJoin(call(m, "runAfterBoth", arrayOf(CS, RUNNABLE), mOther, run))

            // thenAcceptBoth: consume both values; result is null Void.
            val both = java.util.function.BiConsumer<Int, Int> { _, _ -> }
            r.thenAcceptBoth(rOther, both).join() shouldBe
                modelJoin(call(m, "thenAcceptBoth", arrayOf(CS, BICONSUMER), mOther, both))
        }
    }

    test("CompletableFuture exceptionallyCompose recovers on the failure path; passthrough on normal") {
        // failure path: flatten a recovery future built from the cause.
        val rf = java.util.concurrent.CompletableFuture<Int>().also { it.completeExceptionally(RuntimeException("x")) }
        val mf = bmcref.java.util.concurrent.CompletableFuture<Int>()
        call(mf, "completeExceptionally", arrayOf(THROWABLE), RuntimeException("x")).getOrThrow()
        val rComposed = rf.exceptionallyCompose { java.util.concurrent.CompletableFuture.completedFuture(-1) }.join()
        val mCompose = java.util.function.Function<Throwable, Any?> { bmcref.java.util.concurrent.CompletableFuture.completedFuture(-1) }
        modelJoin(call(mf, "exceptionallyCompose", arrayOf(FUNCTION), mCompose)) shouldBe rComposed

        // normal path: value passes through unchanged (fn not invoked).
        val rOk = java.util.concurrent.CompletableFuture.completedFuture(7)
        val mOk = bmcref.java.util.concurrent.CompletableFuture.completedFuture(7)
        val rPass = rOk.exceptionallyCompose { java.util.concurrent.CompletableFuture.completedFuture(-1) }.join()
        modelJoin(call(mOk, "exceptionallyCompose", arrayOf(FUNCTION), mCompose)) shouldBe rPass
    }

    // --- CopyOnWriteArrayList set-add + from-index search ------------------------------------------
    // addIfAbsent appends only when not already present (by equals); addAllAbsent appends each new
    // element (treating earlier additions as present), returning the count added; indexOf/lastIndexOf
    // (fromIndex) search a window. Driven against a real COW list, comparing return values + the final
    // contents in order.
    test("CopyOnWriteArrayList addIfAbsent/addAllAbsent/indexOf(from)/lastIndexOf(from) conform") {
        checkAll(Arb.list(Arb.int(0..4), 0..8)) { items ->
            val r = java.util.concurrent.CopyOnWriteArrayList<Int>()
            val m = bmcref.java.util.concurrent.CopyOnWriteArrayList<Int>()
            for (x in items) {
                (call(m, "addIfAbsent", arrayOf(OBJECT), x).getOrThrow() as Boolean) shouldBe r.addIfAbsent(x)
            }
            // addAllAbsent with a source that overlaps the current contents.
            val rSrc = java.util.ArrayList<Any?>(listOf(0, 1, 2, 9))
            val mSrc = bmcref.java.util.ArrayList<Any?>()
            for (x in listOf(0, 1, 2, 9)) mSrc.add(x)
            assertEquivalent("addAllAbsent",
                call(r, "addAllAbsent", arrayOf(java.util.Collection::class.java), rSrc),
                call(m, "addAllAbsent", arrayOf(bmcref.java.util.Collection::class.java), mSrc))
            // from-index searches across the window.
            for (target in 0..4) {
                for (from in 0..r.size) {
                    assertEquivalent("indexOf($target,$from)",
                        runCatching { r.indexOf(target, from) },
                        call(m, "indexOf", arrayOf(OBJECT, INT), target, from))
                }
                if (r.isNotEmpty()) {
                    assertEquivalent("lastIndexOf($target,${r.size - 1})",
                        runCatching { r.lastIndexOf(target, r.size - 1) },
                        call(m, "lastIndexOf", arrayOf(OBJECT, INT), target, r.size - 1))
                }
            }
            // final contents agree in order.
            val mn = call(m, "size", arrayOf()).getOrThrow() as Int
            (0 until mn).map { call(m, "get", arrayOf(INT), it).getOrThrow() } shouldBe r.toList()
        }
    }

    // --- CopyOnWriteArrayList.toArray(IntFunction) snapshot ----------------------------------------
    // The generator-array snapshot mirrors the JDK in index order. Build both lists identically and
    // compare the resulting array contents.
    test("CopyOnWriteArrayList.toArray(IntFunction) snapshots in index order") {
        checkAll(Arb.list(Arb.int(0..9), 0..8)) { items ->
            val r = java.util.concurrent.CopyOnWriteArrayList<Int>()
            val m = bmcref.java.util.concurrent.CopyOnWriteArrayList<Int>()
            for (x in items) { r.add(x); call(m, "add", arrayOf(OBJECT), x) }
            val gen = java.util.function.IntFunction<Array<Any?>> { arrayOfNulls(it) }
            val rArr = (call(r, "toArray", arrayOf(refClass("java.util.function.IntFunction")), gen).getOrThrow() as Array<*>).toList()
            val mArr = (call(m, "toArray", arrayOf(refClass("java.util.function.IntFunction")), gen).getOrThrow() as Array<*>).toList()
            mArr shouldBe rArr
        }
    }

    // --- CompletableFuture ready-value / ready-failure constructions (sequential plumbing) ---------
    // failedFuture/failedStage build a ready failure; copy mirrors a settled completion;
    // newIncompleteFuture is a fresh pending future; minimalCompletionStage is the settled stage view.
    // The real JDK CompletableFuture is the differential oracle on one thread.
    test("CompletableFuture failedFuture/copy/newIncompleteFuture/minimalCompletionStage conform") {
        checkAll(Arb.int(0..9)) { v ->
            // failedFuture: completed-exceptionally; exceptionally recovers to the same value on both.
            val rf = java.util.concurrent.CompletableFuture.failedFuture<Int>(RuntimeException("x"))
            val mf = staticCall(CF, "failedFuture", arrayOf(THROWABLE), RuntimeException("x")).getOrThrow()!!
            rf.isCompletedExceptionally shouldBe call(mf, "isCompletedExceptionally", arrayOf()).getOrThrow()
            val recover = java.util.function.Function<Throwable, Int> { v + 1 }
            rf.exceptionally(recover).join() shouldBe modelJoin(call(mf, "exceptionally", arrayOf(FUNCTION), recover))

            // failedStage: realized through toCompletableFuture it is exceptional too.
            val ms = staticCall(CF, "failedStage", arrayOf(THROWABLE), RuntimeException("x")).getOrThrow()!!
            (call(call(ms, "toCompletableFuture", arrayOf()).getOrThrow()!!, "isCompletedExceptionally", arrayOf()).getOrThrow()) shouldBe true

            // copy of a completed future carries the value and is not exceptional.
            val rc = java.util.concurrent.CompletableFuture.completedFuture(v)
            val mc = bmcref.java.util.concurrent.CompletableFuture.completedFuture(v)
            rc.copy().join() shouldBe modelJoin(call(mc, "copy", arrayOf()))
            rc.copy().isCompletedExceptionally shouldBe call(call(mc, "copy", arrayOf()).getOrThrow()!!, "isCompletedExceptionally", arrayOf()).getOrThrow()

            // newIncompleteFuture is fresh + pending, then completes to a value.
            val rn = rc.newIncompleteFuture<Int>()
            val mn = call(mc, "newIncompleteFuture", arrayOf()).getOrThrow()!!
            rn.isDone shouldBe call(mn, "isDone", arrayOf()).getOrThrow()
            rn.complete(v) shouldBe call(mn, "complete", arrayOf(OBJECT), v).getOrThrow()
            rn.join() shouldBe call(mn, "join", arrayOf()).getOrThrow()

            // minimalCompletionStage carries the value through a stage combinator.
            val fn = java.util.function.Function<Int, Int> { it + 2 }
            rc.minimalCompletionStage().thenApply(fn).toCompletableFuture().join() shouldBe
                modelJoin(call(call(mc, "minimalCompletionStage", arrayOf()).getOrThrow()!!,
                    "thenApply", arrayOf(FUNCTION), fn).let { call(it.getOrThrow()!!, "toCompletableFuture", arrayOf()) })
        }
    }

    // --- BlockingQueue toArray snapshots (FIFO) ----------------------------------------------------
    // toArray()/toArray(T[])/toArray(IntFunction) snapshot the queued elements in FIFO order. Driven on
    // both impls vs the JDK queue.
    for ((label, makeReal, makeModel) in blockingQueueFactories()) {
        test("$label toArray()/toArray(T[])/toArray(IntFunction) snapshot in FIFO order") {
            checkAll(Arb.list(Arb.int(0..9), 0..6)) { items ->
                val r = makeReal(8)
                val m = makeModel(8)
                for (x in items) { call(r, "offer", arrayOf(OBJECT), x); call(m, "offer", arrayOf(OBJECT), x) }

                val objArrayClass = arrayOfNulls<Any?>(0).javaClass
                val rArr = (call(r, "toArray", arrayOf()).getOrThrow() as Array<*>).toList()
                val mArr = (call(m, "toArray", arrayOf()).getOrThrow() as Array<*>).toList()
                mArr shouldBe rArr

                val rTyped = (call(r, "toArray", arrayOf(objArrayClass), arrayOfNulls<Any?>(0)).getOrThrow() as Array<*>).toList()
                val mTyped = (call(m, "toArray", arrayOf(objArrayClass), arrayOfNulls<Any?>(0)).getOrThrow() as Array<*>).toList()
                mTyped shouldBe rTyped

                val gen = java.util.function.IntFunction<Array<Any?>> { arrayOfNulls(it) }
                val rGen = (call(r, "toArray", arrayOf(refClass("java.util.function.IntFunction")), gen).getOrThrow() as Array<*>).toList()
                val mGen = (call(m, "toArray", arrayOf(refClass("java.util.function.IntFunction")), gen).getOrThrow() as Array<*>).toList()
                mGen shouldBe rGen
            }
        }
    }
})

/** A real Semaphore subclass exposing the protected reducePermits, for the differential reduce test. */
private class ReducibleReal(permits: Int) : java.util.concurrent.Semaphore(permits) {
    fun reduce(n: Int) = reducePermits(n)
}

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
// TimeUnit IS relocated (bmcref.java.util.concurrent.TimeUnit): the model's timed-op overloads take the
// relocated enum, the JDK's take the real one. The duration is dropped either way; we just need each
// side's matching enum type + constant for the reflective lookup to resolve.
private val TIMEUNIT: Class<*> = java.util.concurrent.TimeUnit::class.java
private val MODEL_TIMEUNIT: Class<*> = bmcref.java.util.concurrent.TimeUnit::class.java
private val REAL_MILLIS: Any = java.util.concurrent.TimeUnit.MILLISECONDS
private val MODEL_MILLIS: Any = bmcref.java.util.concurrent.TimeUnit.MILLISECONDS

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
