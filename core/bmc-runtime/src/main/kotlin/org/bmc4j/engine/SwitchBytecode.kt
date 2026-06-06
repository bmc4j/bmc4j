package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.ArrayList

/**
 * Desugars pattern-matching `switch` `invokedynamic` (bootstraps
 * `java.lang.runtime.SwitchBootstraps.typeSwitch` and `.enumSwitch`) into an ordinary
 * `instanceof`/`equals`/`==` chain, because JBMC links an indy call site to an
 * *unconstrained* result — which is unsound for a symbolic-typed subject (the selected branch
 * is decoupled from the subject's real type). The others (string concat, lambdas, record
 * `equals`/`hashCode`/`toString`) are already desugared the same way in
 * [StringBytecode] / [LambdaBytecode]; whatever no pass recognises is surfaced by
 * [ResidualIndyBytecode] rather than silently trusted.
 *
 * `enumSwitch` (a pattern switch over an enum subject, e.g. one with a `case null`
 * arm) shares the typeSwitch contract below; its labels are mostly plain Strings naming constants of
 * the *selector's* enum type (recovered from the indy descriptor), matched by identity —
 * enum constants are singletons, the same reason javac's indy-free `$SwitchMap` form is
 * already sound under JBMC.
 *
 * **The contract we reproduce, exactly.** A `typeSwitch(Object target, int restartIndex)`
 * carries an ordered label list in its bootstrap arguments and returns:
 * - `-1` if `target == null`;
 * - otherwise the index `i` of the *first* label with `i >= restartIndex` that
 *   the target matches — a **type label** (a `Class`) matches when
 *   `label.isInstance(target)`; a **constant label** (`Integer`/`String`/...
 *   constant) matches when `label.equals(target)`; an **enum-constant label** (an
 *   `Enum$EnumDesc` dynamic constant) matches when `target == ThatEnumConstant`;
 * - otherwise `labels.length` (no match — the `default`/`MatchException` arm).
 *
 * The `restartIndex` parameter is how guards re-enter: when a guarded case's `when`
 * clause fails, javac re-invokes the indy with `restartIndex` set to the next case, so the same
 * subject resumes matching *after* the failed case. Honouring `restartIndex` verbatim is
 * what makes guarded switches sound.
 *
 * We replace each such site with an `invokestatic` to a synthesized per-class static helper
 * of the *same descriptor* (`(Ljava/lang/Object;I)I`); the dynamic args already on the
 * stack (subject, restartIndex) become the helper's arguments unchanged. The helper is a linear
 * `i >= restartIndex && <match>` chain — all of `instanceof`, reference `==`, and
 * `Integer/String.equals` (via [BmcStrings.objEquals]) are modelled soundly by JBMC over
 * a symbolic-typed subject, so the selected branch is now provably tied to the subject's real type.
 *
 * **Soundness guard.** If a label is a shape we don't recognise (e.g. a future constant kind),
 * we leave the whole indy *untouched* rather than emit a stand-in that might silently diverge
 * from the JDK contract — never trade a known-unsound site for an unknown-unsound one.
 */
object SwitchBytecode {

    private const val SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps"
    private const val BMC_STRINGS = "org/bmc4j/engine/BmcStrings"

    private val CACHE = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], memoized per classpath (race-free). */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, SwitchBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "switches", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /** One `typeSwitch` site we replaced with a call to a generated helper. */
    private class SwitchHelper(
            @JvmField val name: String,        // generated method name
            @JvmField val desc: String,        // same descriptor as the indy: (Ljava/lang/Object;I)I
            @JvmField val labels: List<LabelMatch>)

    /** A single switch label, decoded into how it matches a subject. */
    private abstract class LabelMatch

    /** Type pattern: matches when `internalName.isInstance(target)`. */
    private class TypeLabel(@JvmField val internalName: String) : LabelMatch()

    /** `String` constant label: matches when `value.equals(target)` (sound via BmcStrings). */
    private class StringLabel(@JvmField val value: String) : LabelMatch()

    /** Boxed-numeric / Character / Boolean constant label: matches when `box(value).equals(target)`. */
    private class BoxedConstLabel(
            @JvmField val value: Any   // Integer / Long / Float / Double / Character / Boolean / Byte / Short
    ) : LabelMatch()

    /** Enum-constant label: matches when `target == EnumClass.CONSTANT` (reference identity). */
    private class EnumLabel(
            @JvmField val enumInternalName: String, // e.g. "probe/Color"
            @JvmField val constantName: String      // e.g. "RED"
    ) : LabelMatch()

    /** Pure transform: desugar `SwitchBootstraps.typeSwitch` sites. Exposed for unit tests. */
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val helpers = ArrayList<SwitchHelper>()
        val counter = intArrayOf(0)
        val owner = arrayOfNulls<String>(1)
        val isInterface = booleanArrayOf(false)

        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String?, sig: String?,
                               superName: String?, ifs: Array<String>?) {
                owner[0] = name
                isInterface[0] = (access and Opcodes.ACC_INTERFACE) != 0
                super.visit(version, access, name, sig, superName, ifs)
            }

            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?,
                                                        vararg bsmArgs: Any?) {
                        if (SWITCH_BOOTSTRAPS == bsm!!.owner
                                && (name == "typeSwitch" || name == "enumSwitch")) {
                            // Same contract, different label encoding: typeSwitch labels are
                            // Class/constant/EnumDesc; enumSwitch labels are mostly plain Strings
                            // NAMING constants of the selector's enum type (taken from the indy
                            // descriptor), matched by identity — not by string equality.
                            val labels = if (name == "typeSwitch")
                                decodeLabels(bsmArgs)
                            else
                                decodeEnumSwitchLabels(desc!!, bsmArgs)
                            if (labels != null) {
                                val hName = "bmc$" + name + "$" + (counter[0]++)
                                helpers.add(SwitchHelper(hName, desc!!, labels))
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0])
                                return
                            }
                        }
                        // Any unrecognised bootstrap / label shape: leave untouched rather than risk an
                        // unsound stand-in — the ResidualIndyBytecode pass then surfaces it as a
                        // visible nondet stub (footnote / strictStubs / REFUTED-to-UNKNOWN demotion).
                        super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
                    }
                }
            }

            override fun visitEnd() {
                for (h in helpers) {
                    emitSwitchHelper(cw, isInterface[0], h)
                }
                super.visitEnd()
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Decode the `typeSwitch` bootstrap arguments into our match list, or return `null` to
     * signal "leave this site alone" if any label is a shape we don't soundly handle.
     */
    private fun decodeLabels(bsmArgs: Array<out Any?>): List<LabelMatch>? {
        val labels = ArrayList<LabelMatch>(bsmArgs.size)
        for (arg in bsmArgs) {
            if (arg is Type) {
                if (arg.sort != Type.OBJECT && arg.sort != Type.ARRAY) {
                    return null // a primitive type label is not a thing typeSwitch emits
                }
                labels.add(TypeLabel(arg.internalName))
            } else if (arg is String) {
                labels.add(StringLabel(arg))
            } else if (arg is Int || arg is Long || arg is Float
                    || arg is Double || arg is Char || arg is Boolean
                    || arg is Byte || arg is Short) {
                labels.add(BoxedConstLabel(arg))
            } else if (arg is ConstantDynamic) {
                val e = decodeEnumLabel(arg)
                        ?: return null // unknown dynamic-constant label
                labels.add(e)
            } else {
                return null // unknown label kind
            }
        }
        return labels
    }

    /**
     * Decode `enumSwitch` bootstrap arguments. The selector's enum type comes from the indy
     * descriptor (`(L<EnumType>;I)I`); a plain `String` label names a constant of that
     * type and matches by **identity** (`target == EnumType.NAME` — enum constants are
     * singletons), NOT by string equality; a `Class` label is an ordinary type pattern; a
     * qualified `Enum$EnumDesc` dynamic constant decodes like typeSwitch's. `null` =
     * "leave this site alone" (an unknown label shape falls through to the residual-indy surfacing).
     */
    private fun decodeEnumSwitchLabels(desc: String, bsmArgs: Array<out Any?>): List<LabelMatch>? {
        val args = Type.getArgumentTypes(desc)
        if (args.size != 2 || args[0].sort != Type.OBJECT) {
            return null // not the (EnumType, int) shape we know
        }
        val enumInternalName = args[0].internalName
        val labels = ArrayList<LabelMatch>(bsmArgs.size)
        for (arg in bsmArgs) {
            if (arg is String) {
                labels.add(EnumLabel(enumInternalName, arg))
            } else if (arg is Type) {
                if (arg.sort != Type.OBJECT && arg.sort != Type.ARRAY) {
                    return null
                }
                labels.add(TypeLabel(arg.internalName))
            } else if (arg is ConstantDynamic) {
                val e = decodeEnumLabel(arg)
                        ?: return null
                labels.add(e)
            } else {
                return null // unknown label kind
            }
        }
        return labels
    }

    /**
     * Decode an `Enum$EnumDesc` dynamic constant (as javac emits for an enum-constant label in a
     * mixed pattern switch). Shape:
     * `ConstantBootstraps.invoke(EnumDesc.of, <ClassDesc dynamic>, "CONSTANT")` where the inner
     * `ClassDesc` dynamic carries the enum's binary class name as a plain String argument.
     * Returns `null` if it isn't that exact shape.
     */
    private fun decodeEnumLabel(cd: ConstantDynamic): EnumLabel? {
        if ("java/lang/Enum\$EnumDesc" != Type.getType(cd.descriptor).internalName) {
            return null
        }
        if (cd.bootstrapMethodArgumentCount != 3) {
            return null
        }
        val classDescArg = cd.getBootstrapMethodArgument(1)
        val nameArg = cd.getBootstrapMethodArgument(2)
        if (nameArg !is String || classDescArg !is ConstantDynamic) {
            return null
        }
        val constantName: String = nameArg
        val classDesc: ConstantDynamic = classDescArg
        // The inner ClassDesc dynamic's last arg is the binary class name, e.g. "probe.Color".
        if (classDesc.bootstrapMethodArgumentCount < 1) {
            return null
        }
        val binName = classDesc.getBootstrapMethodArgument(classDesc.bootstrapMethodArgumentCount - 1)
        if (binName !is String) {
            return null
        }
        return EnumLabel(binName.replace('.', '/'), constantName)
    }

    /**
     * Synthesize `static int <name>(Object target, int restartIndex)` computing the `typeSwitch`
     * contract with a linear `instanceof`/`equals`/`==` chain. Every
     * branch target is reached with an empty operand stack, so explicit `F_NEW` frames (locals =
     * `[Object, int]`, no stack) suffice and we avoid `COMPUTE_FRAMES`.
     */
    private fun emitSwitchHelper(cw: ClassWriter, isInterface: Boolean, h: SwitchHelper) {
        val access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or
                (if (isInterface) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE)
        val mv = cw.visitMethod(access, h.name, h.desc, null, null)
        mv.visitCode()

        // if (target == null) return -1;
        val notNull = Label()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull)
        mv.visitInsn(Opcodes.ICONST_M1)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitLabel(notNull)
        frame(mv)

        var wide = false
        for (i in h.labels.indices) {
            val lm = h.labels[i]
            val next = Label()
            // if (restartIndex > i) skip this label.
            mv.visitVarInsn(Opcodes.ILOAD, 1)
            pushInt(mv, i)
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, next)

            // if (<matches>) return i;
            if (lm is TypeLabel) {
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitTypeInsn(Opcodes.INSTANCEOF, lm.internalName)
                mv.visitJumpInsn(Opcodes.IFEQ, next)
            } else if (lm is StringLabel) {
                mv.visitLdcInsn(lm.value)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
                mv.visitJumpInsn(Opcodes.IFEQ, next)
            } else if (lm is BoxedConstLabel) {
                if (lm.value is Long || lm.value is Double) {
                    wide = true
                }
                pushBoxed(mv, lm.value)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
                mv.visitJumpInsn(Opcodes.IFEQ, next)
            } else {
                val el = lm as EnumLabel
                // target == EnumClass.CONSTANT  (enum constants are singletons => reference identity)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETSTATIC, el.enumInternalName, el.constantName,
                        "L" + el.enumInternalName + ";")
                mv.visitJumpInsn(Opcodes.IF_ACMPNE, next)
            }
            pushInt(mv, i)
            mv.visitInsn(Opcodes.IRETURN)

            mv.visitLabel(next)
            frame(mv)
        }

        // no label matched -> labels.length (the default / MatchException arm)
        pushInt(mv, h.labels.size)
        mv.visitInsn(Opcodes.IRETURN)

        // Peak stack: a String/boxed compare pushes 2 refs; a long/double box pushes a category-2
        // value (constant) + target ref before boxing => up to 3 slots.
        mv.visitMaxs(if (wide) 3 else 2, 2)
        mv.visitEnd()
    }

    /** Stack-map frame at a branch target: locals `[Object, int]`, empty stack. */
    private fun frame(mv: MethodVisitor) {
        mv.visitFrame(Opcodes.F_NEW, 2, arrayOf<Any>("java/lang/Object", Opcodes.INTEGER), 0, arrayOf<Any>())
    }

    /** Push an `int` constant with the most compact opcode. */
    private fun pushInt(mv: MethodVisitor, v: Int) {
        if (v >= -1 && v <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + v) // ICONST_M1..ICONST_5 are contiguous around ICONST_0
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, v)
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, v)
        } else {
            mv.visitLdcInsn(v)
        }
    }

    /** Push a boxed wrapper for a numeric/char/boolean constant label (so `objEquals` sees it). */
    private fun pushBoxed(mv: MethodVisitor, value: Any) {
        if (value is Int) {
            pushInt(mv, value)
            box(mv, "java/lang/Integer", "(I)Ljava/lang/Integer;")
        } else if (value is Long) {
            mv.visitLdcInsn(value)
            box(mv, "java/lang/Long", "(J)Ljava/lang/Long;")
        } else if (value is Float) {
            mv.visitLdcInsn(value)
            box(mv, "java/lang/Float", "(F)Ljava/lang/Float;")
        } else if (value is Double) {
            mv.visitLdcInsn(value)
            box(mv, "java/lang/Double", "(D)Ljava/lang/Double;")
        } else if (value is Char) {
            pushInt(mv, value.code)
            box(mv, "java/lang/Character", "(C)Ljava/lang/Character;")
        } else if (value is Boolean) {
            mv.visitInsn(if (value) Opcodes.ICONST_1 else Opcodes.ICONST_0)
            box(mv, "java/lang/Boolean", "(Z)Ljava/lang/Boolean;")
        } else if (value is Byte) {
            pushInt(mv, value.toInt())
            box(mv, "java/lang/Byte", "(B)Ljava/lang/Byte;")
        } else if (value is Short) {
            pushInt(mv, value.toInt())
            box(mv, "java/lang/Short", "(S)Ljava/lang/Short;")
        } else {
            throw IllegalArgumentException("not a boxable constant: $value")
        }
    }

    private fun box(mv: MethodVisitor, wrapper: String, desc: String) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", desc, false)
    }
}
