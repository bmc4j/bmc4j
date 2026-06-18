package org.bmc4j.engine

import java.io.File

/**
 * A SOURCE-ATTRIBUTED guide to "what's generating the formula", built by parsing jbmc's SSA
 * PROGRAM-EQUATION dump (`--program-only`) and aggregating the equation's steps back to the source
 * lines/methods that produced them. The data behind the `-Dbmc.complexityReport` diagnostic.
 *
 * ## What it does
 * jbmc's `--program-only` prints the bounded program as a flat list of single-assignment (SSA) steps,
 * each step a numbered `(N) <expression>` line, immediately preceded by a `// <id> file <f> line <l>
 * function <fn> bytecode-index <b>` comment giving the SOURCE LOCATION that step came from. This parser
 * binds each step to that location, groups the steps by method (FQN) and by `file:line`, and counts -
 * within each group - the EXPENSIVE-to-bit-blast operator kinds that drive CNF size far more than a
 * plain copy/assignment: multiply, divide, modulo, a variable/symbolic-distance shift, an array
 * read/write at a NON-CONSTANT (symbolic) index (array-theory), and type/bit casts. The render is a
 * ranked "top regions by step count, each with its op-kind breakdown" table, so formula complexity can
 * be attributed to code by MEASUREMENT rather than guessed from a profile's totals.
 *
 * ## HONESTY - this is a PROXY, not clause counts
 * This is SSA program-equation attribution, which is a PROXY for CNF/SAT complexity, **NOT** literal
 * clause counts. One SSA op bit-blasts to a VARIABLE number of CNF clauses: a multiply or a
 * symbolic-array-index explodes; a plain copy is ~free. That is exactly why the op-kind breakdown
 * matters and why this report must NOT be read as "this region is N clauses" or "this is THE
 * bottleneck". It attributes the EQUATION by source and FLAGS the expensive op-kinds; the only way to
 * get exact CNF clause attribution is ablation - remove the code, measure the delta. The rendered
 * header repeats this caveat so it travels with the output.
 *
 * The step location comes from the most recent preceding `// ... file ... line ... function ...`
 * comment; a step the engine emits with no location comment (rare control scaffolding) is attributed
 * to the last location seen, or to an `(unattributed)` bucket if none has been seen yet. The op-kind
 * tallies are read out of the step's EXPRESSION text (the `*` / `/` / `mod` operators, the `<<`/`>>`
 * shift with a non-literal distance, the `[(...)]` single-bracket symbolic index vs the `[[..]]`
 * constant index, and the `(type)` cast), so they reflect what jbmc actually put in the equation.
 */
class JbmcComplexity private constructor(
        /** Per-SOURCE-region tallies, already ranked highest-step-count first. Each region is one method
         *  at one source `file:line`. Excludes the [unattributedSteps] bucket (which is not a source
         *  region) so the ranking is the actionable, source-attributed part of the equation. */
        val regions: List<Region>,
        /** Total SSA steps parsed across the whole equation, including [unattributedSteps] - the
         *  denominator for "what share of the equation this region is". */
        val totalSteps: Int,
        /** Steps jbmc emitted with NO source attribution at all (engine-synthesized class-init / scaffolding
         *  whose dump comment carried no `function`). HONEST, not a parser miss: these steps genuinely have
         *  no source location in jbmc's output. Reported as its own share so the bulk class-init setup
         *  doesn't crowd the source-attributed ranking. */
        val unattributedSteps: Int) {

    /** The expensive-to-bit-blast operator kinds we count per region. A plain copy/assignment is NOT
     *  here - only the kinds whose CNF cost grows super-linearly (or that pull in array/bit-vector
     *  theory) are worth flagging, because they are what actually inflates the formula. */
    enum class OpKind(val label: String) {
        MUL("mul"),
        DIV("div"),
        MOD("mod"),
        SHIFT("sym-shift"),
        SYM_ARRAY("sym-array"),
        CAST("cast");
    }

    /** One source region (a method at a `file:line`) with its step count and per-op-kind tallies. */
    class Region(
            /** Method FQN, e.g. `org.apache.maven.ComparableVersion.parseItem`. */
            @get:JvmName("function") val function: String,
            /** Source file as jbmc reported it (e.g. `ComparableVersion.java`), or null if absent. */
            @get:JvmName("file") val file: String?,
            /** Source line, or 0 when jbmc emitted no line for the steps in this region. */
            @get:JvmName("line") val line: Int,
            /** Number of SSA steps attributed to this region. */
            @get:JvmName("steps") val steps: Int,
            /** Per-op-kind counts within this region (only non-zero kinds present). */
            @get:JvmName("ops") val ops: Map<OpKind, Int>) {

        /** `file:line` for display, or `<unknown>` when no source file was attributed. */
        fun where(): String = if (file == null) "<unknown>" else "$file:$line"

        /** The op-kind breakdown as `mul x40, sym-array x88, cast x12`, expensive-kinds only, or empty
         *  when this region has none (a region of plain copies/assignments). */
        fun opSummary(): String =
                OpKind.entries
                        .mapNotNull { k -> ops[k]?.let { "${k.label} x$it" } }
                        .joinToString(", ")
    }

    /**
     * Render the report as a readable, ranked table, tagged `  bmc4j[complexity]:` so it sits alongside
     * the `  bmc4j[profile]:` lines and is easy to grep. [entryFunction] names the proof. Shows the
     * top-[TOP_N] regions by step count, each with its source location and expensive-op breakdown, under
     * a header that states the proxy caveat plainly.
     */
    fun render(entryFunction: String): String {
        if (regions.isEmpty() && totalSteps == 0) {
            return "  bmc4j[complexity]: $entryFunction -> no SSA program equation was captured" +
                    " (the --program-only pass produced no parsable steps)."
        }
        // Widths so the columns line up: the region label is "function  (file:line)".
        val shown = regions.take(TOP_N)
        val labelWidth = (shown.maxOfOrNull { "${it.function}  (${it.where()})".length } ?: 0)
                .coerceAtMost(80)
        val lines = buildList {
            add(" $entryFunction -> complexity by source (SSA program equation)")
            add("   what this is: each SSA step of jbmc's bounded program equation, attributed back to the")
            add("     source line/method that produced it, ranked by step count. The op-kind columns count")
            add("     the EXPENSIVE-to-bit-blast kinds (mul/div/mod, symbolic-distance shift, symbolic-index")
            add("     array read/write, cast) - the ops that drive CNF size, vs a ~free plain copy.")
            add("   CAVEAT: this is a PROXY for SAT/CNF cost, NOT literal clause counts. One SSA op")
            add("     bit-blasts to a VARIABLE number of clauses (a multiply or symbolic array index")
            add("     explodes; a copy is ~free), which is why the op-kinds matter and why this does NOT")
            add("     claim a region is 'N clauses' or 'THE bottleneck'. Exact clause attribution needs")
            add("     ablation (remove the code, measure the delta); this attributes the EQUATION by source.")
            add("   total SSA steps in the equation: $totalSteps")
            if (unattributedSteps > 0) {
                val pct = if (totalSteps > 0) unattributedSteps * 100.0 / totalSteps else 0.0
                add("   ${String.format("%.1f%%", pct)} ($unattributedSteps steps) carry NO source" +
                        " location (engine class-init / scaffolding) - excluded from the ranking below.")
            }
            add("   top ${shown.size} source regions by step count (of ${regions.size}):")
            shown.forEach { r ->
                val label = "${r.function}  (${r.where()})"
                val pct = if (totalSteps > 0) r.steps * 100.0 / totalSteps else 0.0
                val ops = r.opSummary()
                val opsPart = if (ops.isEmpty()) "(plain copies/assignments)" else "($ops)"
                add("       ${label.padEnd(labelWidth)}  ${"${r.steps} steps".padEnd(11)}" +
                        " ${String.format("%4.1f%%", pct)}  $opsPart")
            }
            if (regions.size > TOP_N) {
                add("       (+ ${regions.size - TOP_N} more regions)")
            }
        }
        return lines.joinToString("\n") { "  bmc4j[complexity]:$it" }
    }

    companion object {

        /** Regions shown in the rendered table (the full list is on the parsed object). */
        private const val TOP_N = 20

        /** A numbered SSA step line: `(123) <expression>`. We only act on the numeric form - `(sliced)`
         *  steps are simplified out of the formula and carry no cost, so they are not counted. */
        private val STEP_RE = Regex("""^\((\d+)\)\s*(.*)$""")

        /** The location comment that PRECEDES a step: `// <id> file <f> line <l> function <fn> ...`. The
         *  file/line are optional (some comments are just `// <id>` or `// <id> file <f> line <l>`); we
         *  capture whatever is present and remember the function for attribution. */
        private val LOC_RE = Regex(
                """^// \d+(?: file (.+?) line (\d+))? function (\S+)""")

        /**
         * Parse the `--program-only` text dump from the spill [file], streaming it line by line so heap
         * stays bounded regardless of dump size. Best-effort and NEVER throws - a parse problem yields an
         * empty/partial report, since this is purely diagnostic and must never affect a verdict.
         */
        @JvmStatic
        fun parse(file: File): JbmcComplexity {
            val acc = Accumulator()
            try {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { acc.consume(it) }
                }
            } catch (e: Exception) {
                // Truncated / unreadable: keep whatever accumulated. Diagnostic only.
            }
            return acc.build()
        }

        /** Parse from an in-heap [text] dump (used by the focused unit test that pins the format). */
        @JvmStatic
        fun parse(text: String): JbmcComplexity {
            val acc = Accumulator()
            text.lineSequence().forEach { acc.consume(it) }
            return acc.build()
        }

        /** `java::pkg.Class.method:(sig)ret` (or a bare `java::pkg.Class.method`) -> `pkg.Class.method`.
         *  Mirrors [JbmcProfile]'s method-FQN normalization so the regions read like the profile's
         *  offenders list. A name without the `java::` prefix is returned trimmed. */
        private fun renderMethod(symbol: String): String {
            var s = symbol.removePrefix("java::")
            val sig = s.indexOf(":(")
            if (sig >= 0) {
                s = s.substring(0, sig)
            }
            return s.ifBlank { symbol }
        }

        /**
         * Count the EXPENSIVE-to-bit-blast op kinds present in one SSA step [expr], folding each into
         * [into]. Counts the OCCURRENCES of each kind in the expression (a step can carry several), since
         * a step with two multiplies costs more than one. Read straight out of the expression text jbmc
         * printed, so it reflects the ops actually in the equation:
         *  - `*` multiply, `/` divide, `mod` modulo;
         *  - a SHIFT (`<<` / `>>`) whose distance is NON-constant (a symbolic shift, the costly form;
         *    a `<< 3` constant shift is cheap and not counted);
         *  - an array access at a SYMBOLIC index: jbmc prints a constant index as a DOUBLE bracket
         *    `arr[[0]]` and a non-constant one as a single bracket `arr[(long)i]` / `{...}[expr]` - we
         *    count the single-bracket, non-numeric-index form (array-theory cost), never the constant one;
         *  - a `(type)` CAST (a parenthesized type prefix), the bit-width conversions that add extension
         *    /truncation constraints.
         */
        internal fun countOps(expr: String, into: MutableMap<OpKind, Int>) {
            fun bump(k: OpKind, n: Int) {
                if (n > 0) into[k] = (into[k] ?: 0) + n
            }
            bump(OpKind.MUL, occurrences(expr, MUL_RE))
            bump(OpKind.DIV, occurrences(expr, DIV_RE))
            bump(OpKind.MOD, occurrences(expr, MOD_RE))
            bump(OpKind.SHIFT, occurrences(expr, SYM_SHIFT_RE))
            bump(OpKind.SYM_ARRAY, occurrences(expr, SYM_INDEX_RE))
            bump(OpKind.CAST, occurrences(expr, CAST_RE))
        }

        private fun occurrences(s: String, re: Regex): Int = re.findAll(s).count()

        // A multiply is ` * ` between operands (jbmc spaces its binary ops). The surrounding spaces avoid
        // the `(int (*)[5])` array-pointer cast spelling, which has `(*)` with no spaces around the star.
        private val MUL_RE = Regex(""" \* """)
        private val DIV_RE = Regex(""" / """)
        // jbmc prints Java `%` as the `mod` operator in the equation.
        private val MOD_RE = Regex("""\bmod\b""")
        // A shift whose distance is NOT a bare integer literal: `<< (expr)` / `>> sym`. A `<< 3` constant
        // shift (cheap, folded) is excluded by requiring the distance to start with something other than a
        // plain digit run.
        private val SYM_SHIFT_RE = Regex("""(?:<<|>>)\s+(?!\d+\b)""")
        // A SYMBOLIC array index: a single `[` followed by a non-digit (so NOT a constant `[0]`), and NOT
        // the `[[` double-bracket constant-index form jbmc uses for a literal index. Matches `arr[(long)i]`
        // and `{...}[expr]`; excludes `arr[[0]]` and `nondet(...)[0L]`.
        private val SYM_INDEX_RE = Regex("""(?<!\[)\[(?!\[)(?![0-9])""")
        // A `(type)` cast: a parenthesized primitive type name used as a prefix. Excludes the `(N)` step
        // number (digits) and guard markers. Covers cbmc's `(unsigned int)` / `(signed long)` spellings.
        private val CAST_RE = Regex(
                """\((?:unsigned |signed )?(?:int|long|short|char|byte|float|double|bool|void)\b[^)]*\)""")

        /** Mutable per-region accumulator. */
        private class RegionAcc {
            var steps = 0
            val ops = LinkedHashMap<OpKind, Int>()
        }

        /** Streams the dump lines, tracking the "current location" (set by the last `// ... function ...`
         *  comment) and folding each numbered step into its region. */
        private class Accumulator {
            // Keyed by "function\tfile\tline" so each method-at-line is one region. LinkedHashMap keeps
            // first-seen order as a stable tiebreak for equal step counts.
            private val regions = LinkedHashMap<String, RegionAcc>()
            private var curFunction: String? = null
            private var curFile: String? = null
            private var curLine = 0
            private var total = 0
            private var unattributed = 0

            fun consume(raw: String) {
                val line = raw.trimEnd()
                // Location comment: update the current attribution target (function always present; file
                // /line present only when jbmc had them). A `// <id>` with no `function` is not a location,
                // so steps keep the last real location (jbmc emits such bare comments for located steps).
                if (line.startsWith("// ")) {
                    LOC_RE.find(line)?.let { m ->
                        curFile = m.groupValues[1].ifEmpty { null }
                        curLine = m.groupValues[2].toIntOrNull() ?: 0
                        curFunction = renderMethod(m.groupValues[3])
                    }
                    return
                }
                val step = STEP_RE.find(line) ?: return
                val expr = step.groupValues[2]
                total++
                if (curFunction == null) {
                    // No source location seen yet: engine class-init / scaffolding. Tallied separately
                    // (not a source region) so it never crowds the attributed ranking.
                    unattributed++
                    return
                }
                val key = "$curFunction\t${curFile ?: ""}\t$curLine"
                val acc = regions.getOrPut(key) { RegionAcc() }
                acc.steps++
                countOps(expr, acc.ops)
            }

            fun build(): JbmcComplexity {
                val ranked = regions.entries
                        .map { (key, acc) ->
                            val parts = key.split('\t')
                            Region(parts[0], parts[1].ifEmpty { null }, parts[2].toIntOrNull() ?: 0,
                                    acc.steps, LinkedHashMap(acc.ops))
                        }
                        .sortedWith(compareByDescending<Region> { it.steps }
                                .thenBy { it.function }.thenBy { it.line })
                return JbmcComplexity(ranked, total, unattributed)
            }
        }
    }
}
