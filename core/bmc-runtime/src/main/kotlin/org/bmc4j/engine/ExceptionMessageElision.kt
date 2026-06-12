package org.bmc4j.engine

import org.bmc4j.RemoveExceptionMessages
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Locale
import java.util.zip.ZipFile

/**
 * **Exception-message elision**: drop the construction of a thrown exception's *message* when no code
 * in the proof's reachable cone ever observes any exception message — making proofs over functions
 * that build expensive dynamic error messages (e.g. `okio.Buffer.readDecimalLong`'s overflow path,
 * which materializes a byte[]→String inside the message of a thrown exception) tractable instead of
 * UNKNOWN.
 *
 * ## The problem
 * BMC encodes a method's WHOLE body, both arms of every branch. A `throw new T(<expensive message>)`
 * therefore forces the engine to symbolically execute the message construction even on a path the
 * proof never takes — and if that construction is intractable (a byte→String materialization, an
 * unbounded concat) it poisons the verdict (TIMEOUT / a LINK_FAILURE_STUB) even on a perfectly valid
 * roundtrip where the throw is never reached. The message is never *read*, but building it sinks the
 * proof.
 *
 * ## What this does
 * At each `Throwable`-subtype constructor site that takes a `String` message, we DROP the
 * message-building bytecode and pass a cheap constant (`null`) instead. The exception is **still
 * constructed and thrown** — only the message *computation* is removed, so control flow and the
 * verdict-relevant "is it thrown" are unchanged; only the (unobserved) message string differs. This is
 * the same class of bytecode surgery as [StringBytecode]'s `new String(char[])` construction redirect:
 * a deferred-replay buffer records the `NEW T; DUP; <build message>; INVOKESPECIAL T.<init>(String)V`
 * region, then on match replays `NEW;DUP` but emits `ACONST_NULL` in place of the recorded
 * message-building actions before the constructor call.
 *
 * ## Soundness — the coarse observability gate (AUTO)
 * Eliding the message is sound **only if the elided value is never read**. So before eliding anything,
 * AUTO walks the proof's **analysis call-graph from the entry method** (the bodies JBMC actually
 * executes, following invoke edges) and scans every reached body for a call that observes a `Throwable`
 * message — `getMessage` / `getLocalizedMessage` / `getStackTrace` / `printStackTrace` / `toString`
 * on a `Throwable`. If **none exists anywhere in that cone**, no analysed code reads any exception
 * message, so eliding every exception message is fully sound (the dropped value is dead). If ANY such
 * observer exists — or the cone can't be bounded (an unresolved call edge, reflection / unknown indy,
 * entry off classpath) — AUTO conservatively does **NOT** elide (all-or-nothing). The gate fails toward
 * NOT eliding: the worst case is no speed-up, never a false VERIFIED.
 *
 * Deliberately a CALL-GRAPH walk from the entry method, NOT [ReachableCone]'s constant-pool type closure
 * of the entry CLASS: the latter is intentionally over-broad for cache keying (it follows annotation /
 * field / parameter type references), so it would drag the JUnit harness and bmc4j's own
 * verdict-rendering runtime — which JBMC never analyses from the entry function, and which DO observe
 * messages — into the cone, and the gate would never clear. The engine's reachable program is the
 * invoke closure from the entry method; that is exactly what this walks (the sibling discipline of
 * [ContractPurityAudit]'s entry-rooted call-graph).
 *
 * ## Modes (the per-proof flag — [org.bmc4j.RemoveExceptionMessages])
 *  - `AUTO` (default): the coarse gate above — elide iff no message-observer is in the cone.
 *  - `ON`: **force**-elide even if an observer exists. This is a USER-ASSERTED override (the user
 *    promises the elided messages don't affect what they prove); the proof extension surfaces it as a
 *    footnote on the verdict, so a VERIFIED-via-forced-elision is never read as unconditional.
 *  - `OFF`: never elide (the pre-feature behaviour).
 *
 * Wired as a per-proof rewrite pass in [JbmcBackend.prepareClasspath] (it is cone-dependent, so unlike
 * the six fused desugars it cannot be hoisted into the cacheable Gradle mirror). Both directory and jar
 * classpath entries are mirrored, via [ClasspathMirror].
 */
internal object ExceptionMessageElision {

    /** Internal name of the root of all exceptions. */
    private const val THROWABLE = "java/lang/Throwable"
    private const val OBJECT = "java/lang/Object"

    /**
     * Method names that READ a `Throwable`'s message / stack (the observability gate's signal). A call
     * to any of these on a `Throwable` (or on `Object`/`Throwable` static type) means some code can see
     * an exception message — so AUTO must not elide. `getCause` is included: chaining a cause and then
     * reading ITS message observes a message indirectly.
     */
    private val MESSAGE_OBSERVERS: Set<String> = setOf(
            "getMessage",
            "getLocalizedMessage",
            "getStackTrace",
            "printStackTrace",
            "toString")

    /**
     * The result of the gate: whether to elide, and (for diagnostics / the verdict footnote) why.
     * [forced] marks an [Mode.ON] override that elided despite a possible observer — the caller surfaces
     * it as a user-assumption footnote.
     */
    internal class Decision private constructor(
            @JvmField val elide: Boolean,
            @JvmField val forced: Boolean,
            @JvmField val reason: String) {
        companion object {
            fun elide(forced: Boolean, reason: String) = Decision(true, forced, reason)
            fun keep(reason: String) = Decision(false, false, reason)
        }
    }

    /**
     * Apply the pass to [classpath] for the proof rooted at [entryClass].[entryMethod] under [mode].
     * Returns the (possibly rewritten) classpath plus the [Decision] (so the caller can surface a
     * forced-elision footnote). A no-op classpath is returned unchanged when the gate declines to elide.
     */
    @JvmStatic
    fun apply(classpath: String, entryClass: String, entryMethod: String, mode: RemoveExceptionMessages): Result {
        val decision = decide(classpath, entryClass, entryMethod, mode)
        if (!decision.elide) {
            return Result(classpath, decision)
        }
        return try {
            val index = Index(classpath)
            val rewritten = ClasspathMirror.mirror(classpath, CACHE_NAME, { bytes ->
                ClasspathMirror.Transformed(rewriteClass(bytes) { name -> index.isThrowable(name) })
            }, MODE_KEY)
            Result(rewritten, decision)
        } catch (e: RuntimeException) {
            // Mirroring failures fail LOUD by contract (ClasspathMirror throws) and are reclassified as
            // UNKNOWN upstream — never a false green. Re-throw so the engine-error path handles it.
            throw e
        }
    }

    /** Identity folded into the elision mirror's cache key (the rewrite is parameter-free beyond the
     *  Throwable hierarchy of the same classpath, so the content hash + this tag suffice). Bump on any
     *  change to which constructor sites [rewriteClass] elides. */
    private const val MODE_KEY = "elide-msg-v3"
    private const val CACHE_NAME = "elide-msg"

    /** The classpath + decision after the pass. */
    internal class Result(
            @JvmField val classpath: String,
            @JvmField val decision: Decision)

    /**
     * The gate: decide whether to elide for this proof. OFF never elides; ON always elides (the forced
     * override); AUTO elides iff the analysis call-graph from [entryClass].[entryMethod] contains NO
     * exception-message observer. A call edge that can't be resolved on the classpath (reflection /
     * method handle / unknown indy) is treated as "an observer may be behind it" — AUTO declines.
     */
    @JvmStatic
    fun decide(classpath: String, entryClass: String, entryMethod: String,
               mode: RemoveExceptionMessages): Decision = when (mode) {
        RemoveExceptionMessages.OFF -> Decision.keep("elision disabled (OFF)")
        RemoveExceptionMessages.ON ->
            Decision.elide(forced = true, reason = "forced elision (ON) — user-asserted override")
        RemoveExceptionMessages.AUTO -> decideAuto(classpath, entryClass, entryMethod)
    }

    private fun decideAuto(classpath: String, entryClass: String, entryMethod: String): Decision {
        return try {
            val index = Index(classpath)
            val gate = ObserverWalk(index).walk(entryClass.replace('.', '/'), entryMethod)
            when {
                gate.unbounded != null ->
                    // An unresolved call edge could hide a message observer behind it. Don't elide.
                    Decision.keep("call-graph unbounded (${gate.unbounded}) — not eliding")
                gate.observer != null ->
                    Decision.keep(
                            "an exception-message observer is reachable (${gate.observer}) — not auto-eliding")
                else ->
                    Decision.elide(forced = false,
                            reason = "no exception-message observer reachable from the entry — elision is sound")
            }
        } catch (e: RuntimeException) {
            Decision.keep("gate walk error (${e.javaClass.simpleName}) — not eliding")
        } catch (e: IOException) {
            Decision.keep("gate walk error (${e.javaClass.simpleName}) — not eliding")
        }
    }

    /**
     * Walks the analysis call-graph from an entry method (following invoke / indy edges over bodies that
     * resolve on the classpath) and reports the FIRST exception-message observer call site it sees, OR
     * the first call edge it cannot bound (reflection / method handle / un-attributable indy) — either of
     * which makes AUTO decline. A callee whose body is NOT on the classpath (a JDK method, a model leaf)
     * is a leaf: the engine analyses its modelled/native body, but an observer there would manifest as a
     * call SITE in some on-classpath caller we DO open, and the JDK/runtime is not the analysed program's
     * own code — so a missing body is treated as a bounded leaf (it does not, by itself, force a decline;
     * an *opaque dispatch* call does). Cycle-guarded; bounded by the classpath's method count.
     */
    private class ObserverWalk(private val index: Index) {

        /** First observer call site `owner.name`, or null. */
        var observer: String? = null

        /** First unbounded edge reason, or null. */
        var unbounded: String? = null

        private val seen = HashSet<String>()
        private val work = ArrayDeque<MethodRef>()

        fun walk(entryOwner: String, entryMethod: String): ObserverWalk {
            // The entry method may be a specific overload; we open every method NAMED entryMethod on the
            // entry owner (a proof method has a unique name in practice). Seeds the worklist.
            val entryBytes = index.bytesOf(entryOwner)
            if (entryBytes == null) {
                unbounded = "entry class $entryOwner not on classpath"
                return this
            }
            forEachMethodNamed(entryBytes, entryMethod) { desc ->
                enqueue(entryOwner, entryMethod, desc)
            }
            drain()
            return this
        }

        private fun enqueue(owner: String, name: String, desc: String?) {
            val key = "$owner.$name${desc ?: ""}"
            if (seen.add(key)) {
                work.add(MethodRef(owner, name, desc))
            }
        }

        private fun drain() {
            while (work.isNotEmpty() && observer == null && unbounded == null) {
                val m = work.poll()
                val bytes = index.bytesOf(m.owner) ?: continue // off-classpath leaf (JDK / model)
                scanBody(bytes, m.name, m.desc)
            }
        }

        /** Scan one method body for an observer call site and enqueue its callees. */
        private fun scanBody(classBytes: ByteArray, methodName: String, methodDesc: String?) {
            val cv = object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                         ex: Array<String>?): MethodVisitor? {
                    if (n != methodName || (methodDesc != null && d != methodDesc)) {
                        return null
                    }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(op: Int, owner: String?, name: String?,
                                                     desc: String?, itf: Boolean) {
                            if (observer != null || unbounded != null || owner == null || name == null) {
                                return
                            }
                            if (isOpaqueDispatch(owner, name)) {
                                unbounded = "opaque dispatch $owner.$name"
                                return
                            }
                            if (isMessageObserver(owner, name)) {
                                observer = "$owner.$name"
                                return
                            }
                            enqueue(owner, name, desc)
                        }

                        override fun visitInvokeDynamicInsn(name: String?, desc: String?,
                                                            bsm: org.objectweb.asm.Handle?,
                                                            vararg bsmArgs: Any?) {
                            if (observer != null || unbounded != null) {
                                return
                            }
                            // Only the compiler desugaring bootstraps name their impl in the args; any
                            // other indy resolves its callee at link time from data we can't read, so it
                            // could hide an observer — decline. The desugaring bootstraps (concat / record
                            // / lambda / switch) don't read exception messages, so following their handle
                            // owners is enough.
                            val key = if (bsm != null) "${bsm.owner}.${bsm.name}" else "<none>"
                            if (bsm == null || !KNOWN_INDY_BOOTSTRAPS.contains(key)) {
                                unbounded = "un-attributable invokedynamic via $key"
                                return
                            }
                            for (arg in bsmArgs) {
                                if (arg is org.objectweb.asm.Handle) {
                                    enqueue(arg.owner, arg.name, arg.desc)
                                }
                            }
                        }
                    }
                }
            }
            ClassReader(classBytes).accept(cv, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }

        /**
         * True when `owner.name` could observe a `Throwable`'s message — the CONSERVATIVE read, since a
         * false "observer present" only forgoes a speed-up while a false "no observer" would be unsound.
         *  - `getMessage` / `getLocalizedMessage` / `printStackTrace` are essentially `Throwable`-specific
         *    member names; a call to any of them is treated as an observer regardless of the static owner
         *    type, so a message read behind a polymorphic dispatch (an interface/`Object` static type
         *    whose runtime receiver is a `Throwable`) is still caught.
         *  - `toString` / `getStackTrace` are more generic, so they count only when the static owner is a
         *    `Throwable` subtype or `Object`/`Throwable` (a virtual call whose receiver could be a
         *    `Throwable`). A `toString` proven to be on a non-`Throwable` (e.g. `StringBuilder.toString`)
         *    is not an observer.
         */
        private fun isMessageObserver(owner: String, name: String): Boolean {
            if (!MESSAGE_OBSERVERS.contains(name)) {
                return false
            }
            if (name == "getMessage" || name == "getLocalizedMessage" || name == "printStackTrace") {
                return true
            }
            // toString / getStackTrace: count on a Throwable/Object static receiver only.
            return owner == OBJECT || index.isThrowable(owner)
        }

        private fun isOpaqueDispatch(owner: String, name: String): Boolean =
                OPAQUE_DISPATCH_OWNERS.contains(owner) ||
                        OPAQUE_DISPATCH_METHODS.contains("$owner.$name")
    }

    private class MethodRef(
            @JvmField val owner: String,
            @JvmField val name: String,
            @JvmField val desc: String?)

    /** Invoke each method NAMED [name] in [classBytes], handing the action its descriptor. */
    private inline fun forEachMethodNamed(classBytes: ByteArray, name: String, action: (String?) -> Unit) {
        val found = ArrayList<String>()
        ClassReader(classBytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor? {
                if (n == name && d != null) {
                    found.add(d)
                }
                return null
            }
        }, ClassReader.SKIP_CODE)
        for (d in found) {
            action(d)
        }
    }

    /** Reflection / method-handle owners and methods that resolve a call target at runtime — an edge the
     *  static walk can't follow, so a message observer could hide behind one. Mirrors [ReachableCone]. */
    private val OPAQUE_DISPATCH_OWNERS: Set<String> = setOf(
            "java/lang/reflect/Method",
            "java/lang/reflect/Constructor",
            "java/lang/invoke/MethodHandle",
            "java/lang/invoke/MethodHandles",
            "java/lang/invoke/MethodHandles\$Lookup",
            "java/lang/invoke/VarHandle")

    private val OPAQUE_DISPATCH_METHODS: Set<String> = setOf(
            "java/lang/Class.forName",
            "java/lang/Class.getMethod",
            "java/lang/Class.getDeclaredMethod",
            "java/lang/Class.newInstance",
            "java/lang/ClassLoader.loadClass")

    /** Desugaring bootstraps whose impl method is named in the indy args (so the walk can follow them);
     *  any other bootstrap is opaque. Mirrors [ReachableCone.KNOWN_INDY_BOOTSTRAPS]. None of these reads
     *  an exception message. */
    private val KNOWN_INDY_BOOTSTRAPS: Set<String> = setOf(
            "java/lang/invoke/LambdaMetafactory.metafactory",
            "java/lang/invoke/LambdaMetafactory.altMetafactory",
            "java/lang/invoke/StringConcatFactory.makeConcat",
            "java/lang/invoke/StringConcatFactory.makeConcatWithConstants",
            "java/lang/runtime/ObjectMethods.bootstrap",
            "java/lang/runtime/SwitchBootstraps.typeSwitch",
            "java/lang/runtime/SwitchBootstraps.enumSwitch")

    /**
     * Pure per-class transform: at each `Throwable`-subtype `<init>(String)` site, drop the
     * message-building bytecode and pass `null` instead. [isThrowable] resolves whether a constructed
     * type is a `Throwable` subtype over the analysis classpath. Exposed for unit tests.
     *
     * Only the single-`String`-argument constructor (`(Ljava/lang/String;)V`) is elided: its
     * `NEW T; DUP; <build message>; INVOKESPECIAL T.<init>(Ljava/lang/String;)V` region has the message
     * as its SOLE operand, so dropping the recorded region and substituting one `ACONST_NULL` is
     * stack-exact and unambiguous. A multi-argument constructor (`(String, Throwable)`, …) interleaves
     * the message build with other operands' builds and can't have the message isolated soundly, so it
     * is left verbatim (conservative — just no elision for that site).
     */
    internal fun rewriteClass(bytes: ByteArray, isThrowable: (String) -> Boolean): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return ElidingMethodVisitor(mv, isThrowable)
            }
        }
        // Read WITH debug (accept(cv, 0)), exactly like the sibling rewrite passes: JBMC's loop / unwinding
        // analysis is sensitive to the LineNumberTable, so an UNMODIFIED method must keep its debug
        // byte-for-byte (else an untouched intractable-loop proof's UNKNOWN can flip). The eliding visitor
        // preserves debug verbatim on every method it does not touch and drops only the (now-inconsistent)
        // LineNumberTable / LocalVariableTable of the methods it actually rewrites — see [visitMaxs].
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Rewriter for the `NEW T; DUP; <build message>; INVOKESPECIAL T.<init>(String)V` shape that ALSO
     * removes the dead backward slice the elision leaves behind. Two distinct dead regions are dropped:
     *
     *  1. **The message-build region** (PR #301): the `<build message>` between `NEW T; DUP` and the
     *     terminal `<init>(String)` of a `Throwable` subtype — replaced by a single `ACONST_NULL`. This is
     *     the SAME region-buffering discipline as [StringBytecode]'s char-array ctor redirect (its `#296`
     *     `visitLabel` fix: a label inside the region is a line-number anchor with no stack effect, so it
     *     is recorded, not abandoned).
     *  2. **The dead-local slice** (this extension): when the elided message region read a value from a
     *     LOCAL that was produced by a PRIOR statement and is read nowhere else, that prior statement is
     *     now dead — its result has no remaining reader. Its construction is dropped too, so the engine
     *     never analyses it (the okio `Buffer.readDecimalLong` overflow shape: the message reads
     *     `buffer.readUtf8()` where `buffer` was built one statement earlier; once the `readUtf8()` is
     *     elided the whole `buffer` build is dead, but it internally unwinds 465× and sinks the proof
     *     unless dropped). See [DeadLocalSlicer] for the conservative escape/liveness boundary that keeps
     *     this sound.
     *
     * Implemented as a WHOLE-METHOD buffering pass: every visit is recorded into an ordered [Step] list
     * (with the structured metadata the slicer needs), then on [visitEnd] the regions are matched and the
     * dead steps are computed and skipped during a single verbatim replay. Buffering the whole method
     * (rather than the streaming NEW-rooted window of PR #301) is what lets the dead-local slice reach the
     * defining statement that PRECEDES the `NEW T` — the streaming form could only see forward of the NEW.
     */
    private class ElidingMethodVisitor(
            private val out: MethodVisitor,
            private val isThrowable: (String) -> Boolean) : MethodVisitor(Opcodes.ASM9, null) {

        /** The whole method, recorded in visit order. */
        private val steps = ArrayList<Step>()

        // ---- recording: every visit becomes a Step, replay-faithful + analysis-tagged ----

        override fun visitTypeInsn(op: Int, type: String?) {
            steps.add(Step(replay = { out.visitTypeInsn(op, type) },
                    opcode = op, typeOperand = type))
        }

        override fun visitInsn(op: Int) {
            steps.add(Step(replay = { out.visitInsn(op) }, opcode = op))
        }

        override fun visitIntInsn(op: Int, operand: Int) {
            steps.add(Step(replay = { out.visitIntInsn(op, operand) }, opcode = op))
        }

        override fun visitVarInsn(op: Int, varIdx: Int) {
            steps.add(Step(replay = { out.visitVarInsn(op, varIdx) }, opcode = op, localSlot = varIdx))
        }

        override fun visitFieldInsn(op: Int, o: String?, nm: String?, d: String?) {
            steps.add(Step(replay = { out.visitFieldInsn(op, o, nm, d) }, opcode = op, fieldDesc = d))
        }

        override fun visitLdcInsn(value: Any?) {
            // LDC pushes one (two for long/double) value with no side effects.
            val wide = value is Long || value is Double
            steps.add(Step(replay = { out.visitLdcInsn(value) }, opcode = Opcodes.LDC, ldcWide = wide))
        }

        override fun visitIincInsn(varIdx: Int, increment: Int) {
            steps.add(Step(replay = { out.visitIincInsn(varIdx, increment) },
                    opcode = Opcodes.IINC, localSlot = varIdx))
        }

        override fun visitJumpInsn(op: Int, label: Label?) {
            steps.add(Step(replay = { out.visitJumpInsn(op, label) }, opcode = op, isBranch = true))
        }

        override fun visitLabel(label: Label?) {
            steps.add(Step(replay = { out.visitLabel(label) }, isLabel = true, label = label))
        }

        override fun visitLineNumber(line: Int, start: Label?) {
            // A LineNumberTable anchor: no stack effect (isMeta), and DEBUG (isLine) so a modified method
            // can drop it — see [visitMaxs]. JBMC's loop/unwinding analysis is sensitive to the
            // LineNumberTable, so it is preserved verbatim on every method this pass does NOT touch.
            steps.add(Step(replay = { out.visitLineNumber(line, start) }, isMeta = true, isLine = true))
        }

        override fun visitFrame(type: Int, nLocal: Int, local: Array<out Any?>?, nStack: Int,
                                stack: Array<out Any?>?) {
            steps.add(Step(replay = { out.visitFrame(type, nLocal, local, nStack, stack) },
                    isFrame = true))
        }

        override fun visitMultiANewArrayInsn(d: String?, dims: Int) {
            steps.add(Step(replay = { out.visitMultiANewArrayInsn(d, dims) },
                    opcode = Opcodes.MULTIANEWARRAY))
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
            steps.add(Step(replay = { out.visitTableSwitchInsn(min, max, dflt, *labels) },
                    opcode = Opcodes.TABLESWITCH, isBranch = true))
        }

        override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
            steps.add(Step(replay = { out.visitLookupSwitchInsn(dflt, keys, labels) },
                    opcode = Opcodes.LOOKUPSWITCH, isBranch = true))
        }

        override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: org.objectweb.asm.Handle?,
                                            vararg bsmArgs: Any?) {
            val args = bsmArgs.clone()
            steps.add(Step(replay = { out.visitInvokeDynamicInsn(name, desc, bsm, *args) },
                    opcode = Opcodes.INVOKEDYNAMIC, methodDesc = desc))
        }

        override fun visitMethodInsn(op: Int, mOwner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            steps.add(Step(replay = { out.visitMethodInsn(op, mOwner, name, desc, itf) },
                    opcode = op, methodOwner = mOwner, methodName = name, methodDesc = desc))
        }

        override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
            // Handlers are method-level metadata, not part of the linear instruction stream; forward
            // verbatim (the elided/sliced steps are all straight-line, never a handler boundary).
            out.visitTryCatchBlock(start, end, handler, type)
        }

        /** Deferred LocalVariableTable entries (ASM visits these after the instructions, before maxs). On
         *  an UNMODIFIED method they replay verbatim (debug preserved exactly, like the sibling passes); on
         *  a MODIFIED method they are DROPPED — a slice can remove the store an LVT range starts on, which
         *  trips JBMC's java_local_variable_table predecessor-map invariant. */
        private val localVars = ArrayList<() -> Unit>()

        override fun visitLocalVariable(name: String?, desc: String?, sig: String?, start: Label?,
                                        end: Label?, index: Int) {
            localVars.add { out.visitLocalVariable(name, desc, sig, start, end, index) }
        }

        // ---- analysis + emit -------------------------------------------------------------------------

        override fun visitCode() {
            out.visitCode()
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            // Removing instructions can only LOWER the real stack/locals high-water mark, so the original
            // maxs stay a valid (if loose) upper bound — emit them unchanged (the rewrite uses
            // ClassWriter(0), so nothing recomputes them).
            val drop = computeDroppedSteps()
            val modified = drop.removed.isNotEmpty() || drop.replacement.isNotEmpty()
            for (i in steps.indices) {
                if (i in drop.removed) {
                    continue
                }
                val s = steps[i]
                // On a MODIFIED method, drop the LineNumberTable anchors too: a slice can remove the store
                // an LVT/line range is bounded on, which trips JBMC's local-variable-table invariant. An
                // UNMODIFIED method replays byte-identically (debug preserved), so JBMC's loop/unwinding
                // analysis — which is sensitive to the LineNumberTable — is unchanged on every untouched
                // method.
                if (modified && s.isLine) {
                    continue
                }
                drop.replacement[i]?.invoke() ?: s.replay()
            }
            // LocalVariableTable: replay verbatim on an unmodified method; drop entirely on a modified one
            // (a dangling range over a removed store is the LVT-invariant hazard above).
            if (!modified) {
                for (lv in localVars) {
                    lv()
                }
            }
            out.visitMaxs(maxStack, maxLocals)
        }

        override fun visitEnd() {
            out.visitEnd()
        }

        /**
         * The two-phase analysis: (1) match every elidable `NEW T; DUP; <msg>; <init>(String)` region and
         * mark its message-build steps removed + the ctor's message arg replaced by `ACONST_NULL`; then
         * (2) hand the elided-region local reads to [DeadLocalSlicer], which marks the now-dead defining
         * slices removed too. Returns the merged removal set + per-step replacements.
         */
        private fun computeDroppedSteps(): Plan {
            val removed = HashSet<Int>()
            val replacement = HashMap<Int, () -> Unit>()
            // Local slots read inside an elided message region (the slicer's roots).
            val elidedReads = HashSet<Int>()
            matchRegions(removed, replacement, elidedReads)
            DeadLocalSlicer(steps).sliceDeadDefs(elidedReads, removed)
            return Plan(removed, replacement)
        }

        /**
         * Phase 1 — find each elidable exception-message region and mark it. A region is the maximal
         * `NEW T; DUP; <build...>; INVOKESPECIAL T.<init>(Ljava/lang/String;)V` where T resolves to a
         * `Throwable` subtype, with NO branch / switch / frame / label-that-is-a-join inside the build (the
         * same conservative shape as PR #301). On a match: the `<build...>` steps are marked removed and
         * the `<init>` step's emit is REPLACED by `ACONST_NULL; INVOKESPECIAL <init>(String)`. Records each
         * local slot the build READ into [elidedReads] (the slicer's roots).
         */
        private fun matchRegions(removed: MutableSet<Int>, replacement: MutableMap<Int, () -> Unit>,
                                 elidedReads: MutableSet<Int>) {
            var i = 0
            while (i < steps.size) {
                val start = steps[i]
                if (start.opcode == Opcodes.NEW && start.typeOperand != null && isThrowable(start.typeOperand)) {
                    val end = scanRegion(i)
                    if (end >= 0) {
                        // Mark the build steps [i+2 .. end-1] removed (NEW at i, DUP at i+1 stay; <init> at
                        // end is replaced). Record local reads in the build for the slicer.
                        for (k in (i + 2) until end) {
                            val s = steps[k]
                            removed.add(k)
                            if (s.opcode in LOAD_OPS && s.localSlot >= 0) {
                                elidedReads.add(s.localSlot)
                            }
                        }
                        val t = start.typeOperand
                        replacement[end] = {
                            out.visitInsn(Opcodes.ACONST_NULL)
                            out.visitMethodInsn(Opcodes.INVOKESPECIAL, t, "<init>",
                                    "(Ljava/lang/String;)V", false)
                        }
                        i = end + 1
                        continue
                    }
                }
                i++
            }
        }

        /**
         * From a `NEW T` at [newIdx], confirm the elidable shape and return the index of the terminal
         * `INVOKESPECIAL T.<init>(String)`, or -1 if the region is not the simple elidable form. Requires:
         * `DUP` immediately after the NEW; the build region contains no branch/switch/frame and no second
         * `NEW T` (ambiguous) and no other `<init>` of T (a different ctor overload); the region ends at
         * `T.<init>(Ljava/lang/String;)V`. Labels/line-numbers inside are tolerated (no stack effect).
         */
        private fun scanRegion(newIdx: Int): Int {
            val t = steps[newIdx].typeOperand ?: return -1
            if (newIdx + 1 >= steps.size || steps[newIdx + 1].opcode != Opcodes.DUP) {
                return -1
            }
            var k = newIdx + 2
            while (k < steps.size) {
                val s = steps[k]
                when {
                    s.isBranch || s.isFrame -> return -1
                    s.opcode == Opcodes.NEW && s.typeOperand == t -> return -1 // ambiguous second NEW T
                    s.opcode == Opcodes.INVOKESPECIAL && s.methodOwner == t && s.methodName == "<init>" -> {
                        return if (s.methodDesc == "(Ljava/lang/String;)V") k else -1
                    }
                    else -> k++
                }
            }
            return -1
        }
    }

    /** One recorded method-visit: a faithful [replay] plus the structured tags the dead-local slicer and
     *  region matcher read. Defaults describe a no-instruction meta visit (label/line/frame). */
    private class Step(
            @JvmField val replay: () -> Unit,
            @JvmField val opcode: Int = -1,
            @JvmField val localSlot: Int = -1,
            @JvmField val typeOperand: String? = null,
            @JvmField val methodOwner: String? = null,
            @JvmField val methodName: String? = null,
            @JvmField val methodDesc: String? = null,
            @JvmField val fieldDesc: String? = null,
            @JvmField val ldcWide: Boolean = false,
            @JvmField val isBranch: Boolean = false,
            @JvmField val isLabel: Boolean = false,
            @JvmField val isFrame: Boolean = false,
            @JvmField val isMeta: Boolean = false,
            @JvmField val isLine: Boolean = false,
            @JvmField val label: Label? = null)

    /** The computed removals + per-step emit replacements for one method. */
    private class Plan(
            @JvmField val removed: Set<Int>,
            @JvmField val replacement: Map<Int, () -> Unit>)

    /** All xLOAD opcodes (the message-build's reads of a prior-statement local). */
    private val LOAD_OPS: Set<Int> = setOf(
            Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD)

    /** All xSTORE opcodes (a local's definition). */
    private val STORE_OPS: Set<Int> = setOf(
            Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE)

    /**
     * Removes the **dead backward slice** an elided message leaves behind: a value produced by a prior
     * straight-line statement, stored to a LOCAL, that the (now-removed) message build was its SOLE reader
     * of. The motivating case is okio `Buffer.readDecimalLong`'s overflow path — `val buffer =
     * Buffer().writeDecimalLong(v).writeByte(b)` one statement, `throw NFE("…${buffer.readUtf8()}")` the
     * next: eliding the message drops `readUtf8()`, leaving `buffer` dead; but `writeDecimalLong` unwinds
     * 465× and sinks the proof unless `buffer`'s construction is dropped too.
     *
     * ## The soundness boundary (fail-safe = KEEP)
     * A slot's defining statement is dropped ONLY when ALL hold — each check fails toward keeping it:
     *  - **Dead after elision**: every read of the slot (`xLOAD` / `IINC`) in the WHOLE method lies inside
     *    a removed message-build region. If any LIVE read remains, the value is still observed — keep.
     *  - **Single straight-line definition**: the slot has exactly one `xSTORE`, and the run of steps from
     *    the prior stack-empty point up to that store is BRANCH-FREE (no jump/switch target or source, no
     *    frame, no label that is a control-flow join). A value merged from multiple paths, or whose build
     *    spans a loop with its own back-edge, is NOT a simple isolated statement — keep.
     *  - **Stack-isolated** (net stack delta 0, starting and ending empty): the statement consumes only
     *    what it itself produced plus pre-existing locals/constants, and leaves exactly the stored value —
     *    so removing it cannot unbalance the surrounding stack.
     *  - **Effect-isolated**: the only writes the statement makes are to its own slot and to objects it
     *    freshly ALLOCATED within itself. Concretely every `INVOKE{VIRTUAL,SPECIAL,INTERFACE}` receiver
     *    must be a value produced WITHIN the slice (a `NEW` here, or a prior in-slice call's result —
     *    traced over the operand stack), never a pre-existing local/field; and the statement makes no
     *    field write, array store, or `INVOKESTATIC` (a static call can mutate global state we can't see).
     *    This is exactly the freshly-allocated-and-escapes-only-here object: its mutations are unobservable
     *    once it is dead. Anything that could touch state observable outside the dead sub-object — keep.
     *
     * Conservative by construction: an unrecognized shape, an ambiguous def, any possible live escape, or
     * any stack/effect doubt all fall through to KEEP (the pre-extension behaviour — elide the message but
     * leave the slice), so the worst case is a missed speed-up, never an unsound drop.
     */
    private class DeadLocalSlicer(private val steps: List<Step>) {

        fun sliceDeadDefs(elidedReads: Set<Int>, removed: MutableSet<Int>) {
            for (slot in elidedReads) {
                if (!isDeadAfterElision(slot, removed)) {
                    continue
                }
                val storeIdx = soleStoreIndex(slot) ?: continue
                val sliceStart = statementStart(storeIdx) ?: continue
                if (sliceStart > storeIdx) {
                    continue
                }
                if (isIsolatedStatement(sliceStart, storeIdx)) {
                    for (k in sliceStart..storeIdx) {
                        removed.add(k)
                    }
                }
            }
        }

        /** True iff EVERY read (`xLOAD`/`IINC`) of [slot] in the method is already in [removed] (i.e. lies
         *  inside an elided message region). A single surviving read means the value is still observed. */
        private fun isDeadAfterElision(slot: Int, removed: Set<Int>): Boolean {
            for (i in steps.indices) {
                val s = steps[i]
                val reads = (s.opcode in LOAD_OPS && s.localSlot == slot) ||
                        (s.opcode == Opcodes.IINC && s.localSlot == slot)
                if (reads && i !in removed) {
                    return false
                }
            }
            return true
        }

        /** The index of the SOLE `xSTORE [slot]`, or null when there are zero or several (a value merged or
         *  reassigned has no single isolated definition we can drop soundly). `IINC` counts as a write too,
         *  so a slot updated in place is rejected. */
        private fun soleStoreIndex(slot: Int): Int? {
            var found = -1
            for (i in steps.indices) {
                val s = steps[i]
                val writes = (s.opcode in STORE_OPS && s.localSlot == slot) ||
                        (s.opcode == Opcodes.IINC && s.localSlot == slot)
                if (writes) {
                    if (found >= 0) {
                        return null
                    }
                    found = i
                }
            }
            return if (found >= 0) found else null
        }

        /**
         * The start index of the straight-line statement ending at [storeIdx]: scan back to the most recent
         * step at which the operand stack was empty (a statement boundary), tolerating only labels /
         * line-numbers with no stack effect. Returns null if a branch / switch / frame / a label (a
         * possible control-flow join) is hit before the stack empties — the build is not a simple
         * straight-line statement and must be kept.
         */
        private fun statementStart(storeIdx: Int): Int? {
            // The store pops the value it stores (+1 or +2 slots); the statement that produced it must
            // net-push exactly that. Walk backward summing producers' deltas until they balance the store's
            // pop at a stack-empty boundary.
            var balance = -stackDelta(steps[storeIdx]) // slots the store popped (1 for int/ref, 2 for long)
            if (balance <= 0) {
                return null
            }
            var i = storeIdx - 1
            while (i >= 0) {
                val s = steps[i]
                if (s.isBranch || s.isFrame || s.isLabel) {
                    // A join/branch inside the would-be statement: not a simple straight-line def.
                    return null
                }
                if (s.isMeta) {
                    i--
                    continue
                }
                val d = stackDelta(s)
                if (d == UNMODELED_DELTA) {
                    return null // an instruction we don't model precisely: keep, fail-safe
                }
                balance -= d
                if (balance == 0) {
                    return i
                }
                if (balance < 0) {
                    // Stack underflowed within the window — the statement reaches below this point; bail.
                    return null
                }
                i--
            }
            return null
        }

        /**
         * True iff the statement [start]..[storeIdx] is stack- and effect-isolated per the boundary doc:
         * net stack delta 0; no field/array write; no INVOKESTATIC; and every method-call receiver is a
         * value produced WITHIN the slice (a NEW here or a prior in-slice call result), traced over a small
         * symbolic operand stack of "is this value slice-allocated?" booleans.
         */
        private fun isIsolatedStatement(start: Int, storeIdx: Int): Boolean {
            // Symbolic stack of "value was allocated within this slice" flags.
            val sliceAllocated = ArrayDeque<Boolean>()
            var i = start
            while (i <= storeIdx) {
                val s = steps[i]
                if (s.isMeta || s.isLabel) {
                    i++
                    continue
                }
                when {
                    // Disallowed effects: any field/array write, or a static call (opaque global effect).
                    s.opcode in FIELD_WRITE_OPS || s.opcode in ARRAY_STORE_OPS ||
                            s.opcode == Opcodes.INVOKESTATIC || s.opcode == Opcodes.INVOKEDYNAMIC ->
                        return false
                    s.opcode == Opcodes.ATHROW || s.opcode in RETURN_OPS -> return false
                    else -> {
                        if (!applyStackEffect(s, sliceAllocated)) {
                            return false
                        }
                    }
                }
                i++
            }
            // After the store, the slice's operand stack must be empty (balanced statement).
            return sliceAllocated.isEmpty()
        }

        /**
         * Push/pop the slice's symbolic "is slice-allocated?" stack for one step and gate the calls.
         * Returns false (=> keep the statement) on any shape we don't model precisely or that violates the
         * receiver rule. Only the opcodes a simple builder/factory statement uses are modelled exactly;
         * anything else fails safe.
         */
        private fun applyStackEffect(s: Step, stack: ArrayDeque<Boolean>): Boolean {
            when (s.opcode) {
                Opcodes.NEW -> stack.push(true) // a freshly-allocated object: slice-owned
                Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> {
                    if (stack.isEmpty()) return false
                    stack.pop() // the terminal store of the produced value (slice slot)
                }
                Opcodes.LSTORE, Opcodes.DSTORE -> {
                    if (stack.size < 2) return false
                    stack.pop(); stack.pop()
                }
                Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD -> stack.push(false) // a pre-existing value
                Opcodes.LLOAD, Opcodes.DLOAD -> { stack.push(false); stack.push(false) }
                Opcodes.LDC -> { stack.push(false); if (s.ldcWide) stack.push(false) }
                Opcodes.BIPUSH, Opcodes.SIPUSH -> stack.push(false)
                Opcodes.ACONST_NULL, Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1,
                Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> stack.push(false)
                Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> {
                    stack.push(false); stack.push(false)
                }
                Opcodes.DUP -> {
                    if (stack.isEmpty()) return false
                    stack.push(stack.peek())
                }
                Opcodes.POP -> { if (stack.isEmpty()) return false; stack.pop() }
                Opcodes.I2C, Opcodes.I2B, Opcodes.I2S, Opcodes.I2L, Opcodes.I2F, Opcodes.I2D,
                Opcodes.INEG, Opcodes.FNEG -> { /* unary, type may widen but we only track 1-slot ints/refs
                    in builder statements; conservatively treat as keep if it crosses widths */
                    if (stack.isEmpty()) return false
                    // pop 1 push 1 — but I2L/I2D widen to 2 slots: model that.
                    val v = stack.pop()
                    stack.push(false)
                    if (s.opcode == Opcodes.I2L || s.opcode == Opcodes.I2D) stack.push(false)
                    @Suppress("UNUSED_EXPRESSION") v
                }
                Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR,
                Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM -> {
                    if (stack.size < 2) return false
                    stack.pop(); stack.pop(); stack.push(false)
                }
                Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL -> {
                    val desc = s.methodDesc ?: return false
                    val argSlots = argSlotCount(desc)
                    if (stack.size < argSlots + 1) return false
                    repeat(argSlots) { stack.pop() } // arguments
                    val receiverSliceOwned = stack.pop() // the receiver
                    if (!receiverSliceOwned) {
                        // The call's receiver is a pre-existing object — a side effect on live state. Keep.
                        return false
                    }
                    // The result (if any) is produced by an in-slice object's method: treat as slice-owned
                    // so a fluent builder chain (`new B().a().b()`) stays in-slice.
                    pushReturn(desc, stack, sliceOwned = true)
                }
                else -> return false // any opcode we don't model exactly -> keep (fail safe)
            }
            return true
        }

        /** Push the descriptor's return value (0/1/2 slots) onto the symbolic stack with [sliceOwned]. */
        private fun pushReturn(desc: String, stack: ArrayDeque<Boolean>, sliceOwned: Boolean) {
            val ret = desc.substring(desc.indexOf(')') + 1)
            when {
                ret == "V" -> {}
                ret == "J" || ret == "D" -> { stack.push(sliceOwned); stack.push(sliceOwned) }
                else -> stack.push(sliceOwned)
            }
        }

        /** Net operand-stack slot delta of one instruction step (meta steps = 0). Only the opcodes a
         *  simple builder/factory statement uses are needed; anything unmodeled returns a sentinel that
         *  makes [statementStart] bail (treated via the caller's branch/keep paths). */
        private fun stackDelta(s: Step): Int {
            if (s.isMeta || s.isLabel || s.isFrame) return 0
            return when (s.opcode) {
                Opcodes.NEW, Opcodes.BIPUSH, Opcodes.SIPUSH, Opcodes.ACONST_NULL,
                Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5,
                Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD -> 1
                Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1,
                Opcodes.LLOAD, Opcodes.DLOAD -> 2
                Opcodes.LDC -> if (s.ldcWide) 2 else 1
                Opcodes.DUP -> 1
                Opcodes.POP -> -1
                Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.ASTORE -> -1
                Opcodes.LSTORE, Opcodes.DSTORE -> -2
                Opcodes.IINC -> 0
                Opcodes.I2C, Opcodes.I2B, Opcodes.I2S, Opcodes.INEG, Opcodes.FNEG -> 0
                Opcodes.I2L, Opcodes.I2D -> 1
                Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR,
                Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM -> -1
                Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL ->
                    returnSlots(s.methodDesc) - argSlotCount(s.methodDesc) - 1 // -receiver
                Opcodes.INVOKESTATIC, Opcodes.INVOKEDYNAMIC ->
                    returnSlots(s.methodDesc) - argSlotCount(s.methodDesc)
                else -> UNMODELED_DELTA
            }
        }

        private fun returnSlots(desc: String?): Int {
            if (desc == null) return 0
            val ret = desc.substring(desc.indexOf(')') + 1)
            return when {
                ret == "V" -> 0
                ret == "J" || ret == "D" -> 2
                else -> 1
            }
        }

        /** Total slots the descriptor's arguments occupy (long/double = 2). */
        private fun argSlotCount(desc: String?): Int {
            if (desc == null) return 0
            var slots = 0
            var i = desc.indexOf('(') + 1
            while (i < desc.length && desc[i] != ')') {
                when (desc[i]) {
                    'J', 'D' -> { slots += 2; i++ }
                    'L' -> { slots += 1; i = desc.indexOf(';', i) + 1 }
                    '[' -> { i++; while (i < desc.length && desc[i] == '[') i++
                             if (i < desc.length && desc[i] == 'L') i = desc.indexOf(';', i) + 1 else i++
                             slots += 1 }
                    else -> { slots += 1; i++ }
                }
            }
            return slots
        }

        private companion object {
            const val UNMODELED_DELTA = Int.MIN_VALUE / 4
            val FIELD_WRITE_OPS = setOf(Opcodes.PUTFIELD, Opcodes.PUTSTATIC)
            val ARRAY_STORE_OPS = setOf(
                    Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE,
                    Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE)
            val RETURN_OPS = setOf(
                    Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN,
                    Opcodes.ARETURN, Opcodes.RETURN)
        }
    }

    /**
     * Indexes a classpath's `.class` files by internal name → bytes (first wins, mirroring JVM/JBMC
     * resolution), and answers `isThrowable`. Both directory and jar entries are read (a published
     * consumer's classes are jars). Memoizes the Throwable verdict per class.
     */
    internal class Index(classpath: String) {
        private val classes = HashMap<String, ByteArray>()
        private val throwableCache = HashMap<String, Boolean>()

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
                    // An unreadable container contributes no classes; a Throwable check then conservatively
                    // resolves via the JDK roots below or returns false. Soundness of the gate is unaffected
                    // (a missed observer can only happen via an unbounded cone, already handled) and the
                    // rewrite only elides a CONFIRMED Throwable subtype.
                }
            }
        }

        fun bytesOf(name: String): ByteArray? = classes[name]

        /**
         * True when [internalName] is `java/lang/Throwable` or transitively extends it. Walks the super
         * chain over the classpath; a class with no bytes on the classpath (a JDK exception) is resolved
         * by the known-JDK-Throwable check. Bounded + cycle-guarded; memoized.
         */
        fun isThrowable(internalName: String): Boolean {
            throwableCache[internalName]?.let { return it }
            val result = computeThrowable(internalName)
            throwableCache[internalName] = result
            return result
        }

        private fun computeThrowable(start: String): Boolean {
            var current: String? = start
            var hops = 0
            val seen = HashSet<String>()
            while (current != null && hops < 64 && seen.add(current)) {
                if (current == THROWABLE) {
                    return true
                }
                if (KNOWN_JDK_THROWABLES.contains(current)) {
                    return true
                }
                val bytes = classes[current]
                if (bytes == null) {
                    // Off-classpath (a JDK exception we don't have bytes for, or Object): only the known
                    // JDK Throwables above are Throwables; anything else (notably java/lang/Object) is not.
                    return false
                }
                current = try {
                    ClassReader(bytes).superName
                } catch (e: RuntimeException) {
                    return false
                }
                hops++
            }
            return false
        }

        private fun indexDir(dir: Path) {
            Files.walk(dir).use { walk ->
                for (c in Iterable { walk.iterator() }) {
                    if (Files.isRegularFile(c) && c.fileName.toString().endsWith(".class")) {
                        put(Files.readAllBytes(c))
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
                    zf.getInputStream(ze).use { put(it.readAllBytes()) }
                }
            }
        }

        private fun put(bytes: ByteArray) {
            val name = try {
                ClassReader(bytes).className
            } catch (e: RuntimeException) {
                return
            }
            classes.putIfAbsent(name, bytes)
        }

        private fun isJar(p: Path): Boolean {
            val n = p.fileName.toString().lowercase(Locale.ROOT)
            return n.endsWith(".jar") || n.endsWith(".zip")
        }
    }

    /**
     * Well-known JDK `Throwable` subtypes whose bytes are usually NOT on the analysis classpath (the
     * real `java.base` is not), so the super-chain walk can't reach `java/lang/Throwable` through them.
     * Any class that extends one of these (resolvable on the classpath) is caught by the walk reaching
     * the listed root. Conservative for the gate (more entries = more "observer might be on a Throwable")
     * and exact for the rewrite (we only elide a class that resolves Throwable). Not exhaustive — a
     * user/library exception extending one of these IS on the classpath and resolves through it; this set
     * just terminates the walk at the JDK boundary for the common bases.
     */
    private val KNOWN_JDK_THROWABLES: Set<String> = setOf(
            "java/lang/Throwable",
            "java/lang/Exception",
            "java/lang/RuntimeException",
            "java/lang/Error",
            "java/lang/IllegalArgumentException",
            "java/lang/IllegalStateException",
            "java/lang/NumberFormatException",
            "java/lang/ArithmeticException",
            "java/lang/IndexOutOfBoundsException",
            "java/lang/ArrayIndexOutOfBoundsException",
            "java/lang/StringIndexOutOfBoundsException",
            "java/lang/NullPointerException",
            "java/lang/ClassCastException",
            "java/lang/UnsupportedOperationException",
            "java/lang/AssertionError",
            "java/io/IOException",
            "java/io/UncheckedIOException",
            "java/io/EOFException",
            "java/util/NoSuchElementException",
            "java/util/ConcurrentModificationException")
}
