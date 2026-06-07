package conformance

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Canonical computation of the per-class TAIL SET — the real JDK members of every per-member-enforced
 * model that fall through to {@code @BmcModelTail} (not modeled, not method-level-stubbed, not
 * class-level-declared). Shared by the docs generator ([ModelCoverageDocsTest]) and the no-growth
 * ratchet ([ModelAuditGateTest]) so they can never disagree, and committed to
 * {@code docs/model-coverage-tail.txt} so any NEW real surface that silently falls into the tail
 * (e.g. a JDK bump) forces an explicit decision (the ratchet fails naming it).
 *
 * Each entry is rendered {@code pkg.Class#member(SimpleType, ...)}, sorted.
 */
object TailSet {

    private const val AUDIT = "org/bmc4j/models/audit/"
    private const val CONFORMS = "L${AUDIT}BmcModelConforms;"
    private const val NOT_MODELLED = "L${AUDIT}BmcNotModelled;"  // method-level only (no class-level / list form)
    private const val NOT_NEEDED = "L${AUDIT}BmcNotNeeded;"
    private const val NOT_NEEDED_LIST = "L${AUDIT}BmcNotNeededList;"
    private const val TAIL = "L${AUDIT}BmcModelTail;"
    private const val SYNTHESIZED = "L${AUDIT}BmcSynthesizedLoud;"

    /** Load the relocated model nodes keyed by their real (un-relocated) FQN. */
    fun loadNodes(): Map<String, ClassNode> {
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
        return nodes
    }

    private fun anns(n: ClassNode) = (n.invisibleAnnotations ?: emptyList()) + (n.visibleAnnotations ?: emptyList())
    private fun methodAnns(m: MethodNode) = (m.invisibleAnnotations ?: emptyList()) + (m.visibleAnnotations ?: emptyList())

    // Param descriptor NORMALIZED back from the relocation, so a model-method key matches the real key.
    private fun paramsDesc(methodDesc: String): String =
        "(" + Type.getArgumentTypes(methodDesc).joinToString("") { it.descriptor.replace("Lbmcref/", "L") } + ")"

    private fun isStub(m: MethodNode): Boolean =
        methodAnns(m).any { it.desc == NOT_MODELLED || it.desc == NOT_NEEDED }

    // Class-level (member=) declarations are @BmcNotNeeded only — @BmcNotModelled is method-only (no
    // TYPE target), so its waivers are method-level loud stubs (see stubKeys), never class-level here.
    private fun classLevelDeclaredKeys(node: ClassNode): Set<String> {
        val out = mutableSetOf<String>()
        fun read(values: List<Any?>?) {
            if (values == null) return
            var member: String? = null; var i = 0
            while (i + 1 < values.size) { if (values[i] == "member") member = values[i + 1] as? String; i += 2 }
            member?.let { declKey(it)?.let(out::add) }
        }
        for (a in anns(node)) when (a.desc) {
            NOT_NEEDED -> read(a.values)
            NOT_NEEDED_LIST -> {
                val v = a.values ?: continue; var j = 0
                while (j + 1 < v.size) {
                    if (v[j] == "value") {
                        @Suppress("UNCHECKED_CAST")
                        (v[j + 1] as? List<AnnotationNode>)?.forEach { read(it.values) }
                    }
                    j += 2
                }
            }
        }
        return out
    }

    /** Keys covered by a method-level @BmcModelConforms, resolved up the model chain. */
    private fun coveredKeys(nodes: Map<String, ClassNode>, realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) {
                if (m.name == "<init>" || m.name == "<clinit>") continue
                if ((m.access and Opcodes.ACC_SYNTHETIC) != 0 || (m.access and Opcodes.ACC_BRIDGE) != 0) continue
                if (methodAnns(m).any { it.desc == SYNTHESIZED }) continue
                if (isStub(m)) continue
                if (methodAnns(m).any { it.desc == CONFORMS }) out.add(m.name + paramsDesc(m.desc))
            }
            val sup = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (sup != null && nodes.containsKey(sup)) nodes[sup] else null
        }
        return out
    }

    /** Method-level stub keys, resolved up the model chain. */
    private fun stubKeys(nodes: Map<String, ClassNode>, realFqn: String): Set<String> {
        val out = mutableSetOf<String>()
        var cur: ClassNode? = nodes[realFqn]
        while (cur != null) {
            for (m in cur.methods) if (isStub(m)) out.add(m.name + paramsDesc(m.desc))
            val sup = cur.superName?.removePrefix("bmcref/")?.replace('/', '.')
            cur = if (sup != null && nodes.containsKey(sup)) nodes[sup] else null
        }
        return out
    }

    private val objectKeys = java.lang.Object::class.java.methods
        .map { it.name + "(" + it.parameterTypes.joinToString("") { p -> Type.getType(p).descriptor } + ")" }.toSet()

    private fun realAuditable(real: Class<*>): List<java.lang.reflect.Method> {
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
        return seen.values.toList()
    }

    private fun hasTail(node: ClassNode): Boolean = anns(node).any { it.desc == TAIL }

    private fun render(realFqn: String, m: java.lang.reflect.Method): String =
        "$realFqn#${m.name}(" + m.parameterTypes.joinToString(", ") { erased(it) } + ")"

    private fun erased(c: Class<*>): String = if (c.isArray) erased(c.componentType) + "[]" else c.simpleName

    /** The full, sorted tail enumeration across every per-member-enforced model. */
    fun compute(nodes: Map<String, ClassNode>): List<String> {
        val out = sortedSetOf<String>()
        for (realFqn in PER_MEMBER_ENFORCED) {
            val node = nodes[realFqn] ?: continue
            if (!hasTail(node)) continue
            val real = try { Class.forName(realFqn) } catch (e: Throwable) { continue }
            val covered = coveredKeys(nodes, realFqn)
            val stubs = stubKeys(nodes, realFqn)
            val declared = classLevelDeclaredKeys(node)
            for (m in realAuditable(real)) {
                val key = m.name + "(" + m.parameterTypes.joinToString("") { Type.getType(it).descriptor } + ")"
                if (key in covered || key in stubs || key in declared) continue
                out.add(render(realFqn, m))
            }
        }
        return out.toList()
    }

    private fun declKey(member: String): String? {
        val o = member.indexOf('('); val c = member.lastIndexOf(')')
        if (o < 0 || c < o) return null
        val name = member.substring(0, o).trim()
        val params = member.substring(o + 1, c).trim()
        val types = if (params.isEmpty()) emptyList() else params.split(',').map { erasedDesc(it.trim()) }
        if (types.any { it == null }) return null
        return name + "(" + types.joinToString("") + ")"
    }

    private fun erasedDesc(s: String): String? {
        if (s.isEmpty()) return null
        if (s.endsWith("[]")) { val e = erasedDesc(s.removeSuffix("[]")) ?: return null; return "[$e" }
        return when (s) {
            "int" -> "I"; "long" -> "J"; "boolean" -> "Z"; "byte" -> "B"; "char" -> "C"
            "short" -> "S"; "float" -> "F"; "double" -> "D"; "void" -> "V"
            else -> "L" + s.replace('.', '/') + ";"
        }
    }
}
