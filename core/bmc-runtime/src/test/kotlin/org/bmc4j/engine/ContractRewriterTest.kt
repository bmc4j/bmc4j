package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ContractRewriter]'s bytecode transform. The end-to-end "JBMC
 * honors the rewrite" property is proven by the spike (spike/contracts MechanismSpikeTest);
 * here we pin the transform itself, which needs no engine.
 */
internal class ContractRewriterTest {

    @Test
    fun redirects_matching_invokestatic_call_sites_to_the_stub() {
        val out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                listOf(TRIANGLE))
        val calls = staticCalls(out)
        assertTrue(calls.contains("pkg/Stubs.triangle(I)I"), "call should be redirected to the stub")
        assertTrue(calls.none { it.startsWith("pkg/C.triangle") },
                "original call target must be gone")
    }

    @Test
    fun leaves_non_matching_calls_untouched() {
        // Different descriptor -> no match.
        val out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(J)I"),
                listOf(TRIANGLE))
        assertTrue(staticCalls(out).contains("pkg/C.triangle(J)I"))
    }

    @Test
    fun null_descriptor_matches_any_overload() {
        val anyDesc = ContractRewriter.Redirect("pkg/C", "triangle", null, "pkg/Stubs", "triangle")
        val out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(J)I"),
                listOf(anyDesc))
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(J)I"))
    }

    @Test
    fun excluded_caller_class_is_left_untouched() {
        // The caller class is "pkg/Caller" (see callerCalling). Excluding it means its
        // call sites pass through unchanged — the modular-enforce case where the proof's
        // own direct call to the method-under-test must stay real.
        val out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                listOf(TRIANGLE), "pkg/Caller")
        assertTrue(staticCalls(out).contains("pkg/C.triangle(I)I"), "excluded caller must keep the real call")
        assertTrue(staticCalls(out).none { it.startsWith("pkg/Stubs") }, "no redirect in excluded class")
    }

    @Test
    fun a_different_excluded_class_still_rewrites_this_one() {
        // Excluding some OTHER class does not protect pkg/Caller.
        val out = ContractRewriter.rewriteClass(callerCalling("pkg/C", "triangle", "(I)I"),
                listOf(TRIANGLE), "pkg/SomethingElse")
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(I)I"))
    }

    @Test
    fun instance_redirect_rewrites_a_virtual_call_to_the_static_stub_with_receiver_prepended() {
        // The call site is invokevirtual acct/Account.project(I)I with [receiver, arg] on the stack;
        // the stub is static project__stub(LAccount;I)I — the operand stack already matches.
        val redirect = ContractRewriter.Redirect("acct/Account", "project", "(I)I",
                "acct/AccountStubs", "project__stub", true, "(Lacct/Account;I)I")
        val out = ContractRewriter.rewriteClass(callerCallingVirtual("acct/Account", "project", "(I)I"),
                listOf(redirect))
        val calls = staticCalls(out)
        assertTrue(calls.contains("acct/AccountStubs.project__stub(Lacct/Account;I)I"),
                "virtual call must be redirected to the static stub with the receiver-prepended descriptor: $calls")
        assertTrue(calls.none { it.startsWith("acct/Account.project") })
    }

    @Test
    fun instance_redirect_leaves_a_same_name_static_call_untouched() {
        // An instance redirect must NOT catch a same-name invokestatic (its descriptor is the
        // un-prepended one, and the kind differs) — only the virtual/interface site is its call site.
        val redirect = ContractRewriter.Redirect("acct/Account", "project", "(I)I",
                "acct/AccountStubs", "project__stub", true, "(Lacct/Account;I)I")
        val out = ContractRewriter.rewriteClass(callerCalling("acct/Account", "project", "(I)I"),
                listOf(redirect))
        assertTrue(staticCalls(out).contains("acct/Account.project(I)I"),
                "an instance redirect must not rewrite an invokestatic call site")
    }

    @Test
    fun a_static_redirect_leaves_a_virtual_call_untouched() {
        // The TRIANGLE redirect is static; a virtual call site of the same owner/name/desc is not its
        // call site and must pass through.
        val out = ContractRewriter.rewriteClass(callerCallingVirtual("pkg/C", "triangle", "(I)I"),
                listOf(TRIANGLE))
        assertTrue(staticCalls(out).none { it.startsWith("pkg/Stubs") },
                "a static redirect must not rewrite a virtual call site")
    }

    @Test
    fun empty_redirects_returns_classpath_unchanged() {
        val cp = "a.jar" + File.pathSeparator + "b.jar"
        assertEquals(cp, ContractRewriter.rewrite(cp, listOf()))
    }

    @Test
    fun mirrors_directories_and_rewrites_class_files(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("pkg"))
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"))
        val mirrored = ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE))
        assertNotEquals(dir.toString(), mirrored)
        val out = Files.readAllBytes(Path.of(mirrored, "pkg/Caller.class"))
        assertTrue(staticCalls(out).contains("pkg/Stubs.triangle(I)I"))
    }

    @Test
    fun a_rewrite_failure_fails_LOUD_never_falls_back_to_the_original_dir(@TempDir dir: Path) {
        // A malformed .class makes ASM's ClassReader throw mid-transform. The contract rewrite must
        // surface that as the fail-loud MirrorException (-> UNKNOWN), NOT swallow it and return the
        // original, un-redirected dir (which would analyse the real call sites as the contract proof).
        Files.createDirectories(dir.resolve("pkg"))
        Files.write(dir.resolve("pkg/Caller.class"), byteArrayOf(0, 1, 2, 3, 4)) // not a valid class
        val ex = assertThrows(RuntimeException::class.java,
                { ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE)) },
                "a rewrite failure must throw (fail loud), never silently fall back to the original dir")
        assertTrue(ex.message != null && ex.message!!.contains(dir.toString()),
                "the failure message must name the entry that couldn't be mirrored: " + ex.message)
    }

    @Test
    fun distinct_configs_over_the_same_dir_get_distinct_complete_mirrors(@TempDir dir: Path) {
        // The contract mirror's identity folds in (redirects, excludeCaller) as extra key material,
        // so two distinct configurations over the SAME source dir must resolve to DISTINCT mirrors —
        // no 32-bit-hash collision mixing one config's classes into the other — and BOTH must be
        // complete (a .done marker present beside each mirror dir).
        Files.createDirectories(dir.resolve("pkg"))
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"))

        // Config A: replace direction (exclude nothing) -> the call site is redirected to the stub.
        val mirrorA = ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE))
        // Config B: enforce direction with pkg/Caller excluded -> its call site stays real.
        val mirrorB = ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE), "pkg/Caller")

        assertNotEquals(mirrorA, mirrorB,
                "distinct (redirects, excludeCaller) configs must map to distinct mirror dirs (no collision)")
        // Both directions stay correct.
        assertTrue(staticCalls(Files.readAllBytes(Path.of(mirrorA, "pkg/Caller.class")))
                .contains("pkg/Stubs.triangle(I)I"),
                "replace config A must redirect the call site to the stub")
        assertTrue(staticCalls(Files.readAllBytes(Path.of(mirrorB, "pkg/Caller.class")))
                .contains("pkg/C.triangle(I)I"),
                "enforce config B (excluded caller) must keep the real call site")
        // Both mirrors are complete: a .done marker sits beside each mirror dir.
        assertTrue(Files.isRegularFile(doneMarkerOf(mirrorA)), "config A mirror must be .done-marked complete")
        assertTrue(Files.isRegularFile(doneMarkerOf(mirrorB)), "config B mirror must be .done-marked complete")
        assertNotEquals(doneMarkerOf(mirrorA), doneMarkerOf(mirrorB), "distinct configs -> distinct markers")
    }

    @Test
    fun a_cache_hit_reuses_a_done_marked_mirror(@TempDir dir: Path) {
        // A second rewrite over the same (dir, config) is a cache hit: it returns the same .done-marked
        // mirror dir rather than building a new one.
        Files.createDirectories(dir.resolve("pkg"))
        Files.write(dir.resolve("pkg/Caller.class"), callerCalling("pkg/C", "triangle", "(I)I"))

        val first = ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE))
        assertTrue(Files.isRegularFile(doneMarkerOf(first)), "first mirror must be .done-marked complete")
        val second = ContractRewriter.rewrite(dir.toString(), listOf(TRIANGLE))
        assertEquals(first, second, "same (dir, config) must reuse the same .done-marked mirror (cache hit)")
        assertFalse(first == dir.toString(), "the mirror must be a cache dir, not the original source dir")
    }

    companion object {
        private val TRIANGLE =
                ContractRewriter.Redirect("pkg/C", "triangle", "(I)I", "pkg/Stubs", "triangle")

        // --- helpers ---

        /** The `<hash>.done` completion marker beside a mirror dir (sibling in the same cache root). */
        private fun doneMarkerOf(mirrorDir: String): Path {
            val p = Path.of(mirrorDir)
            return p.resolveSibling(p.fileName.toString() + ".done")
        }

        /** A class pkg/Caller with a method that does: invokestatic owner.name desc; return. */
        private fun callerCalling(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Caller", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use", "()V", null, null)
            mv.visitCode()
            // push a default arg of the right type, then call.
            if (desc.startsWith("(J)")) {
                mv.visitInsn(Opcodes.LCONST_0)
            } else {
                mv.visitInsn(Opcodes.ICONST_0)
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(2, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class pkg/Caller with a method that does: aconst_null (receiver); push arg;
         *  invokevirtual owner.name desc; return. Models a pure-instance call site. */
        private fun callerCallingVirtual(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(0)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "pkg/Caller", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use", "()V", null, null)
            mv.visitCode()
            mv.visitInsn(Opcodes.ACONST_NULL)          // receiver
            if (desc.startsWith("(J)")) {
                mv.visitInsn(Opcodes.LCONST_0)
            } else {
                mv.visitInsn(Opcodes.ICONST_0)
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false)
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(3, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun staticCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            if (op == Opcodes.INVOKESTATIC) {
                                calls.add("$owner.$name$desc")
                            }
                        }
                    }
                }
            }, 0)
            return calls
        }
    }
}
