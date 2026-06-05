package org.bmc4j.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a refuted proof's counterexample as a <em>replayable</em> block of concrete Java. When JBMC refutes a proof it already hands back the symbolic input assignments that trigger
 * the violation; this turns those back into source the developer can paste into a scratch test and
 * step through in a debugger — closing the refute&rarr;debug loop.
 *
 * <p><b>What it handles soundly:</b>
 * <ul>
 *   <li><b>primitives</b> — {@code int/long/short/byte/char/boolean/float/double}, rendered as valid
 *       Java literals (correct suffixes, {@code char} as a quoted/escaped literal, the IEEE-754 edge
 *       cases NaN/&plusmn;Inf as their {@code Double}/{@code Float} constants);</li>
 *   <li><b>String</b> — emitted as a properly escaped Java string literal (quotes, backslashes,
 *       newlines/tabs, other non-printables as {@code \\uXXXX});</li>
 *   <li><b>enums / {@code Bmc.anyOf(E.values())}</b> — rendered as the enum <em>constant</em>
 *       ({@code Suit.HEARTS}), never an array index, when the binding maps to an enum-typed proof
 *       parameter.</li>
 * </ul>
 *
 * <p><b>What it degrades:</b> anything that can't be expressed as a self-contained literal — cyclic
 * or deep object graphs, partial models, references with no reconstructible value — is emitted as a
 * clearly <b>commented</b> description rather than non-compiling code presented as runnable. The
 * renderer never claims to reproduce what it cannot.
 *
 * <p>Output is a small block prefixed {@code replay:}; an empty result (no reconstructible inputs)
 * yields no block at all. Verified / UNKNOWN / vacuous outcomes never reach here.
 */
public final class ReplayRenderer {

    private ReplayRenderer() {
    }

    /**
     * Render the {@code replay:} block for a violation, or {@code null} when there is nothing
     * reconstructible (so the caller emits no block).
     *
     * @param entryFunctionFqn fully-qualified {@code pkg.Class.method} of the proof method
     * @param proofMethod      the reflected proof method (for declared parameter types: enums,
     *                         strings, objects); may be {@code null} if unavailable
     * @param violation        the refuted property carrying the structured counterexample bindings
     */
    public static String render(String entryFunctionFqn, Method proofMethod,
                                JbmcResult.Violation violation) {
        if (violation == null) {
            return null;
        }
        List<JbmcResult.Binding> bindings = violation.bindings();
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        List<String> degraded = new ArrayList<>();
        for (JbmcResult.Binding b : bindings) {
            String rendered = renderBinding(b, proofMethod);
            if (rendered != null) {
                lines.add(rendered);
            } else {
                degraded.add("    // " + b.name() + ": could not express "
                        + describeKind(b) + " as a literal — inspect the counterexample above");
            }
        }
        if (lines.isEmpty() && degraded.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    replay:\n");
        for (String l : lines) {
            sb.append("      ").append(l).append('\n');
        }
        for (String d : degraded) {
            sb.append("  ").append(d).append('\n');
        }
        // A re-invocation hint: with no parameters the proof body re-runs the locals above; with
        // parameters, the developer calls the proof's target with them. We can't know the exact
        // call (the proof body may do more than one call), so we point at the proof method itself.
        if (entryFunctionFqn != null) {
            sb.append("      // then run the body of ").append(simpleEntry(entryFunctionFqn))
                    .append(" with these value(s)\n");
        }
        return sb.toString().stripTrailing();
    }

    /** One {@code <type> name = literal;} line, or {@code null} to signal the degrade path. */
    private static String renderBinding(JbmcResult.Binding b, Method proofMethod) {
        String name = b.name();
        if (name == null || name.isEmpty()) {
            return null;
        }
        Class<?> declared = declaredType(name, proofMethod);

        // Enum parameter: render the constant, not the int index JBMC picked.
        if (declared != null && declared.isEnum() && isInteger(b.kind())) {
            Object[] consts = declared.getEnumConstants();
            Integer idx = parseIntOrNull(b.data());
            if (consts != null && idx != null && idx >= 0 && idx < consts.length) {
                return declared.getSimpleName() + " " + name + " = "
                        + declared.getSimpleName() + "." + ((Enum<?>) consts[idx]).name() + ";";
            }
            return null; // index out of range / unparseable -> degrade
        }

        // String binding (the parser tags it kind="string" with already-decoded data).
        if ("string".equals(b.kind())) {
            if (b.data() == null) {
                return null;
            }
            return "String " + name + " = " + javaStringLiteral(b.data()) + ";";
        }

        // Primitives. Prefer the declared parameter type when known (so a `long`/`short`/`byte`
        // param renders with the right type + suffix); otherwise infer from the JBMC kind.
        String kind = b.kind();
        String data = b.data();
        if (data == null) {
            return null;
        }
        if (declared == int.class || (declared == null && "integer".equals(kind))) {
            Long v = parseLongOrNull(data);
            return v != null ? "int " + name + " = " + v + ";" : null;
        }
        if (declared == long.class) {
            Long v = parseLongOrNull(data);
            return v != null ? "long " + name + " = " + v + "L;" : null;
        }
        if (declared == short.class) {
            Long v = parseLongOrNull(data);
            return v != null ? "short " + name + " = (short) " + v + ";" : null;
        }
        if (declared == byte.class) {
            Long v = parseLongOrNull(data);
            return v != null ? "byte " + name + " = (byte) " + v + ";" : null;
        }
        if (declared == char.class) {
            Integer v = parseIntOrNull(data);
            return v != null && v >= 0 && v <= 0xFFFF
                    ? "char " + name + " = " + charLiteral((char) (int) v) + ";" : null;
        }
        if (declared == boolean.class || "boolean".equals(kind)) {
            String bool = booleanLiteral(data);
            return bool != null ? "boolean " + name + " = " + bool + ";" : null;
        }
        if (declared == double.class || (declared == null && "double".equals(kind))) {
            String lit = doubleLiteral(data);
            return lit != null ? "double " + name + " = " + lit + ";" : null;
        }
        if (declared == float.class || (declared == null && "float".equals(kind))) {
            String lit = floatLiteral(data);
            return lit != null ? "float " + name + " = " + lit + ";" : null;
        }
        if (declared == String.class && data != null) {
            return "String " + name + " = " + javaStringLiteral(data) + ";";
        }
        return null; // unknown / object / pointer -> degrade
    }

    /** The declared type of a proof parameter named {@code name}, or {@code null} (a local, or no info). */
    private static Class<?> declaredType(String name, Method proofMethod) {
        if (proofMethod == null) {
            return null;
        }
        for (var p : proofMethod.getParameters()) {
            if (p.isNamePresent() && p.getName().equals(name)) {
                return p.getType();
            }
        }
        return null;
    }

    private static String describeKind(JbmcResult.Binding b) {
        String k = b.kind();
        if (k == null) {
            return "value";
        }
        return switch (k) {
            case "pointer", "struct" -> "object/reference value";
            default -> k + " value";
        };
    }

    // --- literal rendering ----------------------------------------------------

    private static boolean isInteger(String kind) {
        return "integer".equals(kind);
    }

    /** A valid Java string literal for {@code s} (surrounding quotes included). */
    static String javaStringLiteral(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            sb.append(escapeChar(s.charAt(i), false));
        }
        sb.append('"');
        return sb.toString();
    }

    /** A valid Java {@code char} literal for {@code c} (surrounding single quotes included). */
    static String charLiteral(char c) {
        return "'" + escapeChar(c, true) + "'";
    }

    /** Escape one char for a string ({@code inChar=false}) or char ({@code inChar=true}) literal. */
    private static String escapeChar(char c, boolean inChar) {
        switch (c) {
            case '\\': return "\\\\";
            case '\n': return "\\n";
            case '\r': return "\\r";
            case '\t': return "\\t";
            case '\b': return "\\b";
            case '\f': return "\\f";
            case '\0': return "\\u0000";
            default:
                if (c == '"') {
                    return inChar ? "\"" : "\\\"";
                }
                if (c == '\'') {
                    return inChar ? "\\'" : "'";
                }
                if (c < 0x20 || c > 0x7E) {
                    return String.format("\\u%04x", (int) c);
                }
                return String.valueOf(c);
        }
    }

    private static String booleanLiteral(String data) {
        if (data == null) {
            return null;
        }
        String d = data.trim();
        if (d.equals("true") || d.equals("false")) {
            return d;
        }
        // JBMC sometimes renders booleans as 0/1.
        if (d.equals("1")) {
            return "true";
        }
        if (d.equals("0")) {
            return "false";
        }
        return null;
    }

    /** Render a double, mapping JBMC's textual NaN/Inf forms to compilable constants. */
    static String doubleLiteral(String data) {
        String special = specialFloat(data, "Double");
        if (special != null) {
            return special;
        }
        Double v = parseDoubleOrNull(data);
        if (v == null) {
            return null;
        }
        if (v.isNaN()) {
            return "Double.NaN";
        }
        if (v.isInfinite()) {
            return v > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
        }
        return v.toString();
    }

    /** Render a float, mapping JBMC's textual NaN/Inf forms to compilable constants. */
    static String floatLiteral(String data) {
        String special = specialFloat(data, "Float");
        if (special != null) {
            return special;
        }
        Double v = parseDoubleOrNull(data);
        if (v == null) {
            return null;
        }
        if (v.isNaN()) {
            return "Float.NaN";
        }
        if (v.isInfinite()) {
            return v > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
        }
        return ((float) (double) v) + "f";
    }

    private static String specialFloat(String data, String boxType) {
        if (data == null) {
            return null;
        }
        String d = data.trim();
        if (d.equalsIgnoreCase("NaN")) {
            return boxType + ".NaN";
        }
        if (d.equalsIgnoreCase("+Inf") || d.equalsIgnoreCase("Inf") || d.equalsIgnoreCase("Infinity")
                || d.equalsIgnoreCase("+Infinity")) {
            return boxType + ".POSITIVE_INFINITY";
        }
        if (d.equalsIgnoreCase("-Inf") || d.equalsIgnoreCase("-Infinity")) {
            return boxType + ".NEGATIVE_INFINITY";
        }
        return null;
    }

    private static String simpleEntry(String fqn) {
        int sig = fqn.indexOf(":(");
        String s = sig >= 0 ? fqn.substring(0, sig) : fqn;
        int dot = s.lastIndexOf('.');
        if (dot <= 0) {
            return s;
        }
        int prev = s.lastIndexOf('.', dot - 1);
        return s.substring(prev + 1); // Class.method
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
