package org.bmc4j.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Reads the two facts the model-trust policy needs from the test JVM's system properties, both set by
 * the Gradle plugin:
 *
 * - `bmc.models` — the user's *declarations* from `bmc { models { … } }`,
 *   serialized one model per entry as `intent|fqn|rationale` (rationale present only for
 *   `domain`). This is the trust metadata.
 * - `bmc.userModels` — the path(s) to the compiled `src/bmcModel` output (the same
 *   property `JbmcBackend` prepends to the analysis classpath). Scanning it gives the
 *   *actual* user-model classes present, so the policy can compare declarations against what's
 *   really shadowing on the classpath.
 *
 * Deliberately a pure reader of (declarations, present-classes) — the [ModelPolicy] turns those
 * into footnote / override-warning / strict-UNKNOWN, exactly as `StubPolicy` consumes the stub
 * fact. Parsing is fail-loud on a malformed declaration entry (a typo'd intent must break the build,
 * not silently drop a model from the trust layer), mirroring the runtime's visible-over-silent ethos.
 */
class ModelManifest private constructor(
        private val declared: List<UserModel>,
        private val presentClasses: Set<String>) {

    /** The declared models from the DSL (may reference classes not actually present — that's a config bug). */
    @JvmName("declared")
    fun declared(): List<UserModel> = declared

    /** Fully-qualified names of the model classes actually compiled under `src/bmcModel`. */
    @JvmName("presentClasses")
    fun presentClasses(): Set<String> = presentClasses

    val isEmpty: Boolean
        get() = declared.isEmpty() && presentClasses.isEmpty()

    companion object {

        /** Serialized declarations from `bmc { models { … } }`. */
        const val MODELS_PROP = "bmc.models"

        /** Compiled `src/bmcModel` output dir(s); same property the backend prepends to the classpath. */
        const val USER_MODELS_PROP = "bmc.userModels"

        /** Entry separator within `bmc.models` (newline-free so it survives a `-D` flag). */
        internal const val ENTRY_SEP = ";;"

        /** Field separator within one declaration entry: `intent|fqn|rationale`. */
        internal const val FIELD_SEP = "|"

        /** Read both facts from system properties (the live runtime path). */
        @JvmStatic
        fun fromSystemProperties(): ModelManifest =
                of(System.getProperty(MODELS_PROP, ""), System.getProperty(USER_MODELS_PROP, ""))

        /**
         * Build a manifest from a raw `bmc.models` string and a path-separator-delimited
         * [userModelsPath] class-dir list. Visible for unit testing; the production path is
         * [fromSystemProperties].
         */
        @JvmStatic
        fun of(modelsProp: String?, userModelsPath: String?): ModelManifest =
                ModelManifest(parseDeclarations(modelsProp), scanPresentClasses(userModelsPath))

        /** Serialize declarations for the `bmc.models` sysprop (used by the plugin / tests). */
        @JvmStatic
        fun serialize(models: List<UserModel>): String =
                models.joinToString(ENTRY_SEP) { m ->
                    m.intent.name.lowercase() + FIELD_SEP + m.className + FIELD_SEP + (m.rationale ?: "")
                }

        private fun parseDeclarations(prop: String?): List<UserModel> {
            val out = mutableListOf<UserModel>()
            if (prop.isNullOrBlank()) {
                return out
            }
            for (entry in prop.split(Pattern.quote(ENTRY_SEP).toRegex())) {
                if (entry.isBlank()) {
                    continue
                }
                // intent|fqn|rationale — split into at most 3 so a rationale may contain a '|'.
                val f = entry.split(Pattern.quote(FIELD_SEP).toRegex(), 3)
                val intent = f.getOrElse(0) { "" }.trim()
                val fqn = f.getOrElse(1) { "" }.trim()
                val rationale = f.getOrElse(2) { "" }.trim()
                if (fqn.isEmpty()) {
                    throw IllegalArgumentException(
                            "malformed bmc { models } entry (no class name): \"$entry\"")
                }
                when (intent) {
                    "conformant" -> out.add(UserModel.conformant(fqn))
                    "domain" -> out.add(UserModel.domain(fqn, rationale))
                    else -> throw IllegalArgumentException(
                            "unknown model intent \"$intent\" for $fqn" +
                                    " (expected \"conformant\" or \"domain\")")
                }
            }
            return out
        }

        /** Walk the `src/bmcModel` class dirs and collect every top-level `.class`'s FQN. */
        private fun scanPresentClasses(userModelsPath: String?): Set<String> {
            val present = LinkedHashSet<String>()
            if (userModelsPath.isNullOrBlank()) {
                return present
            }
            for (entry in userModelsPath.split(File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue
                }
                val dir = Path.of(entry.trim())
                if (!Files.isDirectory(dir)) {
                    continue
                }
                try {
                    Files.walk(dir).use { walk ->
                        walk.filter { Files.isRegularFile(it) }
                                .filter { it.fileName.toString().endsWith(".class") }
                                .forEach { p ->
                                    var rel = dir.relativize(p).toString()
                                            .replace('\\', '/').replace('/', '.')
                                    rel = rel.substring(0, rel.length - ".class".length)
                                    // Skip nested/synthetic classes ('$'): a top-level FQN is what a
                                    // user declares.
                                    if (rel.indexOf('$') < 0) {
                                        present.add(rel)
                                    }
                                }
                    }
                } catch (e: IOException) {
                    // Fail-open on a scan error: a missing present-class only weakens override/strict
                    // detection, never produces a false green (the policy errs toward "undeclared").
                } catch (e: RuntimeException) {
                    // Same fail-open direction.
                }
            }
            return present
        }
    }
}
