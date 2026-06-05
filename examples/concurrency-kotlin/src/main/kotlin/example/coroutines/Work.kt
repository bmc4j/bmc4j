package example.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A few `suspend` functions to verify. Where one suspend fun calls another, the
 * Kotlin compiler emits a coroutine state machine with a suspension point; the
 * proofs drive these through `runBlocking { }` (completing immediately).
 */
object Work {
    // A suspend fun with no suspension point (still compiles to fun(x, Continuation)).
    suspend fun trivial(x: Int): Int = x + 1

    // Suspends into another suspend fun, producing one suspension point.
    suspend fun inner(x: Int): Int = x + 1

    suspend fun compute(x: Int): Int {
        val a = inner(x)
        return a * 2
    }

    // BUG: the result computed AFTER the suspension point is off by one. A proof
    // asserting the intended contract `(x+1)*2` catches it.
    suspend fun computeBuggy(x: Int): Int {
        val a = inner(x)
        return a * 2 + 1
    }

    // Two suspension points in sequence (state machine with labels 0, 1, 2).
    suspend fun computeTwice(x: Int): Int {
        val a = inner(x)   // x+1
        val b = inner(a)   // x+2
        return b
    }

    // A loop whose body suspends — repeated re-entry of the state machine.
    // inner(acc) = acc + 1, so after n iterations acc == n. Contract: countTo(n) == n.
    suspend fun countTo(n: Int): Int {
        var acc = 0
        var i = 0
        while (i < n) {
            acc = inner(acc)
            i++
        }
        return acc
    }

    // Uses `coroutineScope { }`; modeled as immediate drive, so the logic
    // (here (x+1)+1) is provable.
    suspend fun scoped(x: Int): Int = coroutineScope { inner(x) + 1 }

    // Uses `delay()`; modeled as a no-op (timing isn't part of a logic proof), so
    // the result is unchanged: inner(x) == x + 1.
    suspend fun delayed(x: Int): Int {
        delay(10)
        return inner(x)
    }

    // Uses `withContext(Dispatchers.IO) { }` — the dispatcher is irrelevant to the
    // logic; the model drives the block synchronously, so this is inner(x) == x + 1.
    suspend fun onIo(x: Int): Int = withContext(Dispatchers.IO) { inner(x) }

    // Uses `async { } ... await()` — the model runs the block synchronously and
    // await() returns its value, so this is inner(x) + 1 == (x+1)+1 == x+2.
    suspend fun usingAsync(x: Int): Int = coroutineScope {
        val d = async { inner(x) }
        d.await() + 1
    }

    // Uses `launch { }` — a structured scope awaits its children, so by the time the
    // scope returns the launched body has run (model: synchronously): r == x + 1.
    suspend fun launching(x: Int): Int = coroutineScope {
        var r = 0
        launch { r = inner(x) }
        r
    }
}
