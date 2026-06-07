package proofs.coroutines

import example.coroutines.Work
import kotlinx.coroutines.runBlocking
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Shard
import org.bmc4j.Verdict

/**
 * Coroutine proofs, written the way Kotlin coroutine code is normally tested:
 * the whole test body runs inside `runBlocking { }`, so suspend functions are
 * called directly and `Bmc.check` reads naturally. JBMC analyzes a clean bundled
 * model of `runBlocking` (and the coroutine runtime) — no driver, no real
 * dispatcher/event-loop machinery.
 */
class CoroutineProofTests {

    // PASSES: a suspend fun with no suspension point.
    // This class holds seven ~75-80s proofs; pin them across the shards so they don't hash-cluster.
    @Shard(1)
    @BmcProof
    fun trivial_adds_one() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.trivial(x) == x + 1)
    }

    // PASSES: one real suspension point (compute -> inner).
    @BmcProof
    fun compute_doubles_plus_one() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.compute(x) == (x + 1) * 2)
    }

    // PASSES: two suspension points in sequence.
    @BmcProof
    fun computeTwice_adds_two() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.computeTwice(x) == x + 2)
    }

    // PASSES: a loop whose body suspends. Bound n so the loop is decidable.
    @Shard(2)
    @BmcProof(unwind = 8)
    fun countTo_returns_n() = runBlocking {
        val n = Bmc.anyInt(0, 5)
        Bmc.check(Work.countTo(n) == n)
    }

    // FAILS: catches a logic bug that manifests after the suspension point.
    // Expected verdict: REFUTED - the seeded coroutine bug breaks the contract.
    @Shard(3)
    @BmcProof(expect = Verdict.REFUTED)
    fun computeBuggy_violates_contract() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.computeBuggy(x) == (x + 1) * 2)
    }

    // PASSES: logic through `coroutineScope { }` (modeled as immediate drive).
    @Shard(1)
    @BmcProof
    fun scoped_adds_two() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.scoped(x) == x + 2)
    }

    // PASSES: `delay()` is a no-op for logic, so the result is unchanged.
    @Shard(2)
    @BmcProof
    fun delayed_returns_x_plus_one() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.delayed(x) == x + 1)
    }

    // PASSES: logic through `withContext(Dispatchers.IO) { }` (dispatcher ignored).
    @BmcProof
    fun onIo_adds_one() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.onIo(x) == x + 1)
    }

    // PASSES: logic through `async { } ... await()` (modeled as immediate result).
    @Shard(3)
    @BmcProof
    fun usingAsync_adds_two() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.usingAsync(x) == x + 2)
    }

    // PASSES: logic through `launch { }` in a structured scope (runs before return).
    @Shard(1)
    @BmcProof
    fun launching_adds_one() = runBlocking {
        val x = Bmc.anyInt(0, 1000)
        Bmc.check(Work.launching(x) == x + 1)
    }
}
