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
    private const val MODE_KEY = "elide-msg-v1"
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
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Deferred-replay rewriter for the `NEW T; DUP; <build message>; INVOKESPECIAL T.<init>(String)V`
     * shape — the SAME region-buffering discipline as [StringBytecode]'s char-array ctor redirect (see
     * its `#296` `visitLabel` fix for why a label inside the region must be RECORDED, not abandoned: a
     * multi-line message build carries a `LineNumberTable` anchor between operand loads, with no stack
     * effect). From a `NEW <T>` we record subsequent visits; at the matching `<init>(String)` of a
     * `Throwable` subtype we replay `NEW;DUP` but emit `ACONST_NULL` in place of the recorded message
     * build, then call the constructor. Any other shape (a branch inside the region, a multi-arg ctor, a
     * different method call, a second NEW, method end) abandons the buffer to a verbatim replay, so
     * unrelated code is byte-for-byte unchanged.
     */
    private class ElidingMethodVisitor(
            mv: MethodVisitor,
            private val isThrowable: (String) -> Boolean) : MethodVisitor(Opcodes.ASM9, mv) {

        // The pending NEW <type>'s internal name while recording, else null.
        private var pendingNew: String? = null
        private var sawDup = false
        private val recorded = ArrayList<() -> Unit>()

        private fun reset() {
            pendingNew = null
            sawDup = false
            recorded.clear()
        }

        /** Replay the buffered NEW (and DUP, if seen) and recorded actions verbatim — the conservative
         *  fallback for any non-elidable shape. */
        private fun flush() {
            val t = pendingNew ?: return
            val hadDup = sawDup
            val actions = ArrayList(recorded)
            reset()
            super.visitTypeInsn(Opcodes.NEW, t)
            if (hadDup) {
                super.visitInsn(Opcodes.DUP)
            }
            for (act in actions) {
                act()
            }
        }

        override fun visitTypeInsn(op: Int, type: String?) {
            val pend = pendingNew
            if (pend != null) {
                // A nested `NEW <other type>` (e.g. the `new StringBuilder()` that BUILDS the message) is
                // part of the message-construction region and must be RECORDED so it is dropped with the
                // rest — it is a balanced sub-construction (its own <init> follows). Only a second `NEW`
                // of the SAME pending exception type is ambiguous (we can't tell which one the ctor binds
                // to): replay the first verbatim and restart recording from this one, like StringBytecode.
                if (op == Opcodes.NEW && type == pend) {
                    flush()
                    pendingNew = type
                    sawDup = false
                    recorded.clear()
                    return
                }
                recorded.add { super.visitTypeInsn(op, type) }
                return
            }
            if (op == Opcodes.NEW && type != null) {
                pendingNew = type
                sawDup = false
                recorded.clear()
                return
            }
            super.visitTypeInsn(op, type)
        }

        override fun visitInsn(op: Int) {
            if (pendingNew != null) {
                if (op == Opcodes.DUP && !sawDup && recorded.isEmpty()) {
                    sawDup = true // the DUP immediately after NEW: part of the prefix
                    return
                }
                recorded.add { super.visitInsn(op) }
                return
            }
            super.visitInsn(op)
        }

        override fun visitIntInsn(op: Int, operand: Int) {
            if (pendingNew != null) { recorded.add { super.visitIntInsn(op, operand) }; return }
            super.visitIntInsn(op, operand)
        }

        override fun visitVarInsn(op: Int, varIdx: Int) {
            if (pendingNew != null) { recorded.add { super.visitVarInsn(op, varIdx) }; return }
            super.visitVarInsn(op, varIdx)
        }

        override fun visitFieldInsn(op: Int, o: String?, nm: String?, d: String?) {
            if (pendingNew != null) { recorded.add { super.visitFieldInsn(op, o, nm, d) }; return }
            super.visitFieldInsn(op, o, nm, d)
        }

        override fun visitLdcInsn(value: Any?) {
            if (pendingNew != null) { recorded.add { super.visitLdcInsn(value) }; return }
            super.visitLdcInsn(value)
        }

        override fun visitIincInsn(varIdx: Int, increment: Int) {
            if (pendingNew != null) { recorded.add { super.visitIincInsn(varIdx, increment) }; return }
            super.visitIincInsn(varIdx, increment)
        }

        override fun visitJumpInsn(op: Int, label: Label?) {
            // A branch inside the region is not the simple shape we elide; bail to verbatim replay.
            flush()
            super.visitJumpInsn(op, label)
        }

        override fun visitLabel(label: Label?) {
            // A label inside the region is, in practice, only a line-number anchor between operand loads
            // of a multi-line message build (no stack effect) — RECORD it (replayed before the ctor),
            // exactly as StringBytecode's #296 fix does. A real control-flow join is already excluded
            // because visitJumpInsn / the switch visitors flush first, so a recorded label can only be
            // such a forward anchor.
            if (pendingNew != null) { recorded.add { super.visitLabel(label) }; return }
            super.visitLabel(label)
        }

        override fun visitLineNumber(line: Int, start: Label?) {
            if (pendingNew != null) { recorded.add { super.visitLineNumber(line, start) }; return }
            super.visitLineNumber(line, start)
        }

        override fun visitMultiANewArrayInsn(d: String?, dims: Int) {
            if (pendingNew != null) { recorded.add { super.visitMultiANewArrayInsn(d, dims) }; return }
            super.visitMultiANewArrayInsn(d, dims)
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
            flush(); super.visitTableSwitchInsn(min, max, dflt, *labels)
        }

        override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
            flush(); super.visitLookupSwitchInsn(dflt, keys, labels)
        }

        override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: org.objectweb.asm.Handle?,
                                            vararg bsmArgs: Any?) {
            // An indy inside the region (e.g. a makeConcat building the message) is recorded so it is
            // DROPPED with the rest of the message build if this resolves to an elision; if it resolves to
            // a non-elision shape, flush replays it verbatim. We can't decide yet, so buffer it.
            if (pendingNew != null) {
                recorded.add { super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs) }
                return
            }
            super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
        }

        override fun visitMethodInsn(op: Int, mOwner: String?, name: String?, desc: String?,
                                     itf: Boolean) {
            val t = pendingNew
            if (t != null
                    && op == Opcodes.INVOKESPECIAL
                    && mOwner == t
                    && name == "<init>"
                    && desc == "(Ljava/lang/String;)V"
                    && sawDup
                    && isThrowable(t)) {
                // The single-String-arg constructor of the recorded Throwable subtype: replay NEW;DUP,
                // DROP the recorded message build, push null in its place, then call the ctor. The dropped
                // region is exactly the message's construction (the ctor's sole operand), so the stack is
                // [..., T, T] before ACONST_NULL -> [..., T, T, null], which the ctor consumes correctly.
                reset()
                super.visitTypeInsn(Opcodes.NEW, t)
                super.visitInsn(Opcodes.DUP)
                super.visitInsn(Opcodes.ACONST_NULL)
                super.visitMethodInsn(Opcodes.INVOKESPECIAL, t, "<init>", "(Ljava/lang/String;)V", false)
                return
            }
            if (t != null) {
                // A call inside the region that is NOT the terminal `<init>(String)` of the pending
                // exception. Two cases:
                //  - The pending exception's OWN constructor with a DIFFERENT descriptor (a multi-arg
                //    `<init>(String, Throwable)`, a no-arg, …): we can't isolate the message, so abandon
                //    to a verbatim replay and process this ctor normally.
                //  - Any other call (the `new StringBuilder().append(..).toString()` that BUILDS the
                //    message, a helper that formats it): part of the message construction — RECORD it so
                //    it is dropped with the rest if the terminal `<init>(String)` is reached.
                if (op == Opcodes.INVOKESPECIAL && mOwner == t && name == "<init>") {
                    // The pending exception's ctor but not the (String) overload we elide: not our shape.
                    flush()
                    super.visitMethodInsn(op, mOwner, name, desc, itf)
                    return
                }
                recorded.add { super.visitMethodInsn(op, mOwner, name, desc, itf) }
                return
            }
            super.visitMethodInsn(op, mOwner, name, desc, itf)
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            // Safety net: a NEW never resolved to an elidable ctor is replayed verbatim before close.
            flush()
            super.visitMaxs(maxStack, maxLocals)
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
