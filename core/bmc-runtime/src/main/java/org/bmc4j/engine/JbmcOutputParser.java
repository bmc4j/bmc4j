package org.bmc4j.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses JBMC {@code --json-ui --trace} output into a {@link JbmcResult}.
 *
 * <p>The output is a JSON array of message objects; one carries a {@code result}
 * array of properties. For each {@code FAILURE} property we extract the violation
 * location, reconstruct the active call stack from the trace's
 * {@code function-call}/{@code function-return} steps, and pull out the symbolic
 * input assignments that constitute the counterexample.
 */
public final class JbmcOutputParser {

    private JbmcOutputParser() {
    }

    public static JbmcResult parse(String json, String entryFunctionFqn) {
        JsonArray root;
        try {
            root = JsonParser.parseString(json).getAsJsonArray();
        } catch (RuntimeException e) {
            // Unparseable engine output: we can neither verify nor refute, so the verdict
            // is UNKNOWN (undecided), not a silent pass. Fails the test with the undecided framing.
            return JbmcResult.unknown("JBMC produced output bmc4j could not parse", json);
        }
        // Harvest the nondet-stub fact from the engine message stream once, regardless of
        // verdict — policy (footnote / strict-UNKNOWN) is applied later by the caller. Attached to the
        // computed verdict below via withStubbedMethods (a no-op when empty).
        return parseVerdict(root, json, entryFunctionFqn).withStubbedMethods(harvestStubs(root));
    }

    private static JbmcResult parseVerdict(JsonArray root, String json, String entryFunctionFqn) {
        JsonArray result = null;
        for (JsonElement e : root) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            if (o.has("result")) {
                result = o.getAsJsonArray("result");
            }
        }

        List<JbmcResult.Violation> violations = new ArrayList<>();
        int markers = 0;            // injected reachability markers seen
        int markersFailed = 0;      // ... that FAILED (i.e. that exit is reachable)
        int unwindingFailures = 0;  // --unwinding-assertions firings: the BOUND is too small
        if (result != null) {
            for (JsonElement pe : result) {
                JsonObject p = pe.getAsJsonObject();
                String status = str(p, "status");
                if (isReachabilityMarker(p)) {
                    markers++;
                    if ("FAILURE".equals(status)) {
                        markersFailed++;
                    }
                    continue; // never report a marker as a user violation
                }
                if ("FAILURE".equals(status)) {
                    if (isUnwindingAssertion(p)) {
                        // NOT a counterexample: an unwinding-assertion failure says the loop
                        // bound truncated exploration — the analysis is incomplete, nothing was
                        // proven wrong. Counted separately and judged below; reporting it as a
                        // violation would mislabel "bound too small" as REFUTED, and an
                        // expect = REFUTED demo could pass for the wrong reason.
                        unwindingFailures++;
                        continue;
                    }
                    violations.add(toViolation(p, entryFunctionFqn));
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
            if (!violations.isEmpty()) {
                return new JbmcResult(false, violations, json);
            }
            if (unwindingFailures > 0) {
                return JbmcResult.unknown(unwindingReason(unwindingFailures), json);
            }
            if (markersFailed == 0) {
                return new JbmcResult(false, List.of(vacuityViolation()), json, true);
            }
            return new JbmcResult(true, violations, json);
        }
        // No reachability markers. Every real @BmcProof run carries markers (ReachabilityBytecode
        // replaces every return), so a marker-less run is anomalous and CANNOT be passed soundly: the
        // vacuity check never ran (a return-less while(true)/always-throws proof emits no marker, so
        // assume(false) could "pass"), and array-valid output with no result/cProverStatus would
        // otherwise read as VERIFIED. A real user FAILURE is still a refutation; otherwise the verdict
        // is UNKNOWN, not a silent green — we never default a missing status to "ok".
        if (!violations.isEmpty()) {
            return new JbmcResult(false, violations, json);
        }
        if (unwindingFailures > 0) {
            return JbmcResult.unknown(unwindingReason(unwindingFailures), json);
        }
        return JbmcResult.unknown(
                "reachability markers missing — vacuity could not be checked", json);
    }

    /**
     * True if a FAILURE property is an {@code --unwinding-assertions} firing rather than a user
     * property. The bound truncates exploration in two shapes, and BOTH are incompleteness, never a
     * counterexample: a <b>loop</b> overrun is named {@code <function>.unwind.<n>} with description
     * {@code "unwinding assertion loop <n>"}; a <b>recursion</b> overrun is named
     * {@code <function>.recursion} with description {@code "recursion unwinding assertion"}. Either
     * signal suffices per shape (defensive OR — both shapes are pinned against the bundled engine by
     * the parser tests).
     */
    private static boolean isUnwindingAssertion(JsonObject p) {
        String property = str(p, "property");
        if (property != null && (property.contains(".unwind.") || property.endsWith(".recursion"))) {
            return true;
        }
        String description = str(p, "description");
        return description != null
                && (description.startsWith("unwinding assertion")
                        || description.startsWith("recursion unwinding assertion"));
    }

    /** The UNKNOWN reason for a bound-too-small run (the extension appends the remedies). */
    private static String unwindingReason(int count) {
        return "unwinding assertion failed: the unwind bound is too small to cover this proof ("
                + count + (count == 1 ? " loop/recursion" : " loops/recursions") + " hit the bound)"
                + " — exploration was truncated, so this is incompleteness, not a refutation";
    }

    /**
     * Marker phrase JBMC stamps on a method it had no body for and stubbed to nondet. In cbmc 6.9.0's
     * {@code --json-ui} stream (at {@code --verbosity 10}) these surface as STATUS-MESSAGEs of the form
     * {@code "Generating codet:  new opaque symbol: method 'java::pkg.Class.method:(sig)'"}. This is the
     * engine's own term for an unmodeled callee — the soundness fact we harvest. The format
     * is not an engine contract (pinned by {@link JbmcOutputParserTest} against the bundled version; the
     * engine identity is in the verdict-cache key, so a bump forces re-validation).
     */
    private static final String OPAQUE_MARKER = "new opaque symbol: method '";

    /**
     * Harvest the methods JBMC analyzed as nondet stubs: scan every message for the engine's
     * "opaque symbol" marker, extract the {@code pkg.Class.method} (dropping the {@code java::} prefix
     * and {@code :(signature)} suffix), filter to {@linkplain StubFilter#isSignal signal}, and dedupe in
     * first-seen order. Empty when the reachable slice was fully modeled. Pure; never throws.
     */
    static List<String> harvestStubs(JsonArray root) {
        Set<String> stubs = new LinkedHashSet<>();
        for (JsonElement e : root) {
            if (!e.isJsonObject()) {
                continue;
            }
            String text = str(e.getAsJsonObject(), "messageText");
            if (text == null) {
                continue;
            }
            int at = text.indexOf(OPAQUE_MARKER);
            if (at < 0) {
                continue;
            }
            int start = at + OPAQUE_MARKER.length();
            int end = text.indexOf('\'', start);
            String symbol = end > start ? text.substring(start, end) : text.substring(start);
            String fqn = methodFqn(symbol);
            if (fqn != null && StubFilter.isSignal(fqn)) {
                stubs.add(fqn);
            }
        }
        return new ArrayList<>(stubs);
    }

    /** {@code java::pkg.Class.method:(sig)ret} -> {@code pkg.Class.method} (null if unrecognizable). */
    private static String methodFqn(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String s = symbol.startsWith("java::") ? symbol.substring("java::".length()) : symbol;
        int sig = s.indexOf(":(");
        if (sig >= 0) {
            s = s.substring(0, sig);
        }
        return s.isBlank() ? null : s;
    }

    /** The dedicated violation describing an unsatisfiable-assumptions (vacuous) proof. */
    private static JbmcResult.Violation vacuityViolation() {
        return new JbmcResult.Violation(BmcReachability.VACUOUS_MESSAGE, null, 0,
                new ArrayList<>(), new ArrayList<>());
    }

    /**
     * True if {@code property} is an injected reachability marker — identified by the
     * {@linkplain BmcReachability#SENTINEL_LINE sentinel source line} stamped on it by
     * {@link ReachabilityBytecode}. Robust against any assertion the user writes inside a proof.
     */
    private static boolean isReachabilityMarker(JsonObject property) {
        JsonObject sl = property.has("sourceLocation") ? property.getAsJsonObject("sourceLocation") : null;
        return sl != null && BmcReachability.isMarkerLine(intOr(sl, "line", -1));
    }

    private static JbmcResult.Violation toViolation(JsonObject property, String entryFunctionFqn) {
        String description = str(property, "description");
        JsonObject sl = property.has("sourceLocation") ? property.getAsJsonObject("sourceLocation") : null;
        String file = sl != null ? str(sl, "file") : null;
        int line = sl != null ? intOr(sl, "line", 0) : 0;

        List<StackTraceElement> stack = new ArrayList<>();
        List<String> counterexample = new ArrayList<>();
        List<JbmcResult.Binding> bindings = new ArrayList<>();

        if (property.has("trace")) {
            buildStackAndCounterexample(property.getAsJsonArray("trace"), file, line,
                    entryFunctionFqn, stack, counterexample, bindings);
        }
        if (stack.isEmpty() && file != null) {
            stack.add(frame(property.has("sourceLocation")
                    ? str(property.getAsJsonObject("sourceLocation"), "function") : null, file, line));
        }

        // A Bmc.check(...) failure surfaces as an assertion inside our own code.
        // Re-point it at the proof line and give it a clean description before we
        // drop the internal frames.
        boolean internalCheck = isInternalFile(file);
        StackTraceElement userFrame = firstUserFrame(stack);
        if (internalCheck) {
            description = "a checked property does not hold";
            if (userFrame != null) {
                file = userFrame.getFileName();
                line = userFrame.getLineNumber();
            }
        }

        // Hide bmc-runtime / CProver plumbing from the reported stack trace.
        stack.removeIf(JbmcOutputParser::isInternalFrame);

        return new JbmcResult.Violation(description, file, line, stack, counterexample, bindings);
    }

    /** Reconstruct the call stack live at the failure, plus input assignments. */
    private static void buildStackAndCounterexample(JsonArray trace, String failFile, int failLine,
                                                    String entryFunctionFqn,
                                                    List<StackTraceElement> stack, List<String> counterexample,
                                                    List<JbmcResult.Binding> bindings) {
        Deque<Frame> active = new ArrayDeque<>();
        String failFunction = null;
        // name -> final value, restricted to the proof method's own variables.
        java.util.LinkedHashMap<String, String> inputs = new java.util.LinkedHashMap<>();
        // name -> JBMC value kind, parallel to `inputs` (for structured replay rendering).
        java.util.LinkedHashMap<String, String> inputKinds = new java.util.LinkedHashMap<>();
        String entryPrefix = entryFunctionFqn != null ? "java::" + entryFunctionFqn : null;

        for (JsonElement se : trace) {
            JsonObject step = se.getAsJsonObject();
            String type = str(step, "stepType");
            if (type == null) {
                continue;
            }
            switch (type) {
                case "function-call": {
                    String id = funcId(step);
                    if (isUserFunction(id)) {
                        JsonObject loc = step.has("sourceLocation") ? step.getAsJsonObject("sourceLocation") : null;
                        active.push(new Frame(id, locFile(loc), locLine(loc)));
                    }
                    break;
                }
                case "function-return": {
                    String id = funcId(step);
                    if (isUserFunction(id) && !active.isEmpty()) {
                        // Pop the matching frame (LIFO; ids line up in well-formed traces).
                        active.pop();
                    }
                    break;
                }
                case "assignment": {
                    collectCounterexample(step, entryPrefix, inputs, inputKinds);
                    break;
                }
                case "failure": {
                    JsonObject loc = step.has("sourceLocation") ? step.getAsJsonObject("sourceLocation") : null;
                    if (loc != null) {
                        failFunction = str(loc, "function");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        inputs.forEach((k, v) -> {
            counterexample.add(k + " = " + v);
            bindings.add(new JbmcResult.Binding(k, inputKinds.get(k), v));
        });

        // active (top -> bottom) = [innermost callee, ..., entry]
        List<Frame> frames = new ArrayList<>(active); // ArrayDeque iterator is head(top)->tail(bottom)
        if (frames.isEmpty()) {
            return;
        }
        // Innermost frame at the actual failure line.
        String innermostFn = failFunction != null ? failFunction : frames.get(0).id;
        stack.add(frame(innermostFn, failFile, failLine));
        // Each caller is rendered at the call site of the frame below it.
        for (int i = 0; i < frames.size() - 1; i++) {
            Frame callee = frames.get(i);          // its call site lies in the caller
            Frame caller = frames.get(i + 1);
            stack.add(frame(caller.id, callee.callFile, callee.callLine));
        }
    }

    private static void collectCounterexample(JsonObject step, String entryPrefix,
                                              java.util.Map<String, String> inputs,
                                              java.util.Map<String, String> inputKinds) {
        String lhs = str(step, "lhs");
        if (lhs == null || lhs.isEmpty() || !isUserVariable(lhs)) {
            return;
        }
        // Restrict to variables declared in the proof method itself — these are the
        // inputs the developer cares about, not compiler/JBMC temporaries.
        if (entryPrefix != null) {
            JsonObject loc = step.has("sourceLocation") ? step.getAsJsonObject("sourceLocation") : null;
            String fn = loc != null ? str(loc, "function") : null;
            if (fn == null || !fn.startsWith(entryPrefix)) {
                return;
            }
        }
        if (!step.has("value") || !step.get("value").isJsonObject()) {
            return;
        }
        JsonObject value = step.getAsJsonObject("value");
        String kind = str(value, "name");
        // Only show concrete primitive inputs, not pointers/objects/internals.
        if (kind == null || !(kind.equals("integer") || kind.equals("boolean")
                || kind.equals("float") || kind.equals("double"))) {
            return;
        }
        String data = str(value, "data");
        if (data == null) {
            return;
        }
        inputs.put(lhs, data); // last assignment wins -> final counterexample value
        inputKinds.put(lhs, kind);
    }

    /** Reject compiler/JBMC synthetics ($stack, return_tmp, __CPROVER_*, *_return_value). */
    private static boolean isUserVariable(String name) {
        if (!isSimpleName(name)) {
            return false;
        }
        if (name.indexOf('$') >= 0 || name.startsWith("__")) {
            return false;
        }
        return !name.contains("tmp")
                && !name.endsWith("_return_value")
                && !name.startsWith("return_")
                && !name.equals("to_return");
    }

    // --- helpers -------------------------------------------------------------

    private static final class Frame {
        final String id;
        final String callFile;
        final int callLine;

        Frame(String id, String callFile, int callLine) {
            this.id = id;
            this.callFile = callFile;
            this.callLine = callLine;
        }
    }

    private static boolean isInternalFile(String file) {
        if (file == null) {
            return false;
        }
        String f = file.replace('\\', '/');
        return f.endsWith("org/bmc4j/Bmc.java") || f.startsWith("org/cprover/");
    }

    private static boolean isInternalFrame(StackTraceElement e) {
        String c = e.getClassName();
        return c.startsWith("org.bmc4j.") || c.startsWith("org.cprover.");
    }

    private static StackTraceElement firstUserFrame(List<StackTraceElement> stack) {
        for (StackTraceElement e : stack) {
            if (!isInternalFrame(e)) {
                return e;
            }
        }
        return null;
    }

    private static boolean isUserFunction(String id) {
        return id != null && id.startsWith("java::") && !id.contains("<clinit");
    }

    private static String funcId(JsonObject step) {
        if (step.has("function") && step.get("function").isJsonObject()) {
            return str(step.getAsJsonObject("function"), "identifier");
        }
        return null;
    }

    private static String locFile(JsonObject loc) {
        return loc != null ? str(loc, "file") : null;
    }

    private static int locLine(JsonObject loc) {
        return loc != null ? intOr(loc, "line", 0) : 0;
    }

    /**
     * Turn a JBMC function id like {@code java::pkg.Class.method:(I)I} plus a
     * source location into a StackTraceElement.
     */
    private static StackTraceElement frame(String functionId, String file, int line) {
        String className = "unknown";
        String method = "proof";
        if (functionId != null) {
            String s = functionId.startsWith("java::") ? functionId.substring("java::".length()) : functionId;
            int sig = s.indexOf(":(");
            if (sig >= 0) {
                s = s.substring(0, sig);
            }
            int lastDot = s.lastIndexOf('.');
            if (lastDot > 0) {
                className = s.substring(0, lastDot);
                method = s.substring(lastDot + 1);
            } else {
                method = s;
            }
        }
        String fileName = file;
        if (fileName != null) {
            int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
            if (slash >= 0) {
                fileName = fileName.substring(slash + 1);
            }
        }
        return new StackTraceElement(className, method, fileName, line);
    }

    private static boolean isSimpleName(String s) {
        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        String v = str(o, key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
