package org.bmc4j.engine

import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM-wide concurrency budget for jbmc invocations, shared by EVERY proof in the test JVM.
 *
 * The Gradle plugin already runs `@BmcProof` methods concurrently on a JUnit fixed pool sized to
 * `bmc { parallelism }` / `-PbmcParallelism`. A normal proof = one method = one jbmc process, so that
 * pool alone bounds concurrent engine processes. But a `domainSplit` proof is ONE method that fans out
 * into N+1 independent derived runs; if it spawned all N+1 jbmc processes at once it would blow past the
 * cap (the 19GB-OOM lesson: a 50-slice split on a 4-wide machine must still run 4 jbmc at a time).
 *
 * This object is the single budget that reconciles both: a [Semaphore] of [permits] tickets that every
 * actual `backend.verify` (normal proof AND each split-derived run) must hold while its jbmc process is
 * alive (see [JbmcBackend.verify]). The permit count equals the configured parallelism, so the TOTAL
 * concurrent jbmc processes across the whole JVM never exceeds it, however the work is split between
 * normal proofs and a split's fan-out.
 *
 * The split coordinator thread itself holds NO permit while it waits on its fan-out futures — it does no
 * jbmc work, it only blocks on results — so a split parked on its futures never starves the budget; only
 * the leaf `verify` calls draw permits. With permits == JUnit-pool size, normal proofs (≤ pool size of
 * them ever in flight) never block on the gate; contention happens only when a split's extra runs are
 * live, which is exactly when the cap must bite.
 */
internal object JbmcConcurrency {

    /**
     * The configured jbmc parallelism, read from the JUnit fixed-pool size the plugin sets
     * (`junit.jupiter.execution.parallel.config.fixed.parallelism`). When parallel execution is off
     * (property absent — the plugin only sets it for parallelism > 1) the cap is 1, which makes a
     * split's fan-out degrade to one-at-a-time (i.e. the pre-existing sequential behavior). A
     * non-numeric or non-positive value also falls back to 1. Override for tests via
     * `-Dbmc.parallelism`.
     */
    val permits: Int = resolvePermits()

    private val semaphore = Semaphore(permits, /* fair = */ true)

    /**
     * Shared daemon thread pool that runs split-derived runs concurrently. Sized to [permits] so a
     * single split can saturate the budget but no more; daemon threads so a cancelled/early-exited
     * split never keeps the JVM alive. This pool only HOSTS the coordinating tasks — actual jbmc
     * concurrency is bounded by [semaphore], not by this pool's size, so even nested/overlapping splits
     * (different proofs fanning out at once) stay within [permits] live jbmc processes.
     */
    val fanOutPool = Executors.newFixedThreadPool(maxOf(permits, 1), object : ThreadFactory {
        private val n = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            val t = Thread(r, "bmc4j-split-fanout-" + n.incrementAndGet())
            t.isDaemon = true
            return t
        }
    })

    /** Run [block] holding one jbmc permit; blocks until a permit is free, releases it on exit. */
    fun <T> withPermit(block: () -> T): T {
        semaphore.acquire()
        try {
            return block()
        } finally {
            semaphore.release()
        }
    }

    private fun resolvePermits(): Int =
            resolvePermits(System.getProperty("bmc.parallelism"),
                    System.getProperty("junit.jupiter.execution.parallel.config.fixed.parallelism"))

    /**
     * Pure permit-count resolution (exposed for unit tests). An explicit `-Dbmc.parallelism` wins;
     * else the JUnit fixed-pool size the plugin sets for parallel runs; else 1 (the plugin omits the
     * pool size for serial runs, so the absent case degrades a split's fan-out to one-at-a-time). A
     * blank, non-numeric or non-positive value is ignored at each step.
     */
    internal fun resolvePermits(parallelismProp: String?, junitPoolProp: String?): Int {
        val override = parsePositiveInt(parallelismProp)
        if (override != null) {
            return override
        }
        return parsePositiveInt(junitPoolProp) ?: 1
    }

    private fun parsePositiveInt(raw: String?): Int? {
        val s = raw?.trim()
        if (s.isNullOrEmpty()) {
            return null
        }
        val v = s.toIntOrNull() ?: return null
        return if (v >= 1) v else null
    }
}
