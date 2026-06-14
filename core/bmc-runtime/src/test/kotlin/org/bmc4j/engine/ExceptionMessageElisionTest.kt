package org.bmc4j.engine

import org.bmc4j.RemoveExceptionMessages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ExceptionMessageElision]:
 *  - the **rewrite** drops the message-building bytecode at a `Throwable`-subtype `<init>(String)` site
 *    and passes `null`, leaves multi-arg and non-`Throwable` ctors verbatim, and produces JVM-valid
 *    bytecode that still constructs/throws the exception (with a null message);
 *  - the **observability gate** elides iff the reachable cone contains no exception-message observer,
 *    and never elides when an observer exists or the cone can't be bounded — the soundness contract.
 */
internal class ExceptionMessageElisionTest {

    // ---- rewrite ----------------------------------------------------------------------------------

    /** `isThrowable` that recognizes only the JDK roots (no classpath bytes) — enough for the rewrite
     *  tests, which throw `IllegalArgumentException` / a custom subtype we mark explicitly. */
    private val jdkThrowable: (String) -> Boolean = { n ->
        ExceptionMessageElision.Index("").isThrowable(n)
    }

    @Test
    fun single_string_arg_Throwable_ctor_has_its_message_elided() {
        // static void f(int n) { throw new IllegalArgumentException(new StringBuilder().append("bad: ")
        //                                                            .append(n).toString()); }
        // The whole message build (NEW StringBuilder; append; append; toString) sits between the
        // NEW IAE; DUP and the IAE.<init>(String) — it must be DROPPED, replaced by ACONST_NULL.
        val bytes = throwingClass("ThrowIae", "java/lang/IllegalArgumentException", withMessageBuild = true)
        val rewritten = ExceptionMessageElision.rewriteClass(bytes, jdkThrowable)
        val calls = methodCalls(rewritten)

        assertTrue(calls.any { it.contains("java/lang/IllegalArgumentException.<init>(Ljava/lang/String;)V") },
                "the exception is still constructed via <init>(String): $calls")
        assertFalse(calls.any { it.contains("StringBuilder") },
                "the message-building StringBuilder calls must be DROPPED: $calls")
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"),
                "the dropped message must be replaced by an explicit null")

        // The exception is STILL thrown — only its message is null now. (JVM-valid bytecode runs.)
        val c = define("ThrowIae", rewritten)
        val m = c.getMethod("f", Int::class.javaPrimitiveType)
        val thrown = runCatching { m.invoke(null, 7) }.exceptionOrNull()?.cause
        assertTrue(thrown is IllegalArgumentException, "still throws IllegalArgumentException, got $thrown")
        assertNull((thrown as IllegalArgumentException).message, "the message was elided to null")
    }

    @Test
    fun custom_Throwable_subtype_single_string_ctor_is_elided() {
        // A user exception resolved as a Throwable via the super-chain (extends RuntimeException).
        val bytes = throwingClass("ThrowCustom", "my/Boom", withMessageBuild = true)
        val isThrowable: (String) -> Boolean = { n -> n == "my/Boom" || jdkThrowable(n) }
        val rewritten = ExceptionMessageElision.rewriteClass(bytes, isThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(calls.any { it.contains("my/Boom.<init>(Ljava/lang/String;)V") },
                "the custom exception is still constructed: $calls")
        assertFalse(calls.any { it.contains("StringBuilder") },
                "the message build must be dropped for a custom Throwable too: $calls")
    }

    @Test
    fun non_Throwable_single_string_ctor_is_left_verbatim() {
        // new java/lang/StringBuilder(String) — a String-arg ctor on a non-Throwable: never elided.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "MakeSb", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "()Ljava/lang/StringBuilder;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("seed")
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        val ops = opcodes(rewritten)
        assertTrue(methodCalls(rewritten).any { it.contains("java/lang/StringBuilder.<init>(Ljava/lang/String;)V") },
                "a non-Throwable String ctor stays verbatim")
        assertFalse(ops.contains("ACONST_NULL"), "no elision for a non-Throwable ctor")
        val c = define("MakeSb", rewritten)
        assertEquals("seed", (c.getMethod("f").invoke(null) as StringBuilder).toString())
    }

    @Test
    fun multi_arg_Throwable_ctor_is_left_verbatim() {
        // new IllegalStateException(message, cause) — (String, Throwable): the message can't be isolated
        // from the other operand soundly, so the site is left untouched (no elision).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowTwo", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("boom")
        mv.visitInsn(Opcodes.ACONST_NULL) // the cause
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
                "(Ljava/lang/String;Ljava/lang/Throwable;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        assertTrue(methodCalls(rewritten).any {
            it.contains("java/lang/IllegalStateException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V")
        }, "a multi-arg Throwable ctor is left verbatim (not elided)")
        // The literal "boom" LDC is still present (message build was NOT dropped).
        assertTrue(opcodes(rewritten).contains("LDC \"boom\""), "the multi-arg message is preserved")
    }

    @Test
    fun message_build_with_intervening_label_is_still_elided() {
        // A LineNumberTable anchor (label + line) sits inside the message-building region, the shape a
        // multi-line `throw new IAE("..." + x)` emits. The region drop must NOT abandon on that label
        // (no stack effect), exactly like StringBytecode's #296 fix.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowLbl", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "()V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitLdcInsn("seed")
        val mid = org.objectweb.asm.Label()
        mv.visitLabel(mid)
        mv.visitLineNumber(99, mid)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = ExceptionMessageElision.rewriteClass(cw.toByteArray(), jdkThrowable)
        val ops = opcodes(rewritten)
        assertTrue(ops.contains("ACONST_NULL"), "an intervening label must not defeat the elision")
        assertFalse(ops.contains("LDC \"seed\""), "the message literal must be dropped")
        // Still valid bytecode that throws.
        val c = define("ThrowLbl", rewritten)
        val thrown = runCatching { c.getMethod("f").invoke(null) }.exceptionOrNull()?.cause
        assertTrue(thrown is IllegalArgumentException)
        assertNull((thrown as IllegalArgumentException).message)
    }

    // ---- dead-local backward slice ----------------------------------------------------------------

    @Test
    fun dead_local_built_by_a_fresh_object_chain_is_sliced_away() {
        // The okio-shape: a value is built by a fresh-object builder chain in a PRIOR statement, stored to
        // a local, and read ONLY by the elided exception message:
        //   Builder b = new Builder().fill(n).fillMore(n);   // local 1
        //   throw new IllegalArgumentException(b.render());   // sole reader of local 1
        // Eliding the message removes b.render(); local 1 is then dead, so its whole construction (the
        // expensive builder chain) must be sliced away too — not just the message expression.
        val rewritten = ExceptionMessageElision.rewriteClass(deadLocalBuilderClass(), buildersAreNotThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(calls.any { it.contains("ThrowDL.<init>(Ljava/lang/String;)V") },
                "the exception is still constructed: $calls")
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is elided to null")
        // The dead builder chain is gone: no fill/fillMore/render/<init> of the Builder survives, and the
        // Builder is never even NEW'd.
        assertFalse(calls.any { it.contains("bld/Builder") },
                "the dead builder chain (fresh object that escapes only to the elided message) is sliced: $calls")
    }

    @Test
    fun a_live_read_of_the_local_keeps_its_construction() {
        // Same builder, but the local is ALSO read on a live path (returned). It is not dead, so its
        // construction must be KEPT (fail-safe): only a slot read solely inside elided regions is sliced.
        val rewritten = ExceptionMessageElision.rewriteClass(
                deadLocalBuilderClass(liveReadOfLocal = true), buildersAreNotThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is still elided")
        assertTrue(calls.any { it.contains("bld/Builder.<init>") },
                "a builder whose local is read on a live path must NOT be sliced: $calls")
    }

    @Test
    fun a_local_built_by_a_static_call_is_kept() {
        // rendered = Helper.expensive(n) (a STATIC call), stored to a local read only by the elided
        // message. A static call can mutate global state we can't see, so its result-build is conservatively
        // KEPT even though the local is dead — the fail-safe boundary (we only slice fresh-object chains).
        val rewritten = ExceptionMessageElision.rewriteClass(staticCallLocalClass(), jdkThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is elided")
        assertTrue(calls.any { it.contains("Helper.expensive") },
                "a static-call-built local is conservatively kept (static effects are opaque): $calls")
    }

    // ---- dead-OBJECT lifetime (the okio shape: a discarded self-call OUTSIDE the message) ----------

    @Test
    fun okio_shape_discarded_self_call_outside_the_message_is_sliced_with_the_chain() {
        // The faithful okio Buffer.readDecimalLong overflow shape:
        //   Buffer b = new Buffer().writeDecimalLong(n).writeByte(n);   // fresh object, local 1
        //   b.readByte();                                               // DISCARDED self-call OUTSIDE msg
        //   throw new ThrowDL(b.readUtf8());                            // read INSIDE the (elided) message
        // local 1 is FULLY dead (fresh, escapes nowhere, the readByte() result discarded) — so its whole
        // lifetime, the builder chain AND the discarded readByte(), must be sliced. The prior rule kept it
        // because readByte() is an out-of-region read.
        val rewritten = ExceptionMessageElision.rewriteClass(okioShapeClass(), buildersAreNotThrowable)
        val calls = methodCalls(rewritten)
        assertTrue(calls.any { it.contains("ThrowDL.<init>(Ljava/lang/String;)V") },
                "the exception is still constructed: $calls")
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is elided to null")
        assertFalse(calls.any { it.contains("okio/Buffer") },
                "the whole dead Buffer lifetime — chain AND the discarded readByte() — is sliced: $calls")
    }

    @Test
    fun a_self_call_whose_result_is_consumed_keeps_the_lifetime() {
        // Same fresh Buffer, but the out-of-region self-call's result is CONSUMED (stored to another local),
        // not discarded — a live use. The object is not fully dead, so KEEP (fail-safe).
        val rewritten = ExceptionMessageElision.rewriteClass(
                okioShapeClass(selfCallResultConsumed = true), buildersAreNotThrowable)
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is still elided")
        assertTrue(methodCalls(rewritten).any { it.contains("okio/Buffer.<init>") },
                "a self-call whose result is consumed keeps the object lifetime")
    }

    @Test
    fun an_object_passed_as_an_argument_keeps_the_lifetime() {
        // The fresh object is passed as an ARGUMENT to a sink call (an escape), as well as read by the
        // elided message. Escaping anywhere live ⇒ KEEP.
        val rewritten = ExceptionMessageElision.rewriteClass(
                okioShapeClass(escapesAsArgument = true), buildersAreNotThrowable)
        assertTrue(opcodes(rewritten).contains("ACONST_NULL"), "the message is still elided")
        assertTrue(methodCalls(rewritten).any { it.contains("okio/Buffer.<init>") },
                "an object passed as a call argument escapes and must NOT be sliced")
    }

    @Test
    fun real_okio_readDecimalLong_slices_but_writeDecimalLong_keeps_writeUtf8() {
        // Feed okio 3.9.0's ACTUAL Buffer.class (from the gradle cache) through the rewrite, then inspect
        // the surviving instructions. This is the real-jar reproduction. It establishes two facts: the
        // throwaway-Buffer message region in readDecimalLong DOES elide and its dead lifetime IS sliced
        // (the slicer fires correctly on real okio), AND writeDecimalLong's Long.MIN_VALUE writeUtf8 call
        // is non-exceptional and survives, which is the real reason a writeDecimalLong+readDecimalLong
        // round-trip proof keeps writeUtf8 in its cone. If the jar is not present, skip (no false pass).
        val bufferClass = realOkioBufferClass() ?: return
        val rewritten = ExceptionMessageElision.rewriteClass(bufferClass) { n -> jdkOrOkioThrowable(n) }
        val rdl = methodBody(rewritten, "readDecimalLong")

        // 1. Did the "Number too large: " message region elide at all?
        val elided = rdl.any { it == "ACONST_NULL" }
        // 2. Did the throwaway-Buffer lifetime get sliced (no writeDecimalLong/writeByte/readByte/readUtf8
        //    surviving on the overflow path)? readDecimalLong also calls size() etc. on `this`, so we look
        //    specifically for the throwaway-builder calls.
        val builderCalls = rdl.filter {
            it.contains("okio/Buffer.writeDecimalLong") || it.contains("okio/Buffer.writeByte") ||
                    it.contains("okio/Buffer.readByte") || it.contains("okio/Buffer.readUtf8")
        }
        println("[okio-repro] readDecimalLong message-elided=$elided; surviving throwaway-builder calls=$builderCalls")
        assertTrue(elided, "the 'Number too large' message region must elide on real okio")
        assertTrue(builderCalls.isEmpty(),
                "the dead throwaway-Buffer lifetime must be fully sliced on real okio, surviving: $builderCalls")

        // ROOT CAUSE of the "writeUtf8 still in the cone, proof times out" symptom: it is NOT the slicer.
        // okio's writeDecimalLong(long) has a Long.MIN_VALUE special case `this.writeUtf8("-92233...808")`
        // (the value can't be negated). That writeUtf8 is a normal, NON-throwing call on `this`, on a
        // legitimate arm of writeDecimalLong's own branch. The proof CALLS writeDecimalLong(v) in its setup,
        // so the engine encodes both arms and pulls writeUtf8 into the cone REGARDLESS of any readDecimalLong
        // message elision. The exception-message slicer correctly does not (and must not) touch it: it is
        // not inside any elided exception message. So writeDecimalLong must STILL contain its writeUtf8 call
        // after the rewrite (the slicer leaves non-message code verbatim).
        val wdl = methodBody(rewritten, "writeDecimalLong")
        assertTrue(wdl.any { it.contains("okio/Buffer.writeUtf8(Ljava/lang/String;)") },
                "writeDecimalLong's Long.MIN_VALUE writeUtf8 is non-exceptional and stays (this, not the " +
                        "slicer, is what keeps writeUtf8 in the proof cone): $wdl")
    }

    /** Locate okio 3.9.0's Buffer.class in the gradle module cache and return its bytes, or null if absent
     *  (the repro then skips). Searches the user's ~/.gradle modules-2 tree for okio-jvm-3.9.0.jar. */
    private fun realOkioBufferClass(): ByteArray? {
        val home = System.getProperty("user.home") ?: return null
        val root = Path.of(home, ".gradle", "caches", "modules-2", "files-2.1",
                "com.squareup.okio", "okio-jvm", "3.9.0")
        if (!Files.isDirectory(root)) return null
        val jar = Files.walk(root).use { w ->
            w.filter { Files.isRegularFile(it) && it.fileName.toString() == "okio-jvm-3.9.0.jar" }
                    .findFirst().orElse(null)
        } ?: return null
        java.util.zip.ZipFile(jar.toFile()).use { zf ->
            val e = zf.getEntry("okio/Buffer.class") ?: return null
            return zf.getInputStream(e).use { it.readAllBytes() }
        }
    }

    /** Throwable resolver for the real-okio repro: okio defines no Throwables, so the JDK roots suffice. */
    private val jdkOrOkioThrowable: (String) -> Boolean get() = jdkThrowable

    /** Linear list of one method's surviving instructions, rendered as `OWNER.NAME` for calls, `NEW type`,
     *  `ACONST_NULL`, or the raw opcode, in visit order. Used to inspect what the rewrite left behind. */
    private fun methodBody(clazz: ByteArray, method: String): List<String> {
        val body = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor? {
                if (n != method) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        body.add("$owner.$name$desc")
                    }

                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (op == Opcodes.NEW) body.add("NEW $type")
                    }

                    override fun visitInsn(op: Int) {
                        if (op == Opcodes.ACONST_NULL) body.add("ACONST_NULL")
                    }
                }
            }
        }, 0)
        return body
    }

    // ---- observability gate -----------------------------------------------------------------------

    @Test
    fun gate_does_not_elide_when_a_message_observer_is_reachable(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p observes e.getMessage() — an exception-message observer reachable from the entry. Must
        // NOT auto-elide.
        writeObserverClass(classes, "Entry", observe = true)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "a reachable getMessage() observer must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun gate_elides_when_no_message_observer_is_reachable(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p never reads any exception message. No observer reachable -> elision is sound.
        writeObserverClass(classes, "Entry", observe = false)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertTrue(d.elide, "no reachable observer -> elide: ${d.reason}")
        assertFalse(d.forced, "an AUTO elision is not forced")
    }

    @Test
    fun gate_does_not_elide_when_an_observer_is_reachable_through_a_callee(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p calls Helper.h, which reads getMessage(): the observer is reached transitively, through
        // a call edge — the walk must follow it and decline.
        writeObserverThroughCallee(classes)
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "a transitively-reachable observer must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun gate_does_not_elide_when_the_callgraph_cannot_be_bounded(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // Entry.p uses Class.forName — an opaque dispatch the walk can't follow. An observer could hide
        // behind it, so AUTO must NOT elide.
        writeReflectionClass(classes, "Entry")
        val d = ExceptionMessageElision.decide(classes.toString(), "Entry", "p", RemoveExceptionMessages.AUTO)
        assertFalse(d.elide, "an unbounded call-graph must suppress auto-elision: ${d.reason}")
    }

    @Test
    fun ON_forces_elision_and_OFF_disables_it() {
        val on = ExceptionMessageElision.decide("", "Entry", "p", RemoveExceptionMessages.ON)
        assertTrue(on.elide && on.forced, "ON forces elision (a user-asserted override)")
        val off = ExceptionMessageElision.decide("", "Entry", "p", RemoveExceptionMessages.OFF)
        assertFalse(off.elide, "OFF never elides")
    }

    @Test
    fun isThrowable_resolves_a_subtype_over_the_classpath(@TempDir dir: Path) {
        val classes = Files.createDirectory(dir.resolve("classes"))
        // my/Boom extends RuntimeException (a known JDK root) -> resolves Throwable via the super chain.
        writeSubclass(classes, "my/Boom", "java/lang/RuntimeException")
        // my/Plain extends Object -> not a Throwable.
        writeSubclass(classes, "my/Plain", "java/lang/Object")
        val index = ExceptionMessageElision.Index(classes.toString())
        assertTrue(index.isThrowable("my/Boom"), "a RuntimeException subtype is a Throwable")
        assertFalse(index.isThrowable("my/Plain"), "an Object subtype is not a Throwable")
        assertTrue(index.isThrowable("java/lang/IllegalArgumentException"), "a known JDK exception")
        assertFalse(index.isThrowable("java/lang/Object"), "Object is not a Throwable")
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** `isThrowable` for the dead-local tests: ThrowDL is the (only) Throwable; bld/Builder is NOT. */
    private val buildersAreNotThrowable: (String) -> Boolean = { n -> n == "ThrowDL" || jdkThrowable(n) }

    /**
     * The okio-shape dead-local class:
     * ```
     * static void f(int n) {
     *   Builder b = new bld/Builder().fill(n).fillMore(n);   // local 1, a fresh-object builder chain
     *   throw new ThrowDL(b.render());                       // sole reader of local 1, message-eliding ctor
     * }
     * ```
     * When [liveReadOfLocal], an extra `ALOAD 1` keeps the local live on another path (so the slice must
     * NOT fire). bld/Builder.<init>/fill/fillMore/render are declared but never linked (the rewrite is a
     * pure bytecode transform; we only inspect the emitted instructions, never run this class).
     */
    private fun deadLocalBuilderClass(liveReadOfLocal: Boolean = false): ByteArray {
        val cw = ClassWriter(0) // mirror the rewrite's ClassWriter(0): no frame/maxs recomputation
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowDL", null, "java/lang/RuntimeException", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "(I)V", null, null)
        mv.visitCode()
        // local 1 = new Builder().fill(n).fillMore(n)
        mv.visitTypeInsn(Opcodes.NEW, "bld/Builder")
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "bld/Builder", "<init>", "()V", false)
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "bld/Builder", "fill", "(I)Lbld/Builder;", false)
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "bld/Builder", "fillMore", "(I)Lbld/Builder;", false)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        if (liveReadOfLocal) {
            // A live read of local 1 (popped) — not inside any elided region, so the slot is NOT dead.
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitInsn(Opcodes.POP)
        }
        // throw new ThrowDL(b.render())
        mv.visitTypeInsn(Opcodes.NEW, "ThrowDL")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "bld/Builder", "render", "()Ljava/lang/String;", false)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "ThrowDL", "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(4, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * The FAITHFUL okio `Buffer.readDecimalLong` overflow shape — a fresh object in a local with a
     * discarded side-effect-only self-call OUTSIDE the message AND a read inside the message:
     * ```
     * static void f(int n) {
     *   okio/Buffer b = new Buffer().writeDecimalLong(n).writeByte(n);   // local 1, fresh-object chain
     *   b.readByte();                                                    // DISCARDED self-call (POP'd)
     *   throw new ThrowDL(b.readUtf8());                                 // sole in-message read of local 1
     * }
     * ```
     *  - [selfCallResultConsumed]: the `b.readByte()` result is STORED (consumed) rather than POP'd — a
     *    live use, so the object stays live and must NOT be sliced.
     *  - [escapesAsArgument]: `b` is also passed as an ARGUMENT to a sink call — an escape, must NOT slice.
     */
    private fun okioShapeClass(selfCallResultConsumed: Boolean = false,
                               escapesAsArgument: Boolean = false): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowDL", null, "java/lang/RuntimeException", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "(I)V", null, null)
        mv.visitCode()
        // local 1 = new Buffer().writeDecimalLong(n).writeByte(n)
        mv.visitTypeInsn(Opcodes.NEW, "okio/Buffer")
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "okio/Buffer", "<init>", "()V", false)
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "okio/Buffer", "writeDecimalLong",
                "(I)Lokio/Buffer;", false)
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "okio/Buffer", "writeByte", "(I)Lokio/Buffer;", false)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        // The OUT-OF-MESSAGE self-call on b: discarded (readByte()B then POP) by default.
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "okio/Buffer", "readByte", "()B", false)
        when {
            selfCallResultConsumed -> mv.visitVarInsn(Opcodes.ISTORE, 2) // result CONSUMED, not discarded
            else -> mv.visitInsn(Opcodes.POP)                            // result DISCARDED
        }
        if (escapesAsArgument) {
            // b passed as an ARGUMENT to a sink (escape) — not as a self-call receiver.
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "Sink", "consume", "(Lokio/Buffer;)V", false)
        }
        // throw new ThrowDL(b.readUtf8())
        mv.visitTypeInsn(Opcodes.NEW, "ThrowDL")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "okio/Buffer", "readUtf8", "()Ljava/lang/String;", false)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "ThrowDL", "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(4, 3)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * A local built by a STATIC call (`rendered = Helper.expensive(n)`), read only by the elided message —
     * the case the slicer conservatively KEEPS (a static call's effects are opaque).
     */
    private fun staticCallLocalClass(): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ThrowSC", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "(I)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "Helper", "expensive", "(I)Ljava/lang/String;", false)
        mv.visitVarInsn(Opcodes.ASTORE, 1)
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(3, 2)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** static void f(int n) { throw new <exc>(<message build?>); } — the message is a
     *  StringBuilder().append("bad: ").append(n).toString() when [withMessageBuild], else a literal. */
    private fun throwingClass(className: String, exc: String, withMessageBuild: Boolean): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f", "(I)V", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, exc)
        mv.visitInsn(Opcodes.DUP)
        if (withMessageBuild) {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false)
            mv.visitLdcInsn("bad: ")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            mv.visitVarInsn(Opcodes.ILOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(I)Ljava/lang/StringBuilder;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                    "()Ljava/lang/String;", false)
        } else {
            mv.visitLdcInsn("bad")
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, exc, "<init>", "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ATHROW)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** A class whose method throws an exception and (optionally) also reads `e.getMessage()`. */
    private fun writeObserverClass(dir: Path, internalName: String, observe: Boolean) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        mv.visitCode()
        if (observe) {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getMessage",
                    "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.ARETURN)
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL)
            mv.visitInsn(Opcodes.ARETURN)
        }
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    /** Entry.p() -> Helper.h(Throwable) -> e.getMessage(): the observer is one call edge away. */
    private fun writeObserverThroughCallee(dir: Path) {
        val helper = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        helper.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Helper", null, "java/lang/Object", null)
        val h = helper.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "h",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        h.visitCode()
        h.visitVarInsn(Opcodes.ALOAD, 0)
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getMessage",
                "()Ljava/lang/String;", false)
        h.visitInsn(Opcodes.ARETURN)
        h.visitMaxs(0, 0)
        h.visitEnd()
        helper.visitEnd()
        writeClassFile(dir, "Helper", helper.toByteArray())

        val entry = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        entry.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Entry", null, "java/lang/Object", null)
        val p = entry.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", null, null)
        p.visitCode()
        p.visitVarInsn(Opcodes.ALOAD, 0)
        p.visitMethodInsn(Opcodes.INVOKESTATIC, "Helper", "h",
                "(Ljava/lang/Throwable;)Ljava/lang/String;", false)
        p.visitInsn(Opcodes.ARETURN)
        p.visitMaxs(0, 0)
        p.visitEnd()
        entry.visitEnd()
        writeClassFile(dir, "Entry", entry.toByteArray())
    }

    private fun writeReflectionClass(dir: Path, internalName: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "p", "()V", null, null)
        mv.visitCode()
        mv.visitLdcInsn("X")
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;", false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    private fun writeSubclass(dir: Path, internalName: String, superName: String) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName, null)
        cw.visitEnd()
        writeClassFile(dir, internalName, cw.toByteArray())
    }

    private fun writeClassFile(dir: Path, internalName: String, bytes: ByteArray) {
        val target = dir.resolve("$internalName.class")
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun opcodes(clazz: ByteArray): List<String> {
        val ops = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(op: Int) {
                        if (op == Opcodes.ACONST_NULL) ops.add("ACONST_NULL")
                    }

                    override fun visitLdcInsn(value: Any?) {
                        ops.add("LDC \"$value\"")
                    }
                }
            }
        }, 0)
        return ops
    }

    private fun methodCalls(clazz: ByteArray): List<String> {
        val calls = ArrayList<String>()
        ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     e: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        calls.add("$owner.$name$desc")
                    }
                }
            }
        }, 0)
        return calls
    }

    private fun define(name: String, bytes: ByteArray): Class<*> =
            object : ClassLoader(ExceptionMessageElisionTest::class.java.classLoader) {
                fun def(): Class<*> = defineClass(name, bytes, 0, bytes.size)
            }.def()
}
