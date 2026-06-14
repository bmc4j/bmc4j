package org.bmc4j.engine

import java.nio.charset.StandardCharsets

/**
 * Surfaces jbmc's PHASE PROGRESS to the console live, AS the engine streams it, under `@BmcProfile`
 * (or `-Dbmc.streamEngine`). It is what lets a long-running or killed proof show WHICH phase it is
 * really in - symbolic execution, then Convert SSA / bit-blasting, then the solver - instead of only
 * finding out post-mortem from the profile (and surviving a SIGKILL, since each transition is printed
 * the moment its marker arrives, not parsed from the spill file after the kill).
 *
 * It is deliberately a FILTERED view, NOT the `--verbosity 10` flood: only the phase-transition markers
 * ([ENTER_SYMEX] -> symex, [ENTER_CONVERT_SSA] -> Convert SSA, [PASSING_TO_SAT] -> solver) and a
 * periodic SYMEX HEARTBEAT (one line per [HEARTBEAT_STRIDE] unwinding/assignment steps, naming the
 * latest function the engine is unwinding) reach the console. A normal, unprofiled run never constructs
 * one, so its stream copy is byte-for-byte unchanged and pays nothing.
 *
 * The scan is over the RAW byte stream (the markers are short, distinctive ASCII), with a small carry
 * buffer so a marker split across two reads is still matched - no live JSON parsing, so heap stays
 * bounded regardless of output size. Not thread-safe: [feed] is called only from the single gobbler
 * thread that owns the stream.
 */
internal class EngineProgress(private val sink: (String) -> Unit) {

    /** Tail of the previous chunk, retained so a marker straddling a read boundary still matches. The
     *  longest marker we look for is well under [CARRY], so keeping that many trailing chars suffices. */
    private val carry = StringBuilder()
    private var reachedSymex = false
    private var reachedConvertSsa = false
    private var reachedSolver = false
    private var unwindSteps = 0L
    private var nextHeartbeatAt = HEARTBEAT_STRIDE
    private var lastFunction: String? = null

    /**
     * Absorb [len] freshly-read bytes of the engine's stdout and emit any phase transition / heartbeat.
     * Only the COMMITTED region (everything except a small retained overlap tail) is scanned and then
     * dropped, so a marker straddling a read boundary is still matched next time but no occurrence is ever
     * counted twice (the bug a naive re-scan of the retained carry would cause).
     */
    fun feed(bytes: ByteArray, len: Int) {
        if (len <= 0) {
            return
        }
        carry.append(String(bytes, 0, len, StandardCharsets.UTF_8))
        // Commit up to the last NEWLINE: jbmc's --json-ui output puts each message object on its own lines,
        // so a line boundary never splits a marker or a function symbol mid-token (the truncated-name bug a
        // fixed-size cut caused). Everything after the last newline is retained for the next read. We only
        // hold back an unbounded tail if a single line exceeds the cap, in which case we force a commit at
        // CARRY to keep heap bounded (a pathological no-newline stream).
        val lastNl = carry.lastIndexOf("\n")
        val commitEnd = when {
            lastNl >= 0 -> lastNl + 1
            carry.length > CARRY -> carry.length - CARRY
            else -> return // nothing safe to commit yet
        }
        if (commitEnd <= 0) {
            return
        }
        scan(carry.substring(0, commitEnd))
        carry.delete(0, commitEnd)
    }

    /** Flush the final retained tail when the stream ends (so a transition/heartbeat in the last bytes is
     *  not lost). Idempotent for the one-shot transitions; the unwind count it adds is the trailing tail. */
    fun finish() {
        if (carry.isNotEmpty()) {
            scan(carry.toString())
            carry.setLength(0)
        }
    }

    private fun scan(text: String) {
        if (!reachedSolver && text.contains(PASSING_TO_SAT)) {
            reachedSolver = true
            emit("solver - SAT/SMT: passing the formula to propositional reduction")
        }
        if (!reachedConvertSsa && text.contains(ENTER_CONVERT_SSA)) {
            reachedConvertSsa = true
            emit("Convert SSA - converting the program to a bit-vector formula (symex done)")
        }
        if (!reachedSymex && text.contains(ENTER_SYMEX)) {
            reachedSymex = true
            emit("Symex - symbolic execution / loop unwinding has begun")
        }
        // Symex heartbeat: count unwinding steps and beat once per stride, naming the function the engine
        // is currently unwinding so a long symex shows live which method is the hot path.
        var from = 0
        while (true) {
            val at = text.indexOf(UNWIND_LOOP, from)
            if (at < 0) {
                break
            }
            unwindSteps++
            captureFunction(text, at + UNWIND_LOOP.length)
            from = at + UNWIND_LOOP.length
            if (unwindSteps >= nextHeartbeatAt) {
                nextHeartbeatAt += HEARTBEAT_STRIDE
                val where = lastFunction?.let { " (in $it)" } ?: ""
                emit("Symex heartbeat: $unwindSteps loop unwindings so far$where")
            }
        }
    }

    /** Pull the `java::pkg.Class.method` an `Unwinding loop ...` line names, for the heartbeat. */
    private fun captureFunction(text: CharSequence, start: Int) {
        var i = start
        val n = text.length
        while (i < n && text[i] == ' ') {
            i++
        }
        val begin = i
        while (i < n && text[i] != ' ' && text[i] != '"' && text[i] != '\\') {
            i++
        }
        // If we ran off the end of the committed text without hitting a terminator, the symbol was cut by
        // the commit boundary - skip it rather than record a truncated name (the next intact line wins).
        if (i <= begin || i >= n) {
            return
        }
        var symbol = text.subSequence(begin, i).toString().removePrefix("java::")
        val sig = symbol.indexOf(":(")
        if (sig >= 0) {
            // `pkg.Class.method:(desc)ret.<ordinal>` -> `pkg.Class.method` (the signature cut also drops
            // the trailing loop ordinal that follows it).
            symbol = symbol.substring(0, sig)
        } else {
            // No signature present (`pkg.Class.method.<ordinal>`): strip only a trailing numeric ordinal,
            // never the method name itself.
            symbol = symbol.replace(TRAILING_ORDINAL, "")
        }
        if (symbol.isNotBlank()) {
            lastFunction = symbol
        }
    }

    private fun emit(message: String) = sink("  bmc4j[engine]: $message")

    companion object {
        /** Markers shared with [JbmcProfile]; kept literal here to avoid coupling the live scanner to the
         *  profiler's parsing internals. Pinned against the bundled engine by the same tests. */
        private const val ENTER_SYMEX = "Starting Bounded Model Checking"
        private const val ENTER_CONVERT_SSA = "converting SSA"
        private const val PASSING_TO_SAT = "Passing problem to propositional reduction"
        private const val UNWIND_LOOP = "Unwinding loop "

        /** A trailing `.<digits>` loop ordinal on a signature-less `Unwinding loop` symbol. */
        private val TRAILING_ORDINAL = Regex("""\.\d+$""")

        /** Emit one symex heartbeat per this many unwinding steps - frequent enough to show life on a long
         *  symex, sparse enough never to become a flood. */
        private const val HEARTBEAT_STRIDE = 2000L

        /** Bounded carry kept between reads so a marker straddling a chunk boundary is still matched. */
        private const val CARRY = 256

        /**
         * Whether to stream live engine progress for this run. On under `@BmcProfile` ([profile]) by
         * default, and forced on/off by `-Dbmc.streamEngine=true|false` regardless of profiling - so a
         * raw engine run can be watched live without the full profile, or the live lines suppressed under
         * profiling. Off entirely otherwise (the normal path is untouched).
         */
        @JvmStatic
        fun isEnabled(profile: Boolean): Boolean {
            val prop = System.getProperty("bmc.streamEngine")
            if (prop != null) {
                return prop.isBlank() || prop.equals("true", ignoreCase = true)
            }
            return profile
        }
    }
}
