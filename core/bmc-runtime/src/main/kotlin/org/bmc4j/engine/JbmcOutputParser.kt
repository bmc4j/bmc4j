package org.bmc4j.engine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.ArrayDeque

/**
 * Parses JBMC `--json-ui --trace` output into a [JbmcResult].
 *
 * The output is a JSON array of message objects; one carries a `result`
 * array of properties. For each `FAILURE` property we extract the violation
 * location, reconstruct the active call stack from the trace's
 * `function-call`/`function-return` steps, and pull out the symbolic
 * input assignments that constitute the counterexample.
 */
object JbmcOutputParser {

    @JvmStatic
    fun parse(json: String, entryFunctionFqn: String?): JbmcResult {
        val root: JsonArray = try {
            JsonParser.parseString(json).asJsonArray
        } catch (e: RuntimeException) {
            // Unparseable engine output: we can neither verify nor refute, so the verdict
            // is UNKNOWN (undecided), not a silent pass. Fails the test with the undecided framing.
            return JbmcResult.unknown("JBMC produced output bmc4j could not parse", json)
        }
        // Harvest the nondet-stub fact from the engine message stream once, regardless of
        // verdict — policy (footnote / strict-UNKNOWN) is applied later by the caller. Attached to the
        // computed verdict below via withStubbedMethods (a no-op when empty).
        return parseVerdict(root, json, entryFunctionFqn).withStubbedMethods(harvestStubs(root))
    }

    private fun parseVerdict(root: JsonArray, json: String, entryFunctionFqn: String?): JbmcResult {
        var result: JsonArray? = null
        for (e in root) {
            if (!e.isJsonObject) {
                continue
            }
            val o = e.asJsonObject
            if (o.has("result")) {
                result = o.getAsJsonArray("result")
            }
        }

        val violations = mutableListOf<JbmcResult.Violation>()
        var markers = 0            // injected reachability markers seen
        var markersFailed = 0      // ... that FAILED (i.e. that exit is reachable)
        var unwindingFailures = 0  // --unwinding-assertions firings: the BOUND is too small
        if (result != null) {
            for (pe in result) {
                val p = pe.asJsonObject
                val status = str(p, "status")
                if (isReachabilityMarker(p)) {
                    markers++
                    if (status == "FAILURE") {
                        markersFailed++
                    }
                    continue // never report a marker as a user violation
                }
                if (status == "FAILURE") {
                    if (isUnwindingAssertion(p)) {
                        // NOT a counterexample: an unwinding-assertion failure says the loop
                        // bound truncated exploration — the analysis is incomplete, nothing was
                        // proven wrong. Counted separately and judged below; reporting it as a
                        // violation would mislabel "bound too small" as REFUTED, and an
                        // expect = REFUTED demo could pass for the wrong reason.
                        unwindingFailures++
                        continue
                    }
                    violations.add(toViolation(p, entryFunctionFqn))
                }
            }
        }

        // Verdict. When reachability markers are present (every @BmcProof gets them), the
        // overall cProverStatus is ALWAYS "failure" on the green path — a reachable marker is itself a
        // FAILURE property — so we must NOT consult cProverStatus when markers exist; the verdict is
        // driven by user properties + marker reachability:
        //   - any real (non-marker) user FAILURE  -> refuted (a trace within the bound is a REAL
        //                                            trace — under-approximation — so a genuine
        //                                            counterexample stands even if the bound ALSO
        //                                            truncated deeper paths);
        //   - else if any unwinding assertion fired -> UNKNOWN (the bound is too small: exploration
        //                                            was truncated, so neither a green nor a vacuity
        //                                            claim is trustworthy — markers may be unreachable
        //                                            merely because the bound cut them off);
        //   - else if every marker is SUCCESS      -> VACUOUS (all normal exits dead, assumptions
        //                                             unsatisfiable, the proof checked nothing);
        //   - else (>=1 marker reachable)          -> verified.
        if (markers > 0) {
            if (violations.isNotEmpty()) {
                return JbmcResult(false, violations, json)
            }
            if (unwindingFailures > 0) {
                return JbmcResult.unknown(unwindingReason(unwindingFailures), json)
            }
            if (markersFailed == 0) {
                return JbmcResult(false, listOf(vacuityViolation()), json, true)
            }
            return JbmcResult(true, violations, json)
        }
        // No reachability markers. Every real @BmcProof run carries markers (ReachabilityBytecode
        // replaces every return), so a marker-less run is anomalous and CANNOT be passed soundly: the
        // vacuity check never ran (a return-less while(true)/always-throws proof emits no marker, so
        // assume(false) could "pass"), and array-valid output with no result/cProverStatus would
        // otherwise read as VERIFIED. A real user FAILURE is still a refutation; otherwise the verdict
        // is UNKNOWN, not a silent green — we never default a missing status to "ok".
        if (violations.isNotEmpty()) {
            return JbmcResult(false, violations, json)
        }
        if (unwindingFailures > 0) {
            return JbmcResult.unknown(unwindingReason(unwindingFailures), json)
        }
        return JbmcResult.unknown(
                "reachability markers missing — vacuity could not be checked", json)
    }

    /**
     * True if a FAILURE property is an `--unwinding-assertions` firing rather than a user
     * property. The bound truncates exploration in two shapes, and BOTH are incompleteness, never a
     * counterexample: a **loop** overrun is named `<function>.unwind.<n>` with description
     * `"unwinding assertion loop <n>"`; a **recursion** overrun is named
     * `<function>.recursion` with description `"recursion unwinding assertion"`. Either
     * signal suffices per shape (defensive OR — both shapes are pinned against the bundled engine by
     * the parser tests).
     */
    private fun isUnwindingAssertion(p: JsonObject): Boolean {
        val property = str(p, "property")
        if (property != null && (property.contains(".unwind.") || property.endsWith(".recursion"))) {
            return true
        }
        val description = str(p, "description")
        return description != null
                && (description.startsWith("unwinding assertion")
                        || description.startsWith("recursion unwinding assertion"))
    }

    /** The UNKNOWN reason for a bound-too-small run (the extension appends the remedies). */
    private fun unwindingReason(count: Int): String =
            "unwinding assertion failed: the unwind bound is too small to cover this proof (" +
                    count + (if (count == 1) " loop/recursion" else " loops/recursions") +
                    " hit the bound) — exploration was truncated, so this is incompleteness, " +
                    "not a refutation"

    /**
     * Marker phrase JBMC stamps on a method it had no body for and stubbed to nondet. In cbmc 6.9.0's
     * `--json-ui` stream (at `--verbosity 10`) these surface as STATUS-MESSAGEs of the form
     * `"Generating codet:  new opaque symbol: method 'java::pkg.Class.method:(sig)'"`. This is the
     * engine's own term for an unmodeled callee — the soundness fact we harvest. The format
     * is not an engine contract (pinned by `JbmcOutputParserTest` against the bundled version; the
     * engine identity is in the verdict-cache key, so a bump forces re-validation).
     */
    private const val OPAQUE_MARKER = "new opaque symbol: method '"

    /**
     * Harvest the methods JBMC analyzed as nondet stubs: scan every message for the engine's
     * "opaque symbol" marker, extract the `pkg.Class.method` (dropping the `java::` prefix
     * and `:(signature)` suffix), filter to [signal][StubFilter.isSignal], and dedupe in
     * first-seen order. Empty when the reachable slice was fully modeled. Pure; never throws.
     */
    @JvmStatic
    @JvmName("harvestStubs") // internal functions are name-mangled in bytecode; Java tests call it
    internal fun harvestStubs(root: JsonArray): List<String> {
        val stubs = LinkedHashSet<String>()
        for (e in root) {
            if (!e.isJsonObject) {
                continue
            }
            val text = str(e.asJsonObject, "messageText") ?: continue
            val at = text.indexOf(OPAQUE_MARKER)
            if (at < 0) {
                continue
            }
            val start = at + OPAQUE_MARKER.length
            val end = text.indexOf('\'', start)
            val symbol = if (end > start) text.substring(start, end) else text.substring(start)
            val fqn = methodFqn(symbol)
            if (fqn != null && StubFilter.isSignal(fqn)) {
                stubs.add(fqn)
            }
        }
        return stubs.toList()
    }

    /** `java::pkg.Class.method:(sig)ret` -> `pkg.Class.method` (null if unrecognizable). */
    private fun methodFqn(symbol: String?): String? {
        if (symbol.isNullOrBlank()) {
            return null
        }
        var s = symbol.removePrefix("java::")
        val sig = s.indexOf(":(")
        if (sig >= 0) {
            s = s.substring(0, sig)
        }
        return s.ifBlank { null }
    }

    /** The dedicated violation describing an unsatisfiable-assumptions (vacuous) proof. */
    private fun vacuityViolation(): JbmcResult.Violation =
            JbmcResult.Violation(BmcReachability.VACUOUS_MESSAGE, null, 0,
                    mutableListOf(), mutableListOf())

    /**
     * True if [property] is an injected reachability marker — identified by the
     * [sentinel source line][BmcReachability.SENTINEL_LINE] stamped on it by
     * [ReachabilityBytecode]. Robust against any assertion the user writes inside a proof.
     */
    private fun isReachabilityMarker(property: JsonObject): Boolean {
        val sl = if (property.has("sourceLocation")) property.getAsJsonObject("sourceLocation") else null
        return sl != null && BmcReachability.isMarkerLine(intOr(sl, "line", -1))
    }

    private fun toViolation(property: JsonObject, entryFunctionFqn: String?): JbmcResult.Violation {
        var description = str(property, "description")
        val sl = if (property.has("sourceLocation")) property.getAsJsonObject("sourceLocation") else null
        var file = if (sl != null) str(sl, "file") else null
        var line = if (sl != null) intOr(sl, "line", 0) else 0

        val stack = mutableListOf<StackTraceElement>()
        val counterexample = mutableListOf<String>()
        val bindings = mutableListOf<JbmcResult.Binding>()

        if (property.has("trace")) {
            buildStackAndCounterexample(property.getAsJsonArray("trace"), file, line,
                    entryFunctionFqn, stack, counterexample, bindings)
        }
        if (stack.isEmpty() && file != null) {
            stack.add(frame(if (property.has("sourceLocation"))
                    str(property.getAsJsonObject("sourceLocation"), "function") else null, file, line))
        }

        // A Bmc.check(...) failure surfaces as an assertion inside our own code.
        // Re-point it at the proof line and give it a clean description before we
        // drop the internal frames.
        val internalCheck = isInternalFile(file)
        val userFrame = stack.firstOrNull { !isInternalFrame(it) }
        if (internalCheck) {
            description = "a checked property does not hold"
            if (userFrame != null) {
                file = userFrame.fileName
                line = userFrame.lineNumber
            }
        }

        // Hide bmc-runtime / CProver plumbing from the reported stack trace.
        stack.removeIf(::isInternalFrame)

        return JbmcResult.Violation(description, file, line, stack, counterexample, bindings)
    }

    /** Reconstruct the call stack live at the failure, plus input assignments. */
    private fun buildStackAndCounterexample(trace: JsonArray, failFile: String?, failLine: Int,
                                            entryFunctionFqn: String?,
                                            stack: MutableList<StackTraceElement>,
                                            counterexample: MutableList<String>,
                                            bindings: MutableList<JbmcResult.Binding>) {
        val active = ArrayDeque<Frame>()
        var failFunction: String? = null
        // name -> final value, restricted to the proof method's own variables.
        val inputs = LinkedHashMap<String, String>()
        // name -> JBMC value kind, parallel to `inputs` (for structured replay rendering).
        val inputKinds = LinkedHashMap<String, String>()
        val entryPrefix = if (entryFunctionFqn != null) "java::$entryFunctionFqn" else null

        for (se in trace) {
            val step = se.asJsonObject
            val type = str(step, "stepType") ?: continue
            when (type) {
                "function-call" -> {
                    val id = funcId(step)
                    if (isUserFunction(id)) {
                        val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
                        active.push(Frame(id, locFile(loc), locLine(loc)))
                    }
                }
                "function-return" -> {
                    val id = funcId(step)
                    if (isUserFunction(id) && !active.isEmpty()) {
                        // Pop the matching frame (LIFO; ids line up in well-formed traces).
                        active.pop()
                    }
                }
                "assignment" -> collectCounterexample(step, entryPrefix, inputs, inputKinds)
                "failure" -> {
                    val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
                    if (loc != null) {
                        failFunction = str(loc, "function")
                    }
                }
                else -> {
                }
            }
        }

        inputs.forEach { (k, v) ->
            counterexample.add("$k = $v")
            bindings.add(JbmcResult.Binding(k, inputKinds[k], v))
        }

        // active (top -> bottom) = [innermost callee, ..., entry]
        val frames = ArrayList(active) // ArrayDeque iterator is head(top)->tail(bottom)
        if (frames.isEmpty()) {
            return
        }
        // Innermost frame at the actual failure line.
        val innermostFn = failFunction ?: frames[0].id
        stack.add(frame(innermostFn, failFile, failLine))
        // Each caller is rendered at the call site of the frame below it.
        for (i in 0 until frames.size - 1) {
            val callee = frames[i]          // its call site lies in the caller
            val caller = frames[i + 1]
            stack.add(frame(caller.id, callee.callFile, callee.callLine))
        }
    }

    private fun collectCounterexample(step: JsonObject, entryPrefix: String?,
                                      inputs: MutableMap<String, String>,
                                      inputKinds: MutableMap<String, String>) {
        val lhs = str(step, "lhs")
        if (lhs.isNullOrEmpty() || !isUserVariable(lhs)) {
            return
        }
        // Restrict to variables declared in the proof method itself — these are the
        // inputs the developer cares about, not compiler/JBMC temporaries.
        if (entryPrefix != null) {
            val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
            val fn = if (loc != null) str(loc, "function") else null
            if (fn == null || !fn.startsWith(entryPrefix)) {
                return
            }
        }
        if (!step.has("value") || !step.get("value").isJsonObject) {
            return
        }
        val value = step.getAsJsonObject("value")
        val kind = str(value, "name")
        // Only show concrete primitive inputs, not pointers/objects/internals.
        if (kind == null || kind !in setOf("integer", "boolean", "float", "double")) {
            return
        }
        val data = str(value, "data") ?: return
        inputs[lhs] = data // last assignment wins -> final counterexample value
        inputKinds[lhs] = kind
    }

    /** Reject compiler/JBMC synthetics ($stack, return_tmp, __CPROVER_*, *_return_value). */
    private fun isUserVariable(name: String): Boolean {
        if (!isSimpleName(name)) {
            return false
        }
        if (name.indexOf('$') >= 0 || name.startsWith("__")) {
            return false
        }
        return !name.contains("tmp")
                && !name.endsWith("_return_value")
                && !name.startsWith("return_")
                && name != "to_return"
    }

    // --- helpers -------------------------------------------------------------

    private class Frame(val id: String?, val callFile: String?, val callLine: Int)

    private fun isInternalFile(file: String?): Boolean {
        if (file == null) {
            return false
        }
        val f = file.replace('\\', '/')
        return f.endsWith("org/bmc4j/Bmc.java") || f.startsWith("org/cprover/")
    }

    private fun isInternalFrame(e: StackTraceElement): Boolean {
        val c = e.className
        return c.startsWith("org.bmc4j.") || c.startsWith("org.cprover.")
    }

    private fun isUserFunction(id: String?): Boolean =
            id != null && id.startsWith("java::") && !id.contains("<clinit")

    private fun funcId(step: JsonObject): String? {
        if (step.has("function") && step.get("function").isJsonObject) {
            return str(step.getAsJsonObject("function"), "identifier")
        }
        return null
    }

    private fun locFile(loc: JsonObject?): String? = if (loc != null) str(loc, "file") else null

    private fun locLine(loc: JsonObject?): Int = if (loc != null) intOr(loc, "line", 0) else 0

    /**
     * Turn a JBMC function id like `java::pkg.Class.method:(I)I` plus a
     * source location into a StackTraceElement.
     */
    private fun frame(functionId: String?, file: String?, line: Int): StackTraceElement {
        var className = "unknown"
        var method = "proof"
        if (functionId != null) {
            var s = functionId.removePrefix("java::")
            val sig = s.indexOf(":(")
            if (sig >= 0) {
                s = s.substring(0, sig)
            }
            val lastDot = s.lastIndexOf('.')
            if (lastDot > 0) {
                className = s.substring(0, lastDot)
                method = s.substring(lastDot + 1)
            } else {
                method = s
            }
        }
        var fileName = file
        if (fileName != null) {
            val slash = maxOf(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'))
            if (slash >= 0) {
                fileName = fileName.substring(slash + 1)
            }
        }
        return StackTraceElement(className, method, fileName, line)
    }

    private fun isSimpleName(s: String): Boolean {
        if (!Character.isJavaIdentifierStart(s[0])) {
            return false
        }
        for (i in 1 until s.length) {
            if (!Character.isJavaIdentifierPart(s[i])) {
                return false
            }
        }
        return true
    }

    private fun str(o: JsonObject?, key: String): String? =
            if (o != null && o.has(key) && !o.get(key).isJsonNull) o.get(key).asString else null

    private fun intOr(o: JsonObject, key: String, fallback: Int): Int {
        val v = str(o, key) ?: return fallback
        return v.trim().toIntOrNull() ?: fallback
    }
}
