package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * The PER-MEMBER model auditing gate. For every concrete java.* model the real JDK class and the
 * relocated model load side by side; the gate enumerates the real class's public/protected surface
 * (filtering bridge/synthetic members and Object's universal methods) and requires EACH member to be:
 *
 *  - implemented by the model AND carrying {@code @BmcModelConforms} (resolved through the model's
 *    inheritance chain — a member implemented by a modeled superclass counts), or
 *  - waived by a {@code @BmcUnmodelable} (loud-if-reached — the body must throw the recognized failure)
 *    or {@code @BmcNotNeeded} (green-if-reached — the unmodeled real/inline path is sound under JBMC, so
 *    no loud body is required), method-level OR class-level ({@code member=…}, each repeatable), or
 *  - absorbed by a class-level {@code @BmcModelTail} (the exotic remainder; the build-time synthesis
 *    pass gives every such member a LOUD body, never a silent stub).
 *
 * An undeclared real member FAILS the build naming class+member. The gate also fails on:
 *  - a dangling declaration (an Unmodelable/NotNeeded names a member the real class lacks — typo / JDK drift),
 *  - an implemented model method with no {@code @BmcModelConforms} (every model member must be an
 *    explicit, audited decision),
 *  - a registered model class that bears ZERO audit annotations (a new model can't silently skip).
 *
 * Pristine, NON-registered, NON-annotated classes produce a single aggregate warning. Post-migration
 * that list is empty.
 */
class ModelAuditGateTest : FunSpec({

    val auditPkg = "org/bmc4j/models/audit/"
    val conformsDesc = "L${auditPkg}BmcModelConforms;"
    val unmodelableDesc = "L${auditPkg}BmcUnmodelable;"          // loud-if-reached (method + class-level + list)
    val unmodelableListDesc = "L${auditPkg}BmcUnmodelableList;"
    val notNeededDesc = "L${auditPkg}BmcNotNeeded;"              // green-if-reached (no loud stub required)
    val notNeededListDesc = "L${auditPkg}BmcNotNeededList;"
    val tailDesc = "L${auditPkg}BmcModelTail;"

    // ---- load every model class node from the relocated jar -------------------------------------
    val jarPath = System.getProperty("java.class.path").split(File.pathSeparatorChar)
        .firstOrNull { it.replace('\\', '/').endsWith("bmcref-models.jar") }
        ?: error("relocated models jar not found on the test classpath")

    // realFqn -> ClassNode (model bytecode, with bmcref. stripped from the class name for keying).
    val nodes = LinkedHashMap<String, ClassNode>()
    JarFile(jarPath).use { jar ->
        jar.entries().asSequence()
            .filter { it.name.endsWith(".class") && !it.name.endsWith("package-info.class") }
            .filter { !it.name.contains('$') } // skip nested/inner/anon — not independently-registered models
            .forEach { e ->
                val node = ClassNode()
                ClassReader(jar.getInputStream(e).readBytes()).accept(node, 0)
                val realFqn = node.name.removePrefix("bmcref/").replace('/', '.')
                nodes[realFqn] = node
            }
    }

    fun anns(node: ClassNode): List<AnnotationNode> =
        (node.invisibleAnnotations ?: emptyList()) + (node.visibleAnnotations ?: emptyList())

    fun hasAnyAuditAnnotation(node: ClassNode): Boolean {
        if (anns(node).any { it.desc.startsWith("L$auditPkg") }) return true
        // also a method-level @BmcModelConforms counts as "opted in"
        return node.methods.any { m ->
            ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList()))
                .any { it.desc == conformsDesc }
        }
    }

    // ---- declaration readers --------------------------------------------------------------------
    data class Decl(val member: String, val reason: String, val kind: String)
    fun readDecl(values: List<Any?>?, kind: String): Decl? {
        if (values == null) return null
        var member: String? = null; var reason: String? = null
        var i = 0
        while (i + 1 < values.size) {
            when (values[i]) { "member" -> member = values[i + 1] as? String; "reason" -> reason = values[i + 1] as? String }
            i += 2
        }
        return if (member != null && reason != null) Decl(member, reason, kind) else null
    }
    // CLASS-LEVEL declarations are @BmcUnmodelable(member=) (loud-if-reached) OR @BmcNotNeeded(member=)
    // (green-if-reached), each repeatable via its *List container. Both account for the named member; the
    // loud-vs-green distinction is enforced on the METHOD-level stub body, not on class-level declarations
    // (a class-level declaration cannot carry a body to check).
    fun declarations(node: ClassNode): List<Decl> {
        val out = mutableListOf<Decl>()
        fun readList(ann: AnnotationNode, kind: String) {
            val vals = ann.values ?: return
            var j = 0
            while (j + 1 < vals.size) {
                if (vals[j] == "value") {
                    @Suppress("UNCHECKED_CAST")
                    (vals[j + 1] as? List<AnnotationNode>)?.forEach { inner -> readDecl(inner.values, kind)?.let { out.add(it) } }
                }
                j += 2
            }
        }
        for (ann in anns(node)) {
            when (ann.desc) {
                unmodelableDesc -> readDecl(ann.values, "Unmodelable")?.let { out.add(it) }
                unmodelableListDesc -> readList(ann, "Unmodelable")
                notNeededDesc -> readDecl(ann.values, "NotNeeded")?.let { out.add(it) }
                notNeededListDesc -> readList(ann, "NotNeeded")
            }
        }
        return out
    }
    fun tailReason(node: ClassNode): String? {
        for (ann in anns(node)) if (ann.desc == tailDesc) {
            val vals = ann.values ?: continue
            var j = 0
            while (j + 1 < vals.size) { if (vals[j] == "reason") return vals[j + 1] as? String; j += 2 }
        }
        return null
    }

    // ---- method-level Unmodelable / NotNeeded stubs (the primary form) --------------------------
    // The decision lives ON a real stub method whose loud body throws the recognized message. The gate
    // accounts for the method's own key as declared, never requires @BmcModelConforms on it, never
    // counts it as modeled, and verifies its body actually throws via the BmcUnmodelledReached sentinel.
    fun methodAnns(m: MethodNode): List<AnnotationNode> =
        (m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())

    fun stubKind(m: MethodNode): String? = when {
        methodAnns(m).any { it.desc == unmodelableDesc } -> "Unmodelable"
        methodAnns(m).any { it.desc == notNeededDesc } -> "NotNeeded"
        else -> null
    }

    /** Keys (name+params) of this class's OWN method-level Unmodelable/NotNeeded stub methods. */
    fun methodLevelStubKeys(node: ClassNode): Set<String> =
        node.methods.filter { stubKind(it) != null }.map { it.name + paramsDesc(it.desc) }.toSet()

    /**
     * Method-level stub keys resolved UP the modeled superclass chain — the stub-side analogue of
     * [conformsKeys]. A subclass model (e.g. ZoneOffset) inherits its modeled super's (ZoneId's)
     * per-member loud stubs, so they account for the inherited real members even though the stub
     * declaration lives on the super. Mirrors TailSet.stubKeys so the tail ratchet and this completeness
     * check can't disagree.
     */
    fun methodLevelStubKeysChain(realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) if (stubKind(m) != null) out.add(m.name + paramsDesc(m.desc))
            val superReal = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (superReal != null && nodes.containsKey(superReal)) nodes[superReal] else null
        }
        return out
    }

    // The loud-body recognizer: a stub's body must (a) LDC a string starting with the recognized
    // prefix, and (b) call the BmcUnmodelledReached sentinel (fail/reached). This is exactly what makes
    // a reach demote to UNKNOWN naming the member; checking it here prevents real logic hiding under a
    // not-modeled annotation, or a stub that throws without the recognized signature.
    val loudPrefix = "bmc4j: unmodelled member "
    val sentinelOwner = "org/bmc4j/analysis/BmcUnmodelledReached"
    fun bodyThrowsRecognizedLoud(m: MethodNode): Boolean {
        val insns = m.instructions ?: return false
        var hasPrefixLdc = false
        var callsSentinel = false
        for (insn in insns) {
            if (insn is LdcInsnNode) {
                val c = insn.cst
                if (c is String && c.startsWith(loudPrefix)) hasPrefixLdc = true
            }
            if (insn is MethodInsnNode && insn.owner == sentinelOwner &&
                (insn.name == "fail" || insn.name == "reached")) callsSentinel = true
        }
        return hasPrefixLdc && callsSentinel
    }

    // model member keys that COUNT AS CONFORMING, resolved through the model inheritance chain. A
    // member conforms iff it carries a method-level @BmcModelConforms (there is no class-level
    // "blanket" form anymore — every conforming member is pinned individually).
    // key = name + erased-param-desc (return-agnostic). Constructors excluded (audited via real ctors
    // separately if ever needed; the real-member surface enumerates methods, not <init>).
    val synthesizedDesc = "L${auditPkg}BmcSynthesizedLoud;"
    fun isSynthesizedLoud(m: org.objectweb.asm.tree.MethodNode): Boolean =
        ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())).any { it.desc == synthesizedDesc }

    fun conformsKeys(realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) {
                if (m.name == "<clinit>" || m.name == "<init>") continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                if (isSynthesizedLoud(m)) continue // a loud stub is NOT a genuine model implementation
                if (stubKind(m) != null) continue   // a method-level Unmodelable/NotNeeded stub is NOT modeled
                val methodConforms = ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList()))
                    .any { it.desc == conformsDesc }
                if (methodConforms) out.add(m.name + paramsDesc(m.desc))
            }
            // walk to modeled superclass (bmcref-prefixed); stop at non-model supers.
            val superReal = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (superReal != null && nodes.containsKey(superReal)) nodes[superReal] else null
        }
        return out
    }

    // all implemented (name+params) on the model class itself + modeled supers (for "implemented but
    // unannotated" reporting we only inspect the class's OWN methods, see below).
    val objectKeys = java.lang.Object::class.java.methods
        .map { it.name + "(" + it.parameterTypes.joinToString("") { p -> Type.getType(p).descriptor } + ")" }.toSet()

    fun realAuditableKeys(real: Class<*>): Map<String, java.lang.reflect.Method> {
        val out = LinkedHashMap<String, java.lang.reflect.Method>()
        for (m in real.methods) {
            if (m.isBridge || m.isSynthetic) continue
            val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
            if (key in objectKeys) continue
            out.putIfAbsent(key, m)
        }
        // protected methods aren't in getMethods(); add declared protected ones up the hierarchy.
        var c: Class<*>? = real
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.isBridge || m.isSynthetic) continue
                if (!Modifier.isProtected(m.modifiers)) continue
                val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
                if (key in objectKeys) continue
                out.putIfAbsent(key, m)
            }
            c = c.superclass
        }
        return out
    }

    fun declKey(member: String): String? {
        val open = member.indexOf('('); val close = member.lastIndexOf(')')
        if (open < 0 || close < open) return null
        val name = member.substring(0, open).trim()
        val params = member.substring(open + 1, close).trim()
        val types = if (params.isEmpty()) emptyList() else params.split(',').map { erasedTypeToDescriptor(it.trim()) }
        if (types.any { it == null }) return null
        return name + "(" + types.joinToString("") + ")"
    }

    test("every per-member-enforced model accounts for its real class's full surface") {
        val failures = mutableListOf<String>()

        for (realFqn in PER_MEMBER_ENFORCED.sorted()) {
            withClue("model $realFqn is registered for per-member enforcement but is not in the relocated jar") {
                nodes.containsKey(realFqn) shouldBe true
            }
            val node = nodes[realFqn] ?: continue
            val real = try { Class.forName(realFqn) } catch (e: Throwable) {
                failures.add("$realFqn: real class not loadable for the gate (${e.javaClass.simpleName})"); continue
            }

            // (a) registered class must bear at least one audit annotation.
            if (!hasAnyAuditAnnotation(node)) {
                failures.add("$realFqn: registered for per-member auditing but carries NO audit annotations")
            }

            val decls = declarations(node)                  // class-level @BmcNotNeeded(member=) declarations
            val methodStubKeys = methodLevelStubKeys(node)   // method-level Unmodelable/NotNeeded stubs
            val tail = tailReason(node)
            val realMembers = realAuditableKeys(real)

            // (b) dangling CLASS-LEVEL declarations: a named member must exist on the real class.
            for (d in decls) {
                val key = declKey(d.member)
                if (key == null) { failures.add("$realFqn: @Bmc${d.kind} member='${d.member}' is not a parseable signature"); continue }
                if (!realMembers.containsKey(key)) {
                    failures.add("$realFqn: dangling @Bmc${d.kind}(member=\"${d.member}\") — the real class has no such member (typo / JDK drift)")
                }
            }

            // (b2) every method-level Unmodelable/NotNeeded stub (1) must mirror a REAL member (else it's
            // a typo / JDK drift), and (2) — for @BmcUnmodelable ONLY (loud-if-reached) — its body must
            // actually throw the recognized loud failure via the BmcUnmodelledReached sentinel, so no real
            // logic hides under an unmodelable annotation. @BmcNotNeeded is GREEN/documentary: it accounts
            // for the member but its body is NOT required to throw (the unmodeled real/inline path is sound).
            for (m in node.methods) {
                // Skip compiler-generated bridge/synthetic methods: a covariant-return override (e.g. a
                // model's `with(TemporalField,long): ChronoLocalDate` narrowing Temporal's
                // `: Temporal`) makes javac emit a bridge with the SAME name+params that javac may also
                // copy the source annotations onto. The bridge just forwards to the real stub; it is
                // never a hand-written stub body, so it must not be subjected to the loud-body check.
                if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                val kind = stubKind(m) ?: continue
                val key = m.name + paramsDesc(m.desc)
                if (!realMembers.containsKey(key)) {
                    failures.add("$realFqn: @Bmc$kind stub ${m.name}${paramsDesc(m.desc)} mirrors no real member (typo / JDK drift)")
                }
                if (kind == "Unmodelable" && !bodyThrowsRecognizedLoud(m)) {
                    failures.add("$realFqn: @Bmc$kind stub ${m.name}${paramsDesc(m.desc)} body does not throw the recognized " +
                        "loud failure (must `throw fail(\"bmc4j: unmodelled member …\")` via BmcUnmodelledReached) — " +
                        "no real logic may hide under an unmodelable (loud-if-reached) annotation")
                }
            }

            // (c) implemented-but-unannotated: every OWN public/protected model method that mirrors a
            // real member must be accounted for — covered by its own method-level @BmcModelConforms
            // OR be a method-level Unmodelable/NotNeeded stub. Constructors and bridge/synthetic excluded.
            val conformsOwn = node.methods.filter { m ->
                ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())).any { it.desc == conformsDesc }
            }.map { it.name + paramsDesc(it.desc) }.toSet()
            for (m in node.methods) {
                if (m.name == "<init>" || m.name == "<clinit>") continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                if (isSynthesizedLoud(m)) continue             // a build-synthesized loud tail stub is NOT a model impl
                val isPublic = (m.access and org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0
                val isProtected = (m.access and org.objectweb.asm.Opcodes.ACC_PROTECTED) != 0
                if (!isPublic && !isProtected) continue
                val key = m.name + paramsDesc(m.desc)
                // Only methods that mirror a REAL member (model-internal helpers aren't part of the audit).
                if (!realMembers.containsKey(key)) continue
                if (key in methodStubKeys) continue            // an Unmodelable/NotNeeded stub: accounted for
                if (key in conformsOwn) continue               // modeled + conforming (own method-level annotation)
                failures.add("$realFqn: implemented model member ${m.name}${paramsDesc(m.desc)} lacks @BmcModelConforms")
            }

            // (d) completeness: every real member must be covered/declared/tailed.
            val covered = conformsKeys(realFqn)
            val declared = decls.mapNotNull { declKey(it.member) }.toSet()
            // method-level stubs resolved up the modeled superclass chain, so a subclass model inherits
            // its modeled super's per-member loud stubs (e.g. ZoneOffset inherits ZoneId's region stubs).
            val stubKeysChain = methodLevelStubKeysChain(realFqn)
            for ((key, m) in realMembers) {
                if (key in covered) continue
                if (key in declared) continue
                if (key in stubKeysChain) continue             // method-level loud stub (own or inherited) accounts for it
                if (tail != null) continue
                failures.add("$realFqn: real member ${render(m)} is neither modeled (@BmcModelConforms), declared (@BmcUnmodelable/@BmcNotNeeded), nor tail-waived (@BmcModelTail)")
            }
        }

        withClue("MODEL AUDITING GATE — unaccounted members / mis-annotations (${failures.size}):\n  " +
            failures.sorted().joinToString("\n  ")) {
            failures.isEmpty() shouldBe true
        }
    }

    // ---- MUTUAL EXCLUSIVITY of the four classification annotations -------------------------------
    // The four audit classifications are MUTUALLY EXCLUSIVE per member: a member is EXACTLY ONE of
    //   @BmcModelConforms (modeled + conforming), @BmcUnmodelable (can't be modeled, loud-if-reached
    //   stub), @BmcNotNeeded (no model needed, green-if-reached — the real/inline path is sound), or
    //   @BmcModelTail (build-synthesized loud tail).
    // Carrying two is contradictory (e.g. @BmcUnmodelable + @BmcModelConforms claims a member is BOTH a
    // loud unmodelable stub AND a conforming model). The gate counts, per member, how many of the four
    // are present and FAILS naming the member FQN + the conflicting annotations.
    //
    // Scope: @BmcModelConforms is method-only; @BmcUnmodelable and @BmcNotNeeded are method-or-class
    // (repeatable via BmcUnmodelableList / BmcNotNeededList); @BmcModelTail is class-only. The
    // contradiction surfaces ON A METHOD (a method carrying ≥2 of {Conforms, Unmodelable, NotNeeded}),
    // so we count per method. Bridge/synthetic methods are SKIPPED (PR #150): a covariant-return bridge
    // carries the source method's duplicated annotations but is not a hand-written decision, so it must
    // not false-positive.
    //
    // descriptors of the four mutually-exclusive classification annotations → display names, in a
    // stable order. Shared by the gate scan and the focused kernel unit tests below.
    val classifierDescs = linkedMapOf(
        conformsDesc to "@BmcModelConforms",
        unmodelableDesc to "@BmcUnmodelable",
        notNeededDesc to "@BmcNotNeeded",
        tailDesc to "@BmcModelTail",
    )

    test("the four classification annotations are mutually exclusive on every model member") {
        val conflicts = mutableListOf<String>()

        for ((realFqn, node) in nodes) {
            for (m in node.methods) {
                if (m.name == "<init>" || m.name == "<clinit>") continue
                // Skip compiler-generated bridge/synthetic methods (PR #150's bridge-skip): a
                // covariant-return override makes javac emit a bridge with the SAME name+params onto
                // which the source annotations may be copied. The bridge is not a hand-written
                // decision, so a duplicated pair on it must not be reported as a contradiction.
                if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                val carried = mutualExclusivityConflict(methodAnns(m).map { it.desc }, classifierDescs)
                if (carried.size > 1) {
                    conflicts.add("$realFqn#${m.name}${paramsDesc(m.desc)}: carries ${carried.size} " +
                        "mutually-exclusive classification annotations ${carried.joinToString(" + ")} " +
                        "(a member must be EXACTLY ONE classification)")
                }
            }
        }

        withClue("MODEL AUDITING GATE — members carrying MORE THAN ONE mutually-exclusive classification " +
            "annotation (${conflicts.size}); each member must be exactly one of @BmcModelConforms / " +
            "@BmcUnmodelable / @BmcNotNeeded / @BmcModelTail:\n  " + conflicts.sorted().joinToString("\n  ")) {
            conflicts.isEmpty() shouldBe true
        }
    }

    // ---- FOCUSED unit tests proving the mutual-exclusivity check BITES --------------------------
    // Hand-built annotation sets over the pure kernel: a double-annotated member is flagged (size > 1),
    // a single-annotated member passes (size == 1), and the @Repeatable container collapses correctly.
    test("mutual-exclusivity kernel: a member with two classification annotations is flagged") {
        // a member cannot be BOTH a loud unmodelable stub AND a conforming model.
        val carried = mutualExclusivityConflict(listOf(unmodelableDesc, conformsDesc), classifierDescs)
        carried shouldBe listOf("@BmcModelConforms", "@BmcUnmodelable")
        (carried.size > 1) shouldBe true
    }

    test("mutual-exclusivity kernel: a member with one classification annotation passes") {
        mutualExclusivityConflict(listOf(conformsDesc), classifierDescs).size shouldBe 1
        mutualExclusivityConflict(listOf(unmodelableDesc), classifierDescs).size shouldBe 1
        // a non-classification annotation alongside a single classifier does NOT count as a conflict.
        mutualExclusivityConflict(listOf(conformsDesc, "Lorg/bmc4j/models/audit/BmcSynthesizedLoud;"),
            classifierDescs).size shouldBe 1
        // no classification annotation at all → nothing carried.
        mutualExclusivityConflict(emptyList(), classifierDescs).size shouldBe 0
    }

    test("mutual-exclusivity kernel: the @Repeatable containers collapse to one presence") {
        // a member with multiple @BmcNotNeeded / @BmcUnmodelable carries its *List container, not the
        // bare annotation — each must still count as exactly ONE classification, never zero.
        mutualExclusivityConflict(listOf(notNeededListDesc), classifierDescs) shouldBe listOf("@BmcNotNeeded")
        mutualExclusivityConflict(listOf(unmodelableListDesc), classifierDescs) shouldBe listOf("@BmcUnmodelable")
        // container alongside a second classifier IS a conflict.
        (mutualExclusivityConflict(listOf(notNeededListDesc, conformsDesc), classifierDescs).size > 1) shouldBe true
        (mutualExclusivityConflict(listOf(unmodelableListDesc, conformsDesc), classifierDescs).size > 1) shouldBe true
    }

    // ---- POLARITY: the gate distinguishes the loud-if-reached vs green-if-reached buckets ----------
    // The loud-body recognizer (bodyThrowsRecognizedLoud) is what the gate uses to enforce that an
    // @BmcUnmodelable stub actually diverts to the BmcUnmodelledReached sentinel. These two tests pin the
    // OPPOSITE reach-semantics the taxonomy split introduces, using hand-built MethodNodes (no fixture
    // jar): an Unmodelable stub whose body does NOT throw is rejected (loud-stub enforced); a NotNeeded
    // stub whose body does NOT throw is accepted (green: no loud stub demanded).
    fun stubBodyPasses(annDesc: String, throws: Boolean): Boolean {
        val m = MethodNode(org.objectweb.asm.Opcodes.ACC_PUBLIC, "sample", "()V", null, null)
        m.visitAnnotation(annDesc, false)
        if (throws) {
            m.instructions.add(LdcInsnNode("bmc4j: unmodelled member sample()"))
            m.instructions.add(MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                "org/bmc4j/analysis/BmcUnmodelledReached", "fail", "(Ljava/lang/String;)Ljava/lang/AssertionError;", false))
            m.instructions.add(org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ATHROW))
        } else {
            m.instructions.add(org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN))
        }
        // Mirror the gate's (b2) rule: the loud body is required for Unmodelable only.
        val kind = stubKind(m) ?: return false
        return kind != "Unmodelable" || bodyThrowsRecognizedLoud(m)
    }

    test("polarity: an @BmcUnmodelable stub that does NOT throw the loud failure FAILS the gate") {
        stubBodyPasses(unmodelableDesc, throws = false) shouldBe false
        // a properly-loud @BmcUnmodelable stub still passes.
        stubBodyPasses(unmodelableDesc, throws = true) shouldBe true
    }

    test("polarity: an @BmcNotNeeded stub that does NOT throw the loud failure PASSES the gate (green)") {
        stubBodyPasses(notNeededDesc, throws = false) shouldBe true
        // a green @BmcNotNeeded stub may also throw and still passes (no loud body demanded either way).
        stubBodyPasses(notNeededDesc, throws = true) shouldBe true
    }

    test("annotated-but-not-registered models are still audited; pristine non-registered models warn") {
        val pristine = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for ((realFqn, node) in nodes) {
            if (realFqn in PER_MEMBER_ENFORCED) continue
            val annotated = hasAnyAuditAnnotation(node)
            val real = try { Class.forName(realFqn) } catch (e: Throwable) { null }
            if (annotated && real != null) {
                // audit the annotations it DOES carry (dangling-declaration check), without demanding
                // whole-surface completeness (facades/value-classes/enums opt into per-member as they grow).
                val realMembers = realAuditableKeys(real)
                for (d in declarations(node)) {
                    val key = declKey(d.member)
                    if (key == null) { failures.add("$realFqn: @Bmc${d.kind} member='${d.member}' not parseable"); continue }
                    if (!realMembers.containsKey(key)) {
                        failures.add("$realFqn: dangling @Bmc${d.kind}(member=\"${d.member}\")")
                    }
                }
            } else if (!annotated && realFqn !in WAIVED && realFqn !in COVERED_NO_OWN_SURFACE) {
                // A REGISTERED (COVERED) model that bears no audit annotations FAILS — a registered model
                // can't silently skip auditing. A genuinely-new, non-registered, non-waived model only
                // WARNS here (CoverageGateTest is what fails on an unregistered new model). Models in
                // COVERED_NO_OWN_SURFACE are exempt: they have no own member to pin @BmcModelConforms on.
                if (realFqn in COVERED) {
                    failures.add("$realFqn: registered (COVERED) but carries NO audit annotation — annotate each " +
                        "conforming member with a method-level @BmcModelConforms (or, if it has no own auditable " +
                        "surface, add it to COVERED_NO_OWN_SURFACE with a reason)")
                } else {
                    pristine.add(realFqn)
                }
            }
        }
        if (pristine.isNotEmpty()) {
            println("MODEL AUDITING — pristine (un-annotated, non-registered, non-waived) model classes — " +
                "annotate to bring them under per-member auditing:\n  " + pristine.sorted().joinToString("\n  "))
        }
        withClue("model auditing — registered-but-unannotated models and mis-annotations:\n  ${failures.sorted().joinToString("\n  ")}") {
            failures.isEmpty() shouldBe true
        }
    }

    // ---- Tail no-growth RATCHET -----------------------------------------------------------------
    // New real-class surface (e.g. a JDK bump) must NOT silently fall into the tail. The committed,
    // generator-maintained docs/model-coverage-tail.txt enumerates EXACTLY today's tail; the gate
    // diffs the actual tail set against it and FAILS naming any member that newly appeared — forcing
    // an explicit decision (model it, stub it with a reason, or regenerate the file to accept it).
    // Converting/removing a tail member just regenerates the file. Regenerate with -Dbmc.regenerateDocs=true.
    test("tail no-growth ratchet: the committed tail enumeration matches the actual tail set") {
        val actual = TailSet.compute(nodes)
        val tailFile = File("../../docs/model-coverage-tail.txt").absoluteFile
        val regenerate = System.getProperty("bmc.regenerateDocs") == "true"

        val header = "# GENERATED tail enumeration — every real member that falls through to @BmcModelTail.\n" +
            "# The tail no-growth ratchet (conformance.ModelAuditGateTest) diffs the actual tail against this.\n" +
            "# Do NOT edit by hand: regenerate with\n" +
            "#   gradlew -p core :bmc-models-conformance:test --tests conformance.ModelAuditGateTest -Dbmc.regenerateDocs=true\n"
        val rendered = header + actual.joinToString("\n") + "\n"

        if (regenerate || !tailFile.exists()) {
            tailFile.parentFile.mkdirs()
            tailFile.writeText(rendered)
        }

        val committedLines = tailFile.readText().replace("\r\n", "\n").lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }.toCollection(LinkedHashSet())
        val actualSet = actual.toCollection(LinkedHashSet())
        val newlyTailed = (actualSet - committedLines).sorted()
        val noLongerTailed = (committedLines - actualSet).sorted()

        withClue("TAIL NO-GROWTH RATCHET — the tail set drifted from docs/model-coverage-tail.txt.\n" +
            (if (newlyTailed.isNotEmpty()) "  NEW members fell into the tail (decide explicitly — model it, " +
                "stub it with a reason, or regenerate to accept):\n    " + newlyTailed.joinToString("\n    ") + "\n" else "") +
            (if (noLongerTailed.isNotEmpty()) "  members LEFT the tail (now modeled/declared — just regenerate):\n    " +
                noLongerTailed.joinToString("\n    ") + "\n" else "") +
            "  regenerate with -Dbmc.regenerateDocs=true and commit") {
            (newlyTailed.isEmpty() && noLongerTailed.isEmpty()) shouldBe true
        }
    }
})

// Model-method param descriptor, NORMALIZED back from the relocation: a model's own param types are
// relocated (bmcref/java/util/Collection), but the real member's are not (java/util/Collection) — strip
// the bmcref/ prefix so a model-method key matches the reflection-derived real-member key. (Without
// this, every model method taking another model type as a param would spuriously look like a different
// member than its real twin.)
private fun paramsDesc(methodDesc: String): String =
    "(" + Type.getArgumentTypes(methodDesc).joinToString("") { it.descriptor.replace("Lbmcref/", "L") } + ")"

// The @Repeatable containers (multiple @BmcNotNeeded / @BmcUnmodelable on one member collapse to these).
private const val NOT_NEEDED_LIST_DESC = "Lorg/bmc4j/models/audit/BmcNotNeededList;"
private const val UNMODELABLE_LIST_DESC = "Lorg/bmc4j/models/audit/BmcUnmodelableList;"

/**
 * Pure mutual-exclusivity kernel (extracted so it is unit-testable in isolation, without needing a
 * fixture model on the relocated jar). Given the annotation descriptors present on ONE member and the
 * ordered map of the four classification-annotation descriptors → display names, returns the display
 * names of the classification annotations the member carries — preserving the map's order. A returned
 * list of size > 1 is a contradiction (a member must be EXACTLY ONE classification).
 *
 * Normalizes each @Repeatable container ({@code BmcNotNeededList}, {@code BmcUnmodelableList}) to its
 * single bare-annotation presence, so the list form counts the same as the bare annotation.
 */
private fun mutualExclusivityConflict(
    presentDescs: List<String>,
    classifierDescs: Map<String, String>,
): List<String> {
    val notNeededDesc = "Lorg/bmc4j/models/audit/BmcNotNeeded;"
    val unmodelableDesc = "Lorg/bmc4j/models/audit/BmcUnmodelable;"
    val present = presentDescs
        .map {
            when (it) {
                NOT_NEEDED_LIST_DESC -> notNeededDesc
                UNMODELABLE_LIST_DESC -> unmodelableDesc
                else -> it
            }
        }
        .toSet()
    return classifierDescs.filterKeys { it in present }.values.toList()
}

private fun render(m: java.lang.reflect.Method): String =
    m.name + "(" + m.parameterTypes.joinToString(",") { erasedName(it) } + ")"

private fun erasedName(c: Class<*>): String =
    if (c.isArray) erasedName(c.componentType) + "[]" else (c.canonicalName ?: c.name)

private fun erasedTypeToDescriptor(s: String): String? {
    if (s.isEmpty()) return null
    if (s.endsWith("[]")) { val e = erasedTypeToDescriptor(s.removeSuffix("[]")) ?: return null; return "[$e" }
    return when (s) {
        "int" -> "I"; "long" -> "J"; "boolean" -> "Z"; "byte" -> "B"; "char" -> "C"
        "short" -> "S"; "float" -> "F"; "double" -> "D"; "void" -> "V"
        else -> "L" + s.replace('.', '/') + ";"
    }
}
