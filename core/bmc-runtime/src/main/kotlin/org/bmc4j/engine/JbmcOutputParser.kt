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
    @JvmOverloads
    fun parse(json: String, entryFunctionFqn: String?, userClasspath: String? = null): JbmcResult {
        val root: JsonArray = try {
            JsonParser.parseString(json).asJsonArray
        } catch (e: RuntimeException) {
            // Unparseable engine output: we can neither verify nor refute, so the verdict
            // is UNKNOWN (undecided), not a silent pass. Fails the test with the undecided framing.
            // The raw output is in hand here, so fold a self-diagnosing tail into the reason:
            // total length + an empty/truncated-JSON/garbage classification + a bounded tail, so the
            // next occurrence classifies itself (pipe truncation vs engine stderr bleed vs OOM-kill
            // mid-write) instead of reading only "could not parse".
            return JbmcResult.unknownParse(parseFailureReason(json), json)
        }
        // Harvest the nondet-stub fact from the engine message stream once, regardless of
        // verdict — policy (footnote / strict-UNKNOWN) is applied later by the caller. Attached to the
        // computed verdict below via withStubbedMethods (a no-op when empty).
        return parseVerdict(root, json, entryFunctionFqn, WitnessUserCode.from(userClasspath))
                .withStubbedMethods(harvestStubs(root))
                .withUnmodelledMembers(harvestUnmodelledMembers(root))
                // linkFailureStubs carries BOTH fingerprints of a present-class nondet stub a refutation
                // ran through: (1) the stub_ignored_arg* trace fingerprint (caller had a body but it was
                // havoc'd), and (2) the explicit "no body for callee <member>" property JBMC emits when it
                // could not resolve an invokeinterface/abstract call to its present concrete override (the
                // devirtualization-fragility case). Both demote a would-be REFUTED to a member-named UNKNOWN
                // when the owner class is present on the classpath — never a silent false refutation.
                .withLinkFailureStubs(harvestLinkFailureStubMembers(root) + harvestNoBodyCalleeMembers(root))
    }

    private fun parseVerdict(root: JsonArray, json: String, entryFunctionFqn: String?,
                            userCode: WitnessUserCode?): JbmcResult {
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
                    violations.add(toViolation(p, entryFunctionFqn, userCode))
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
                return JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION,
                        unwindingReason(unwindingFailures), json)
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
            return JbmcResult.unknown(UnknownKind.UNWINDING_ASSERTION,
                    unwindingReason(unwindingFailures), json)
        }
        // The engine produced output, but the injected reachability markers are absent, so no
        // trustworthy verdict signal could be extracted (vacuity could not be checked). Deterministic,
        // so not retryable — classified SOLVER_GAVE_UP (engine returned an undecidable result).
        return JbmcResult.unknown(UnknownKind.SOLVER_GAVE_UP,
                "reachability markers missing — vacuity could not be checked", json)
    }

    /**
     * Build the self-diagnosing reason for a PARSE_FAILURE: the engine exited with a verdict
     * code but its `--json-ui` stdout didn't parse into the expected JSON array. The raw output is in
     * hand, so we classify it (empty / truncated-JSON / non-JSON garbage), report its total length,
     * and append a bounded tail (last [PARSE_TAIL_MAX] chars) — enough to tell pipe truncation from an
     * engine stderr bleed from an OOM-kill mid-write on the next occurrence, without bloating the
     * message with the whole output.
     */
    internal fun parseFailureReason(raw: String?): String = buildString {
        append("JBMC produced output bmc4j could not parse")
        val text = raw ?: ""
        val len = text.length
        val trimmed = text.trim()
        val classification = when {
            trimmed.isEmpty() -> "empty (no stdout captured — engine wrote nothing, or it was killed " +
                    "before the first byte)"
            (trimmed.startsWith("[") || trimmed.startsWith("{"))
                    && !(trimmed.endsWith("]") || trimmed.endsWith("}")) ->
                "truncated JSON (starts like the --json-ui array/object but is cut off — pipe " +
                        "truncation or an OOM-kill mid-write)"
            trimmed.startsWith("[") || trimmed.startsWith("{") ->
                "malformed JSON (delimited like the --json-ui array/object but not well-formed — " +
                        "likely interleaved engine stderr bleed)"
            else -> "non-JSON garbage (does not begin like the --json-ui array/object — engine " +
                    "stderr on stdout, or a wrapper banner)"
        }
        append("; classification: ").append(classification)
        append("; total length: ").append(len).append(" chars")
        if (len > 0) {
            val tail = if (len > PARSE_TAIL_MAX) text.substring(len - PARSE_TAIL_MAX) else text
            append("; last ").append(tail.length).append(" chars: ")
                    .append(tail.replace('\n', ' ').replace('\r', ' ').trim())
        }
    }

    /** Bytes of the raw output's tail folded into a PARSE_FAILURE reason (the self-diagnosis). */
    private const val PARSE_TAIL_MAX = 500

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
     * The internal id of the unmodelled-member sentinel ([org.bmc4j.analysis.BmcUnmodelledReached]).
     * The bmc-models loud-body synthesis routes every unmodelled (declared / tail) member through it,
     * so a proof that reaches such a member trips an assertion JBMC reports against THIS function. We
     * recognize it to demote that would-be REFUTED to UNKNOWN — a model gap is bmc4j's own limitation,
     * never a counterexample in the user's code. Robust by FQN (the assertion's constant message is
     * discarded by JBMC; the violated function is the reliable signal — cf. the residual-indy marker).
     */
    private const val UNMODELLED_SENTINEL = "java::org.bmc4j.analysis.BmcUnmodelledReached.reached:"

    /** Any function in the unmodelled-member sentinel class (reached / fail) — skipped when recovering
     *  the offending MEMBER, which is the first caller OUTSIDE the sentinel class. */
    private const val UNMODELLED_SENTINEL_CLASS = "java::org.bmc4j.analysis.BmcUnmodelledReached."

    /**
     * Harvest the unmodelled MEMBERS this run reached: for every FAILURE property whose violated
     * function is the [UNMODELLED_SENTINEL], recover the offending member from the property's trace —
     * the user/model function that CALLED the sentinel (e.g. `java.util.ArrayList.sort(Comparator)`),
     * rendered in dot form. Deduped, first-seen order. Empty on a normal run. Pure; never throws.
     *
     * The fact is parallel to [harvestStubs]; the POLICY (demote REFUTED -> UNKNOWN naming the member)
     * is applied by [org.bmc4j.junit.BmcProofExtension], exactly like the nondet-stub footnote/strict
     * ladder and the residual-indy demotion.
     */
    internal fun harvestUnmodelledMembers(root: JsonArray): List<String> {
        var resultArray: JsonArray? = null
        for (e in root) {
            if (e.isJsonObject && e.asJsonObject.has("result")) {
                resultArray = e.asJsonObject.getAsJsonArray("result")
            }
        }
        if (resultArray == null) {
            return emptyList()
        }
        val members = LinkedHashSet<String>()
        for (pe in resultArray) {
            val p = pe.asJsonObject
            if (str(p, "status") != "FAILURE") {
                continue
            }
            if (!violatedFunctionIsSentinel(p)) {
                continue
            }
            val member = callerOfSentinel(p)
            if (member != null) {
                members.add(member)
            }
        }
        return members.toList()
    }

    /** True when [property]'s violated function is the unmodelled-member sentinel. */
    private fun violatedFunctionIsSentinel(property: JsonObject): Boolean {
        val sl = if (property.has("sourceLocation")) property.getAsJsonObject("sourceLocation") else null
        val fn = if (sl != null) str(sl, "function") else null
        return fn != null && fn.startsWith(UNMODELLED_SENTINEL)
    }

    /**
     * The model member that called the sentinel in this property's trace: walk the function-call /
     * function-return steps, and when the sentinel is entered return the function active just below it
     * (its caller). Rendered in `pkg.Class.method(p1,p2)` dot form. Falls back to the sentinel-call
     * source location's function when no trace caller is recoverable.
     */
    private fun callerOfSentinel(property: JsonObject): String? {
        if (!property.has("trace")) {
            return null
        }
        val active = ArrayDeque<String>() // ids of open java:: frames, top = innermost
        for (se in property.getAsJsonArray("trace")) {
            val step = se.asJsonObject
            when (str(step, "stepType")) {
                "function-call" -> {
                    val id = funcId(step) ?: continue
                    if (id.startsWith("java::") && !id.contains("<clinit")) {
                        if (id.startsWith(UNMODELLED_SENTINEL)) {
                            // The offending MEMBER is the first open frame OUTSIDE the sentinel class —
                            // a hand-written stub reaches reached() via the fail() helper (both in the
                            // sentinel class), so skip every sentinel frame, not just the innermost.
                            val caller = active.firstOrNull { !it.startsWith(UNMODELLED_SENTINEL_CLASS) }
                            if (caller != null) {
                                return renderMember(caller)
                            }
                        }
                        active.push(id)
                    }
                }
                "function-return" -> {
                    val id = funcId(step) ?: continue
                    if (id.startsWith("java::") && !id.contains("<clinit") && !active.isEmpty()
                            && !id.startsWith(UNMODELLED_SENTINEL)) {
                        active.pop()
                    }
                }
            }
        }
        return null
    }

    /** `java::pkg.Class.method:(Lp1;Lp2;)V` -> `pkg.Class.method(p1, p2)` dot form. */
    private fun renderMember(funcId: String): String {
        var s = funcId.removePrefix("java::")
        val sig = s.indexOf(":(")
        var params = ""
        if (sig >= 0) {
            val desc = s.substring(sig + 1)
            s = s.substring(0, sig)
            params = renderParams(desc)
        }
        return "$s($params)"
    }

    /** Render a method descriptor's argument types as a comma-separated simple-name list. */
    private fun renderParams(methodDesc: String): String {
        val open = methodDesc.indexOf('(')
        val close = methodDesc.indexOf(')')
        if (open < 0 || close < open) {
            return ""
        }
        val out = mutableListOf<String>()
        var i = open + 1
        var arr = 0
        while (i < close) {
            when (val c = methodDesc[i]) {
                '[' -> { arr++; i++ }
                'L' -> {
                    val semi = methodDesc.indexOf(';', i)
                    val internal = methodDesc.substring(i + 1, semi)
                    out.add(internal.substringAfterLast('/') + "[]".repeat(arr)); arr = 0; i = semi + 1
                }
                else -> {
                    val prim = when (c) {
                        'I' -> "int"; 'J' -> "long"; 'Z' -> "boolean"; 'B' -> "byte"; 'C' -> "char"
                        'S' -> "short"; 'F' -> "float"; 'D' -> "double"; 'V' -> "void"; else -> c.toString()
                    }
                    out.add(prim + "[]".repeat(arr)); arr = 0; i++
                }
            }
        }
        return out.joinToString(", ")
    }

    /**
     * The variable-name prefix JBMC stamps on the (discarded) parameters of a nondet *stub body*: when
     * it has no body for a callee it synthesizes one that ignores its arguments and returns nondet,
     * naming each ignored parameter `stub_ignored_arg<n>`. So an assignment to a `stub_ignored_arg*`
     * variable in a counterexample trace is the fingerprint of a refutation that ran THROUGH a nondet
     * stub — the "counterexample" rests on that method's havoc'd result. (Not an engine contract; pinned
     * by [JbmcOutputParserTest] against the bundled cbmc 6.9.0, whose identity is in the verdict-cache
     * key, so a bump forces re-validation — same discipline as [OPAQUE_MARKER].)
     */
    private const val STUB_IGNORED_ARG_PREFIX = "stub_ignored_arg"

    /**
     * Harvest the STUBBED MEMBERS whose nondet body a FAILURE trace ran through: for every FAILURE
     * property, scan its trace for an `assignment` to a `stub_ignored_arg*` variable (the fingerprint of
     * a synthesized nondet stub body — see [STUB_IGNORED_ARG_PREFIX]) and record the STUBBED CALLEE that
     * assignment runs inside, rendered `pkg.Class.method(params)` like [harvestUnmodelledMembers].
     * Deduped, first-seen order. Empty on a clean run. Pure; never throws.
     *
     * The callee is the INNERMOST OPEN `java::` frame at the assignment — the function the stub body
     * structurally lives in — reconstructed from the trace's `function-call` / `function-return` steps.
     * It is emphatically NOT the assignment step's `sourceLocation.function`: real JBMC stamps the
     * CALLER's frame there, not the stub it synthesized (verified against a live trace — a lateinit
     * pre-init read assigns `stub_ignored_arg0` with `sourceLocation.function = Session.getUser` (the
     * caller) while the stubbed callee is `Intrinsics.throwUninitializedPropertyAccessException`). Reading
     * the source location named the ever-present caller class, so the present-on-classpath demotion always
     * tripped on the wrong class. The open-frame stack is the reliable signal — the same discipline
     * [callerOfSentinel] uses, and self-contained per trace (no dependence on the `--verbosity 10`
     * opaque-symbol stream, which [harvestStubs] consumes separately and which only reports that SOMETHING
     * was stubbed, not which method a given counterexample ran through).
     *
     * This is the parse-time FACT that a refutation passed through a nondet stub. The POLICY — demote
     * such a REFUTED to a member-named UNKNOWN when the stub's owning class is nonetheless PRESENT on the
     * analysis classpath (a transient engine link failure, not a real counterexample) — is applied by
     * [org.bmc4j.junit.BmcProofExtension], which alone knows the classpath. A genuinely absent class
     * (sliced away / a missing dependency) is the [harvestStubs] / SliceSoundnessProbe path and is left
     * to the nondet-stub footnote ladder; the present-on-classpath check is what separates the two.
     */
    internal fun harvestLinkFailureStubMembers(root: JsonArray): List<String> {
        var resultArray: JsonArray? = null
        for (e in root) {
            if (e.isJsonObject && e.asJsonObject.has("result")) {
                resultArray = e.asJsonObject.getAsJsonArray("result")
            }
        }
        if (resultArray == null) {
            return emptyList()
        }
        val members = LinkedHashSet<String>()
        for (pe in resultArray) {
            val p = pe.asJsonObject
            if (str(p, "status") != "FAILURE" || !p.has("trace")) {
                continue
            }
            // Track the open java:: call frames so a stub_ignored_arg* assignment can be attributed to
            // the function it runs INSIDE (the stubbed callee = innermost open frame), not the caller the
            // engine mislabels on the assignment's source location.
            val active = ArrayDeque<String>() // top = innermost open frame
            for (se in p.getAsJsonArray("trace")) {
                val step = se.asJsonObject
                when (str(step, "stepType")) {
                    "function-call" -> {
                        val id = funcId(step) ?: continue
                        if (id.startsWith("java::") && !id.contains("<clinit")) {
                            active.push(id)
                        }
                    }
                    "function-return" -> {
                        val id = funcId(step) ?: continue
                        if (id.startsWith("java::") && !id.contains("<clinit") && !active.isEmpty()) {
                            active.pop()
                        }
                    }
                    "assignment" -> {
                        val lhs = str(step, "lhs") ?: continue
                        if (!lhs.startsWith(STUB_IGNORED_ARG_PREFIX)) {
                            continue
                        }
                        val callee = active.peek() // innermost open frame = the stubbed callee
                        if (callee != null) {
                            members.add(renderMember(callee))
                        }
                    }
                }
            }
        }
        return members.toList()
    }

    /**
     * Marker phrase JBMC stamps on a FAILURE property when it could not resolve a virtual/interface
     * call to a body and havoc'd the result: the property's `description` reads
     * `"no body for callee <pkg.Class.method(params)>"` (the member already in dot/erased form). This is
     * the SECOND fingerprint of a present-class nondet stub (alongside the [STUB_IGNORED_ARG_PREFIX]
     * trace assignment): it fires for an `invokeinterface`/abstract call the engine failed to
     * devirtualize to its present concrete override — exactly the devirtualization-fragility case where
     * a modelled-abstract collection interface ({@code java.util.List}/{@code Set}/{@code Map}) is held
     * over an unmodelled concrete subtype. The owning class is the modelled interface, which IS present
     * on the classpath, so the interpreter's present-on-classpath demotion turns the would-be REFUTED
     * into a member-named UNKNOWN instead of leaking a false refutation on the havoc artifact.
     */
    private const val NO_BODY_CALLEE_PREFIX = "no body for callee "

    /**
     * Harvest the members of every `"no body for callee <member>"` FAILURE property: scan each FAILURE
     * property's `description`, and when it starts with [NO_BODY_CALLEE_PREFIX] take the trailing
     * `pkg.Class.method(params)` member (already dot/erased form — no `java::`/signature to strip).
     * Deduped, first-seen order. Empty on a clean run (the description only appears alongside a
     * could-not-link FAILURE). Pure; never throws.
     *
     * Parallel FACT to [harvestLinkFailureStubMembers]; the demote-to-UNKNOWN POLICY (only when the
     * member's owning class is present on the classpath, and never when the proof PINS expect=REFUTED)
     * lives in [org.bmc4j.junit.BmcProofExtension], identical to the stub_ignored_arg* path.
     */
    internal fun harvestNoBodyCalleeMembers(root: JsonArray): List<String> {
        var resultArray: JsonArray? = null
        for (e in root) {
            if (e.isJsonObject && e.asJsonObject.has("result")) {
                resultArray = e.asJsonObject.getAsJsonArray("result")
            }
        }
        if (resultArray == null) {
            return emptyList()
        }
        val members = LinkedHashSet<String>()
        for (pe in resultArray) {
            val p = pe.asJsonObject
            if (str(p, "status") != "FAILURE") {
                continue
            }
            val desc = str(p, "description") ?: continue
            if (!desc.startsWith(NO_BODY_CALLEE_PREFIX)) {
                continue
            }
            val member = desc.substring(NO_BODY_CALLEE_PREFIX.length).trim()
            if (member.isNotEmpty() && member.contains('(')) {
                members.add(member)
            }
        }
        return members.toList()
    }

    /**
     * Harvest the methods JBMC analyzed as nondet stubs: scan every message for the engine's
     * "opaque symbol" marker, extract the `pkg.Class.method` (dropping the `java::` prefix
     * and `:(signature)` suffix), filter to [signal][StubFilter.isSignal], and dedupe in
     * first-seen order. Empty when the reachable slice was fully modeled. Pure; never throws.
     */
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

    private fun toViolation(property: JsonObject, entryFunctionFqn: String?,
                            userCode: WitnessUserCode?): JbmcResult.Violation {
        var description = str(property, "description")
        val sl = if (property.has("sourceLocation")) property.getAsJsonObject("sourceLocation") else null
        var file = if (sl != null) str(sl, "file") else null
        var line = if (sl != null) intOr(sl, "line", 0) else 0

        val stack = mutableListOf<StackTraceElement>()
        val counterexample = mutableListOf<String>()
        val bindings = mutableListOf<JbmcResult.Binding>()

        if (property.has("trace")) {
            buildStackAndCounterexample(property.getAsJsonArray("trace"), file, line,
                    entryFunctionFqn, userCode, stack, counterexample, bindings)
        }
        // When the property carries no location of its own (jbmc sometimes omits it on a model-lowered
        // assertion — e.g. a divide check merged through the Integer/collection models), recover it from
        // the failure step in the trace so the reason and stack still point at the offending line.
        if (file == null && property.has("trace")) {
            failureStepLocation(property.getAsJsonArray("trace"))?.let { (f, l) ->
                file = f
                line = l
            }
        }
        if (stack.isEmpty() && file != null) {
            stack.add(frame(if (property.has("sourceLocation"))
                    str(property.getAsJsonObject("sourceLocation"), "function") else null, file, line))
        }

        // A Bmc.check(...) failure surfaces as an assertion inside our own code.
        // Re-point it at the proof line (the user's frame) before we drop the internal frames.
        val internalCheck = isInternalFile(file)
        val userFrame = stack.firstOrNull { !isInternalFrame(it) }
        if (internalCheck && userFrame != null) {
            file = userFrame.fileName
            line = userFrame.lineNumber
        }

        // Surface WHAT failed, not just the counterexample inputs. Replaces the old blanket
        // "a checked property does not hold" with a GENUINE reason derived from the failing
        // property's nature: a named exception (NPE / divide-by-zero / array-bounds / an explicit
        // uncaught throw) with its source location and a recoverable constant message, or — for an
        // assertion / Bmc.check / require — an "assertion failed at <user line>" framing. Soundness:
        // when the trace can't reliably name the cause we fall back to a neutral framing, never a
        // guessed type (see [deriveFailureReason]). The witness/counterexample is unchanged (additive).
        val reason = deriveFailureReason(property, sl, internalCheck, file, line)
        if (reason != null) {
            description = reason
        }

        // Hide bmc-runtime / CProver plumbing from the reported stack trace.
        stack.removeIf(::isInternalFrame)

        return JbmcResult.Violation(description, file, line, stack, counterexample, bindings)
    }

    /**
     * Derive the GENUINE failure reason for a refuted property, or `null` to keep JBMC's raw
     * description. Three shapes, all keyed off signals pinned against the bundled engine (jbmc 6.9.0;
     * the engine identity is in the verdict-cache key, so a bump re-validates — same discipline as the
     * witness path). This is DISPLAY-ONLY: it never affects the verdict.
     *
     *  1. **A built-in safety check** (NPE / integer-divide-by-zero / array-bounds): the FAILURE
     *     property's `sourceLocation.propertyClass` names the kind directly (`null-pointer-exception`,
     *     `integer-divide-by-zero`, `array-index-out-of-bounds-{high,low}`). We render the Java
     *     exception that kind corresponds to plus the source location — e.g.
     *     `java.lang.ArithmeticException: / by zero at InterpolationSearch.java:41`.
     *  2. **An explicit uncaught throw** (the `require`/`check`/hand-thrown case): JBMC's
     *     `"no uncaught exception"` check FAILED (`propertyClass` is absent and the description is
     *     exactly that phrase). The THROWN TYPE is recovered from the trace — the class the last
     *     `<init>` constructed before the failure — and a constant message from that constructor's
     *     String argument when present. Renders `<type>[: <msg>] at <file>:<line>`.
     *  3. **An assertion** (`propertyClass == "assertion"`: a Java `assert`, or our own `Bmc.check`,
     *     which throws an `AssertionError`): rendered as `assertion failed at <user file>:<line>`,
     *     with the recovered constant `check(...) { "msg" }` message appended when present. For a
     *     `Bmc.check` ([internalCheck]) the location is already re-pointed at the user's line.
     *
     * Falls through to `null` (keep the raw description) for any FAILURE shape we don't recognize — so
     * a description we can't improve is never replaced by a worse, generic one.
     */
    private fun deriveFailureReason(property: JsonObject, sl: JsonObject?, internalCheck: Boolean,
                                    file: String?, line: Int): String? {
        val rawDescription = str(property, "description")
        val propertyClass = if (sl != null) str(sl, "propertyClass") else null
        val at = locationSuffix(file, line)

        // (1) A built-in safety check names its kind in propertyClass.
        builtinExceptionType(propertyClass)?.let { return it + at }

        // (2) An explicit uncaught throw: JBMC's "no uncaught exception" check failed. Recover the
        //     thrown type (and a constant message) from the construction in the trace.
        if (propertyClass == null && rawDescription == NO_UNCAUGHT_EXCEPTION) {
            val thrown = recoverThrownException(property)
            if (thrown != null) {
                val msg = if (thrown.message != null) ": " + thrown.message else ""
                return thrown.type + msg + at
            }
            // Genuinely could not name the thrown type — neutral framing, never a guess.
            return "an uncaught exception was thrown" + at
        }

        // (3) An assertion: a Java `assert`, our own Bmc.check (an AssertionError throw), or any check
        //     jbmc lowered to a bare `assertion` property — identified by propertyClass "assertion" OR a
        //     bare/leading "assertion" description (the model-lowered shape carries no propertyClass and
        //     sometimes no location, e.g. a divide check merged through the Integer/collection models).
        if (propertyClass == "assertion" || isBareAssertionDescription(rawDescription)) {
            val base = if (internalCheck) "assertion failed (Bmc.check)" else "assertion failed"
            val msg = recoverAssertionMessage(property)
            return base + at + if (msg != null) ": $msg" else ""
        }

        return null
    }

    /** True for jbmc's bare assertion description — exactly `"assertion"`, or the
     *  `"assertion at file <f> line <n> function <fn> bytecode-index <i>"` long form it stamps on a
     *  Java `assert`. Used to name an assertion failure even when no propertyClass is present (the
     *  model-lowered shape). Deliberately NOT matched for richer descriptions we'd rather keep verbatim. */
    private fun isBareAssertionDescription(description: String?): Boolean =
            description == "assertion" || (description != null && description.startsWith("assertion at file "))

    /** ` at <file>:<line>` when a usable location is known, else empty. */
    private fun locationSuffix(file: String?, line: Int): String {
        if (file.isNullOrBlank()) {
            return ""
        }
        val name = shortFileName(file)
        return if (line > 0) " at $name:$line" else " at $name"
    }

    private fun shortFileName(file: String): String {
        val slash = maxOf(file.lastIndexOf('/'), file.lastIndexOf('\\'))
        return if (slash >= 0) file.substring(slash + 1) else file
    }

    /** JBMC's FAILURE description for a thrown-but-uncaught exception (its `no uncaught exception`
     *  check, which FAILS when an exception escapes). The thrown type is recovered from the trace. */
    private const val NO_UNCAUGHT_EXCEPTION = "no uncaught exception"

    /**
     * Map a built-in safety-check [propertyClass] to the Java exception the JVM would throw at that
     * point. Only the kinds whose Java exception type is UNAMBIGUOUS are mapped (so the named type is
     * always genuine); an unknown class returns null and the caller keeps JBMC's raw description.
     */
    private fun builtinExceptionType(propertyClass: String?): String? = when (propertyClass) {
        "null-pointer-exception" -> "java.lang.NullPointerException"
        "integer-divide-by-zero" -> "java.lang.ArithmeticException: / by zero"
        "array-index-out-of-bounds-high", "array-index-out-of-bounds-low" ->
            "java.lang.ArrayIndexOutOfBoundsException"
        "array-create-negative-size" -> "java.lang.NegativeArraySizeException"
        "class-cast-exception" -> "java.lang.ClassCastException"
        else -> null
    }

    /** A recovered thrown exception: its fully-qualified [type] and a constant [message] when one
     *  was cleanly present in the constructor's String argument (else null). */
    private class ThrownException(val type: String, val message: String?)

    /**
     * Recover the exception a `"no uncaught exception"` FAILURE actually threw: the MOST-DERIVED
     * Throwable `<init>` constructor called in the trace (its `function.identifier` is
     * `java::<type>.<init>:(<desc>)V`). Each `new X(...)` emits X's own `<init>` first, then nested
     * super-ctors — so we prefer a non-base type over the `Throwable`/`Exception`/`Error`/`RuntimeException`
     * base ctors it chains into (taking the bare base only when nothing more specific appears). The
     * constant message is recovered ONLY when that ctor took a single `String` and a literal for it is
     * present, SCOPED to the chars built just before that ctor call (so an unrelated constant elsewhere in
     * the trace is never picked up). Returns null when no Throwable ctor is recoverable — the caller then
     * falls back to a neutral framing, never a guess.
     */
    private fun recoverThrownException(property: JsonObject): ThrownException? {
        if (!property.has("trace")) {
            return null
        }
        val trace = property.getAsJsonArray("trace")
        var bestType: String? = null      // most-derived Throwable type seen
        var bestIdx = -1                  // trace index of its <init> call (to scope the message)
        var bestTakesString = false
        trace.forEachIndexed { i, se ->
            val step = se.asJsonObject
            if (str(step, "stepType") == "function-call") {
                val id = funcId(step)
                if (id != null && id.startsWith("java::") && id.contains(".<init>:(")) {
                    val internal = id.removePrefix("java::").substringBefore(".<init>:(")
                    if (isThrowableName(internal) && preferType(bestType, internal)) {
                        bestType = internal
                        bestIdx = i
                        bestTakesString = id.contains(".<init>:(Ljava/lang/String;)V")
                    }
                }
            }
        }
        val type = bestType ?: return null
        val message = if (bestTakesString) constStringBefore(trace, bestIdx) else null
        return ThrownException(type, message)
    }

    /** Should [candidate] replace the currently-chosen [current] thrown type? Yes when nothing is chosen
     *  yet, or when [current] is a generic base (the super-ctor a `new SubException` chained into) and
     *  [candidate] is more specific. Keeps the first most-derived type otherwise (stable). */
    private fun preferType(current: String?, candidate: String): Boolean {
        if (current == null) {
            return true
        }
        return isBaseThrowable(current) && !isBaseThrowable(candidate)
    }

    /** The Throwable base classes a `new SubException(...)`'s `<init>` chains into — too generic to name
     *  as the cause when a concrete subtype is also constructed. */
    private fun isBaseThrowable(internal: String): Boolean = internal in BASE_THROWABLES

    private val BASE_THROWABLES = setOf(
            "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException", "java.lang.Error")

    /** True for a class name that is plausibly a Throwable (so we never mislabel an ordinary object
     *  construction as the thrown cause). */
    private fun isThrowableName(internal: String): Boolean {
        val simple = internal.substringAfterLast('.')
        return simple.endsWith("Exception") || simple.endsWith("Error") || simple == "Throwable"
    }

    /**
     * Recover the constant message of an assertion FAILURE — ONLY when the trace carries a genuine
     * `AssertionError` (our `Bmc.check` throws one) construction taking a `String`. The message is the
     * literal built just before that `<init>` call. This scoping is what keeps a model-lowered bare
     * `assertion` (no AssertionError ctor in its trace, but plenty of unrelated baked-in constants) from
     * yielding a FABRICATED message — soundness: we surface only a message we can tie to the throw.
     */
    private fun recoverAssertionMessage(property: JsonObject): String? {
        if (!property.has("trace")) {
            return null
        }
        val trace = property.getAsJsonArray("trace")
        var ctorIdx = -1
        trace.forEachIndexed { i, se ->
            val id = funcId(se.asJsonObject)
            if (str(se.asJsonObject, "stepType") == "function-call" && id != null
                    && id.startsWith("java::java.lang.AssertionError.<init>:(")
                    && id.contains("Ljava/lang/")) { // a message-bearing AssertionError ctor (Object/String arg)
                ctorIdx = i // last such ctor wins (the actually-thrown one)
            }
        }
        if (ctorIdx < 0) {
            return null
        }
        return constStringBefore(trace, ctorIdx)
    }

    /**
     * Recover the constant String message ARGUMENT of the exception ctor at trace index [before] — but
     * ONLY when it is UNAMBIGUOUS. JBMC lays a String literal down as `<name>_constarray[<i>L]` per-char
     * `integer`(char) assignments; we stitch every distinct contiguous such array that completes before
     * [before]. A model-heavy trace bakes in MANY unrelated String constants (type names, descriptors),
     * so the closest-preceding array is NOT reliably the message — picking it yielded fabrications like
     * `: void` / `: java.math.BigDecimal`. To stay SOUND we surface a message ONLY when exactly ONE
     * recoverable literal exists in the window (the simple hand-throw / `Bmc.check(cond, "msg")` shape);
     * otherwise we return null and the caller shows the cause with no message. A wrong message is worse
     * than none. Pure; never throws.
     */
    private fun constStringBefore(trace: JsonArray, before: Int): String? {
        // base name -> (index -> char), only from assignment steps strictly before `before`.
        val arrays = LinkedHashMap<String, MutableMap<Int, Char>>()
        trace.forEachIndexed { i, se ->
            if (i >= before) {
                return@forEachIndexed
            }
            val step = se.asJsonObject
            if (str(step, "stepType") != "assignment") {
                return@forEachIndexed
            }
            val lhs = str(step, "lhs") ?: return@forEachIndexed
            val m = CONST_CHAR_RE.matchEntire(lhs) ?: return@forEachIndexed
            if (!step.has("value") || !step.get("value").isJsonObject) {
                return@forEachIndexed
            }
            val v = step.getAsJsonObject("value")
            if (str(v, "name") != "integer" || str(v, "type") != "char") {
                return@forEachIndexed
            }
            val ch = parseCharData(str(v, "data")) ?: return@forEachIndexed
            val idx = m.groupValues[2].toIntOrNull() ?: return@forEachIndexed
            arrays.getOrPut(m.groupValues[1]) { LinkedHashMap() }[idx] = ch
        }
        // Collect every fully-contiguous literal in the window. Surface one ONLY when it is the SOLE
        // candidate — more than one and we cannot tell the message from a baked-in type/descriptor string.
        val literals = arrays.values.mapNotNull { chars ->
            if (chars.isEmpty()) {
                return@mapNotNull null
            }
            val max = chars.keys.max()
            if ((0..max).all { it in chars }) (0..max).map { chars[it] }.joinToString("") else null
        }
        return if (literals.size == 1) literals[0] else null
    }

    /** Parse a JBMC char value `data` — either a quoted char literal (`'m'`) or a numeric code. */
    private fun parseCharData(data: String?): Char? {
        val d = data?.trim() ?: return null
        if (d.length >= 3 && d.first() == '\'' && d.last() == '\'') {
            val inner = d.substring(1, d.length - 1)
            return if (inner.length == 1) inner[0] else null
        }
        val code = d.toIntOrNull() ?: return null
        return if (code in 0..0xFFFF) code.toChar() else null
    }

    /** `<name>_constarray[<i>L]` — JBMC's backing char array for a String literal. */
    private val CONST_CHAR_RE = Regex("""^(.+)_constarray\[(\d+)L?]$""")

    /** Reconstruct the call stack live at the failure, plus input assignments. */
    private fun buildStackAndCounterexample(trace: JsonArray, failFile: String?, failLine: Int,
                                            entryFunctionFqn: String?, userCode: WitnessUserCode?,
                                            stack: MutableList<StackTraceElement>,
                                            counterexample: MutableList<String>,
                                            bindings: MutableList<JbmcResult.Binding>) {
        val active = ArrayDeque<Frame>()
        var failFunction: String? = null
        // name -> final value, restricted to the proof method's own variables.
        val inputs = LinkedHashMap<String, String>()
        // name -> JBMC value kind, parallel to `inputs` (for structured replay rendering).
        val inputKinds = LinkedHashMap<String, String>()
        // The heap state needed to reconstruct an ARRAY input back to `name = [e0, e1, …]`. A Java
        // `int[]`/`long[]` is not a flat value in the trace: the proof-local variable (`a`) is only a
        // POINTER to a heap `dynamic_object$N` struct, whose `data` member points to a backing store
        // (`dynamic_array`), and the concrete elements arrive as per-index assignments to that backing
        // store. So we harvest the three links across the whole trace and stitch them together after.
        val heap = ArrayHeap()
        val entryPrefix = if (entryFunctionFqn != null) "java::$entryFunctionFqn" else null

        // Explicit USER-nondet witness tags (NondetTagBytecode) are the PRIMARY input channel, so harvest
        // them BEFORE the scalar/heap trace scan and seed `inputs` with them: a `Bmc.recordNondet("name",
        // value)` call site surfaces the input's NAME + VALUE + KIND directly from the trace, independent
        // of whether the value was later boxed through a Triple/carrier or minted in a helper / user model
        // (the flow-fragility the anonlocal/LVT scalar path drops). Seeding first means a tagged input wins
        // its name: the scalar path ([collectCounterexample]) and the array heap path both skip a name
        // already in `inputs`, so they only ADD the un-tagged inputs alongside (the auto-marked @BmcProof
        // PARAMETER inputs — which carry no call site to tag — and any older-snapshot scalar). Degrades to
        // a no-op on an untagged run. An OBJECT-handle tag (anyOf / a symbolic array) carries no
        // displayable scalar (kind+value both null): it is deliberately NOT seeded here, leaving its name
        // free for the heap reconstruction below to render `name = [..]` (or a name-only object mention).
        val tags = harvestNondetTags(trace)
        tags.forEach { (name, tag) ->
            val value = tag.value
            if (name !in inputs && value != null) {
                inputs[name] = value
                // A tag with a value always carries a kind (only the object-handle tag has both null).
                inputKinds[name] = tag.kind ?: "integer"
            }
        }

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
                "assignment" -> {
                    collectCounterexample(step, entryPrefix, userCode, inputs, inputKinds)
                    heap.observe(step, entryPrefix, userCode)
                }
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
        // Array inputs: stitch each user array variable's pointer -> object -> backing-store -> elements
        // into one `name = [e0, e1, …]` binding (concrete values, index order), honoring first-wins on
        // the array's name exactly like the scalar path. A scalar already bound under the same name wins
        // (an array variable is never also a primitive, so they don't collide in practice).
        heap.resolveArrays().forEach { (name, arr) ->
            if (name !in inputs) {
                counterexample.add("$name = ${arr.render()}")
                bindings.add(JbmcResult.Binding(name, arr.kind, arr.render()))
            }
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

    /** The JBMC function-id prefix of the explicit witness-tag sink ([NondetTagBytecode] injects calls
     *  to it). A `function-call` into this id carries the tagged input's name + value as its arguments. */
    private const val RECORD_NONDET_ID = "java::org.bmc4j.Bmc.recordNondet:"

    /** The `String.Literal.` prefix JBMC stamps on a string-constant pointer's `data` — the tag NAME
     *  arrives this way (the `Bmc.recordNondet("x", ...)` first argument), and a tagged String VALUE too. */
    private const val STRING_LITERAL_PREFIX = "java.lang.String.Literal."

    /**
     * One harvested witness tag: the binding [kind] the replay renderer keys on (`"integer"`,
     * `"boolean"`, `"float"`, `"double"`, `"string"`, or `null` for an object handle that carries no
     * displayable scalar) and the displayable [value] (null for an object handle — e.g. an array, whose
     * elements the heap reconstruction renders, or a bare object input shown by name only).
     */
    internal class TagValue(val kind: String?, val value: String?)

    /**
     * Harvest the explicit USER-nondet witness tags from a FAILURE [trace]: for each `function-call`
     * into [RECORD_NONDET_ID] (`Bmc.recordNondet(name, value)`), read the argument bindings the engine
     * assigns inside that frame — the `String` literal NAME (a `pointer` whose `data` is
     * `java.lang.String.Literal.<name>`, always the FIRST literal-string arg) and the VALUE — and return
     * them as ordered `name -> TagValue` pairs, first-wins per name.
     *
     * The VALUE's kind is taken from the `recordNondet` OVERLOAD the call resolved to (its descriptor in
     * the frame id): `J` -> integer, `Z` -> boolean, `F` -> float, `D` -> double, a trailing
     * `Ljava/lang/String;` -> string, a trailing `Ljava/lang/Object;` -> object handle (no scalar value;
     * the variable is named, and an array's elements are rendered by the heap reconstruction). This is
     * the robust counterexample-input channel: the tag is emitted at the symbolic-input call site, so its
     * value lands in the trace regardless of how the value later flows (boxed through a `Triple`/carrier,
     * returned from a helper, minted in a user model). Pure; never throws; empty on an untagged run.
     */
    internal fun harvestNondetTags(trace: JsonArray): LinkedHashMap<String, TagValue> {
        val out = LinkedHashMap<String, TagValue>()
        var valueKind: String? = null       // the overload's value kind for the current tag frame
        var inTag = false
        var name: String? = null
        var value: String? = null
        for (se in trace) {
            val step = se.asJsonObject
            when (str(step, "stepType")) {
                "function-call" -> {
                    val id = funcId(step)
                    if (id != null && id.startsWith(RECORD_NONDET_ID)) {
                        inTag = true; name = null; value = null; valueKind = recordNondetValueKind(id)
                    }
                }
                "function-return" -> {
                    val id = funcId(step)
                    if (id != null && id.startsWith(RECORD_NONDET_ID)) {
                        val resolvedName = name
                        if (resolvedName != null && resolvedName !in out) {
                            // An object handle (kind null) records the NAME with no value; the heap path
                            // (arrays) or a name-only mention renders it. Every other kind needs a value.
                            if (valueKind == null) {
                                out[resolvedName] = TagValue(null, null)
                            } else if (value != null) {
                                out[resolvedName] = TagValue(valueKind, value)
                            }
                        }
                        inTag = false; name = null; value = null; valueKind = null
                    }
                }
                "assignment" -> {
                    if (!inTag || !step.has("value") || !step.get("value").isJsonObject) {
                        continue
                    }
                    val v = step.getAsJsonObject("value")
                    when (str(v, "name")) {
                        "pointer" -> {
                            val d = str(v, "data")
                            if (d != null && d.startsWith(STRING_LITERAL_PREFIX)) {
                                val literal = d.removePrefix(STRING_LITERAL_PREFIX)
                                // The FIRST string literal is the NAME (arg0); for a String-valued tag the
                                // SECOND is the value. For an object tag the value pointer isn't a literal,
                                // so only the name is captured (value stays null -> object handle).
                                if (name == null) {
                                    name = literal
                                } else if (valueKind == "string" && value == null) {
                                    value = literal
                                }
                            }
                        }
                        "integer" -> if (value == null) value = str(v, "data")
                        "boolean" -> if (value == null) value = str(v, "data")
                        "float", "double" -> if (value == null) value = str(v, "data")
                    }
                }
            }
        }
        return out
    }

    /** The binding kind for a `recordNondet` overload, from its descriptor in the frame [id]
     *  (`...recordNondet:(Ljava/lang/String;<X>)V`): `J/Z/F/D` map to the scalar kinds, a trailing
     *  `String` to `"string"`, and a trailing `Object` to `null` (an object handle, no scalar value). */
    private fun recordNondetValueKind(id: String): String? = when {
        id.contains(";J)") -> "integer"
        id.contains(";Z)") -> "boolean"
        id.contains(";F)") -> "float"
        id.contains(";D)") -> "double"
        id.contains(";Ljava/lang/String;)") -> "string"
        else -> null // Ljava/lang/Object; -> object handle
    }

    private fun collectCounterexample(step: JsonObject, entryPrefix: String?,
                                      userCode: WitnessUserCode?,
                                      inputs: MutableMap<String, String>,
                                      inputKinds: MutableMap<String, String>) {
        val lhs = str(step, "lhs")
        // Cheap synthetic pre-filter ($stack, *tmp, __CPROVER_*, *_return_value) before any class read.
        if (lhs.isNullOrEmpty() || !isUserVariable(lhs)) {
            return
        }
        // The function this assignment is attributed to (the trace stamps the CALLER's frame here).
        val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
        val fn = if (loc != null) str(loc, "function") else null
        if (!isUserDeclaredLocal(fn, lhs, entryPrefix, userCode)) {
            return
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
        // First assignment wins: keep the input value, before any in-loop/callee mutation of a
        // same-named variable. The proof input is bound BEFORE the algorithm-under-test mutates a
        // same-named param (e.g. a tailrec lowered to a loop that reassigns `n`, or a callee whose
        // param shares the input name), and a callee param-binding first value is the input too. Guard
        // BOTH maps on the same "absent" condition so they never diverge; the LinkedHashMap preserves
        // the insertion order of first-seen names.
        if (lhs !in inputs) {
            inputs[lhs] = data
            inputKinds[lhs] = kind
        }
    }

    /**
     * The shared "is [name], attributed to frame [fn], a real user-declared input?" discrimination —
     * used by BOTH the scalar [collectCounterexample] and the array reconstruction ([ArrayHeap]) so
     * they keep the same inputs. With a classpath ([userCode]): require a user-owned frame (a
     * directory-compiled, non-reserved class — never a package-prefix guess) AND that the name is an
     * actual LocalVariableTable entry of that method (drops engine synthetics like the nondet `i` jbmc
     * mints for a `Bmc.anyInt()` call; NO_TABLE degrades to keep-this-frame). Without a classpath (the
     * pure-parser unit tests / engine canary): restrict to the proof method's own frame via [entryPrefix].
     */
    private fun isUserDeclaredLocal(fn: String?, name: String, entryPrefix: String?,
                                    userCode: WitnessUserCode?): Boolean {
        if (userCode != null) {
            if (!userCode.isUserFrame(fn)) {
                return false
            }
            return userCode.checkLocal(fn, name) != WitnessUserCode.LocalCheck.UNDECLARED
        }
        if (entryPrefix != null) {
            return fn != null && fn.startsWith(entryPrefix)
        }
        return true
    }

    /**
     * Reconstructs ARRAY inputs from a refutation trace. A Java `int[]`/`long[]` is not a flat value in
     * JBMC's `--json-ui` trace; it surfaces as a three-link heap chain that we harvest across the trace
     * and stitch back together in [resolveArrays]:
     *
     *  1. the proof-local variable (`a`) is a `pointer` whose `type` is `struct java::array[…] *` and
     *     whose `data` is a heap object id (`dynamic_object$0`) — recorded FIRST-WINS per name, and only
     *     for a real user-declared local (same [isUserDeclaredLocal] discrimination as the scalar path);
     *  2. that object's backing store is an assignment to `dynamic_object$N.data` (a `pointer` whose
     *     `data` is the backing-array id, e.g. `dynamic_array`) — recorded last-wins (the settled link);
     *  3. the concrete elements are per-index assignments to that backing store (`dynamic_array[<i>L]`),
     *     each an `integer` value with a primitive `type` (`int`/`long`/…) — recorded last-per-index
     *     (the symbolic fill overwrites the `new T[n]` zero-init, and the proof under test does not
     *     mutate its own input array).
     *
     * The element `type` (`int`/`long`) yields the binding kind (`int[]`/`long[]`) the replay renderer
     * keys on. This is DISPLAY-ONLY reconstruction — it never affects the verdict — and pins the shape
     * jbmc 6.9.0 actually emits (covered by the parser unit tests; the engine identity is in the
     * verdict-cache key, so a bump forces re-validation, same discipline as the scalar witness path).
     */
    private class ArrayHeap {
        // user array variable name -> heap object id (dynamic_object$N), FIRST-WINS.
        private val arrayVars = LinkedHashMap<String, String>()
        // heap object id -> its backing-store id (dynamic_array), last-wins.
        private val objectBacking = HashMap<String, String>()
        // backing-store id -> (index -> element data), last-per-index.
        private val backingElems = HashMap<String, MutableMap<Int, String>>()
        // backing-store id -> element primitive type (int/long/…), from the per-index value's `type`.
        private val backingType = HashMap<String, String>()

        /** Folds one `assignment` step into the heap maps. Pure bookkeeping; never throws. */
        fun observe(step: JsonObject, entryPrefix: String?, userCode: WitnessUserCode?) {
            val lhs = str(step, "lhs")
            if (lhs.isNullOrEmpty()
                    || !step.has("value") || !step.get("value").isJsonObject) {
                return
            }
            val value = step.getAsJsonObject("value")
            // (1) a user-declared array variable: an array-typed pointer in the user's own frame.
            if (isUserVariable(lhs) && str(value, "name") == "pointer" && isArrayPointerType(str(value, "type"))) {
                val obj = str(value, "data")
                if (obj != null && obj != "null" && lhs !in arrayVars) {
                    val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
                    val fn = if (loc != null) str(loc, "function") else null
                    if (isUserDeclaredLocal(fn, lhs, entryPrefix, userCode)) {
                        arrayVars[lhs] = obj
                    }
                }
                return
            }
            // (2) the object's backing store: `dynamic_object$N.data` -> backing-array id.
            val backing = OBJECT_DATA_RE.matchEntire(lhs)
            if (backing != null && str(value, "name") == "pointer") {
                val bid = str(value, "data")
                if (bid != null && bid != "null") {
                    objectBacking[backing.groupValues[1]] = bid
                }
                return
            }
            // (3) a per-index element write: `<backing>[<i>L] = <integer>`.
            val elem = ELEMENT_RE.matchEntire(lhs)
            if (elem != null && str(value, "name") == "integer") {
                val data = str(value, "data") ?: return
                val bid = elem.groupValues[1]
                val idx = elem.groupValues[2].toIntOrNull() ?: return
                backingElems.getOrPut(bid) { LinkedHashMap() }[idx] = data
                str(value, "type")?.let { backingType[bid] = it }
            }
        }

        /** Stitch every recorded array variable into a [ResolvedArray], in first-seen name order.
         *  An array that couldn't be fully resolved (no backing store / no elements) is dropped. */
        fun resolveArrays(): Map<String, ResolvedArray> {
            val out = LinkedHashMap<String, ResolvedArray>()
            for ((name, obj) in arrayVars) {
                val backing = objectBacking[obj] ?: continue
                val elems = backingElems[backing] ?: continue
                if (elems.isEmpty()) {
                    continue
                }
                val ordered = elems.toSortedMap()
                val values = ordered.values.toList()
                out[name] = ResolvedArray(values, arrayKind(backingType[backing]))
            }
            return out
        }

        /** `int`/`long`/… element type -> the binding kind (`int[]`/`long[]`/…) the renderer keys on. */
        private fun arrayKind(elementType: String?): String =
                if (elementType.isNullOrBlank()) "array" else "$elementType[]"

        companion object {
            private val OBJECT_DATA_RE = Regex("""^(dynamic_object\$\d+)\.data$""")
            private val ELEMENT_RE = Regex("""^([A-Za-z_][A-Za-z0-9_$]*)\[(\d+)L?]$""")

            /** True for a JBMC array-handle pointer type, e.g. `struct java::array[int] *`. */
            private fun isArrayPointerType(type: String?): Boolean =
                    type != null && type.contains("java::array[")
        }
    }

    /** A reconstructed array input: its concrete element data in index order, plus the binding kind
     *  (`int[]`/`long[]`). [render] is the human-readable `[e0, e1, …]` display form. */
    private class ResolvedArray(val elements: List<String>, val kind: String) {
        fun render(): String = elements.joinToString(", ", "[", "]")
    }

    /**
     * Reject compiler/JBMC synthetics ($stack, return_tmp, __CPROVER_*, *_return_value). A cheap
     * name-only PRE-filter; the authoritative "is this a real, user-declared input" test is the
     * LocalVariableTable check in [collectCounterexample] via [WitnessUserCode].
     */
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

    /**
     * Recover a `(file, line)` for a property that carries no location of its own: prefer the `failure`
     * step's own sourceLocation, else the LAST trace step before/at the failure that bears a `file` (the
     * offending line — e.g. the divide site). Returns null when no trace step carries a location. Pure.
     */
    private fun failureStepLocation(trace: JsonArray): Pair<String, Int>? {
        var last: Pair<String, Int>? = null
        for (se in trace) {
            val step = se.asJsonObject
            val type = str(step, "stepType")
            val loc = if (step.has("sourceLocation")) step.getAsJsonObject("sourceLocation") else null
            val f = if (loc != null) str(loc, "file") else null
            if (f != null) {
                last = f to (if (loc != null) intOr(loc, "line", 0) else 0)
            }
            if (type == "failure") {
                // The failure's own location wins when present; otherwise the most recent located step.
                return if (f != null) (f to (if (loc != null) intOr(loc, "line", 0) else 0)) else last
            }
        }
        return last
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
