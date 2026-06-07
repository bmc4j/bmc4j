package org.bmc4j.coroutines

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The compile-visible driver a generated `suspend`-contract **enforce-proof** uses to run the real
 * suspend body to completion. A suspend function is lowered to `(args, Continuation)Object`; the
 * enforce-proof — generated Java that cannot open a Kotlin `runBlocking { }` block — calls that lowered
 * signature directly with [complete] and unboxes the returned result.
 *
 * Why this lives here (not only in the bundled `kotlinx.coroutines` model): the enforce-proof must
 * **compile** against a real symbol on the consumer's classpath, and the bundled model `BuildersKt`
 * is on JBMC's *analysis* classpath only. This driver ships in `bmc-runtime` (already a consumer
 * dependency), so the generated code compiles, and JBMC analyses its trivial real bytecode — an
 * immediately-completing continuation whose `resumeWith` is a no-op. That is exactly the
 * **immediate-dispatch idealization**: every nested suspension point completes synchronously, so the
 * suspend call returns its boxed declared result in one call (never `COROUTINE_SUSPENDED`), and this
 * top-level completion is never actually resumed. Concurrency/timing/interleaving are out of scope
 * (the same stance the `runBlocking { }` proofs take); use Lincheck for those.
 */
object BmcSuspend {

    /** An immediately-completing [Continuation] for driving one suspend call to completion. */
    @JvmStatic
    fun complete(): Continuation<Any?> = Completion

    private object Completion : Continuation<Any?> {
        override val context: CoroutineContext
            get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Any?>) {
            // immediate dispatch: nothing to resume
        }
    }
}
