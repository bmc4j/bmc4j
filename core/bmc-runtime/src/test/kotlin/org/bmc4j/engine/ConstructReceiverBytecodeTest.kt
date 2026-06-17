package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ConstructReceiverBytecode] — the receiver-construction pass that synthesises a static
 * wrapper `new EntryClass().proofMethod()` into the entry class so jbmc runs `<init>` (pinning instance
 * fields to their initializers) instead of analysing a nondet `this`.
 *
 * The engine-level demonstration (an instance array / scalar proof that VERIFIES only because the receiver
 * is constructed, plus the no-ctor fallback) lives in the `proofs.constructreceiver` example proofs against
 * the real engine; these tests pin the bytecode contract: the eligibility decision, the synthesised
 * wrapper's exact shape, that the PROOF METHOD's bytecode (hence its loop ids) is byte-identical, and the
 * mirror keying.
 */
internal class ConstructReceiverBytecodeTest {

    // --- Fixtures built with ASM so we control ctor/instance/static/loop shape ------------------

    private companion object {
        const val ENTRY = "pkg/Proofs"
        const val PROOF = "proof"
        const val PROOF_DESC = "()V"
    }

    /** A class `pkg/Proofs` with a no-arg ctor, an instance field initialised in `<init>`, and an
     *  instance `()V` proof method that contains [loops] back-edge loops (so we can pin its loop ids). */
    private fun instanceProofClass(withNoArgCtor: Boolean = true, isAbstract: Boolean = false,
                                   staticProof: Boolean = false, loops: Int = 0): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val access = Opcodes.ACC_PUBLIC or (if (isAbstract) Opcodes.ACC_ABSTRACT else 0)
        cw.visit(Opcodes.V17, access, ENTRY, null, "java/lang/Object", null)
        cw.visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL, "n", "I", null, null).visitEnd()

        if (withNoArgCtor) {
            val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            ctor.visitCode()
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            // this.n = 8  (the instance-field initializer that <init> runs)
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            ctor.visitIntInsn(Opcodes.BIPUSH, 8)
            ctor.visitFieldInsn(Opcodes.PUTFIELD, ENTRY, "n", "I")
            ctor.visitInsn(Opcodes.RETURN)
            ctor.visitMaxs(0, 0)
            ctor.visitEnd()
        } else {
            // Only a parameterised ctor: no analysable no-arg constructor.
            val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null)
            ctor.visitCode()
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            ctor.visitInsn(Opcodes.RETURN)
            ctor.visitMaxs(0, 0)
            ctor.visitEnd()
        }

        val pAccess = Opcodes.ACC_PUBLIC or (if (staticProof) Opcodes.ACC_STATIC else 0)
        val mv = cw.visitMethod(pAccess, PROOF, PROOF_DESC, null, null)
        mv.visitCode()
        // Emit `loops` simple counted loops so the proof method has back edges (loop ids 0..loops-1).
        for (i in 0 until loops) {
            val top = Label()
            val end = Label()
            val v = if (staticProof) 0 else 1
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitVarInsn(Opcodes.ISTORE, v)
            mv.visitLabel(top)
            mv.visitVarInsn(Opcodes.ILOAD, v)
            mv.visitIntInsn(Opcodes.BIPUSH, 4)
            mv.visitJumpInsn(Opcodes.IF_ICMPGE, end)
            mv.visitIincInsn(v, 1)
            mv.visitJumpInsn(Opcodes.GOTO, top)
            mv.visitLabel(end)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()

        cw.visitEnd()
        return cw.toByteArray()
    }

    // --- Eligibility decision -------------------------------------------------------------------

    @Test
    fun instanceProofWithNoArgCtor_isEligible_andCapturesTheProofDescriptor() {
        val d = ConstructReceiverBytecode.analyzeBytes(instanceProofClass(), PROOF)
        assertTrue(d.eligible, "an instance proof on a class with a no-arg ctor constructs its receiver")
        assertEquals(ConstructReceiverBytecode.Reason.ELIGIBLE, d.reason)
        assertEquals(PROOF_DESC, d.proofDesc, "the proof method's exact descriptor is captured")
    }

    @Test
    fun staticProofMethod_fallsBack_noReceiverNeeded() {
        val d = ConstructReceiverBytecode.analyzeBytes(instanceProofClass(staticProof = true), PROOF)
        assertFalse(d.eligible)
        assertEquals(ConstructReceiverBytecode.Reason.STATIC_PROOF, d.reason,
                "a static proof method has no receiver to construct")
    }

    @Test
    fun noAnalyzableNoArgCtor_fallsBack() {
        val d = ConstructReceiverBytecode.analyzeBytes(instanceProofClass(withNoArgCtor = false), PROOF)
        assertFalse(d.eligible)
        assertEquals(ConstructReceiverBytecode.Reason.NO_ANALYZABLE_NO_ARG_CTOR, d.reason,
                "only a parameterised ctor means no constructible no-arg receiver -> fallback")
    }

    @Test
    fun abstractEntryClass_fallsBack() {
        val d = ConstructReceiverBytecode.analyzeBytes(instanceProofClass(isAbstract = true), PROOF)
        assertFalse(d.eligible)
        assertEquals(ConstructReceiverBytecode.Reason.ABSTRACT_ENTRY_CLASS, d.reason,
                "an abstract entry class cannot be constructed")
    }

    @Test
    fun unknownProofMethod_fallsBack() {
        val d = ConstructReceiverBytecode.analyzeBytes(instanceProofClass(), "noSuchMethod")
        assertFalse(d.eligible)
        assertEquals(ConstructReceiverBytecode.Reason.PROOF_METHOD_NOT_FOUND, d.reason)
    }

    // --- Synthesised wrapper shape --------------------------------------------------------------

    @Test
    fun rewrite_addsAStaticWrapperThatConstructsAndCallsTheProof() {
        val rewritten = ConstructReceiverBytecode.rewriteClass(instanceProofClass(), ENTRY, PROOF, PROOF_DESC)
        val w = wrapperOf(rewritten, ConstructReceiverBytecode.wrapperName(PROOF))
                ?: error("the synthetic wrapper must be present")
        assertTrue(w.isStatic, "the wrapper is static (no receiver of its own)")
        assertEquals("()V", w.descriptor, "the wrapper is a no-arg void entry")
        assertTrue(w.throwsThrowable, "the wrapper declares throws Throwable so any checked proof signature calls cleanly")
        // It must NEW the entry, run <init>, then INVOKEVIRTUAL the proof method on the fresh receiver.
        assertTrue(w.calls.contains("NEW $ENTRY"), "constructs the entry class: ${w.calls}")
        assertTrue(w.calls.contains("INVOKESPECIAL $ENTRY.<init>()V"), "runs the no-arg <init>: ${w.calls}")
        assertTrue(w.calls.contains("INVOKEVIRTUAL $ENTRY.$PROOF$PROOF_DESC"),
                "calls the proof method on the constructed receiver: ${w.calls}")
    }

    @Test
    fun rewrite_leavesTheProofMethodBytecode_byteIdentical_soLoopIdsArePreserved() {
        // A proof method with two loops -> loop ids ...method:()V.0 and .1, numbered by back-edge order.
        val original = instanceProofClass(loops = 2)
        val rewritten = ConstructReceiverBytecode.rewriteClass(original, ENTRY, PROOF, PROOF_DESC)
        assertArrayEquals(rawMethodBody(original, PROOF, PROOF_DESC),
                rawMethodBody(rewritten, PROOF, PROOF_DESC),
                "the proof method's instructions must be byte-identical after adding the wrapper, so its " +
                        "back-edge-numbered loop ids (java::pkg.Proofs.proof:()V.N) never move")
        // And the constructor is untouched too (the wrapper is purely additive).
        assertArrayEquals(rawMethodBody(original, "<init>", "()V"),
                rawMethodBody(rewritten, "<init>", "()V"),
                "the constructor is unchanged")
    }

    @Test
    fun rewrite_isANoOpForANonEntryClass() {
        val other = otherClass()
        assertArrayEquals(other, ConstructReceiverBytecode.rewriteClass(other, ENTRY, PROOF, PROOF_DESC),
                "a class that is not the entry class is copied verbatim")
    }

    // --- Mirror keying --------------------------------------------------------------------------

    @Test
    fun rewrite_overAClasspath_synthesizesIntoOnlyTheEntryClass(@TempDir tmp: Path) {
        val dir = Files.createDirectory(tmp.resolve("out"))
        writeClass(dir, ENTRY, instanceProofClass())
        writeClass(dir, "pkg/Other", otherClass())

        val mirrored = ConstructReceiverBytecode.rewrite(dir.toString(), "pkg.Proofs", PROOF, PROOF_DESC)
        // The mirror is a fresh dir; the entry class there carries the wrapper, the other class does not.
        val mirrorDir = Path.of(mirrored)
        assertNotEquals(dir.toString(), mirrored, "the rewrite mirrors to a fresh content-hashed dir")
        assertTrue(hasWrapper(Files.readAllBytes(mirrorDir.resolve("$ENTRY.class")), PROOF),
                "the entry class in the mirror carries the synthetic wrapper")
        assertFalse(hasWrapper(Files.readAllBytes(mirrorDir.resolve("pkg/Other.class")), PROOF),
                "a non-entry class is unchanged in the mirror")
    }

    // --- pass gating + fallback (no engine) -----------------------------------------------------

    @Test
    fun pass_isSkipped_andEntryIsUnchanged_whenTheProofFallsBack(@TempDir tmp: Path) {
        // A proof class with only a parameterised ctor: no analysable no-arg constructor -> FALLBACK.
        val dir = Files.createDirectory(tmp.resolve("out"))
        writeClass(dir, ENTRY, instanceProofClass(withNoArgCtor = false))
        val request = BmcRequest("pkg.Proofs", "pkg.Proofs.$PROOF", dir.toString(), 16, true, 16)

        val decision = ConstructReceiverBytecode.analyze(
                request.classpath, request.entryClass, PROOF)
        assertFalse(decision.eligible, "no analysable no-arg ctor -> not eligible")

        // The pass gate (shouldTransform) is OFF for a fallback, so the entry stays the proof method
        // itself — exactly today's nondet-`this` entry, no wrapper, no crash.
        val ctx = BmcContext(request, "jbmc").also { it.receiverDecision = decision }
        assertFalse(ConstructReceiverPass.shouldTransform(ctx),
                "the construct-receiver pass is a no-op when the proof falls back")
    }

    @Test
    fun pass_runs_whenEligible() {
        val request = BmcRequest("pkg.Proofs", "pkg.Proofs.$PROOF", "/cp", 16, true, 16)
        val ctx = BmcContext(request, "jbmc").also {
            it.receiverDecision = ConstructReceiverBytecode.Decision(
                    ConstructReceiverBytecode.Reason.ELIGIBLE, PROOF_DESC)
        }
        assertTrue(ConstructReceiverPass.shouldTransform(ctx),
                "the pass runs for an eligible instance proof")
    }

    @Test
    fun optOut_property_forcesFallback() {
        val prev = System.getProperty("bmc.constructReceiver")
        try {
            // With a real eligible class on a temp dir, the opt-out still forces a fallback Decision.
            System.setProperty("bmc.constructReceiver", "false")
            val d = ConstructReceiverBytecode.analyze("/cp", "pkg.Proofs", PROOF)
            assertFalse(d.eligible)
            assertEquals(ConstructReceiverBytecode.Reason.DISABLED, d.reason,
                    "-Dbmc.constructReceiver=false restores the legacy nondet-`this` entry")
        } finally {
            if (prev == null) System.clearProperty("bmc.constructReceiver")
            else System.setProperty("bmc.constructReceiver", prev)
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private class WrapperInfo(val isStatic: Boolean, val descriptor: String, val throwsThrowable: Boolean,
                              val calls: List<String>)

    /** Extract the synthetic wrapper [name] from [clazz] (or null if absent), with its modifiers + the
     *  type/invoke instructions it emits, for shape assertions. */
    private fun wrapperOf(clazz: ByteArray, name: String): WrapperInfo? {
        var info: WrapperInfo? = null
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != name) {
                    return null
                }
                val calls = ArrayList<String>()
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (op == Opcodes.NEW) calls.add("NEW $type")
                    }

                    override fun visitMethodInsn(op: Int, owner: String?, mn: String?, md: String?,
                                                 itf: Boolean) {
                        val opName = when (op) {
                            Opcodes.INVOKESPECIAL -> "INVOKESPECIAL"
                            Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
                            else -> "INVOKE$op"
                        }
                        calls.add("$opName $owner.$mn$md")
                    }

                    override fun visitEnd() {
                        info = WrapperInfo(
                                (access and Opcodes.ACC_STATIC) != 0, d ?: "",
                                ex?.contains("java/lang/Throwable") == true, calls)
                    }
                }
            }
        }, 0)
        return info
    }

    private fun hasWrapper(clazz: ByteArray, method: String): Boolean =
            wrapperOf(clazz, ConstructReceiverBytecode.wrapperName(method)) != null

    /** The raw instruction bytes of method [name][desc] in [clazz] (the Code attribute's code array),
     *  for a byte-identity comparison of the proof method across the rewrite. */
    private fun rawMethodBody(clazz: ByteArray, name: String, desc: String): ByteArray {
        var out = ByteArray(0)
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != name || d != desc) {
                    return null
                }
                // Re-encode the method through a writer that ONLY carries this method, so two encodings of
                // an unchanged method are byte-equal (constant-pool order can't perturb a code-array compare
                // because we compare the recorded opcode stream, not the class file).
                return object : MethodVisitor(Opcodes.ASM9) {
                    val ops = StringBuilder()
                    override fun visitInsn(op: Int) { ops.append("i$op;") }
                    override fun visitIntInsn(op: Int, o: Int) { ops.append("ii$op,$o;") }
                    override fun visitVarInsn(op: Int, v: Int) { ops.append("v$op,$v;") }
                    override fun visitJumpInsn(op: Int, l: Label?) { ops.append("j$op;") }
                    override fun visitIincInsn(v: Int, i: Int) { ops.append("inc$v,$i;") }
                    override fun visitFieldInsn(op: Int, ow: String?, fn: String?, fd: String?) {
                        ops.append("f$op,$ow.$fn:$fd;")
                    }
                    override fun visitMethodInsn(op: Int, ow: String?, mn: String?, md: String?, itf: Boolean) {
                        ops.append("m$op,$ow.$mn$md;")
                    }
                    override fun visitEnd() { out = ops.toString().toByteArray() }
                }
            }
        }, 0)
        return out
    }

    private fun otherClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Other", null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(0, 0)
        ctor.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun writeClass(dir: Path, internalName: String, bytes: ByteArray) {
        val f = dir.resolve("$internalName.class")
        Files.createDirectories(f.parent)
        Files.write(f, bytes)
    }
}
