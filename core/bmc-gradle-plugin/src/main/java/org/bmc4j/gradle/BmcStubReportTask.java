package org.bmc4j.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Aggregates the nondet stubs harvested across the proof suite into a ranked "most-hit unmodeled
 * methods" report — a data-driven {@code bmc-models} backlog.
 *
 * <p>The fact is already persisted: every verified proof's verdict-cache entry under
 * {@code build/bmc4j/verdict-cache/} carries its harvested {@code STUB <fqn>} lines. This task reads
 * those entries, tallies how many proofs hit each stubbed method, and writes a ranked report (and prints
 * the top of it). Run the {@code test} task first so the cache is populated; a stub the suite never hit
 * won't appear (that's the point — it ranks what proofs actually depend on).
 *
 * <p>Note: counts are per cached <em>verdict entry</em>, the same granularity the cache stores; a stub
 * acknowledged via {@code allowStubs} is still counted here (the report is about modeling coverage, not
 * about what's been waved through).
 */
@DisableCachingByDefault(because = "Re-reads the current verdict cache on every run; output is a freshly aggregated report")
public abstract class BmcStubReportTask extends DefaultTask {

    /**
     * The verdict-cache directory to scan ({@code build/bmc4j/verdict-cache}); set by the plugin.
     * {@code @Internal}: this is a report task run on demand — it should re-read the current cache
     * each invocation, not be skipped by up-to-date checks on the cache dir.
     */
    @Internal
    public abstract DirectoryProperty getCacheDir();

    /** Where the ranked report is written. Defaults to {@code build/bmc4j/stub-report.txt}. */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void report() throws IOException {
        Path dir = getCacheDir().get().getAsFile().toPath();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int entries = 0;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    if (!Files.isRegularFile(f) || f.getFileName().toString().contains(".tmp.")) {
                        continue;
                    }
                    List<String> lines = readLinesQuietly(f);
                    if (lines.isEmpty() || !lines.get(0).startsWith("VERIFIED")) {
                        continue;
                    }
                    entries++;
                    for (String line : lines) {
                        if (line.startsWith("STUB ")) {
                            String fqn = line.substring("STUB ".length()).trim();
                            counts.merge(fqn, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(counts.entrySet());
        ranked.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });

        StringBuilder sb = new StringBuilder();
        sb.append("bmc4j stub report — most-hit nondet-stubbed methods across the proof suite\n");
        sb.append("(a data-driven bmc-models backlog; counts are per cached verified proof)\n");
        sb.append("scanned ").append(entries).append(" cached verified proof(s); ")
                .append(counts.size()).append(" distinct stubbed method(s)\n\n");
        if (ranked.isEmpty()) {
            sb.append("No nondet stubs harvested. Either the suite's reachable slices are fully modeled,\n");
            sb.append("or the verdict cache is empty — run the `test` task first to populate it.\n");
        } else {
            sb.append(String.format("%-6s  %s%n", "hits", "method"));
            for (Map.Entry<String, Integer> e : ranked) {
                sb.append(String.format("%-6d  %s%n", e.getValue(), e.getKey()));
            }
        }

        Path out = getReportFile().get().getAsFile().toPath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);

        getLogger().lifecycle(sb.toString().stripTrailing());
        getLogger().lifecycle("\nbmc4j: stub report written to " + out);
    }

    private static List<String> readLinesQuietly(Path f) {
        try {
            return Files.readAllLines(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }
}
