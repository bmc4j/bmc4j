package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.ArrayList

/**
 * Desugars lambda / method-reference `invokedynamic` (bootstrap
 * `java.lang.invoke.LambdaMetafactory`) into ordinary classes, because JBMC cannot construct
 * the hidden class the JVM spins at runtime for an indy lambda. This is the classic
 * "delambdafication" the JDK does at runtime (and javac did pre-8): for each lambda site we
 * generate a class implementing the functional interface, holding the captured values in fields and
 * delegating the SAM method to the implementation handle, and replace the indy with an
 * `invokestatic` to a generated factory of the same descriptor (so the captured args already
 * on the stack become the factory's arguments unchanged).
 *
 * The generated classes only ever go on JBMC's analysis classpath — never a real classloader —
 * so lambda-body access (private synthetic `lambda$*` methods) is handled by bumping those to
 * public rather than by nestmate attributes.
 *
 * v1 covers capturing/non-capturing lambdas and static/instance/constructor method references
 * over a single-abstract-method interface, with primitive box/unbox + cast adaptation. Deferred:
 * serializable lambdas (the serialization side of `altMetafactory`), marker/intersection SAMs.
 */
object LambdaBytecode {

    private const val METAFACTORY = "java/lang/invoke/LambdaMetafactory"

    private val CACHE = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], memoized per classpath (race-free).
     *  Each lambda site spins an extra generated class, emitted alongside the rewritten owner — in the
     *  mirror dir or, for a jar entry, as a new jar entry. */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, LambdaBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "lambdas", { bytes ->
                val r = transform(bytes)
                if (r.extra.isEmpty()) {
                    ClasspathMirror.Transformed(r.main)
                } else {
                    val extra = java.util.LinkedHashMap<String, ByteArray>()
                    for (g in r.extra) {
                        extra[g.internalName] = g.bytes
                    }
                    ClasspathMirror.Transformed(r.main, extra)
                }
            })

    internal class GeneratedClass(
            @JvmField val internalName: String,
            @JvmField val bytes: ByteArray)

    internal class Result(
            @JvmField val main: ByteArray,
            @JvmField val extra: List<GeneratedClass>)

    /** One lambda indy site we replaced with a call to a generated factory + class. */
    private class LambdaSite(
            @JvmField val factoryName: String,   // generated static factory in the owner
            @JvmField val genName: String,       // generated class implementing the functional interface
            @JvmField val indyDesc: String,      // (captures...)FunctionalInterface
            @JvmField val samName: String,       // functional-interface method name
            @JvmField val samType: Type,         // erased SAM descriptor (bsmArg 0)
            @JvmField val impl: Handle)          // implementation method handle (bsmArg 1)

    /** Package-private for tests. */
    @JvmStatic
    @JvmName("transform") // internal members are name-mangled in bytecode; the Java test calls it
    internal fun transform(bytes: ByteArray): Result {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val sites = ArrayList<LambdaSite>()
        val extra = ArrayList<GeneratedClass>()
        val counter = intArrayOf(0)
        val owner = arrayOfNulls<String>(1)
        val version = IntArray(1)

        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(ver: Int, access: Int, name: String?, sig: String?,
                               sup: String?, ifs: Array<String>?) {
                owner[0] = name
                version[0] = ver
                super.visit(ver, access, name, sig, sup, ifs)
            }

            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                // Lambda bodies are private synthetic; the generated class must call them, so make
                // them public (analysis classpath only — never loaded by a real JVM).
                var access = a
                if (n!!.startsWith("lambda$") && (a and Opcodes.ACC_PRIVATE) != 0) {
                    access = (a and Opcodes.ACC_PRIVATE.inv()) or Opcodes.ACC_PUBLIC
                }
                val mv = super.visitMethod(access, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?,
                                                        vararg bsmArgs: Any?) {
                        if (METAFACTORY == bsm!!.owner
                                && (bsm.name == "metafactory" || bsm.name == "altMetafactory")
                                && bsmArgs.size >= 2 && bsmArgs[0] is Type && bsmArgs[1] is Handle) {
                            val n2 = counter[0]++
                            val genName = owner[0] + "\$\$Lambda$" + n2
                            val factoryName = "bmc\$lambda$" + n2
                            sites.add(LambdaSite(factoryName, genName, desc!!, name!!,
                                    bsmArgs[0] as Type, bsmArgs[1] as Handle))
                            // captured args on the stack -> factory args (same descriptor).
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], factoryName, desc, false)
                        } else {
                            super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
                        }
                    }
                }
            }

            override fun visitEnd() {
                for (s in sites) {
                    emitFactory(cw, owner[0]!!, s)
                    extra.add(GeneratedClass(s.genName, generateLambdaClass(version[0], owner[0]!!, s)))
                }
                super.visitEnd()
            }
        }
        cr.accept(cv, 0)
        return Result(cw.toByteArray(), extra)
    }

    /** `static <FI> bmc$lambda$N(<captures>) { return new Owner$$Lambda$N(captures); }` */
    private fun emitFactory(cw: ClassWriter, owner: String, s: LambdaSite) {
        val mv = cw.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                s.factoryName, s.indyDesc, null, null)
        mv.visitCode()
        val caps = Type.getArgumentTypes(s.indyDesc)
        mv.visitTypeInsn(Opcodes.NEW, s.genName)
        mv.visitInsn(Opcodes.DUP)
        var slot = 0
        var capSizes = 0
        for (c in caps) {
            mv.visitVarInsn(c.getOpcode(Opcodes.ILOAD), slot)
            slot += c.size
            capSizes += c.size
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, s.genName, "<init>",
                "(" + argDesc(caps) + ")V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(capSizes + 2, Math.max(capSizes, 1))
        mv.visitEnd()
    }

    private fun generateLambdaClass(version: Int, owner: String, s: LambdaSite): ByteArray {
        val caps = Type.getArgumentTypes(s.indyDesc)
        val fi = Type.getReturnType(s.indyDesc).internalName

        val cw = ClassWriter(0)
        cw.visit(Math.max(version, Opcodes.V1_8), Opcodes.ACC_SYNTHETIC or Opcodes.ACC_FINAL,
                s.genName, null, "java/lang/Object", arrayOf(fi))

        for (i in caps.indices) {
            cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL, "f$i", caps[i].descriptor, null, null)
                    .visitEnd()
        }

        // constructor: store captures into fields
        val ctor = cw.visitMethod(0, "<init>", "(" + argDesc(caps) + ")V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        var slot = 1
        var capSizes = 0
        for (i in caps.indices) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            ctor.visitVarInsn(caps[i].getOpcode(Opcodes.ILOAD), slot)
            ctor.visitFieldInsn(Opcodes.PUTFIELD, s.genName, "f$i", caps[i].descriptor)
            slot += caps[i].size
            capSizes += caps[i].size
        }
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(3, 1 + capSizes)
        ctor.visitEnd()

        emitSamBody(cw, caps, s)
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** The SAM method: load captures + SAM args, adapt, invoke the impl handle, adapt the result. */
    private fun emitSamBody(cw: ClassWriter, caps: Array<Type>, s: LambdaSite) {
        val samArgs = Type.getArgumentTypes(s.samType.descriptor)
        val samRet = Type.getReturnType(s.samType.descriptor)
        val implArgs = Type.getArgumentTypes(s.impl.desc)
        val tag = s.impl.tag
        val ctorRef = tag == Opcodes.H_NEWINVOKESPECIAL
        val instanceCall = tag == Opcodes.H_INVOKEVIRTUAL || tag == Opcodes.H_INVOKEINTERFACE
                || tag == Opcodes.H_INVOKESPECIAL

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, s.samName, s.samType.descriptor, null, null)
        mv.visitCode()

        if (ctorRef) {
            mv.visitTypeInsn(Opcodes.NEW, s.impl.owner)
            mv.visitInsn(Opcodes.DUP)
        }

        // Provided values, in order: captures (from fields) then SAM args (from params).
        // For an instance call the first provided value is the receiver.
        val provided = caps.size + samArgs.size
        var implIdx = 0 // index into implArgs for the next non-receiver value
        val samSlotBase = 1 // SAM params start at local 1
        val samSlot = IntArray(samArgs.size)
        var sp = samSlotBase
        for (i in samArgs.indices) {
            samSlot[i] = sp
            sp += samArgs[i].size
        }

        for (pos in 0 until provided) {
            val isCapture = pos < caps.size
            val srcType = if (isCapture) caps[pos] else samArgs[pos - caps.size]
            // load the value
            if (isCapture) {
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETFIELD, s.genName, "f$pos", srcType.descriptor)
            } else {
                mv.visitVarInsn(srcType.getOpcode(Opcodes.ILOAD), samSlot[pos - caps.size])
            }
            // adapt to the impl's expected type
            val dstType: Type
            if (instanceCall && pos == 0) {
                dstType = Type.getObjectType(s.impl.owner) // receiver
            } else {
                dstType = implArgs[implIdx++]
            }
            coerce(mv, srcType, dstType)
        }

        val op = if (ctorRef) Opcodes.INVOKESPECIAL
        else if (tag == Opcodes.H_INVOKESTATIC) Opcodes.INVOKESTATIC
        else if (tag == Opcodes.H_INVOKEINTERFACE) Opcodes.INVOKEINTERFACE
        else if (tag == Opcodes.H_INVOKESPECIAL) Opcodes.INVOKESPECIAL
        else Opcodes.INVOKEVIRTUAL
        val itf = tag == Opcodes.H_INVOKEINTERFACE
        mv.visitMethodInsn(op, s.impl.owner, s.impl.name, s.impl.desc, itf)

        // result type produced by the impl, then adapt to the SAM return type
        val implRet = if (ctorRef) Type.getObjectType(s.impl.owner) else Type.getReturnType(s.impl.desc)
        if (samRet.sort == Type.VOID) {
            if (implRet.sort != Type.VOID) {
                mv.visitInsn(if (implRet.size == 2) Opcodes.POP2 else Opcodes.POP)
            }
            mv.visitInsn(Opcodes.RETURN)
        } else {
            coerce(mv, implRet, samRet)
            mv.visitInsn(samRet.getOpcode(Opcodes.IRETURN))
        }

        var providedSizes = 0
        for (c in caps) {
            providedSizes += c.size
        }
        for (a in samArgs) {
            providedSizes += a.size
        }
        mv.visitMaxs(providedSizes + 4, 1 + (sp - samSlotBase))
        mv.visitEnd()
    }

    /** Coerce the value on top of the stack from `src` to `dst` (box/unbox/cast/widen). */
    private fun coerce(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.descriptor == dst.descriptor) {
            return
        }
        val srcPrim = isPrimitive(src)
        val dstPrim = isPrimitive(dst)

        if (srcPrim && dstPrim) {
            widen(mv, src, dst)
            return
        }
        if (srcPrim) { // primitive -> reference: box
            val wrapper = wrapperOf(src)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf",
                    "(" + src.descriptor + ")L" + wrapper + ";", false)
            return
        }
        if (dstPrim) { // reference -> primitive: unbox
            val wrapper = wrapperOf(dst)
            mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, unboxMethod(dst),
                    "()" + dst.descriptor, false)
            return
        }
        // reference -> reference
        if (dst.internalName != "java/lang/Object") {
            mv.visitTypeInsn(Opcodes.CHECKCAST, dst.internalName)
        }
    }

    private fun widen(mv: MethodVisitor, src: Type, dst: Type) {
        val s = src.sort
        val d = dst.sort
        if (s == d) {
            return
        }
        // int/short/byte/char are I on the stack; widen I -> long/float/double when needed.
        if (s <= Type.INT) {
            when (d) {
                Type.LONG -> { mv.visitInsn(Opcodes.I2L); return }
                Type.FLOAT -> { mv.visitInsn(Opcodes.I2F); return }
                Type.DOUBLE -> { mv.visitInsn(Opcodes.I2D); return }
                else -> return
            }
        }
        if (s == Type.LONG) {
            if (d == Type.FLOAT) { mv.visitInsn(Opcodes.L2F); return }
            if (d == Type.DOUBLE) { mv.visitInsn(Opcodes.L2D); return }
        }
        if (s == Type.FLOAT && d == Type.DOUBLE) {
            mv.visitInsn(Opcodes.F2D)
        }
        // other narrowings are not expected from a well-typed lambda site
    }

    private fun isPrimitive(t: Type): Boolean =
            t.sort >= Type.BOOLEAN && t.sort <= Type.DOUBLE

    private fun wrapperOf(t: Type): String {
        return when (t.sort) {
            Type.BOOLEAN -> "java/lang/Boolean"
            Type.CHAR -> "java/lang/Character"
            Type.BYTE -> "java/lang/Byte"
            Type.SHORT -> "java/lang/Short"
            Type.INT -> "java/lang/Integer"
            Type.LONG -> "java/lang/Long"
            Type.FLOAT -> "java/lang/Float"
            Type.DOUBLE -> "java/lang/Double"
            else -> throw IllegalArgumentException("not a primitive: $t")
        }
    }

    private fun unboxMethod(t: Type): String {
        return when (t.sort) {
            Type.BOOLEAN -> "booleanValue"
            Type.CHAR -> "charValue"
            Type.BYTE -> "byteValue"
            Type.SHORT -> "shortValue"
            Type.INT -> "intValue"
            Type.LONG -> "longValue"
            Type.FLOAT -> "floatValue"
            Type.DOUBLE -> "doubleValue"
            else -> throw IllegalArgumentException("not a primitive: $t")
        }
    }

    private fun argDesc(types: Array<Type>): String {
        val sb = StringBuilder()
        for (t in types) {
            sb.append(t.descriptor)
        }
        return sb.toString()
    }
}
