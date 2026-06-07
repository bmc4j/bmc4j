import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm-tree:9.7") }
}

plugins {
    java
    `maven-publish`
}

// This module's MAIN sources live in the java.* packages (JBMC models for JDK types),
// so they're compiled by patching java.base. The resulting classes are only ever
// read by JBMC from the analysis classpath — never loaded by a real JVM (the
// bootstrap loader always wins for java.*), so shipping them is safe.
//
// NOTE: --release is incompatible with --patch-module, so we don't set it here.
val javaSrc = layout.projectDirectory.dir("src/main/java").asFile.absolutePath

// 17 baseline so 17-targeting consumers (e.g. Kotlin 1.9) can resolve this.
// Use source/target (not --release, which is incompatible with --patch-module).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The model-audit annotations (org.bmc4j.models.audit.*) live in this module so the model jar
// carries them with NO new runtime deps — but they are plain org.* classes, NOT java.* models, so
// they compile on a normal classpath (no --patch-module). They are bundled into the same jar and put
// on the main sources' compile classpath so models can reference them.
val audit by sourceSets.creating {
    java.srcDir("src/audit/java")
}

dependencies {
    // The blocking j.u.c models (BlockingQueue.put/take, Semaphore.acquire, CountDownLatch.await)
    // prune their would-block path with org.cprover.CProver.assume — the same primitive Bmc.assume
    // and the engine desugars use. JBMC recognises CProver by FQN and substitutes its assume
    // semantics; the body never runs. compileOnly: needed only to compile against, never shipped on
    // this artifact (the proof plugin already puts bmc-runtime, where CProver lives, on JBMC's
    // analysis classpath). The non-blocking model surface stays pure Java and JVM-runnable.
    compileOnly(project(":bmc-runtime"))
    // The audit annotations are on the models' compile classpath (compileOnly: CLASS-retention, so
    // never needed at runtime) but bundled into the jar via the audit source set output below.
    compileOnly(audit.output)
}

tasks.named<JavaCompile>("compileJava") {
    // --add-reads: the patched java.base must be allowed to read org.cprover.CProver AND the audit
    // annotations, both in the unnamed module (the compileOnly classpath entries above).
    options.compilerArgs.addAll(
        listOf("--patch-module", "java.base=$javaSrc", "--add-reads", "java.base=ALL-UNNAMED"),
    )
}

// Bundle the compiled audit annotations into the main model jar.
tasks.named<Jar>("jar") {
    from(audit.output)
}

// Expose ONLY the compiled audit annotation classes as a consumable artifact, so bmc-kotlin-models
// can put them on its compile classpath WITHOUT pulling the java.* model classes onto it.
val auditAnnotations by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}
artifacts {
    audit.output.classesDirs.files.forEach { dir ->
        add(auditAnnotations.name, dir) { builtBy(tasks.named("compileAuditJava")) }
    }
}

// javadoc categorically refuses to document sources in java.* packages ("package exists in
// another module: java.base"), and this module shadows them BY DESIGN — so the javadoc task
// can never run here. The published javadoc jar remains (Central requires the artifact to
// exist) but is empty by necessity; the real documentation is docs/coverage.md.
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

// ---------------------------------------------------------------------------------------------
// Loud-body synthesis (Upgrade A): give a LOUD-failing body to every member the REAL JDK class has
// but the model lacks AND has accounted for via @BmcNotModelled / @BmcNotNeeded (named) or
// @BmcModelTail (the whole exotic remainder). A synthesized body throws
//   AssertionError("bmc4j: unmodelled member <Class.member> — <reason>")
// so a proof that REACHES an unmodeled member fails NAMED AND LOUD under JBMC instead of silently
// havocking to a nondet stub. Implemented model methods are NEVER touched. Mirrors bmc-kotlin-models'
// build-time ASM rename pass. Reflection-driven: these are java.* models whose real twin is always
// loadable from the bootstrap loader in this build JVM, so we read the real surface + descriptors
// directly (correct return types), rather than guessing from the signature string.
val synthesizeLoudBodies by tasks.registering {
    description = "Synthesize loud-failing bodies for unmodelled (declared/tail) real members."
    val classesDir = tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }
    inputs.dir(classesDir)
    outputs.dir(classesDir)
    doLast {
        synthesizeLoudUnmodelledBodies(classesDir.get().asFile)
    }
}
tasks.named("classes") { dependsOn(synthesizeLoudBodies) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

fun synthesizeLoudUnmodelledBodies(classesDir: File) {
    val notModelled = "Lorg/bmc4j/models/audit/BmcNotModelled;"
    val notModelledList = "Lorg/bmc4j/models/audit/BmcNotModelledList;"
    val notNeeded = "Lorg/bmc4j/models/audit/BmcNotNeeded;"
    val notNeededList = "Lorg/bmc4j/models/audit/BmcNotNeededList;"
    val tail = "Lorg/bmc4j/models/audit/BmcModelTail;"

    // Pre-index every model class by internal name so we can resolve the model inheritance chain: a
    // member implemented by a modeled SUPERCLASS counts as implemented (e.g. the LinkedList model
    // inheriting the ArrayList model's add/get/size) and must NOT be overridden with a loud body.
    val byInternal = HashMap<String, ClassNode>()
    classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { f ->
        val n = ClassNode(); ClassReader(f.readBytes()).accept(n, org.objectweb.asm.ClassReader.SKIP_CODE); byInternal[n.name] = n
    }
    fun inheritedKeys(start: ClassNode): Set<String> {
        val keys = HashSet<String>()
        var cur: ClassNode? = byInternal[start.superName]
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur.name)) {
            for (m in cur.methods) keys.add(m.name + paramsDesc(m.desc))
            cur = byInternal[cur.superName]
        }
        return keys
    }

    classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { classFile ->
        val node = ClassNode()
        ClassReader(classFile.readBytes()).accept(node, 0)

        // Synthesis EXCLUSION: interfaces. Their members are abstract; a loud concrete body can't be
        // attached to an interface method, and it's the concrete impl (e.g. ListStream for the Stream
        // model) that JBMC dispatches to — that impl's tail is what gets the loud body. Skip interfaces.
        if ((node.access and Opcodes.ACC_INTERFACE) != 0) return@forEach

        // Named (member, reason) declarations + optional tail reason.
        data class Decl(val member: String, val reason: String)
        val named = mutableListOf<Decl>()
        var tailReason: String? = null
        fun readDecl(values: List<Any?>?): Decl? {
            if (values == null) return null
            var member: String? = null
            var reason: String? = null
            var i = 0
            while (i + 1 < values.size) {
                when (values[i]) {
                    "member" -> member = values[i + 1] as? String
                    "reason" -> reason = values[i + 1] as? String
                }
                i += 2
            }
            return if (member != null && reason != null) Decl(member, reason) else null
        }
        for (ann in (node.invisibleAnnotations ?: emptyList())) {
            when (ann.desc) {
                notModelled, notNeeded -> readDecl(ann.values)?.let { named.add(it) }
                notModelledList, notNeededList -> {
                    val vals = ann.values ?: continue
                    var j = 0
                    while (j + 1 < vals.size) {
                        if (vals[j] == "value") {
                            @Suppress("UNCHECKED_CAST")
                            (vals[j + 1] as? List<AnnotationNode>)?.forEach { inner ->
                                readDecl(inner.values)?.let { named.add(it) }
                            }
                        }
                        j += 2
                    }
                }
                tail -> {
                    val vals = ann.values ?: continue
                    var j = 0
                    while (j + 1 < vals.size) {
                        if (vals[j] == "reason") tailReason = vals[j + 1] as? String
                        j += 2
                    }
                }
            }
        }
        if (named.isEmpty() && tailReason == null) return@forEach

        // Load the real JDK twin (same FQN — bootstrap loader wins in this build JVM).
        val realName = node.name.replace('/', '.')
        val real = try { Class.forName(realName, false, ClassLoader.getSystemClassLoader()) }
            catch (e: Throwable) { null }

        // Methods the model already implements (own + inherited from modeled superclasses): name +
        // erased param descriptor (return-type agnostic). Inherited ones must never be overridden with
        // a loud body — that would shadow a working inherited implementation.
        val implemented = node.methods.map { it.name + paramsDesc(it.desc) }.toSet() + inheritedKeys(node)
        val className = node.name.replace('/', '.')
        var changed = false

        fun synthesize(name: String, params: List<Type>, ret: Type, memberLabel: String, reason: String) {
            val key = name + paramsKey(params)
            if (implemented.contains(key)) return // never touch implemented methods
            if (node.methods.any { it.name == name && paramsDesc(it.desc) == paramsKey(params) }) return
            val desc = Type.getMethodDescriptor(ret, *params.toTypedArray())
            val mn = MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null)
            // Mark as synthesized so the gate/docs don't count it as a genuine model implementation.
            mn.visitAnnotation("Lorg/bmc4j/models/audit/BmcSynthesizedLoud;", false)
            val msg = "bmc4j: unmodelled member $className.$memberLabel — $reason"
            mn.instructions.apply {
                add(TypeInsnNode(Opcodes.NEW, "java/lang/AssertionError"))
                add(InsnNode(Opcodes.DUP))
                add(LdcInsnNode(msg))
                add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/AssertionError",
                    "<init>", "(Ljava/lang/Object;)V", false))
                add(InsnNode(Opcodes.ATHROW))
            }
            node.methods.add(mn)
            changed = true
        }

        // Named declarations: prefer the real reflective descriptor (correct return type); fall back to
        // a void return when the real class can't be loaded.
        for (d in named) {
            val parsed = parseMemberSignature(d.member) ?: continue
            val (name, params) = parsed
            val ret = realReturnType(real, name, params) ?: Type.VOID_TYPE
            synthesize(name, params, ret, d.member, d.reason)
        }

        // Tail: every real public/protected member the model neither implements nor named, gets a loud
        // body too (so the WHOLE remainder is loud, never a silent stub).
        val tr = tailReason
        if (tr != null && real != null) {
            for (m in realAuditableMethods(real)) {
                val params = m.parameterTypes.map { Type.getType(it) }
                synthesize(m.name, params, Type.getType(m.returnType),
                    m.name + "(" + m.parameterTypes.joinToString(",") { erasedName(it) } + ")", tr)
            }
        }

        if (changed) {
            val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
            node.accept(cw)
            classFile.writeBytes(cw.toByteArray())
        }
    }
}

/** Real public+protected, non-bridge/synthetic instance/static methods (audit surface), minus Object's. */
fun realAuditableMethods(real: Class<*>): List<java.lang.reflect.Method> {
    fun key(m: java.lang.reflect.Method): String =
        m.name + "(" + m.parameterTypes.joinToString(",") { p -> p.name } + ")"
    val objectKeys = Any::class.java.methods.map { key(it) }.toSet()
    return real.methods
        .filter { !it.isBridge && !it.isSynthetic }
        .filter { key(it) !in objectKeys }
}

fun realReturnType(real: Class<*>?, name: String, params: List<Type>): Type? {
    if (real == null) return null
    val want = paramsKey(params)
    val m = real.methods.firstOrNull { it.name == name && paramsKey(it.parameterTypes.map { p -> Type.getType(p) }) == want }
    return m?.let { Type.getType(it.returnType) }
}

fun erasedName(c: Class<*>): String = if (c.isArray) erasedName(c.componentType) + "[]" else (c.canonicalName ?: c.name)

/** name + erased-param descriptor of a method descriptor, e.g. "(ILjava/lang/Object;)" — return-agnostic. */
fun paramsDesc(methodDesc: String): String = "(" + Type.getArgumentTypes(methodDesc).joinToString("") { it.descriptor } + ")"
fun paramsKey(params: List<Type>): String = "(" + params.joinToString("") { it.descriptor } + ")"

/** Parse an erased member signature `name(p1,p2,...)` into (name, list<asm Type>). */
fun parseMemberSignature(sig: String): Pair<String, List<Type>>? {
    val open = sig.indexOf('(')
    val close = sig.lastIndexOf(')')
    if (open < 0 || close < open) return null
    val name = sig.substring(0, open).trim()
    val params = sig.substring(open + 1, close).trim()
    val types = if (params.isEmpty()) emptyList() else params.split(',').map { erasedTypeToAsm(it.trim()) }
    if (types.any { it == null }) return null
    @Suppress("UNCHECKED_CAST")
    return name to (types as List<Type>)
}

/** Map an erased source type form to an ASM Type. Supports primitives, FQ reference types, and []. */
fun erasedTypeToAsm(s: String): Type? {
    if (s.isEmpty()) return null
    if (s.endsWith("[]")) {
        val elem = erasedTypeToAsm(s.removeSuffix("[]")) ?: return null
        return Type.getType("[" + elem.descriptor)
    }
    return when (s) {
        "int" -> Type.INT_TYPE
        "long" -> Type.LONG_TYPE
        "boolean" -> Type.BOOLEAN_TYPE
        "byte" -> Type.BYTE_TYPE
        "char" -> Type.CHAR_TYPE
        "short" -> Type.SHORT_TYPE
        "float" -> Type.FLOAT_TYPE
        "double" -> Type.DOUBLE_TYPE
        "void" -> Type.VOID_TYPE
        else -> Type.getObjectType(s.replace('.', '/'))
    }
}
