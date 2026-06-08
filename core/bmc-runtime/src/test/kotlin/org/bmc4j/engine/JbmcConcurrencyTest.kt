package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JbmcConcurrency]'s permit-count resolution — the JVM-wide cap on concurrent jbmc
 * processes that bounds a `domainSplit` proof's fan-out to the configured parallelism. The cap is read
 * from the same JUnit fixed-pool size the Gradle plugin sets for parallel proof execution (or an
 * explicit `-Dbmc.parallelism` override), so a split's N+1 derived runs never spawn more jbmc processes
 * than a normal parallel run would.
 */
class JbmcConcurrencyTest {

    @Test
    fun `explicit bmc_parallelism override wins`() {
        assertEquals(3, JbmcConcurrency.resolvePermits("3", "8"),
                "an explicit -Dbmc.parallelism wins over the JUnit pool size")
    }

    @Test
    fun `falls back to the junit fixed pool size`() {
        assertEquals(4, JbmcConcurrency.resolvePermits(null, "4"),
                "with no override, the cap is the JUnit fixed-pool size the plugin sets")
    }

    @Test
    fun `serial run (no pool size) caps at one`() {
        // The plugin only sets the fixed-pool size for parallelism > 1; absent => serial => cap 1,
        // which degrades a split's fan-out to one-at-a-time (the pre-existing sequential behavior).
        assertEquals(1, JbmcConcurrency.resolvePermits(null, null))
    }

    @Test
    fun `blank, non-numeric and non-positive values are ignored`() {
        assertEquals(1, JbmcConcurrency.resolvePermits("", ""))
        assertEquals(5, JbmcConcurrency.resolvePermits("not-a-number", "5"))
        assertEquals(2, JbmcConcurrency.resolvePermits("0", "2"))
        assertEquals(1, JbmcConcurrency.resolvePermits("-4", "0"))
    }
}
