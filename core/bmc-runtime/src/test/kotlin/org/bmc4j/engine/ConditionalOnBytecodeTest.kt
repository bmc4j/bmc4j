package org.bmc4j.engine

import org.bmc4j.BmcCondition
import org.bmc4j.StringMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ConditionalOnBytecode], the prep-time `@ConditionalOn` override-swap pass.
 *
 * Two layers, no engine needed:
 *  - the pure [ConditionalOnBytecode.rewriteClass] transform redirects a target call site to its override
 *    when handed a firing redirect map, and excludes the override's own body; and
 *  - the end-to-end [ConditionalOnBytecode.rewrite] over a real classpath dir fires the redirect ONLY
 *    when the override's [BmcCondition] holds for the request (here: string mode), and is a no-op when it
 *    does not.
 */
internal class ConditionalOnBytecodeTest {

    // ---- pure transform: a firing redirect map retargets the target call site ----

    @Test
    fun rewriteClass_redirects_target_callsite_to_override() {
        val byCallSite = mapOf("p/Model tgt (I)Ljava/lang/String;" to "ovr")
        val overrideNames = mapOf("p/Model" to setOf("ovr"))
        val calls = methodCalls(ConditionalOnBytecode.rewriteClass(
                classCalling("p/Model", "tgt", "(I)Ljava/lang/String;"), byCallSite, overrideNames))
        assertTrue(calls.contains("INVOKESTATIC p/Model.ovr(I)Ljava/lang/String;"),
                "the tgt call site must be retargeted to ovr (same descriptor): $calls")
        assertFalse(calls.any { it.contains("p/Model.tgt") },
                "the original tgt call must be gone: $calls")
    }

    @Test
    fun rewriteClass_leaves_other_descriptor_overload_untouched() {
        // The redirect names tgt(I); a tgt(J) overload is a DIFFERENT call site and must pass through.
        val byCallSite = mapOf("p/Model tgt (I)Ljava/lang/String;" to "ovr")
        val overrideNames = mapOf("p/Model" to setOf("ovr"))
        val calls = methodCalls(ConditionalOnBytecode.rewriteClass(
                classCalling("p/Model", "tgt", "(J)Ljava/lang/String;"), byCallSite, overrideNames))
        assertTrue(calls.any { it.contains("p/Model.tgt(J)Ljava/lang/String;") },
                "the long overload must pass through unredirected: $calls")
    }

    @Test
    fun rewriteClass_does_not_rewrite_the_override_own_body() {
        // A class whose OWN method `ovr` calls tgt must NOT have that inner call redirected (anti-loop).
        val bytes = classWithMethodCalling("p/Model", "ovr", "(I)Ljava/lang/String;",
                "p/Model", "tgt", "(I)Ljava/lang/String;")
        val byCallSite = mapOf("p/Model tgt (I)Ljava/lang/String;" to "ovr")
        val overrideNames = mapOf("p/Model" to setOf("ovr"))
        val calls = methodCalls(ConditionalOnBytecode.rewriteClass(bytes, byCallSite, overrideNames))
        assertTrue(calls.any { it.contains("p/Model.tgt(I)Ljava/lang/String;") },
                "the override's own call to tgt must be left alone (no self-loop): $calls")
    }

    // ---- end-to-end: the scan only fires the redirect when the condition holds ----

    @Test
    fun rewrite_redirects_when_condition_holds(@TempDir dir: Path) {
        writeModelClasspath(dir)
        // STRING_REFINEMENT_OFF holds under CHAR_ARRAY_MODEL -> the override fires.
        val out = ConditionalOnBytecode.rewrite(dir.toString(),
                request(StringMode.CHAR_ARRAY_MODEL))
        val callerCalls = methodCallsOf(out, "p/Caller")
        assertTrue(callerCalls.contains("INVOKESTATIC p/Model.ovr(I)Ljava/lang/String;"),
                "under CHAR_ARRAY_MODEL the tgt call must be redirected to ovr: $callerCalls")
        assertFalse(callerCalls.any { it.contains("p/Model.tgt") },
                "the original tgt call must be gone under CHAR_ARRAY_MODEL: $callerCalls")
    }

    @Test
    fun rewrite_is_noop_when_condition_does_not_hold(@TempDir dir: Path) {
        writeModelClasspath(dir)
        // STRING_REFINEMENT_OFF does NOT hold under REFINEMENT -> no redirect, classpath unchanged string.
        val out = ConditionalOnBytecode.rewrite(dir.toString(), request(StringMode.REFINEMENT))
        // No firing override: rewrite returns the classpath unchanged (no mirror created).
        val callerCalls = methodCallsOf(out, "p/Caller")
        assertTrue(callerCalls.any { it.contains("p/Model.tgt(I)Ljava/lang/String;") },
                "under REFINEMENT the tgt call must be left untouched: $callerCalls")
        assertFalse(callerCalls.any { it.contains("p/Model.ovr") },
                "no redirect must happen under REFINEMENT: $callerCalls")
    }

    companion object {

        private fun request(mode: StringMode): BmcRequest =
                BmcRequest("p.Caller", "p.Caller.use", "", 1, true, 16, stringMode = mode)

        /** Write `p/Model` (with a @ConditionalOn(STRING_REFINEMENT_OFF, target="tgt") override `ovr`) and
         *  `p/Caller` (which calls `Model.tgt(I)`) into [dir] as a real class dir. */
        private fun writeModelClasspath(dir: Path) {
            writeClass(dir, "p/Model", modelClass())
            writeClass(dir, "p/Caller", classCalling("p/Model", "tgt", "(I)Ljava/lang/String;")
                    .let { renameClass(it, "p/Caller") })
        }

        private fun writeClass(dir: Path, internalName: String, bytes: ByteArray) {
            val f = dir.resolve("$internalName.class")
            Files.createDirectories(f.parent)
            Files.write(f, bytes)
        }

        /** `p/Model` with a real `tgt(int):String` target and a static `ovr(int):String` carrying
         *  `@ConditionalOn(condition = STRING_REFINEMENT_OFF, target = "tgt")`. */
        private fun modelClass(): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "p/Model", null, "java/lang/Object", null)
            // static String tgt(int) { return null; }  (body irrelevant for the redirect)
            emitReturnNull(cw, Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "tgt", "(I)Ljava/lang/String;")
            // @ConditionalOn(condition = STRING_REFINEMENT_OFF, target = "tgt") static String ovr(int)
            val mv = cw.visitMethod(Opcodes.ACC_STATIC, "ovr", "(I)Ljava/lang/String;", null, null)
            val av: AnnotationVisitor = mv.visitAnnotation("Lorg/bmc4j/ConditionalOn;", true)
            av.visitEnum("condition", "Lorg/bmc4j/BmcCondition;", BmcCondition.STRING_REFINEMENT_OFF.name)
            av.visit("target", "tgt")
            av.visitEnd()
            mv.visitCode()
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun emitReturnNull(cw: ClassWriter, access: Int, name: String, desc: String) {
            val mv = cw.visitMethod(access, name, desc, null, null)
            mv.visitCode()
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
        }

        /** A class `C` with `static use(int):String` making one static call to owner.name desc. */
        private fun classCalling(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use",
                    "(I)Ljava/lang/String;", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ILOAD, 0)
            // For a (J) overload, widen the int arg to a long so the call verifies.
            if (desc.startsWith("(J)")) {
                mv.visitInsn(Opcodes.I2L)
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class `p/Model` whose method [methodName] makes one call to callOwner.callName callDesc — to
         *  exercise the anti-loop exclusion of the override's own body. */
        private fun classWithMethodCalling(owner: String, methodName: String, methodDesc: String,
                                           callOwner: String, callName: String, callDesc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_STATIC, methodName, methodDesc, null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ILOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, callOwner, callName, callDesc, false)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun renameClass(bytes: ByteArray, newInternalName: String): ByteArray {
            val cw = ClassWriter(0)
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                override fun visit(v: Int, a: Int, name: String?, s: String?, sup: String?,
                                   ifs: Array<String>?) {
                    super.visit(v, a, newInternalName, s, sup, ifs)
                }
            }, 0)
            return cw.toByteArray()
        }

        private fun methodCalls(clazz: ByteArray): List<String> = methodCallsIn(clazz)

        /** Read the class `internalName.class` out of the mirrored classpath [out] and list its calls. */
        private fun methodCallsOf(out: String, internalName: String): List<String> {
            for (entry in out.split(java.io.File.pathSeparator)) {
                if (entry.isEmpty()) continue
                val f = Path.of(entry).resolve("$internalName.class")
                if (Files.exists(f)) {
                    return methodCallsIn(Files.readAllBytes(f))
                }
            }
            error("$internalName not found on $out")
        }

        private fun methodCallsIn(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            if (op == Opcodes.INVOKESTATIC) {
                                calls.add("INVOKESTATIC $owner.$name$desc")
                            }
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
