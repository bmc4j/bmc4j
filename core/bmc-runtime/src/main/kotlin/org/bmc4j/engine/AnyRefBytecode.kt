package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Intrinsifies `Bmc.anyRef(Foo.class)` - the symbolic-dependency handle for `assumeEvery`/`assumeStable`
 * ("give me ANY implementation of this interface, no concrete stub").
 *
 * ## The defect this fixes
 * `Bmc.anyRef(Class<T>)` is declared `<T> T anyRef(Class<T>)`; generic erasure makes its erased return
 * type `Object`, so javac/kotlinc emit a trailing `checkcast Foo` at the CALL SITE to recover `T`:
 *
 * ```
 * ldc           Foo.class               // the Class<T> token (a constant)
 * invokestatic  Bmc.anyRef:(LClass;)LObject;
 * checkcast     Foo                      // erasure cast back to T
 * ```
 *
 * JBMC EXECUTES `anyRef`'s real body (`return CProver.nondetWithoutNull();`), whose erased return type is
 * `Object`, so the havoc'd object is typed `Object` and the trailing `checkcast Foo` can fail - JBMC
 * models the implicit `ClassCastException` as a verification assertion and the proof comes back REFUTED
 * with "Dynamic cast check" at the call site. `anyRef` thus refuted on its own headline use case.
 *
 * ## The fix
 * `CProver.nondetWithoutNull()` is JBMC-INTRINSIC: at a `checkcast Foo` immediately consuming its result,
 * the engine havocs a fresh object ALREADY typed `Foo`, so the cast holds (verified empirically - a
 * direct `(Foo) CProver.nondetWithoutNull()` to an interface passes the dynamic-cast check). The problem
 * is only that `anyRef` interposes a real, `Object`-typed method body between the havoc and the cast.
 *
 * So at every `INVOKESTATIC org/bmc4j/Bmc.anyRef:(Ljava/lang/Class;)Ljava/lang/Object;` this pass:
 *   - `POP`s the `Class<T>` token left on the stack by the preceding `LDC` (the type is documentation; the
 *     value is symbolic regardless), and
 *   - replaces the call with `INVOKESTATIC org/cprover/CProver.nondetWithoutNull:()Ljava/lang/Object;`.
 *
 * The preceding `LDC` and the trailing `checkcast Foo` are left UNTOUCHED; the cast now consumes the
 * intrinsic havoc directly, so JBMC havocs an object of the cast's static type and the cast holds. The
 * substitution is stack-neutral (one `Object` in, one `Object` out), introduces no jump target, and
 * leaves the result as a SOUND over-approximation: a fresh non-null nondet `Foo`, standing in for any
 * implementation (its methods are nondet stubs unless an `assumeEvery`/`assumeStable` constrains them).
 *
 * Pure, env-independent bytecode - it only touches `Bmc.anyRef` call sites - so it is hoisted into the
 * cacheable mirror chain exactly like the sibling passes ([NondetTagBytecode], [ReachabilityBytecode]).
 */
object AnyRefBytecode {

    private const val BMC_OWNER = "org/bmc4j/Bmc"
    private const val ANY_REF = "anyRef"
    private const val ANY_REF_DESC = "(Ljava/lang/Class;)Ljava/lang/Object;"
    private const val CPROVER_OWNER = "org/cprover/CProver"
    private const val NONDET = "nondetWithoutNull"
    private const val NONDET_DESC = "()Ljava/lang/Object;"

    private val CACHE = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, AnyRefBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "anyref", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        // Fast no-op: a class with no `anyRef` reference in its constant pool can't call it, so leave its
        // bytes byte-for-byte untouched (the mirror dedups identical content). Avoids re-emitting the
        // bulk of the classpath through the writer.
        if (!referencesAnyRef(cr)) {
            return bytes
        }
        // COMPUTE_MAXS only: the substitution is stack-neutral and adds no jump target, so existing
        // stack-map frames stay valid (same rationale as the sibling passes).
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor =
                    Rewriter(super.visitMethod(access, name, desc, sig, ex))
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /** Does [cr]'s constant pool name `Bmc.anyRef`? A class that never references it can't call it. */
    private fun referencesAnyRef(cr: ClassReader): Boolean {
        for (i in 1 until cr.itemCount) {
            try {
                if (ANY_REF == cr.readUTF8(cr.getItem(i), CharArray(cr.maxStringLength))) {
                    return true
                }
            } catch (e: RuntimeException) {
                // Not a UTF8 constant at this slot - skip.
            }
        }
        return false
    }

    private class Rewriter(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM9, mv) {
        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (opcode == Opcodes.INVOKESTATIC && owner == BMC_OWNER && name == ANY_REF
                    && desc == ANY_REF_DESC) {
                // Drop the Class<T> token the preceding LDC pushed, then call the JBMC-intrinsic havoc.
                // The trailing checkcast (the erasure cast back to T) then consumes the intrinsic result
                // directly, so the engine havocs an object of the cast's static type and the cast holds.
                super.visitInsn(Opcodes.POP)
                super.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER_OWNER, NONDET, NONDET_DESC, false)
                return
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf)
        }
    }
}
