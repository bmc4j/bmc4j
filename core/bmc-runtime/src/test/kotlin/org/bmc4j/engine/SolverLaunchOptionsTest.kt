package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for the `@SolverLaunchOptions` passthrough to the EXTERNAL SAT solver.
 *
 * The options reach the external-sat command via a wrapper script (CBMC runs the
 * `--external-sat-solver` value as a single executable, so the options can't be embedded in the value
 * directly — see [SolverWrapper]). They are part of the verdict-cache identity ([VerdictCache.computeKey])
 * so setting or changing them forces a fresh run, the empty default keys identically to no annotation,
 * and they are inert (warn, no-op) when no external solver is in play.
 */
internal class SolverLaunchOptionsTest {

    /** A request on the external SAT path (non-empty externalSatPath) with the given launch options. */
    private fun externalSatRequest(opts: String): BmcRequest =
            BmcRequest("pkg.C", "pkg.C.proof", "/cp", 16, true, 16, "", 0,
                    externalSatPath = "/path/to/kissat", solverLaunchOptions = opts)

    /** A request NOT on the external SAT path (default solver) with the given launch options. */
    private fun defaultSolverRequest(opts: String): BmcRequest =
            BmcRequest("pkg.C", "pkg.C.proof", "/cp", 16, true, 16, "", 0,
                    solverLaunchOptions = opts)

    // --- the options reach the external-sat command -----------------------------------------------

    @Test
    fun args_externalSat_withOptions_emitsWrapperContainingOptions() {
        // The wrapper mechanism is POSIX-only (CBMC's CreateProcessW can't launch a .bat wrapper, and the
        // fast solver is never bundled on Windows). On Windows the value falls back to the bare path.
        assumeTrue(!System.getProperty("os.name", "").lowercase().contains("win"),
                "wrapper-script mechanism is POSIX-only")
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "/usr/bin/kissat",
                org.bmc4j.StringMode.CHAR_ARRAY_MODEL, "", emptyMap(), "--shrink --walkinitially")
        val i = a.indexOf("--external-sat-solver")
        assertTrue(i >= 0, "an external SAT solver must be selected: $a")
        val value = a[i + 1]
        assertNotEquals("/usr/bin/kissat", value,
                "with options set, the value must be a wrapper, not the bare solver path: $value")
        val wrapper = File(value)
        assertTrue(wrapper.isFile, "the wrapper script must exist on disk: $value")
        val body = wrapper.readText()
        assertTrue(body.contains("/usr/bin/kissat"), "wrapper must exec the real solver: $body")
        assertTrue(body.contains("--shrink") && body.contains("--walkinitially"),
                "wrapper must splice in every @SolverLaunchOptions token: $body")
        // CBMC appends only the DIMACS path, which the wrapper forwards via "$@".
        assertTrue(body.contains("\"$@\""), "wrapper must forward CBMC's appended DIMACS path: $body")
    }

    @Test
    fun args_externalSat_noOptions_usesBareSolverPath() {
        val a = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "/usr/bin/kissat",
                org.bmc4j.StringMode.CHAR_ARRAY_MODEL, "", emptyMap(), "")
        val i = a.indexOf("--external-sat-solver")
        assertTrue(i >= 0, "an external SAT solver must be selected: $a")
        assertEquals("/usr/bin/kissat", a[i + 1],
                "with no @SolverLaunchOptions the bare solver path is used (no wrapper)")
    }

    // --- inert / no-op when no external solver is selected ----------------------------------------

    @Test
    fun args_noExternalSolver_optionsAreInert() {
        // Default solver path (no external sat): the options must not leak into the argv as bogus flags
        // and must not select any solver — they are a no-op (the driver warns separately).
        val withOpts = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT, "", emptyMap(), "--shrink")
        val noOpts = Jbmc.args("pkg.T", "pkg.T.proof", "/cp", 1, true, 16, null, "",
                org.bmc4j.StringMode.REFINEMENT, "", emptyMap(), "")
        assertEquals(noOpts, withOpts,
                "@SolverLaunchOptions must not change the command when no external solver is in use")
        assertFalse(withOpts.contains("--shrink"),
                "a solver option must never be spliced into the jbmc command on the default solver path")
        assertFalse(withOpts.contains("--external-sat-solver"),
                "@SolverLaunchOptions must not conjure an external solver that wasn't selected")
    }

    // --- verdict-cache identity -------------------------------------------------------------------

    @Test
    fun cacheKey_absentOptions_keysIdenticallyToNoAnnotation() {
        val base = BmcRequest("pkg.C", "pkg.C.proof", "/cp", 16, true, 16, "", 0,
                externalSatPath = "/path/to/kissat")
        assertEquals(VerdictCache.computeKey(base, "engine-id"),
                VerdictCache.computeKey(externalSatRequest(""), "engine-id"),
                "an absent @SolverLaunchOptions must not perturb the verdict-cache key")
    }

    @Test
    fun cacheKey_changesWhenOptionsSet() {
        assertNotEquals(VerdictCache.computeKey(externalSatRequest(""), "engine-id"),
                VerdictCache.computeKey(externalSatRequest("--shrink"), "engine-id"),
                "setting @SolverLaunchOptions must bust the verdict cache (force a fresh engine run)")
    }

    @Test
    fun cacheKey_changesWhenOptionsChange() {
        assertNotEquals(VerdictCache.computeKey(externalSatRequest("--shrink"), "engine-id"),
                VerdictCache.computeKey(externalSatRequest("--no-shrink"), "engine-id"),
                "changing @SolverLaunchOptions must bust the verdict cache")
    }

    @Test
    fun cacheKey_usesStableOptionsMarker_notVolatileWrapperPath() {
        // The cache key must key on the STABLE solver-path+options, never on the per-machine wrapper path
        // (which would churn the cache). So the flag SIGNATURE carries --bmc-solver-launch-options <opts>.
        val sig = Jbmc.verdictRelevantFlags(externalSatRequest("--shrink"))
        assertTrue(sig.contains("--bmc-solver-launch-options --shrink"),
                "the cache-key signature must carry a stable solver-launch-options marker: $sig")
        assertFalse(sig.contains("solver-wrapper"),
                "the cache-key signature must NOT contain a volatile wrapper-script path: $sig")
    }

    @Test
    fun cacheKey_changesWhenOptionsChange_onCacheKeyEvenWithoutAnExternalSatRun() {
        // Belt-and-suspenders: even setting the options on a request that isn't on the external-sat path
        // must bust the cache (the per-field update fires regardless), so a user who flips solvers later
        // never replays a stale verdict computed under different options.
        assertNotEquals(VerdictCache.computeKey(defaultSolverRequest(""), "engine-id"),
                VerdictCache.computeKey(defaultSolverRequest("--shrink"), "engine-id"),
                "setting @SolverLaunchOptions must bust the cache even off the external-sat path")
    }

    // --- the wrapper helper itself ----------------------------------------------------------------

    @Test
    fun solverWrapper_execsSolverWithOptionsThenForwardsArgs() {
        assumeTrue(SolverWrapper.supportedOnThisPlatform(), "wrapper-script mechanism is POSIX-only")
        val path = SolverWrapper.forSolver("/usr/bin/kissat", "--shrink")
        val body = File(path).readText()
        assertTrue(body.startsWith("#!/bin/sh"), "wrapper must be a POSIX shell script: $body")
        assertTrue(body.contains("exec '/usr/bin/kissat' --shrink \"$@\""),
                "wrapper must exec the solver with options ahead of CBMC's appended args: $body")
        // Same (solver, options) resolves to the same wrapper file (deterministic, not a fresh temp).
        assertEquals(path, SolverWrapper.forSolver("/usr/bin/kissat", "--shrink"),
                "the wrapper for a given (solver, options) must be reused, not regenerated")
    }
}
