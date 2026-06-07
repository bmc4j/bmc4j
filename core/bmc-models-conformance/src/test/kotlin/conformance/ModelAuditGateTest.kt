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
 *  - waived by a method-level {@code @BmcNotModelled} / {@code @BmcNotNeeded} loud stub, or a
 *    class-level {@code @BmcNotNeeded(member=…)} declaration ({@code @BmcNotModelled} is method-only —
 *    it has no class-level form), or
 *  - absorbed by a class-level {@code @BmcModelTail} (the exotic remainder; the build-time synthesis
 *    pass gives every such member a LOUD body, never a silent stub).
 *
 * An undeclared real member FAILS the build naming class+member. The gate also fails on:
 *  - a dangling declaration (a NotModelled/NotNeeded names a member the real class lacks — typo / JDK drift),
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
    val notModelledDesc = "L${auditPkg}BmcNotModelled;"  // method-level only (no class-level / list form)
    val notNeededDesc = "L${auditPkg}BmcNotNeeded;"
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
    // CLASS-LEVEL declarations are @BmcNotNeeded only. @BmcNotModelled is METHOD-only (its TYPE target
    // was removed — class-level use was being misused to blanket-exempt whole classes), so it never
    // appears here; not-modeled waivers are method-level loud stubs (see methodLevelStubKeys).
    fun declarations(node: ClassNode): List<Decl> {
        val out = mutableListOf<Decl>()
        for (ann in anns(node)) {
            when (ann.desc) {
                notNeededDesc -> readDecl(ann.values, "NotNeeded")?.let { out.add(it) }
                notNeededListDesc -> {
                    val vals = ann.values ?: continue
                    var j = 0
                    while (j + 1 < vals.size) {
                        if (vals[j] == "value") {
                            @Suppress("UNCHECKED_CAST")
                            (vals[j + 1] as? List<AnnotationNode>)?.forEach { inner -> readDecl(inner.values, "NotNeeded")?.let { out.add(it) } }
                        }
                        j += 2
                    }
                }
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

    // ---- method-level NotModelled / NotNeeded stubs (the revision-2 primary form) ---------------
    // The decision lives ON a real stub method whose loud body throws the recognized message. The gate
    // accounts for the method's own key as declared, never requires @BmcModelConforms on it, never
    // counts it as modeled, and verifies its body actually throws via the BmcUnmodelledReached sentinel.
    fun methodAnns(m: MethodNode): List<AnnotationNode> =
        (m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())

    fun stubKind(m: MethodNode): String? = when {
        methodAnns(m).any { it.desc == notModelledDesc } -> "NotModelled"
        methodAnns(m).any { it.desc == notNeededDesc } -> "NotNeeded"
        else -> null
    }

    /** Keys (name+params) of this class's OWN method-level NotModelled/NotNeeded stub methods. */
    fun methodLevelStubKeys(node: ClassNode): Set<String> =
        node.methods.filter { stubKind(it) != null }.map { it.name + paramsDesc(it.desc) }.toSet()

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
                if (stubKind(m) != null) continue   // a method-level NotModelled/NotNeeded stub is NOT modeled
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
            val methodStubKeys = methodLevelStubKeys(node)   // method-level NotModelled/NotNeeded stubs
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

            // (b2) every method-level NotModelled/NotNeeded stub (1) must mirror a REAL member (else it's
            // a typo / JDK drift), and (2) its body must actually throw the recognized loud failure via
            // the BmcUnmodelledReached sentinel — no real logic may hide under a not-modeled annotation.
            for (m in node.methods) {
                val kind = stubKind(m) ?: continue
                val key = m.name + paramsDesc(m.desc)
                if (!realMembers.containsKey(key)) {
                    failures.add("$realFqn: @Bmc$kind stub ${m.name}${paramsDesc(m.desc)} mirrors no real member (typo / JDK drift)")
                }
                if (!bodyThrowsRecognizedLoud(m)) {
                    failures.add("$realFqn: @Bmc$kind stub ${m.name}${paramsDesc(m.desc)} body does not throw the recognized " +
                        "loud failure (must `throw fail(\"bmc4j: unmodelled member …\")` via BmcUnmodelledReached) — " +
                        "no real logic may hide under a not-modeled annotation")
                }
            }

            // (c) implemented-but-unannotated: every OWN public/protected model method that mirrors a
            // real member must be accounted for — covered by its own method-level @BmcModelConforms
            // OR be a method-level NotModelled/NotNeeded loud stub. Constructors and bridge/synthetic excluded.
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
                if (key in methodStubKeys) continue            // a loud NotModelled/NotNeeded stub: accounted for
                if (key in conformsOwn) continue               // modeled + conforming (own method-level annotation)
                failures.add("$realFqn: implemented model member ${m.name}${paramsDesc(m.desc)} lacks @BmcModelConforms")
            }

            // (d) completeness: every real member must be covered/declared/tailed.
            val covered = conformsKeys(realFqn)
            val declared = decls.mapNotNull { declKey(it.member) }.toSet()
            for ((key, m) in realMembers) {
                if (key in covered) continue
                if (key in declared) continue
                if (key in methodStubKeys) continue            // method-level loud stub accounts for it
                if (tail != null) continue
                failures.add("$realFqn: real member ${render(m)} is neither modeled (@BmcModelConforms), declared (@BmcNotModelled/@BmcNotNeeded), nor tail-waived (@BmcModelTail)")
            }
        }

        withClue("MODEL AUDITING GATE — unaccounted members / mis-annotations (${failures.size}):\n  " +
            failures.sorted().joinToString("\n  ")) {
            failures.isEmpty() shouldBe true
        }
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
