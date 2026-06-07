package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ContractPurityAudit]. Each test compiles a tiny class with ASM into a temp
 * classpath dir, names a contracted target via a [ContractRewriter.Redirect], and asserts the
 * audit either certifies (no throw) or rejects with a [ContractPurityError] whose message names
 * the offending instruction/callee. The transitive walk is exercised by a helper that calls
 * another method on the classpath.
 */
internal class ContractPurityAuditTest {

    // ---- pure bodies certify -----------------------------------------------------------------

    @Test
    fun certifies_arithmetic_and_branching(@TempDir dir: Path) {
        // static int f(int n) { return (n > 0) ? n * 2 : -n; }
        emit(dir, "pkg/Pure") { cw ->
            method(cw, "f", "(I)I") { mv ->
                val neg = org.objectweb.asm.Label()
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitJumpInsn(Opcodes.IFLE, neg)
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IMUL)
                mv.visitInsn(Opcodes.IRETURN)
                mv.visitLabel(neg)
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.INEG)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        assertCertifies(dir, "pkg/Pure", "f", "(I)I")
    }

    @Test
    fun certifies_fresh_allocation_populate_and_return(@TempDir dir: Path) {
        // static int[] make(int a, int b) { int[] r = new int[2]; r[0] = a; r[1] = b; return r; }
        emit(dir, "pkg/Alloc") { cw ->
            method(cw, "make", "(II)[I") { mv ->
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT) // fresh array
                mv.visitInsn(Opcodes.DUP)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.IASTORE)                     // store into FRESH array -> pure
                mv.visitInsn(Opcodes.DUP)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                mv.visitInsn(Opcodes.IASTORE)
                mv.visitInsn(Opcodes.ARETURN)
            }
        }
        assertCertifies(dir, "pkg/Alloc", "make", "(II)[I")
    }

    @Test
    fun certifies_a_call_into_another_pure_method_on_the_classpath(@TempDir dir: Path) {
        // static int caller(int n) { return Helper.dbl(n); }   Helper.dbl(int) is pure.
        emit(dir, "pkg/Caller") { cw ->
            method(cw, "caller", "(I)I") { mv ->
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Helper", "dbl", "(I)I", false)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        emit(dir, "pkg/Helper") { cw ->
            method(cw, "dbl", "(I)I") { mv ->
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.ICONST_2)
                mv.visitInsn(Opcodes.IMUL)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        assertCertifies(dir, "pkg/Caller", "caller", "(I)I")
    }

    @Test
    fun certifies_recursion(@TempDir dir: Path) {
        // static int fac(int n) { return n <= 1 ? 1 : n * fac(n - 1); }  — a self-call is auditable.
        emit(dir, "pkg/Rec") { cw ->
            method(cw, "fac", "(I)I") { mv ->
                val rec = org.objectweb.asm.Label()
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitJumpInsn(Opcodes.IF_ICMPGT, rec)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
                mv.visitLabel(rec)
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitVarInsn(Opcodes.ILOAD, 0)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.ISUB)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Rec", "fac", "(I)I", false)
                mv.visitInsn(Opcodes.IMUL)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        assertCertifies(dir, "pkg/Rec", "fac", "(I)I")
    }

    // ---- each rejection class ----------------------------------------------------------------

    @Test
    fun rejects_putfield_on_a_preexisting_object(@TempDir dir: Path) {
        // static void poke(Holder h, int v) { h.x = v; }  — writes a field of a parameter object.
        emit(dir, "pkg/Holder") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC, "x", "I", null, null).visitEnd()
        }
        emit(dir, "pkg/Poke") { cw ->
            method(cw, "poke", "(Lpkg/Holder;I)V") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)   // the parameter (pre-existing)
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Holder", "x", "I")
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Poke", "poke", "(Lpkg/Holder;I)V")
        assertTrue(msg.contains("writes field pkg/Holder.x"), msg)
    }

    @Test
    fun rejects_array_store_into_a_parameter(@TempDir dir: Path) {
        // static void fill(int[] a, int v) { a[0] = v; }  — stores into a parameter array.
        emit(dir, "pkg/Fill") { cw ->
            method(cw, "fill", "([II)V") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)   // parameter array (pre-existing)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                mv.visitInsn(Opcodes.IASTORE)
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Fill", "fill", "([II)V")
        assertTrue(msg.contains("array it did not allocate"), msg)
    }

    @Test
    fun rejects_putstatic(@TempDir dir: Path) {
        // static void bump() { Counter.n = 1; }
        emit(dir, "pkg/Counter") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "n", "I", null, null).visitEnd()
            method(cw, "bump", "()V") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Counter", "n", "I")
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Counter", "bump", "()V")
        assertTrue(msg.contains("writes static pkg/Counter.n"), msg)
    }

    @Test
    fun rejects_read_of_a_mutable_static(@TempDir dir: Path) {
        // static int readList() { return State.items.size(); } — GETSTATIC of a mutable List field.
        emit(dir, "pkg/State") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "items",
                    "Ljava/util/List;", null, null).visitEnd()
            method(cw, "readList", "()I") { mv ->
                mv.visitFieldInsn(Opcodes.GETSTATIC, "pkg/State", "items", "Ljava/util/List;")
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/State", "readList", "()I")
        assertTrue(msg.contains("reads mutable static pkg/State.items"), msg)
    }

    @Test
    fun rejects_a_denylisted_call_time(@TempDir dir: Path) {
        // static long now() { return System.nanoTime(); }
        emit(dir, "pkg/Clock") { cw ->
            method(cw, "now", "()J") { mv ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
                mv.visitInsn(Opcodes.LRETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Clock", "now", "()J")
        assertTrue(msg.contains("java/lang/System.nanoTime"), msg)
    }

    @Test
    fun rejects_a_denylisted_call_random(@TempDir dir: Path) {
        // static int roll(java.util.Random r) { return r.nextInt(); } — call into Random.
        emit(dir, "pkg/Dice") { cw ->
            method(cw, "roll", "(Ljava/util/Random;)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextInt", "()I", false)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Dice", "roll", "(Ljava/util/Random;)I")
        assertTrue(msg.contains("java/util/Random"), msg)
    }

    @Test
    fun rejects_a_denylisted_call_io(@TempDir dir: Path) {
        // static void shout(java.io.PrintStream out) { out.flush(); } — I/O on a stream.
        emit(dir, "pkg/Shout") { cw ->
            method(cw, "shout", "(Ljava/io/PrintStream;)V") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "flush", "()V", false)
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Shout", "shout", "(Ljava/io/PrintStream;)V")
        assertTrue(msg.contains("java/io/PrintStream"), msg)
    }

    @Test
    fun rejects_monitorenter(@TempDir dir: Path) {
        // static int sync(Object lock) { synchronized(lock) { return 1; } } — uses monitorenter.
        emit(dir, "pkg/Sync") { cw ->
            method(cw, "sync", "(Ljava/lang/Object;)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.MONITORENTER)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.MONITOREXIT)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Sync", "sync", "(Ljava/lang/Object;)I")
        assertTrue(msg.contains("monitorenter"), msg)
    }

    @Test
    fun rejects_a_non_devirtualizable_call_with_no_body_on_the_classpath(@TempDir dir: Path) {
        // static int via(pkg/Iface i) { return i.compute(); } — Iface.compute has no impl on the cp.
        emit(dir, "pkg/Via") { cw ->
            method(cw, "via", "(Lpkg/Iface;)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "pkg/Iface", "compute", "()I", true)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Via", "via", "(Lpkg/Iface;)I")
        assertTrue(msg.contains("not on the analysis classpath"), msg)
        assertTrue(msg.contains("pkg.Iface.compute"), msg)
    }

    @Test
    fun rejects_a_native_method(@TempDir dir: Path) {
        // A method declared native has no auditable body -> reject as unresolvable.
        emit(dir, "pkg/Nat") { cw ->
            cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_NATIVE,
                    "raw", "()I", null, null).visitEnd()
        }
        val msg = assertRejects(dir, "pkg/Nat", "raw", "()I")
        assertTrue(msg.contains("not on the analysis classpath"), msg)
    }

    @Test
    fun rejects_an_impurity_reached_transitively(@TempDir dir: Path) {
        // pure-looking root that calls an impure helper: the message names the reached callee.
        emit(dir, "pkg/Outer") { cw ->
            method(cw, "f", "()V") { mv ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Inner", "g", "()V", false)
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        emit(dir, "pkg/Inner") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "n", "I", null, null).visitEnd()
            method(cw, "g", "()V") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Inner", "n", "I")
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Outer", "f", "()V")
        assertTrue(msg.contains("reaches pkg.Inner.g"), msg)
        assertTrue(msg.contains("writes static pkg/Inner.n"), msg)
    }

    @Test
    fun message_names_the_contract_target_and_remedies(@TempDir dir: Path) {
        emit(dir, "pkg/Counter2") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "n", "I", null, null).visitEnd()
            method(cw, "bump", "()V") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Counter2", "n", "I")
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val msg = assertRejects(dir, "pkg/Counter2", "bump", "()V")
        assertTrue(msg.contains("Contract on pkg.Counter2.bump"), "names the target: $msg")
        assertTrue(msg.contains("not provably PURE"), msg)
        assertTrue(msg.contains("remove the @Requires/@Ensures contract"), "lists remedies: $msg")
    }

    // ---- pure instance contracts -------------------------------------------------------------

    @Test
    fun certifies_a_pure_instance_method_reading_this(@TempDir dir: Path) {
        // int scaled(int by) { return this.value * by; }  — reads `this` but never mutates it.
        // The receiver is conservatively pre-existing (ALOAD 0 is non-fresh), but a READ of its
        // field is pure: only a WRITE to pre-existing state disqualifies. Must certify.
        emit(dir, "pkg/Scale") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd()
            instanceMethod(cw, "pkg/Scale", "scaled", "(I)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)   // this (pre-existing, but only read)
                mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Scale", "value", "I")
                mv.visitVarInsn(Opcodes.ILOAD, 1)   // by
                mv.visitInsn(Opcodes.IMUL)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        // An instance redirect: the descriptor is the instance descriptor (no receiver); the audit
        // locates the instance method body by it exactly like a static one.
        ContractPurityAudit.audit(listOf(instanceRedirect("pkg/Scale", "scaled", "(I)I")), dir.toString())
    }

    @Test
    fun rejects_receiver_mutation_in_an_instance_contract(@TempDir dir: Path) {
        // void grow(int by) { this.value += by; }  — mutates `this`, the most common impurity for an
        // instance method. The receiver is pre-existing (ALOAD 0 is non-fresh), so the PUTFIELD on it
        // is a heap write to pre-existing state — the audit must REJECT. This is the pinning test the
        // purity-audit PR flagged: an instance contract must not widen the silent-effect-dropping hole.
        emit(dir, "pkg/Mut") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd()
            instanceMethod(cw, "pkg/Mut", "grow", "(I)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)   // this (pre-existing)
                mv.visitInsn(Opcodes.DUP)
                mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Mut", "value", "I")
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                mv.visitInsn(Opcodes.IADD)
                mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Mut", "value", "I") // writes this.value
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Mut", "value", "I")
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        val ex = assertThrows(ContractPurityError::class.java) {
            ContractPurityAudit.audit(listOf(instanceRedirect("pkg/Mut", "grow", "(I)I")), dir.toString())
        }
        assertTrue(ex.message!!.contains("writes field pkg/Mut.value"),
                "receiver mutation must be rejected naming the field write: ${ex.message}")
    }

    @Test
    fun instance_contract_is_relevant_to_a_proof_that_makes_a_virtual_call(@TempDir dir: Path) {
        // A proof reaching the impure instance target via an invokevirtual must be scoped-in by
        // auditRelevant (reachability now records virtual call sites, not just invokestatic ones).
        emit(dir, "pkg/Mut2") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd()
            instanceMethod(cw, "pkg/Mut2", "grow", "(I)I") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.DUP)
                mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Mut2", "value", "I")
                mv.visitVarInsn(Opcodes.ILOAD, 1)
                mv.visitInsn(Opcodes.IADD)
                mv.visitFieldInsn(Opcodes.PUTFIELD, "pkg/Mut2", "value", "I")
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETFIELD, "pkg/Mut2", "value", "I")
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        emit(dir, "proofs/VReacher") { cw ->
            method(cw, "p", "(Lpkg/Mut2;)V") { mv ->
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "pkg/Mut2", "grow", "(I)I", false)
                mv.visitInsn(Opcodes.POP)
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        val manifest = ContractManifest.parse(listOf(
                ContractManifest.contractLine("pkg/Mut2", "grow", "(I)I", "pkg/Mut2\$\$Stubs",
                        "grow__stub", true, "(Lpkg/Mut2;I)I")))
        val ex = assertThrows(ContractPurityError::class.java) {
            ContractPurityAudit.auditRelevant(manifest, "proofs.VReacher", "p",
                    dir.toString(), dir.toString())
        }
        assertTrue(ex.message!!.contains("writes field pkg/Mut2.value"), ex.message)
    }

    @Test
    fun no_redirects_is_a_no_op() {
        // Should not throw even with a bogus classpath — nothing to audit.
        ContractPurityAudit.audit(listOf(), "does/not/exist")
    }

    // ---- per-proof scoping (auditRelevant) ---------------------------------------------------

    @Test
    fun relevant_audit_rejects_only_a_proof_that_reaches_the_impure_contract(@TempDir dir: Path) {
        // An impure contracted target Bad.bump(), a proof that CALLS it (Reacher.p), and a proof that
        // does NOT (Bystander.p). With the redirect published, auditRelevant must reject Reacher (its
        // call site would be summarized, dropping the static write) and leave Bystander green.
        emit(dir, "pkg/Bad") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "n", "I", null, null).visitEnd()
            method(cw, "bump", "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Bad", "n", "I")
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        emit(dir, "proofs/Reacher") { cw ->
            method(cw, "p", "()V") { mv ->
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "pkg/Bad", "bump", "()I", false)
                mv.visitInsn(Opcodes.POP)
                mv.visitInsn(Opcodes.RETURN)
            }
        }
        emit(dir, "proofs/Bystander") { cw ->
            method(cw, "p", "()V") { mv -> mv.visitInsn(Opcodes.RETURN) }
        }
        val manifest = ContractManifest.parse(listOf(
                ContractManifest.contractLine("pkg/Bad", "bump", "()I", "pkg/Bad\$\$Stubs", "bump__stub")))

        // The proof whose ENTRY (Reacher.p) reaches Bad.bump is rejected, naming the offending write.
        val ex = assertThrows(ContractPurityError::class.java) {
            ContractPurityAudit.auditRelevant(manifest, "proofs.Reacher", "p",
                    dir.toString(), dir.toString())
        }
        assertTrue(ex.message!!.contains("writes static pkg/Bad.n"), ex.message)

        // The bystander proof (entry Bystander.p) does NOT reach Bad.bump -> no relevant redirect ->
        // green, even though the impure contract is published on the same classpath. Scoping works.
        ContractPurityAudit.auditRelevant(manifest, "proofs.Bystander", "p",
                dir.toString(), dir.toString())
    }

    @Test
    fun relevant_audit_is_a_noop_when_no_proof_reaches_the_contract(@TempDir dir: Path) {
        // Same impure target, but the only class on the proof classpath never calls it -> no relevant
        // redirect -> no audit -> green. Proves the scoping doesn't poison unrelated proofs.
        emit(dir, "pkg/Bad") { cw ->
            cw.visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "n", "I", null, null).visitEnd()
            method(cw, "bump", "()I") { mv ->
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitFieldInsn(Opcodes.PUTSTATIC, "pkg/Bad", "n", "I")
                mv.visitInsn(Opcodes.ICONST_1)
                mv.visitInsn(Opcodes.IRETURN)
            }
        }
        // A proof classpath that does NOT call Bad.bump.
        val proofDir = Files.createDirectories(dir.resolve("proofcp"))
        emitInto(proofDir, "proofs/Lonely") { cw ->
            method(cw, "p", "()V") { mv -> mv.visitInsn(Opcodes.RETURN) }
        }
        val manifest = ContractManifest.parse(listOf(
                ContractManifest.contractLine("pkg/Bad", "bump", "()I", "pkg/Bad\$\$Stubs", "bump__stub")))
        // entry Lonely.p reaches nothing; proofClasspath = proofDir, analysisClasspath = dir. No reject.
        ContractPurityAudit.auditRelevant(manifest, "proofs.Lonely", "p",
                proofDir.toString(), dir.toString())
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun assertCertifies(dir: Path, owner: String, name: String, desc: String) {
        // No exception == certified pure.
        ContractPurityAudit.audit(listOf(redirect(owner, name, desc)), dir.toString())
    }

    private fun assertRejects(dir: Path, owner: String, name: String, desc: String): String {
        val ex = assertThrows(ContractPurityError::class.java) {
            ContractPurityAudit.audit(listOf(redirect(owner, name, desc)), dir.toString())
        }
        assertNotNull(ex.message)
        return ex.message!!
    }

    private fun redirect(owner: String, name: String, desc: String): ContractRewriter.Redirect =
            ContractRewriter.Redirect(owner, name, desc, "$owner\$\$Stubs", "${name}__stub")

    /** An instance redirect: the call-site descriptor is the instance descriptor (no receiver); the
     *  stub descriptor prepends the receiver type. */
    private fun instanceRedirect(owner: String, name: String, desc: String): ContractRewriter.Redirect =
            ContractRewriter.Redirect(owner, name, desc, "$owner\$\$Stubs", "${name}__stub",
                    true, "(L$owner;" + desc.removePrefix("("))

    /** Emit a `public` (non-static) instance method [name][desc] whose body is built by [code]. */
    private fun instanceMethod(cw: ClassWriter, internalName: String, name: String, desc: String,
                               code: (MethodVisitor) -> Unit) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null)
        mv.visitCode()
        code(mv)
        mv.visitMaxs(0, 0) // COMPUTE_MAXS
        mv.visitEnd()
    }

    /** Emit a public class [internalName] into [dir] via [body], with a default no-arg ctor omitted
     *  (these fixtures only declare statics / fields). */
    private fun emit(dir: Path, internalName: String, body: (ClassWriter) -> Unit) =
            emitInto(dir, internalName, body)

    /** Emit [internalName] under root [dir] (identical to [emit]; named for call-site clarity in
     *  the scoping tests where the proof classpath and analysis classpath are distinct roots). */
    private fun emitInto(dir: Path, internalName: String, body: (ClassWriter) -> Unit) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        val access = if (internalName == "pkg/Iface")
            Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT
        else
            Opcodes.ACC_PUBLIC
        cw.visit(Opcodes.V17, access, internalName, null, "java/lang/Object", null)
        if (internalName == "pkg/Iface") {
            cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT, "compute", "()I", null, null)
                    .visitEnd()
        }
        body(cw)
        cw.visitEnd()
        val out = dir.resolve("$internalName.class")
        Files.createDirectories(out.parent)
        Files.write(out, cw.toByteArray())
    }

    /** Emit a `public static` method [name][desc] whose body is built by [code]. */
    private fun method(cw: ClassWriter, name: String, desc: String, code: (MethodVisitor) -> Unit) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, desc, null, null)
        mv.visitCode()
        code(mv)
        mv.visitMaxs(0, 0) // COMPUTE_MAXS
        mv.visitEnd()
    }
}
