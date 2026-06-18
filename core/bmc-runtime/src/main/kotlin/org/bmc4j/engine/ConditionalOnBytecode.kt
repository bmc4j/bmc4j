package org.bmc4j.engine

import org.bmc4j.BmcCondition
import org.bmc4j.StringMode
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * The prep-time pass behind [org.bmc4j.ConditionalOn]: it swaps in a MODE-CONDITIONAL override body for
 * its target when the override's [BmcCondition] holds for this proof's resolved config.
 *
 * ## What it does
 * A model method can carry a default body AND a `@ConditionalOn`-annotated STATIC override (in the same
 * class) that should replace it on a specific path. For each `@ConditionalOn` override whose condition
 * HOLDS for this run, this pass REDIRECTS every call to the override's `target` (the same-class method of
 * matching descriptor) to the override method instead - the call-site redirect proven by
 * [StringBytecode] / [StringLengthBytecode]. When the condition does NOT hold, nothing is rewritten: the
 * target keeps its default body, so the unaffected path (string refinement) is byte-for-byte untouched.
 *
 * ## Why a model needs this
 * bmc-models is the ALWAYS-ON overlay (it applies under BOTH [StringMode.REFINEMENT] and
 * [StringMode.CHAR_ARRAY_MODEL]). Some JDK methods are sound+fast via a refinement INTRINSIC but wrong
 * under no-refine, so they need a different body per mode WITHOUT a blanket override that would degrade
 * refinement. The motivating case: `Integer.toString(int)` / `Long.toString(long)` bottom out in the
 * refinement primitive `org.cprover.CProverString.toString`, which under `--no-refine-strings` returns an
 * UNCONSTRAINED (nondet-length) String. A `@ConditionalOn(STRING_REFINEMENT_OFF)` override does a bounded
 * digit build instead, while the default keeps delegating to the fast refinement intrinsic.
 *
 * ## Two phases
 * 1. SCAN the whole classpath for `@ConditionalOn` override methods whose condition holds, building a
 *    redirect map keyed by the target's `(owner, name, desc)`. The override and its target are in the
 *    same class, and the override's own descriptor equals the target's, so the redirect is
 *    overload-precise. A scan must precede the rewrite because the overrides and a call site may live in
 *    different classpath entries.
 * 2. MIRROR the classpath, redirecting every `INVOKESTATIC targetOwner.targetName(targetDesc)` to the
 *    override. The override method's OWN body is excluded from re-redirect (mirroring how [BmcStrings] is
 *    excluded in [StringLengthBytecode]) so an override can never be redirected into a loop.
 *
 * ## MVP scope
 * STATIC overrides only (the redirect is a call-site retarget to a static method) - which covers
 * `Integer`/`Long.toString`. Instance-method body-swap is a deliberate future expansion; a non-static
 * `@ConditionalOn` method is ignored here (no redirect entry built for it).
 *
 * ## Why this is per-proof, not hoistable
 * The condition is evaluated against the run's resolved config (the string mode), so the rewrite is
 * per-proof: it runs in-JVM in [JbmcBackend.prepareClasspath], gated on mode, AFTER the model jars are
 * spliced (so the override classes are present and their call sites are reachable). Under the
 * non-holding mode it does not run at all.
 */
internal object ConditionalOnBytecode {

    private const val CONDITIONAL_ON_DESC = "Lorg/bmc4j/ConditionalOn;"
    private const val CACHE_NAME = "conditional-on"

    /** Field separator for the composite map keys: a single space, built from its code point so no
     *  literal whitespace can be mangled in the source. A descriptor never contains it. */
    private val SEP: String = 32.toChar().toString()

    /** A resolved override: redirect `(targetOwner, targetName, targetDesc)` call sites to [overrideName]
     *  in the same class (same descriptor). */
    private class Override(
            @JvmField val targetOwner: String,
            @JvmField val targetName: String,
            @JvmField val desc: String,
            @JvmField val overrideName: String)

    /** Parsed `@ConditionalOn` annotation values off a method. */
    private class Parsed(@JvmField var condition: BmcCondition? = null,
                         @JvmField var target: String? = null)

    /** Memoize per `(classpath, condition)` - the redirect set is a pure function of those two. */
    private val CACHE = ConcurrentHashMap<String, String>()

    /** Compose a call-site key `owner<sep>name<sep>desc`. */
    private fun callKey(owner: String?, name: String?, desc: String?): String =
            owner + SEP + name + SEP + desc

    /**
     * Evaluate a [BmcCondition] against the proof's resolved configuration. THE single place conditions
     * are evaluated: adding a new condition is one enum constant ([BmcCondition]) plus one arm here.
     */
    @JvmStatic
    fun holds(condition: BmcCondition, request: BmcRequest): Boolean =
            when (condition) {
                BmcCondition.STRING_REFINEMENT_ON -> request.stringMode == StringMode.REFINEMENT
                BmcCondition.STRING_REFINEMENT_OFF -> request.stringMode == StringMode.CHAR_ARRAY_MODEL
            }

    /**
     * Rewrite [classpath] so every `@ConditionalOn` override whose condition holds for [request] replaces
     * its target at every call site. A no-op (returns [classpath] unchanged) when no override fires.
     * Memoized per `(classpath, resolved condition)`.
     */
    @JvmStatic
    fun rewrite(classpath: String, request: BmcRequest): String {
        // The condition outcomes for this run are what make the rewrite vary, so key on the string mode.
        val key = classpath + SEP + request.stringMode.name
        return CACHE.computeIfAbsent(key) { doRewrite(classpath, request) }
    }

    private fun doRewrite(classpath: String, request: BmcRequest): String {
        val overrides = scan(classpath, request)
        if (overrides.isEmpty()) {
            return classpath
        }
        // Index the firing redirects by the target call-site key for an O(1) lookup, and remember the
        // override method names per owner so the override's OWN body is excluded from re-redirect.
        val byCallSite = HashMap<String, String>()
        val overrideNamesByOwner = HashMap<String, MutableSet<String>>()
        for (o in overrides) {
            byCallSite[callKey(o.targetOwner, o.targetName, o.desc)] = o.overrideName
            overrideNamesByOwner.getOrPut(o.targetOwner) { HashSet() }.add(o.overrideName)
        }
        return ClasspathMirror.mirror(classpath, CACHE_NAME + "-" + request.stringMode.name, { b ->
            ClasspathMirror.Transformed(rewriteClass(b, byCallSite, overrideNamesByOwner))
        })
    }

    /**
     * Pure transform: redirect a target call site to its conditional override. [byCallSite] maps a
     * target's call-site key ([callKey]) to the override method name; [overrideNamesByOwner] names, per
     * owner, the override methods whose own bodies must NOT be re-redirected (the anti-loop guard). Exposed
     * for tests.
     */
    internal fun rewriteClass(bytes: ByteArray, byCallSite: Map<String, String>,
                              overrideNamesByOwner: Map<String, Set<String>>): ByteArray {
        if (byCallSite.isEmpty()) {
            return bytes
        }
        val cr = ClassReader(bytes)
        // ClassWriter(0): full re-emit so every call site flows through the redirect visitor below. The
        // transform only swaps the called method's name (same descriptor), so it adds no jump target.
        val cw = ClassWriter(0)
        val owner = arrayOfNulls<String>(1)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String?, sig: String?,
                               superName: String?, ifs: Array<String>?) {
                owner[0] = name
                super.visit(version, access, name, sig, superName, ifs)
            }

            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, desc, sig, ex)
                // Exclude the override method's OWN body: a redirect inside it could loop the target call
                // back to the override. (The Integer/Long overrides don't call the target, but mirror the
                // BmcStrings exclusion in StringLengthBytecode for safety as the construct generalizes.)
                if (name != null && overrideNamesByOwner[owner[0]]?.contains(name) == true) {
                    return mv
                }
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(op: Int, mOwner: String?, mName: String?,
                                                 mDesc: String?, itf: Boolean) {
                        if (op == Opcodes.INVOKESTATIC) {
                            val redirect = byCallSite[callKey(mOwner, mName, mDesc)]
                            if (redirect != null) {
                                // Retarget the static call to the same-class, same-descriptor override.
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, mOwner, redirect, mDesc, itf)
                                return
                            }
                        }
                        super.visitMethodInsn(op, mOwner, mName, mDesc, itf)
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Walk every classpath entry (dirs + jars) and collect the `@ConditionalOn` STATIC override methods
     * whose condition holds for [request]. A non-static override, a malformed annotation, or a missing
     * same-class target of matching descriptor is skipped (no redirect built) - never a soundness risk,
     * just an inert annotation.
     */
    private fun scan(classpath: String, request: BmcRequest): List<Override> {
        val out = ArrayList<Override>()
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) continue
            val path = Path.of(entry)
            if (!Files.exists(path)) continue
            if (Files.isDirectory(path)) {
                scanDir(path, request, out)
            } else {
                scanJar(path, request, out)
            }
        }
        return out
    }

    private fun scanDir(dir: Path, request: BmcRequest, out: MutableList<Override>) {
        Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                    .forEach { scanClass(Files.readAllBytes(it), request, out) }
        }
    }

    private fun scanJar(jar: Path, request: BmcRequest, out: MutableList<Override>) {
        ZipInputStream(Files.newInputStream(jar)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.endsWith(".class")) {
                    scanClass(zis.readBytes(), request, out)
                }
                e = zis.nextEntry
            }
        }
    }

    /** Collect the firing `@ConditionalOn` overrides declared by one class. */
    private fun scanClass(bytes: ByteArray, request: BmcRequest, out: MutableList<Override>) {
        val cr = ClassReader(bytes)
        // First find every (name, desc, isStatic) and every firing @ConditionalOn(condition, target), then
        // pair an override to its same-class same-descriptor target afterwards.
        val classOwner = arrayOfNulls<String>(1)
        val methods = ArrayList<MethodDecl>()
        val annotated = ArrayList<Triple<String, String, Parsed>>() // overrideName, overrideDesc, parsed
        val cv = object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String?, sig: String?,
                               superName: String?, ifs: Array<String>?) {
                classOwner[0] = name
            }

            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                methods.add(MethodDecl(name!!, desc!!, (access and Opcodes.ACC_STATIC) != 0))
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(d: String?, visible: Boolean): AnnotationVisitor? {
                        if (CONDITIONAL_ON_DESC != d) {
                            return null
                        }
                        val parsed = Parsed()
                        // Record every @ConditionalOn; the non-static / descriptor-mismatch filtering is
                        // done when pairing (so an unsupported annotation is simply inert, not an error).
                        annotated.add(Triple(name!!, desc!!, parsed))
                        return object : AnnotationVisitor(Opcodes.ASM9) {
                            override fun visitEnum(n: String?, enumDesc: String?, value: String?) {
                                if (n == "condition" && value != null) {
                                    parsed.condition =
                                            runCatching { BmcCondition.valueOf(value) }.getOrNull()
                                }
                            }
                            override fun visit(n: String?, value: Any?) {
                                if (n == "target") parsed.target = value as? String
                            }
                        }
                    }
                }
            }
        }
        cr.accept(cv, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)

        val staticByKey = methods.filter { it.isStatic }.associateBy { it.name + SEP + it.desc }
        for ((overrideName, overrideDesc, parsed) in annotated) {
            val condition = parsed.condition ?: continue
            val target = parsed.target ?: continue
            if (!holds(condition, request)) continue
            // The override must itself be static (MVP), and a same-class target of the SAME descriptor must
            // exist - otherwise the redirect would be ambiguous/unsound, so skip (inert annotation).
            if (staticByKey[overrideName + SEP + overrideDesc] == null) continue
            if (staticByKey[target + SEP + overrideDesc] == null) continue
            out.add(Override(classOwner[0]!!, target, overrideDesc, overrideName))
        }
    }

    private class MethodDecl(@JvmField val name: String, @JvmField val desc: String,
                             @JvmField val isStatic: Boolean)
}
