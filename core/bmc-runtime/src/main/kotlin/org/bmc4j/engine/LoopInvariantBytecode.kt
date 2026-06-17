package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.concurrent.ConcurrentHashMap

/**
 * The ANNOTATION form of [LoopContractBytecode]: turns an `@LoopInvariant(loop = "<id>", predicate = "p")`
 * on a proof method into the SAME sound base/step/summary loop-contract VCs -- but applied to the loop AS
 * WRITTEN (a real `for`/`while`), recovered from the bytecode, rather than from a re-authored marker
 * sequence the proof writes by hand.
 *
 * ## What it recovers
 * For the structured, head-guarded counted-loop shape javac and kotlinc emit for `for (i; i < n; i++)` and
 * `while (pos < limit)` (the okio decimal codec's exact shapes):
 * ```
 *   <init>                          // i = 0   (before the loop, untouched)
 *  HEADER:
 *   <guard>  if_icmp<cmp> EXIT      // i < n   -- the loop CONTINUES iff the branch is NOT taken
 *   <body...>                       // the loop body
 *  BACK:                            // a backward goto (or the iinc + goto) to HEADER
 *   goto HEADER
 *  EXIT:
 *   <rest>
 * ```
 * The Nth such natural loop (by the order its back-edge target appears) is the one whose engine id ends in
 * `.N` -- the SAME `.N` index [org.bmc4j.LoopUnwind] / @BmcProfile use. The loop's locals are read from the
 * LocalVariableTable and bound BY NAME to the predicate method's parameters.
 *
 * ## The transform (in place, reusing [LoopContractBytecode]'s proven VC shape)
 * The recovered loop is replaced, in the method's instruction list, by:
 * ```
 *   check(I)                        // base case (assert I on entry)
 *   havoc(W)                        // re-symbolize the auto-computed assigns set
 *   assume(I); if (!nondet) goto CONT   // inductive hyp + open the nondet step branch
 *     assume(guard)                 //   [step] open under the guard
 *     <body once>                   //   [step] the recovered body, back-edge removed
 *     check(I); assume(false)       //   [step] preservation assert, then cut the path
 *   CONT:
 *   assume(!guard)                  // summary continuation (loop exited)
 *   <rest>                          // EXIT onward, unchanged
 * ```
 * where `I` is computed by INVOKESTATIC of the predicate method (locals loaded in its parameter order,
 * bound by name). This is byte-for-byte the lowering [LoopContractBytecode] proved sound; only the SOURCE
 * of the loop differs (recovered vs marker-bracketed).
 *
 * ## Soundness
 * Identical to the marker DSL: base + step preservation are ASSERTED (engine-proven); only the summary
 * (havoc + assume I + assume !guard) uses the invariant. A WRONG predicate fails the base or step assert and
 * REFUTES. The assigns set (local int/long stores in the body) is auto-computed; a body that writes a field
 * or array element is REFUSED LOUD ([LoopInvariantError] -> UNKNOWN), exactly as the marker form refuses an
 * unsupported heap frame.
 *
 * ## Spike boundary
 * Recovers the head-guarded `if_icmp<cmp>`-exit counted loop (the okio shape) in the ENTRY proof method.
 * Other guard encodings (`ifne`/`iflt` on a precomputed boolean, `lcmp`-based long guards, fully unstructured
 * back-edges) and contracting a loop inside a CALLEE are not yet recovered -- they surface a loud
 * [LoopInvariantError] (UNKNOWN), never a silent wrong frame. This is the auto-detection brittleness the
 * spike flagged; the marker DSL ([LoopContractBytecode]) remains the fully general fallback.
 */
internal object LoopInvariantBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val CPROVER = "org/cprover/CProver"
    private const val ANNOT = "Lorg/bmc4j/LoopInvariant;"
    private const val ANNOT_CONTAINER = "Lorg/bmc4j/LoopInvariants;"

    /** A malformed `@LoopInvariant`, an unrecoverable loop, or an unsupported (heap) assigns set. Unchecked
     *  so it propagates out of the rewrite path and is reclassified UNKNOWN (never a silent false green). */
    class LoopInvariantError(message: String) : RuntimeException(message)

    /** One `@LoopInvariant` spec parsed off a method: the engine loop id and the predicate method name. */
    data class Spec(val loopId: String, val predicate: String)

    /** Read every `@LoopInvariant` on [methodName] of [bytes] (unwrapping the `@LoopInvariants` container).
     *  Empty if the method carries none -- the cheap gate the per-proof pass uses to skip ordinary proofs. */
    internal fun specsOf(bytes: ByteArray, methodName: String): List<Spec> {
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES)
        val m = cn.methods.firstOrNull { it.name == methodName } ?: return emptyList()
        val out = ArrayList<Spec>()
        val singles = m.visibleAnnotations?.filter { it.desc == ANNOT } ?: emptyList()
        for (a in singles) {
            out.add(specFromValues(a.values))
        }
        val containers = m.visibleAnnotations?.filter { it.desc == ANNOT_CONTAINER } ?: emptyList()
        for (c in containers) {
            // @LoopInvariants.value() is an array of nested @LoopInvariant annotation nodes.
            val arr = annotationArray(c.values, "value")
            for (node in arr) {
                out.add(specFromValues((node as org.objectweb.asm.tree.AnnotationNode).values))
            }
        }
        return out
    }

    private fun specFromValues(values: List<Any?>?): Spec {
        var loop: String? = null
        var pred: String? = null
        if (values != null) {
            var i = 0
            while (i + 1 < values.size) {
                when (values[i]) {
                    "loop" -> loop = values[i + 1] as? String
                    "predicate" -> pred = values[i + 1] as? String
                }
                i += 2
            }
        }
        if (loop.isNullOrBlank() || pred.isNullOrBlank()) {
            throw LoopInvariantError("@LoopInvariant requires non-blank loop and predicate")
        }
        return Spec(loop.trim(), pred.trim())
    }

    @Suppress("UNCHECKED_CAST")
    private fun annotationArray(values: List<Any?>?, key: String): List<Any?> {
        if (values == null) return emptyList()
        var i = 0
        while (i + 1 < values.size) {
            if (values[i] == key) {
                return values[i + 1] as List<Any?>
            }
            i += 2
        }
        return emptyList()
    }

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite [classpath] so every `@LoopInvariant` on [entryClass].[methodName] is lowered to its
     * base/step/summary VCs. Memoized per (classpath, class, method). Mirrors [LoopContractBytecode.rewrite].
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, methodName: String): String {
        val key = "$classpath|$entryClass|$methodName"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "loopinvariant", { b ->
                ClasspathMirror.Transformed(
                        rewriteClass(b, entryClass.replace('.', '/'), methodName))
            }, "$entryClass|$methodName")
        }
    }

    /** Rewrite the `@LoopInvariant`-annotated [methodName] of [internalName]; everything else copied
     *  verbatim. Exposed for unit tests. */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String): ByteArray {
        val cr = ClassReader(bytes)
        if (cr.className != internalName) {
            return bytes
        }
        val specs = specsOf(bytes, methodName)
        if (specs.isEmpty()) {
            return bytes
        }
        val cn = ClassNode()
        cr.accept(cn, 0)
        val ownerDot = internalName.replace('/', '.')
        for (spec in specs) {
            val mn = cn.methods.firstOrNull { it.name == methodName }
                    ?: throw LoopInvariantError("$internalName.$methodName not found")
            contractLoop(cn, ownerDot, mn, spec)
        }
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        return cw.toByteArray()
    }

    /** The method `pkg.Cls.name:(sig)ret` of an engine loop id `java::pkg.Cls.name:(sig)ret.N`, plus N. */
    private data class LoopRef(val methodKey: String, val index: Int)

    private fun parseLoopId(loopId: String): LoopRef {
        val body = loopId.removePrefix("java::")
        val dot = body.lastIndexOf('.')
        if (dot < 0) {
            throw LoopInvariantError("malformed loop id (no .N index): $loopId")
        }
        val index = body.substring(dot + 1).toIntOrNull()
                ?: throw LoopInvariantError("malformed loop id (.N not an int): $loopId")
        return LoopRef(body.substring(0, dot), index)
    }

    /** The `pkg.Cls.name:(sig)ret` key for [mn] on [ownerDot], matching the engine loop-id method half. */
    private fun methodKey(ownerDot: String, mn: MethodNode): String =
            "$ownerDot.${mn.name}:${mn.desc}"

    /**
     * Recover the loop named by [spec] in [mn] and replace it, in place, with the contract VCs. THROWS
     * [LoopInvariantError] when the loop id names a different method, the Nth loop is not the recoverable
     * head-guarded shape, the body writes the heap, or a predicate param has no matching local.
     */
    private fun contractLoop(cn: ClassNode, ownerDot: String, mn: MethodNode, spec: Spec) {
        val ref = parseLoopId(spec.loopId)
        val key = methodKey(ownerDot, mn)
        if (ref.methodKey != key) {
            throw LoopInvariantError(
                    "@LoopInvariant loop id ${spec.loopId} names method ${ref.methodKey}, but the contract is " +
                            "on $key. This spike contracts loops in the ENTRY proof method only; a loop in a " +
                            "callee is not yet recoverable (use the Bmc.loop* marker DSL).")
        }
        val loop = recoverNthLoop(mn, ref.index)
        val assigns = computeAssigns(mn, loop)
        val predDesc = predicateDescriptor(cn, spec.predicate)
        val params = predicateParamNames(cn, spec.predicate)
        val locals = bindLocals(mn, loop, params)
        spliceContract(ownerDot, mn, loop, assigns, spec.predicate, predDesc, locals)
    }

    /** A recovered head-guarded loop: its header label, the guard jump (whose NOT-taken edge enters the
     *  body), the exit label the guard jumps to, and the back-edge goto returning to the header. */
    private data class Loop(
            val header: LabelNode,
            val guard: JumpInsnNode,
            val exit: LabelNode,
            val backEdge: JumpInsnNode)

    /**
     * Find the [index]-th natural head-guarded loop in [mn]. Loops are ordered by the position of their
     * back-edge TARGET (the header), ascending -- the same source order jbmc numbers `.0, .1, ...`. A loop
     * is: a backward GOTO whose target (the header) reaches, before the back-edge, a conditional
     * `if_icmp<cmp>`/`if<cmp>` whose taken edge jumps FORWARD past the back-edge (the exit test). The
     * conditional need not be the header's first insn -- the operand loads precede it. THROWS if there is no
     * such Nth loop.
     */
    private fun recoverNthLoop(mn: MethodNode, index: Int): Loop {
        val insns = mn.instructions
        val pos = HashMap<AbstractInsnNode, Int>()
        var i = 0
        for (n in insns) {
            pos[n] = i++
        }
        val loops = ArrayList<Loop>()
        for (n in insns) {
            if (n !is JumpInsnNode || n.opcode != Opcodes.GOTO) {
                continue
            }
            val targetPos = pos[n.label] ?: continue
            val gotoPos = pos[n] ?: continue
            if (targetPos >= gotoPos) {
                continue // forward goto -- not a back-edge
            }
            // The header is the back-edge target. Its guard is the FIRST conditional branch at/after the
            // header (before the back-edge) whose taken edge jumps FORWARD past the back-edge -- i.e. the
            // loop-exit test. The header's first instructions LOAD the guard operands (iload i; iload n);
            // the conditional itself is a few insns in, so scan forward rather than demand it be first.
            var h: AbstractInsnNode? = n.label
            var guard: JumpInsnNode? = null
            while (h != null && h !== n) {
                if (h is JumpInsnNode && isConditionalBranch(h.opcode)) {
                    val exitPos = pos[h.label]
                    if (exitPos != null && exitPos > gotoPos) {
                        guard = h
                        break
                    }
                }
                h = h.next
            }
            if (guard == null) {
                continue // no loop-exit conditional before the back-edge -- not the recoverable shape
            }
            loops.add(Loop(n.label, guard, guard.label, n))
        }
        loops.sortBy { pos[it.header]!! }
        if (index < 0 || index >= loops.size) {
            throw LoopInvariantError(
                    "loop index .$index not found: recovered ${loops.size} head-guarded loop(s) in this " +
                            "method. The loop must be a structured for/while with an if_icmp<cmp> head guard " +
                            "(the okio codec shape); other shapes are not yet auto-recovered.")
        }
        return loops[index]
    }

    private fun isConditionalBranch(op: Int): Boolean = op in Opcodes.IFEQ..Opcodes.IF_ACMPNE

    /** Local int/long stores the body performs (header..back-edge, exclusive of the guard) = the assigns
     *  set. THROWS on a field/array store (an unsupported heap frame). */
    private fun computeAssigns(mn: MethodNode, loop: Loop): List<Store> {
        val stores = LinkedHashMap<Int, Store>()
        var n: AbstractInsnNode? = loop.guard.next
        while (n != null && n !== loop.backEdge) {
            when (n) {
                is VarInsnNode -> if (n.opcode == Opcodes.ISTORE || n.opcode == Opcodes.LSTORE) {
                    stores.putIfAbsent(n.`var`, Store(n.`var`, n.opcode))
                }
                is IincInsnNode -> stores.putIfAbsent(n.`var`, Store(n.`var`, Opcodes.ISTORE))
                is org.objectweb.asm.tree.FieldInsnNode ->
                    if (n.opcode == Opcodes.PUTFIELD || n.opcode == Opcodes.PUTSTATIC) {
                        throw LoopInvariantError(
                                "loop body writes a HEAP field (${n.owner}.${n.name}); this spike " +
                                        "auto-havocs LOCAL int/long stores only. Refused rather than " +
                                        "silently havoc'd unsoundly.")
                    }
                else -> if (n.opcode in ARRAY_STORE_OPCODES) {
                    throw LoopInvariantError(
                            "loop body writes a HEAP array element; this spike auto-havocs LOCAL int/long " +
                                    "stores only. Refused rather than silently havoc'd unsoundly.")
                }
            }
            n = n.next
        }
        return stores.values.toList()
    }

    private data class Store(val slot: Int, val storeOpcode: Int)

    /** The predicate method's descriptor (it must be a `static boolean(...)` in the proof class). */
    private fun predicateDescriptor(cn: ClassNode, predicate: String): String {
        val m = cn.methods.firstOrNull {
            it.name == predicate && (it.access and Opcodes.ACC_STATIC) != 0 &&
                    Type.getReturnType(it.desc) == Type.BOOLEAN_TYPE
        } ?: throw LoopInvariantError(
                "predicate static boolean ${cn.name}.$predicate(...) not found")
        return m.desc
    }

    private fun predicateParamNames(cn: ClassNode, predicate: String): List<String> {
        val m = cn.methods.first {
            it.name == predicate && (it.access and Opcodes.ACC_STATIC) != 0
        }
        val argCount = Type.getArgumentTypes(m.desc).size
        // Parameter names live in the method's own LVT (slots 0..argCount-1 for a static method).
        val names = arrayOfNulls<String>(argCount)
        for (lv in m.localVariables ?: emptyList<LocalVariableNode>()) {
            if (lv.index < argCount) {
                names[lv.index] = lv.name
            }
        }
        if (names.any { it == null }) {
            throw LoopInvariantError(
                    "predicate ${cn.name}.$predicate must be compiled with parameter names " +
                            "(LocalVariableTable); a parameter name was missing")
        }
        @Suppress("UNCHECKED_CAST")
        return names.toList() as List<String>
    }

    /** A predicate parameter bound to a method local: the local's slot + its load opcode. */
    private data class Binding(val slot: Int, val loadOpcode: Int, val type: Type)

    /**
     * Bind each predicate parameter NAME to a local of [mn] in scope across the loop, via the LVT. The
     * predicate's parameter TYPES must match the bound locals' types (the descriptor is the contract).
     */
    private fun bindLocals(mn: MethodNode, loop: Loop, paramNames: List<String>): List<Binding> {
        val pos = HashMap<AbstractInsnNode, Int>()
        var i = 0
        for (n in mn.instructions) {
            pos[n] = i++
        }
        val headerPos = pos[loop.header]!!
        val out = ArrayList<Binding>()
        for (name in paramNames) {
            // Pick the LVT entry of this name whose scope covers the loop header.
            val lv = (mn.localVariables ?: emptyList<LocalVariableNode>()).firstOrNull {
                it.name == name && pos[it.start]!! <= headerPos && pos[it.end]!! >= headerPos
            } ?: (mn.localVariables ?: emptyList()).firstOrNull { it.name == name }
            ?: throw LoopInvariantError(
                    "predicate parameter '$name' has no matching local in the proof method's " +
                            "LocalVariableTable (names must match the loop's locals)")
            val t = Type.getType(lv.desc)
            out.add(Binding(lv.index, loadOpcodeFor(t), t))
        }
        return out
    }

    private fun loadOpcodeFor(t: Type): Int = when (t.sort) {
        Type.LONG -> Opcodes.LLOAD
        Type.FLOAT -> Opcodes.FLOAD
        Type.DOUBLE -> Opcodes.DLOAD
        Type.OBJECT, Type.ARRAY -> Opcodes.ALOAD
        else -> Opcodes.ILOAD // boolean/byte/char/short/int
    }

    /**
     * Replace the recovered [loop] in [mn]'s instruction list with the contract VCs (base/havoc/assume/
     * step-branch/preserve/exit), the body reused once from the recovered instructions.
     */
    private fun spliceContract(
            ownerDot: String, mn: MethodNode, loop: Loop, assigns: List<Store>,
            predicate: String, predDesc: String, locals: List<Binding>) {
        val insns = mn.instructions
        val ownerInternal = ownerDot.replace('.', '/')
        // Capture the guard's operand-load insns ONCE, up front, before any mutation -- they get cloned into
        // both the step (assume continues) and the exit (assume !continues), and the originals are excised.
        val guardOperands = guardOperandLoads(loop)
        val contLabel = LabelNode(Label())

        val pre = InsnList()
        // --- base case: check(predicate(locals)) ---
        loadPredicate(pre, ownerInternal, predicate, predDesc, locals)
        pre.add(MethodInsnNode(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false))
        // --- havoc the assigns set ---
        for (st in assigns) {
            val (nondet, retDesc) = NONDET_FOR_STORE.getValue(st.storeOpcode)
            pre.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, nondet, retDesc, false))
            pre.add(VarInsnNode(st.storeOpcode, st.slot))
        }
        // --- inductive hyp: assume(predicate); open the nondet step branch ---
        loadPredicate(pre, ownerInternal, predicate, predDesc, locals)
        pre.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", "(Z)V", false))
        pre.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "nondetBoolean", "()Z", false))
        pre.add(JumpInsnNode(Opcodes.IFEQ, contLabel))
        // --- step opens under the guard: assume(guardContinues) ---
        emitGuard(pre, loop, guardOperands, assume = true)

        // Insert the prefix right after the header label (the guard's slot), then excise the original guard.
        insns.insert(loop.header, pre)
        removeGuardEvaluation(insns, loop, guardOperands)

        // --- after the body: preservation assert + cut, then CONT, then exit assume ---
        val post = InsnList()
        loadPredicate(post, ownerInternal, predicate, predDesc, locals)
        post.add(MethodInsnNode(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false))
        post.add(org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0))
        post.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", "(Z)V", false))
        post.add(contLabel)
        emitGuard(post, loop, guardOperands, assume = false) // assume(!guardContinues)
        // Splice the post block in place of the back-edge goto (which exits the step to the summary).
        insns.insert(loop.backEdge, post)
        insns.remove(loop.backEdge)
    }

    private fun loadPredicate(
            il: InsnList, ownerInternal: String, predicate: String, predDesc: String, locals: List<Binding>) {
        for (b in locals) {
            il.add(VarInsnNode(b.loadOpcode, b.slot))
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, ownerInternal, predicate, predDesc, false))
    }

    /**
     * Emit the loop's CONTINUES guard as a boolean and `assume` it (or its negation). The recovered guard is
     * an `if_icmp<cmp> EXIT` whose NOT-taken edge continues; the continues condition is the NEGATION of the
     * guard's comparison. We reconstruct it from the two operands the guard consumed -- recovered as the two
     * insns immediately preceding the guard jump (a `load; load` pair for if_icmp, a single `load` for if<cmp>).
     */
    private fun emitGuard(
            il: InsnList, loop: Loop, operands: List<AbstractInsnNode>, assume: Boolean) {
        for (op in operands) {
            il.add(op.clone(emptyMap<LabelNode, LabelNode>()))
        }
        // continues == !(guard taken). The guard branches to EXIT when the loop should STOP, so:
        //   assume(continues)  == assume the guard is NOT taken
        //   assume(!continues) == assume the guard IS taken
        val cmp = if (assume) negateComparison(loop.guard.opcode) else loop.guard.opcode
        emitCompareToBoolean(il, cmp)
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", "(Z)V", false))
    }

    /** The operand-load insns the guard consumed: the [arity] real insns immediately before the guard. */
    private fun guardOperandLoads(loop: Loop): List<AbstractInsnNode> {
        val arity = if (loop.guard.opcode in Opcodes.IF_ICMPEQ..Opcodes.IF_ACMPNE) 2 else 1
        val out = ArrayList<AbstractInsnNode>()
        var n: AbstractInsnNode? = loop.guard.previous
        while (n != null && out.size < arity) {
            if (n.opcode >= 0) {
                out.add(0, n)
            }
            n = n.previous
        }
        if (out.size != arity) {
            throw LoopInvariantError(
                    "could not recover the guard's $arity operand load(s); guard shape unsupported")
        }
        return out
    }

    /** Excise the original guard (its operand loads + the conditional jump) from the body so it runs only
     *  via our re-emitted assume; the body between the guard and the back-edge stays. */
    private fun removeGuardEvaluation(
            insns: InsnList, loop: Loop, operands: List<AbstractInsnNode>) {
        for (op in operands) {
            insns.remove(op)
        }
        insns.remove(loop.guard)
    }

    /** Push `1` if `cmp` holds for the operand(s) already on the stack, else `0` -- a branchful boolean. */
    private fun emitCompareToBoolean(il: InsnList, cmp: Int) {
        val pushTrue = LabelNode(Label())
        val done = LabelNode(Label())
        il.add(JumpInsnNode(cmp, pushTrue))
        il.add(org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_0))
        il.add(JumpInsnNode(Opcodes.GOTO, done))
        il.add(pushTrue)
        il.add(org.objectweb.asm.tree.InsnNode(Opcodes.ICONST_1))
        il.add(done)
    }

    /** The comparison whose truth means "loop continues" = the negation of the exit-guard's comparison. */
    private fun negateComparison(op: Int): Int = when (op) {
        Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
        Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE
        Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE
        Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
        Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE
        Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
        Opcodes.IFGE -> Opcodes.IFLT
        Opcodes.IFLT -> Opcodes.IFGE
        Opcodes.IFGT -> Opcodes.IFLE
        Opcodes.IFLE -> Opcodes.IFGT
        Opcodes.IFEQ -> Opcodes.IFNE
        Opcodes.IFNE -> Opcodes.IFEQ
        else -> throw LoopInvariantError("unsupported guard comparison opcode $op")
    }

    private val ARRAY_STORE_OPCODES = setOf(
            Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
            Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE)

    private val NONDET_FOR_STORE = mapOf(
            Opcodes.ISTORE to ("nondetInt" to "()I"),
            Opcodes.LSTORE to ("nondetLong" to "()J"))
}
