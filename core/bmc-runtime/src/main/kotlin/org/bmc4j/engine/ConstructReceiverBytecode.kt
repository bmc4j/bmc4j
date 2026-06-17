package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Constructs the proof's RECEIVER so an instance `@BmcProof` analyses with its instance fields pinned to
 * their initializers, exactly as a JUnit run would.
 *
 * jbmc analyses a `@BmcProof` instance method as a standalone entry function, synthesising its implicit
 * `this` NONDETERMINISTICALLY — it never runs the class constructor `<init>`. Every instance field is
 * initialised in `<init>`, so under that nondet `this` an instance `private val arr = intArrayOf(...)`
 * reads as a nondet array ref (null -> NPE, or unknown length -> AIOOBE) and an instance `private val n = 8`
 * reads as a SILENT nondet int (the proof then quantifies over all `n`, masking a defect or passing
 * vacuously). Statics (`<clinit>`, already run) and method locals are unaffected.
 *
 * This pass closes that gap by SYNTHESISING a loop-free static wrapper into the entry class:
 *
 * ```
 *   public static void __bmc$entry$<method>() throws Throwable { new EntryClass().<proofMethod>(); }
 * ```
 *
 * and [JbmcBackend] redirects `--function` to the wrapper ([wrapperEntryFunction]). jbmc then executes
 * `<init>` on a freshly-constructed receiver before the proof body runs, pinning instance fields to their
 * initializers. The proof method's OWN bytecode is left byte-identical — only a new method is added — so
 * its loop ids (`java::pkg.Class.method:(desc)ret.N`) and every other analysis property are preserved.
 *
 * FALLBACK (today's behaviour, direct entry / nondet `this`) when the entry class has no analysable no-arg
 * constructor — a constructor that takes parameters, an abstract class, an interface, or a `<init>` we can't
 * locate. A proof that worked before must never stop working; [analyze] reports WHY it fell back and
 * [JbmcBackend] logs it. A STATIC proof method needs no receiver and is left unchanged.
 *
 * Mirrors the sibling passes' [ClasspathMirror] mechanics; the (entry class, method) identity is folded into
 * the mirror key so the wrapper-bearing mirror is a distinct, complete cache entry.
 */
object ConstructReceiverBytecode {

    private const val NO_ARG_CTOR_DESC = "()V"

    /** The synthetic wrapper's name prefix; deterministic per proof method so the redirect and the
     *  synthesis agree without threading a value between them. */
    private const val WRAPPER_PREFIX = "__bmc\$entry\$"

    /** Build-wide opt-OUT, default ON. `-Dbmc.constructReceiver=false` restores the legacy nondet-`this`
     *  entry for every proof (an escape hatch / A-B lever); any other value (or unset) keeps the fix on.
     *  Folded into the verdict cache via the runtime semantics identity bump that shipped the feature. */
    private const val ENABLED_PROP = "bmc.constructReceiver"

    private fun enabled(): Boolean =
            System.getProperty(ENABLED_PROP, "true").trim().lowercase() != "false"

    /** The synthetic wrapper method name for proof method [methodName] (deterministic). The proof name
     *  rides through verbatim (JVM method names admit the backtick-spaces Kotlin proofs use). */
    @JvmStatic
    fun wrapperName(methodName: String): String = WRAPPER_PREFIX + methodName

    /** Why this proof does NOT construct its receiver (falls back to today's nondet-`this` entry), or
     *  [ELIGIBLE] when it does. */
    enum class Reason(@JvmField val eligible: Boolean, @JvmField val note: String) {
        ELIGIBLE(true, "constructs the receiver via the no-arg constructor"),
        DISABLED(false, "receiver construction is disabled (-Dbmc.constructReceiver=false)"),
        STATIC_PROOF(false, "the proof method is static (no receiver to construct)"),
        ENTRY_CLASS_NOT_FOUND(false, "the entry class could not be read from the classpath"),
        PROOF_METHOD_NOT_FOUND(false, "the proof method could not be located in the entry class"),
        ABSTRACT_ENTRY_CLASS(false, "the entry class is abstract or an interface (cannot be constructed)"),
        NO_ANALYZABLE_NO_ARG_CTOR(false, "the entry class has no analysable no-arg constructor"),
        PARAMETERIZED_PROOF(false, "the proof method has parameters (jbmc nondets them as entry inputs; " +
                "a no-arg wrapper would drop them)")
    }

    /** The decision for one proof: whether to construct the receiver, and (when so) the proof method's
     *  exact descriptor so the wrapper invokes it correctly. */
    data class Decision(@JvmField val reason: Reason, @JvmField val proofDesc: String?) {
        val eligible: Boolean get() = reason.eligible
    }

    /**
     * Decide whether [entryClass].[methodName] on [classpath] should construct its receiver. ELIGIBLE iff
     * the proof method is an INSTANCE method AND the entry class is concrete with an analysable no-arg
     * constructor; otherwise a fallback [Reason]. Reads only the entry class's bytecode (the same
     * classpath-resolution as the JVM/jbmc); fail-safe to a fallback when the class can't be read.
     */
    @JvmStatic
    fun analyze(classpath: String, entryClass: String, methodName: String): Decision {
        if (!enabled()) {
            // Build-wide opt-out: behave exactly as before the feature (no wrapper, nondet `this`).
            return Decision(Reason.DISABLED, null)
        }
        val internalName = entryClass.replace('.', '/')
        val bytes = readClassFromClasspath(classpath, "$internalName.class")
                ?: return Decision(Reason.ENTRY_CLASS_NOT_FOUND, null)
        return analyzeBytes(bytes, methodName)
    }

    /** [analyze] over already-loaded class bytes. Exposed for unit tests. */
    internal fun analyzeBytes(bytes: ByteArray, methodName: String): Decision {
        var proofDesc: String? = null
        var hasNoArgCtor = false
        var isAbstractClass = false
        val cr = ClassReader(bytes)
        cr.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String?, signature: String?,
                               superName: String?, interfaces: Array<String>?) {
                // An abstract class or interface cannot be `new`-ed, so its receiver can't be constructed.
                isAbstractClass = (access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE)) != 0
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?,
                                     exceptions: Array<String>?): MethodVisitor? {
                if (name == "<init>" && descriptor == NO_ARG_CTOR_DESC) {
                    hasNoArgCtor = true
                }
                // The proof method: match by NAME. A proof method is no-arg (JUnit invokes it with no
                // args; symbolic inputs come from the body), but capture the exact descriptor regardless
                // so the wrapper's INVOKEVIRTUAL is always well-formed. A static proof method disqualifies.
                if (name == methodName && proofDesc == null) {
                    if ((access and Opcodes.ACC_STATIC) != 0) {
                        proofDesc = STATIC_MARKER
                    } else {
                        proofDesc = descriptor
                    }
                }
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        // A proof method with parameters is a symbolic-parameter proof: jbmc nondets the entry function's
        // arguments when --function points straight at the method. A no-arg wrapper that calls it can't
        // supply those, so we fall back to the direct entry (today's behavior) for parameterized proofs.
        val desc = proofDesc
        return when {
            desc == null -> Decision(Reason.PROOF_METHOD_NOT_FOUND, null)
            desc == STATIC_MARKER -> Decision(Reason.STATIC_PROOF, null)
            isAbstractClass -> Decision(Reason.ABSTRACT_ENTRY_CLASS, null)
            !hasNoArgCtor -> Decision(Reason.NO_ANALYZABLE_NO_ARG_CTOR, null)
            !desc.startsWith("()") -> Decision(Reason.PARAMETERIZED_PROOF, null)
            else -> Decision(Reason.ELIGIBLE, desc)
        }
    }

    /** Sentinel descriptor recording that the proof method was found but is static. */
    private const val STATIC_MARKER = "<static>"

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite [classpath] so the entry class [entryClass] carries the synthetic static wrapper for
     * [methodName] (and [proofDesc] — the proof method's exact descriptor). Memoized per (classpath, class,
     * method, desc). Both directory and jar entries are mirrored; every class but the entry is copied
     * verbatim. Call ONLY when [analyze] returned ELIGIBLE.
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, methodName: String, proofDesc: String): String {
        val internalName = entryClass.replace('.', '/')
        val key = "$classpath|$internalName|$methodName|$proofDesc"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "constructreceiver", { b ->
                ClasspathMirror.Transformed(rewriteClass(b, internalName, methodName, proofDesc))
            }, "$internalName|$methodName|$proofDesc")
        }
    }

    /**
     * Add the static wrapper to [internalName] (the entry class); every other class is copied verbatim.
     * The wrapper is `public static void <wrapper>() throws Throwable { new <entry>().<method><desc>; }`
     * — loop-free, never inlined into the proof method, so the proof method's loop ids are untouched.
     * Exposed for unit tests.
     */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String,
                              proofDesc: String): ByteArray {
        val cr = ClassReader(bytes)
        if (cr.className != internalName) {
            return bytes // not the entry class — nothing to add
        }
        // COMPUTE_FRAMES: the wrapper is a fresh straight-line method (no branches), but it does construct
        // an object; letting ASM compute the frames/maxs keeps the synthesis trivially correct. The
        // reader is passed so unchanged methods are copied without re-encoding.
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitEnd() {
                emitWrapper(this, internalName, methodName, proofDesc)
                super.visitEnd()
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /** Emit `public static void <wrapper>() throws Throwable { new <entry>().<method><desc>; }` into [cv].
     *  Declaring `throws Throwable` lets a proof method with any checked-throws signature be called without
     *  the verifier needing a handler. The result of a non-void proof method (proofs are `()V`, but be
     *  robust) is popped. */
    private fun emitWrapper(cv: ClassVisitor, internalName: String, methodName: String, proofDesc: String) {
        val mv = cv.visitMethod(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
                wrapperName(methodName), NO_ARG_CTOR_DESC, null, arrayOf("java/lang/Throwable"))
        mv.visitCode()
        // new <entry> ; dup ; invokespecial <entry>.<init>()V
        mv.visitTypeInsn(Opcodes.NEW, internalName)
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internalName, "<init>", NO_ARG_CTOR_DESC, false)
        // <receiver>.<proofMethod>(...)  — instance call on the freshly-constructed receiver.
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internalName, methodName, proofDesc, false)
        // Discard a return value if the proof method is (unusually) non-void, so the stack is balanced.
        popReturn(mv, proofDesc)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0) // recomputed by COMPUTE_MAXS/COMPUTE_FRAMES
        mv.visitEnd()
    }

    /** Pop a non-void return value left on the stack by the proof method (category-2 aware). A `()V` proof
     *  (the norm) pops nothing. */
    private fun popReturn(mv: MethodVisitor, proofDesc: String) {
        val ret = proofDesc.substringAfterLast(')')
        when (ret) {
            "V" -> {}
            "J", "D" -> mv.visitInsn(Opcodes.POP2)
            else -> mv.visitInsn(Opcodes.POP)
        }
    }

    /**
     * Read the bytes of [resource] (`a/b/C.class`) from the first [classpath] entry that holds it
     * (classpath order, exactly as the JVM/jbmc resolve), or null when absent. Both directory and jar
     * entries are searched. Fail-safe: a bad/locked entry is skipped, never throws.
     */
    private fun readClassFromClasspath(classpath: String, resource: String): ByteArray? {
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            try {
                val p = Path.of(entry)
                if (Files.isDirectory(p)) {
                    val f = p.resolve(resource)
                    if (Files.isRegularFile(f)) {
                        return Files.readAllBytes(f)
                    }
                } else if (Files.isRegularFile(p)
                        && (entry.endsWith(".jar", true) || entry.endsWith(".zip", true))) {
                    ZipFile(p.toFile()).use { zf ->
                        val e = zf.getEntry(resource)
                        if (e != null) {
                            return zf.getInputStream(e).use { it.readAllBytes() }
                        }
                    }
                }
            } catch (e: Exception) {
                // skip a bad entry; the next one may hold the class
            }
        }
        return null
    }
}
