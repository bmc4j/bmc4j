package org.bmc4j.engine

/**
 * A wall-clock timing record for bmc4j's OWN pre-engine pipeline — the classpath mirroring + bytecode
 * rewrites [JbmcBackend.prepareClasspath] runs BEFORE jbmc ever launches (mirror, desugars,
 * reachability, domain-split, exception-message elision, contracts, model-slice). None of that work is
 * visible to jbmc's `Runtime <Phase>:` stream, so without this a slow proof's profile could only ever
 * blame the engine, never bmc4j's prep.
 *
 * It is a tiny ordered accumulator: [time] wraps one pass, recording its wall-time under a stable label;
 * repeated labels accumulate (so a pass run twice — e.g. Reachability before AND after a domain split —
 * sums). The values are bmc4j-MEASURED (a wall-clock around our own code), kept DISTINCT in the rendered
 * profile from jbmc's engine-REPORTED `Runtime` phases. Purely additive diagnostics: collecting timings
 * never changes what the passes do or the verdict.
 *
 * Not thread-safe by design: one instance is created per [JbmcBackend.verify] call and used only on that
 * call's own thread while it prepares the classpath (the passes run sequentially), so no synchronization
 * is needed. The map is read once, after preparation, to seed the profile.
 */
class PipelineTiming {

    /** Pass label -> accumulated wall-time in seconds, in first-seen order. */
    private val seconds = LinkedHashMap<String, Double>()

    /**
     * Run [body], recording its wall-clock under [label] (accumulating if [label] recurs). Returns
     * [body]'s result unchanged — a transparent wrapper, so instrumenting a pass is a one-line edit that
     * can never change the pass's behaviour. The timing is taken even if [body] throws (a failing pass
     * still cost wall-time), then the throwable propagates as normal.
     */
    inline fun <T> time(label: String, body: () -> T): T {
        val start = System.nanoTime()
        try {
            return body()
        } finally {
            record(label, (System.nanoTime() - start) / 1_000_000_000.0)
        }
    }

    /** Accumulate [secs] under [label]. Public so [time]'s inline body can reach it. */
    fun record(label: String, secs: Double) {
        seconds[label] = (seconds[label] ?: 0.0) + secs
    }

    /** A snapshot of the recorded pass timings, label -> seconds, in first-seen order. */
    fun snapshot(): Map<String, Double> = LinkedHashMap(seconds)

    /** True when no pass was timed (e.g. a fully pre-mirrored, contract-free, unsliced proof). */
    fun isEmpty(): Boolean = seconds.isEmpty()
}
