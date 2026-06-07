package org.bmc4j.junit

import org.bmc4j.Verdict
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Machine-readable per-proof summary sink. When `-Dbmc.summaryDir=<dir>` is set, every proof the
 * [BmcProofExtension] runs appends exactly one JSON object (one line, JSONL) recording its outcome:
 * what was expected, what verdict it resolved to, whether the verdict was served from the per-proof
 * verdict cache or solved live, how long it took, whether the proof passed, and — on a failure — a
 * truncated, single-line counterexample/issue string.
 *
 * This is the STRUCTURED data source the CI PR-comment job aggregates (the alternative —
 * log-scraping the `bmc4j: ... -> ...` progress lines — is fragile: those lines are emitted only on
 * some paths, are multi-line, and were never designed as an interchange format). Emitting a typed
 * record at the one place every verdict is decided is robust by construction.
 *
 * ## Format & robustness
 * - One file per JVM (named with a random suffix), so the many forked Gradle test workers a sharded
 *   leg spawns never interleave half-lines into one file. The CI job globs every `*.jsonl` under the
 *   summary dir and unions them — order doesn't matter, the proof id is the identity.
 * - Append is `synchronized` + a complete line written in one `Files.write`, so concurrent proofs in
 *   the SAME worker (parallel test execution) can't tear a record either.
 * - **Fail-open, always.** This is observability, never correctness: any I/O error is swallowed.
 *   A proof's verdict must never change because its summary couldn't be written. When
 *   `-Dbmc.summaryDir` is unset (every local/IDE run), [enabled] is false and every call is a no-op.
 *
 * The schema (stable; the CI parser depends on these keys):
 * ```json
 * {"proof":"pkg.Cls.method","cls":"pkg.Cls","expected":"VERIFIED","verdict":"REFUTED",
 *  "cached":false,"ok":false,"ms":1234,"detail":"score = 100 (Foo.java:42)"}
 * ```
 * `cls` lets the aggregator count DISCOVERED proofs per class for the shard-union "expected" total
 * (so a lost shard is visible as a discovered-but-absent gap). `detail` is empty on a pass.
 */
internal object ProofSummary {

    private const val SUMMARY_DIR_PROP = "bmc.summaryDir"

    /** Max characters of counterexample/issue text kept per record (keeps the JSONL + comment small). */
    private const val DETAIL_MAX = 400

    private val lock = Any()

    /** The JSONL file this JVM appends to, created lazily on first write. Null when disabled. */
    @Volatile
    private var file: File? = null

    @Volatile
    private var resolved = false

    /** True when `-Dbmc.summaryDir` names a writable directory. */
    val enabled: Boolean
        get() = System.getProperty(SUMMARY_DIR_PROP)?.isNotBlank() == true

    /**
     * Record one proof's outcome. No-op unless [enabled]. [detail] should be the human-readable
     * counterexample / undecided reason on a failure (empty/blank on a pass); it is collapsed to a
     * single line and truncated.
     */
    fun record(proof: String, declaringClass: String, expected: Verdict, verdict: Verdict,
               cached: Boolean, ok: Boolean, elapsedMs: Long, detail: String?) {
        if (!enabled) {
            return
        }
        try {
            val sb = StringBuilder(256)
            sb.append('{')
            field(sb, "proof", proof); sb.append(',')
            field(sb, "cls", declaringClass); sb.append(',')
            field(sb, "expected", expected.name); sb.append(',')
            field(sb, "verdict", verdict.name); sb.append(',')
            sb.append("\"cached\":").append(cached).append(',')
            sb.append("\"ok\":").append(ok).append(',')
            sb.append("\"ms\":").append(if (elapsedMs < 0) 0 else elapsedMs).append(',')
            field(sb, "detail", clip(detail))
            sb.append("}\n")
            append(sb.toString())
        } catch (_: Throwable) {
            // Observability only — never let a summary error perturb the proof outcome.
        }
    }

    private fun append(line: String) {
        synchronized(lock) {
            val f = target() ?: return
            try {
                Files.write(f.toPath(), line.toByteArray(StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND)
            } catch (_: IOException) {
                // swallow — fail-open
            }
        }
    }

    /** Resolve (once) this JVM's unique JSONL file under the summary dir, creating the dir. */
    private fun target(): File? {
        if (resolved) {
            return file
        }
        resolved = true
        val dir = System.getProperty(SUMMARY_DIR_PROP)
        if (dir.isNullOrBlank()) {
            return null
        }
        return try {
            val path: Path = File(dir).toPath()
            Files.createDirectories(path)
            // pid (best-effort) + a random suffix: never collide across the forked test workers.
            val pid = try { ProcessHandle.current().pid().toString() } catch (_: Throwable) { "x" }
            val f = path.resolve("proofs-$pid-${UUID.randomUUID()}.jsonl").toFile()
            file = f
            f
        } catch (_: Throwable) {
            file = null
            null
        }
    }

    /** Collapse to one line and truncate so a pathological counterexample can't bloat the comment. */
    private fun clip(detail: String?): String {
        if (detail.isNullOrBlank()) {
            return ""
        }
        val flat = detail.replace('\n', ' ').replace('\r', ' ').replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= DETAIL_MAX) flat else flat.substring(0, DETAIL_MAX) + "…"
    }

    private fun field(sb: StringBuilder, key: String, value: String) {
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"')
    }

    /** Minimal JSON string escaping (the CI side parses with a real JSON reader). */
    private fun escape(s: String): String {
        val out = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u").append(String.format("%04x", c.code)) else out.append(c)
            }
        }
        return out.toString()
    }
}
