package org.bmc4j.engine

import java.lang.reflect.Method

/**
 * Renders a refuted proof's counterexample as a *replayable* block of concrete Java. When JBMC
 * refutes a proof it already hands back the symbolic input assignments that trigger
 * the violation; this turns those back into source the developer can paste into a scratch test and
 * step through in a debugger — closing the refute→debug loop.
 *
 * **What it handles soundly:**
 * - **primitives** — `int/long/short/byte/char/boolean/float/double`, rendered as valid
 *   Java literals (correct suffixes, `char` as a quoted/escaped literal, the IEEE-754 edge
 *   cases NaN/±Inf as their `Double`/`Float` constants);
 * - **String** — emitted as a properly escaped Java string literal (quotes, backslashes,
 *   newlines/tabs, other non-printables as `\uXXXX`);
 * - **enums / `Bmc.anyOf(E.values())`** — rendered as the enum *constant*
 *   (`Suit.HEARTS`), never an array index, when the binding maps to an enum-typed proof
 *   parameter.
 *
 * **What it degrades:** anything that can't be expressed as a self-contained literal — cyclic
 * or deep object graphs, partial models, references with no reconstructible value — is emitted as a
 * clearly **commented** description rather than non-compiling code presented as runnable. The
 * renderer never claims to reproduce what it cannot.
 *
 * Output is a small block prefixed `replay:`; an empty result (no reconstructible inputs)
 * yields no block at all. Verified / UNKNOWN / vacuous outcomes never reach here.
 */
object ReplayRenderer {

    /**
     * Render the `replay:` block for a violation, or `null` when there is nothing
     * reconstructible (so the caller emits no block).
     *
     * @param entryFunctionFqn fully-qualified `pkg.Class.method` of the proof method
     * @param proofMethod      the reflected proof method (for declared parameter types: enums,
     *                         strings, objects); may be `null` if unavailable
     * @param violation        the refuted property carrying the structured counterexample bindings
     */
    @JvmStatic
    fun render(entryFunctionFqn: String?, proofMethod: Method?,
               violation: JbmcResult.Violation?): String? {
        if (violation == null) {
            return null
        }
        val bindings = violation.bindings
        if (bindings.isEmpty()) {
            return null
        }

        val lines = mutableListOf<String>()
        val degraded = mutableListOf<String>()
        for (b in bindings) {
            val rendered = renderBinding(b, proofMethod)
            if (rendered != null) {
                lines.add(rendered)
            } else {
                degraded.add("    // " + b.name + ": could not express " +
                        describeKind(b) + " as a literal — inspect the counterexample above")
            }
        }
        if (lines.isEmpty() && degraded.isEmpty()) {
            return null
        }

        return buildString {
            append("    replay:\n")
            for (l in lines) {
                append("      ").append(l).append('\n')
            }
            for (d in degraded) {
                append("  ").append(d).append('\n')
            }
            // A re-invocation hint: with no parameters the proof body re-runs the locals above; with
            // parameters, the developer calls the proof's target with them. We can't know the exact
            // call (the proof body may do more than one call), so we point at the proof method itself.
            if (entryFunctionFqn != null) {
                append("      // then run the body of ").append(simpleEntry(entryFunctionFqn))
                        .append(" with these value(s)\n")
            }
        }.trimEnd()
    }

    /** One `<type> name = literal;` line, or `null` to signal the degrade path. */
    private fun renderBinding(b: JbmcResult.Binding, proofMethod: Method?): String? {
        val name = b.name
        if (name.isEmpty()) {
            return null
        }
        val declared = declaredType(name, proofMethod)

        // Enum parameter: render the constant, not the int index JBMC picked.
        if (declared != null && declared.isEnum && b.kind == "integer") {
            val consts = declared.enumConstants
            val idx = parseIntOrNull(b.data)
            if (consts != null && idx != null && idx >= 0 && idx < consts.size) {
                return declared.simpleName + " " + name + " = " +
                        declared.simpleName + "." + (consts[idx] as Enum<*>).name + ";"
            }
            return null // index out of range / unparseable -> degrade
        }

        // String binding (the parser tags it kind="string" with already-decoded data).
        if (b.kind == "string") {
            val data = b.data ?: return null
            return "String " + name + " = " + javaStringLiteral(data) + ";"
        }

        // Primitives. Prefer the declared parameter type when known (so a `long`/`short`/`byte`
        // param renders with the right type + suffix); otherwise infer from the JBMC kind.
        val kind = b.kind
        val data = b.data ?: return null
        if (declared == Integer.TYPE || (declared == null && kind == "integer")) {
            val v = parseLongOrNull(data)
            return if (v != null) "int $name = $v;" else null
        }
        if (declared == java.lang.Long.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) "long $name = ${v}L;" else null
        }
        if (declared == java.lang.Short.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) "short $name = (short) $v;" else null
        }
        if (declared == java.lang.Byte.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) "byte $name = (byte) $v;" else null
        }
        if (declared == Character.TYPE) {
            val v = parseIntOrNull(data)
            return if (v != null && v in 0..0xFFFF)
                "char $name = ${charLiteral(v.toChar())};" else null
        }
        if (declared == java.lang.Boolean.TYPE || kind == "boolean") {
            val bool = booleanLiteral(data)
            return if (bool != null) "boolean $name = $bool;" else null
        }
        if (declared == java.lang.Double.TYPE || (declared == null && kind == "double")) {
            val lit = doubleLiteral(data)
            return if (lit != null) "double $name = $lit;" else null
        }
        if (declared == java.lang.Float.TYPE || (declared == null && kind == "float")) {
            val lit = floatLiteral(data)
            return if (lit != null) "float $name = $lit;" else null
        }
        if (declared == String::class.java) {
            return "String " + name + " = " + javaStringLiteral(data) + ";"
        }
        return null // unknown / object / pointer -> degrade
    }

    /** The declared type of a proof parameter named [name], or `null` (a local, or no info). */
    private fun declaredType(name: String, proofMethod: Method?): Class<*>? {
        if (proofMethod == null) {
            return null
        }
        return proofMethod.parameters
                .firstOrNull { it.isNamePresent && it.name == name }
                ?.type
    }

    private fun describeKind(b: JbmcResult.Binding): String = when (val k = b.kind) {
        null -> "value"
        "pointer", "struct" -> "object/reference value"
        else -> "$k value"
    }

    // --- literal rendering ----------------------------------------------------

    /** A valid Java string literal for [s] (surrounding quotes included). */
    internal fun javaStringLiteral(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            append(escapeChar(c, false))
        }
        append('"')
    }

    /** A valid Java `char` literal for [c] (surrounding single quotes included). */
    internal fun charLiteral(c: Char): String = "'" + escapeChar(c, true) + "'"

    /** Escape one char for a string (`inChar=false`) or char (`inChar=true`) literal. */
    private fun escapeChar(c: Char, inChar: Boolean): String = when (c) {
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000C' -> "\\f"
        '\u0000' -> "\\u0000"
        '"' -> if (inChar) "\"" else "\\\""
        '\'' -> if (inChar) "\\'" else "'"
        else ->
            if (c < ' ' || c > '~') String.format("\\u%04x", c.code) else c.toString()
    }

    private fun booleanLiteral(data: String?): String? {
        val d = data?.trim() ?: return null
        return when (d) {
            "true", "false" -> d
            // JBMC sometimes renders booleans as 0/1.
            "1" -> "true"
            "0" -> "false"
            else -> null
        }
    }

    /** Render a double, mapping JBMC's textual NaN/Inf forms to compilable constants. */
    internal fun doubleLiteral(data: String?): String? {
        specialFloat(data, "Double")?.let { return it }
        val v = parseDoubleOrNull(data) ?: return null
        return when {
            v.isNaN() -> "Double.NaN"
            v.isInfinite() -> if (v > 0) "Double.POSITIVE_INFINITY" else "Double.NEGATIVE_INFINITY"
            else -> v.toString()
        }
    }

    /** Render a float, mapping JBMC's textual NaN/Inf forms to compilable constants. */
    internal fun floatLiteral(data: String?): String? {
        specialFloat(data, "Float")?.let { return it }
        val v = parseDoubleOrNull(data) ?: return null
        return when {
            v.isNaN() -> "Float.NaN"
            v.isInfinite() -> if (v > 0) "Float.POSITIVE_INFINITY" else "Float.NEGATIVE_INFINITY"
            else -> v.toFloat().toString() + "f"
        }
    }

    private fun specialFloat(data: String?, boxType: String): String? {
        val d = data?.trim() ?: return null
        return when {
            d.equals("NaN", ignoreCase = true) -> "$boxType.NaN"
            d.equals("+Inf", true) || d.equals("Inf", true)
                    || d.equals("Infinity", true) || d.equals("+Infinity", true) ->
                "$boxType.POSITIVE_INFINITY"
            d.equals("-Inf", true) || d.equals("-Infinity", true) -> "$boxType.NEGATIVE_INFINITY"
            else -> null
        }
    }

    private fun simpleEntry(fqn: String): String {
        val sig = fqn.indexOf(":(")
        val s = if (sig >= 0) fqn.substring(0, sig) else fqn
        val dot = s.lastIndexOf('.')
        if (dot <= 0) {
            return s
        }
        val prev = s.lastIndexOf('.', dot - 1)
        return s.substring(prev + 1) // Class.method
    }

    private fun parseIntOrNull(s: String?): Int? = s?.trim()?.toIntOrNull()

    private fun parseLongOrNull(s: String?): Long? = s?.trim()?.toLongOrNull()

    private fun parseDoubleOrNull(s: String?): Double? = s?.trim()?.toDoubleOrNull()
}
