package org.bmc4j.engine

import org.bmc4j.contracts.Case
import org.bmc4j.contracts.ContractDefinition
import org.bmc4j.contracts.EnforcementLevel
import org.bmc4j.contracts.ExpectEnforce
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lowers the contracts DSL ([org.bmc4j.contracts.contractFor]) onto the existing enforce-proof backend.
 *
 * The DSL **executes** at build time and self-registers a [ContractDefinition] per top-level `val`
 * (structural facts: the cases, labels, frame presence, expected exception types, level, expect-verdict).
 * This decoder supplies the missing piece - the predicate **implementation handles** - by a NARROW static
 * read of that one `contractFor(...)` site in the contracts facade's `<clinit>`, the same
 * indy-bootstrap-argument decode [AssumeContractBytecode] uses to sidestep the invokedynamic fault line.
 * In source order the `<clinit>` pushes `getstatic <facade>$<valName>$1.INSTANCE` (the member
 * callable-ref) then the contract-body singleton; the body's `invoke` holds the predicate SAM sites
 * (`whenPrecondition`/`thenPostCondition`/`updatesOnly`) in source order. Registration order zips 1:1 with
 * the bytecode site order, so the structural [Case] list lines up with the decoded handles.
 *
 * Each predicate is lowered to a DIRECT `invokestatic`/`invokevirtual` of its compiled lambda body, never
 * through a megamorphic `FunctionN.invoke` (which JBMC will not devirtualize). The generated enforce proof
 * snapshots the receiver's pre-state into an independent `before` object, runs the REAL body, then checks
 * the postcondition (`before`/`after` relate pre/post) and the frame (locations outside the modifies set
 * are unchanged) - exactly the `assume(pre); run body; check(posts); check(frame)` the issue specifies.
 */
internal object ContractDslBytecode {

    private const val CPROVER = "org/cprover/CProver"
    private const val BMC = "org/bmc4j/Bmc"
    private const val METAFACTORY = "java/lang/invoke/LambdaMetafactory"
    private const val CONTRACT_FOR = "contractFor"

    /** The synthetic default-args wrapper kotlinc emits when a `contractFor(...)` call omits the optional
     *  `level`/`expect` (the common case): the body still appears as the last SAM value before this call. */
    private const val CONTRACT_FOR_DEFAULT = "contractFor\$default"

    /** The contracts package facade methods - `contractFor` overloads compile to `ContractDslKt`. */
    private const val DSL_FACADE = "org/bmc4j/contracts/ContractDslKt"

    class ContractDslError(message: String) : RuntimeException(message)

    /** Classpath roots for reading sibling/nested classes (the member-ref + body + predicate classes,
     *  and the contracted target's own class for its field layout). Set per [lower] call. */
    @JvmField
    var classRoots: List<Path> = emptyList()

    // === public entry: lower a facade's registered definitions to an enforce class ===================

    /** One lowered contract: the decoded target + the structural definition + the per-case predicate
     *  handles, ready for [emitProof]. */
    class Lowered(
            @JvmField val targetOwner: String,
            @JvmField val targetName: String,
            @JvmField val targetDesc: String,
            @JvmField val isInstance: Boolean,
            @JvmField val definition: ContractDefinition,
            @JvmField val cases: List<LoweredCase>,
            /** A stable enforce-method name unique within the generated class. */
            @JvmField val enforceMethod: String,
    )

    /** A case's decoded predicate handles, paired with its structural [Case]. */
    class LoweredCase(
            @JvmField val case: Case,
            @JvmField val pre: Handle,
            @JvmField val posts: List<Handle>,
            /** The frame lambda handle when `updatesOnly` was declared, else null. */
            @JvmField val frame: Handle?,
    )

    /**
     * Decode every `contractFor(...)` site in the contracts facade [facadeBytes], zip it with the
     * registry [definitions] (same source order), and return the lowered contracts. Throws
     * [ContractDslError] when a site cannot be resolved or the frame cannot be determined (fail loud).
     */
    fun lower(facadeBytes: ByteArray, definitions: List<ContractDefinition>): List<Lowered> {
        val sites = decodeSites(facadeBytes)
        if (sites.size != definitions.size) {
            throw ContractDslError(
                    "the contracts facade has ${sites.size} contractFor(...) site(s) but the registry holds" +
                            " ${definitions.size} definition(s) - they must zip 1:1 in source order.")
        }
        return sites.mapIndexed { i, site -> lowerOne(site, definitions[i], i) }
    }

    // === step 1: decode the contractFor sites from the facade <clinit> ===============================

    /** A decoded `contractFor(member, ...) { body }` site: the member singleton class and the body
     *  singleton class (both kotlinc callable-reference / lambda singletons in the 2.3/2.4 shapes). */
    /** A SAM value pushed before a `contractFor` call: a kotlinc callable-reference / lambda singleton
     *  class (`getstatic <C>.INSTANCE`, in [singletonClass]) OR a `LambdaMetafactory` indy whose impl
     *  handle ([handle]) points at the synthetic method holding the value (the facade body method, or a
     *  forwarding thunk for the member reference). One decoder serves both kotlinc shapes (2.3/2.4) and
     *  the default-args lowering (which emits the body as an indy, not a singleton). */
    private class SamValue(@JvmField val singletonClass: String?, @JvmField val handle: Handle?)

    /** A decoded `contractFor(member, ...) { body }` site: the member ref value and the body value. */
    private class Site(@JvmField val member: SamValue, @JvmField val body: SamValue)

    private fun decodeSites(facadeBytes: ByteArray): List<Site> {
        val sites = ArrayList<Site>()
        ClassReader(facadeBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, name: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9) {
                        // The two SAM values pushed before each contractFor call: the member ref (prev) and
                        // the contract body (last). Default args push two `aconst_null`s + an int + a null
                        // between the body and the call, but those are not SAM values so they don't disturb
                        // prev/last.
                        private var prev: SamValue? = null
                        private var last: SamValue? = null

                        private fun push(v: SamValue) {
                            prev = last
                            last = v
                        }

                        override fun visitFieldInsn(op: Int, owner: String?, n: String?, dsc: String?) {
                            if (op == Opcodes.GETSTATIC && n == "INSTANCE" && owner != null
                                    && !owner.startsWith("kotlin/") && !owner.startsWith("java/")) {
                                push(SamValue(owner, null))
                            }
                        }

                        override fun visitTypeInsn(op: Int, type: String?) {
                            // `new <member-ref-class>` (a captured callable reference, e.g. an `object`
                            // method ref bound to its INSTANCE) is the member value when followed by its
                            // <init>; record the class now and let the dup/init plumbing pass through.
                            if (op == Opcodes.NEW && type != null
                                    && !type.startsWith("kotlin/") && !type.startsWith("java/")) {
                                push(SamValue(type, null))
                            }
                        }

                        override fun visitInvokeDynamicInsn(n: String?, dsc: String?, bsm: Handle?,
                                                            vararg bsmArgs: Any?) {
                            samImplHandle(bsm, bsmArgs)?.let { push(SamValue(null, it)) }
                        }

                        override fun visitMethodInsn(op: Int, owner: String?, n: String?, dsc: String?,
                                                     itf: Boolean) {
                            if (op == Opcodes.INVOKESTATIC && owner == DSL_FACADE
                                    && (n == CONTRACT_FOR || n == CONTRACT_FOR_DEFAULT)) {
                                val body = last ?: throw ContractDslError(
                                        "a contractFor(...) site is missing its contract-body lambda.")
                                val member = prev ?: throw ContractDslError(
                                        "a contractFor(...) site is missing its member reference - pass a" +
                                                " direct unbound member reference (Type::member).")
                                sites.add(Site(member, body))
                                prev = null; last = null
                            }
                        }
                    }
        }, 0)
        return sites
    }

    // === step 2: resolve target + predicate handles, zip with the structural definition ==============

    private fun lowerOne(site: Site, def: ContractDefinition, index: Int): Lowered {
        val memberClass = site.member.singletonClass
                ?: throw ContractDslError("the contractFor member reference was not a resolvable" +
                        " callable-reference - pass a direct unbound member reference (Type::member).")
        val target = resolveTarget(memberClass)
        val sources = bodyPredicateSources(site.body)
        // Zip the flat handle stream against the structural cases: per case, consume one pre handle, then
        // one post handle per declared postcondition, then one frame handle iff updatesOnly was declared.
        // A thenThrows case consumes only its pre handle (no lambda). Source order is preserved on both
        // sides, so the stream lines up exactly.
        var cursor = 0
        fun next(what: String): Handle = sources.getOrNull(cursor++)
                ?: throw ContractDslError("ran out of predicate lambdas decoding $what for" +
                        " ${target.owner}.${target.name}; the registry and bytecode disagree.")
        val cases = def.cases.map { case ->
            val pre = next("a whenPrecondition")
            val posts = case.postconditionLabels.map { next("a thenPostCondition") }
            val frame = if (case.hasExplicitFrame) next("an updatesOnly frame") else null
            LoweredCase(case, pre, posts, frame)
        }
        return Lowered(target.owner, target.name, target.desc, target.isInstance, def, cases,
                "enforce__${target.name}__$index")
    }

    private class Target(@JvmField val owner: String, @JvmField val name: String,
                         @JvmField val desc: String, @JvmField val isInstance: Boolean)

    /** Resolve the real method a kotlinc callable-reference class forwards to: its `invoke(...)` body is
     *  a null-check guard then a single `invokevirtual`/`invokestatic` of the contracted method. */
    private fun resolveTarget(refClass: String): Target {
        val bytes = readClass(refClass)
                ?: throw ContractDslError("could not read the member-reference class $refClass" +
                        " - pass a direct unbound member reference (Type::member).")
        var target: Target? = null
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, name: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (name != "invoke") return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitMethodInsn(op: Int, owner: String?, n: String?, dsc: String?,
                                                 itf: Boolean) {
                        if (owner == "kotlin/jvm/internal/Intrinsics" || isBoxOwner(owner)) {
                            return // null-check guard / box-unbox plumbing, not the forwarded call
                        }
                        if (target == null && owner != null && n != null && dsc != null
                                && (op == Opcodes.INVOKEVIRTUAL || op == Opcodes.INVOKEINTERFACE
                                        || op == Opcodes.INVOKESTATIC)) {
                            target = Target(owner, n, dsc, op != Opcodes.INVOKESTATIC)
                        }
                    }
                }
            }
        }, 0)
        return target ?: throw ContractDslError(
                "could not resolve the contracted method from $refClass.")
    }

    private fun isBoxOwner(owner: String?): Boolean = owner in setOf(
            "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte",
            "java/lang/Character", "java/lang/Boolean", "java/lang/Float", "java/lang/Double")

    /** The predicate body handles in source order from a contract body. The body is either an indy whose
     *  impl handle ([SamValue.handle]) points at the facade's synthetic body method (kotlinc 2.4 /
     *  default-args lowering), or a singleton class ([SamValue.singletonClass]) whose `invoke` holds the
     *  body (kotlinc 2.3). Either way we scan THAT method's instructions for the predicate SAM sites. */
    private fun bodyPredicateSources(body: SamValue): List<Handle> {
        val (bytes, methodName, methodDesc) = if (body.handle != null) {
            val owner = body.handle.owner
            Triple(readClass(owner)
                    ?: throw ContractDslError("could not read the contract-body class $owner."),
                    body.handle.name, body.handle.desc)
        } else {
            val owner = body.singletonClass!!
            Triple(readClass(owner)
                    ?: throw ContractDslError("could not read the contract-body class $owner."),
                    "invoke", null)
        }
        return scanPredicateSites(bytes, methodName, methodDesc)
    }

    /** Scan the named method (matching [desc] too when given) for the predicate SAM sites in source order:
     *  a `LambdaMetafactory` indy (impl handle is the lambda body) or a nested callable-reference singleton
     *  (kotlinc 2.3) resolved to its `invoke`/`test`/`locations`. */
    private fun scanPredicateSites(bytes: ByteArray, name: String, desc: String?): List<Handle> {
        val out = ArrayList<Handle>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != name || (desc != null && d != desc)) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInvokeDynamicInsn(idn: String?, idd: String?, bsm: Handle?,
                                                        vararg bsmArgs: Any?) {
                        samImplHandle(bsm, bsmArgs)?.let { out.add(it) }
                    }

                    override fun visitFieldInsn(op: Int, owner: String?, fn: String?, fd: String?) {
                        if (op == Opcodes.GETSTATIC && fn == "INSTANCE" && owner != null
                                && !owner.startsWith("kotlin/") && !owner.startsWith("java/")) {
                            out.add(resolveSingletonPredicate(owner))
                        }
                    }
                }
            }
        }, 0)
        return out
    }

    /** Resolve a kotlinc-2.3 callable-reference predicate singleton to its boolean/void body handle. */
    private fun resolveSingletonPredicate(refClass: String): Handle {
        val bytes = readClass(refClass)
                ?: throw ContractDslError("could not read the predicate class $refClass.")
        var handle: Handle? = null
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if ((n == "test" || n == "invoke" || n == "locations") && d != null && handle == null) {
                    handle = Handle(Opcodes.H_INVOKEVIRTUAL, refClass, n, d, false)
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        return handle ?: throw ContractDslError("predicate class $refClass has no body method.")
    }

    private fun samImplHandle(bsm: Handle?, bsmArgs: Array<out Any?>): Handle? {
        if (bsm != null && METAFACTORY == bsm.owner
                && (bsm.name == "metafactory" || bsm.name == "altMetafactory")
                && bsmArgs.size >= 2 && bsmArgs[1] is Handle) {
            return bsmArgs[1] as Handle
        }
        return null
    }

    private fun readClass(internalName: String): ByteArray? {
        val resource = "$internalName.class"
        for (root in classRoots) {
            val f = root.resolve(resource)
            if (Files.isRegularFile(f)) {
                return Files.readAllBytes(f)
            }
        }
        return null
    }

    // === taint: the verdict-note a non-fully-proved level rides ======================================

    /**
     * The verdict-taint notes the [lowered] contracts carry, mirroring the assume-guarantee footnote
     * ("VERIFIED under assumed contract ... NOT unconditional"): a [EnforcementLevel.NONE] contract is an
     * ASSUMED axiom (its enforce proof is skipped, so any verdict that relies on it is `[NONE: assumed]`),
     * and a [EnforcementLevel.TRUSTED_PURE] contract still proves the body but trusts predicate purity
     * (`[TRUSTED_PURE: predicate purity trusted]`). [EnforcementLevel.MUST_BE_PURE] is fully sound and
     * carries no taint. The notes ride up exactly like the assume-guarantee note (surfaced at the lowering
     * boundary while call-site summary reuse is staged).
     */
    fun taintNotes(lowered: List<Lowered>): List<String> = lowered.mapNotNull { l ->
        val target = "${l.targetOwner.replace('/', '.')}.${l.targetName}"
        when (l.definition.level) {
            EnforcementLevel.NONE -> "$target [NONE: assumed]"
            EnforcementLevel.TRUSTED_PURE -> "$target [TRUSTED_PURE: predicate purity trusted]"
            EnforcementLevel.MUST_BE_PURE -> null
        }
    }

    // === step 3: emit the enforce class ==============================================================

    /** Generate the enforce-proof class [internalName] holding one `@BmcProof` per lowered contract whose
     *  level is not [EnforcementLevel.NONE] (a NONE contract is assumed, never enforced). */
    fun generateEnforceClass(internalName: String, lowered: List<Lowered>): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ctor.visitCode()
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()
        for (l in lowered) {
            if (l.definition.level != EnforcementLevel.NONE) {
                emitProof(cw, l)
            }
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    /**
     * Emit one enforce proof. For an instance target with a mutating body and a relating postcondition:
     *
     * ```
     * @BmcProof
     * void enforce__deposit__0() {
     *     Account self  = (Account) nondetWithoutNull(); assume(self != null);   // post-state object
     *     Account before = (Account) nondetWithoutNull(); assume(before != null);// pre-state stand-in
     *     assume(before.balance == self.balance);   // pin every receiver field: before snapshots pre-state
     *     int a = nondet();
     *     // (frame snapshots of non-modified fields would be taken here)
     *     assume(pre(self, a));                      // whenPrecondition, called directly
     *     self.deposit(a);                           // the REAL body (mutates self)
     *     check(post(before, self, a, ret));         // thenPostCondition: before/after relate pre/post
     *     // check(frame): non-modified fields unchanged vs their before snapshot
     * }
     * ```
     *
     * A `thenThrows<E>` case instead asserts the call throws `E` and does not return normally.
     */
    private fun emitProof(cw: ClassWriter, l: Lowered) {
        // One proof per case (each when -> then is an independent implication), so a multi-case contract
        // emits enforce__<m>__<idx>__case<n>; a single case keeps the plain name.
        l.cases.forEachIndexed { caseIndex, lc ->
            val suffix = if (l.cases.size == 1) "" else "__case$caseIndex"
            emitCaseProof(cw, l, lc, l.enforceMethod + suffix)
        }
    }

    private fun emitCaseProof(cw: ClassWriter, l: Lowered, lc: LoweredCase, methodName: String) {
        val expect = if (l.definition.expect == ExpectEnforce.REFUTED) "REFUTED" else "VERIFIED"
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        val av: AnnotationVisitor = mv.visitAnnotation("Lorg/bmc4j/BmcProof;", true)
        if (expect != "VERIFIED") {
            av.visitEnum("expect", "Lorg/bmc4j/Verdict;", expect)
        }
        av.visitEnd()
        mv.visitCode()

        // The reference threads its FIRST parameter in as the subject (`self`, and `before`/`after` in
        // postconditions). For an INSTANCE target that subject is the receiver object (declared owner);
        // its remaining call args are targetDesc. For a STATIC target the subject is the method's FIRST
        // argument (a value); the rest are the remaining args. A static value subject is not mutated by the
        // call, so before == after == the subject value (the uniform `self` slot the issue describes).
        val descArgs = Type.getArgumentTypes(l.targetDesc)
        val retType = Type.getReturnType(l.targetDesc)
        val subjectType: Type
        val callArgTypes: Array<Type>
        if (l.isInstance) {
            subjectType = Type.getObjectType(l.targetOwner)
            callArgTypes = descArgs
        } else {
            require(descArgs.isNotEmpty()) {
                "a static contract target needs at least one parameter (threaded as `self`)."
            }
            subjectType = descArgs[0]
            callArgTypes = descArgs.copyOfRange(1, descArgs.size)
        }

        var line = 1
        fun lineMark() {
            val lbl = Label()
            mv.visitLabel(lbl)
            mv.visitLineNumber(line++, lbl)
        }

        // Slot plan: subject (self/after), before, remaining args..., (ret), frame snapshots...
        val subjectSlot = 1
        var nextSlot = 2
        val beforeSlot: Int
        if (l.isInstance) {
            beforeSlot = nextSlot
            nextSlot += 1
        } else {
            beforeSlot = subjectSlot // a value subject is its own pre-state
        }
        val argSlots = IntArray(callArgTypes.size)
        for (i in callArgTypes.indices) {
            argSlots[i] = nextSlot
            nextSlot += callArgTypes[i].size
        }

        // The receiver fields whose pre-call values are snapshotted into PRIMITIVE locals: every readable
        // field (the postcondition's `before.field` reads resolve to these snapshot locals, and the frame
        // check reuses the non-modified ones). One `self` heap cell - no second `before` object - so JBMC
        // never reasons over two same-type heap cells (the path that does not decide in reasonable time).
        val snapshotFields = if (l.isInstance) readableFields(l.targetOwner) else emptyList()

        lineMark()
        if (l.isInstance) {
            // self = nondet receiver. Its pre-state is captured as primitive snapshots below; the body's
            // mutation of self moves only the live object, never the snapshots.
            newNonNull(mv, l.targetOwner, subjectSlot)
            // nondet the call args.
            for (i in callArgTypes.indices) {
                pushNondet(mv, callArgTypes[i])
                mv.visitVarInsn(callArgTypes[i].getOpcode(Opcodes.ISTORE), argSlots[i])
            }
        } else {
            // The subject IS the first call arg; nondet it, then the rest.
            pushNondet(mv, subjectType)
            mv.visitVarInsn(subjectType.getOpcode(Opcodes.ISTORE), subjectSlot)
            for (i in callArgTypes.indices) {
                pushNondet(mv, callArgTypes[i])
                mv.visitVarInsn(callArgTypes[i].getOpcode(Opcodes.ISTORE), argSlots[i])
            }
        }

        // Snapshot every readable receiver field's PRE-CALL value into a primitive local.
        val preSnapshotSlots = HashMap<String, Int>()
        val preSnapshotTypes = HashMap<String, Type>()
        for (f in snapshotFields) {
            lineMark()
            loadFieldVia(mv, subjectSlot, l.targetOwner, f)
            mv.visitVarInsn(f.type.getOpcode(Opcodes.ISTORE), nextSlot)
            preSnapshotSlots[f.name] = nextSlot
            preSnapshotTypes[f.name] = f.type
            nextSlot += f.type.size
        }
        // The frame check (after the call) compares the non-modified fields to their pre-call snapshot.
        val modifies = if (l.isInstance) frameModifies(l, lc) else emptySet()
        val frameFields = snapshotFields.filter { it.name !in modifies }

        // assume(pre(self, args...)). The body has not run, so `self.field` is still the pre-state.
        lineMark()
        emitPredicateCall(mv, lc.pre, subjectType, subjectSlot, beforeSlot, callArgTypes, argSlots,
                retSlot = -1, retType = null, includeBefore = false, includeRet = false)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "assume", "(Z)V", false)

        if (lc.case.throwsType != null) {
            emitThrowsCase(mv, l, lc, subjectType, subjectSlot, callArgTypes, argSlots, ::lineMark)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0)
            mv.visitEnd()
            return
        }

        // ret = call the REAL body.
        lineMark()
        val retSlot = if (retType.sort != Type.VOID) nextSlot else -1
        if (retSlot >= 0) nextSlot += retType.size
        emitRealCall(mv, l, subjectType, subjectSlot, callArgTypes, argSlots)
        if (retSlot >= 0) {
            mv.visitVarInsn(retType.getOpcode(Opcodes.ISTORE), retSlot)
        }

        // check(post(before, after=self, args..., ret)) for each declared postcondition. For an instance
        // target the postcondition is INLINED: its body is replayed with `before.field` reads rewritten to
        // the primitive pre-snapshot locals (so `before` is never a heap object) and `after` bound to the
        // single live `self`. A static target has no receiver heap state, so its predicate is called direct.
        for (post in lc.posts) {
            lineMark()
            if (l.isInstance) {
                nextSlot = inlinePostcondition(mv, post, l.targetOwner, subjectSlot, callArgTypes, argSlots,
                        retSlot, retType, preSnapshotSlots, preSnapshotTypes, nextSlot)
            } else {
                emitPredicateCall(mv, post, subjectType, subjectSlot, beforeSlot, callArgTypes, argSlots,
                        retSlot, retType, includeBefore = true, includeRet = true)
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false)
        }

        // check(frame): every non-modified field equals its pre-call snapshot.
        for (f in frameFields) {
            lineMark()
            loadFieldVia(mv, subjectSlot, l.targetOwner, f)
            mv.visitVarInsn(f.type.getOpcode(Opcodes.ILOAD), preSnapshotSlots[f.name]!!)
            cmpEqCheck(mv, f.type)
        }

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    /** Push the subject (instance only) + call args and invoke the real contracted body. */
    private fun emitRealCall(mv: MethodVisitor, l: Lowered, subjectType: Type, subjectSlot: Int,
                             callArgTypes: Array<Type>, argSlots: IntArray) {
        if (l.isInstance) {
            mv.visitVarInsn(Opcodes.ALOAD, subjectSlot)
            for (i in callArgTypes.indices) {
                mv.visitVarInsn(callArgTypes[i].getOpcode(Opcodes.ILOAD), argSlots[i])
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, l.targetOwner, l.targetName, l.targetDesc, false)
        } else {
            // Static: the subject is the first method arg.
            mv.visitVarInsn(subjectType.getOpcode(Opcodes.ILOAD), subjectSlot)
            for (i in callArgTypes.indices) {
                mv.visitVarInsn(callArgTypes[i].getOpcode(Opcodes.ILOAD), argSlots[i])
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, l.targetOwner, l.targetName, l.targetDesc, false)
        }
    }

    /** Emit a `thenThrows<E>` obligation: prove the call throws `E` and does not return normally. We run
     *  the call in a try/catch for `E`; the normal-return path asserts unreachable (must throw), and the
     *  catch is the success path. An undeclared throw escapes the catch and REFUTES the proof. */
    private fun emitThrowsCase(mv: MethodVisitor, l: Lowered, lc: LoweredCase, subjectType: Type,
                              subjectSlot: Int, callArgTypes: Array<Type>, argSlots: IntArray,
                              lineMark: () -> Unit) {
        val tryStart = Label()
        val tryEnd = Label()
        val handler = Label()
        val exType = lc.case.throwsType!!
        mv.visitTryCatchBlock(tryStart, tryEnd, handler, exType)
        lineMark()
        mv.visitLabel(tryStart)
        emitRealCall(mv, l, subjectType, subjectSlot, callArgTypes, argSlots)
        val retType = Type.getReturnType(l.targetDesc)
        if (retType.sort != Type.VOID) mv.visitInsn(if (retType.size == 2) Opcodes.POP2 else Opcodes.POP)
        mv.visitLabel(tryEnd)
        // Normal return reached -> the must-throw obligation FAILS: assert false.
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "check", "(Z)V", false)
        val done = Label()
        mv.visitJumpInsn(Opcodes.GOTO, done)
        // Caught the declared exception -> success.
        mv.visitLabel(handler)
        mv.visitInsn(Opcodes.POP)
        mv.visitLabel(done)
    }

    /**
     * Push a predicate's args and invoke its body directly. Arg order matches the fun-interface:
     * Pre: `(self, args...)`; Post: `(before, after, args..., ret)`. The subject is the receiver (instance)
     * or the first method arg (static); `before`/`after` are its pre/post values (equal for a static).
     */
    private fun emitPredicateCall(mv: MethodVisitor, h: Handle, subjectType: Type, subjectSlot: Int,
                                  beforeSlot: Int, callArgTypes: Array<Type>, argSlots: IntArray,
                                  retSlot: Int, retType: Type?,
                                  includeBefore: Boolean, includeRet: Boolean) {
        pushPredicateReceiver(mv, h)
        val params = Type.getArgumentTypes(h.desc)
        var p = 0
        if (includeBefore) {
            loadCoerced(mv, beforeSlot, subjectType, params[p++])   // before
            loadCoerced(mv, subjectSlot, subjectType, params[p++])  // after (= self)
        } else {
            loadCoerced(mv, subjectSlot, subjectType, params[p++])  // self (pre-state)
        }
        for (i in callArgTypes.indices) {
            loadCoerced(mv, argSlots[i], callArgTypes[i], params[p++])
        }
        if (includeRet && retType != null && retType.sort != Type.VOID) {
            loadCoerced(mv, retSlot, retType, params[p++])
        } else if (includeRet && retType != null) {
            // Unit/void return: the postcondition's `ret` is kotlin.Unit. Push the Unit singleton.
            mv.visitFieldInsn(Opcodes.GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;")
        }
        callPredicate(mv, h)
    }

    /**
     * Inline an INSTANCE postcondition body so it never reasons over a second `before` heap cell. The
     * predicate is a static synthetic lambda `(before, after, args..., ret)`; we replay its instruction
     * list onto [mv] with three rewrites, leaving the boolean result on the stack for the caller's `check`:
     *
     *  - `before.<field>` reads (an `ALOAD before` then a getter `invokevirtual` or `GETFIELD` on the
     *    receiver) become a load of that field's PRIMITIVE pre-snapshot local - so `before` is never an
     *    object and the only receiver heap cell is the live `self`.
     *  - the kotlinc `Intrinsics.checkNotNullParameter(...)` guards are stripped (with their two feeder
     *    instructions); non-nullness of the receiver is already established by an assume.
     *  - the remaining locals are relocated above the enforce method's slots, `after`/args/`ret` preloaded
     *    from the live `self` / arg / return locals, and each `IRETURN` becomes a jump to a common end
     *    label so the body's value lands on the stack rather than returning.
     *
     * Returns the next free local slot after the relocated predicate frame.
     */
    private fun inlinePostcondition(mv: MethodVisitor, h: Handle, owner: String, subjectSlot: Int,
                                    callArgTypes: Array<Type>, argSlots: IntArray, retSlot: Int,
                                    retType: Type?, preSnapshotSlots: Map<String, Int>,
                                    preSnapshotTypes: Map<String, Type>, nextSlot: Int): Int {
        val method = readMethodNode(h)
        val params = Type.getArgumentTypes(h.desc)
        // Param slots in the lambda frame: before=0, after=1, args..., ret (last). Long/double take 2.
        val beforeParam = 0
        val afterParam = 1
        var slot = afterParam + params[afterParam].size
        val argParamSlots = IntArray(callArgTypes.size)
        for (i in callArgTypes.indices) {
            argParamSlots[i] = slot
            slot += params[afterParam + 1 + i].size
        }
        val retParam = if (retType != null) slot else -1

        val inlineBase = nextSlot
        fun relocate(s: Int) = inlineBase + s
        var maxLocal = 0
        // Preload `after` <- self, args <- argSlots, ret <- retSlot / Unit, into the relocated frame.
        mv.visitVarInsn(Opcodes.ALOAD, subjectSlot)
        mv.visitVarInsn(Opcodes.ASTORE, relocate(afterParam))
        maxLocal = maxOf(maxLocal, afterParam + params[afterParam].size)
        for (i in callArgTypes.indices) {
            mv.visitVarInsn(callArgTypes[i].getOpcode(Opcodes.ILOAD), argSlots[i])
            coerce(mv, callArgTypes[i], params[afterParam + 1 + i])
            mv.visitVarInsn(params[afterParam + 1 + i].getOpcode(Opcodes.ISTORE), relocate(argParamSlots[i]))
            maxLocal = maxOf(maxLocal, argParamSlots[i] + params[afterParam + 1 + i].size)
        }
        if (retParam >= 0) {
            val rp = params.last()  // the postcondition's last param is always `ret`
            if (retType != null && retType.sort != Type.VOID) {
                mv.visitVarInsn(retType.getOpcode(Opcodes.ILOAD), retSlot)
                coerce(mv, retType, rp)
            } else {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;")
            }
            mv.visitVarInsn(rp.getOpcode(Opcodes.ISTORE), relocate(retParam))
            maxLocal = maxOf(maxLocal, retParam + rp.size)
        }

        val getterToField = gettersOf(owner)
        val end = Label()
        // Index of the next REAL instruction after [from] (skipping label/line/frame pseudo-nodes), or -1.
        val nodes = method.instructions.toArray()
        fun nextReal(from: Int): Int {
            var j = from + 1
            while (j < nodes.size && !nodes[j].isRealInsn()) j++
            return if (j < nodes.size) j else -1
        }

        var i = 0
        while (i < nodes.size) {
            val node = nodes[i]
            if (!node.isRealInsn()) { emitInlineNode(mv, node, ::relocate, end); i++; continue }
            // Strip the kotlinc null-guard `<load arg>; ldc "name"; Intrinsics.checkNotNullParameter`
            // (it leads each lambda for `before`/`after`/`ret`). Detected at the load by lookahead.
            val ldc = nextReal(i)
            val call = if (ldc >= 0) nextReal(ldc) else -1
            val callNode = if (call >= 0) nodes[call] else null
            if (callNode is MethodInsnNode && callNode.owner == "kotlin/jvm/internal/Intrinsics"
                    && callNode.name == "checkNotNullParameter") {
                i = call + 1
                continue
            }
            // `ALOAD before` then a getter/GETFIELD -> load that field's pre-snapshot primitive local
            // (so `before` is never materialized as an object).
            if (node is VarInsnNode && node.opcode == Opcodes.ALOAD && node.`var` == beforeParam) {
                val readNode = if (ldc >= 0) nodes[ldc] else null
                val field = beforeFieldRead(readNode, owner, getterToField)
                        ?: throw ContractDslError("a postcondition uses `before` other than as a field" +
                                " read (before.<field>) on $owner; only pre-state field reads are supported.")
                val s = preSnapshotSlots[field] ?: throw ContractDslError(
                        "postcondition reads before.$field but no pre-snapshot of $owner.$field exists.")
                mv.visitVarInsn(preSnapshotTypes[field]!!.getOpcode(Opcodes.ILOAD), s)
                i = ldc + 1
                continue
            }
            emitInlineNode(mv, node, ::relocate, end)
            i++
        }
        mv.visitLabel(end)
        // The relocated frame spans the predicate's full local area (params + any body temps).
        return inlineBase + maxOf(maxLocal, maxOf(slot, method.maxLocals))
    }

    /** A real bytecode instruction (not a label / line-number / frame pseudo-node). */
    private fun org.objectweb.asm.tree.AbstractInsnNode.isRealInsn(): Boolean = opcode >= 0

    /** If [node] is a getter invoke or GETFIELD on the receiver, the backing field name; else null. */
    private fun beforeFieldRead(node: org.objectweb.asm.tree.AbstractInsnNode?, owner: String,
                                getterToField: Map<String, String>): String? {
        if (node is MethodInsnNode && node.opcode == Opcodes.INVOKEVIRTUAL && node.owner == owner
                && getterToField.containsKey(node.name)) {
            return getterToField[node.name]
        }
        if (node is FieldInsnNode && node.opcode == Opcodes.GETFIELD && node.owner == owner) {
            return node.name
        }
        return null
    }

    /** Replay one predicate-body node onto [mv], remapping locals and turning IRETURN into a jump to the
     *  shared end label (leaving the boolean on the stack). Frame/line nodes are dropped (the generated
     *  class is V1_6, so it carries no StackMapTable). */
    private fun emitInlineNode(mv: MethodVisitor, node: org.objectweb.asm.tree.AbstractInsnNode,
                               relocate: (Int) -> Int, end: Label) {
        when (node) {
            is org.objectweb.asm.tree.FrameNode -> {}
            is org.objectweb.asm.tree.LineNumberNode -> {}
            is VarInsnNode -> mv.visitVarInsn(node.opcode, relocate(node.`var`))
            is org.objectweb.asm.tree.IincInsnNode -> mv.visitIincInsn(relocate(node.`var`), node.incr)
            is org.objectweb.asm.tree.InsnNode -> {
                if (node.opcode == Opcodes.IRETURN) {
                    mv.visitJumpInsn(Opcodes.GOTO, end)
                } else {
                    node.accept(mv)
                }
            }
            else -> node.accept(mv)
        }
    }

    /** Read the predicate lambda body referenced by [h] into a MethodNode (with its instruction list). */
    private fun readMethodNode(h: Handle): MethodNode {
        val bytes = readClass(h.owner)
                ?: throw ContractDslError("could not read the predicate class ${h.owner}.")
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG)
        return cn.methods.firstOrNull { it.name == h.name && it.desc == h.desc }
                ?: throw ContractDslError("predicate ${h.owner}.${h.name}${h.desc} not found for inlining.")
    }

    // --- frame: the modifies set named by updatesOnly ------------------------------------------------

    /** The receiver field names the `updatesOnly` frame lambda reads (its body never runs; only the
     *  field reads matter). When a case has an explicit frame, every read is a permitted modification.
     *  Fail loud when an instance case has NO frame and the body is unanalyzable for inference - increment
     *  1 needs the explicit `updatesOnly` for a mutating instance contract. */
    private fun frameModifies(l: Lowered, lc: LoweredCase): Set<String> {
        if (lc.frame == null) {
            // No explicit frame. A normal-return instance case must declare what it changes (we do not
            // silently havoc-everything). A thenThrows case changes nothing observable on normal return.
            if (lc.case.throwsType != null) return emptySet()
            // Inference would go here; for increment 1 a mutating instance target needs an explicit frame.
            // If the receiver has no writable field a method could touch, the empty set is sound.
            return inferOrFailFrame(l)
        }
        return frameReadFields(lc.frame, l.targetOwner)
    }

    /** Read the field names a frame lambda body reads on the receiver (via GETFIELD or a getter call). */
    private fun frameReadFields(frame: Handle, owner: String): Set<String> {
        val bytes = readClass(frame.owner)
                ?: throw ContractDslError("could not read the frame lambda class ${frame.owner}.")
        val names = LinkedHashSet<String>()
        val getterToField = gettersOf(owner)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != frame.name) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitFieldInsn(op: Int, fo: String?, fn: String?, fd: String?) {
                        if (op == Opcodes.GETFIELD && fo == owner && fn != null) names.add(fn)
                    }

                    override fun visitMethodInsn(op: Int, mo: String?, mn: String?, md: String?,
                                                 itf: Boolean) {
                        if (mo == owner && mn != null && getterToField.containsKey(mn)) {
                            names.add(getterToField[mn]!!)
                        }
                    }
                }
            }
        }, 0)
        return names
    }

    /** Auto-detected frame for own code: a sound over-approximation of the receiver fields the body
     *  writes (PUTFIELD on the receiver). Fail loud if the body cannot be read. */
    private fun inferOrFailFrame(l: Lowered): Set<String> {
        val bytes = readClass(l.targetOwner) ?: throw ContractDslError(
                "could not determine what ${l.targetOwner}.${l.targetName} changes, so callers must" +
                        " assume it changes everything; add updatesOnly { ... } to tighten.")
        val written = LinkedHashSet<String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n != l.targetName || d != l.targetDesc) return null
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitFieldInsn(op: Int, fo: String?, fn: String?, fd: String?) {
                        if (op == Opcodes.PUTFIELD && fo == l.targetOwner && fn != null) written.add(fn)
                    }
                }
            }
        }, 0)
        return written
    }

    // --- receiver field layout (primitive-typed, readable via a zero-arg getter) ---------------------

    private class Field(@JvmField val name: String, @JvmField val type: Type,
                        @JvmField val getter: String?)

    /** The receiver's instance fields readable for snapshotting/frame checks: primitive-typed fields with
     *  a public zero-arg getter (Kotlin properties) or a directly-readable field. */
    private fun readableFields(owner: String): List<Field> {
        val bytes = readClass(owner) ?: return emptyList()
        val fields = ArrayList<Field>()
        val getters = gettersOf(owner) // getterName -> fieldName
        val fieldToGetter = getters.entries.associate { it.value to it.key }
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(access: Int, name: String?, desc: String?, sig: String?,
                                    value: Any?): org.objectweb.asm.FieldVisitor? {
                if (name != null && desc != null && access and Opcodes.ACC_STATIC == 0) {
                    val t = Type.getType(desc)
                    if (t.sort in Type.BOOLEAN..Type.DOUBLE) {
                        // Prefer a getter (the field may be private); fall back to a public field.
                        val getter = fieldToGetter[name]
                        if (getter != null || access and Opcodes.ACC_PUBLIC != 0) {
                            fields.add(Field(name, t, getter))
                        }
                    }
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        return fields
    }

    /** Map of zero-arg primitive getters `getX()T` to their backing field name `x`. */
    private fun gettersOf(owner: String): Map<String, String> {
        val bytes = readClass(owner) ?: return emptyMap()
        val out = HashMap<String, String>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(access: Int, name: String?, desc: String?, sig: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (name != null && desc != null && name.startsWith("get") && name.length > 3
                        && Type.getArgumentTypes(desc).isEmpty()
                        && Type.getReturnType(desc).sort in Type.BOOLEAN..Type.DOUBLE
                        && access and Opcodes.ACC_PUBLIC != 0) {
                    val field = name[3].lowercaseChar() + name.substring(4)
                    out[name] = field
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        return out
    }

    private fun loadFieldVia(mv: MethodVisitor, slot: Int, owner: String, f: Field) {
        mv.visitVarInsn(Opcodes.ALOAD, slot)
        if (f.getter != null) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, f.getter, "()" + f.type.descriptor, false)
        } else {
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, f.name, f.type.descriptor)
        }
    }

    // --- low-level emit helpers (kept from the prior spike) ------------------------------------------

    private fun newNonNull(mv: MethodVisitor, owner: String, slot: Int) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, CPROVER, "nondetWithoutNull",
                "()Ljava/lang/Object;", false)
        mv.visitTypeInsn(Opcodes.CHECKCAST, owner)
        mv.visitVarInsn(Opcodes.ASTORE, slot)
        mv.visitVarInsn(Opcodes.ALOAD, slot)
        val nn = Label()
        val done = Label()
        mv.visitJumpInsn(Opcodes.IFNULL, nn)
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitJumpInsn(Opcodes.GOTO, done)
        mv.visitLabel(nn)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitLabel(done)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, "assume", "(Z)V", false)
    }

    /** Top-of-stack holds two values of primitive [t]; emit `assume(a == b)`. */
    private fun cmpEqAssume(mv: MethodVisitor, t: Type) = cmpEq(mv, t, "assume")

    private fun cmpEqCheck(mv: MethodVisitor, t: Type) = cmpEq(mv, t, "check")

    private fun cmpEq(mv: MethodVisitor, t: Type, op: String) {
        val ne = Label()
        val done = Label()
        when (t.sort) {
            Type.LONG -> { mv.visitInsn(Opcodes.LCMP); mv.visitJumpInsn(Opcodes.IFNE, ne) }
            Type.FLOAT -> { mv.visitInsn(Opcodes.FCMPL); mv.visitJumpInsn(Opcodes.IFNE, ne) }
            Type.DOUBLE -> { mv.visitInsn(Opcodes.DCMPL); mv.visitJumpInsn(Opcodes.IFNE, ne) }
            else -> mv.visitJumpInsn(Opcodes.IF_ICMPNE, ne)
        }
        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitJumpInsn(Opcodes.GOTO, done)
        mv.visitLabel(ne)
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitLabel(done)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC, op, "(Z)V", false)
    }

    private fun isInstancePred(h: Handle): Boolean =
            h.tag == Opcodes.H_INVOKEVIRTUAL || h.tag == Opcodes.H_INVOKEINTERFACE

    private fun pushPredicateReceiver(mv: MethodVisitor, h: Handle) {
        if (isInstancePred(h)) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, h.owner, "INSTANCE", "L${h.owner};")
        }
    }

    private fun callPredicate(mv: MethodVisitor, h: Handle) {
        val op = if (isInstancePred(h)) {
            if (h.tag == Opcodes.H_INVOKEINTERFACE) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        } else {
            Opcodes.INVOKESTATIC
        }
        mv.visitMethodInsn(op, h.owner, h.name, h.desc, h.tag == Opcodes.H_INVOKEINTERFACE)
    }

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

    private fun coerce(mv: MethodVisitor, src: Type, dst: Type) {
        if (src.descriptor == dst.descriptor) return
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
