package org.bmc4j.engine

/**
 * A user-supplied model declared with its *intent* — the trust metadata bmc4j needs to put
 * provenance on a verdict that rests on it.
 *
 * Shadowing a class on JBMC's analysis classpath already works (see `src/bmcModel`); what's
 * missing without this is *declared intent*. A model is either:
 * - [Intent.CONFORMANT] — it claims JDK fidelity (a faithful stand-in for a real class bmc4j
 *   doesn't model). It can be checked the same way bundled models are (differential-vs-JDK + laws),
 *   and a green proof that rests on it needs no caveat beyond naming it.
 * - [Intent.DOMAIN] — it *intentionally diverges* from the JDK to encode a domain
 *   constraint ("keys never collide", "lists bounded to 32"). This is `Bmc.assume()` at
 *   classpath altitude: legitimate, but invisible at the proof site — so it carries a one-line
 *   [rationale] and is surfaced on every green proof that used it.
 *
 * This is the *declaration* only; [ModelPolicy] turns a set of declarations plus the
 * models actually on a proof's classpath into the footnote / override-warning / strict-UNKNOWN decision,
 * mirroring how `StubPolicy` turns the stub fact into a policy decision.
 */
class UserModel private constructor(
        /** The fully-qualified class name this model shadows (e.g. `acme.NoCollisionMap`). */
        @get:JvmName("className") val className: String,
        @get:JvmName("intent") val intent: Intent,
        /** The domain rationale; `null` for a conformant model. */
        @get:JvmName("rationale") val rationale: String?) {

    /** What a user model claims about its relationship to the real JDK class it shadows. */
    enum class Intent {
        /** Claims JDK fidelity; verifiable by the same conformance harness as bundled models. */
        CONFORMANT,
        /** Intentionally diverges to encode a domain constraint; requires a [rationale]. */
        DOMAIN
    }

    val isDomain: Boolean
        get() = intent == Intent.DOMAIN

    companion object {

        /** A conformant model — claims JDK fidelity, no rationale needed. */
        @JvmStatic
        fun conformant(className: String): UserModel =
                UserModel(normalize(className), Intent.CONFORMANT, null)

        /**
         * A domain model — intentional divergence. [rationale] is required (a one-line explanation of
         * the assumed constraint, e.g. `"keys are UUIDs, collision-free"`); a blank rationale is a
         * declaration bug and fails loudly, because the whole point of a domain model is that its
         * divergence is stated, not silent.
         */
        @JvmStatic
        fun domain(className: String, rationale: String?): UserModel {
            if (rationale.isNullOrBlank()) {
                throw IllegalArgumentException(
                        "domain model $className must declare a one-line rationale " +
                                "(it intentionally diverges from the JDK — say how, e.g. " +
                                "\"keys are UUIDs, collision-free\")")
            }
            return UserModel(normalize(className), Intent.DOMAIN, rationale.trim())
        }

        private fun normalize(className: String?): String {
            if (className.isNullOrBlank()) {
                throw IllegalArgumentException("model class name must be non-blank")
            }
            // Accept either binary (a/b/C) or source (a.b.C) form; store source form for matching FQNs.
            return className.trim().replace('/', '.')
        }
    }
}
