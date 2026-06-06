package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Implements the **replace** direction of method contracts at the bytecode
 * level — "Route A", proven by the spike. For a proof that should reuse a contract instead
 * of re-analyzing a method's body, it rewrites the *call sites* of contracted
 * methods to call generated stub methods (which `assert(requires); return
 * assume(ensures) over a nondet result`). The contracted method's real class is never
 * shadowed, so its own enforce-proof still analyzes the real body — replace and enforce
 * coexist by which classpath a given proof is handed.
 *
 * The rewrite is routed through [ClasspathMirror] — the one fail-loud, content-hashed
 * mirroring engine every other rewrite pass uses. Both directory and jar entries are mirrored
 * (each `.class` call-site-rewritten, everything else copied verbatim); the redirect set and
 * the excluded caller are folded into the mirror's content hash as extra key material, so distinct
 * contract configurations over the same source never alias one mirror. A mirror failure THROWS
 * (reclassified to UNKNOWN by the engine-error handler) rather than silently analysing the real,
 * un-redirected call sites as if they were the contract proof.
 *
 * v1 redirects `invokestatic` calls to contracted static methods. The stub has the
 * same descriptor, so the operand stack is unchanged.
 *
 * **Modular enforce.** A redirect set may be applied with one class *excluded*
 * as a caller: its call sites are left untouched. This is how an enforce-proof analyzes a
 * method's real body while every contracted callee (including a recursive self-call) is
 * still summarized — the proof class is excluded so its direct call to the method-under-test
 * stays real, but the method's own body (in a different class) has its contracted calls
 * redirected. That is exactly the inductive step for recursion and modular composition for
 * call chains. A replace-proof excludes nothing, so all of its contracted calls are summarized.
 */
object ContractRewriter {

    /** A single call-site redirect: calls to `owner.name(descriptor)` become
     *  `invokestatic stubOwner.stubName(descriptor)`. A null descriptor matches any. */
    class Redirect(
            @JvmField internal val owner: String,
            @JvmField internal val name: String,
            @JvmField internal val descriptor: String?,
            @JvmField internal val stubOwner: String,
            @JvmField internal val stubName: String) {

        internal fun matches(o: String?, n: String?, d: String?): Boolean =
                owner == o && name == n && (descriptor == null || descriptor == d)

        /** Stable, fully-specified form — part of the contract mirror's cache key, so two distinct
         *  redirect sets can never alias the same mirror. */
        override fun toString(): String = "$owner.$name$descriptor->$stubOwner.$stubName"
    }

    /** Rewrite directory entries of [classpath], returning the new classpath. */
    @JvmStatic
    fun rewrite(classpath: String, redirects: List<Redirect>): String =
            rewrite(classpath, redirects, null)

    /**
     * Rewrite the call sites on [classpath], leaving the call sites of
     * [excludeCallerInternalName] (an internal class name like `pkg/Proof`, or
     * `null` to exclude nothing) untouched — see the class doc on modular enforce.
     *
     * Routed through [ClasspathMirror]: each entry is mirrored into a fresh dir/jar keyed by
     * a full SHA-256 of (content + this config), atomically published, and marked `.done` last;
     * a mirror failure throws (→ UNKNOWN) rather than silently passing the un-rewritten entry through.
     * The [redirects] and [excludeCallerInternalName] are the extra key material — the
     * source bytes are identical regardless of them, so the mirror identity must include them
     * explicitly or two distinct configurations would collide into one cached mirror.
     */
    @JvmStatic
    fun rewrite(classpath: String, redirects: List<Redirect>,
                excludeCallerInternalName: String?): String {
        if (redirects.isEmpty()) {
            return classpath
        }
        val extraKey = "$redirects|x=$excludeCallerInternalName"
        return ClasspathMirror.mirror(
                classpath,
                "contracts",
                { bytes ->
                    ClasspathMirror.Transformed(
                            rewriteClass(bytes, redirects, excludeCallerInternalName))
                },
                extraKey)
    }

    /** Pure transform: redirect matching `invokestatic` call sites. Exposed for tests. */
    internal fun rewriteClass(bytes: ByteArray, redirects: List<Redirect>): ByteArray =
            rewriteClass(bytes, redirects, null)

    /**
     * Pure transform: redirect matching `invokestatic` call sites, unless the class
     * being rewritten is [excludeCaller] (its call sites pass through unchanged).
     * Exposed for tests.
     */
    internal fun rewriteClass(bytes: ByteArray, redirects: List<Redirect>,
                              excludeCaller: String?): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            private var excluded = false

            override fun visit(v: Int, a: Int, name: String?, sig: String?, sup: String?,
                               ifs: Array<String>?) {
                excluded = excludeCaller != null && excludeCaller == name
                super.visit(v, a, name, sig, sup, ifs)
            }

            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC && !excluded) {
                            val r = redirects.firstOrNull { it.matches(owner, name, desc) }
                            if (r != null) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, r.stubOwner, r.stubName,
                                        desc, false)
                                return
                            }
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf)
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }
}
