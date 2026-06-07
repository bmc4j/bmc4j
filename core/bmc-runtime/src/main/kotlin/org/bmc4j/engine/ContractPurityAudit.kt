package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Locale
import java.util.zip.ZipFile

/**
 * The contract **purity certifier**. Every other contract obligation is discharged
 * structurally — enforce-before-reuse is automatic, a false `@Ensures` turns the build red —
 * but *purity* was, until this pass, a documentation sentence: a contract on a method that
 * mutates pre-existing heap state, reads a mutable static, or performs I/O silently drops those
 * effects at every redirected call site while its enforce-proof still passes. That is the last
 * false-green vector in the contracts design (the replace-stub summarizes only the return value,
 * so a side effect the caller would have observed vanishes when the call site is rewritten).
 *
 * Purity is undecidable in general, and we do not try to decide it. This is a **sound
 * over-approximation**: a body is *certified pure by construction* only when everything it
 * reaches is one of a small set of provably-effect-free shapes; everything else is **rejected
 * loudly**. Wrong-side errors (certifying an impure body) are impossible by design — the only
 * cost is false rejections of bodies we cannot see through, the same conservative bias as the
 * verdict cache and the nondet-stub policy.
 *
 * ## What disqualifies a body (caller-observable effects beyond the return value)
 *
 * - **Heap writes to pre-existing state** — `PUTFIELD` / array stores on an object the body did
 *   not itself allocate, and any `PUTSTATIC`. A fresh allocation populated and returned is
 *   fine: writes whose target is provably a `NEW`/`NEWARRAY` made in this body don't escape to
 *   any caller-visible prior state (a conservative, allocation-site escape test — see
 *   [PurityMethodVisitor]).
 * - **Known-impure calls** — a denylist of I/O, wall-clock (`nanoTime`/`currentTimeMillis`),
 *   `Random`, threads/locks, `Unsafe`, reflection / `MethodHandle`s, and native methods. Naming
 *   the callee.
 * - **`monitorenter`** — a concurrency effect.
 * - **Reads of mutable statics** — a `GETSTATIC` of a field that is not `static final` of a
 *   constable type: its value can differ between the enforce-proof and a real call site, so a
 *   contract that depends on it is not a function of its inputs. `static final` primitive /
 *   `String` constants are fine.
 *
 * Exception behaviour is deliberately **not** audited: the replace-stub never throws, but the
 * enforce-proof runs the real body under `@Requires` and JBMC fails it on any uncaught
 * exception — so *enforce-green already is a no-throw-under-`requires` proof*.
 *
 * ## Transitive closure
 *
 * A body is pure only iff everything it reaches is. We run a worklist over the call graph with
 * three-way classification of each callee:
 *
 * 1. **Auditable** — bytecode we can see on the (already rewritten) analysis classpath: the
 *    user's methods, other contracted methods, and the bundled models. We run against the
 *    *rewritten* classpath, where JDK calls already resolve to our model bytecode, so we scan
 *    the models' real instructions instead of whitelisting JDK methods on trust.
 * 2. **Known-impure** — the denylist. Reject, naming the call.
 * 3. **Unresolvable** — a non-devirtualizable virtual / interface call (no class on the
 *    classpath provides the body), an unknown owner, an intrinsified JDK floor not covered by a
 *    model. Reject, naming the call site. (Lambdas are already desugared to plain synthetic
 *    methods by [LambdaBytecode] before this pass runs, so the walker follows them like any
 *    other call.)
 *
 * ## Failure
 *
 * A rejection is a hard, unconditional failure of the build via [ContractPurityError] — NOT a
 * runtime UNKNOWN that `@BmcProof(expect = …)` could swallow, and NOT a refutation. It names the
 * contract's target method, the offending instruction or callee chain, and the remedies
 * (restructure the body; drop the contract; or, if the flagged callee is genuinely pure and
 * modeled, report the audit gap). The check runs in [JbmcBackend] before any contracted call
 * site is reused, so an impure contract can never reach the engine as a silent green.
 */
internal object ContractPurityAudit {

    /** Internal names whose methods are summarily impure when called: any method on one of these
     *  owners disqualifies the caller. I/O, clock, randomness, threads/locks, unsafe, reflection. */
    private val IMPURE_OWNERS: Set<String> = setOf(
            "java/io/PrintStream",
            "java/io/InputStream",
            "java/io/OutputStream",
            "java/io/Reader",
            "java/io/Writer",
            "java/io/File",
            "java/io/RandomAccessFile",
            "java/nio/file/Files",
            "java/util/Random",
            "java/util/concurrent/ThreadLocalRandom",
            "java/security/SecureRandom",
            "java/lang/Thread",
            "java/util/concurrent/locks/Lock",
            "java/util/concurrent/locks/ReentrantLock",
            "java/util/concurrent/locks/ReadWriteLock",
            "sun/misc/Unsafe",
            "jdk/internal/misc/Unsafe",
            "java/lang/reflect/Method",
            "java/lang/reflect/Field",
            "java/lang/reflect/Constructor",
            "java/lang/invoke/MethodHandle",
            "java/lang/invoke/MethodHandles",
            "java/lang/invoke/VarHandle")

    // ---- coroutine (suspend) plumbing allowance ----------------------------------------------
    //
    // A `suspend` function is lowered to `(args, Continuation)Object` over a generated state machine,
    // and bmc4j contracts suspend targets under the immediate-dispatch idealization (a suspend call
    // completes linearly in one call). Such a body unavoidably touches a small set of provably-benign
    // coroutine internals that the general purity rules would otherwise flag. Each allowance below is
    // narrow and individually justified — we do NOT blanket-allow kotlin/kotlinx coroutines, so a real
    // `this`-mutation (or any other genuine effect) inside a suspend body still rejects.

    /** The base classes of a Kotlin coroutine **state machine** (continuation). The compiler emits a
     *  synthetic `Owner$method$N` per suspend function that extends one of these; bmc4j bundles clean
     *  models of them. A class transitively extending one is the per-call, non-escaping state machine —
     *  writes/reads of ITS OWN fields (`label`, `L$N`, `result`, `completion`) are internal plumbing,
     *  never a caller-observable effect, so they are allowed (see [isCoroutineStateMachine]). */
    private val CONTINUATION_BASES: Set<String> = setOf(
            "kotlin/coroutines/jvm/internal/BaseContinuationImpl",
            "kotlin/coroutines/jvm/internal/ContinuationImpl",
            "kotlin/coroutines/jvm/internal/RestrictedContinuationImpl",
            "kotlin/coroutines/jvm/internal/SuspendLambda",
            "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda")

    /** `GETSTATIC owner.name` reads that are allowed inside coroutine plumbing despite the general
     *  "no mutable-static read" rule: each is a constant suspension sentinel, not run-varying state. */
    private val ALLOWED_COROUTINE_STATICS: Set<String> = setOf(
            // The COROUTINE_SUSPENDED sentinel — a `static final` enum singleton compared by identity at
            // every suspension point. Its value is a fixed constant of the runtime, identical in the
            // enforce-proof and at every call site, so reading it is not a function of run-varying state.
            "kotlin/coroutines/intrinsics/CoroutineSingletons.COROUTINE_SUSPENDED",
            "kotlin/coroutines/intrinsics/IntrinsicsKt.COROUTINE_SUSPENDED")

    /** `"owner.name"` call sites that are impure even though their owner has pure methods too —
     *  wall-clock reads and process/IO entry points on otherwise-fine classes. */
    private val IMPURE_METHODS: Set<String> = setOf(
            "java/lang/System.nanoTime",
            "java/lang/System.currentTimeMillis",
            "java/lang/System.getenv",
            "java/lang/System.getProperty",
            "java/lang/System.setProperty",
            "java/lang/System.exit",
            "java/lang/System.arraycopy", // writes into a (possibly pre-existing) destination array
            "java/lang/Runtime.exec",
            "java/lang/Runtime.halt")

    /**
     * Audit every contracted target named by [redirects] against [analysisClasspath] (the
     * fully-prepared, model-bearing, desugared classpath). Throws [ContractPurityError] naming the
     * first contract whose body is not provably pure. A no-op when there are no redirects.
     *
     * The classpath is scanned once into an internal-name → bytes index; the transitive walk
     * then resolves each callee against that index. Resolution failures for a *callee* are a
     * rejection (we could not see through it); a target named by a redirect that is itself
     * missing is treated as unresolvable for that contract.
     */
    @JvmStatic
    fun audit(redirects: List<ContractRewriter.Redirect>, analysisClasspath: String?) {
        if (redirects.isEmpty() || analysisClasspath.isNullOrBlank()) {
            return
        }
        val index = ClasspathIndex(analysisClasspath)
        val boundaries = stubBoundaries(redirects)
        for (r in redirects) {
            auditOne(r, index, boundaries)
        }
    }

    /** The `stubOwner.stubName` of every redirect — the summarized contract boundaries the purity
     *  walk treats as pure leaves (each is audited by its own contract). */
    private fun stubBoundaries(redirects: List<ContractRewriter.Redirect>): Set<String> {
        val out = HashSet<String>()
        for (r in redirects) {
            out.add(r.stubOwner + '.' + r.stubName)
        }
        return out
    }

    /**
     * Audit only the contracts THIS proof would consume, scoped by the proof's own transitive call
     * graph. A redirect is *relevant* iff the proof's entry method ([entryClass]/[entryMethod])
     * transitively reaches an `INVOKESTATIC` of the redirect's target on the **pre-rewrite**
     * classpath [proofClasspath]:
     *
     * - in a **replace-proof** that call site is rewritten to the stub, so the body's effects are
     *   dropped — the unsoundness the audit closes;
     * - in an **enforce-proof** that call site is the generated `enforce__<m>`'s direct call to the
     *   real body, exactly where an impurity would leak past the `@Ensures` check.
     *
     * Crucially this is *entry-rooted* reachability, not a flat scan of the whole classpath: every
     * proof in a module shares the same compiled-classes classpath, so a flat scan would make every
     * contract "reachable" from every proof and an impure contract would poison the module. Walking
     * the call graph from the entry instead scopes a rejection to precisely the proofs that actually
     * reuse the impure contract (including its own enforce-proof). The reachability walk runs over
     * the pre-rewrite [proofClasspath] (so the real, un-stubbed call sites are visible); the purity
     * walk of each relevant target's body runs over the prepared, model-bearing [analysisClasspath].
     */
    @JvmStatic
    fun auditRelevant(manifest: ContractManifest, entryClass: String, entryMethod: String,
                      proofClasspath: String?, analysisClasspath: String?) {
        if (manifest.isEmpty || analysisClasspath.isNullOrBlank()) {
            return
        }
        val redirects = manifest.redirects()
        if (redirects.isEmpty()) {
            return
        }
        val reachIndex = ClasspathIndex(proofClasspath ?: "")
        val reached = reachableCallSites(entryClass.replace('.', '/'), entryMethod, reachIndex)
        val relevant = redirects.filter { reached.contains(it.owner + '.' + it.name) }
        if (relevant.isEmpty()) {
            return
        }
        val index = ClasspathIndex(analysisClasspath)
        // Boundaries come from ALL redirects (not just the relevant ones): a relevant target's body
        // may call any other contracted method, whose stub is a summarized boundary regardless of
        // whether that other contract is itself relevant to this proof.
        val boundaries = stubBoundaries(redirects)
        for (r in relevant) {
            auditOne(r, index, boundaries)
        }
    }

    /**
     * The `owner.name` call sites transitively reachable from `entryOwner.entryMethod`, walking
     * method bodies on [index]. Only call sites with a resolvable body on the classpath are recursed
     * into (an unresolvable callee can't itself reach a contracted target we'd care about), but every
     * call target is RECORDED whether or not its body resolves — a contracted target need not have
     * its body on the pre-rewrite proof classpath to be a call site here. Both `invokestatic` (a
     * static contract's call site) and `invokevirtual`/`invokeinterface` (a pure-instance contract's
     * call site) targets are recorded, so a redirect of either kind is correctly judged relevant to a
     * proof that reaches it. This is a deliberately simple reachability over the call graph; virtual
     * dispatch edges are followed only to a body that resolves on the exact owner, which is enough to
     * find the call sites the contract rewriter targets (exact-class binding).
     */
    private fun reachableCallSites(entryOwner: String, entryMethod: String,
                                   index: ClasspathIndex): Set<String> {
        val sites = HashSet<String>()
        val seen = HashSet<String>()
        val work = ArrayDeque<MethodRef>()
        // The entry method's descriptor is unknown to us here; null matches the first overload, which
        // is the proof method (proof methods are not overloaded).
        val start = MethodRef(entryOwner, entryMethod, null)
        work.add(start)
        seen.add("$entryOwner.$entryMethod")
        while (work.isNotEmpty()) {
            val m = work.poll()
            val body = index.find(m.owner, m.name, m.descriptor) ?: continue
            val callees = collectCallees(body)
            for (c in callees) {
                sites.add(c.owner + '.' + c.name)
                val key = "${c.owner}.${c.name}${c.descriptor ?: ""}"
                if (seen.add(key)) {
                    work.add(c)
                }
            }
        }
        return sites
    }

    /** Every method call site in [body] (for reachability — not purity). */
    private fun collectCallees(body: MethodBody): List<MethodRef> {
        val out = ArrayList<MethodRef>()
        ClassReader(body.classBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != body.name || (body.descriptor != null && body.descriptor != d)) {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        if (owner != null && name != null) {
                            out.add(MethodRef(owner, name, desc))
                        }
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return out
    }

    /** Walk the transitive call graph rooted at the contracted target; throw on the first impurity.
     *  [boundaries] are `owner.name` call sites treated as already-summarized pure leaves: the
     *  generated contract stubs (a call to another — or this same, recursive — contract is summarized
     *  by ITS own audit, exactly like modular enforce) and the bmc4j assume/check helpers. */
    private fun auditOne(redirect: ContractRewriter.Redirect, index: ClasspathIndex,
                         boundaries: Set<String>) {
        val rootKey = methodKey(redirect.owner, redirect.name, redirect.descriptor)
        val seen = HashSet<String>()
        val work = ArrayDeque<MethodRef>()
        val root = MethodRef(redirect.owner, redirect.name, redirect.descriptor)
        work.add(root)
        seen.add(rootKey)

        while (work.isNotEmpty()) {
            val m = work.poll()
            // A summarized boundary (another contract's stub, the recursive self-stub of modular
            // enforce, or a bmc assume/check helper) is a pure leaf — don't open its body. Its own
            // purity obligation is discharged by its contract's audit, not folded into this one.
            if (m !== root && isBoundary(m, boundaries)) {
                continue
            }
            // A PURE_JDK_FLOOR owner is a vouched-for effect-free value type (boxing wrappers, Math,
            // Objects, String value ops, exception construction). Treat it as a pure leaf even when a
            // bundled MODEL supplies a body — otherwise the model's own constructor (e.g.
            // Integer.<init> writing this.value while boxing a suspend result) would read as a write to
            // non-fresh state. These owners are certified pure by construction; their bodies needn't be
            // re-derived. (Without a model body this is already handled in the body==null branch below;
            // this covers the with-model case the suspend boxing path newly exercises.)
            if (m !== root && isPureJdkFloor(m.owner)) {
                continue
            }
            val body = index.find(m.owner, m.name, m.descriptor)
            if (body == null) {
                // No body on the classpath. A KNOWN-PURE JDK floor method (exception construction,
                // boxing, value-typed String/Math/Objects helpers) is certified pure without a body —
                // JBMC models these soundly and they have no caller-observable effect. Anything else
                // unresolvable (an unknown jar, a non-devirtualizable virtual/interface call, an
                // intrinsified JDK method we don't vouch for) can't be certified -> reject.
                if (isPureJdkFloor(m.owner)) {
                    continue
                }
                throw reject(root, m,
                        "its body is not on the analysis classpath (an unknown jar, an" +
                                " un-devirtualizable virtual/interface call, or an intrinsified JDK" +
                                " method with no bundled model)")
            }
            val finding = scan(body, index)
            val impurity = finding.impurity
            if (impurity != null) {
                throw reject(root, m, impurity)
            }
            for (callee in finding.calls) {
                val k = methodKey(callee.owner, callee.name, callee.descriptor)
                if (seen.add(k)) {
                    work.add(callee)
                }
            }
        }
    }

    /**
     * True when [owner] is a Kotlin coroutine **state machine** — a synthetic continuation class
     * (`Owner$method$N`) the compiler generates per suspend function, transitively extending one of
     * [CONTINUATION_BASES]. Walked over [index] up the super-class chain (bounded; cycle-guarded). A
     * write/read of such a class's OWN fields is the per-call, non-escaping coroutine plumbing the
     * suspend ABI requires, not a caller-observable effect — so the purity audit allows it while still
     * rejecting any other heap write (e.g. a `this`-mutation in the real suspend logic).
     */
    private fun isCoroutineStateMachine(owner: String, index: ClasspathIndex): Boolean {
        if (CONTINUATION_BASES.contains(owner)) {
            return true
        }
        var current: String? = owner
        var hops = 0
        val seen = HashSet<String>()
        while (current != null && hops < 12 && seen.add(current)) {
            val sup = index.superNameOf(current) ?: return false
            if (CONTINUATION_BASES.contains(sup)) {
                return true
            }
            current = sup
            hops++
        }
        return false
    }

    /** True when [m] is a summarized contract-stub boundary or a bmc assume/check helper. */
    private fun isBoundary(m: MethodRef, boundaries: Set<String>): Boolean {
        if (boundaries.contains(m.owner + '.' + m.name)) {
            return true
        }
        // The generated stub classes (…__BmcStubs) summarize a contract; never open them.
        if (m.owner.endsWith("__BmcStubs")) {
            return true
        }
        // bmc4j's own assume/check primitives throw an AssertionError on a violated predicate but
        // have no heap effect — and exception behaviour is out of scope (enforce-green is the
        // no-throw proof). Treat them as pure leaves so the assert machinery in a stub/enforce path
        // doesn't read as impurity.
        return m.owner == "org/bmc4j/Bmc"
    }

    /** Scan a single method body: classify it impure (with a reason) or collect its callees. The
     *  [index]-backed coroutine allowance lets a suspend body's state-machine plumbing through (writes
     *  to its own continuation class, the COROUTINE_SUSPENDED sentinel read) without blanket-allowing
     *  real impurity. */
    private fun scan(body: MethodBody, index: ClasspathIndex): ScanResult {
        val v = PurityMethodVisitor(
                isStateMachineField = { owner -> isCoroutineStateMachine(owner, index) },
                isAllowedStaticRead = { owner, name -> ALLOWED_COROUTINE_STATICS.contains("$owner.$name") })
        ClassReader(body.classBytes).accept(SingleMethodVisitor(body.name, body.descriptor, v), 0)
        return ScanResult(v.impurity, v.calls)
    }

    // ---- impurity classification -------------------------------------------------------------

    /** The reason a read of mutable static `owner.name` is impure. */
    private fun mutableStaticReadImpurity(owner: String?, name: String?): String =
            "reads mutable static $owner.$name (its value can differ between the" +
                    " enforce-proof and a real call site, so the contract isn't a function of its inputs)"

    /** Owners on the **pure JDK floor**: their methods are effect-free value computations / object
     *  construction that JBMC models soundly, so an unresolved call to one is certified pure rather
     *  than rejected. Deliberately conservative — only owners with no I/O, no statics-mutation, no
     *  clock/randomness. (Owners that are partly impure, e.g. System, are handled by the denylist,
     *  which is consulted first; a method here is reached only when its body isn't on the classpath.) */
    private val PURE_JDK_FLOOR: Set<String> = setOf(
            "java/lang/Object",            // <init>, getClass, equals/hashCode (no effect)
            "java/lang/AssertionError",    // assert / Bmc.check failure path — exception construction
            "java/lang/Error",
            "java/lang/Throwable",
            "java/lang/Exception",
            "java/lang/RuntimeException",
            "java/lang/IllegalArgumentException",
            "java/lang/IllegalStateException",
            "java/lang/ArithmeticException",
            "java/lang/IndexOutOfBoundsException",
            "java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/NullPointerException",
            "java/lang/StringBuilder",     // sound concat machinery (desugar target) — no effect
            "java/lang/StringBuffer",
            "java/lang/String",            // value methods (content ops are desugared to BmcStrings)
            "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte",
            "java/lang/Character", "java/lang/Boolean", "java/lang/Float", "java/lang/Double",
            "java/lang/Number",
            "java/lang/Math",              // modeled value math (unmodeled ints are redirected to BmcMath)
            "java/lang/StrictMath",
            "java/util/Objects",           // equals/hashCode/requireNonNull — pure
            "org/bmc4j/engine/BmcStrings", // sound String shim (rewrite target) — pure
            "org/bmc4j/engine/BmcMath",    // sound integer math (rewrite target) — pure
            "org/cprover/CProver")         // nondet* — symbolic value, no effect

    private fun isPureJdkFloor(owner: String): Boolean = PURE_JDK_FLOOR.contains(owner)

    private fun impureCall(owner: String?, name: String?): String? {
        if (owner == null || name == null) {
            return null
        }
        if (IMPURE_OWNERS.contains(owner)) {
            return "calls into known-impure $owner.$name (I/O, clock, randomness, threads," +
                    " unsafe, or reflection)"
        }
        if (IMPURE_METHODS.contains("$owner.$name")) {
            return "calls $owner.$name (a wall-clock / environment / process effect)"
        }
        return null
    }

    // ---- rejection message -------------------------------------------------------------------

    private fun reject(root: MethodRef, at: MethodRef, reason: String): ContractPurityError {
        val sb = StringBuilder()
        sb.append("Contract on ").append(dot(root.owner)).append('.').append(root.name)
                .append(root.descriptor ?: "").append(" is not provably PURE.\n")
        if (sameMethod(root, at)) {
            sb.append("  ✗ ").append(reason).append('\n')
        } else {
            sb.append("  ✗ reaches ").append(dot(at.owner)).append('.').append(at.name)
                    .append(at.descriptor ?: "").append(", which ").append(reason).append('\n')
        }
        sb.append("    A contract redirects every call site of the target to a stub that summarizes\n")
                .append("    only its return value, so any caller-observable side effect is silently\n")
                .append("    dropped. Contracts are certified pure-by-construction; this body isn't.\n")
                .append("    To fix, choose one:\n")
                .append("      - restructure the method to be pure (no writes to pre-existing state, no\n")
                .append("        I/O / clock / randomness / locks, no reads of mutable statics); or\n")
                .append("      - remove the @Requires/@Ensures contract for it (analyze it inline); or\n")
                .append("      - if the flagged callee is genuinely pure and SHOULD be modeled, report\n")
                .append("        the audit gap so a bundled model can be added.")
        return ContractPurityError(sb.toString())
    }

    private fun sameMethod(a: MethodRef, b: MethodRef): Boolean =
            a.owner == b.owner && a.name == b.name && a.descriptor == b.descriptor

    private fun dot(internal: String): String = internal.replace('/', '.')

    private fun methodKey(owner: String, name: String, desc: String?): String =
            "$owner.$name${desc ?: ""}"

    // ---- data --------------------------------------------------------------------------------

    private class MethodRef(@JvmField val owner: String, @JvmField val name: String,
                            @JvmField val descriptor: String?)

    private class ScanResult(@JvmField val impurity: String?, @JvmField val calls: List<MethodRef>)

    /** A located method body: the bytes of its declaring class plus the exact name+descriptor. */
    private class MethodBody(@JvmField val classBytes: ByteArray, @JvmField val name: String,
                             @JvmField val descriptor: String?)

    // ---- classpath indexing ------------------------------------------------------------------

    /**
     * Indexes the analysis classpath's `.class` files by internal class name → bytes, the first
     * occurrence winning (classpath order, exactly as the JVM/JBMC resolve). Both directory and jar
     * entries are read, mirroring [ClasspathMirror]'s coverage so a published consumer's jar'd
     * models are seen as well as the in-repo class dirs. Lazy per-class parsing: a class is parsed
     * for its methods only when the walk actually reaches it.
     */
    private class ClasspathIndex(classpath: String) {
        /** internal class name -> raw class bytes (first on the classpath wins). */
        private val classes = HashMap<String, ByteArray>()

        init {
            for (entry in classpath.split(File.pathSeparator)) {
                if (entry.isEmpty()) {
                    continue
                }
                val p = Path.of(entry)
                try {
                    when {
                        Files.isDirectory(p) -> indexDir(p)
                        Files.isRegularFile(p) && isJar(p) -> indexJar(p)
                        else -> {}
                    }
                } catch (ignored: IOException) {
                    // fail-open per entry: an unreadable container contributes no classes. A class it
                    // would have provided then resolves to null -> a rejection (we couldn't see it),
                    // never a false certification.
                } catch (ignored: RuntimeException) {
                }
            }
        }

        /**
         * The body of `owner.name desc`, or null if no class named [owner] on the classpath
         * declares it. A null [desc] (a redirect that matched any overload) resolves to the first
         * same-named method found. Resolution does NOT walk superclasses: a contracted static
         * target and its in-class/-module callees are declared where they're called; an inherited
         * or interface-dispatched callee that isn't on the exact owner reads as unresolvable, which
         * is the conservative reject we want for non-devirtualizable dispatch.
         */
        fun find(owner: String, name: String, desc: String?): MethodBody? {
            val bytes = classes[owner] ?: return null
            return if (hasMethod(bytes, name, desc)) MethodBody(bytes, name, desc ?: anyDesc(bytes, name)) else null
        }

        /** The internal super-class name of [owner], or null if [owner] isn't on the classpath. Used to
         *  walk a class's supertype chain (e.g. to recognise a coroutine state machine). */
        fun superNameOf(owner: String): String? {
            val bytes = classes[owner] ?: return null
            return try {
                ClassReader(bytes).superName
            } catch (e: RuntimeException) {
                null
            }
        }

        private fun anyDesc(bytes: ByteArray, name: String): String? {
            var found: String? = null
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         ex: Array<String>?): MethodVisitor? {
                    if (found == null && n == name) {
                        found = d
                    }
                    return null
                }
            }, ClassReader.SKIP_CODE)
            return found
        }

        private fun hasMethod(bytes: ByteArray, name: String, desc: String?): Boolean {
            var present = false
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         ex: Array<String>?): MethodVisitor? {
                    if (n == name && (desc == null || desc == d)) {
                        // A native method has no body to certify: treat it as not-present so the
                        // walk rejects it as unresolvable (a native call is caller-observable I/O).
                        if ((a and Opcodes.ACC_NATIVE) == 0) {
                            present = true
                        }
                    }
                    return null
                }
            }, ClassReader.SKIP_CODE)
            return present
        }

        private fun indexDir(dir: Path) {
            Files.walk(dir).use { walk ->
                for (c in Iterable { walk.iterator() }) {
                    if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                        try {
                            put(Files.readAllBytes(c))
                        } catch (ignored: IOException) {
                        } catch (ignored: RuntimeException) {
                        }
                    }
                }
            }
        }

        private fun indexJar(jar: Path) {
            ZipFile(jar.toFile()).use { zf ->
                val en = zf.entries()
                while (en.hasMoreElements()) {
                    val ze = en.nextElement()
                    if (ze.isDirectory || !ze.name.endsWith(".class")) {
                        continue
                    }
                    try {
                        zf.getInputStream(ze).use { put(it.readAllBytes()) }
                    } catch (ignored: IOException) {
                    } catch (ignored: RuntimeException) {
                    }
                }
            }
        }

        /** Record a class's bytes under its internal name, first-on-the-classpath winning. */
        private fun put(bytes: ByteArray) {
            val name = internalNameOf(bytes) ?: return
            classes.putIfAbsent(name, bytes)
        }

        private fun internalNameOf(bytes: ByteArray): String? = try {
            ClassReader(bytes).className
        } catch (e: RuntimeException) {
            null
        }

        private fun isJar(p: Path): Boolean {
            val n = p.fileName.toString().lowercase(Locale.ROOT)
            return n.endsWith(".jar") || n.endsWith(".zip")
        }
    }

    // ---- ASM visitors ------------------------------------------------------------------------

    /** Routes only the one target method's instructions to [delegate]; skips every other method. */
    private class SingleMethodVisitor(private val name: String, private val descriptor: String?,
                                      private val delegate: PurityMethodVisitor)
        : ClassVisitor(Opcodes.ASM9) {

        override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                 ex: Array<String>?): MethodVisitor? {
            if (n == name && (descriptor == null || descriptor == d)) {
                return delegate
            }
            return null
        }
    }

    /**
     * Classifies one method body. Sets [impurity] (a human message) the moment it sees a
     * disqualifying instruction, and meanwhile collects every callee into [calls] for the
     * transitive walk.
     *
     * **Fresh-allocation escape test.** A `PUTFIELD` / array store is only impure when its
     * target object pre-existed the body. We track, conservatively, whether the value the store
     * writes *through* is a fresh allocation made in this body: a `NEW` / `NEWARRAY` /
     * `ANEWARRAY` / `MULTIANEWARRAY` pushes a "fresh" marker, and an instruction that consumes it
     * without aliasing keeps the top-of-stack fresh status local. The test is intentionally
     * simple and over-rejecting at the margins: a store is treated as a write to fresh state only
     * when the receiver on the stack is *unambiguously* a value produced by an allocation in this
     * body and not since stored anywhere a caller could reach. Anything we can't prove fresh is
     * treated as a pre-existing write — a reject — which keeps the audit sound (it never certifies
     * a real heap effect as pure).
     */
    private class PurityMethodVisitor(
            /** True when the field-instruction owner is a coroutine state-machine (continuation) class:
             *  a write/read of its own fields is per-call coroutine plumbing, not a caller-observable
             *  effect. Defaults to "no class qualifies" for non-suspend callers. */
            private val isStateMachineField: (String) -> Boolean = { false },
            /** True when a `GETSTATIC owner.name` is an allowed coroutine constant (the
             *  COROUTINE_SUSPENDED sentinel) rather than run-varying mutable state. */
            private val isAllowedStaticRead: (String, String) -> Boolean = { _, _ -> false },
    ) : MethodVisitor(Opcodes.ASM9) {
        @JvmField var impurity: String? = null
        @JvmField val calls = ArrayList<MethodRef>()

        /** Operand-stack freshness shadow: true entries are values provably from a `NEW`/`*NEWARRAY`
         *  in this body. Pushed/popped in lockstep with the real stack for the instruction shapes a
         *  pure allocate-populate-return body uses; any shape we don't model collapses to "not fresh"
         *  (the safe, reject-leaning direction). */
        private val fresh = ArrayDeque<Boolean>()

        private fun flag(reason: String) {
            if (impurity == null) {
                impurity = reason
            }
        }

        private fun push(isFresh: Boolean) = fresh.push(isFresh)
        private fun pop(): Boolean = if (fresh.isEmpty()) false else fresh.pop()
        private fun clear() = fresh.clear()

        override fun visitTypeInsn(opcode: Int, type: String?) {
            when (opcode) {
                Opcodes.NEW -> push(true)            // fresh, uninitialized instance reference
                Opcodes.ANEWARRAY -> { pop(); push(true) } // length -> fresh array ref
                Opcodes.NEWARRAY -> { pop(); push(true) }
                Opcodes.CHECKCAST -> { /* ref stays as-is */ }
                Opcodes.INSTANCEOF -> { pop(); push(false) } // ref -> int(0/1), not fresh
                else -> {}
            }
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
            if (opcode == Opcodes.NEWARRAY) {
                pop(); push(true) // length(int) -> fresh primitive array ref
            } else if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                push(false)
            }
        }

        override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
            for (i in 0 until numDimensions) {
                pop()
            }
            push(true)
        }

        override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
            when (opcode) {
                Opcodes.PUTSTATIC ->
                    flag("writes static $owner.$name (a heap write to pre-existing global state)")
                Opcodes.GETSTATIC -> {
                    // GETSTATIC: only a static-final constable constant is pure to read. We can't see
                    // the field's modifiers from the call site here, so treat a read of a likely
                    // non-constant static conservatively. Primitive / String typed reads of a field
                    // that the compiler did NOT inline (it still emits GETSTATIC) are flagged unless
                    // they're known constant-ish; to stay sound without over-rejecting every enum/
                    // bundled-model constant, we only flag GETSTATIC whose descriptor is a mutable
                    // reference type (collections, arrays, holders). A final-primitive/String is
                    // inlined by javac (no GETSTATIC) or is an interface constant (effectively final).
                    // ALLOWANCE: the COROUTINE_SUSPENDED suspension sentinel is a fixed runtime constant
                    // (a static-final enum singleton compared by identity), identical in the enforce-proof
                    // and at every call site — reading it is not run-varying state.
                    if (isMutableStaticType(descriptor) && !isAllowedStaticRead(owner ?: "", name ?: "")) {
                        flag(mutableStaticReadImpurity(owner, name))
                    }
                    push(false)
                }
                Opcodes.PUTFIELD -> {
                    // Stack (pre): ..., objectref, value  -> store writes a field of objectref.
                    val valueFresh = pop()    // value
                    val targetFresh = pop()   // objectref
                    // ALLOWANCE: a write to a coroutine state-machine (continuation) class's OWN field is
                    // per-call coroutine plumbing — the state machine is fresh per call and never escapes
                    // to the caller, so the write (`label`, `L$N`, `result`, `completion`) is not a
                    // caller-observable effect. (A `this`-mutation in the real suspend logic targets the
                    // receiver class, NOT a continuation class, so it is still rejected below.)
                    if (!targetFresh && !isStateMachineField(owner ?: "")) {
                        flag("writes field $owner.$name on an object it did not allocate (a heap" +
                                " write to pre-existing state)")
                    }
                    // A PUTFIELD that stores INTO a fresh object is fine; nothing pushed.
                    // valueFresh is irrelevant to the store's purity (the value escapes into the
                    // fresh object, which is fine), but consuming it keeps the shadow aligned.
                    @Suppress("UNUSED_EXPRESSION") valueFresh
                }
                Opcodes.GETFIELD -> {
                    pop()        // objectref
                    push(false)  // the loaded field value is not tracked as fresh
                }
            }
        }

        override fun visitInsn(opcode: Int) {
            when (opcode) {
                Opcodes.AASTORE, Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
                Opcodes.DASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> {
                    // Stack (pre): ..., arrayref, index, value -> array store.
                    pop()                       // value
                    pop()                       // index
                    val arrayFresh = pop()      // arrayref
                    if (!arrayFresh) {
                        flag("stores into an array it did not allocate (e.g. an array passed in as a" +
                                " parameter) — a heap write to pre-existing state")
                    }
                }
                Opcodes.MONITORENTER, Opcodes.MONITOREXIT ->
                    flag("uses monitorenter/exit (a concurrency / locking effect)")
                Opcodes.DUP -> {
                    val t = pop(); push(t); push(t)
                }
                Opcodes.POP -> pop()
                Opcodes.POP2 -> { pop(); pop() }
                Opcodes.ACONST_NULL -> push(false)
                Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.FCONST_0, Opcodes.FCONST_1,
                Opcodes.FCONST_2, Opcodes.DCONST_0, Opcodes.DCONST_1 -> push(false)
                Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN,
                Opcodes.ARETURN, Opcodes.RETURN -> clear()
                Opcodes.ATHROW -> clear()
                else -> {
                    // Arithmetic, comparisons, conversions, dup variants we don't special-case:
                    // their results are never fresh allocations, so collapsing the shadow is sound.
                    // (Over-clearing only risks a false REJECT of a write to a fresh object, never a
                    // false certification.)
                    clear()
                }
            }
        }

        override fun visitMethodInsn(opcode: Int, owner: String?, name: String?,
                                     descriptor: String?, isInterface: Boolean) {
            val reason = impureCall(owner, name)
            if (reason != null) {
                flag(reason)
            }
            // Record the callee for the transitive walk (constructors included: <init> of a fresh
            // object is part of the allocate step and its own body is audited too). A null owner
            // can't happen for a method insn; guard anyway.
            if (owner != null && name != null) {
                calls.add(MethodRef(owner, name, descriptor))
            }
            // Consume args + receiver; push the (non-fresh) result. We don't try to thread freshness
            // through a callee return — a factory's result is conservatively non-fresh, so storing
            // through it would reject. That's the safe direction.
            val argCount = countArgsAndReceiver(opcode, descriptor)
            for (i in 0 until argCount) {
                pop()
            }
            if (descriptor != null && !descriptor.endsWith(")V")) {
                push(false)
            }
        }

        override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bsm: Handle?,
                                            vararg bsmArgs: Any?) {
            // A surviving invokedynamic past the desugar passes is an unresolvable effect we can't
            // see through — reject. (Concat/record/lambda/typeSwitch indys are already desugared to
            // plain methods before this audit runs; anything still here is a genuine opaque site.)
            flag("contains an un-desugared invokedynamic ($name) whose effect can't be audited")
            clear()
        }

        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            when (opcode) {
                Opcodes.ALOAD -> push(false)  // a local ref is not tracked as fresh (conservative)
                Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD -> push(false)
                Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE -> pop()
                else -> {}
            }
        }

        override fun visitLdcInsn(value: Any?) = push(false)

        override fun visitJumpInsn(opcode: Int, label: Label?) {
            // Any control-flow edge invalidates the linear freshness shadow (a value's freshness at a
            // merge point is not generally the same on both edges). Collapse to safe.
            clear()
        }

        override fun visitLabel(label: Label?) = clear()
        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) = clear()
        override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<Label>?) = clear()
        override fun visitIincInsn(varIndex: Int, increment: Int) {}

        private fun countArgsAndReceiver(opcode: Int, descriptor: String?): Int {
            if (descriptor == null) {
                return 0
            }
            var n = countDescriptorArgs(descriptor)
            if (opcode != Opcodes.INVOKESTATIC) {
                n += 1 // receiver
            }
            return n
        }
    }

    /** Count the argument slots (each arg = 1 entry for the freshness shadow, longs/doubles incl.). */
    private fun countDescriptorArgs(descriptor: String): Int {
        var i = descriptor.indexOf('(') + 1
        val end = descriptor.indexOf(')')
        var count = 0
        while (i < end) {
            when (descriptor[i]) {
                'L' -> { i = descriptor.indexOf(';', i) + 1; count++ }
                '[' -> { i++; continue }
                else -> { i++; count++ }
            }
        }
        return count
    }

    /** True when a static field's declared type is a mutable reference type whose read could vary
     *  between runs — arrays and reference types (collections, holders). Primitives and the few
     *  constable types stay readable: javac inlines a `static final` primitive/`String`, so a
     *  GETSTATIC of one is rare; we permit it to avoid over-rejecting enum/`$VALUES`-free constants. */
    private fun isMutableStaticType(descriptor: String?): Boolean {
        if (descriptor == null) {
            return false
        }
        // Arrays are always mutable. A reference type other than String is treated as mutable.
        if (descriptor.startsWith("[")) {
            return true
        }
        if (descriptor.startsWith("L")) {
            return descriptor != "Ljava/lang/String;"
        }
        return false // primitive constant
    }
}
