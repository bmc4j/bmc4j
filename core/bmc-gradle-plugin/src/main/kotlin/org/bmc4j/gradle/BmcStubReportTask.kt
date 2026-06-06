package org.bmc4j.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Aggregates the nondet stubs harvested across the proof suite into a ranked "most-hit unmodeled
 * methods" report — a data-driven `bmc-models` backlog.
 *
 * The fact is already persisted: every verified proof's verdict-cache entry under
 * `build/bmc4j/verdict-cache/` carries its harvested `STUB <fqn>` lines. This task reads
 * those entries, tallies how many proofs hit each stubbed method, and writes a ranked report (and prints
 * the top of it). Run the `test` task first so the cache is populated; a stub the suite never hit
 * won't appear (that's the point — it ranks what proofs actually depend on).
 *
 * Note: counts are per cached *verdict entry*, the same granularity the cache stores; a stub
 * acknowledged via `allowStubs` is still counted here (the report is about modeling coverage, not
 * about what's been waved through).
 */
@DisableCachingByDefault(because = "Re-reads the current verdict cache on every run; output is a freshly aggregated report")
abstract class BmcStubReportTask : DefaultTask() {

    /**
     * The verdict-cache directory to scan (`build/bmc4j/verdict-cache`); set by the plugin.
     * `@Internal`: this is a report task run on demand — it should re-read the current cache
     * each invocation, not be skipped by up-to-date checks on the cache dir.
     */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    /** Where the ranked report is written. Defaults to `build/bmc4j/stub-report.txt`. */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun report() {
        val dir = cacheDir.get().asFile.toPath()
        val counts = LinkedHashMap<String, Int>()
        var entries = 0
        if (Files.isDirectory(dir)) {
            Files.list(dir).use { files ->
                files.asSequence()
                        .filter { Files.isRegularFile(it) && !it.fileName.toString().contains(".tmp.") }
                        .map { readLinesQuietly(it) }
                        .filter { it.isNotEmpty() && it[0].startsWith("VERIFIED") }
                        .forEach { lines ->
                            entries++
                            lines.filter { it.startsWith("STUB ") }
                                    .forEach { counts.merge(it.removePrefix("STUB ").trim(), 1, Int::plus) }
                        }
            }
        }

        val ranked = counts.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

        val report = buildString {
            append("bmc4j stub report — most-hit nondet-stubbed methods across the proof suite\n")
            append("(a data-driven bmc-models backlog; counts are per cached verified proof)\n")
            append("scanned ").append(entries).append(" cached verified proof(s); ")
                    .append(counts.size).append(" distinct stubbed method(s)\n\n")
            if (ranked.isEmpty()) {
                append("No nondet stubs harvested. Either the suite's reachable slices are fully modeled,\n")
                append("or the verdict cache is empty — run the `test` task first to populate it.\n")
            } else {
                append(String.format("%-6s  %s%n", "hits", "method"))
                ranked.forEach { append(String.format("%-6d  %s%n", it.value, it.key)) }
            }
        }

        val out = reportFile.get().asFile.toPath()
        Files.createDirectories(out.parent)
        Files.writeString(out, report, StandardCharsets.UTF_8)

        logger.lifecycle(report.trimEnd())
        logger.lifecycle("\nbmc4j: stub report written to $out")
    }

    private fun readLinesQuietly(f: Path): List<String> = try {
        Files.readAllLines(f, StandardCharsets.UTF_8)
    } catch (e: IOException) {
        emptyList()
    }
}
