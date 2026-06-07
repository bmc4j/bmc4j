package org.bmc4j.engine

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale
import java.util.TreeSet
import java.util.zip.ZipFile

/**
 * The **transitively-reachable cone** of a proof: the set of classes a proof entry point can reach by
 * a static constant-pool / call-graph walk over its analysis classpath. Touching a class outside the
 * cone cannot change the proof's verdict, so the verdict cache keys on the cone's content instead of
 * the whole module — a change to an unrelated class then no longer invalidates every proof.
 *
 * This is a sibling of [ContractPurityAudit]'s entry-rooted reachability walk and shares its
 * conservative bias: the cone is a sound **over**-approximation of what a proof depends on. Over-
 * inclusion only costs extra cache misses (a re-run that proves the same thing); under-inclusion would
 * be a soundness bug — a stale green served after a class the proof actually depends on changed. So
 * anything the static walk cannot resolve is handled by **falling back to the whole classpath** (the
 * old coarse behaviour) for that proof, never by silently dropping the unresolved edge.
 *
 * The walk is deliberately broad about *type references* (not just call edges): from each reached
 * class it follows superclasses, interfaces, field types, every method's parameter/return/exception
 * types, and every type referenced by a method body (call owners, field owners, `new`/cast/array
 * types, descriptors, `.class` constants, the static types in `instanceof`). That superset is what a
 * constant-pool walk would capture, so a change to any class structurally reachable from the entry is
 * inside the cone.
 *
 * Intended to be reusable: the same cone identifies which classes a model-slicing pass would need to
 * hand the engine, not just which to hash.
 */
internal object ReachableCone {

    /**
     * Owners whose presence as a *call target* means the proof can reach code by a path the static
     * walk cannot follow — reflection and method handles resolve their target at runtime from data we
     * cannot read here. A reached call into one forces the conservative whole-classpath fallback (we
     * cannot bound the cone), exactly the bias [ContractPurityAudit] uses when it rejects these.
     */
    private val OPAQUE_DISPATCH_OWNERS: Set<String> = setOf(
            "java/lang/reflect/Method",
            "java/lang/reflect/Constructor",
            "java/lang/reflect/Field",
            "java/lang/reflect/Array",
            "java/lang/invoke/MethodHandle",
            "java/lang/invoke/MethodHandles",
            "java/lang/invoke/MethodHandles\$Lookup",
            "java/lang/invoke/VarHandle")

    /** `owner.name` call sites that load a class by name at runtime — an edge the static walk can't
     *  follow, so a reached call into one forces the whole-classpath fallback. */
    private val OPAQUE_DISPATCH_METHODS: Set<String> = setOf(
            "java/lang/Class.forName",
            "java/lang/Class.getMethod",
            "java/lang/Class.getDeclaredMethod",
            "java/lang/Class.getConstructor",
            "java/lang/Class.getDeclaredConstructor",
            "java/lang/Class.newInstance",
            "java/lang/ClassLoader.loadClass")

    /**
     * The `invokedynamic` bootstrap methods whose target the walk **can** statically attribute: the
     * compiler-emitted desugaring bootstraps whose implementation method (or referenced types) appear
     * directly in the instruction's bootstrap-method arguments, so following those arguments captures
     * the real callee. Any *other* bootstrap is opaque — its target is computed at link time from data
     * we don't model — and forces the whole-classpath fallback.
     */
    private val KNOWN_INDY_BOOTSTRAPS: Set<String> = setOf(
            "java/lang/invoke/LambdaMetafactory.metafactory",
            "java/lang/invoke/LambdaMetafactory.altMetafactory",
            "java/lang/invoke/StringConcatFactory.makeConcat",
            "java/lang/invoke/StringConcatFactory.makeConcatWithConstants",
            "java/lang/runtime/ObjectMethods.bootstrap",
            "java/lang/runtime/SwitchBootstraps.typeSwitch",
            "java/lang/runtime/SwitchBootstraps.enumSwitch")

    /**
     * The result of a cone computation. Either a resolved cone (the set of reachable internal class
     * names, used to scope the content digest) or [whole] — the conservative request to hash the
     * whole classpath because the walk hit something it could not bound soundly.
     */
    internal class Cone private constructor(
            /** Resolved reachable internal class names, or null when [whole] is set. */
            @JvmField val classes: Set<String>?,
            /** True when the cone could not be bounded and the caller must fall back to the whole
             *  classpath (the coarse, always-sound key). */
            @JvmField val whole: Boolean,
            /** Human reason the walk fell back, for diagnostics/tests; empty when resolved. */
            @JvmField val fallbackReason: String) {

        companion object {
            fun resolved(classes: Set<String>): Cone = Cone(classes, false, "")
            fun whole(reason: String): Cone = Cone(null, true, reason)
        }
    }

    /**
     * Compute the reachable cone of `entryClass` (a dotted FQN) over [classpath].
     *
     * Returns [Cone.whole] — the signal to hash the entire classpath — whenever the walk cannot
     * soundly bound the dependency set: the entry class isn't on the classpath, a reached body uses an
     * un-attributable `invokedynamic`, a reached body calls reflection / `MethodHandle` /
     * `Class.forName`, or any parse/IO error occurs. Otherwise returns the resolved set of every
     * internal class name structurally reachable from the entry.
     */
    @JvmStatic
    fun compute(entryClass: String, classpath: String?): Cone {
        if (classpath.isNullOrBlank()) {
            return Cone.whole("empty classpath")
        }
        return try {
            val index = ConeIndex(classpath)
            val entryInternal = entryClass.replace('.', '/')
            if (!index.contains(entryInternal)) {
                // The proof's own entry class isn't resolvable on the classpath we'd walk — we can't
                // root the cone, so fall back to the whole classpath (never under-approximate).
                return Cone.whole("entry class $entryInternal not on classpath")
            }
            walk(entryInternal, index)
        } catch (e: RuntimeException) {
            Cone.whole("walk error: " + e.javaClass.simpleName)
        } catch (e: IOException) {
            Cone.whole("walk error: " + e.javaClass.simpleName)
        }
    }

    /** Worklist over the type-reference graph from [entryInternal]. Records every reachable class; a
     *  reachable but unresolvable type is recorded but not opened (a JDK/library type with no body on
     *  the classpath contributes nothing further) — its bytes, when on the classpath, are hashed; when
     *  off-classpath it's a real-JDK/model type whose content the coarse classpath digest already
     *  covers via the model jars. An opaque dispatch / unknown indy aborts to the whole-classpath
     *  fallback. */
    private fun walk(entryInternal: String, index: ConeIndex): Cone {
        val reached = HashSet<String>()
        val work = ArrayDeque<String>()
        val refs = ReferenceCollector()
        if (reached.add(entryInternal)) {
            work.add(entryInternal)
        }
        while (work.isNotEmpty()) {
            val owner = work.poll()
            val bytes = index.bytesOf(owner) ?: continue // not on classpath: a leaf for the walk
            refs.reset()
            val abortReason = refs.scan(bytes)
            if (abortReason != null) {
                return Cone.whole(abortReason)
            }
            for (ref in refs.referenced) {
                if (reached.add(ref)) {
                    work.add(ref)
                }
            }
        }
        return Cone.resolved(reached)
    }

    /**
     * Collects every internal class name a class structurally references, and signals (via [scan]'s
     * return) when it sees something the walk can't bound — an opaque dispatch call or an un-attributable
     * `invokedynamic`. Reused across classes via [reset] to avoid per-class allocation.
     */
    private class ReferenceCollector {
        @JvmField val referenced = HashSet<String>()
        private var abort: String? = null

        fun reset() {
            referenced.clear()
            abort = null
        }

        /** Scan [classBytes]; returns a fallback reason if an unbounded edge was seen, else null. */
        fun scan(classBytes: ByteArray): String? {
            ClassReader(classBytes).accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            return abort
        }

        private fun addInternal(name: String?) {
            if (name == null || abort != null) {
                return
            }
            // Some callers (e.g. ANEWARRAY's type operand) can hand us an array descriptor such as
            // `[Ljava/lang/String;` or `[I`. Strip array dimensions to the element type and unwrap an
            // `L...;` object descriptor; a primitive array element (`[I` -> `I`) has no class to reach.
            // CRUCIAL: the primitive-letter check applies ONLY to a name that was actually in descriptor
            // form (array-stripped or `L...;`-wrapped). A *bare internal name* is already resolved and
            // must be recorded verbatim even when it is a single letter — a class literally named `B`
            // is a valid type to reach, not the `byte` primitive descriptor it happens to spell.
            var n: String = name
            val wasDescriptor = n.startsWith("[")
            while (n.startsWith("[")) {
                n = n.substring(1)
            }
            if (n.startsWith("L") && n.endsWith(";")) {
                n = n.substring(1, n.length - 1)
            } else if (wasDescriptor && isPrimitiveDescriptor(n)) {
                return // an array of a primitive element: no class to reach
            }
            if (n.isNotEmpty()) {
                referenced.add(n)
            }
        }

        private fun isPrimitiveDescriptor(n: String): Boolean =
                n.length == 1 && "VZBCSIJFD".indexOf(n[0]) >= 0

        /** Record every object type mentioned in a method/field descriptor. */
        private fun addTypesOfDescriptor(descriptor: String?) {
            if (descriptor == null || abort != null) {
                return
            }
            try {
                if (descriptor.startsWith("(")) {
                    for (t in Type.getArgumentTypes(descriptor)) {
                        addType(t)
                    }
                    addType(Type.getReturnType(descriptor))
                } else {
                    addType(Type.getType(descriptor))
                }
            } catch (e: RuntimeException) {
                // A malformed descriptor: ignore (it adds no resolvable type). Soundness unaffected —
                // a real reference is still caught by the owner/explicit-type edges.
            }
        }

        private fun addType(t: Type?) {
            if (t == null) {
                return
            }
            when (t.sort) {
                Type.OBJECT -> addInternal(t.internalName)
                Type.ARRAY -> if (t.elementType.sort == Type.OBJECT) addInternal(t.elementType.internalName)
                else -> {}
            }
        }

        private fun flagOpaque(reason: String) {
            if (abort == null) {
                abort = reason
            }
        }

        private val methodVisitor = object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                         itf: Boolean) {
                if (owner != null && (OPAQUE_DISPATCH_OWNERS.contains(owner) ||
                                (name != null && OPAQUE_DISPATCH_METHODS.contains("$owner.$name")))) {
                    flagOpaque("reaches opaque dispatch $owner.$name (reflection / method handle):" +
                            " cone can't be bounded statically")
                    return
                }
                addInternal(owner)
                addTypesOfDescriptor(desc)
            }

            override fun visitFieldInsn(op: Int, owner: String?, name: String?, desc: String?) {
                addInternal(owner)
                addTypesOfDescriptor(desc)
            }

            override fun visitTypeInsn(op: Int, type: String?) = addInternal(type)

            override fun visitMultiANewArrayInsn(descriptor: String?, dims: Int) =
                    addTypesOfDescriptor(descriptor)

            override fun visitLdcInsn(value: Any?) {
                when (value) {
                    is Type -> addType(value)
                    is Handle -> {
                        addInternal(value.owner)
                        addTypesOfDescriptor(value.desc)
                    }
                    else -> {}
                }
            }

            override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bsm: Handle?,
                                                vararg bsmArgs: Any?) {
                // An invokedynamic links its callee at runtime. We can only bound the cone when the
                // bootstrap is one whose implementation is named directly in the instruction's args
                // (the desugaring bootstraps the compiler emits); for those we follow the args to the
                // real impl method's owner. Any other bootstrap is opaque -> whole-classpath fallback.
                val bsmKey = if (bsm != null) "${bsm.owner}.${bsm.name}" else "<none>"
                if (bsm == null || !KNOWN_INDY_BOOTSTRAPS.contains(bsmKey)) {
                    flagOpaque("reaches un-attributable invokedynamic via $bsmKey:" +
                            " cone can't be bounded statically")
                    return
                }
                addTypesOfDescriptor(descriptor)
                for (arg in bsmArgs) {
                    when (arg) {
                        is Type -> addType(arg)
                        is Handle -> {
                            addInternal(arg.owner)
                            addTypesOfDescriptor(arg.desc)
                        }
                        else -> {}
                    }
                }
            }

            override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) =
                    addInternal(type)

            override fun visitLocalVariable(name: String?, descriptor: String?, signature: String?,
                                            start: Label?, end: Label?, index: Int) =
                    addTypesOfDescriptor(descriptor)
        }

        private val fieldVisitor = object : FieldVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                addTypesOfDescriptor(descriptor)
                return null
            }
        }

        private val visitor = object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String?, signature: String?,
                               superName: String?, interfaces: Array<String>?) {
                addInternal(superName)
                interfaces?.forEach { addInternal(it) }
            }

            override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?,
                                    value: Any?): FieldVisitor {
                addTypesOfDescriptor(descriptor)
                return fieldVisitor
            }

            override fun visitMethod(access: Int, name: String?, descriptor: String?,
                                     signature: String?, exceptions: Array<String>?): MethodVisitor {
                addTypesOfDescriptor(descriptor)
                exceptions?.forEach { addInternal(it) }
                return methodVisitor
            }

            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                addTypesOfDescriptor(descriptor)
                return null
            }

            override fun visitNestHost(nestHost: String?) = addInternal(nestHost)
            override fun visitNestMember(nestMember: String?) = addInternal(nestMember)
            override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, a: Int) {
                addInternal(name)
                addInternal(outerName)
            }
        }
    }

    /**
     * Indexes a classpath's `.class` files by internal class name → raw bytes (first on the classpath
     * wins, mirroring JVM/JBMC resolution). Both directory and jar entries are read, matching
     * [ContractPurityAudit]'s index so a published consumer's jar'd classes are seen as well as in-repo
     * class dirs. Parsing is eager (read all bytes once); the walk's scans are over the in-memory bytes.
     */
    private class ConeIndex(classpath: String) {
        private val classes = HashMap<String, ByteArray>()

        init {
            for (entry in classpath.split(File.pathSeparator)) {
                if (entry.isEmpty()) {
                    continue
                }
                val p = Path.of(entry)
                try {
                    when {
                        Files.isDirectory(p) -> indexDir(p)
                        Files.isRegularFile(p) && isJar(p) -> indexJar(p)
                        else -> {}
                    }
                } catch (ignored: IOException) {
                    // An unreadable container contributes no classes; a class it would have provided
                    // then resolves to null -> a leaf for the walk. That's an under-approximation of
                    // the cone, which would be unsound — but [compute]'s caller treats a parse/IO
                    // throw as a whole-classpath fallback, and a per-entry read failure here is rare
                    // and bounded. To stay strictly sound, surface it as an error to the caller.
                    throw ConeIndexException(entry)
                } catch (ignored: RuntimeException) {
                    throw ConeIndexException(entry)
                }
            }
        }

        fun contains(internalName: String): Boolean = classes.containsKey(internalName)

        fun bytesOf(internalName: String): ByteArray? = classes[internalName]

        private fun indexDir(dir: Path) {
            Files.walk(dir).use { walk ->
                for (c in Iterable { walk.iterator() }) {
                    if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                        put(Files.readAllBytes(c))
                    }
                }
            }
        }

        private fun indexJar(jar: Path) {
            ZipFile(jar.toFile()).use { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val ze = en.nextElement()
                    if (ze.isDirectory || !ze.name.endsWith(".class")) {
                        continue
                    }
                    zf.getInputStream(ze).use { put(it.readAllBytes()) }
                }
            }
        }

        private fun put(bytes: ByteArray) {
            val name = try {
                ClassReader(bytes).className
            } catch (e: RuntimeException) {
                return
            }
            classes.putIfAbsent(name, bytes)
        }

        private fun isJar(p: Path): Boolean {
            val n = p.fileName.toString().lowercase(Locale.ROOT)
            return n.endsWith(".jar") || n.endsWith(".zip")
        }
    }

    /** Signals an unreadable classpath entry while indexing — caught by [compute] as a fallback. */
    private class ConeIndexException(entry: String) : RuntimeException("unreadable classpath entry: $entry")

    /**
     * The sorted set of resolved cone class names, for diagnostics and stable test assertions. Returns
     * an empty set when the cone is the whole classpath.
     */
    @JvmStatic
    fun coneClasses(entryClass: String, classpath: String?): Set<String> {
        val cone = compute(entryClass, classpath)
        return if (cone.whole || cone.classes == null) emptySet() else TreeSet(cone.classes)
    }
}
