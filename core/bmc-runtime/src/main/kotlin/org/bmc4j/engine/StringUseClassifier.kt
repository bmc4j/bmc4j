package org.bmc4j.engine

/**
 * Classifies a proof as **text/String-using** or **text-free** by walking its reachable cone.
 *
 * ## Why this exists (the cardinal invariant)
 * An external DIMACS SAT solver (the fast path, e.g. the bundled fast solver) makes the engine run
 * with its String reasoning turned OFF. For a proof that touches NO text/String operations that's
 * sound and much faster. For a proof that DOES reason about text, running with String reasoning off
 * can report a **false pass** that would not hold with it on. So the fast solver must engage ONLY for
 * a proof this classifier proves text-free; everything else stays on the default solver.
 *
 * ## Conservative by construction (over-approximate toward "text-using")
 * The classifier reuses [ReachableCone] — the same sound over-approximation the verdict cache keys on
 * — and treats a proof as **text-using** whenever EITHER:
 *  - its reachable cone references any of the text types ([TEXT_TYPES]: `String`, `StringBuilder`,
 *    `StringBuffer`, `CharSequence`, `char[]`-via-`String`, and the string-concat / record-toString
 *    bootstrap factories), OR
 *  - the cone **could not be bounded soundly** (reflection / method handles, an un-attributable
 *    `invokedynamic`, the entry class off the classpath, or any walk error) — i.e. [ReachableCone]
 *    returned its whole-classpath fallback. An unbounded cone might reach text we can't see, so it is
 *    text-using.
 *
 * A false "text-free" is the one catastrophic outcome (it would let the fast solver serve an unsound
 * pass), so every uncertainty resolves to text-using. A false "text-using" merely forgoes a speedup.
 */
internal object StringUseClassifier {

    /**
     * Internal names whose appearance anywhere in a proof's reachable cone marks the proof
     * text/String-using. Kept as a prefix set: a reached type is text-using if its internal name
     * equals or is nested under one of these (so `String$CaseInsensitiveComparator` and the like count).
     *
     * `char[]` itself is a primitive-array descriptor with no class to reach, so it isn't listed; a
     * proof that turns a `char[]` into text necessarily routes through `String`/`CharSequence`
     * (`new String(char[])`, `CharSequence.charAt`, …), which ARE listed — so char-array text handling
     * is caught via those owners.
     */
    private val TEXT_TYPES: Set<String> = setOf(
            "java/lang/String",
            "java/lang/StringBuilder",
            "java/lang/StringBuffer",
            "java/lang/CharSequence",
            // The concat / record-toString bootstrap factories: a proof reaching these is doing text
            // formatting whose result the fast solver can't reason about soundly. The desugar passes
            // turn most of these into StringBuilder (already caught), but classify defensively in case
            // a residual form survives.
            "java/lang/invoke/StringConcatFactory",
            "java/lang/AbstractStringBuilder")

    /**
     * True when the proof rooted at [entryClass] over [classpath] is text/String-using and therefore
     * must NOT be handed to the fast (String-reasoning-off) external SAT solver.
     *
     * Over-approximates: an unbounded cone (the [ReachableCone] whole-classpath fallback) is text-using,
     * as is any cone that references a [TEXT_TYPES] entry. Fail-safe: ANY error here yields `true`
     * (text-using) — never `false` — so a classifier failure can never open the unsound fast path.
     */
    @JvmStatic
    fun usesText(entryClass: String, classpath: String?): Boolean = classify(entryClass, classpath).usesText

    /** The full classification result: the verdict plus a plain-language reason (for logs / tests). */
    @JvmStatic
    fun classify(entryClass: String, classpath: String?): Classification {
        return try {
            val cone = ReachableCone.compute(entryClass, classpath)
            if (cone.whole || cone.classes == null) {
                // Unbounded cone: we can't see everything the proof reaches, so it might touch text.
                return Classification(true, "the proof's reachable code couldn't be fully bounded" +
                        " (${cone.fallbackReason.ifBlank { "unbounded" }}), so it's treated as" +
                        " text/String-using to stay safe")
            }
            val hit = cone.classes.firstOrNull { isTextType(it) }
            if (hit != null) {
                Classification(true, "the proof reaches a text/String type ($hit)")
            } else {
                Classification(false, "the proof reaches no text/String types")
            }
        } catch (e: RuntimeException) {
            // Fail-safe: a classification error must never let the fast (unsound-for-text) path open.
            Classification(true, "couldn't classify the proof's text use (${e.javaClass.simpleName})," +
                    " so it's treated as text/String-using to stay safe")
        }
    }

    /** True if [internalName] is one of the text types, or nested under one (an inner/synthetic class). */
    private fun isTextType(internalName: String): Boolean =
            TEXT_TYPES.any { internalName == it || internalName.startsWith("$it$") }

    /** A text-use verdict plus a plain-language reason — the message a user-facing log can print. */
    class Classification internal constructor(
            @JvmField val usesText: Boolean,
            @JvmField val reason: String)
}
