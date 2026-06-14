package org.bmc4j.engine

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Locates the JBMC binary that is *bundled* on the test runtime classpath
 * (shipped inside a `bmc-engine-<platform>` jar) and extracts it to a local
 * cache so it can be executed. There is no network access: the engine arrived as
 * an ordinary, integrity-verified Gradle dependency.
 *
 * Layout inside the engine jar, for platform `<p>`:
 * ```
 *   jbmc/<p>/files.txt      newline list of files to extract
 *   jbmc/<p>/version.txt    engine version (used as a cache key)
 *   jbmc/<p>/bin/jbmc[.exe]
 *   jbmc/<p>/lib/core-models.jar
 *   jbmc/<p>/bin/kissat[.exe]   (optional) bundled KISSAT SAT solver
 *   jbmc/<p>/KISSAT-LICENSE     (optional) its MIT license
 * ```
 */
object BundledEngine {

    /**
     * The bundled engine's version string (e.g. `"cbmc-6.9.0"`), or `null` if no engine
     * is bundled on the classpath. Used as part of the verdict-cache key: a new engine
     * version can change a verdict, so its identity must bust the cache.
     */
    @JvmStatic
    fun version(): String? {
        val platform = Platform.current()
        return readResourceAsString("jbmc/" + platform.id + "/version.txt")
    }

    /** Serializes first-use extraction across this JVM's proof worker threads. */
    private val EXTRACT_LOCK = Any()

    /**
     * Extract (once) and return the path to the bundled jbmc executable.
     *
     * Concurrency-safe: proofs verify in parallel, so first use races N workers here
     * (observed on CI as a `FileAlreadyExistsException` mid-`Files.copy`).
     * In-JVM racers are serialized by a lock; cross-process racers (parallel Gradle test
     * JVMs sharing the user-level cache) are handled by extracting into a unique temp
     * dir and atomically renaming it into place — the cache dir is only ever observed
     * complete, and the losing extractor just uses the winner's copy.
     */
    @JvmStatic
    fun extract(): String {
        // Platform.current() already redirects a musl/Alpine x64 host to the musl engine jar
        // (linux-x64-musl), so musl is now a SELECTION, not a failure: the matching musl-built
        // jbmc is picked here. If that engine jar simply isn't on the classpath, readManifest
        // raises the generic, actionable "add the engine dependency" error below.
        val platform = Platform.current()
        val root = "jbmc/" + platform.id
        val files = readManifest("$root/files.txt", platform)
        val version = readResourceAsString("$root/version.txt")

        val cacheDir = baseCacheDir().resolve(platform.id + if (version != null) "-$version" else "")
        val exeRel = "bin/jbmc" + if (platform.isWindows) ".exe" else ""
        val exe = cacheDir.resolve(exeRel)
        if (Files.isRegularFile(exe)) {
            return exe.toString()
        }

        synchronized(EXTRACT_LOCK) {
            if (Files.isRegularFile(exe)) {
                return exe.toString() // an in-JVM racer extracted while we waited
            }
            try {
                val tmp = Files.createTempDirectory(
                        Files.createDirectories(cacheDir.parent),
                        cacheDir.fileName.toString() + ".tmp-")
                for (rel in files) {
                    val target = tmp.resolve(rel)
                    Files.createDirectories(target.parent)
                    resource("$root/$rel").use { input ->
                        if (input == null) {
                            throw IllegalStateException("Bundled engine is missing $rel")
                        }
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                // Every bundled bin/ entry is an executable (jbmc AND, when present, the kissat SAT
                // solver). Extraction copies files without the exec bit, so on POSIX an un-chmod'd
                // kissat fails execvp with EACCES ("Permission denied") - mark them all runnable.
                val binExes = files.filter { it.startsWith("bin/") }.map { tmp.resolve(it) }
                for (binExe in binExes) {
                    binExe.toFile().setExecutable(true)
                }
                if (platform.isMac) {
                    // Clear Gatekeeper quarantine and ad-hoc sign so the relocated binaries run.
                    bestEffort("xattr", "-dr", "com.apple.quarantine", tmp.toString())
                    for (binExe in binExes) {
                        bestEffort("codesign", "--force", "--sign", "-", binExe.toString())
                    }
                }
                try {
                    Files.move(tmp, cacheDir, StandardCopyOption.ATOMIC_MOVE)
                } catch (raced: IOException) {
                    if (Files.isRegularFile(exe)) {
                        // A concurrent process won and its copy is complete — use it.
                        deleteRecursively(tmp)
                    } else {
                        // cacheDir exists but has no executable: a stale partial from a
                        // pre-fix crash. Replace it with our complete copy.
                        deleteRecursively(cacheDir)
                        Files.move(tmp, cacheDir, StandardCopyOption.ATOMIC_MOVE)
                    }
                }
                return exe.toString()
            } catch (e: IOException) {
                throw IllegalStateException("Failed to extract bundled JBMC engine to $cacheDir", e)
            }
        }
    }

    /**
     * Path to the *bundled* KISSAT SAT solver binary extracted next to jbmc, or `null` if no
     * kissat is bundled for this platform.
     *
     * KISSAT is shipped inside the same `bmc-engine-<platform>` jar (an entry in `files.txt`),
     * so when present it is extracted by [extract] into the same cache dir and is
     * **integrity-by-construction**: it arrived through the verified jar, exactly like jbmc.
     *
     * This is a passive accessor only. It is deliberately **NOT consulted by the jbmc
     * invocation/run path** — the bundled solver is shipped but unused. (External-SAT routing,
     * if ever enabled, is driven solely by the `bmc.externalSat` property in [Jbmc], never by
     * this method.)
     *
     * Returns `null` rather than extracting on demand: callers that need the binary on disk run
     * [extract] first (which unpacks every `files.txt` entry, kissat included). On a platform
     * whose jar bundles no kissat (e.g. windows-x64 when no Windows build exists), or before
     * extraction has run, this returns `null`.
     */
    @JvmStatic
    fun kissatPath(): String? {
        val platform = Platform.current()
        val version = readResourceAsString("jbmc/" + platform.id + "/version.txt")
        val cacheDir = baseCacheDir().resolve(platform.id + if (version != null) "-$version" else "")
        val kissat = cacheDir.resolve("bin/kissat" + if (platform.isWindows) ".exe" else "")
        return if (Files.isRegularFile(kissat)) kissat.toString() else null
    }

    /**
     * True if this host uses the musl C library (Alpine) rather than glibc. Checked only on
     * Linux. Reads the real filesystem root; the root-injecting overload is the testable core.
     */
    internal fun isMuslLibc(): Boolean = isMuslLibc(Path.of("/"))

    /**
     * True if [root] looks like a musl/Alpine system. Two independent, reliable signals:
     * the Alpine release marker, or a musl dynamic loader under `/lib`. Either is sufficient;
     * a glibc system has neither. Root-injecting so tests can stage the markers.
     */
    internal fun isMuslLibc(root: Path): Boolean {
        if (Files.exists(root.resolve("etc/alpine-release"))) {
            return true
        }
        return hasMuslLoader(root.resolve("lib")) || hasMuslLoader(root.resolve("usr/lib"))
    }

    /** True if [dir] contains a musl dynamic loader (`ld-musl-<arch>.so.1`). */
    private fun hasMuslLoader(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) {
            return false
        }
        return try {
            Files.newDirectoryStream(dir, "ld-musl-*").use { it.iterator().hasNext() }
        } catch (e: IOException) {
            false
        }
    }

    /** Best-effort recursive delete (cleanup of temp/partial extraction dirs). */
    private fun deleteRecursively(dir: Path) {
        try {
            Files.walk(dir).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
            }
        } catch (ignored: IOException) {
            // Non-fatal: a leftover temp dir is harmless.
        }
    }

    private fun readManifest(resourcePath: String, platform: Platform): List<String> {
        try {
            resource(resourcePath).use { input ->
                if (input == null) {
                    throw IllegalStateException(
                            "No bundled JBMC engine for platform '" + platform.id +
                                    "' on the test classpath.\n" +
                                    "Add the matching engine dependency (the 'org.bmc4j' plugin does this " +
                                    "automatically), or set -Dbmc.jbmc=<path to a local jbmc>.")
                }
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                return reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: IOException) {
            throw IllegalStateException("Could not read engine manifest $resourcePath", e)
        }
    }

    private fun readResourceAsString(resourcePath: String): String? {
        return try {
            resource(resourcePath).use { input ->
                input?.readAllBytes()?.toString(StandardCharsets.UTF_8)?.trim()
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun resource(path: String): InputStream? =
            BundledEngine::class.java.classLoader.getResourceAsStream(path)

    private fun baseCacheDir(): Path =
            Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "engine")

    private fun bestEffort(vararg cmd: String) {
        try {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            p.inputStream.readAllBytes()
            p.waitFor()
        } catch (e: IOException) {
            // Non-fatal: if signing/xattr is unavailable the binary may still run.
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            // Non-fatal: if signing/xattr is unavailable the binary may still run.
        }
    }
}
