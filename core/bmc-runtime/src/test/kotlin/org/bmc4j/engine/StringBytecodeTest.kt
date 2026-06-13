package org.bmc4j.engine

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.ArrayList

/** Unit tests for [StringBytecode]'s transform. The sound-semantics end-to-end is covered
 *  by the strings example; here we pin the call-site redirect, which needs no engine. */
internal class StringBytecodeTest {

    @Test
    fun redirects_content_ops_to_BmcStrings_with_receiver_prepended() {
        assertRedirected("equals", "(Ljava/lang/Object;)Z", "(Ljava/lang/String;Ljava/lang/Object;)Z")
        assertRedirected("startsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;Ljava/lang/String;)Z")
        assertRedirected("endsWith", "(Ljava/lang/String;)Z", "(Ljava/lang/String;Ljava/lang/String;)Z")
        assertRedirected("contains", "(Ljava/lang/CharSequence;)Z", "(Ljava/lang/String;Ljava/lang/CharSequence;)Z")
    }

    @Test
    fun leaves_other_String_calls_untouched() {
        val calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I")))
        assertTrue(calls.any { it.contains("java/lang/String.length()I") })
        assertFalse(calls.any { it.contains("BmcStrings") })
    }

    /**
     * equalsIgnoreCase / compareTo / isEmpty / indexOf / lastIndexOf / substring were
     * probed and found NATIVE-SOUND under JBMC (verified by the conformance pins in
     * `proofs.strings.StringLaws`), so they are deliberately NOT redirected to BmcStrings —
     * unlike equals/startsWith/endsWith/contains. This pins that decision: if a future change adds a
     * redirect for one of them, this test fails and forces a deliberate re-evaluation (a needless
     * shim over a native-sound op is wasted unwinding). The arg descriptors below are the real JDK
     * ones for the no-arg / single-arg forms exercised by the conformance proofs.
     */
    @Test
    fun native_sound_String_ops_are_left_unredirected() {
        assertNotRedirected("equalsIgnoreCase", "(Ljava/lang/String;)Z")
        assertNotRedirected("compareTo", "(Ljava/lang/String;)I")
        assertNotRedirected("isEmpty", "()Z")
        assertNotRedirected("indexOf", "(Ljava/lang/String;)I")
        assertNotRedirected("indexOf", "(I)I")
        assertNotRedirected("lastIndexOf", "(I)I")
        assertNotRedirected("substring", "(I)Ljava/lang/String;")
        assertNotRedirected("substring", "(II)Ljava/lang/String;")
    }

    // ---- String-from-chars construction redirects to BmcStrings.ofChar(s) ----

    @Test
    fun string_valueOf_char_data_redirects_to_BmcStrings_factories() {
        // String.valueOf(char)/(char[])/(char[],int,int) and copyValueOf materialize a String from char
        // data; JBMC links them to a nondet string (CProverString.ofCharArray). They must retarget to the
        // sound BmcStrings factories (SAME descriptor) — ofChar for the single char, ofChars for arrays.
        assertStaticRedirected("java/lang/String", "valueOf", "(C)Ljava/lang/String;",
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChar(C)Ljava/lang/String;")
        assertStaticRedirected("java/lang/String", "valueOf", "([C)Ljava/lang/String;",
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([C)Ljava/lang/String;")
        assertStaticRedirected("java/lang/String", "valueOf", "([CII)Ljava/lang/String;",
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([CII)Ljava/lang/String;")
        assertStaticRedirected("java/lang/String", "copyValueOf", "([C)Ljava/lang/String;",
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([C)Ljava/lang/String;")
        assertStaticRedirected("java/lang/Character", "toString", "(C)Ljava/lang/String;",
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChar(C)Ljava/lang/String;")
    }

    @Test
    fun new_String_from_char_array_redirects_and_runs_correctly() {
        // static String f(char[] a) { return new String(a); }  — the NEW;DUP;aload;INVOKESPECIAL
        // String.<init>([C)V shape. The redirect must DROP the NEW;DUP, emit BmcStrings.ofChars, leave no
        // dangling uninitialized String (verifiable bytecode), and compute the right content on a real JVM.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorC", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([C)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        val calls = methodCalls(rewritten)
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([C)Ljava/lang/String;"),
                "the constructor must be redirected to BmcStrings.ofChars: $calls")
        assertFalse(calls.any { it.contains("java/lang/String.<init>") },
                "the original String.<init>([C)V must be gone: $calls")
        // Bytecode must load (no dangling uninitialized NEW) and compute correctly on a real JVM.
        val c = define("CtorC", rewritten)
        val f = c.getMethod("f", CharArray::class.java)
        assertEquals("hi", f.invoke(null, charArrayOf('h', 'i')))
        assertEquals("", f.invoke(null, charArrayOf()))
    }

    @Test
    fun new_String_from_char_array_range_redirects_and_runs_correctly() {
        // static String f(char[] a) { return new String(a, 1, 2); }  — the 3-arg ctor variant.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorR", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([C)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitInsn(Opcodes.ICONST_2)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([CII)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        val calls = methodCalls(rewritten)
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([CII)Ljava/lang/String;"),
                "the 3-arg constructor must be redirected: $calls")
        val c = define("CtorR", rewritten)
        val f = c.getMethod("f", CharArray::class.java)
        assertEquals("el", f.invoke(null, charArrayOf('h', 'e', 'l', 'l', 'o')))
    }

    @Test
    fun unrelated_new_String_constructor_is_left_alone_and_runs() {
        // new String(String) is NOT a from-chars construction — its NEW;DUP must be preserved verbatim,
        // the ctor untouched, and the class must still load and run (the deferred-replay fallback path).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CopyC", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "(Ljava/lang/String;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        val calls = methodCalls(rewritten)
        assertTrue(calls.any { it.contains("java/lang/String.<init>(Ljava/lang/String;)V") },
                "new String(String) must be left as a native constructor: $calls")
        assertFalse(calls.any { it.contains("BmcStrings.ofChars") },
                "new String(String) must NOT be redirected to ofChars: $calls")
        val c = define("CopyC", rewritten)
        val f = c.getMethod("f", String::class.java)
        assertEquals("hi", f.invoke(null, "hi"))
    }

    @Test
    fun new_String_from_char_array_with_intervening_label_still_redirects() {
        // The construction region carries a LABEL + line number between the arg load and the ctor — the
        // shape kotlinc/javac emit for a multi-line `new String(chars)` (LineNumberTable anchors mid
        // expression). The redirect must NOT abandon on that intervening label (it has no stack effect);
        // it must still drop NEW;DUP, emit BmcStrings.ofChars, and run. (Regression for the visitLabel /
        // visitLineNumber recording fix: a flush-on-label would leave the native ctor and lose the redirect.)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorLbl", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([C)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        val mid = org.objectweb.asm.Label()
        mv.visitLabel(mid)                 // line-number anchor inside the construction region
        mv.visitLineNumber(42, mid)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        val calls = methodCalls(rewritten)
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.ofChars([C)Ljava/lang/String;"),
                "an intervening label must not defeat the from-chars redirect: $calls")
        assertFalse(calls.any { it.contains("java/lang/String.<init>") },
                "the original String.<init>([C)V must be gone even with an intervening label: $calls")
        val c = define("CtorLbl", rewritten)
        val f = c.getMethod("f", CharArray::class.java)
        assertEquals("hi", f.invoke(null, charArrayOf('h', 'i')))
    }

    // ---- byte[] -> String charset-decode constructor redirect ----

    @Test
    fun new_String_from_bytes_with_charset_getstatic_redirects_to_monomorphic_decoder() {
        // static String f(byte[] a) { return new String(a, StandardCharsets.UTF_8); } and the
        // ISO_8859_1 variant. The recognized Charset getstatic that feeds the ctor must be DROPPED and
        // the call retargeted to the monomorphic BmcStrings.ofBytesUtf8 / ofBytesLatin1 (no Charset
        // operand) — JBMC can't reason about a Charset object at runtime. Must run correctly on a real JVM.
        for ((field, factory) in listOf(
                "UTF_8" to "ofBytesUtf8", "ISO_8859_1" to "ofBytesLatin1", "US_ASCII" to "ofBytesLatin1")) {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorB$field", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                    "([B)Ljava/lang/String;", null, null)
            mv.visitCode()
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
            mv.visitInsn(Opcodes.DUP)
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/nio/charset/StandardCharsets", field,
                    "Ljava/nio/charset/Charset;")
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                    "([BLjava/nio/charset/Charset;)V", false)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()

            val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
            val calls = methodCalls(rewritten)
            assertTrue(calls.contains(
                    "INVOKESTATIC org/bmc4j/engine/BmcStrings.$factory([B)Ljava/lang/String;"),
                    "$field must redirect to BmcStrings.$factory (charset getstatic dropped): $calls")
            assertFalse(calls.any { it.contains("java/lang/String.<init>") },
                    "the native byte[] ctor must be gone for $field: $calls")
            assertFalse(calls.any { it.contains("StandardCharsets") },
                    "the recognized charset getstatic must be dropped for $field: $calls")
            val c = define("CtorB$field", rewritten)
            val res = c.getMethod("f", ByteArray::class.java).invoke(null, byteArrayOf('h'.code.toByte(), 'i'.code.toByte()))
            assertEquals("hi", res, "$field decode of ASCII bytes")
        }
    }

    @Test
    fun new_String_from_bytes_default_charset_redirects_to_ofBytes() {
        // static String f(byte[] a) { return new String(a); } — no Charset operand. Redirect to the
        // descriptor-matched BmcStrings.ofBytes([B) (default-charset UTF-8 decode).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorBDef", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([B)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([B)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        val calls = methodCalls(rewritten)
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.ofBytes([B)Ljava/lang/String;"),
                "new String(byte[]) must redirect to BmcStrings.ofBytes([B): $calls")
        val c = define("CtorBDef", rewritten)
        val res = c.getMethod("f", ByteArray::class.java).invoke(null, byteArrayOf('o'.code.toByte(), 'k'.code.toByte()))
        assertEquals("ok", res)
    }

    @Test
    fun new_String_from_bytes_with_kotlin_charsets_getstatic_redirects() {
        // A Kotlin charset-decode site loads kotlin/text/Charsets.UTF_8 (not StandardCharsets). That getstatic must be
        // recognized and dropped too, routing to the monomorphic UTF-8 decoder. (Bytecode-shape only —
        // we don't load it, since kotlin/text/Charsets need not be on the test runtime classpath.)
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorBKt", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([B)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitFieldInsn(Opcodes.GETSTATIC, "kotlin/text/Charsets", "UTF_8", "Ljava/nio/charset/Charset;")
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val calls = methodCalls(StringBytecode.rewriteClass(cw.toByteArray()))
        assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.ofBytesUtf8([B)Ljava/lang/String;"),
                "kotlin/text/Charsets.UTF_8 must redirect to BmcStrings.ofBytesUtf8: $calls")
        assertFalse(calls.any { it.contains("kotlin/text/Charsets") },
                "the kotlin Charsets getstatic must be dropped: $calls")
    }

    @Test
    fun new_String_from_bytes_with_unrecognized_charset_keeps_generic_ofBytes() {
        // A Charset from a VARIABLE (not a recognized getstatic singleton) can't be resolved at rewrite
        // time, so the redirect must keep the generic BmcStrings.ofBytes(...,Charset) — which falls
        // through to a nondet (conservatively UNKNOWN) result, never a false VERIFY. The Charset operand
        // stays on the stack (the factory takes it).
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "CtorBVar", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "([BLjava/nio/charset/Charset;)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/String")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ALOAD, 1)                  // charset from a parameter, not a getstatic
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>",
                "([BLjava/nio/charset/Charset;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val calls = methodCalls(StringBytecode.rewriteClass(cw.toByteArray()))
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.ofBytes([BLjava/nio/charset/Charset;)Ljava/lang/String;"),
                "an unrecognized (variable) charset must keep the generic ofBytes(...,Charset): $calls")
    }

    // ---- #18: Object-typed equals() call sites redirect to BmcStrings.objEquals ----

    @Test
    fun object_equals_call_site_is_redirected_to_objEquals() {
        // The collection models compare keys/elements via `key.equals(x)` where key is statically
        // typed Object, so javac emits INVOKEVIRTUAL java/lang/Object.equals(Object)Z. That site
        // bypassed the String-owner redirect and dispatched into JBMC's unsound native String.equals
        // (issue #18). It must now route through BmcStrings.objEquals(Object,Object)Z.
        val calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z")))
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.objEquals(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                "Object.equals must be redirected to BmcStrings.objEquals: $calls")
        assertFalse(calls.any { it.contains("java/lang/Object.equals") },
                "the original Object.equals call must be gone: $calls")
    }

    @Test
    fun interface_equals_call_site_is_redirected_to_objEquals() {
        // An interface that redeclares equals (e.g. java/util/List) compiles `list.equals(x)` to
        // INVOKEINTERFACE java/util/List.equals(Object)Z; that is also Object-typed dispatch over a
        // potentially-String element and must be redirected too.
        val calls = methodCalls(StringBytecode.rewriteClass(classCallingInterface(
                "java/util/List", "equals", "(Ljava/lang/Object;)Z")))
        assertTrue(calls.contains(
                "INVOKESTATIC org/bmc4j/engine/BmcStrings.objEquals(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                "interface .equals must be redirected to BmcStrings.objEquals: $calls")
        assertFalse(calls.any { it.contains("java/util/List.equals") },
                "the original interface equals call must be gone: $calls")
    }

    @Test
    fun concrete_class_equals_virtual_call_is_left_alone() {
        // A virtual equals on a concrete non-Object class (e.g. Integer.equals) has a non-String
        // receiver and an already-sound modeled equals, so it is deliberately NOT redirected — only
        // Object-typed (INVOKEVIRTUAL java/lang/Object / INVOKEINTERFACE) dispatch is.
        val calls = methodCalls(StringBytecode.rewriteClass(classCalling(
                Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "equals", "(Ljava/lang/Object;)Z")))
        assertTrue(calls.any { it.contains("java/lang/Integer.equals") },
                "Integer.equals must be left as a native virtual call: $calls")
        assertFalse(calls.any { it.contains("objEquals") },
                "Integer.equals must NOT be redirected: $calls")
    }

    @Test
    fun objEquals_routes_strings_soundly_and_delegates_non_strings() {
        // BmcStrings.objEquals is the redirect target: String/String goes through the sound shim,
        // everything else delegates to the receiver's real equals (so boxed primitives and user
        // classes keep normal semantics). On a real JVM the String/String case runs the shim's
        // length()+charAt loop; CProverString.charAt now delegates to the real String.charAt
        // off-engine (sound under no-refine, intrinsic-lowered under refinement), so the content
        // compare is exact here; content soundness is also covered end-to-end by the BMC proof below.
        assertTrue(BmcStrings.objEquals("abc", "abc"), "identical String content compares equal")
        assertTrue(BmcStrings.objEquals(null, null), "null/null is equal")
        assertFalse(BmcStrings.objEquals("abc", null), "String vs null is not equal")
        assertFalse(BmcStrings.objEquals("abc", 7), "String vs non-String is not equal")
        // Non-String receivers delegate to their own equals — boxed primitives stay correct.
        assertTrue(BmcStrings.objEquals(Integer.valueOf(7), Integer.valueOf(7)),
                "Integer.equals delegated, equal values compare equal")
        assertFalse(BmcStrings.objEquals(Integer.valueOf(7), Integer.valueOf(8)),
                "Integer.equals delegated, differing values are not equal")
        assertFalse(BmcStrings.objEquals(Integer.valueOf(7), java.lang.Long.valueOf(7L)),
                "Integer.equals delegated, cross-type is not equal")
    }

    @Test
    fun objEquals_call_site_inside_BmcStrings_is_not_rewritten() {
        // Soundness/termination guard: objEquals's own `a.equals(b)` fallback is INVOKEVIRTUAL
        // java/lang/Object.equals; if the redirect rewrote it, objEquals would call itself forever.
        // The BMC_STRINGS owner guard must leave BmcStrings's own equals call sites untouched.
        val rewritten = StringBytecode.rewriteClass(classBytes(BmcStrings::class.java))
        val calls = methodCalls(rewritten)
        assertFalse(calls.any { it.contains("BmcStrings.objEquals") },
                "BmcStrings's own equals fallback must NOT be redirected into self-recursion: $calls")
        assertTrue(calls.any { it.contains("java/lang/Object.equals") },
                "BmcStrings.objEquals must keep its real Object.equals delegation: $calls")
    }

    // ---- JVM-level behavioral tests: the rewritten bytecode must verify AND compute correctly ----
    // The record fixtures (Pt/Prims/WithRef) live in the Java StringBytecodeTestFixtures so they emit
    // javac's actual ObjectMethods bootstrap — see that file's javadoc for why they cannot be Kotlin.

    @Test
    fun concat_indy_is_desugared_to_working_bytecode() {
        // static String f(String s) { return "[" + s + "]"; }  via StringConcatFactory indy
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "ConcatC", null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "f",
                "(Ljava/lang/String;)Ljava/lang/String;", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        val bsm = Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false)
        mv.visitInvokeDynamicInsn("makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", bsm, "[]")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()

        val rewritten = StringBytecode.rewriteClass(cw.toByteArray())
        // The indy is gone, replaced by a call to a generated helper.
        val calls = methodCalls(rewritten)
        assertFalse(calls.any { it.contains("StringConcatFactory") }, "indy should be gone")
        val c = define("ConcatC", rewritten)
        val f = c.getMethod("f", String::class.java)
        assertEquals("[hi]", f.invoke(null, "hi"))
        assertEquals("[]", f.invoke(null, ""))
    }

    @Test
    fun record_equals_indy_is_desugared_to_working_bytecode() {
        val name = StringBytecodeTestFixtures.Pt::class.java.name
        val orig: ByteArray
        javaClass.classLoader.getResourceAsStream(name.replace('.', '/') + ".class").use { input ->
            orig = input!!.readAllBytes()
        }
        val rewritten = StringBytecode.rewriteClass(orig)
        val pt = ChildFirst(javaClass.classLoader, mapOf(name to rewritten)).loadClass(name)
        val ctor = pt.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        ctor.isAccessible = true
        val a = ctor.newInstance(1, 2)
        val b = ctor.newInstance(1, 2)
        val c = ctor.newInstance(1, 3)
        val d = ctor.newInstance(9, 2)
        val eq = pt.getMethod("equals", Any::class.java)
        eq.isAccessible = true
        assertTrue(eq.invoke(a, b) as Boolean, "equal records compare equal")
        assertFalse(eq.invoke(a, c) as Boolean, "differing second component")
        assertFalse(eq.invoke(a, d) as Boolean, "differing first component")
        assertFalse(eq.invoke(a, "not a Pt") as Boolean, "non-record is not equal")
    }

    @Test
    fun record_hashCode_indy_is_desugared_to_consistent_pure_function() {
        // Property under test (the soundness contract): hashCode is a pure, deterministic function of
        // the components — equal records hash equal, repeated calls agree, and it varies with input.
        // We deliberately do NOT assert a specific magic value (the JDK leaves it unspecified).
        val prims = loadRewritten(StringBytecodeTestFixtures.Prims::class.java)
        val ctor = prims.getDeclaredConstructor(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Double::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        ctor.isAccessible = true
        val hc = prims.getMethod("hashCode")
        hc.isAccessible = true

        val a = ctor.newInstance(7, 99L, true, 3.5, 1.25f)
        val b = ctor.newInstance(7, 99L, true, 3.5, 1.25f)   // equal components
        val c = ctor.newInstance(8, 99L, true, 3.5, 1.25f)   // differs in i
        val dl = ctor.newInstance(7, 100L, true, 3.5, 1.25f)  // differs in long
        val e = ctor.newInstance(7, 99L, false, 3.5, 1.25f)  // differs in boolean
        val dd = ctor.newInstance(7, 99L, true, 4.5, 1.25f)  // differs in double
        val df = ctor.newInstance(7, 99L, true, 3.5, 2.25f)  // differs in float

        val ha = hc.invoke(a) as Int
        assertEquals(ha, hc.invoke(b) as Int, "equal records must have equal hashCode")
        assertEquals(ha, hc.invoke(a) as Int, "hashCode must be consistent across calls")
        assertTrue(ha != hc.invoke(c) as Int, "hashCode should depend on int component")
        assertTrue(ha != hc.invoke(dl) as Int, "hashCode should depend on long component")
        assertTrue(ha != hc.invoke(e) as Int, "hashCode should depend on boolean component")
        assertTrue(ha != hc.invoke(dd) as Int, "hashCode should depend on double component")
        assertTrue(ha != hc.invoke(df) as Int, "hashCode should depend on float component")
    }

    @Test
    fun record_toString_indy_is_desugared_to_canonical_form() {
        // For an all-primitive record the generated toString must equal javac's canonical
        // "Name[c1=v1, c2=v2]" exactly (no charAt involved, so this is meaningful on a real JVM).
        val prims = loadRewritten(StringBytecodeTestFixtures.Prims::class.java)
        val ctor = prims.getDeclaredConstructor(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Double::class.javaPrimitiveType, Float::class.javaPrimitiveType)
        ctor.isAccessible = true
        val o = ctor.newInstance(7, 99L, true, 3.5, 1.25f)
        val ts = prims.getMethod("toString")
        ts.isAccessible = true
        assertEquals("Prims[i=7, l=99, b=true, d=3.5, f=1.25]", ts.invoke(o))
    }

    @Test
    fun record_toString_with_reference_component_is_left_alone() {
        // A record with a non-String reference component cannot be rendered soundly (String.valueOf of
        // a reference is JBMC-nondet), so its toString indy is intentionally NOT desugared.
        val rewritten = StringBytecode.rewriteClass(classBytes(StringBytecodeTestFixtures.WithRef::class.java))
        val sawToString = booleanArrayOf(false)
        ClassReader(rewritten).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                        bsm: Handle?, vararg args: Any?) {
                        if (bsm?.owner == "java/lang/runtime/ObjectMethods" && name == "toString") {
                            sawToString[0] = true
                        }
                    }
                }
            }
        }, 0)
        assertTrue(sawToString[0], "toString indy with a non-String reference component must be left untouched")
    }

    @Test
    fun record_object_methods_indy_are_gone_for_all_primitive_record() {
        // For an all-primitive record, all three ObjectMethods sites (equals/hashCode/toString) are
        // desugared away; no invokedynamic to ObjectMethods remains.
        val rewritten = StringBytecode.rewriteClass(classBytes(StringBytecodeTestFixtures.Pt::class.java))
        val remaining = ArrayList<String>()
        ClassReader(rewritten).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                        bsm: Handle?, vararg args: Any?) {
                        if (bsm?.owner == "java/lang/runtime/ObjectMethods") {
                            remaining.add(name!!)
                        }
                    }
                }
            }
        }, 0)
        assertTrue(remaining.isEmpty(),
                "all ObjectMethods sites (equals/hashCode/toString) must be desugared: $remaining")
    }

    // ---- BmcStrings.contains must not throw a spurious CCE on a non-String CharSequence ----

    @Test
    fun contains_with_String_needle_runs_without_a_classcast() {
        // The sound path: a String needle takes the char-loop. On a real JVM CProverString.charAt
        // returns '\0' (its meaning is only inside JBMC), so we don't assert content semantics here —
        // only that the shim runs and returns a boolean without throwing (the loop is unchanged by the
        // CharSequence-needle fix). Content soundness is covered end-to-end by the strings conformance proofs.
        assertDoesNotThrow { BmcStrings.contains("haystack", "needle") }
    }

    @Test
    fun contains_with_StringBuilder_needle_does_not_throw_classcast() {
        // The redirected descriptor is (CharSequence)Z, so s.contains(aStringBuilder) used to
        // hit `String n = (String) needle` and throw a ClassCastException INSIDE our own shim — a
        // spurious refutation pointing at bmc4j. After the fix a non-String CharSequence degrades
        // gracefully (routes through toString()) instead of crashing.
        val needle = StringBuilder("abc")
        assertDoesNotThrow({ BmcStrings.contains("xxabcxx", needle) },
                "a StringBuilder needle must not throw a ClassCastException inside BmcStrings")
        // And it must still reject null per String.contains semantics.
        assertThrows(NullPointerException::class.java) { BmcStrings.contains("x", null) }
    }

    companion object {
        private fun assertRedirected(name: String, desc: String, expectedDesc: String) {
            val calls = methodCalls(StringBytecode.rewriteClass(
                    classCalling(Opcodes.INVOKEVIRTUAL, "java/lang/String", name, desc)))
            assertTrue(calls.contains("INVOKESTATIC org/bmc4j/engine/BmcStrings.$name$expectedDesc"),
                    "$name should be redirected to BmcStrings: $calls")
            assertFalse(calls.any { it.contains("java/lang/String.$name") },
                    "original String.$name call must be gone")
        }

        /** Assert a static `owner.name desc` call rewrites to exactly [expectedCall]. */
        private fun assertStaticRedirected(owner: String, name: String, desc: String, expectedCall: String) {
            val calls = methodCalls(StringBytecode.rewriteClass(classCallingStatic(owner, name, desc)))
            assertTrue(calls.contains(expectedCall),
                    "$owner.$name$desc should be redirected to $expectedCall: $calls")
            assertFalse(calls.any { it.contains("$owner.$name") },
                    "original $owner.$name call must be gone: $calls")
        }

        /** A class C with a method that makes exactly the given INVOKESTATIC call. Builds the argument
         *  types from [desc] (char from an int arg, char[] from a ref arg) so the stack is well-formed. */
        private fun classCallingStatic(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use",
                    "()Ljava/lang/String;", null, null)
            mv.visitCode()
            for (t in org.objectweb.asm.Type.getArgumentTypes(desc)) {
                when (t.sort) {
                    org.objectweb.asm.Type.CHAR, org.objectweb.asm.Type.INT -> mv.visitInsn(Opcodes.ICONST_0)
                    org.objectweb.asm.Type.ARRAY -> {
                        mv.visitInsn(Opcodes.ICONST_0)
                        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR)
                    }
                    else -> mv.visitInsn(Opcodes.ACONST_NULL)
                }
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun assertNotRedirected(name: String, desc: String) {
            val calls = methodCalls(StringBytecode.rewriteClass(
                    classCalling(Opcodes.INVOKEVIRTUAL, "java/lang/String", name, desc)))
            assertTrue(calls.any { it.contains("java/lang/String.$name$desc") },
                    "$name should be left as a native String call: $calls")
            assertFalse(calls.any { it.contains("BmcStrings.$name") },
                    "$name must NOT be redirected to BmcStrings (it is native-sound): $calls")
        }

        private fun classBytes(c: Class<*>): ByteArray {
            val name = c.name
            StringBytecodeTest::class.java.classLoader
                    .getResourceAsStream(name.replace('.', '/') + ".class").use { input ->
                return input!!.readAllBytes()
            }
        }

        private fun loadRewritten(c: Class<*>): Class<*> {
            val name = c.name
            val rewritten = StringBytecode.rewriteClass(classBytes(c))
            return ChildFirst(StringBytecodeTest::class.java.classLoader, mapOf(name to rewritten)).loadClass(name)
        }

        private fun define(name: String, bytes: ByteArray): Class<*> {
            return ChildFirst(StringBytecodeTest::class.java.classLoader, mapOf(name to bytes)).defineNamed(name)
        }

        /** A class C with a method that makes exactly the given call (args already on the stack as needed). */
        private fun classCalling(op: Int, owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            // static use(String a, String b) { a.<call>(...); }  (b loaded only for the 1-arg equals case)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use",
                    "(Ljava/lang/String;Ljava/lang/String;)V", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)                       // receiver
            if (!desc.startsWith("()")) {
                mv.visitVarInsn(Opcodes.ALOAD, 1)                  // the single (ref) argument, if any
            }
            mv.visitMethodInsn(op, owner, name, desc, false)
            mv.visitInsn(Opcodes.POP)                              // discard result
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        /** A class C with a method making exactly one INVOKEINTERFACE call to owner.name(arg). */
        private fun classCallingInterface(owner: String, name: String, desc: String): ByteArray {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "C", null, "java/lang/Object", null)
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "use",
                    "(L$owner;Ljava/lang/Object;)V", null, null)
            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)                       // interface-typed receiver
            mv.visitVarInsn(Opcodes.ALOAD, 1)                       // the Object argument
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, desc, true)
            mv.visitInsn(Opcodes.POP)                              // discard result
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            cw.visitEnd()
            return cw.toByteArray()
        }

        private fun methodCalls(clazz: ByteArray): List<String> {
            val calls = ArrayList<String>()
            ClassReader(clazz).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         e: Array<String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            val kind = when (op) {
                                Opcodes.INVOKESTATIC -> "INVOKESTATIC"
                                Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL"
                                else -> "INVOKE"
                            }
                            calls.add("$kind $owner.$name$desc")
                        }
                    }
                }
            }, 0)
            return calls
        }
    }

    /** Loads the named classes from given bytes (child-first), delegating everything else to the parent. */
    private class ChildFirst(parent: ClassLoader, private val defs: Map<String, ByteArray>) :
            ClassLoader(parent) {

        fun defineNamed(name: String): Class<*> {
            val b = defs[name]!!
            return defineClass(name, b, 0, b.size)
        }

        @Throws(ClassNotFoundException::class)
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (defs.containsKey(name)) {
                synchronized(getClassLoadingLock(name)) {
                    var c = findLoadedClass(name)
                    if (c == null) {
                        c = defineNamed(name)
                    }
                    if (resolve) {
                        resolveClass(c)
                    }
                    return c
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}
