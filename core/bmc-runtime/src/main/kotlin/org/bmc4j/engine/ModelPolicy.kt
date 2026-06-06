package org.bmc4j.engine

/**
 * Turns the model *facts* ([ModelManifest]: the user's intent declarations + the model
 * classes actually compiled under `src/bmcModel`) into a *policy* decision for a green
 * proof — exactly the fact-vs-policy split `StubPolicy` does for nondet stubs.
 *
 * A user model is `Bmc.assume()` at classpath altitude: it can quietly change what a proof
 * means, and unlike a stub it isn't visible at the proof site at all. The trust layer therefore mirrors
 * the stub ladder (footnote → warn → strict):
 *
 * - **declared** models present on the classpath get a provenance footnote on every green proof
 *   — naming the model, and for `domain` models appending the declared rationale ("proven
 *   under domain model `acme.NoCollisionMap` (assumes no key collisions)");
 * - **undeclared** present user models (a class shadowing on the analysis classpath with no
 *   `bmc { models { … } }` declaration) are a trust gap — footnoted loudly in lenient mode and
 *   forced to UNKNOWN under `strictModels`, so no proof silently rests on an undeclared
 *   override;
 * - **overriding** models — a present user model whose name shadows a bundled/JDK model bmc4j
 *   already ships a verified stand-in for — warn loudly even in lenient mode (you're replacing a
 *   checked model with an unchecked one).
 *
 * **Granularity.** "Present on the classpath" is the relevance signal, not "provably exercised by
 * this specific proof": JBMC gives no per-proof which-model-bodies-were-linked report (unlike the
 * nondet-stub messages it emits), so the honest unit is "this proof's analysis classpath included the
 * user model". That can over-attribute a model to a proof that didn't actually call it; the footnote
 * wording stays deliberately classpath-scoped ("relied on the model(s) on this analysis classpath")
 * rather than claiming per-call precision. This is the same granularity `src/bmcModel` shadowing
 * already has — every proof in the module shares that classpath.
 */
class ModelPolicy private constructor(
        private val declaredPresent: List<UserModel>,
        private val undeclaredPresent: List<String>,
        private val overriding: List<String>) {

    /** Declared models actually present on the classpath — the ones a green proof footnotes for provenance. */
    @JvmName("declaredPresent")
    fun declaredPresent(): List<UserModel> = declaredPresent

    /** Present user-model classes with no intent declaration — the strict-UNKNOWN / loud-footnote subset. */
    @JvmName("undeclaredPresent")
    fun undeclaredPresent(): List<String> = undeclaredPresent

    /** Present user models shadowing a bundled/JDK model bmc4j already verifies — warned loud always. */
    @JvmName("overriding")
    fun overriding(): List<String> = overriding

    /** True if any user model is present (declared or not) — i.e. there is provenance to surface. */
    fun hasAnyPresent(): Boolean = declaredPresent.isNotEmpty() || undeclaredPresent.isNotEmpty()

    /** True if a present user model has no declaration (footnote-loud in lenient, UNKNOWN in strict). */
    fun hasUndeclared(): Boolean = undeclaredPresent.isNotEmpty()

    /** True if a present user model shadows a bundled/JDK verified model (always a loud warning). */
    fun hasOverriding(): Boolean = overriding.isNotEmpty()

    companion object {

        /**
         * Judge a manifest: cross the declared models against the classes actually present under
         * `src/bmcModel`. A declared model whose class isn't present is dropped (it can't affect a
         * verdict — likely a typo, but harmless to the trust layer); a present class with no declaration
         * is an undeclared override.
         */
        @JvmStatic
        fun judge(manifest: ModelManifest): ModelPolicy {
            val byName = LinkedHashMap<String, UserModel>()
            for (m in manifest.declared()) {
                byName[m.className] = m // last declaration of a name wins
            }
            val declaredPresent = mutableListOf<UserModel>()
            val undeclaredPresent = mutableListOf<String>()
            val overriding = mutableListOf<String>()
            for (present in manifest.presentClasses()) {
                val decl = byName[present]
                if (decl != null) {
                    declaredPresent.add(decl)
                } else {
                    undeclaredPresent.add(present)
                }
                if (shadowsBundledModel(present)) {
                    overriding.add(present)
                }
            }
            return ModelPolicy(declaredPresent, undeclaredPresent, overriding)
        }

        /**
         * True if [fqn] names a class bmc4j already ships a (verified) model for — the JDK (`java.`
         * / `javax.` / `jdk.`) and the Kotlin runtime (`kotlin.` / `kotlinx.`).
         * Shadowing one of these replaces a checked stand-in with an unchecked user one, so it's worth a
         * loud warning. Coarse on purpose: it's keyed on the package roots bmc4j models, not an
         * enumeration of the bundled jar's exact classes (which would couple this to the jar's build
         * layout) — and it errs only toward warning, never toward a false green.
         */
        internal fun shadowsBundledModel(fqn: String?): Boolean {
            if (fqn == null) {
                return false
            }
            return fqn.startsWith("java.") || fqn.startsWith("javax.") || fqn.startsWith("jdk.")
                    || fqn.startsWith("kotlin.") || fqn.startsWith("kotlinx.")
        }
    }
}
