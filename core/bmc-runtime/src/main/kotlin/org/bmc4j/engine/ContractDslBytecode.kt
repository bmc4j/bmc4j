package org.bmc4j.engine

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) to the existing enforce-proof backend by
 * reading the method reference and the predicate lambdas **statically** from a registration class's
 * bytecode - the same indy-bootstrap-argument decode [AssumeContractBytecode] uses to sidestep the
 * invokedynamic fault line - then generating a `@BmcProof` enforce-proof class as bytecode.
 *
 * ## Why bytecode (and not the Java-source generator / KSP)
 * The predicate is an inline Kotlin lambda. kotlinc compiles each lambda body to a private static
 * synthetic method, and the `whenPrecondition { ... }` / `thenPostCondition { ... }` SAM conversions to
 * `invokedynamic` sites whose `bsmArgs[1]` is a [Handle] to that body. Those body names are only knowable
 * from compiled bytecode (KSP sees source, not the synthesized names), and a `FunctionN.invoke` call is
 * not devirtualized by JBMC - so the lowering reads the body handles and lowers the predicates to
 * ordinary `static boolean` methods JBMC analyses directly.
 *
 * ## The generated enforce-proof (increment 1: instance method `Self.member(A): R`)
 * ```
 * @BmcProof   // or @BmcProof(expect = REFUTED) for a deliberately-false demo
 * public void enforce__member() {
 *     Self self = (Self) nondetWithoutNull();
 *     Bmc.assume(self != null);         // pin the receiver non-null (predicate null-guards need it)
 *     A    a    = nondet();
 *     Bmc.assume(pre(self, a));         // the whenPrecondition predicate body, called directly
 *     R ret = self.member(a);           // the REAL body
 *     Bmc.check(post(self, self, a, ret));  // the thenPostCondition predicate body (before == after)
 * }
 * ```
 * Each predicate is the compiled lambda body, called **directly and monomorphically** - a plain
 * `invokestatic` for an indy lambda (kotlinc 2.4) or an `invokevirtual` on the singleton for a
 * callable-reference class (kotlinc 2.3), never through a megamorphic `FunctionN.invoke`. `Bmc.assume`/
 * `Bmc.check` are the public proof primitives (assume lowers to `CProver.assume`; check throws an
 * `AssertionError` JBMC treats as the proof obligation). A false postcondition fails the check, so the
 * proof REFUTES - the same "annotate != asserting" guarantee the annotation form gives.
 *
 * ## Known limitation (next phase)
 * A postcondition that reads a RECEIVER FIELD (`before.field`) currently produces a spurious refutation:
 * in the generated enforce-proof JBMC does not link the predicate's symbolic-receiver field read to the
 * real body's field read, although a byte-equivalent hand-written Kotlin `@BmcProof` verifies. The
 * argument-only path (predicates over the call argument and result) verifies and refutes correctly. The
 * receiver-field-state path is the immediate next increment.
 */
internal object ContractDslBytecode {

    private const val DSL = "org/bmc4j/contracts/ContractDslKt"
    private const val CONTRACT_FOR = "contractFor"
    private const val CPROVER = "org/cprover/CProver"
    private const val BMC = "org/bmc4j/Bmc"
    private const val METAFACTORY = "java/lang/invoke/LambdaMetafactory"

    /** One decoded DSL contract: the target instance method + the pre/post predicate lambda bodies. */
    class Decoded(
            /** Internal name of the class declaring the contracted method. */
            @JvmField val targetOwner: String,
            @JvmField val targetName: String,
            /** The contracted method's real descriptor `(A)R` (instance: no receiver in the descriptor). */
            @JvmField val targetDesc: String,
            /** The precondition lambda body: owner/name/desc of the compiled `(Self, A)Z` synthetic. */
            @JvmField val pre: Handle,
            /** The postcondition lambda body: owner/name/desc of the compiled `(Self, Self, A, R)Z`. */
            @JvmField val post: Handle,
            /** Expected enforce verdict - "VERIFIED" or "REFUTED" (a deliberately-false demo). */
            @JvmField val expect: String) {

        val enforceMethod: String get() = "enforce__$targetName"
    }

    class ContractDslError(message: String) : RuntimeException(message)

    /**
     * Decode every `contractFor(...)` site in [bytes] (a compiled registration class). The Kotlin codegen
     * shape is two-level:
     * - the `contractFor(member, body)` call site loads the **member reference** as a synthetic
     *   callable-reference class singleton (`getstatic <Ref>.INSTANCE`, whose `invoke` forwards to the real
     *   instance method) and the **body** as a `Function1` SAM-conversion indy whose impl is the registration
     *   class's body method `_init_$lambda$N(ContractBuilder)`;
     * - that body method holds the `whenPrecondition(...)` and `thenPostCondition(...)` calls, each preceded
     *   by a SAM-conversion indy whose impl is the compiled predicate lambda body (`lambda$N$0`,
     *   `lambda$N$1`).
     *
     * So we scan every method: a `contractFor` call yields the member-ref class + the body-method handle;
     * we then re-scan the body method for the pre/post predicate handles. Reading the impl handle from each
     * indy's bootstrap arguments (never executing it) is the same invokedynamic-fault-line sidestep
     * [AssumeContractBytecode] uses.
     */
    fun decode(bytes: ByteArray, expect: String): List<Decoded> {
        // Index every method's ordered "lambda sources" (each whenPrecondition/thenPostCondition/body
        // lambda, however kotlinc emitted it - see [LambdaSource]). The registration's body method and the
        // predicate bodies all live on this class, so one index resolves the whole two-level walk.
        val methodSources = indexMethodSources(bytes)
        val sites = ArrayList<ContractForSite>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor = ContractForScanner(sites)
        }, 0)
        return sites.map { site -> build(site, methodSources, expect) }
    }

    /**
     * A lambda / method-reference value, however kotlinc lowered the SAM conversion. Across the consumer
     * Kotlin matrix (2.0-2.4) a `fun interface` SAM target is emitted EITHER as a `LambdaMetafactory`
     * `invokedynamic` (impl handle in [handle]) OR as a synthetic callable-reference class with a singleton
     * (`getstatic <Class>.INSTANCE`, name in [refClass]) whose `invoke` forwards to / contains the body.
     * One decoder handles both by resolving either form to the underlying body method.
     */
    private class LambdaSource(@JvmField val handle: Handle?, @JvmField val refClass: String?)

    /** A decoded `contractFor(member, body)` call site: the member reference and the contract body, each as
     *  a [LambdaSource] (the last two SAM values pushed before the marker call). */
    private class ContractForSite(@JvmField val member: LambdaSource, @JvmField val body: LambdaSource)

    /** Scans one method for `contractFor(...)` sites, remembering the last two SAM values (a getstatic
     *  singleton OR an indy impl) pushed before the marker - the `member` and `body` arguments. */
    private class ContractForScanner(private val out: MutableList<ContractForSite>) :
            MethodVisitor(Opcodes.ASM9) {

        private var prev: LambdaSource? = null
        private var last: LambdaSource? = null

        private fun push(s: LambdaSource) {
            prev = last
            last = s
        }

        override fun visitFieldInsn(op: Int, owner: String?, name: String?, desc: String?) {
            if (op == Opcodes.GETSTATIC && name == "INSTANCE" && owner != null
                    && !owner.startsWith("kotlin/") && !owner.startsWith("java/")) {
                push(LambdaSource(null, owner))
            }
        }

        override fun visitInvokeDynamicInsn(name: String?, descriptor: String?, bsm: Handle?,
                                            vararg bsmArgs: Any?) {
            samImplHandle(bsm, bsmArgs)?.let { push(LambdaSource(it, null)) }
        }

        override fun visitMethodInsn(op: Int, owner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            if (op == Opcodes.INVOKESTATIC && owner == DSL && name == CONTRACT_FOR) {
                val body = last ?: throw ContractDslError(
                        "a contractFor(...) site is missing its contract-body lambda.")
                val member = prev ?: throw ContractDslError(
                        "a contractFor(...) site is missing its member reference - pass a direct unbound" +
                                " instance-method reference (Type::member).")
                out.add(ContractForSite(member, body))
                prev = null; last = null
            }
        }
    }

    private fun build(site: ContractForSite,
                      methodSources: Map<String, List<LambdaSource>>, expect: String): Decoded {
        // The member: a callable-reference class (its `invoke` forwards to the real instance method) - in
        // both kotlinc shapes the member reference is the ref-class form.
        val refClass = site.member.refClass ?: throw ContractDslError(
                "the contractFor member reference was not a resolvable callable-reference - pass a direct" +
                        " unbound instance-method reference (Type::member).")
        val target = resolveReferenceTarget(refClass)
                ?: throw ContractDslError(
                        "could not resolve the contracted method from the reference class $refClass" +
                                " - increment 1 supports a direct unbound instance-method reference.")
        // The body's pre/post predicate sources, in source order: the body holds the
        // whenPrecondition/thenPostCondition lambdas, either in the registration's body method (the indy
        // impl, kotlinc 2.4) or in the body callable-reference class's `invoke` (kotlinc 2.3).
        val preds = bodySources(site.body, methodSources)
        val pre = preds.getOrNull(0) ?: throw ContractDslError(
                "a contractFor(...) block declares no whenPrecondition.")
        val post = preds.getOrNull(1) ?: throw ContractDslError(
                "a contractFor(...) block declares a whenPrecondition with no thenPostCondition.")
        return Decoded(target.owner, target.name, target.desc,
                resolvePredicateBody(pre), resolvePredicateBody(post), expect)
    }

    /** The ordered predicate [LambdaSource]s inside a contract body. For an indy body (kotlinc 2.4) the
     *  body is a method on the REGISTRATION class, already in [registrationSources]. For a ref-class body
     *  (kotlinc 2.3) the body's `invoke` lives on a nested `$N` class - index that class's `invoke`. */
    private fun bodySources(body: LambdaSource,
                            registrationSources: Map<String, List<LambdaSource>>): List<LambdaSource> {
        if (body.handle != null) {
            val key = body.handle.name + body.handle.desc
            return registrationSources[key]
                    ?: throw ContractDslError("could not read the contract-body method for $key.")
        }
        val refBytes = readSiblingByInternalName(body.refClass!!)
                ?: throw ContractDslError("could not read the contract-body class ${body.refClass}.")
        return indexMethodSources(refBytes)
                .entries.firstOrNull { it.key.startsWith("invoke(") }?.value
                ?: throw ContractDslError("contract-body class ${body.refClass} has no invoke method.")
    }

    /** Resolve a predicate [LambdaSource] to the boolean body method [Handle]: an indy impl is the body;
     *  a ref-class form's body is its `invoke(...)` method on the synthetic class. */
    private fun resolvePredicateBody(pred: LambdaSource): Handle {
        pred.handle?.let { return it }
        val refClass = pred.refClass!!
        val refBytes = readSiblingByInternalName(refClass)
                ?: throw ContractDslError("could not read the predicate class $refClass.")
        var handle: Handle? = null
        ClassReader(refBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                // The synthetic predicate class's `invoke(...)Z` (or `Object` boxed) is the body. Prefer the
                // primitive-boolean `test(...)` SAM (Precondition1/Postcondition1 declare `test`).
                if ((n == "test" || n == "invoke") && d != null && d.endsWith(")Z") && handle == null) {
                    handle = Handle(Opcodes.H_INVOKEVIRTUAL, refClass, n, d, false)
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        return handle ?: throw ContractDslError("predicate class $refClass has no boolean test/invoke.")
    }

    /** The impl [Handle] (`bsmArgs[1]`) of a `LambdaMetafactory` SAM-conversion indy, else null. */
    private fun samImplHandle(bsm: Handle?, bsmArgs: Array<out Any?>): Handle? {
        if (bsm != null && METAFACTORY == bsm.owner
                && (bsm.name == "metafactory" || bsm.name == "altMetafactory")
                && bsmArgs.size >= 2 && bsmArgs[1] is Handle) {
            return bsmArgs[1] as Handle
        }
        return null
    }

    /** Index every method of [bytes] by `name+desc` to its ordered [LambdaSource]s (each
     *  whenPrecondition/thenPostCondition SAM value, in source order), covering BOTH the indy and the
     *  getstatic-singleton kotlinc shapes. */
    private fun indexMethodSources(bytes: ByteArray): Map<String, List<LambdaSource>> {
        val out = HashMap<String, List<LambdaSource>>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor {
                val sources = ArrayList<LambdaSource>()
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitFieldInsn(op: Int, owner: String?, n: String?, d: String?) {
                        // A synthetic SAM singleton, NOT stdlib noise (`kotlin/Unit.INSTANCE`, etc.): only a
                        // user/synthetic class's INSTANCE is a contract lambda value.
                        if (op == Opcodes.GETSTATIC && n == "INSTANCE" && owner != null
                                && !owner.startsWith("kotlin/") && !owner.startsWith("java/")) {
                            sources.add(LambdaSource(null, owner))
                        }
                    }

                    override fun visitInvokeDynamicInsn(n: String?, d: String?, bsm: Handle?,
                                                        vararg a: Any?) {
                        samImplHandle(bsm, a)?.let { sources.add(LambdaSource(it, null)) }
                    }

                    override fun visitEnd() {
                        out["$name$desc"] = sources
                    }
                }
            }
        }, 0)
        return out
    }

    /** A resolved instance-method target: owner/name/descriptor of the real contracted method. */
    private class Target(@JvmField val owner: String, @JvmField val name: String,
                         @JvmField val desc: String)

    /**
     * Resolve the real instance method a Kotlin callable-reference class forwards to. The synthetic
     * `<Ref>.invoke(Self, A)R` body is a null-check guard then a single `invokevirtual Self.member(A)R`;
     * read that forwarded call. The reference class is a nested `<Registration>$N` on the classpath, read
     * via [readSiblingByInternalName] from the configured [classRoots].
     */
    private fun resolveReferenceTarget(refClass: String): Target? {
        val refBytes = readSiblingByInternalName(refClass) ?: return null
        var target: Target? = null
        ClassReader(refBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (name != "invoke") {
                    return null
                }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, n: String?, d: String?,
                                                 itf: Boolean) {
                        if (owner == "kotlin/jvm/internal/Intrinsics" || owner == "java/lang/Integer"
                                || owner == "java/lang/Long" || owner == "java/lang/Double"
                                || owner == "java/lang/Float" || owner == "java/lang/Short"
                                || owner == "java/lang/Byte" || owner == "java/lang/Character"
                                || owner == "java/lang/Boolean") {
                            return // null-check guard / box-unbox plumbing, not the forwarded call
                        }
                        if (target == null && (op == Opcodes.INVOKEVIRTUAL
                                        || op == Opcodes.INVOKEINTERFACE) && owner != null
                                && n != null && d != null) {
                            target = Target(owner, n, d)
                        }
                    }
                }
            }
        }, 0)
        return target
    }

    /** The classpath roots the build pass makes available for reading sibling/nested class files, set per
     *  [decode] call by [GradleContractsDsl]. The reference / predicate / body classes are nested classes
     *  of the registration (kotlinc callable-reference singletons) read from here. */
    @JvmField
    var classRoots: List<java.nio.file.Path> = emptyList()

    // --- enforce-proof generation -------------------------------------------------------------------

    /**
     * Generate the enforce-proof class [internalName] holding one `@BmcProof enforce__<member>()` per
     * decoded contract. Each proof nondets a receiver + argument, assumes the precondition, calls the real
     * body, and asserts the postcondition - all via direct `invokestatic`s to the decoded lambda bodies.
     */
    fun generateEnforceClass(internalName: String, decoded: List<Decoded>): ByteArray {
        // V1_6 + COMPUTE_MAXS: a Java-6 class needs no StackMapTable, so the proof method's branch (the
        // receiver non-null assume) verifies without frame computation - and COMPUTE_FRAMES (which would
        // have to LOAD the referenced analysis-only types to merge frames) is avoided.
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        // Default constructor - JUnit instantiates the proof class.
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        for (d in decoded) {
            emitProof(cw, d)
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Read a class's bytes by internal name from the configured [classRoots]. */
    private fun readSiblingByInternalName(internalName: String): ByteArray? {
        val resource = "$internalName.class"
        for (root in classRoots) {
            val f = root.resolve(resource)
            if (java.nio.file.Files.isRegularFile(f)) {
                return java.nio.file.Files.readAllBytes(f)
            }
        }
        return null
    }

    private fun emitProof(cw: ClassWriter, d: Decoded) {
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, d.enforceMethod, "()V", null, null)
        // @BmcProof(expect = <expect>). A bare VERIFIED expectation is the annotation default, so emit the
        // explicit enum only for a non-default (REFUTED demo) - mirrors the annotation form's choice.
        val av: AnnotationVisitor = mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true)
        if (d.expect != "VERIFIED") {
            av.visitEnum("expect", "Lorg/bmc4j/Verdict;", d.expect)
        }
        av.visitEnd()
        mv.visitCode()

        val argType = Type.getArgumentTypes(d.targetDesc)[0]
        val retType = Type.getReturnType(d.targetDesc)
        val selfType = Type.getObjectType(d.targetOwner)

        // A LineNumberTable is REQUIRED: jbmc's loop/unwind and value tracking are sensitive to it
        // (a method with no line numbers gets spurious REFUTED - see the LineNumberTable soundness note).
        // We stamp a fresh line per statement so each step has a distinct location, exactly like javac.
        var line = 1
        fun lineMark() {
            val l = org.objectweb.asm.Label()
            mv.visitLabel(l)
            mv.visitLineNumber(line++, l)
        }

        // self = (Self) nondetWithoutNull();  a = nondet();
        val selfSlot = 1
        val argSlot = 2
        val retSlot = 3
        lineMark()
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetWithoutNull",
                "()Ljava/lang/Object;", false)
        mv.visitTypeInsn(Opcodes.CHECKCAST, d.targetOwner)
        mv.visitVarInsn(Opcodes.ASTORE, selfSlot)
        // Pin the receiver non-null: assume(self != null), so the predicate's Kotlin checkNotNullParameter
        // guards cannot trip on a would-be-null receiver. Computed as a boolean and fed to Bmc.assume.
        mv.visitVarInsn(Opcodes.ALOAD, selfSlot)
        val nn = org.objectweb.asm.Label()
        val nnDone = org.objectweb.asm.Label()
        mv.visitJumpInsn(Opcodes.IFNULL, nn)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitJumpInsn(Opcodes.GOTO, nnDone)
        mv.visitLabel(nn)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitLabel(nnDone)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "assume", "(Z)V", false)
        lineMark()
        pushNondet(mv, argType)
        mv.visitVarInsn(argType.getOpcode(Opcodes.ISTORE), argSlot)

        // assume(pre(self, a)) - the precondition predicate body, called directly (monomorphic). For an
        // instance-form predicate (a kotlinc callable-reference class) the singleton receiver is pushed
        // first; for a static-form predicate (an indy lambda body) it is a plain invokestatic.
        lineMark()
        pushPredicateReceiver(mv, d.pre)
        loadCoerced(mv, selfSlot, selfType, predParam(d.pre, 0))
        loadCoerced(mv, argSlot, argType, predParam(d.pre, 1))
        callPredicate(mv, d.pre)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "assume", "(Z)V", false)

        // ret = self.member(a)   (the REAL instance body)
        lineMark()
        mv.visitVarInsn(Opcodes.ALOAD, selfSlot)
        mv.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), argSlot)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, d.targetOwner, d.targetName, d.targetDesc, false)
        mv.visitVarInsn(retType.getOpcode(Opcodes.ISTORE), retSlot)
        lineMark()

        // check(post(before=self, after=self, a, ret))   (before == after for a pure instance method)
        pushPredicateReceiver(mv, d.post)
        loadCoerced(mv, selfSlot, selfType, predParam(d.post, 0))
        loadCoerced(mv, selfSlot, selfType, predParam(d.post, 1))
        loadCoerced(mv, argSlot, argType, predParam(d.post, 2))
        loadCoerced(mv, retSlot, retType, predParam(d.post, 3))
        callPredicate(mv, d.post)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false)

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    /** The i-th declared parameter type of a predicate body handle's descriptor (the predicate's value
     *  params; a receiver is never in the descriptor - it is the handle's owner for an instance handle). */
    private fun predParam(h: Handle, i: Int): Type = Type.getArgumentTypes(h.desc)[i]

    /** True if a predicate body handle is an instance method (a kotlinc callable-reference class's
     *  `invoke`/`test`), so the singleton receiver must be pushed before the args. */
    private fun isInstancePred(h: Handle): Boolean =
            h.tag == Opcodes.H_INVOKEVIRTUAL || h.tag == Opcodes.H_INVOKEINTERFACE

    /** Push the singleton receiver for an instance-form predicate (`getstatic <owner>.INSTANCE`); a no-op
     *  for a static-form predicate (an indy lambda body). */
    private fun pushPredicateReceiver(mv: MethodVisitor, h: Handle) {
        if (isInstancePred(h)) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, h.owner, "INSTANCE", "L${h.owner};")
        }
    }

    /** Invoke a predicate body: a plain `invokestatic` for an indy lambda body, or an `invokevirtual` on
     *  the singleton for a kotlinc callable-reference class's `test`/`invoke`. */
    private fun callPredicate(mv: MethodVisitor, h: Handle) {
        val op = if (isInstancePred(h)) {
            if (h.tag == Opcodes.H_INVOKEINTERFACE) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        } else {
            Opcodes.INVOKESTATIC
        }
        mv.visitMethodInsn(op, h.owner, h.name, h.desc, h.tag == Opcodes.H_INVOKEINTERFACE)
    }

    /** Load local [slot] of source type [src], coerced (box/unbox/cast) to the lambda parameter [dst]. */
    private fun loadCoerced(mv: MethodVisitor, slot: Int, src: Type, dst: Type) {
        mv.visitVarInsn(src.getOpcode(Opcodes.ILOAD), slot)
        coerce(mv, src, dst)
    }

    private fun pushNondet(mv: MethodVisitor, t: Type) {
        when (t.sort) {
            Type.INT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetInt", "()I", false)
            Type.SHORT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetShort", "()S", false)
            Type.BYTE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetByte", "()B", false)
            Type.CHAR -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetChar", "()C", false)
            Type.BOOLEAN -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetBoolean", "()Z", false)
            Type.LONG -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetLong", "()J", false)
            Type.FLOAT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetFloat", "()F", false)
            Type.DOUBLE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetDouble", "()D", false)
            else -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetWithoutNull",
                        "()Ljava/lang/Object;", false)
                if (t.internalName != "java/lang/Object") {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, t.internalName)
                }
            }
        }
    }

    /** Coerce top-of-stack from [src] to [dst] (box/unbox/widen/cast), mirroring [LambdaBytecode]. */
    private fun coerce(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.descriptor == dst.descriptor) {
            return
        }
        val srcPrim = src.sort in Type.BOOLEAN..Type.DOUBLE
        val dstPrim = dst.sort in Type.BOOLEAN..Type.DOUBLE
        when {
            srcPrim && dstPrim -> widen(mv, src, dst)
            srcPrim -> {
                val w = wrapper(src)
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, w, "valueOf",
                        "(" + src.descriptor + ")L" + w + ";", false)
            }
            dstPrim -> {
                val w = wrapper(dst)
                mv.visitTypeInsn(Opcodes.CHECKCAST, w)
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, w, unbox(dst), "()" + dst.descriptor, false)
            }
            dst.internalName != "java/lang/Object" -> mv.visitTypeInsn(Opcodes.CHECKCAST, dst.internalName)
        }
    }

    private fun widen(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.sort <= Type.INT) {
            when (dst.sort) {
                Type.LONG -> mv.visitInsn(Opcodes.I2L)
                Type.FLOAT -> mv.visitInsn(Opcodes.I2F)
                Type.DOUBLE -> mv.visitInsn(Opcodes.I2D)
            }
        } else if (src.sort == Type.LONG) {
            when (dst.sort) {
                Type.FLOAT -> mv.visitInsn(Opcodes.L2F)
                Type.DOUBLE -> mv.visitInsn(Opcodes.L2D)
            }
        } else if (src.sort == Type.FLOAT && dst.sort == Type.DOUBLE) {
            mv.visitInsn(Opcodes.F2D)
        }
    }

    private fun wrapper(t: Type): String = when (t.sort) {
        Type.BOOLEAN -> "java/lang/Boolean"
        Type.CHAR -> "java/lang/Character"
        Type.BYTE -> "java/lang/Byte"
        Type.SHORT -> "java/lang/Short"
        Type.INT -> "java/lang/Integer"
        Type.LONG -> "java/lang/Long"
        Type.FLOAT -> "java/lang/Float"
        Type.DOUBLE -> "java/lang/Double"
        else -> throw IllegalArgumentException("not a primitive: $t")
    }

    private fun unbox(t: Type): String = when (t.sort) {
        Type.BOOLEAN -> "booleanValue"
        Type.CHAR -> "charValue"
        Type.BYTE -> "byteValue"
        Type.SHORT -> "shortValue"
        Type.INT -> "intValue"
        Type.LONG -> "longValue"
        Type.FLOAT -> "floatValue"
        Type.DOUBLE -> "doubleValue"
        else -> throw IllegalArgumentException("not a primitive: $t")
    }
}
