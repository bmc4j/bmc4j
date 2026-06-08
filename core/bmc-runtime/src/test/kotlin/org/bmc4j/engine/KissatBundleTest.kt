package org.bmc4j.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pins the *bundled-but-unused* contract for the bundled KISSAT SAT solver.
 *
 * KISSAT is shipped inside each `bmc-engine-<platform>` jar (a `files.txt` entry) and, when
 * present, [BundledEngine.extract] unpacks it into the engine cache next to jbmc. This test
 * asserts two things:
 *  1. **Discoverable:** when a kissat binary is staged in the engine cache, [BundledEngine.kissatPath]
 *     returns its path (integrity-by-construction: it came from the verified jar); when nothing is
 *     staged it returns `null`.
 *  2. **Unreferenced by the run path:** the jbmc command builder ([Jbmc.args]) never mentions the
 *     bundled kissat path. The bundled solver is shipped but unused — nothing in the invocation path
 *     consults [BundledEngine.kissatPath]. External-SAT routing remains driven *solely* by the
 *     `bmc.externalSat` property, which is unset here, so no `--external-sat-solver` flag appears.
 *
 * Hermetic: like [BundledEngineTest], `user.home` points at a [TempDir] so the developer's real
 * `~/.cache/bmc4j` is untouched, and a *fake* kissat file is staged (no real binary required).
 */
internal class KissatBundleTest {

    private var savedUserHome: String? = null

    @BeforeEach
    fun redirectCacheToTempHome(@TempDir fakeHome: Path) {
        savedUserHome = System.getProperty("user.home")
        System.setProperty("user.home", fakeHome.toString())
    }

    @AfterEach
    fun restoreUserHome() {
        if (savedUserHome != null) {
            System.setProperty("user.home", savedUserHome!!)
        } else {
            System.clearProperty("user.home")
        }
    }

    @Test
    fun kissatPath_is_null_when_no_kissat_is_staged() {
        assertNull(BundledEngine.kissatPath(),
                "with nothing staged in the engine cache, kissatPath() must be null")
    }

    @Test
    fun kissatPath_finds_a_staged_binary() {
        val staged = stageFakeKissat()
        val found = BundledEngine.kissatPath()
        assertEquals(staged.toString(), found,
                "a staged kissat binary must be discoverable via kissatPath()")
        assertTrue(Files.isRegularFile(Path.of(found!!)), "the returned path must be a real file")
    }

    @Test
    fun jbmc_run_path_does_not_reference_the_bundled_kissat() {
        // Stage a kissat so kissatPath() WOULD return a real path — the run path must still ignore it.
        val staged = stageFakeKissat()
        // No bmc.externalSat / bmc.solverCmd / bmc.solver set, so the args use the default backend.
        val saved = listOf("bmc.externalSat", "bmc.solverCmd", "bmc.solver")
                .associateWith { System.getProperty(it) }
        saved.keys.forEach { System.clearProperty(it) }
        try {
            val args = Jbmc.args(
                    "pkg.T", "pkg.T.proof", "/cp",
                    1, false, 0, null)
            assertFalse(args.any { it.contains("kissat") },
                    "the jbmc command must not reference the bundled kissat: $args")
            assertFalse(args.contains(staged.toString()),
                    "the jbmc command must not contain the bundled kissat path: $args")
            assertFalse(args.contains("--external-sat-solver"),
                    "no --external-sat-solver wiring without an explicit bmc.externalSat: $args")
        } finally {
            saved.forEach { (k, v) -> if (v != null) System.setProperty(k, v) else System.clearProperty(k) }
        }
    }

    /** Stage a fake kissat binary at the exact cache path BundledEngine.kissatPath() resolves. */
    private fun stageFakeKissat(): Path {
        val platform = Platform.current()
        val version = BundledEngine.version()
        val cacheDir = Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "engine")
                .resolve(platform.id + if (version != null) "-$version" else "")
        val kissat = cacheDir.resolve("bin/kissat" + if (platform.isWindows) ".exe" else "")
        Files.createDirectories(kissat.parent)
        Files.writeString(kissat, "FAKE_KISSAT_BINARY\n")
        return kissat
    }
}
