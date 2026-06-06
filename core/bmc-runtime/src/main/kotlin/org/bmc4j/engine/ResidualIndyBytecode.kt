package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Surfaces every `invokedynamic` the desugar passes left behind, instead of silently
 * trusting it.
 *
 * `invokedynamic` is the analysis's one fault line: JBMC links an indy call site to an
 * *unconstrained* result — and unlike a bodiless method, it does so without emitting any
 * opaque-symbol message, so a residual site is invisible to the nondet-stub policy: no footnote in
 * lenient mode, no `UNKNOWN` under `strictStubs`. The earlier passes desugar the common
 * bootstraps (string concat, record `equals`/`hashCode`/`toString`, lambdas /
 * method references, pattern `typeSwitch`), but what they deliberately leave —
 * `enumSwitch`, a `typeSwitch` label shape we don't soundly handle, a record `toString`
 * with a reference component, or any bootstrap a future compiler invents — was silently trusted:
 * a possible silent green.
 *
 * This pass runs AFTER every indy desugarer and replaces each remaining `invokedynamic`
 * with an `invokestatic` to [MARKER_CLASS] — a real class on the analysis classpath
 * that deliberately declares **no methods** (a missing METHOD on an existing class takes the
 * engine's standard nondet-stub path; a missing CLASS would add an unknown-class throw edge and
 * spuriously refute proofs that merely reach a residual site). The call has the indy's exact
 * descriptor, so it is a stack-compatible drop-in with the *same semantics JBMC already gave
 * the indy* (nondet result), but now the engine reports its standard no-body opaque symbol for
 * it, and the existing stub machinery takes over: harvested into the verdict-cache entry,
 * footnoted in lenient mode, `UNKNOWN` under `-Dbmc.strictStubs=true`, acknowledgeable
 * via `allowStubs`. The marker's method name carries the evidence —
 * `<indyName>__<bootstrapOwner>`, e.g. `enumSwitch__SwitchBootstraps` — so the
 * footnote names what was left un-desugared. ([StubFilter] exempts the marker from its
 * `org.bmc4j.*` noise filter.)
 *
 * Soundness direction: verdicts are unchanged (nondet before, nondet now); only the
 * *visibility* changes — the fault line goes from "silently trusted" to "visibly undecided",
 * the same trust channel every other havoc'd callee already uses.
 */
object ResidualIndyBytecode {

    /**
     * The marker owner: `org.bmc4j.analysis.ResidualInvokedynamic`, a real class that
     * deliberately declares no methods — JBMC finding the class but no body for the method is the
     * entire mechanism (see the class's javadoc for why the class itself must exist).
     */
    internal const val MARKER_CLASS = "org/bmc4j/analysis/ResidualInvokedynamic"

    /** The harvested-stub FQN prefix of marker methods (dot form), for the policy layers. */
    const val MARKER_FQN_PREFIX = "org.bmc4j.analysis.ResidualInvokedynamic."

    private val CACHE = ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], memoized per classpath (race-free). */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, ResidualIndyBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "residual-indy", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /** Exposed for unit tests: replace every remaining indy in one class with a marker call. */
    @JvmStatic
    @JvmName("rewriteClass") // internal functions are name-mangled in bytecode; Java tests call it
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitInvokeDynamicInsn(name: String, desc: String, bsm: Handle?,
                                                        vararg bsmArgs: Any?) {
                        // The indy's descriptor IS the call's stack contract (dynamic args -> return),
                        // so an invokestatic with the same descriptor is a drop-in replacement.
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, MARKER_CLASS,
                                markerMethodName(name, bsm), desc, false)
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * The marker method name: `<indyName>__<bootstrapOwnerSimpleName>`, sanitized to a valid
     * identifier. Both halves matter in the footnote: the indy name says which call was left
     * (e.g. `enumSwitch`, `toString`), the bootstrap owner says whose machinery it was
     * (e.g. `SwitchBootstraps`, `ObjectMethods`).
     */
    @JvmStatic
    @JvmName("markerMethodName") // internal functions are name-mangled in bytecode; Java tests call it
    internal fun markerMethodName(indyName: String, bsm: Handle?): String {
        val owner = bsm?.owner ?: "unknown"
        val simple = owner.substringAfterLast('/')
        return sanitize(indyName) + "__" + sanitize(simple)
    }

    private fun sanitize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(if (Character.isJavaIdentifierPart(c)) c else '_')
        }
        return if (sb.isEmpty()) "_" else sb.toString()
    }
}
