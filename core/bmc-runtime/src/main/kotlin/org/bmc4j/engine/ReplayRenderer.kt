package org.bmc4j.engine

import java.lang.reflect.Method

/**
 * Renders a refuted proof's counterexample as a *replayable* block of concrete source. When JBMC
 * refutes a proof it already hands back the symbolic input assignments that trigger
 * the violation; this turns those back into source the developer can paste into a scratch test and
 * step through in a debugger — closing the refute→debug loop.
 *
 * The block is rendered in either **Java** or **Kotlin** ([Language]). Java is the historical
 * default and its output is unchanged; Kotlin mirrors the same bindings with `val` declarations and
 * Kotlin literal syntax (no `d`/`D` double suffix, `$` escaped in strings, explicit `Short`/`Byte`
 * types because bare integer literals are `Int`).
 *
 * **What it handles soundly:**
 * - **primitives** — `int/long/short/byte/char/boolean/float/double`, rendered as valid
 *   literals (correct suffixes, `char` as a quoted/escaped literal, the IEEE-754 edge
 *   cases NaN/±Inf as their `Double`/`Float` constants);
 * - **String** — emitted as a properly escaped string literal (quotes, backslashes,
 *   newlines/tabs, other non-printables as `\uXXXX`; in Kotlin, `$` escaped too);
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

    /** Target source language for a rendered replay block. */
    enum class Language { JAVA, KOTLIN }

    /**
     * Render the `replay:` block for a violation, or `null` when there is nothing
     * reconstructible (so the caller emits no block).
     *
     * @param entryFunctionFqn fully-qualified `pkg.Class.method` of the proof method
     * @param proofMethod      the reflected proof method (for declared parameter types: enums,
     *                         strings, objects); may be `null` if unavailable
     * @param violation        the refuted property carrying the structured counterexample bindings
     * @param language         JAVA (default, unchanged) or KOTLIN
     */
    @JvmStatic
    @JvmOverloads
    fun render(entryFunctionFqn: String?, proofMethod: Method?,
               violation: JbmcResult.Violation?, language: Language = Language.JAVA): String? {
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
            val rendered = renderBinding(b, proofMethod, language)
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

    /**
     * One binding declaration line (`<type> name = literal;` in Java, `val name[: Type] = literal`
     * in Kotlin), or `null` to signal the degrade path.
     */
    private fun renderBinding(b: JbmcResult.Binding, proofMethod: Method?,
                              language: Language): String? {
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
                val const = declared.simpleName + "." + (consts[idx] as Enum<*>).name
                return decl(language, declared.simpleName, name, const)
            }
            return null // index out of range / unparseable -> degrade
        }

        // String binding (the parser tags it kind="string" with already-decoded data).
        if (b.kind == "string") {
            val data = b.data ?: return null
            return decl(language, "String", name, stringLiteral(data, language))
        }

        // Primitives. Prefer the declared parameter type when known (so a `long`/`short`/`byte`
        // param renders with the right type + suffix); otherwise infer from the JBMC kind.
        val kind = b.kind
        val data = b.data ?: return null
        if (declared == Integer.TYPE || (declared == null && kind == "integer")) {
            val v = parseLongOrNull(data)
            return if (v != null) decl(language, "int", name, v.toString()) else null
        }
        if (declared == java.lang.Long.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) decl(language, "long", name, "${v}L") else null
        }
        if (declared == java.lang.Short.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) shortByteDecl(language, "short", "Short", name, v) else null
        }
        if (declared == java.lang.Byte.TYPE) {
            val v = parseLongOrNull(data)
            return if (v != null) shortByteDecl(language, "byte", "Byte", name, v) else null
        }
        if (declared == Character.TYPE) {
            val v = parseIntOrNull(data)
            return if (v != null && v in 0..0xFFFF)
                decl(language, "char", name, charLiteral(v.toChar())) else null
        }
        if (declared == java.lang.Boolean.TYPE || kind == "boolean") {
            val bool = booleanLiteral(data)
            return if (bool != null) decl(language, "boolean", name, bool) else null
        }
        if (declared == java.lang.Double.TYPE || (declared == null && kind == "double")) {
            val lit = doubleLiteral(data)
            return if (lit != null) decl(language, "double", name, lit) else null
        }
        if (declared == java.lang.Float.TYPE || (declared == null && kind == "float")) {
            val lit = floatLiteral(data)
            return if (lit != null) decl(language, "float", name, lit) else null
        }
        if (declared == String::class.java) {
            return decl(language, "String", name, stringLiteral(data, language))
        }
        return null // unknown / object / pointer -> degrade
    }

    // --- declaration formatting -----------------------------------------------

    /**
     * Format one binding declaration. Java: `<javaType> name = literal;`. Kotlin: `val name =
     * literal` (the type is inferred from the literal for everything except `short`/`byte`, which
     * go through [shortByteDecl]).
     */
    private fun decl(language: Language, javaType: String, name: String, literal: String): String =
            when (language) {
                Language.JAVA -> "$javaType $name = $literal;"
                Language.KOTLIN -> "val ${ktName(name)} = $literal"
            }

    /**
     * `short`/`byte` bindings. Java casts the literal (`short n = (short) 3;`); Kotlin annotates the
     * `val` with an explicit type (`val n: Short = 3`) because a bare integer literal is `Int` and
     * won't implicitly narrow.
     */
    private fun shortByteDecl(language: Language, javaType: String, kotlinType: String,
                              name: String, v: Long): String = when (language) {
        Language.JAVA -> "$javaType $name = ($javaType) $v;"
        Language.KOTLIN -> "val ${ktName(name)}: $kotlinType = $v"
    }

    /**
     * A Kotlin identifier for [name]. Kotlin proof methods can have backtick names containing spaces
     * and other non-identifier characters (`fun \`clamp is in bounds\`()`); but a *binding* name here
     * is a parameter/local name, which is already a valid Java/Kotlin identifier. We still backtick
     * any name that isn't a legal Kotlin identifier (or is a hard keyword) so the generated `val`
     * always compiles.
     */
    private fun ktName(name: String): String =
            if (isPlainKotlinIdentifier(name)) name else "`" + name.replace("`", "") + "`"

    // --- literal rendering ----------------------------------------------------

    /** A valid string literal for [s] in [language] (surrounding quotes included). */
    private fun stringLiteral(s: String, language: Language): String = when (language) {
        Language.JAVA -> javaStringLiteral(s)
        Language.KOTLIN -> kotlinStringLiteral(s)
    }

    /** A valid Java string literal for [s] (surrounding quotes included). */
    internal fun javaStringLiteral(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            append(escapeChar(c, false, false))
        }
        append('"')
    }

    /**
     * A valid Kotlin string literal for [s] (surrounding quotes included). Identical to the Java
     * escaping except `$` is also escaped (`\$`) — a bare `$` starts a template interpolation in
     * Kotlin.
     */
    internal fun kotlinStringLiteral(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            append(escapeChar(c, false, true))
        }
        append('"')
    }

    /** A valid `char` literal for [c] (surrounding single quotes included; same in both languages). */
    internal fun charLiteral(c: Char): String = "'" + escapeChar(c, true, false) + "'"

    /**
     * Escape one char for a string (`inChar=false`) or char (`inChar=true`) literal. When
     * [kotlin] is true, `$` is escaped as well (template interpolation); the char-literal context
     * never sets it (no interpolation inside `'...'`).
     */
    private fun escapeChar(c: Char, inChar: Boolean, kotlin: Boolean): String = when (c) {
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000C' -> "\\f"
        '\u0000' -> "\\u0000"
        '"' -> if (inChar) "\"" else "\\\""
        '\'' -> if (inChar) "\\'" else "'"
        '$' -> if (kotlin && !inChar) "\\$" else "$"
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

    /**
     * Render a double, mapping JBMC's textual NaN/Inf forms to compilable constants. Identical in
     * Java and Kotlin: a bare decimal (`1.5`) is a valid `double`/`Double` literal in both (Kotlin
     * has no `d`/`D` suffix and we never emit one), and `Double.NaN`/`Double.POSITIVE_INFINITY` name
     * the same constants.
     */
    internal fun doubleLiteral(data: String?): String? {
        specialFloat(data, "Double")?.let { return it }
        val v = parseDoubleOrNull(data) ?: return null
        return when {
            v.isNaN() -> "Double.NaN"
            v.isInfinite() -> if (v > 0) "Double.POSITIVE_INFINITY" else "Double.NEGATIVE_INFINITY"
            else -> v.toString()
        }
    }

    /** Render a float, mapping JBMC's textual NaN/Inf forms to compilable constants (`f` suffix is
     *  valid Kotlin too). */
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

    /** True if [s] is a plain Kotlin identifier (no backticks needed) and not a hard keyword. */
    private fun isPlainKotlinIdentifier(s: String): Boolean {
        if (s.isEmpty()) {
            return false
        }
        if (!s[0].isLetter() && s[0] != '_') {
            return false
        }
        if (!s.all { it.isLetterOrDigit() || it == '_' }) {
            return false
        }
        return s !in KOTLIN_HARD_KEYWORDS
    }

    /** Kotlin hard keywords: usable as an identifier only when backtick-quoted. */
    private val KOTLIN_HARD_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
            "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "var", "when", "while")

    private fun describeKind(b: JbmcResult.Binding): String = when (val k = b.kind) {
        null -> "value"
        "pointer", "struct" -> "object/reference value"
        else -> "$k value"
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

    private fun parseIntOrNull(s: String?): Int? = s?.trim()?.toIntOrNull()

    private fun parseLongOrNull(s: String?): Long? = s?.trim()?.toLongOrNull()

    private fun parseDoubleOrNull(s: String?): Double? = s?.trim()?.toDoubleOrNull()
}
