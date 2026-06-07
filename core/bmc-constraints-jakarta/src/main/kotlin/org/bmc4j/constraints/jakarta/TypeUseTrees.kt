package org.bmc4j.constraints.jakarta

import com.sun.source.tree.ParameterizedTypeTree
import com.sun.source.tree.VariableTree
import com.sun.source.util.TreePath
import com.sun.source.util.Trees
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

/**
 * Source-tree fallback for container-element (TYPE_USE) annotations.
 *
 * javac up to 22 drops type-use annotations from the TYPE ARGUMENTS of a field's
 * `TypeMirror` obtained via `Element.asType()` (the JDK-8225377 family, fixed for 23) —
 * so `List<@Min(1) Integer>` looks like a bare `List<Integer>` to the mirror API on the
 * very JDKs (17, 21) bmc4j verifies, and the element constraints would be SILENTLY
 * dropped from the generated `assumeValid`. The ATTRIBUTED SOURCE TREE keeps them:
 * this helper walks `Trees -> VariableTree.type -> typeArguments[index]` and returns
 * that tree's mirror instead.
 *
 * Returns null (callers keep the plain-mirror behavior) when the tree is unavailable:
 * non-javac compilers, or a wrapped `ProcessingEnvironment` it cannot unwrap.
 */
internal object TypeUseTrees {

    fun typeArgumentMirror(env: ProcessingEnvironment, field: Element, index: Int): TypeMirror? {
        val trees = treesOf(env) ?: return null
        return try {
            val path = trees.getPath(field) ?: return null
            val varTree = path.leaf as? VariableTree ?: return null
            val typeTree = varTree.type as? ParameterizedTypeTree ?: return null
            val argTree = typeTree.typeArguments.getOrNull(index) ?: return null
            val argPath = TreePath.getPath(path, argTree) ?: return null
            trees.getTypeMirror(argPath)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun treesOf(env: ProcessingEnvironment): Trees? {
        tryInstance(env)?.let { return it }
        // Gradle's incremental annotation processing hands processors a WRAPPING
        // ProcessingEnvironment that Trees.instance() rejects; unwrap delegate fields
        // (the standard workaround, same as error-prone's) until a javac one appears.
        var cur: Any = env
        repeat(4) {
            val delegateField = cur.javaClass.declaredFields.firstOrNull {
                ProcessingEnvironment::class.java.isAssignableFrom(it.type)
            } ?: return null
            val next = try {
                delegateField.isAccessible = true
                delegateField.get(cur)
            } catch (_: RuntimeException) {
                return null
            } ?: return null
            (next as? ProcessingEnvironment)?.let { pe -> tryInstance(pe)?.let { return it } }
            cur = next
        }
        return null
    }

    private fun tryInstance(env: ProcessingEnvironment): Trees? = try {
        Trees.instance(env)
    } catch (_: RuntimeException) {
        null
    }
}
