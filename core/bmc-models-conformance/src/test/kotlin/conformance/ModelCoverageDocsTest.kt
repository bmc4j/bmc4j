package conformance

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Upgrade B — docs generation. Renders the per-member model coverage tables straight from the audit
 * annotations and the real JDK surface, and enforces that the committed {@code docs/model-coverage.md}
 * matches. The doc is GENERATED, never hand-edited: run with {@code -Dbmc.regenerateDocs=true} to
 * rewrite it after changing annotations. If the committed copy is stale the build fails loudly (same
 * loud-by-default spirit as the gate) telling you to regenerate. A small auto-write fallback keeps it
 * frictionless when the file is simply missing.
 */
class ModelCoverageDocsTest : FunSpec({

    test("docs/model-coverage.md is generated from the annotations and is not stale") {
        val jarPath = System.getProperty("java.class.path").split(File.pathSeparatorChar)
            .firstOrNull { it.replace('\\', '/').endsWith("bmcref-models.jar") }
            ?: error("relocated models jar not found on the test classpath")

        val nodes = LinkedHashMap<String, ClassNode>()
        JarFile(jarPath).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.name.endsWith("package-info.class") && !it.name.contains('$') }
                .forEach { e ->
                    val n = ClassNode()
                    ClassReader(jar.getInputStream(e).readBytes()).accept(n, 0)
                    nodes[n.name.removePrefix("bmcref/").replace('/', '.')] = n
                }
        }

        val generated = renderModelCoverage(nodes)

        // docs live at <repo>/docs; the test's working dir is core/bmc-models-conformance.
        val docsFile = File("../../docs/model-coverage.md").absoluteFile
        val regenerate = System.getProperty("bmc.regenerateDocs") == "true"

        if (regenerate || !docsFile.exists()) {
            docsFile.parentFile.mkdirs()
            docsFile.writeText(generated)
        }

        val committed = docsFile.readText().replace("\r\n", "\n")
        withClue("docs/model-coverage.md is stale — regenerate with " +
            "`gradlew -p core :bmc-models-conformance:test --tests conformance.ModelCoverageDocsTest -Dbmc.regenerateDocs=true` and commit") {
            committed shouldBe generated.replace("\r\n", "\n")
        }
    }
})

private fun renderModelCoverage(nodes: Map<String, ClassNode>): String {
    val auditPkg = "org/bmc4j/models/audit/"
    val conformsDesc = "L${auditPkg}BmcModelConforms;"
    val notModelledDesc = "L${auditPkg}BmcNotModelled;"  // method-level only (no class-level / list form)
    val notNeededDesc = "L${auditPkg}BmcNotNeeded;"
    val notNeededListDesc = "L${auditPkg}BmcNotNeededList;"
    val tailDesc = "L${auditPkg}BmcModelTail;"

    fun anns(n: ClassNode) = (n.invisibleAnnotations ?: emptyList()) + (n.visibleAnnotations ?: emptyList())

    data class Decl(val member: String, val reason: String, val kind: String)
    fun readDecl(values: List<Any?>?, kind: String): Decl? {
        if (values == null) return null
        var member: String? = null; var reason: String? = null; var i = 0
        while (i + 1 < values.size) {
            when (values[i]) { "member" -> member = values[i + 1] as? String; "reason" -> reason = values[i + 1] as? String }
            i += 2
        }
        return if (member != null && reason != null) Decl(member, reason, kind) else null
    }
    // Class-level declarations are @BmcNotNeeded only — @BmcNotModelled is method-only (no TYPE target).
    fun declarations(n: ClassNode): List<Decl> {
        val out = mutableListOf<Decl>()
        for (a in anns(n)) when (a.desc) {
            notNeededDesc -> readDecl(a.values, "NotNeeded")?.let { out.add(it) }
            notNeededListDesc -> {
                val vals = a.values ?: continue; var j = 0
                while (j + 1 < vals.size) {
                    if (vals[j] == "value") {
                        @Suppress("UNCHECKED_CAST")
                        (vals[j + 1] as? List<AnnotationNode>)?.forEach { inner -> readDecl(inner.values, "NotNeeded")?.let { out.add(it) } }
                    }
                    j += 2
                }
            }
        }
        return out
    }
    fun tailReason(n: ClassNode): String? {
        for (a in anns(n)) if (a.desc == tailDesc) {
            val vals = a.values ?: continue; var j = 0
            while (j + 1 < vals.size) { if (vals[j] == "reason") return vals[j + 1] as? String; j += 2 }
        }
        return null
    }

    val objectKeys = java.lang.Object::class.java.methods
        .map { it.name + "(" + it.parameterTypes.joinToString("") { p -> Type.getType(p).descriptor } + ")" }.toSet()
    fun realAuditable(real: Class<*>): List<java.lang.reflect.Method> {
        val seen = LinkedHashMap<String, java.lang.reflect.Method>()
        for (m in real.methods) {
            if (m.isBridge || m.isSynthetic) continue
            val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
            if (key in objectKeys) continue
            seen.putIfAbsent(key, m)
        }
        var c: Class<*>? = real
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.isBridge || m.isSynthetic || !Modifier.isProtected(m.modifiers)) continue
                val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
                if (key in objectKeys) continue
                seen.putIfAbsent(key, m)
            }
            c = c.superclass
        }
        return seen.values.sortedBy { render(it) }
    }
    val synthesizedDesc = "L${auditPkg}BmcSynthesizedLoud;"
    fun methodAnns(m: org.objectweb.asm.tree.MethodNode) =
        (m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())
    // A method-level NotModelled/NotNeeded loud stub is NOT a genuine model implementation.
    fun methodStubKind(m: org.objectweb.asm.tree.MethodNode): String? = when {
        methodAnns(m).any { it.desc == notModelledDesc } -> "NotModelled"
        methodAnns(m).any { it.desc == notNeededDesc } -> "NotNeeded"
        else -> null
    }
    fun conformsKeys(realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) {
                if (m.name == "<init>" || m.name == "<clinit>") continue
                if ((m.access and Opcodes.ACC_SYNTHETIC) != 0 || (m.access and Opcodes.ACC_BRIDGE) != 0) continue
                val isSynth = methodAnns(m).any { it.desc == synthesizedDesc }
                if (isSynth) continue // loud stub, not a genuine model implementation
                if (methodStubKind(m) != null) continue // method-level NotModelled/NotNeeded stub: not modeled
                val mc = methodAnns(m).any { it.desc == conformsDesc }
                if (mc) out.add(m.name + paramsDescDoc(m.desc))
            }
            val sup = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (sup != null && nodes.containsKey(sup)) nodes[sup] else null
        }
        return out
    }
    // Method-level stub (name+params) -> (kind, reason), resolved through the model inheritance chain.
    fun methodStubs(realFqn: String): Map<String, Pair<String, String>> {
        val out = LinkedHashMap<String, Pair<String, String>>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) {
                val kind = methodStubKind(m) ?: continue
                val reason = methodAnns(m).firstOrNull { it.desc == notModelledDesc || it.desc == notNeededDesc }
                    ?.let { a -> val v = a.values ?: emptyList<Any?>(); var i = 0; var r: String? = null
                        while (i + 1 < v.size) { if (v[i] == "reason") r = v[i + 1] as? String; i += 2 }; r } ?: ""
                out.putIfAbsent(m.name + paramsDescDoc(m.desc), kind to reason)
            }
            val sup = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (sup != null && nodes.containsKey(sup)) nodes[sup] else null
        }
        return out
    }
    fun declKey(member: String): String? {
        val o = member.indexOf('('); val c = member.lastIndexOf(')')
        if (o < 0 || c < o) return null
        val name = member.substring(0, o).trim()
        val params = member.substring(o + 1, c).trim()
        val types = if (params.isEmpty()) emptyList() else params.split(',').map { erasedDesc(it.trim()) }
        if (types.any { it == null }) return null
        return name + "(" + types.joinToString("") + ")"
    }

    val sb = StringBuilder()
    sb.append("# Model coverage (generated)\n\n")
    sb.append("<!-- GENERATED by conformance.ModelCoverageDocsTest from the @BmcModelConforms / ")
    sb.append("@BmcNotModelled / @BmcNotNeeded / @BmcModelTail audit annotations. Do NOT edit by hand: ")
    sb.append("change the annotations on the models, then regenerate with ")
    sb.append("`gradlew -p core :bmc-models-conformance:test --tests conformance.ModelCoverageDocsTest -Dbmc.regenerateDocs=true`. -->\n\n")
    sb.append("Every public/protected member of each per-member-audited model's real JDK target is ")
    sb.append("accounted for below: **modeled** (sound under BMC), **not-modeled** (cannot be), ")
    sb.append("**not-needed** (exotic), or in the **tail** (the exotic remainder, enumerated in full). ")
    sb.append("Not-modeled/not-needed members carry a hand-written loud body (a real stub method whose ")
    sb.append("decision and reason live next to the surface it waives); tail members get a ")
    sb.append("build-synthesized loud body. Reaching ANY of them trips the BmcUnmodelledReached ")
    sb.append("sentinel, so the verdict is an honest member-named **UNKNOWN** (a bmc4j model gap), never ")
    sb.append("a false REFUTED and never a silent havoc — unless explicitly acknowledged via ")
    sb.append("`acknowledgeUnmodelled`, which degrades it to a footnoted nondet stub.\n")

    for (realFqn in PER_MEMBER_ENFORCED.sorted()) {
        val node = nodes[realFqn] ?: continue
        val real = try { Class.forName(realFqn) } catch (e: Throwable) { continue }
        val decls = declarations(node)
        val tail = tailReason(node)
        val covered = conformsKeys(realFqn)
        val declaredByKind = HashMap<String, Decl>()
        for (d in decls) declKey(d.member)?.let { declaredByKind[it] = d }   // class-level @BmcNotNeeded(member=) form
        val stubByKey = methodStubs(realFqn)                                 // method-level stub form

        val members = realAuditable(real)
        val modeled = ArrayList<String>()
        val notModelled = ArrayList<Pair<String, String>>()
        val notNeeded = ArrayList<Pair<String, String>>()
        val tailed = ArrayList<String>()
        for (m in members) {
            val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
            when {
                key in covered -> modeled.add(render(m))
                stubByKey.containsKey(key) -> {
                    val (kind, reason) = stubByKey[key]!!
                    if (kind == "NotModelled") notModelled.add(render(m) to reason) else notNeeded.add(render(m) to reason)
                }
                declaredByKind.containsKey(key) -> {
                    val d = declaredByKind[key]!!
                    if (d.kind == "NotModelled") notModelled.add(render(m) to d.reason) else notNeeded.add(render(m) to d.reason)
                }
                tail != null -> tailed.add(render(m))
            }
        }

        sb.append("\n## `").append(realFqn).append("`\n\n")
        sb.append("Real surface: ${members.size} members — ")
        sb.append("modeled ${modeled.size}, not-modeled ${notModelled.size}, not-needed ${notNeeded.size}, tail ${tailed.size}.\n\n")
        if (modeled.isNotEmpty()) {
            sb.append("**Modeled** (`@BmcModelConforms`): ")
            sb.append(modeled.sorted().joinToString(", ") { "`$it`" }).append("\n\n")
        }
        if (notModelled.isNotEmpty()) {
            sb.append("| Not modeled (cannot) | Reason |\n|---|---|\n")
            notModelled.sortedBy { it.first }.forEach { sb.append("| `${it.first}` | ${it.second} |\n") }
            sb.append("\n")
        }
        if (notNeeded.isNotEmpty()) {
            sb.append("| Not needed (exotic) | Reason |\n|---|---|\n")
            notNeeded.sortedBy { it.first }.forEach { sb.append("| `${it.first}` | ${it.second} |\n") }
            sb.append("\n")
        }
        if (tail != null) {
            // ENUMERATE every tail member — nothing summarized away. (Compact, collapsible.)
            sb.append("<details><summary><b>Tail</b> (<code>@BmcModelTail</code>, ${tailed.size} members, all loud): ")
            sb.append(tail).append("</summary>\n\n")
            if (tailed.isEmpty()) {
                sb.append("_(none — the real surface is fully modeled/declared)_\n")
            } else {
                tailed.sorted().forEach { sb.append("- `").append(it).append("`\n") }
            }
            sb.append("\n</details>\n\n")
        }
    }
    return sb.toString().replace("\r\n", "\n")
}

private fun render(m: java.lang.reflect.Method): String =
    m.name + "(" + m.parameterTypes.joinToString(", ") { erasedNameDoc(it) } + ")"
private fun erasedNameDoc(c: Class<*>): String =
    if (c.isArray) erasedNameDoc(c.componentType) + "[]" else (c.simpleName)
// Normalized back from the relocation (bmcref/java/... -> java/...) so a model-method key matches the
// reflection-derived real-member key (see the gate's paramsDesc for the rationale).
private fun paramsDescDoc(methodDesc: String): String =
    "(" + Type.getArgumentTypes(methodDesc).joinToString("") { it.descriptor.replace("Lbmcref/", "L") } + ")"
private fun erasedDesc(s: String): String? {
    if (s.isEmpty()) return null
    if (s.endsWith("[]")) { val e = erasedDesc(s.removeSuffix("[]")) ?: return null; return "[$e" }
    return when (s) {
        "int" -> "I"; "long" -> "J"; "boolean" -> "Z"; "byte" -> "B"; "char" -> "C"
        "short" -> "S"; "float" -> "F"; "double" -> "D"; "void" -> "V"
        else -> "L" + s.replace('.', '/') + ";"
    }
}
