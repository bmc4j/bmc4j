package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * AUTOMATIC, SOUND branch decomposition (see [org.bmc4j.BmcBranchDecompose] for the user-facing
 * semantics; [org.bmc4j.junit.BmcProofExtension] for the orchestration + verdict aggregation).
 *
 * Unlike a domain split (which restates an input case-split as `assume(cond)` and changes NO
 * structure), this pass performs real COMPOSITIONAL verification: it discovers value branches by CFG
 * analysis - the user marks nothing - EXTRACTS each into a separately-proven synthetic method, proves
 * that method against an automatically-derived SUMMARY, and discharges the summary back into the caller
 * at the call site (havoc the result, assume the summary). The caller then calls the summarized method
 * instead of inlining the branch body, so its formula is RESTRUCTURED: the branch's internal control
 * flow is replaced by a single flat relation predicate that the solver can simplify independently of
 * the rest of the proof.
 *
 * ## What is discovered (the LOCUS)
 *
 * A "value branch" is an `if/else` (possibly an `else if` chain, i.e. a Kotlin/Java `when`/ternary
 * expression) whose every arm ends by leaving ONE value on the stack that is stored into the same
 * result local, with no other side effects in the arms. This is exactly the idiom
 * `val r = if (c1) e1 else if (c2) e2 else e3`.
 *
 * Discovery is CALL-GRAPH WIDE, not just the proof method body: the proof entry method is usually a
 * thin harness (set up symbolic inputs, call the code under test, assert) whose branches live in the
 * CALLEES the engine inlines during symex. So we walk the call graph reachable from the proof entry
 * (bounded depth) and discover EVERY value branch in EVERY reachable method, in deterministic order.
 * Each discovered branch records the OWNER class + method it lives in. A method with no value branch is
 * not decomposed; a proof with no discoverable branch anywhere falls through to the ordinary path - a
 * sound default (we never decompose a shape we cannot extract precisely).
 *
 * ## Path condition (SOUNDNESS for non-entry / nested branches)
 *
 * A branch that is not the unconditional head of its method sits under a PATH CONDITION: the
 * leading guards in its own method that must hold to reach the region (e.g. an early-return guard
 * `if (x < 0) return -1;` before the branch). The extracted region is required to be a TOTAL pure
 * value function of its inputs (no division/remainder, no calls/allocations/field-or-array stores), so
 * the exact-relation summary is sound and precise at ANY locus with no path condition. We ADDITIONALLY
 * carry the leading-guard path condition into the LEAF as an `assume`, so the leaf certifies the
 * summary over EXACTLY the input set the parent invokes the stub under - "the parent assumes only the
 * relation the leaf certified under the same path condition." Capturing a path condition we cannot
 * replay precisely disqualifies that branch (it is simply not decomposed - the sound default).
 *
 * ## The derived artifacts (per discovered branch, all mechanically derived from the same arms)
 *
 * For a discovered branch with arms `[(C1,e1), ..., (else,en)]` returning result type `R` over inputs
 * `inputs`, reached under leading guards `pre`, the pass synthesizes four STATIC methods on the
 * branch's owner class (static so a call site in any class - the owner method, the leaf proof - can
 * reach them without an instance of the owner type):
 *
 * - **`branch$N(inputs): R`** - the EXTRACTED branch: the real arm bytecode, returning the result.
 * - **`branch$N$post(R r, inputs): boolean`** - the branch's exact input/output RELATION (each
 *   `return ei` turned into `return (r == ei)`, `.equals` for references). The auto-derived summary.
 * - **`branch$N$stub(inputs): R`** - the SUMMARIZE-at-call-site stub: `R r = nondet();
 *   assume(branch$N$post(r, inputs)); return r;`. The caller calls this instead of the branch.
 * - **`branch$N$enforce(): void`** annotated @BmcProof - the LEAF obligation:
 *   `inputs = nondet(); assume(pre(inputs)); R r = branch$N(inputs); check(branch$N$post(r, inputs));`.
 *
 * ## The derived runs (fanned out, proven in parallel)
 *
 * - **PARENT run** ([RunPlan.Parent]): the WHOLE classpath with EVERY discovered branch replaced by a
 *   call to its summarize stub, so no parent ever explores any arm control flow - each sees only a flat
 *   relation predicate.
 * - **LEAF run** ([RunPlan.Leaf]): one synthetic `branch$N$enforce` per discovered branch, proving the
 *   real branch satisfies its summary under the branch's path condition.
 *
 * ## Why it is sound AND as precise as inlining
 *
 * The summary is the branch's EXACT relation (`r == ei` under `Ci`), not a lossy abstraction. Each leaf
 * proves the real branch satisfies it under the branch's path condition (so the assumed post is
 * GUARANTEED, never an unsound narrowing); the parent assumes exactly that relation, so it loses no
 * information the inlined arms carried. The proof VERIFIES iff the parent AND every leaf VERIFIED. A
 * wrong branch value fails its leaf's relation OR surfaces in the parent's downstream check; a wrong
 * remainder fails the parent.
 */
object BranchDecomposeBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val CPROVER = "org/cprover/CProver"
    private const val BOOL_DESC = "(Z)V"
    private const val BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;"

    /** How many call-graph levels below the proof entry we search for decomposable branches. The proof
     *  entry is level 0; its direct callees level 1; and so on. Deep enough to reach the realistic SUT
     *  (harness -> code under test -> helper), bounded so a pathological graph can't blow up discovery. */
    private const val MAX_CALL_DEPTH = 6

    /**
     * Which derived run a rewrite produces. A [Parent] rewrites EVERY discovered branch (each in its
     * owner method) to its summarize stub. A [Leaf] proves the single branch at [index] - the engine's
     * entry function is that branch's synthetic `branch$index$enforce` on its owner class.
     */
    sealed interface RunPlan {
        /** The parent run: the proof with EVERY discovered branch discharged as its summary stub. */
        object Parent : RunPlan
        /** The leaf run for the discovered branch at global [index]. */
        data class Leaf(val index: Int) : RunPlan
    }

    /** The synthetic leaf entry method name for the discovered branch at global [index]. */
    @JvmStatic
    fun leafEntryMethod(index: Int): String = "branch\$$index\$enforce"

    /**
     * A processing-time error in branch decomposition. Unchecked so it propagates out of the analysis
     * path and fails the proof LOUD - a malformed decomposition must never silently run as an ordinary
     * proof.
     */
    class BranchDecomposeError(message: String) : RuntimeException(PROCESSING_TAG + message) {
        companion object {
            /** Stable leading tag so the error is recognisable across the test-worker -> Gradle boundary
             *  (it arrives as a PlaceholderException; the listener matches the message, not the type). */
            const val PROCESSING_TAG = "branch-decompose processing error: "
        }
    }

    /**
     * One discovered branch's identity, as the orchestration needs it: its global [index] (the leaf
     * key), and the OWNER class (dotted) + synthetic leaf method that the leaf run must target. The
     * orchestration builds one leaf [BmcRequest] per [BranchSite].
     */
    data class BranchSite(val index: Int, val ownerClassDotted: String, val leafMethod: String)

    /**
     * The static analysis of one proof's reachable branch structure. [sites] is every decomposable
     * value branch discovered across the entry method and its reachable callees, in deterministic
     * order. [isDecomposed] is true when at least one was found.
     */
    data class Plan(val sites: List<BranchSite>) {
        val branchCount: Int get() = sites.size
        val isDecomposed: Boolean get() = sites.isNotEmpty()
    }

    // ---- discovery -----------------------------------------------------------

    /**
     * A leading guard on the path to a branch region: the height-0 operand-push slice plus its
     * conditional jump, and whether reaching the region requires the jump to be TAKEN. Replayed (with
     * the jump rewritten to a boolean) to form the leaf's path-condition `assume`.
     */
    private class PreGuard(val region: List<AbstractInsnNode>, val jump: JumpInsnNode,
                           val takenToReach: Boolean)

    /**
     * A discovered value branch. [ownerInternal] / [methodName] / [methodDesc] name the method it lives
     * in; [index] is its global discovery index (the leaf key). [regionNodes] is the guard/arm-value
     * skeleton (EXCLUDING the trailing join store), cloned fresh on each append so label/jump nodes are
     * never shared. [inputs] are the live-in locals the region (and its [preGuards]) read. [preGuards]
     * is the leading-guard path condition the leaf assumes. [startIndex]/[endIndex] bound the region in
     * the owner method body for the parent rewrite.
     */
    private class DiscoveredBranch(
            val index: Int,
            val ownerInternal: String,
            val methodName: String,
            val methodDesc: String,
            val resultType: Type,
            /** The join-store local (the `val r = if (...) ...` idiom), or -1 when [returnJoin]. */
            val resultLocal: Int,
            /** True when the branch reconverges at an `xRETURN` of the value (the `return (...) ? ...`
             *  idiom - the whole method body is the value branch) rather than a join `xSTORE`. */
            val returnJoin: Boolean,
            val regionNodes: List<AbstractInsnNode>,
            val inputs: List<InputLocal>,
            val preGuards: List<PreGuard>,
            val startIndex: Int,
            val endIndex: Int)

    /** A live-in local the branch reads: its slot in the owner method and its type. */
    private class InputLocal(val slot: Int, val type: Type)

    /**
     * Analyse [entryClass].[methodName] on [classpath]: walk the call graph and discover EVERY
     * decomposable value branch in the entry method and its reachable callees. Returns a [Plan]; an
     * empty plan when nothing decomposable is found (the proof then runs ordinarily).
     */
    @JvmStatic
    fun analyze(classpath: String, entryClass: String, methodName: String): Plan {
        val branches = discoverAll(classpath, entryClass.replace('.', '/'), methodName)
        return Plan(branches.map {
            BranchSite(it.index, it.ownerInternal.replace('/', '.'), leafEntryMethod(it.index))
        })
    }

    /** [analyze] of a single method's own body over already-loaded class bytes (no call-graph walk).
     *  Exposed for unit tests. Returns a [Plan] of the branches in that one method. */
    internal fun analyzeBytes(bytes: ByteArray, methodName: String): Plan {
        val cn = classNode(bytes)
        val method = cn.methods.firstOrNull { it.name == methodName } ?: return Plan(emptyList())
        val branches = ArrayList<DiscoveredBranch>()
        discoverInMethod(cn.name, method, branches)
        return Plan(branches.map {
            BranchSite(it.index, it.ownerInternal.replace('/', '.'), leafEntryMethod(it.index))
        })
    }

    /**
     * Walk the call graph from [entryInternal].[entryMethod] (bounded by [MAX_CALL_DEPTH]) and collect
     * every decomposable value branch in deterministic order: the entry method first, then its callees
     * breadth-first, methods in classpath/method order, branches in body order. A class that can't be
     * read, or a callee whose owner is off the classpath (JDK / a library we don't analyse), is simply
     * not descended into - a sound default.
     */
    private fun discoverAll(classpath: String, entryInternal: String,
                            entryMethod: String): List<DiscoveredBranch> {
        val out = ArrayList<DiscoveredBranch>()
        val visited = HashSet<String>()
        // BFS over (owner, name, desc) method keys; we resolve the entry by NAME (its descriptor is the
        // proof's, recovered from the class), and callees by their call-site (owner, name, desc).
        val entryClassBytes = readClassFromClasspath(classpath, entryInternal) ?: return out
        val entryCn = classNode(entryClassBytes)
        val entryNode = entryCn.methods.firstOrNull { it.name == entryMethod } ?: return out
        data class Target(val owner: String, val name: String, val desc: String, val depth: Int)
        val queue = ArrayDeque<Target>()
        queue.add(Target(entryInternal, entryNode.name, entryNode.desc, 0))
        // Cache class reads across the walk so a class hit by several call sites is parsed once.
        val classCache = HashMap<String, ClassNode?>()
        fun cn(owner: String): ClassNode? = classCache.getOrPut(owner) {
            readClassFromClasspath(classpath, owner)?.let { classNode(it) }
        }
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            val key = "${t.owner}.${t.name}${t.desc}"
            if (!visited.add(key)) {
                continue
            }
            // Never extract from / descend into framework, model, JDK or Kotlin-runtime classes: their
            // bytecode is the product or the platform, not the user's code under test, and several are
            // special-cased by the engine + rewrite layer. We only decompose USER code.
            if (isFrameworkOwned(t.owner)) {
                continue
            }
            val owningClass = cn(t.owner) ?: continue
            val method = owningClass.methods.firstOrNull { it.name == t.name && it.desc == t.desc }
                    ?: continue
            discoverInMethod(t.owner, method, out)
            if (t.depth >= MAX_CALL_DEPTH) {
                continue
            }
            // Enqueue every call target whose owner is ON the classpath (so we can read + extract from
            // it). Owners we can't read (JDK, libraries, models) or that are framework-owned are skipped
            // - we never decompose bytecode we don't own.
            for (n in method.instructions.toArray()) {
                if (n is MethodInsnNode && n.opcode != Opcodes.INVOKEDYNAMIC) {
                    if (!isFrameworkOwned(n.owner) && readClassFromClasspath(classpath, n.owner) != null) {
                        queue.add(Target(n.owner, n.name, n.desc, t.depth + 1))
                    }
                }
            }
        }
        return out
    }

    /** True for classes we never decompose: the bmc4j framework + CProver intrinsics (special-cased by
     *  the engine and rewrite layer), the JDK, and the Kotlin runtime. Only USER code under test is a
     *  decomposition locus. */
    private fun isFrameworkOwned(internalName: String): Boolean =
            internalName.startsWith("org/bmc4j/") ||
            internalName.startsWith("org/cprover/") ||
            internalName.startsWith("java/") ||
            internalName.startsWith("javax/") ||
            internalName.startsWith("jdk/") ||
            internalName.startsWith("sun/") ||
            internalName.startsWith("kotlin/") ||
            internalName.startsWith("kotlinx/")

    /** Discover every decomposable value branch in one method body, appending each to [out] with a
     *  fresh global index. Branches are found in body order. A leading-guard path condition is captured
     *  for branches that are not the unconditional head of the method. */
    private fun discoverInMethod(ownerInternal: String, method: MethodNode,
                                 out: MutableList<DiscoveredBranch>) {
        val insns = method.instructions
        if (insns.size() == 0) {
            return
        }
        val arr = insns.toArray()
        val stackHeights = stackHeightsAtEntry(method, arr) ?: return
        var i = 0
        // The exclusive end of the previously-extracted branch region, so a chain of sibling value
        // branches in one method are each found once and not re-detected from an inner guard.
        var resumeFrom = 0
        while (i < arr.size) {
            if (i < resumeFrom) {
                i++
                continue
            }
            val node = arr[i]
            if (node is JumpInsnNode && isConditionalIntJump(node.opcode)) {
                val branch = tryExtractValueBranch(ownerInternal, method, arr, i, stackHeights,
                        out.size, resumeFrom)
                if (branch != null) {
                    out.add(branch)
                    resumeFrom = branch.endIndex
                    i = branch.endIndex
                    continue
                }
            }
            i++
        }
    }

    private fun classNode(bytes: ByteArray): ClassNode {
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES)
        return cn
    }

    /**
     * The operand-stack height at ENTRY to each instruction (indexed parallel to [arr]). Used to find
     * where a branch's guard operands begin: the region start is the latest stack-height-0 point at or
     * before the conditional jump, so the extracted region is stack-balanced. Returns null if the
     * method cannot be analysed (then it is simply not decomposed - a sound default).
     */
    private fun stackHeightsAtEntry(method: MethodNode, arr: Array<AbstractInsnNode>): IntArray? {
        return try {
            val analyzer = org.objectweb.asm.tree.analysis.Analyzer(
                    org.objectweb.asm.tree.analysis.BasicInterpreter())
            analyzer.analyze("bmc4j/BranchOwner", method)
            val frames = analyzer.frames
            IntArray(arr.size) { idx -> frames[idx]?.stackSize ?: -1 }
        } catch (e: Exception) {
            null
        }
    }

    /** True for the int/ref conditional jumps that open an `if` (not GOTO/JSR). */
    private fun isConditionalIntJump(op: Int): Boolean = when (op) {
        Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
        Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
        Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
        Opcodes.IFNULL, Opcodes.IFNONNULL -> true
        else -> false
    }

    /** The opcode of the jump that means the OPPOSITE outcome (taken<->not-taken), used to build the
     *  path-condition predicate when reaching the region requires the original jump NOT be taken. */
    private fun invertJump(op: Int): Int = when (op) {
        Opcodes.IFEQ -> Opcodes.IFNE; Opcodes.IFNE -> Opcodes.IFEQ
        Opcodes.IFLT -> Opcodes.IFGE; Opcodes.IFGE -> Opcodes.IFLT
        Opcodes.IFGT -> Opcodes.IFLE; Opcodes.IFLE -> Opcodes.IFGT
        Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE; Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
        Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE; Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
        Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE; Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
        Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE; Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ
        Opcodes.IFNULL -> Opcodes.IFNONNULL; Opcodes.IFNONNULL -> Opcodes.IFNULL
        else -> op
    }

    /**
     * Try to read a value branch starting at the conditional jump [jumpIdx] in [arr]. Walks forward
     * collecting `if`-guard / arm-value pairs until it reaches a single `xSTORE result` that every arm
     * reconverges on. Returns the [DiscoveredBranch] or null if the region is not a clean TOTAL,
     * side-effect-free value branch.
     */
    private fun tryExtractValueBranch(ownerInternal: String, method: MethodNode,
                                      arr: Array<AbstractInsnNode>, jumpIdx: Int,
                                      stackHeights: IntArray, globalIndex: Int,
                                      scanFrom: Int): DiscoveredBranch? {
        // Back up the region START to the latest stack-height-0 point at or before the jump, so the
        // region includes the instructions that PUSH the guard's operands.
        var startIdx = jumpIdx
        while (startIdx > 0 && stackHeights[startIdx] != 0) {
            startIdx--
        }
        if (stackHeights[startIdx] != 0) {
            return null
        }
        // Find the nearest following JOIN after the jump, reached with exactly the result value on the
        // stack (height == value size), so the region pushes exactly one value. The join is either:
        //  - an `xSTORE result` (the `val r = if (...) ... else ...` idiom), or
        //  - an `xRETURN` of a VALUE (the `return (...) ? ... : ...;` idiom common in a callee under
        //    test - the whole method body IS the value branch).
        var joinIdx = -1
        var resultLocal = -1
        var resultType: Type? = null
        var returnJoin = false
        var j = jumpIdx + 1
        while (j < arr.size) {
            val n = arr[j]
            val st = storeTypeOf(n)
            if (st != null) {
                if (stackHeights[j] != st.size) {
                    return null // not the clean single-value join of this branch
                }
                joinIdx = j
                resultLocal = (n as VarInsnNode).`var`
                resultType = st
                break
            }
            val rt = valueReturnTypeOf(n.opcode)
            if (rt != null) {
                if (stackHeights[j] != rt.size) {
                    return null // not the clean single-value join of this branch
                }
                joinIdx = j
                resultType = rt
                returnJoin = true
                break
            }
            if (isReturnOrThrow(n.opcode)) {
                return null // a void RETURN / ATHROW is not a value join
            }
            j++
        }
        if (joinIdx < 0 || resultType == null) {
            return null
        }
        // The arm-value region is [startIdx, joinIdx). It must be self-contained, side-effect free AND
        // TOTAL (no trapping division), so the extracted method is a total pure function of the inputs -
        // which is what makes the exact-relation summary sound and precise at any locus.
        val regionNodes = ArrayList<AbstractInsnNode>()
        for (k in startIdx until joinIdx) {
            val n = arr[k]
            if (isDisallowedInRegion(n)) {
                return null
            }
            regionNodes.add(n)
        }
        if (!jumpsStayWithinRegion(arr, startIdx, joinIdx - 1)) {
            return null
        }
        val storeIdx = joinIdx
        // The leading-guard PATH CONDITION: simple comparison guards before the region whose taken/not
        // -taken outcome must hold to reach it. If a leading guard is present but not the precise shape
        // we can replay, disqualify the branch (sound default) rather than drop its path condition.
        val preGuards = collectPreGuards(arr, scanFrom, startIdx, stackHeights) ?: return null
        // Inputs: the locals the region AND its pre-guards LOAD - the live-ins threaded as parameters.
        val inputs = collectInputs(arr, preGuards, startIdx, storeIdx)
        return DiscoveredBranch(globalIndex, ownerInternal, method.name, method.desc, resultType,
                resultLocal, returnJoin, regionNodes, inputs, preGuards, startIdx, storeIdx + 1)
    }

    /** The value type of an `xRETURN` that returns a VALUE (not the void RETURN / ATHROW), or null. */
    private fun valueReturnTypeOf(op: Int): Type? = when (op) {
        Opcodes.IRETURN -> Type.INT_TYPE
        Opcodes.LRETURN -> Type.LONG_TYPE
        Opcodes.FRETURN -> Type.FLOAT_TYPE
        Opcodes.DRETURN -> Type.DOUBLE_TYPE
        Opcodes.ARETURN -> Type.getObjectType("java/lang/Object")
        else -> null
    }

    /**
     * Collect the leading-guard PATH CONDITION for the branch region beginning at [regionStart],
     * scanning the method body from [scanFrom] (the end of the previously-claimed branch, or method
     * start). The path condition is the conjunction of the EARLY-GUARD conditional jumps on the path:
     * an `if (cond) <early exit>;` whose not-taken edge falls through to the region (the early-return
     * idiom that gates a callee's value branch).
     *
     * We model ONLY the EARLY-GUARD idiom we can replay precisely and soundly: a conditional jump
     * (at stack height 0 after its operand push) whose TAKEN edge jumps FORWARD to the region, skipping
     * an early-exit body (a `return`/`throw`) on its fall-through - i.e. `if (cond) <region>; else
     * <early exit>`, which is how `if (!cond) return; ...region...` and `if (x < 0) return; ...region`
     * compile (the test is negated, so the jump-to-region edge is the taken one). Reaching the region
     * then requires the jump TAKEN. The operand-push slice must be a simple comparison on locals /
     * constants (no call/allocation/store), so it replays as a pure predicate.
     *
     * Plain SETUP before the region (the calls/stores that define the inputs, e.g. `int x =
     * Bmc.anyInt()`) is IGNORED: the leaf havocs every input, so setup never constrains the proof and
     * needs no modelling. A guard we cannot model in this shape (a GOTO/loop skeleton, a guard whose
     * taken edge jumps backward or past the region, a guard with a call in its operand slice)
     * DISQUALIFIES the whole branch (return null - the sound default), so a captured path condition is
     * never an under-approximation that would let the leaf prove the summary somewhere the parent never
     * reaches.
     */
    private fun collectPreGuards(arr: Array<AbstractInsnNode>, scanFrom: Int, regionStart: Int,
                                 stackHeights: IntArray): List<PreGuard>? {
        val guards = ArrayList<PreGuard>()
        var k = scanFrom
        while (k < regionStart) {
            val n = arr[k]
            if (n is JumpInsnNode) {
                if (!isConditionalIntJump(n.opcode)) {
                    // A GOTO / switch on the pre-path is structured control flow (a loop, a more complex
                    // skeleton) we don't model - disqualify.
                    return null
                }
                val targetIdx = indexOfLabel(arr, n.label)
                // The taken edge must jump FORWARD to the region (skipping the early-exit body on the
                // fall-through). A backward jump (loop) or a jump past the region is a skeleton we don't
                // model.
                if (targetIdx < k || targetIdx > regionStart) {
                    return null
                }
                // The guard's operand-push slice: back to the latest height-0 point at or before it.
                var gStart = k
                while (gStart > 0 && stackHeights[gStart] != 0) {
                    gStart--
                }
                if (stackHeights[gStart] != 0) {
                    return null
                }
                val region = ArrayList<AbstractInsnNode>()
                for (g in gStart until k) {
                    if (isDisallowedInRegion(arr[g]) || arr[g] is JumpInsnNode) {
                        return null
                    }
                    region.add(arr[g])
                }
                // Reaching the region requires the jump TAKEN (its taken edge lands at the region).
                guards.add(PreGuard(region, n, true))
            }
            // Anything else (setup loads/calls/stores/constants) is ignored: inputs are havoc'd.
            k++
        }
        return guards
    }

    /** The instruction index of [label] in [arr], or -1 if absent. */
    private fun indexOfLabel(arr: Array<AbstractInsnNode>, label: LabelNode): Int {
        for (i in arr.indices) {
            if (arr[i] === label) {
                return i
            }
        }
        return -1
    }

    /** The value type of an `xSTORE` (the join store), or null if [n] is not a store. */
    private fun storeTypeOf(n: AbstractInsnNode): Type? = when (n.opcode) {
        Opcodes.ISTORE -> Type.INT_TYPE
        Opcodes.LSTORE -> Type.LONG_TYPE
        Opcodes.FSTORE -> Type.FLOAT_TYPE
        Opcodes.DSTORE -> Type.DOUBLE_TYPE
        Opcodes.ASTORE -> Type.getObjectType("java/lang/Object")
        else -> null
    }

    private fun isReturnOrThrow(op: Int): Boolean = when (op) {
        Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN,
        Opcodes.RETURN, Opcodes.ATHROW -> true
        else -> false
    }

    /** Disallow side-effecting / hard-to-extract / TRAPPING instructions inside the branch region: any
     *  store to a field or array element, object/array allocation, monitor ops, calls, a store to a
     *  local, AND integer division/remainder (which traps on a zero divisor). Forbidding division keeps
     *  the extracted region a TOTAL pure function of its inputs, so the exact-relation summary holds
     *  over the whole input domain - sound and spurious-refute-free at any locus, with no need to thread
     *  a divisor-nonzero path condition into the leaf. */
    private fun isDisallowedInRegion(n: AbstractInsnNode): Boolean {
        return when (n.opcode) {
            Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
            Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE,
            Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.NEW, Opcodes.NEWARRAY,
            Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY,
            Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE,
            Opcodes.INVOKESPECIAL, Opcodes.INVOKEDYNAMIC,
            // Trapping arithmetic: a zero divisor throws, making the region a PARTIAL function. Reject
            // so the summary stays total (else the leaf could spuriously refute on a divisor the caller
            // never actually passes as zero).
            Opcodes.IDIV, Opcodes.LDIV, Opcodes.IREM, Opcodes.LREM,
            // A store inside the arm-value region would mean the region writes another local; reject so
            // the extracted method's only output is the result.
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> true
            else -> false
        }
    }

    /** True iff every jump in [start, end] targets a label inside [start, end] (no escape, no entry
     *  from a loop back-edge). LabelNodes carry identity; we map by reference. */
    private fun jumpsStayWithinRegion(arr: Array<AbstractInsnNode>, start: Int, end: Int): Boolean {
        val regionLabels = HashSet<LabelNode>()
        for (k in start..end) {
            (arr[k] as? LabelNode)?.let { regionLabels.add(it) }
        }
        for (k in start..end) {
            val n = arr[k]
            if (n is JumpInsnNode && n.label !in regionLabels) {
                return false
            }
        }
        return true
    }

    /** Collect, in first-use order, the method locals the region [start, end] and its [preGuards] LOAD
     *  - the live-in inputs threaded as parameters of the synthetic methods. */
    private fun collectInputs(arr: Array<AbstractInsnNode>, preGuards: List<PreGuard>,
                              start: Int, end: Int): List<InputLocal> {
        val seen = LinkedHashMap<Int, Type>()
        // Pre-guard loads first (so a guard input keeps a stable, low slot), then the region's loads.
        for (g in preGuards) {
            for (n in g.region) {
                if (n is VarInsnNode) {
                    loadTypeOf(n.opcode)?.let { seen.putIfAbsent(n.`var`, it) }
                }
            }
        }
        for (k in start until end) {
            val n = arr[k]
            if (n is VarInsnNode) {
                loadTypeOf(n.opcode)?.let { seen.putIfAbsent(n.`var`, it) }
            }
        }
        return seen.entries.map { InputLocal(it.key, it.value) }
    }

    private fun loadTypeOf(op: Int): Type? = when (op) {
        Opcodes.ILOAD -> Type.INT_TYPE
        Opcodes.LLOAD -> Type.LONG_TYPE
        Opcodes.FLOAD -> Type.FLOAT_TYPE
        Opcodes.DLOAD -> Type.DOUBLE_TYPE
        Opcodes.ALOAD -> Type.getObjectType("java/lang/Object")
        else -> null
    }

    // ---- classpath read ------------------------------------------------------

    /**
     * Read the bytes of [internalName] (`a/b/C`) from the first [classpath] entry that holds it
     * (classpath order). Both directory and jar entries are searched. Fail-safe: a bad/locked entry is
     * skipped, never throws.
     */
    private fun readClassFromClasspath(classpath: String, internalName: String): ByteArray? {
        val resource = "$internalName.class"
        for (entry in classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue
            }
            try {
                val p = Path.of(entry)
                if (Files.isDirectory(p)) {
                    val f = p.resolve(resource)
                    if (Files.isRegularFile(f)) {
                        return Files.readAllBytes(f)
                    }
                } else if (Files.isRegularFile(p)
                        && (entry.endsWith(".jar", true) || entry.endsWith(".zip", true))) {
                    ZipFile(p.toFile()).use { zf ->
                        val e = zf.getEntry(resource)
                        if (e != null) {
                            return zf.getInputStream(e).use { it.readAllBytes() }
                        }
                    }
                }
            } catch (e: Exception) {
                // skip a bad entry; the next one may hold the class
            }
        }
        return null
    }

    // ---- rewrite -------------------------------------------------------------

    private val CACHE = ConcurrentHashMap<String, String>()

    /**
     * Rewrite [classpath] for one derived [run] of the decomposed proof at [entryClass].[entryMethod].
     * Memoized per (classpath, class, method, run). Discovers every branch call-graph-wide, then mirrors
     * the WHOLE classpath: each class gains the synthetic methods for branches it OWNS, each owner method
     * is rewritten (parent: branches -> stub calls; leaf: untouched, the engine runs the enforce proof).
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, entryMethod: String, run: RunPlan): String {
        val key = "$classpath|$entryClass|$entryMethod|${runKey(run)}"
        return CACHE.computeIfAbsent(key) {
            val branches = discoverAll(classpath, entryClass.replace('.', '/'), entryMethod)
            // Branches grouped by their owner class, so each class is rewritten once with all of its.
            val byOwner = branches.groupBy { it.ownerInternal }
            ClasspathMirror.mirror(classpath, "branchdecompose", { b ->
                ClasspathMirror.Transformed(rewriteClass(b, byOwner, run))
            }, runKey(run) + "|" + entryClass + "." + entryMethod)
        }
    }

    private fun runKey(run: RunPlan): String = when (run) {
        is RunPlan.Parent -> "parent"
        is RunPlan.Leaf -> "leaf|${run.index}"
    }

    /**
     * Rewrite a SINGLE class's [bytes] for [run], discovering the branches in [internalName].[methodName]
     * (that one method's own body, no call-graph walk). Exposed for unit tests; the production path uses
     * [rewrite] (call-graph-wide, classpath-mirrored).
     */
    internal fun rewriteClassForTest(bytes: ByteArray, internalName: String, methodName: String,
                                     run: RunPlan): ByteArray {
        val cn = classNode(bytes)
        if (cn.name != internalName) {
            return bytes
        }
        val method = cn.methods.firstOrNull { it.name == methodName } ?: return bytes
        val branches = ArrayList<DiscoveredBranch>()
        discoverInMethod(cn.name, method, branches)
        return rewriteClass(bytes, branches.groupBy { it.ownerInternal }, run)
    }

    /**
     * Rewrite one class [bytes] for [run], given the discovered branches grouped [byOwner] (internal
     * class name -> its branches). A class that owns no discovered branch is copied verbatim. For an
     * owner class we synthesize the four static methods per owned branch (extracted / post / stub /
     * enforce) and, on the PARENT run, rewrite each owner method to call its branches' stubs. On a LEAF
     * run the owner methods are untouched (the engine runs the relevant enforce proof as the entry).
     */
    private fun rewriteClass(bytes: ByteArray, byOwner: Map<String, List<DiscoveredBranch>>,
                             run: RunPlan): ByteArray {
        val cr = ClassReader(bytes)
        val owned = byOwner[cr.className] ?: return bytes // not an owner class - nothing to rewrite
        val cn = ClassNode()
        cr.accept(cn, ClassReader.SKIP_FRAMES)

        for (branch in owned) {
            synthesizeBranchMethods(cn, branch)
        }
        if (run is RunPlan.Parent) {
            // Rewrite each owner method (replace its branches' regions with stub calls). Group the
            // branches by the method they live in; rewrite each method's regions back-to-front so an
            // earlier splice never shifts a later region's node identities.
            for ((methodKey, group) in owned.groupBy { it.methodName + it.methodDesc }) {
                val method = cn.methods.firstOrNull { it.name + it.desc == methodKey } ?: continue
                rewriteParentMethod(method, group)
            }
        }

        val cw = SyntheticFrameClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cn.accept(cw)
        return cw.toByteArray()
    }

    /**
     * A [ClassWriter] whose frame computation never needs to LOAD the proof class or its referenced
     * types: any pair whose common supertype we can't resolve falls back to `java/lang/Object`. The
     * synthetic methods only manipulate primitives and `Object`-typed nondets, so a precise reference
     * lattice is never required; this keeps COMPUTE_FRAMES from throwing on an unloadable analysis type.
     */
    private class SyntheticFrameClassWriter(flags: Int) : ClassWriter(flags) {
        override fun getCommonSuperClass(type1: String, type2: String): String {
            if (type1 == type2) {
                return type1
            }
            return try {
                super.getCommonSuperClass(type1, type2)
            } catch (e: Throwable) {
                "java/lang/Object"
            }
        }
    }

    private fun baseName(branch: DiscoveredBranch): String = "branch\$${branch.index}"

    /** The descriptor of the extracted `branch$N(inputs): R` method. */
    private fun branchDescriptor(branch: DiscoveredBranch): String =
            Type.getMethodDescriptor(branch.resultType, *branch.inputs.map { it.type }.toTypedArray())

    /** The descriptor of `branch$N$post(R r, inputs): boolean`. */
    private fun postDescriptor(branch: DiscoveredBranch): String {
        val params = ArrayList<Type>(branch.inputs.size + 1)
        params.add(branch.resultType)
        params.addAll(branch.inputs.map { it.type })
        return Type.getMethodDescriptor(Type.BOOLEAN_TYPE, *params.toTypedArray())
    }

    /** The descriptor of `branch$N$pre(inputs): boolean` (the path-condition predicate). */
    private fun preDescriptor(branch: DiscoveredBranch): String =
            Type.getMethodDescriptor(Type.BOOLEAN_TYPE, *branch.inputs.map { it.type }.toTypedArray())

    /** Add the synthetic STATIC methods for [branch] to its owner class [cn]. */
    private fun synthesizeBranchMethods(cn: ClassNode, branch: DiscoveredBranch) {
        cn.methods.add(buildExtractedBranch(branch))
        cn.methods.add(buildPostRelation(branch))
        if (branch.preGuards.isNotEmpty()) {
            cn.methods.add(buildPreRelation(branch))
        }
        cn.methods.add(buildStub(branch, cn.name))
        cn.methods.add(buildEnforceProof(branch, cn.name))
    }

    private val STATIC = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC

    /**
     * `private static R branch$N(inputs)`: replay the branch region with the inputs read from the
     * synthetic parameters (parameter slots from 0, since the method is static). The region ends in a
     * STORE of the result local; we drop that store and instead `xRETURN` the value.
     */
    private fun buildExtractedBranch(branch: DiscoveredBranch): MethodNode {
        val mv = MethodNode(STATIC, baseName(branch), branchDescriptor(branch), null, null)
        appendRegionAsValue(mv.instructions, branch, paramSlotRemap(branch, 0))
        mv.instructions.add(InsnNode(returnOpcode(branch.resultType)))
        return mv
    }

    /**
     * `private static boolean branch$N$post(R r, inputs)`: the SAME arm control flow as the extracted
     * branch, but each arm's value `ei` is compared to the `r` parameter (`r == ei`) and that boolean
     * returned. The relation `OR_i (Ci && r == ei)` characterises the branch EXACTLY.
     */
    private fun buildPostRelation(branch: DiscoveredBranch): MethodNode {
        val mv = MethodNode(STATIC, "${baseName(branch)}\$post", postDescriptor(branch), null, null)
        // Static params: slot 0 = r (size 1 or 2), inputs after r.
        val rSlot = 0
        val remap = HashMap<Int, Int>()
        var next = rSlot + branch.resultType.size
        for (input in branch.inputs) {
            remap[input.slot] = next
            next += input.type.size
        }
        appendRegionAsValue(mv.instructions, branch, remap)
        appendEqualsBooleanReturn(mv.instructions, branch.resultType, rSlot)
        return mv
    }

    /**
     * `private static boolean branch$N$pre(inputs)`: the leading-guard PATH CONDITION as a boolean -
     * the conjunction of each leading guard's outcome on the path to the region. Built by replaying each
     * guard's operand push and emitting its comparison (taken-to-reach => the original test; not-taken
     * => the inverted test) ANDed together, short-circuiting to `return false` on the first failing
     * guard and `return true` if all hold. The leaf assumes this, so it certifies the summary over
     * exactly the inputs the parent invokes the stub under.
     */
    private fun buildPreRelation(branch: DiscoveredBranch): MethodNode {
        val mv = MethodNode(STATIC, "${baseName(branch)}\$pre", preDescriptor(branch), null, null)
        val il = mv.instructions
        val remap = paramSlotRemap(branch, 0)
        val fail = LabelNode()
        for (g in branch.preGuards) {
            // Re-emit the guard's operand-push slice with inputs remapped to the static params.
            val labelMap = HashMap<LabelNode, LabelNode>()
            for (n in g.region) {
                (n as? LabelNode)?.let { labelMap[it] = LabelNode() }
            }
            for (n in g.region) {
                if (n is VarInsnNode && loadTypeOf(n.opcode) != null) {
                    il.add(VarInsnNode(n.opcode, remap[n.`var`] ?: n.`var`))
                } else {
                    il.add(n.clone(labelMap))
                }
            }
            // To REACH the region the original jump must go a particular way. We want "guard holds ->
            // continue; else -> return false". The original jump's TAKEN edge reaches the region iff
            // takenToReach. So the predicate "this guard is on the reaching path" is the original test
            // when takenToReach, the inverted test otherwise; jump to `fail` when it does NOT hold.
            val reachOp = if (g.takenToReach) g.jump.opcode else invertJump(g.jump.opcode)
            // Continue (guard holds) when reachOp is satisfied; otherwise fall to `fail`. Emit: if NOT
            // reach -> goto fail. The negation of reachOp is invertJump(reachOp).
            il.add(JumpInsnNode(invertJump(reachOp), fail))
        }
        // All guards held: true.
        il.add(InsnNode(Opcodes.ICONST_1))
        il.add(InsnNode(Opcodes.IRETURN))
        il.add(fail)
        il.add(InsnNode(Opcodes.ICONST_0))
        il.add(InsnNode(Opcodes.IRETURN))
        return mv
    }

    /**
     * `private static R branch$N$stub(inputs)`: `R r = nondet(); assume(branch$N$post(r, inputs));
     * return r;`. The SUMMARIZE direction at the bytecode layer - the caller calls it instead of inlining
     * the branch, so the caller's formula carries only the flat relation predicate.
     */
    private fun buildStub(branch: DiscoveredBranch, owner: String): MethodNode {
        val mv = MethodNode(STATIC, "${baseName(branch)}\$stub", branchDescriptor(branch), null, null)
        val il = mv.instructions
        // Params occupy slots [0, paramSize); the fresh result local sits above them.
        var rSlot = 0
        for (input in branch.inputs) {
            rSlot += input.type.size
        }
        appendNondet(il, branch.resultType)
        il.add(VarInsnNode(storeOpcode(branch.resultType), rSlot))
        // assume(branch$N$post(r, inputs))
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        var p = 0
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), p))
            p += input.type.size
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "${baseName(branch)}\$post",
                postDescriptor(branch), false))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false))
        // return r;
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        il.add(InsnNode(returnOpcode(branch.resultType)))
        return mv
    }

    /**
     * `@BmcProof void branch$N$enforce()`: `inputs = nondet(); assume(branch$N$pre(inputs)); R r =
     * branch$N(inputs); check(branch$N$post(r, inputs));`. The LEAF obligation - it proves the real
     * branch satisfies the summary the parent assumes, UNDER the branch's path condition, closing the
     * assume-guarantee loop. The `pre` assume is omitted when the branch has no leading guards (an
     * unconditional head).
     */
    private fun buildEnforceProof(branch: DiscoveredBranch, owner: String): MethodNode {
        // Public + STATIC so jbmc can invoke it as an entry function with no receiver.
        val mv = MethodNode(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "${baseName(branch)}\$enforce",
                "()V", null, null)
        mv.visitAnnotation(BMC_PROOF_DESC, true).visitEnd()
        val il = mv.instructions
        // Allocate input locals from slot 0 (static, no `this`); nondet each.
        val inputSlot = HashMap<Int, Int>()
        var slot = 0
        for (input in branch.inputs) {
            appendNondet(il, input.type)
            il.add(VarInsnNode(storeOpcode(input.type), slot))
            inputSlot[input.slot] = slot
            slot += input.type.size
        }
        // assume(branch$N$pre(inputs)) - the path condition, when there is one.
        if (branch.preGuards.isNotEmpty()) {
            for (input in branch.inputs) {
                il.add(VarInsnNode(loadOpcode(input.type), inputSlot[input.slot]!!))
            }
            il.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "${baseName(branch)}\$pre",
                    preDescriptor(branch), false))
            il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false))
        }
        // R r = branch$N(inputs);
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), inputSlot[input.slot]!!))
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, baseName(branch), branchDescriptor(branch),
                false))
        val rSlot = slot
        il.add(VarInsnNode(storeOpcode(branch.resultType), rSlot))
        // check(branch$N$post(r, inputs));
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), inputSlot[input.slot]!!))
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "${baseName(branch)}\$post",
                postDescriptor(branch), false))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, BMC, "check", BOOL_DESC, false))
        il.add(InsnNode(Opcodes.RETURN))
        return mv
    }

    /**
     * PARENT rewrite of one owner [method]: replace each of its discovered branch regions with a call to
     * `branch$N$stub(inputs)`, leaving the stub's (havoc'd, post-constrained) result on the stack
     * exactly where the original join STORE consumed it. [branches] all live in [method]; we splice
     * back-to-front so an earlier splice never invalidates a later region's node identities.
     */
    private fun rewriteParentMethod(method: MethodNode, branches: List<DiscoveredBranch>) {
        val arr = method.instructions.toArray()
        for (branch in branches.sortedByDescending { it.startIndex }) {
            val replacement = InsnList()
            for (input in branch.inputs) {
                replacement.add(VarInsnNode(loadOpcode(input.type), input.slot))
            }
            replacement.add(MethodInsnNode(Opcodes.INVOKESTATIC, branch.ownerInternal,
                    "${baseName(branch)}\$stub", branchDescriptor(branch), false))
            // Consume the stub's (havoc'd, post-constrained) result exactly as the inline branch's join
            // did: store it into the join local, or return it (the return-join idiom).
            if (branch.returnJoin) {
                replacement.add(InsnNode(returnOpcode(branch.resultType)))
            } else {
                replacement.add(VarInsnNode(storeOpcode(branch.resultType), branch.resultLocal))
            }

            val first = arr[branch.startIndex]
            val last = arr[branch.endIndex - 1]
            method.instructions.insertBefore(first, replacement)
            var node: AbstractInsnNode? = first
            while (node != null) {
                val nextNode = node.next
                method.instructions.remove(node)
                if (node === last) {
                    break
                }
                node = nextNode
            }
        }
    }

    // ---- region replay + value helpers --------------------------------------

    /**
     * Append the branch [region] to [il], remapping the owner method's input locals to the synthetic
     * method's parameter slots and DROPPING the trailing join STORE (so the branch's result value is
     * left on the operand stack). [remap] maps each input's owner slot to its synthetic-method slot.
     */
    private fun appendRegionAsValue(il: InsnList, branch: DiscoveredBranch, remap: Map<Int, Int>) {
        val labelMap = HashMap<LabelNode, LabelNode>()
        for (n in branch.regionNodes) {
            (n as? LabelNode)?.let { labelMap[it] = LabelNode() }
        }
        for (n in branch.regionNodes) {
            if (n is VarInsnNode && loadTypeOf(n.opcode) != null) {
                il.add(VarInsnNode(n.opcode, remap[n.`var`] ?: n.`var`))
            } else {
                il.add(n.clone(labelMap))
            }
        }
    }

    /** Map each input's owner-method slot to the synthetic STATIC method's parameter slot, starting at
     *  [base] (0 for a pure-input method; past `r` for the post relation). */
    private fun paramSlotRemap(branch: DiscoveredBranch, base: Int): Map<Int, Int> {
        val remap = HashMap<Int, Int>()
        var next = base
        for (input in branch.inputs) {
            remap[input.slot] = next
            next += input.type.size
        }
        return remap
    }

    /** Emit `boolean := (r == valueOnStack)` for [resultType] (with `r` in slot [rSlot]) and IRETURN.
     *  For primitives an equality compare; for references `.equals`. */
    private fun appendEqualsBooleanReturn(il: InsnList, resultType: Type, rSlot: Int) {
        when (resultType.sort) {
            Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN -> {
                il.add(VarInsnNode(Opcodes.ILOAD, rSlot))
                appendIntEqBoolean(il)
            }
            Type.LONG -> {
                il.add(VarInsnNode(Opcodes.LLOAD, rSlot))
                il.add(InsnNode(Opcodes.LCMP))
                appendZeroEqBoolean(il)
            }
            Type.FLOAT -> {
                il.add(VarInsnNode(Opcodes.FLOAD, rSlot))
                il.add(InsnNode(Opcodes.FCMPL))
                appendZeroEqBoolean(il)
            }
            Type.DOUBLE -> {
                il.add(VarInsnNode(Opcodes.DLOAD, rSlot))
                il.add(InsnNode(Opcodes.DCMPL))
                appendZeroEqBoolean(il)
            }
            else -> {
                // Reference: value.equals(r) (value is on the stack; r is the parameter).
                il.add(VarInsnNode(Opcodes.ALOAD, rSlot))
                il.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals",
                        "(Ljava/lang/Object;)Z", false))
            }
        }
        il.add(InsnNode(Opcodes.IRETURN))
    }

    /** stack: int a, int b -> push (a == b ? 1 : 0). */
    private fun appendIntEqBoolean(il: InsnList) {
        val eq = LabelNode()
        val done = LabelNode()
        il.add(JumpInsnNode(Opcodes.IF_ICMPEQ, eq))
        il.add(InsnNode(Opcodes.ICONST_0))
        il.add(JumpInsnNode(Opcodes.GOTO, done))
        il.add(eq)
        il.add(InsnNode(Opcodes.ICONST_1))
        il.add(done)
    }

    /** stack: int cmp (0 iff equal) -> push (cmp == 0 ? 1 : 0). */
    private fun appendZeroEqBoolean(il: InsnList) {
        val eq = LabelNode()
        val done = LabelNode()
        il.add(JumpInsnNode(Opcodes.IFEQ, eq))
        il.add(InsnNode(Opcodes.ICONST_0))
        il.add(JumpInsnNode(Opcodes.GOTO, done))
        il.add(eq)
        il.add(InsnNode(Opcodes.ICONST_1))
        il.add(done)
    }

    private fun appendNondet(il: InsnList, type: Type) {
        val (name, desc) = when (type.sort) {
            Type.INT -> "nondetInt" to "()I"
            Type.SHORT -> "nondetShort" to "()S"
            Type.BYTE -> "nondetByte" to "()B"
            Type.CHAR -> "nondetChar" to "()C"
            Type.BOOLEAN -> "nondetBoolean" to "()Z"
            Type.LONG -> "nondetLong" to "()J"
            Type.FLOAT -> "nondetFloat" to "()F"
            Type.DOUBLE -> "nondetDouble" to "()D"
            else -> {
                il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "nondetWithoutNull",
                        "()Ljava/lang/Object;", false))
                il.add(org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, type.internalName))
                return
            }
        }
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, name, desc, false))
    }

    private fun returnOpcode(t: Type): Int = t.getOpcode(Opcodes.IRETURN)
    private fun storeOpcode(t: Type): Int = t.getOpcode(Opcodes.ISTORE)
    private fun loadOpcode(t: Type): Int = t.getOpcode(Opcodes.ILOAD)
}
