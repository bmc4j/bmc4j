package org.bmc4j.engine

import java.io.IOException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a refuted proof's counterexample to a runnable `@Test` scratch file (v2):
 * `build/bmc4j/replays/<Class>_<method>Replay.java` (or `.kt` for a Kotlin proof). The file
 * reconstructs the concrete inputs from the counterexample and leaves a clearly-marked spot to
 * invoke the code under test, so the developer can drop it into a test source set and step through
 * it in a debugger.
 *
 * **Replay language.** The artifact matches the language of the proof it came from:
 * - `auto` (default): a proof class carrying `kotlin.Metadata` emits a `.kt` replay; any other
 *   class emits a `.java` replay (byte-identical to the historical output).
 * - forced: `-Dbmc.replayLanguage=java` / `=kotlin` (plumbed from the Gradle extension's
 *   `replayLanguage` and the `-Pbmc.replayLanguage` CLI flag) pins one language regardless of the
 *   proof class — for mixed modules or teams that keep scratch tests in one language.
 *
 * It is a **scratch artifact**: never auto-added to any source set (the user opts into running
 * it). The renderer reuses [ReplayRenderer]'s literal rendering, so the inputs are always
 * compilable; the invocation is left as a `// TODO` comment because the symbolic proof body can
 * make several calls and bmc4j cannot soundly pick the one true target call to fabricate.
 *
 * Writing is best-effort: any I/O failure returns `null` (no file, no block reference) so a
 * read-only or sandboxed filesystem never turns a refutation into a crash.
 */
object ReplayTestWriter {

    /** Output dir, overridable for tests via `-Dbmc.replayDir`. */
    internal fun outputDir(): Path {
        val override = System.getProperty("bmc.replayDir")
        if (!override.isNullOrBlank()) {
            return Path.of(override.trim())
        }
        return Path.of("build", "bmc4j", "replays")
    }

    /**
     * Write the replay test for [violation] and return its path (as a string), or `null`
     * if there is nothing to render or the write failed.
     */
    @JvmStatic
    fun write(entryFunctionFqn: String, proofMethod: Method?,
              violation: JbmcResult.Violation): String? {
        val language = resolveLanguage(proofMethod)
        ReplayRenderer.render(entryFunctionFqn, proofMethod, violation, language) ?: return null
        val testClass = sanitize(simpleClassOf(entryFunctionFqn)) + "_" +
                sanitize(methodOf(entryFunctionFqn)) + "Replay"
        val source = renderSource(testClass, entryFunctionFqn, proofMethod, violation, language)
        val extension = if (language == ReplayRenderer.Language.KOTLIN) "kt" else "java"
        return try {
            val dir = outputDir()
            Files.createDirectories(dir)
            val file = dir.resolve("$testClass.$extension")
            Files.writeString(file, source, StandardCharsets.UTF_8)
            file.toString()
        } catch (e: IOException) {
            null // best-effort: never let a write failure mask the refutation
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * Decide the replay language: `-Dbmc.replayLanguage` forces it when set to `java`/`kotlin`;
     * otherwise (`auto` or unset) the proof class's `kotlin.Metadata` annotation selects Kotlin,
     * everything else Java. An unrecognized forced value falls back to `auto` (the plugin validates
     * the value loudly at configuration time, so a bad value never normally reaches here).
     */
    internal fun resolveLanguage(proofMethod: Method?): ReplayRenderer.Language {
        return when (System.getProperty("bmc.replayLanguage")?.trim()?.lowercase()) {
            "java" -> ReplayRenderer.Language.JAVA
            "kotlin" -> ReplayRenderer.Language.KOTLIN
            else -> if (isKotlinClass(proofMethod?.declaringClass)) {
                ReplayRenderer.Language.KOTLIN
            } else {
                ReplayRenderer.Language.JAVA
            }
        }
    }

    /** True if [clazz] is a Kotlin class — detected by the `kotlin.Metadata` annotation kotlinc
     *  stamps onto every class it emits. Loaded by name so a Java-only runtime needs no kotlin dep. */
    private fun isKotlinClass(clazz: Class<*>?): Boolean {
        if (clazz == null) {
            return false
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val metadata = Class.forName("kotlin.Metadata") as Class<out Annotation>
            clazz.isAnnotationPresent(metadata)
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /** The full source of the scratch replay test, in [language]. */
    internal fun renderSource(testClass: String, entryFunctionFqn: String, proofMethod: Method?,
                              violation: JbmcResult.Violation,
                              language: ReplayRenderer.Language): String =
            if (language == ReplayRenderer.Language.KOTLIN) {
                renderKotlinSource(testClass, entryFunctionFqn, proofMethod, violation)
            } else {
                renderJavaSource(testClass, entryFunctionFqn, proofMethod, violation)
            }

    /** Java overload preserved for callers/tests that don't pass a language (defaults to Java). */
    @JvmStatic
    internal fun renderSource(testClass: String, entryFunctionFqn: String, proofMethod: Method?,
                              violation: JbmcResult.Violation): String =
            renderJavaSource(testClass, entryFunctionFqn, proofMethod, violation)

    /** The full Java source of the scratch replay test (unchanged from v2). */
    private fun renderJavaSource(testClass: String, entryFunctionFqn: String, proofMethod: Method?,
                                 violation: JbmcResult.Violation): String = buildString {
        append("// Auto-generated by bmc4j — a SCRATCH replay of a refuted proof.\n")
        append("// Not on any source set. Drop it into src/test/java, wire the invocation marked\n")
        append("// TODO below, and run it under a debugger to reproduce the counterexample.\n")
        append("import org.junit.jupiter.api.Test;\n\n")
        append("class ").append(testClass).append(" {\n\n")
        append("    @Test\n")
        append("    void replay() {\n")
        // Emit the declarations directly from the renderer's block (strip its "replay:" framing).
        for (l in declarationLines(entryFunctionFqn, proofMethod, violation, ReplayRenderer.Language.JAVA)) {
            append("        ").append(l).append('\n')
        }
        append("        // TODO: invoke the same code path the proof exercised, e.g.\n")
        append("        //   ").append(simpleClassOf(entryFunctionFqn)).append('.')
                .append(methodOf(entryFunctionFqn)).append("(...);\n")
        append("    }\n")
        append("}\n")
    }

    /**
     * The full Kotlin source of the scratch replay test: the Java shape mirrored with a `@Test fun
     * replay()`, `val` declarations, and a header pointing at `src/test/kotlin`. The class and the
     * `@Test` function name come from the proof's class/method; a backtick-named proof method
     * (`fun \`x is in bounds\`()`) is sanitized into the class identifier (so the *file* name is a
     * plain identifier) and the original method name is shown as a backtick-quoted call in the TODO.
     */
    private fun renderKotlinSource(testClass: String, entryFunctionFqn: String,
                                   proofMethod: Method?,
                                   violation: JbmcResult.Violation): String = buildString {
        append("// Auto-generated by bmc4j — a SCRATCH replay of a refuted proof.\n")
        append("// Not on any source set. Drop it into src/test/kotlin, wire the invocation marked\n")
        append("// TODO below, and run it under a debugger to reproduce the counterexample.\n")
        append("import org.junit.jupiter.api.Test\n\n")
        append("class ").append(testClass).append(" {\n\n")
        append("    @Test\n")
        append("    fun replay() {\n")
        for (l in declarationLines(entryFunctionFqn, proofMethod, violation, ReplayRenderer.Language.KOTLIN)) {
            append("        ").append(l).append('\n')
        }
        append("        // TODO: invoke the same code path the proof exercised, e.g.\n")
        append("        //   ").append(simpleClassOf(entryFunctionFqn)).append('.')
                .append(kotlinCallName(methodOf(entryFunctionFqn))).append("(...)\n")
        append("    }\n")
        append("}\n")
    }

    /** The bare declaration lines (no `replay:` header / hint comment) for the test body. */
    private fun declarationLines(entryFunctionFqn: String, proofMethod: Method?,
                                 violation: JbmcResult.Violation,
                                 language: ReplayRenderer.Language): List<String> {
        val block = ReplayRenderer.render(entryFunctionFqn, proofMethod, violation, language)
                ?: return emptyList()
        return block.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "replay:" && !it.startsWith("// then run") }
    }

    private fun simpleClassOf(fqn: String): String {
        val s = stripSig(fqn)
        val dot = s.lastIndexOf('.')
        if (dot <= 0) {
            return s
        }
        val prev = s.lastIndexOf('.', dot - 1)
        return s.substring(prev + 1, dot)
    }

    private fun methodOf(fqn: String): String = stripSig(fqn).substringAfterLast('.')

    private fun stripSig(fqn: String): String {
        val sig = fqn.indexOf(":(")
        return if (sig >= 0) fqn.substring(0, sig) else fqn
    }

    /**
     * The method name as a Kotlin *call* token: a plain identifier stays bare; a name with spaces or
     * other non-identifier characters (a backtick-named Kotlin proof) is backtick-quoted so the
     * emitted call compiles.
     */
    private fun kotlinCallName(method: String): String {
        val plain = method.isNotEmpty() &&
                (method[0].isLetter() || method[0] == '_') &&
                method.all { it.isLetterOrDigit() || it == '_' }
        return if (plain) method else "`" + method.replace("`", "") + "`"
    }

    /**
     * Make [s] a legal Java/Kotlin *identifier* (used for the file name and the class name): every
     * non-identifier character (including the spaces a backtick-named Kotlin proof carries) becomes
     * `_`. So `clamp is in bounds` → `clamp_is_in_bounds`, a plain identifier that needs no
     * backticks in the file name.
     */
    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(if (Character.isJavaIdentifierPart(c)) c else '_')
        }
        return if (sb.isEmpty()) "X" else sb.toString()
    }
}
