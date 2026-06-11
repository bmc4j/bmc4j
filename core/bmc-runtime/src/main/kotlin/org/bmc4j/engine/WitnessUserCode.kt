package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decides which counterexample assignments belong in the witness: those that name a **local variable
 * the developer actually declared in their own code**, dropping engine synthetics and library/model
 * internals. Built once per [parse][JbmcOutputParser.parse] from the test JVM's *original* classpath
 * (`java.class.path`), then queried per trace frame.
 *
 * ## Discriminating "the user's own code" from the library / models / engine (the SOUND part)
 * A counterexample frame is attributed to a JBMC function id like
 * `java::pkg.Class.method:(I)V`. The question is whether `pkg.Class` is *the consumer's own test code*
 * (its proof method, or a helper it factored inputs into - both should keep their inputs) versus the
 * library-under-proof, the bmc4j models, the bmc4j runtime, or an engine frame (all of which must be
 * excluded). The discriminator is **origin on the classpath**, not a package-prefix guess - the user
 * is free to put helpers in any package, so a prefix match is unsound:
 *
 *  1. **Compiled into a DIRECTORY entry, not a dependency jar.** A consumer compiles their own
 *     proof/test/helper classes into their build's *output directory*; everything they did not author
 *     arrives as a **jar** (the published `bmc-runtime`, the bundled models, kotlinx, JUnit, the JDK).
 *     So we accept a class only when its `.class` resolves under a *directory* root of the ORIGINAL
 *     classpath. This is the load-bearing signal.
 *  2. **Not a reserved library/model/engine namespace.** In the in-repo / `includeBuild` development
 *     layout the bmc4j models (`java.*`, `kotlin.*`) and the bmc4j runtime/analysis classes
 *     (`org.bmc4j.*`, `org.cprover.*`) are *also* directory entries, so (1) alone would let them
 *     through. A consumer can never author a class in those namespaces, so excluding them by package is
 *     sound and closes the dev-layout gap. (A real consumer's own helper is in their own package and is
 *     a directory class, so it passes both.)
 *
 * Both conditions together: a frame is user code iff its class is a directory-origin class AND its
 * package is outside the reserved namespaces. The library-under-proof (a jar dependency), the models
 * (jars when published; `java.*`/`kotlin.*` dirs in-repo), the runtime (a jar when published;
 * `org.bmc4j.*` in-repo), and synthetic engine frames (no resolvable `.class`) are all rejected.
 *
 * ## Requiring a DECLARED LOCAL (the LocalVariableTable mechanism)
 * Passing the frame filter is necessary but not sufficient: `Bmc.anyInt()` is
 * `CProver.nondetInt()`, which JBMC intrinsifies into a fresh nondet symbol the trace names (e.g.) `i`
 * and attributes to the `anyInt()` call's source line *inside the user proof method* - so it shares the
 * user frame yet was declared nowhere. To drop it while keeping the developer's real `val x = ...`, we
 * require the assignment's name to be an actual **declared local** of the method it is attributed to,
 * checked against that method's `LocalVariableTable` (read via ASM). The synthetic `i` is declared in
 * no `LocalVariableTable`, so it is dropped; `x` / a helper's `a` are declared, so they survive.
 *
 * **Graceful fallback.** A class compiled `-g:none` carries no `LocalVariableTable`; demanding one
 * there would wrongly drop every real input. So the table is queried per (class, method, descriptor)
 * and the answer is tri-state:
 *  - [LocalCheck.DECLARED] - the name is in the method's table -> keep.
 *  - [LocalCheck.UNDECLARED] - the table exists for the method but the name is absent -> drop (this is
 *    the engine-synthetic case the feature targets).
 *  - [LocalCheck.NO_TABLE] - no usable table for the method (compiled `-g:none`, class unreadable, or
 *    method not found) -> **degrade to the pre-existing behavior** (keep, subject only to the cheap
 *    synthetic pre-filter) for that frame rather than dropping everything.
 *
 * Class bytes and parsed tables are **memoized per class** so a trace touching one method of a class
 * pays one read.
 */
internal class WitnessUserCode private constructor(private val dirRoots: List<Path>) {

    /** Memoized per internal class name: the parsed local tables, or null when the class is unreadable. */
    private val tableCache = HashMap<String, ClassLocals?>()

    /** Memoized per internal class name: whether it is a user (directory-origin, non-reserved) class. */
    private val userClassCache = HashMap<String, Boolean>()

    /**
     * True if [funcId] (a JBMC `java::pkg.Class.method:(desc)ret`) is one of the **consumer's own**
     * methods - a directory-origin class outside the reserved bmc4j/JDK/Kotlin namespaces. The
     * library-under-proof, the models, the runtime, and synthetic engine frames are all false.
     */
    fun isUserFrame(funcId: String?): Boolean {
        val internal = internalClassOf(funcId) ?: return false
        return userClassCache.getOrPut(internal) { isUserClass(internal) }
    }

    /**
     * Whether [name] should be kept as a witness input for the assignment attributed to [funcId]:
     * DECLARED (in the method's LocalVariableTable) -> keep; UNDECLARED (table present, name absent -
     * the engine-synthetic case) -> drop; NO_TABLE (no usable debug info) -> degrade to legacy behavior.
     * See [LocalCheck].
     */
    fun checkLocal(funcId: String?, name: String): LocalCheck {
        val internal = internalClassOf(funcId) ?: return LocalCheck.NO_TABLE
        val method = methodNameOf(funcId) ?: return LocalCheck.NO_TABLE
        val desc = descriptorOf(funcId) ?: return LocalCheck.NO_TABLE
        val locals = tableCache.getOrPut(internal) { readClassLocals(internal) }
                ?: return LocalCheck.NO_TABLE
        val declared = locals.localsOf(method, desc) ?: return LocalCheck.NO_TABLE
        return if (declared.contains(name)) LocalCheck.DECLARED else LocalCheck.UNDECLARED
    }

    /**
     * A class is the user's own iff its `.class` lives under a DIRECTORY root of the original classpath
     * (i.e. it was compiled into a build output dir, not delivered as a dependency jar) AND its package
     * is outside the reserved library/model/engine namespaces (which a consumer can never author and
     * which appear as directory entries in the in-repo / includeBuild dev layout).
     */
    private fun isUserClass(internal: String): Boolean {
        if (isReservedNamespace(internal)) {
            return false
        }
        return classFileUnderDirRoot(internal) != null
    }

    /** The `.class` file for [internal] under one of the directory roots, or null if it is jar-only. */
    private fun classFileUnderDirRoot(internal: String): Path? {
        val rel = internal.replace('/', File.separatorChar) + ".class"
        for (root in dirRoots) {
            val candidate = root.resolve(rel)
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
        }
        return null
    }

    /** Read + parse the LocalVariableTables of [internal] from its directory-root `.class`; null if absent/unreadable. */
    private fun readClassLocals(internal: String): ClassLocals? {
        val file = classFileUnderDirRoot(internal) ?: return null
        return try {
            val bytes = Files.readAllBytes(file)
            val collector = LocalsCollector()
            // SKIP_FRAMES keeps the read cheap; we need the LocalVariableTable, which is NOT skipped
            // (only SKIP_DEBUG would drop it), so do not pass SKIP_DEBUG here.
            ClassReader(bytes).accept(collector, ClassReader.SKIP_FRAMES)
            ClassLocals(collector.byMethod)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }

    /** Parsed local-variable names of one class, keyed by `method+descriptor`. */
    private class ClassLocals(private val byMethod: Map<String, Set<String>>) {
        /** The declared local names of `name+desc`, or null when that method carried no table. */
        fun localsOf(name: String, desc: String): Set<String>? = byMethod[name + desc]
    }

    /**
     * Collects, per `method+descriptor`, the set of names in its LocalVariableTable. A method with a
     * table but no entries records an empty set (so a name is correctly judged UNDECLARED); a method
     * with no table at all records NOTHING (so the lookup returns null -> NO_TABLE -> legacy fallback).
     */
    private class LocalsCollector : ClassVisitor(Opcodes.ASM9) {
        val byMethod = HashMap<String, MutableSet<String>>()

        override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?,
                                 exceptions: Array<out String>?): MethodVisitor? {
            if (name == null || descriptor == null) {
                return null
            }
            val key = name + descriptor
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitLocalVariable(localName: String?, localDesc: String?, sig: String?,
                                                start: Label?, end: Label?, index: Int) {
                    // First local variable of this method: the method HAS a table - create the set even
                    // if (defensively) a name is null, so localsOf returns non-null (UNDECLARED, not the
                    // NO_TABLE fallback) for a method that carries debug info.
                    val set = byMethod.getOrPut(key) { LinkedHashSet() }
                    if (localName != null) {
                        set.add(localName)
                    }
                }
            }
        }
    }

    companion object {

        /**
         * Build a discriminator from the test JVM's ORIGINAL classpath (the un-rewritten
         * `request.classpath`), or null when no classpath is available - in which case the caller keeps
         * its legacy proof-method-frame behavior. Only DIRECTORY entries are retained as user roots; jar
         * entries are dependencies and never carry the consumer's own code.
         */
        @JvmStatic
        fun from(classpath: String?): WitnessUserCode? {
            if (classpath.isNullOrBlank()) {
                return null
            }
            val dirs = mutableListOf<Path>()
            for (entry in classpath.split(File.pathSeparator)) {
                if (entry.isEmpty()) {
                    continue
                }
                try {
                    val p = Path.of(entry)
                    if (Files.isDirectory(p)) {
                        dirs.add(p)
                    }
                } catch (ignored: RuntimeException) {
                    // An unparseable entry contributes no root; it simply can't make a class "user code".
                }
            }
            return WitnessUserCode(dirs)
        }

        /**
         * Reserved namespaces a consumer can never author: the bmc4j runtime/analysis/models
         * (`org.bmc4j.*`), CProver intrinsics (`org.cprover.*`), and the JDK / Kotlin stand-in models
         * (`java.*`, `javax.*`, `jdk.*`, `sun.*`, `com.sun.*`, `kotlin.*`, `kotlinx.*`). In the in-repo /
         * includeBuild layout these ship as directory entries, so the directory-origin test alone would
         * admit them; excluding by namespace closes that gap soundly (none can be consumer-authored).
         */
        internal fun isReservedNamespace(internal: String): Boolean {
            val dotted = internal.replace('/', '.')
            return RESERVED_PREFIXES.any { dotted == it.trimEnd('.') || dotted.startsWith(it) }
        }

        private val RESERVED_PREFIXES = listOf(
                "org.bmc4j.", "org.cprover.",
                "java.", "javax.", "jdk.", "sun.", "com.sun.",
                "kotlin.", "kotlinx.")

        /** `java::pkg.Class.method:(desc)ret` -> internal class name `pkg/Class`, or null. */
        internal fun internalClassOf(funcId: String?): String? {
            val dotted = dottedClassOf(funcId) ?: return null
            return dotted.replace('.', '/')
        }

        /** `java::pkg.Class.method:(desc)ret` -> `pkg.Class`, or null when unrecognizable. */
        private fun dottedClassOf(funcId: String?): String? {
            if (funcId == null || !funcId.startsWith("java::")) {
                return null
            }
            var s = funcId.removePrefix("java::")
            val sig = s.indexOf(":(")
            if (sig >= 0) {
                s = s.substring(0, sig)
            }
            val lastDot = s.lastIndexOf('.')
            if (lastDot <= 0) {
                return null
            }
            return s.substring(0, lastDot)
        }

        /** `java::pkg.Class.method:(desc)ret` -> `method`, or null. */
        internal fun methodNameOf(funcId: String?): String? {
            if (funcId == null || !funcId.startsWith("java::")) {
                return null
            }
            var s = funcId.removePrefix("java::")
            val sig = s.indexOf(":(")
            if (sig >= 0) {
                s = s.substring(0, sig)
            }
            val lastDot = s.lastIndexOf('.')
            if (lastDot < 0 || lastDot + 1 >= s.length) {
                return null
            }
            return s.substring(lastDot + 1)
        }

        /** `java::pkg.Class.method:(desc)ret` -> the JVM descriptor `(desc)ret`, or null. */
        internal fun descriptorOf(funcId: String?): String? {
            if (funcId == null) {
                return null
            }
            val sig = funcId.indexOf(":(")
            if (sig < 0) {
                return null
            }
            return funcId.substring(sig + 1)
        }
    }

    /** Result of the LocalVariableTable check for one assignment - see [checkLocal]. */
    enum class LocalCheck {
        /** The name is a declared local of the attributed method -> keep it in the witness. */
        DECLARED,

        /** The method carries a table and the name is absent -> an engine synthetic -> drop it. */
        UNDECLARED,

        /** No usable table (compiled `-g:none`, class unreadable, method not found) -> legacy fallback. */
        NO_TABLE
    }
}
