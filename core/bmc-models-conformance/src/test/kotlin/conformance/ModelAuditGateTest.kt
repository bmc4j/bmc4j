package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
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
 *  - declared in a class-level {@code @BmcNotModelled} / {@code @BmcNotNeeded}, or
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
    val notModelledDesc = "L${auditPkg}BmcNotModelled;"
    val notModelledListDesc = "L${auditPkg}BmcNotModelledList;"
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
    fun declarations(node: ClassNode): List<Decl> {
        val out = mutableListOf<Decl>()
        for (ann in anns(node)) {
            when (ann.desc) {
                notModelledDesc -> readDecl(ann.values, "NotModelled")?.let { out.add(it) }
                notNeededDesc -> readDecl(ann.values, "NotNeeded")?.let { out.add(it) }
                notModelledListDesc, notNeededListDesc -> {
                    val kind = if (ann.desc == notModelledListDesc) "NotModelled" else "NotNeeded"
                    val vals = ann.values ?: continue
                    var j = 0
                    while (j + 1 < vals.size) {
                        if (vals[j] == "value") {
                            @Suppress("UNCHECKED_CAST")
                            (vals[j + 1] as? List<AnnotationNode>)?.forEach { inner -> readDecl(inner.values, kind)?.let { out.add(it) } }
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

    fun classLevelConforms(node: ClassNode): Boolean = anns(node).any { it.desc == conformsDesc }

    // model member keys that COUNT AS CONFORMING, resolved through the model inheritance chain. A
    // member conforms if it carries a method-level @BmcModelConforms, OR its declaring class carries a
    // class-level @BmcModelConforms (blanket: every implemented member mirrors-and-conforms).
    // key = name + erased-param-desc (return-agnostic). Constructors excluded (audited via real ctors
    // separately if ever needed; the real-member surface enumerates methods, not <init>).
    val synthesizedDesc = "L${auditPkg}BmcSynthesizedLoud;"
    fun isSynthesizedLoud(m: org.objectweb.asm.tree.MethodNode): Boolean =
        ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())).any { it.desc == synthesizedDesc }

    fun conformsKeys(realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            val blanket = classLevelConforms(cur)
            for (m in cur.methods) {
                if (m.name == "<clinit>" || m.name == "<init>") continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                if (isSynthesizedLoud(m)) continue // a loud stub is NOT a genuine model implementation
                val methodConforms = ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList()))
                    .any { it.desc == conformsDesc }
                if (blanket || methodConforms) out.add(m.name + paramsDesc(m.desc))
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

            val decls = declarations(node)
            val tail = tailReason(node)
            val realMembers = realAuditableKeys(real)

            // (b) dangling declarations: a named member must exist on the real class.
            for (d in decls) {
                val key = declKey(d.member)
                if (key == null) { failures.add("$realFqn: @Bmc${d.kind} member='${d.member}' is not a parseable signature"); continue }
                if (!realMembers.containsKey(key)) {
                    failures.add("$realFqn: dangling @Bmc${d.kind}(member=\"${d.member}\") — the real class has no such member (typo / JDK drift)")
                }
            }

            // (c) implemented-but-unannotated: every OWN public/protected model method that mirrors a
            // real member must be covered by @BmcModelConforms — either a class-level blanket or its own
            // method-level annotation. Constructors and bridge/synthetic excluded.
            val blanket = classLevelConforms(node)
            val conformsOwn = node.methods.filter { m ->
                ((m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())).any { it.desc == conformsDesc }
            }.map { it.name + paramsDesc(it.desc) }.toSet()
            if (!blanket) {
                for (m in node.methods) {
                    if (m.name == "<init>" || m.name == "<clinit>") continue
                    if ((m.access and org.objectweb.asm.Opcodes.ACC_SYNTHETIC) != 0) continue
                    if ((m.access and org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) continue
                    val isPublic = (m.access and org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0
                    val isProtected = (m.access and org.objectweb.asm.Opcodes.ACC_PROTECTED) != 0
                    if (!isPublic && !isProtected) continue
                    val key = m.name + paramsDesc(m.desc)
                    // Only require coverage for methods that mirror a REAL member (model-internal helpers
                    // that don't shadow the real surface aren't part of the audit).
                    if (!realMembers.containsKey(key)) continue
                    if (key !in conformsOwn) {
                        failures.add("$realFqn: implemented model member ${m.name}${paramsDesc(m.desc)} lacks @BmcModelConforms")
                    }
                }
            }

            // (d) completeness: every real member must be covered/declared/tailed.
            val covered = conformsKeys(realFqn)
            val declared = decls.mapNotNull { declKey(it.member) }.toSet()
            for ((key, m) in realMembers) {
                if (key in covered) continue
                if (key in declared) continue
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
            } else if (!annotated && realFqn !in WAIVED) {
                // A REGISTERED (COVERED) model that bears no audit annotations FAILS — a registered model
                // can't silently skip auditing. A genuinely-new, non-registered, non-waived model only
                // WARNS here (CoverageGateTest is what fails on an unregistered new model).
                if (realFqn in COVERED) {
                    failures.add("$realFqn: registered (COVERED) but carries NO audit annotation — annotate it (at least a class-level @BmcModelConforms)")
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
})

private fun paramsDesc(methodDesc: String): String =
    "(" + Type.getArgumentTypes(methodDesc).joinToString("") { it.descriptor } + ")"

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
