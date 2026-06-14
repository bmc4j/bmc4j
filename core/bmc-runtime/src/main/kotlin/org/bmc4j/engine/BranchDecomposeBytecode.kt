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
 * structure), this pass performs real COMPOSITIONAL verification: it discovers a branch in the proof
 * method by CFG analysis - the user marks nothing - EXTRACTS it into a separately-proven synthetic
 * method, proves that method against an automatically-derived SUMMARY, and discharges the summary back
 * into the parent at the call site (havoc the result, assume the summary). The parent then calls the
 * summarized method instead of inlining the branch body, so the parent's formula is RESTRUCTURED: the
 * branch's internal control flow is replaced by a single flat relation predicate that the solver can
 * simplify independently of the rest of the proof.
 *
 * ## What is discovered (increment 1)
 *
 * The FIRST top-level "value branch" in the proof method: an `if/else` (possibly an `else if` chain,
 * i.e. a Kotlin/Java `when`/ternary expression) whose every arm ends by leaving ONE value on the stack
 * that is stored into the same result local, with no other side effects in the arms. This is exactly
 * the proof idiom `val r = if (c1) e1 else if (c2) e2 else e3`. A method that does not match this
 * shape is NOT decomposed ([Plan.isDecomposed] is false) and falls through to the ordinary proof path
 * - a sound default (we never decompose a shape we cannot extract precisely).
 *
 * ## The three derived artifacts (all mechanically derived from the same discovered arms)
 *
 * For a discovered branch with arms `[(C1,e1), (C2,e2), ..., (else,en)]` returning result type `R`
 * over the live-in inputs `inputs`, the pass synthesizes three methods on the proof class:
 *
 * - **`branch$N(inputs): R`** - the EXTRACTED branch: the real arm bytecode, `if (C1) return e1; ...
 *   else return en;`. The branch body, verbatim, in its own method.
 * - **`branch$N$post(R r, inputs): boolean`** - the branch's exact input/output RELATION, derived from
 *   the same arms by turning each `return ei` into `return (r == ei)` (`.equals` for references):
 *   `if (C1) return r == e1; ... else return r == en;`. This is the auto-derived summary - as precise
 *   as inlining, because it IS the branch's semantics.
 * - **`branch$N$stub(self, inputs): R`** - the SUMMARIZE-at-call-site stub: `R r = nondet();
 *   assume(self.branch$N$post(r, inputs)); return r;`. The parent calls this instead of the branch.
 *
 * ## The two derived runs (fanned out, proven in parallel)
 *
 * - **PARENT run** ([RunPlan.Parent]): the proof method with the inline branch replaced by a call to
 *   `branch$N$stub(inputs)` (leaving the havoc'd-and-constrained result where the original `ISTORE r`
 *   expected it), so the parent NEVER explores the arm control flow - it sees only the flat relation
 *   predicate. This is the discharge: `check(reachable); havoc(r); assume(post)`.
 * - **LEAF run** ([RunPlan.Leaf]): a synthetic `@BmcProof void branch$N$enforce()` that proves the
 *   real branch satisfies its summary: `inputs = nondet(); R r = self.branch$N(inputs);
 *   check(self.branch$N$post(r, inputs));`.
 *
 * ## Why it is sound AND as precise as inlining
 *
 * The summary is the branch's EXACT relation (`r == ei` under `Ci`), not a lossy abstraction. The leaf
 * proves the real branch satisfies it (so the assumed post is GUARANTEED, never an unsound narrowing);
 * the parent assumes exactly that relation, so it loses no information the inlined arms carried.
 * Together the leaf (the branch is correct against its summary) and the parent (the proof is correct
 * given the summary) are an assume-guarantee decomposition: VERIFIED iff BOTH verified. A refutation on
 * either side surfaces its counterexample - a bug inside the branch fails the leaf, a bug in the
 * remainder fails the parent.
 *
 * Mirrors the sibling passes' [ClasspathMirror] mechanics; the run identity is folded into the mirror
 * key so the leaf mirror and the parent mirror are distinct, complete cache entries.
 */
object BranchDecomposeBytecode {

    private const val BMC = "org/bmc4j/Bmc"
    private const val CPROVER = "org/cprover/CProver"
    private const val BOOL_DESC = "(Z)V"
    private const val BMC_PROOF_DESC = "Lorg/bmc4j/BmcProof;"

    /**
     * Which derived run a rewrite produces. [proofMethod] is the ORIGINAL proof method whose branch is
     * decomposed (the discovery target); for a leaf run the engine's entry function is the synthetic
     * `branch$N$enforce` while discovery still keys off [proofMethod]. [index] is which discovered
     * branch (increment 1: always 0).
     */
    sealed interface RunPlan {
        val proofMethod: String
        val index: Int

        /** The parent run: the proof with branch [index] replaced by its summarize stub call. */
        data class Parent(override val proofMethod: String, override val index: Int) : RunPlan
        /** The leaf run: the synthetic enforce proof for branch [index]. */
        data class Leaf(override val proofMethod: String, override val index: Int) : RunPlan
    }

    /** The synthetic leaf entry method name for branch [index] of [proofMethod]'s decomposition. */
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
     * The static analysis of one proof method's branch structure. [branchCount] is the number of
     * decomposable top-level value branches discovered (increment 1 extracts the FIRST one, so
     * [branchCount] is 0 or 1).
     */
    data class Plan(val branchCount: Int) {
        /** True when at least one decomposable branch was discovered. */
        val isDecomposed: Boolean get() = branchCount > 0
    }

    // ---- discovery -----------------------------------------------------------

    /** One arm of a discovered value branch: the guard label chain reaching it, the instructions that
     *  compute its returned value (a slice of the method body), and a flag for the trailing `else`. */
    private class Arm(val valueInsns: InsnList, val isElse: Boolean)

    /**
     * A discovered value branch: a top-level `if/else(-if chain)` expression whose arms each leave a
     * single result value (of [resultType]) on the stack stored into [resultLocal]. The branch reads
     * the live-in [inputs] (method locals it loads). [headInsns] is the guard/control-flow skeleton -
     * the conditional jumps and the per-arm value computations - that the synthetic methods replay.
     */
    private class DiscoveredBranch(
            val resultType: Type,
            val resultLocal: Int,
            /** The branch region's source nodes (guards + arm value computations, EXCLUDING the trailing
             *  join store), in order, as references into the proof method. Cloned fresh on each append
             *  into a synthetic method so the same label/jump nodes are never shared across lists. */
            val regionNodes: List<AbstractInsnNode>,
            /** The local slot a synthetic method's body loads/stores against for each input. */
            val inputs: List<InputLocal>,
            /** Index of the first and (exclusive) last instruction of the branch region in the body. */
            val startIndex: Int,
            val endIndex: Int)

    /** A live-in local the branch reads: its slot in the proof method and its type. */
    private class InputLocal(val slot: Int, val type: Type)

    /**
     * Analyse [entryClass].[methodName] on [classpath]. Returns a [Plan]; `Plan(0)` when no
     * decomposable branch is found (the proof then runs ordinarily).
     */
    @JvmStatic
    fun analyze(classpath: String, entryClass: String, methodName: String): Plan {
        val internalName = entryClass.replace('.', '/')
        val bytes = readClassFromClasspath(classpath, internalName) ?: return Plan(0)
        return analyzeBytes(bytes, methodName)
    }

    /** [analyze] over already-loaded class bytes. Exposed for unit tests. */
    internal fun analyzeBytes(bytes: ByteArray, methodName: String): Plan {
        val method = methodNode(bytes, methodName) ?: return Plan(0)
        val branch = discoverFirstValueBranch(method)
        return Plan(if (branch == null) 0 else 1)
    }

    private fun methodNode(bytes: ByteArray, methodName: String): MethodNode? {
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES)
        return cn.methods.firstOrNull { it.name == methodName }
    }

    /**
     * Discover the FIRST top-level value branch in [method] (increment 1). The shape we extract:
     *
     *  - a conditional jump `IF*` whose two successors (fall-through = then-arm, jump target = the
     *    next guard or the else-arm) each compute ONE value and store it into the SAME result local,
     *    reconverging at the store. An `else if` chain is a sequence of such guards before a final
     *    fall-through arm.
     *
     * We require: every arm pushes exactly one value of a primitive/`String`/`Object` result type and
     * the arms reconverge at a single `xSTORE result`. Arms must be side-effect free (no field/array
     * writes, no calls other than the proof's own helper reads) so the extracted method is a pure
     * function of the inputs - this is what makes the relation summary exact. A method that does not
     * match returns null (not decomposed).
     *
     * The conservative, robust detector keys on the bytecode the Kotlin/Java compilers emit for a
     * `val r = if (...) ... else ...` value expression: a chain of conditional jumps, each arm ending
     * in a value left on the stack, all joining at one store. We capture the region from the first
     * guard up to and including that store.
     */
    private fun discoverFirstValueBranch(method: MethodNode): DiscoveredBranch? {
        val insns = method.instructions
        if (insns.size() == 0) {
            return null
        }
        val arr = insns.toArray()
        val stackHeights = stackHeightsAtEntry(method, arr) ?: return null
        var i = 0
        while (i < arr.size) {
            val node = arr[i]
            if (node is JumpInsnNode && isConditionalIntJump(node.opcode)) {
                val branch = tryExtractValueBranch(arr, i, stackHeights)
                if (branch != null) {
                    return branch
                }
            }
            i++
        }
        return null
    }

    /**
     * The operand-stack height at ENTRY to each instruction (indexed parallel to [arr]), via ASM's
     * dataflow [org.objectweb.asm.tree.analysis.Analyzer]. Used to find where a branch's guard operands
     * begin: the region start is the latest stack-height-0 point at or before the conditional jump, so
     * the extracted region is stack-balanced (it pushes exactly its one result value). Returns null if
     * the method cannot be analysed (then it is simply not decomposed - a sound default).
     */
    private fun stackHeightsAtEntry(method: MethodNode, arr: Array<AbstractInsnNode>): IntArray? {
        return try {
            val analyzer = org.objectweb.asm.tree.analysis.Analyzer(
                    org.objectweb.asm.tree.analysis.BasicInterpreter())
            // Owner name is irrelevant to stack heights (BasicInterpreter ignores the type lattice
            // detail we don't use); a placeholder owner is fine for height computation.
            analyzer.analyze("bmc4j/BranchOwner", method)
            val frames = analyzer.frames
            // A null frame is an UNREACHABLE node (often a trailing label/line-number pseudo-instruction
            // after the final return); we record -1 for it. The branch detector only ever reads heights
            // at REAL instructions (the conditional jump and the join store), which are reachable and so
            // carry a real frame, so -1 sentinels at pseudo-nodes never affect the result.
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

    /**
     * Try to read a value branch starting at the conditional jump [startIdx] in [arr]. Walks forward
     * collecting `if`-guard / arm-value pairs until it reaches a single `xSTORE result` that every arm
     * reconverges on. Returns the [DiscoveredBranch] or null if the region is not a clean value branch.
     *
     * The accepted region is: `[guard0][arm0-value][GOTO end][guard1?][arm1-value]...[elseArm-value]
     * STORE result`. We find the join store as the first `xSTORE` instruction that the post-branch
     * code reads, with all arms targeting it. To stay robust and sound we accept only when:
     *  - the region contains no side-effecting instructions (field/array stores, non-helper calls,
     *    NEW/monitor), so the extracted method is pure; and
     *  - the region ends in exactly one store of a value of a supported result type; and
     *  - every byte between [startIdx] and the store is part of the guards/arm-values (a contiguous
     *    region with no external jump in or out beyond the arms' own GOTOs to the join).
     */
    private fun tryExtractValueBranch(arr: Array<AbstractInsnNode>, jumpIdx: Int,
                                      stackHeights: IntArray): DiscoveredBranch? {
        // Back up the region START to the latest stack-height-0 point at or before the jump, so the
        // region includes the instructions that PUSH the guard's operands (e.g. `iload x; bipush -10`
        // before `if_icmpge`). The compiler emits the guard operand pushes from an empty stack, so the
        // height-0 point right before them is the true branch start.
        var startIdx = jumpIdx
        while (startIdx > 0 && stackHeights[startIdx] != 0) {
            startIdx--
        }
        if (stackHeights[startIdx] != 0) {
            return null
        }
        // Find the nearest following store (the join) AFTER the jump. The store must be reached with
        // exactly the result value on the stack (height == value size), so the region pushes exactly
        // one value.
        var storeIdx = -1
        var resultLocal = -1
        var resultType: Type? = null
        var j = jumpIdx + 1
        while (j < arr.size) {
            val n = arr[j]
            val st = storeTypeOf(n)
            if (st != null) {
                if (stackHeights[j] != st.size) {
                    return null // the store is not the clean single-value join of this branch
                }
                storeIdx = j
                resultLocal = (n as VarInsnNode).`var`
                resultType = st
                break
            }
            if (isReturnOrThrow(n.opcode)) {
                return null
            }
            j++
        }
        if (storeIdx < 0 || resultType == null) {
            return null
        }
        // The arm-value region is [startIdx, storeIdx) - everything up to but NOT including the join
        // store. It must be self-contained and side-effect free so the extracted method is a pure
        // function of the inputs (which is what makes the relation summary exact).
        val regionNodes = ArrayList<AbstractInsnNode>()
        for (k in startIdx until storeIdx) {
            val n = arr[k]
            if (isDisallowedInRegion(n)) {
                return null
            }
            regionNodes.add(n)
        }
        // No jump may leave the region except to labels inside it; a jump whose target is outside
        // [startIdx, storeIdx) is a loop/early-exit we do not handle in increment 1.
        if (!jumpsStayWithinRegion(arr, startIdx, storeIdx - 1)) {
            return null
        }
        // Collect the live-in inputs: the locals the region LOADS (it stores nothing - the join store
        // is excluded - so every load is of a live-in).
        val inputs = collectInputs(arr, startIdx, storeIdx)
        return DiscoveredBranch(resultType, resultLocal, regionNodes, inputs, startIdx, storeIdx + 1)
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

    /** Disallow side-effecting / hard-to-extract instructions inside the branch region: any store to a
     *  field or array element, object/array allocation, monitor ops, and calls other than pure helper
     *  reads (we allow no calls at all in increment 1 - a pure value branch needs none). */
    private fun isDisallowedInRegion(n: AbstractInsnNode): Boolean {
        return when (n.opcode) {
            Opcodes.PUTFIELD, Opcodes.PUTSTATIC, Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
            Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE,
            Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.NEW, Opcodes.NEWARRAY,
            Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY,
            Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE,
            Opcodes.INVOKESPECIAL, Opcodes.INVOKEDYNAMIC -> true
            // A store inside the arm-value region (the trailing join store is excluded by the caller)
            // would mean the region also writes another local; reject so the extracted method's only
            // output is the result.
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> true
            else -> false
        }
    }

    /** True iff every jump in [start, end] targets a label inside [start, end] (no escape, no entry
     *  from a loop back-edge). LabelNodes in the array carry identity; we map by reference. */
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

    /** Collect, in first-use order, the method locals the region [start, end] LOADS - the live-in
     *  inputs threaded as parameters of the synthetic methods. (The region is side-effect free and
     *  stores nothing but the join, so every load is of a live-in.) */
    private fun collectInputs(arr: Array<AbstractInsnNode>, start: Int, end: Int): List<InputLocal> {
        val seen = LinkedHashMap<Int, Type>()
        for (k in start until end) {
            val n = arr[k]
            if (n is VarInsnNode) {
                val t = loadTypeOf(n.opcode) ?: continue
                seen.putIfAbsent(n.`var`, t)
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
     * Rewrite [classpath] for one derived [run] of the decomposed proof at
     * [entryClass].[RunPlan.proofMethod]. Memoized per (classpath, class, run). Both directory and jar
     * entries are mirrored.
     */
    @JvmStatic
    fun rewrite(classpath: String, entryClass: String, run: RunPlan): String {
        val key = "$classpath|$entryClass|${runKey(run)}"
        return CACHE.computeIfAbsent(key) {
            ClasspathMirror.mirror(classpath, "branchdecompose", { b ->
                ClasspathMirror.Transformed(
                        rewriteClass(b, entryClass.replace('.', '/'), run.proofMethod, run))
            }, runKey(run) + "|" + entryClass)
        }
    }

    private fun runKey(run: RunPlan): String = when (run) {
        is RunPlan.Parent -> "parent|${run.proofMethod}|${run.index}"
        is RunPlan.Leaf -> "leaf|${run.proofMethod}|${run.index}"
    }

    /**
     * Rewrite the proof class [internalName] for [run]; every other class is copied verbatim. The
     * entry class gains the synthetic `branch$N`, `branch$N$post`, `branch$N$stub` methods; the proof
     * method itself is rewritten (parent: branch -> stub call) or the leaf enforce proof is what the
     * engine runs as the entry. Exposed for unit tests.
     */
    internal fun rewriteClass(bytes: ByteArray, internalName: String, methodName: String,
                              run: RunPlan): ByteArray {
        val cr = ClassReader(bytes)
        if (cr.className != internalName) {
            return bytes // not the entry class - nothing to rewrite
        }
        val cn = ClassNode()
        cr.accept(cn, ClassReader.SKIP_FRAMES)
        val method = cn.methods.firstOrNull { it.name == methodName } ?: return bytes
        val branch = discoverFirstValueBranch(method) ?: return bytes

        val base = "branch\$${run.index}"
        synthesizeBranchMethods(cn, branch, base)

        when (run) {
            is RunPlan.Parent -> rewriteParent(method, branch, base, cn.name)
            is RunPlan.Leaf -> {
                // The leaf entry is the synthetic enforce proof; demote the original proof so JUnit /
                // the engine runs branch$N$enforce as the entry. We leave the original method intact
                // (the engine targets the entry function the request names; see BmcProofExtension).
            }
        }

        // COMPUTE_FRAMES + COMPUTE_MAXS: the synthetic methods and the parent rewrite introduce new
        // control flow, so frames/maxs are recomputed from scratch. We do NOT pass the ClassReader to
        // the writer: every method is re-emitted from the (modified) ClassNode, so there is no verbatim
        // copy to preserve, and a reader-linked writer could otherwise reuse stale frames.
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

    /**
     * Add the three synthetic methods to [cn]:
     *  - `branch$N(inputs): R` (the extracted arms, returning the result),
     *  - `branch$N$post(R r, inputs): boolean` (the arms turned into `r == ei`),
     *  - `branch$N$stub(inputs): R` (havoc + assume(post); the parent's summarize call),
     *  - `branch$N$enforce(): void` annotated @BmcProof (the leaf obligation).
     */
    private fun synthesizeBranchMethods(cn: ClassNode, branch: DiscoveredBranch, base: String) {
        cn.methods.add(buildExtractedBranch(branch, base))
        cn.methods.add(buildPostRelation(branch, base))
        cn.methods.add(buildStub(branch, base, cn.name))
        cn.methods.add(buildEnforceProof(branch, base, cn.name))
    }

    /**
     * `private R branch$N(inputs)`: replay the branch region with the inputs read from the synthetic
     * parameters (slot i+1 for the i-th input; slot 0 is `this`). The region ends in a STORE of the
     * result local; we drop that store and instead `xRETURN` the value, so the method returns the
     * branch's result.
     */
    private fun buildExtractedBranch(branch: DiscoveredBranch, base: String): MethodNode {
        val mv = MethodNode(Opcodes.ACC_PRIVATE, base, branchDescriptor(branch), null, null)
        val remap = inputSlotRemap(branch)
        appendRegionAsValue(mv.instructions, branch, remap)
        mv.instructions.add(InsnNode(returnOpcode(branch.resultType)))
        return mv
    }

    /**
     * `private boolean branch$N$post(R r, inputs)`: the SAME arm control flow as the extracted branch,
     * but each arm's value `ei` is compared to the `r` parameter (`r == ei`) and that boolean returned.
     * The relation `OR_i (Ci && r == ei)` characterises the branch EXACTLY, so it is both provable by
     * the leaf and precise enough for the parent.
     *
     * Built by replaying the region with the inputs read from parameter slots (r is slot 1, the inputs
     * follow), and replacing the trailing STORE-of-result with: load `r`, compare with the value on the
     * stack, push the 0/1 result, and IRETURN.
     */
    private fun buildPostRelation(branch: DiscoveredBranch, base: String): MethodNode {
        val mv = MethodNode(Opcodes.ACC_PRIVATE, "$base\$post", postDescriptor(branch), null, null)
        // Parameter slots: 0 = this, 1 = r (size 1 or 2), inputs after r.
        val rSlot = 1
        val rSize = branch.resultType.size
        val remap = HashMap<Int, Int>()
        var next = rSlot + rSize
        for (input in branch.inputs) {
            remap[input.slot] = next
            next += input.type.size
        }
        appendRegionAsValue(mv.instructions, branch, remap)
        // The branch value is now on top of the stack; emit `r == value -> boolean` and return it.
        appendEqualsBooleanReturn(mv.instructions, branch.resultType, rSlot)
        return mv
    }

    /**
     * `private R branch$N$stub(inputs)`: `R r = nondet(); assume(this.branch$N$post(r, inputs));
     * return r;`. This is the SUMMARIZE direction realized at the bytecode layer for a synthetic
     * method - the parent calls it instead of inlining the branch, so the parent's formula carries
     * only the flat relation predicate.
     */
    private fun buildStub(branch: DiscoveredBranch, base: String, owner: String): MethodNode {
        val mv = MethodNode(Opcodes.ACC_PRIVATE, "$base\$stub", branchDescriptor(branch), null, null)
        val il = mv.instructions
        // R r = nondet(); store into a fresh local above the params.
        var rSlot = 1
        for (input in branch.inputs) {
            rSlot += input.type.size
        }
        appendNondet(il, branch.resultType)
        il.add(VarInsnNode(storeOpcode(branch.resultType), rSlot))
        // assume(this.branch$N$post(r, inputs))
        il.add(VarInsnNode(Opcodes.ALOAD, 0))
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        var p = 1
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), p))
            p += input.type.size
        }
        il.add(MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "$base\$post",
                postDescriptor(branch), false))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, CPROVER, "assume", BOOL_DESC, false))
        // return r;
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        il.add(InsnNode(returnOpcode(branch.resultType)))
        return mv
    }

    /**
     * `@BmcProof void branch$N$enforce()`: `inputs = nondet(); R r = this.branch$N(inputs);
     * check(this.branch$N$post(r, inputs));`. The LEAF obligation - it proves the real branch satisfies
     * the summary the parent assumes, closing the assume-guarantee loop.
     */
    private fun buildEnforceProof(branch: DiscoveredBranch, base: String, owner: String): MethodNode {
        val mv = MethodNode(Opcodes.ACC_PUBLIC, "$base\$enforce", "()V", null, null)
        // @BmcProof so the engine recognises it as a proof entry.
        mv.visitAnnotation(BMC_PROOF_DESC, true).visitEnd()
        val il = mv.instructions
        // Allocate input locals above `this`: slot 1..; nondet each.
        val inputSlot = HashMap<Int, Int>()
        var slot = 1
        for (input in branch.inputs) {
            appendNondet(il, input.type)
            il.add(VarInsnNode(storeOpcode(input.type), slot))
            inputSlot[input.slot] = slot
            slot += input.type.size
        }
        // R r = this.branch$N(inputs);
        il.add(VarInsnNode(Opcodes.ALOAD, 0))
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), inputSlot[input.slot]!!))
        }
        il.add(MethodInsnNode(Opcodes.INVOKESPECIAL, owner, base, branchDescriptor(branch), false))
        val rSlot = slot
        il.add(VarInsnNode(storeOpcode(branch.resultType), rSlot))
        // check(this.branch$N$post(r, inputs));
        il.add(VarInsnNode(Opcodes.ALOAD, 0))
        il.add(VarInsnNode(loadOpcode(branch.resultType), rSlot))
        for (input in branch.inputs) {
            il.add(VarInsnNode(loadOpcode(input.type), inputSlot[input.slot]!!))
        }
        il.add(MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "$base\$post",
                postDescriptor(branch), false))
        il.add(MethodInsnNode(Opcodes.INVOKESTATIC, BMC, "check", BOOL_DESC, false))
        il.add(InsnNode(Opcodes.RETURN))
        return mv
    }

    /**
     * PARENT rewrite: replace the inline branch region in the proof method with a call to
     * `branch$N$stub(inputs)`, leaving the stub's (havoc'd, post-constrained) result on the stack
     * exactly where the original join STORE consumed it. The parent thus never explores the arms.
     */
    private fun rewriteParent(method: MethodNode, branch: DiscoveredBranch, base: String,
                              owner: String) {
        val arr = method.instructions.toArray()
        // Build the replacement: ALOAD this; load each input; INVOKESPECIAL stub; (result on stack);
        // then a STORE of the result into the original join local, exactly as the inline branch did.
        val replacement = InsnList()
        replacement.add(VarInsnNode(Opcodes.ALOAD, 0))
        for (input in branch.inputs) {
            replacement.add(VarInsnNode(loadOpcode(input.type), input.slot))
        }
        replacement.add(MethodInsnNode(Opcodes.INVOKESPECIAL, owner, "$base\$stub",
                branchDescriptor(branch), false))
        replacement.add(VarInsnNode(storeOpcode(branch.resultType), branch.resultLocal))

        // Remove the region [startIndex, endIndex) and splice in the replacement before it.
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

    // ---- region replay + value helpers --------------------------------------

    /**
     * Append the branch [region] to [il], remapping the proof method's input locals to the synthetic
     * method's parameter slots and DROPPING the trailing join STORE (so the branch's result value is
     * left on the operand stack). [remap] maps each input's proof slot to its synthetic-method slot.
     */
    private fun appendRegionAsValue(il: InsnList, branch: DiscoveredBranch, remap: Map<Int, Int>) {
        // Clone the region fresh (a private label map per call) so label/jump nodes are never shared
        // across the synthetic methods. The region excludes the join store, so after appending it the
        // branch's result value is left on the operand stack.
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

    private fun inputSlotRemap(branch: DiscoveredBranch): Map<Int, Int> {
        // branch$N(inputs): slot 0 = this, inputs at 1, 1+size0, ...
        val remap = HashMap<Int, Int>()
        var next = 1
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
                // stack: value ; load r ; if value == r push 1 else 0.
                il.add(VarInsnNode(Opcodes.ILOAD, rSlot))
                appendIntEqBoolean(il)
            }
            Type.LONG -> {
                il.add(VarInsnNode(Opcodes.LLOAD, rSlot))
                il.add(InsnNode(Opcodes.LCMP)) // 0 iff equal
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
                // Reference: nondetWithoutNull returns Object; cast to the declared type.
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
