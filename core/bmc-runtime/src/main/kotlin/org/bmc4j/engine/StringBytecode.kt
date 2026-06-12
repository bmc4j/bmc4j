package org.bmc4j.engine

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.ArrayList

/**
 * Redirects JVM constructs that JBMC models unsoundly to sound stand-ins, by rewriting their
 * sites in the analysed bytecode. Three transforms today:
 *
 * - **String-from-chars construction** — `String.valueOf(char)/(char[])/(char[],int,int)`,
 *   `String.copyValueOf(...)`, `Character.toString(char)` and the `new String(char[])` /
 *   `new String(char[],int,int)` constructors all materialize a String from char data. JBMC links
 *   its native construction path (which lowers to `org.cprover.CProverString.ofCharArray`) to an
 *   UNCONSTRAINED string — the construction analogue of the unsound native `String.equals` — so a
 *   String built from chars otherwise has nondet `length()`/`charAt`. We redirect them to
 *   [BmcStrings.ofChar]/[BmcStrings.ofChars], which rebuild the String via
 *   `StringBuilder.append(char)` + `toString()` (the one construction primitive JBMC models soundly,
 *   the same machinery the concat desugar uses), so the result's `length`/`charAt` agree with the
 *   source chars and compose with the content shims below. The static factory sites retarget in place
 *   (same descriptor); the constructor sites have their `NEW;DUP` prefix dropped and the argument
 *   building replayed before the [BmcStrings.ofChars] call (so no dangling uninitialized String reaches
 *   JVM verification, and the stack is one ref shallower) — see the deferred-replay buffer in
 *   [rewriteClass]. Unrelated `new String(...)` (e.g. `new String(String)`) is left verbatim.
 * - **String content ops** — `String.equals/startsWith/endsWith/contains` →
 *   [BmcStrings] (the receiver becomes the first argument, so the operand stack is
 *   unchanged). JBMC's own `String.equals` is unsound (it can't even prove
 *   `"x".equals("x")`); [BmcStrings] rebuilds these from `length()` +
 *   `charAt` which JBMC *does* model soundly. **Object-typed `equals`**
 *   call sites — `INVOKEVIRTUAL java/lang/Object.equals` and `INVOKEINTERFACE
 *   .../equals`, which the collection models emit for `key.equals(...)` /
 *   `o.equals(...)` (static type `Object`) — are redirected to
 *   [BmcStrings.objEquals] (issue #18), which routes the String/String case
 *   through the sound shim and otherwise delegates to the receiver's real `equals`, so
 *   String-keyed collection lookups become sound without changing any non-String behaviour.
 * - **String concatenation** — the `invokedynamic` produced by `+` / Kotlin
 *   string templates (bootstrap `StringConcatFactory.makeConcat[WithConstants]`) is a
 *   blindspot: JBMC links it to an unconstrained result. We desugar each such site back to the
 *   pre-Java-9 `StringBuilder.append(...).toString()` form — which JBMC's string library
 *   *does* handle soundly — by replacing the `invokedynamic` with an
 *   `invokestatic` to a synthesized per-class helper of the same descriptor.
 *
 * Both directory and jar classpath entries are mirrored (with sites rewritten) via
 * `ClasspathMirror` — a published consumer gets `bmc-models` and third-party libs as
 * jars, and those need the same desugaring as the in-repo class dirs.
 */
object StringBytecode {

    private const val STRING = "java/lang/String"
    private const val OBJECT = "java/lang/Object"
    private const val BMC_STRINGS = "org/bmc4j/engine/BmcStrings"

    /** `"name desc"` of the `Object.equals` call site we redirect to
     *  [BmcStrings.objEquals]. */
    private const val OBJECT_EQUALS = "equals (Ljava/lang/Object;)Z"
    private const val STRING_BUILDER = "java/lang/StringBuilder"
    private const val CONCAT_FACTORY = "java/lang/invoke/StringConcatFactory"
    private const val OBJECT_METHODS = "java/lang/runtime/ObjectMethods"

    // StringConcatFactory recipe tags (see java.lang.invoke.StringConcatFactory). Written as
    // Unicode escapes, not raw control characters, so the source stays plain ASCII (the
    // same hygiene family as the ContractRewriter NUL-byte fix; a raw control char is easy to mangle).
    private const val TAG_ARG = '\u0001'
    private const val TAG_CONST = '\u0002'

    /** `"name desc"` of the String methods we redirect to [BmcStrings]. Each has a
     *  matching `BmcStrings` method taking the receiver as an extra first `String` arg. */
    private val REDIRECTS: Set<String> = setOf(
            "equals (Ljava/lang/Object;)Z",
            "startsWith (Ljava/lang/String;)Z",
            "endsWith (Ljava/lang/String;)Z",
            "contains (Ljava/lang/CharSequence;)Z")

    private const val CHARACTER = "java/lang/Character"

    /**
     * Static `String`/`Character` factories that MATERIALIZE a String from char data, mapped to the
     * sound [BmcStrings] rebuild (`"name desc"` -> BmcStrings method name). JBMC links its native
     * construction path to a nondet string (it routes `String.valueOf(char[])` and the
     * `String(char[])` constructor through `CProverString.ofCharArray`, which comes back
     * unconstrained), so a String built from chars otherwise has nondet `length()`/`charAt`. The
     * BmcStrings rebuild has the SAME descriptor, so the call site is retargeted in place. The
     * `String(char[])` *constructor* (an `INVOKESPECIAL` with a NEW/DUP prefix) needs extra stack
     * surgery and is handled separately below.
     */
    private val STRING_VALUEOF_REDIRECTS: Map<String, String> = mapOf(
            "valueOf (C)Ljava/lang/String;" to "ofChar",
            "valueOf ([C)Ljava/lang/String;" to "ofChars",
            "valueOf ([CII)Ljava/lang/String;" to "ofChars",
            "copyValueOf ([C)Ljava/lang/String;" to "ofChars",
            "copyValueOf ([CII)Ljava/lang/String;" to "ofChars")

    /** `Character.toString(char)` -> [BmcStrings.ofChar] (same single-char materialization). */
    private const val CHARACTER_TOSTRING = "toString (C)Ljava/lang/String;"

    /** `String(char[])` / `String(char[],int,int)` constructor descriptors, mapped to the
     *  [BmcStrings] factory that materializes the same content soundly. */
    private val STRING_CTOR_REDIRECTS: Map<String, String> = mapOf(
            "([C)V" to "([C)Ljava/lang/String;",
            "([CII)V" to "([CII)Ljava/lang/String;")

    /**
     * `String(byte[], ...)` charset-decode constructor descriptors, mapped to the [BmcStrings.ofBytes]
     * factory of the SAME parameter shape (`(P...)V` -> `(P...)Ljava/lang/String;`). JBMC links native
     * byte[] decode to a nondet string (a charset-decoding library's `bytes -> String` accessor boils
     * down to exactly `new String(byte[],Charset)`); the factory decodes soundly for the charsets it recognizes
     * (UTF-8, ISO-8859-1/US-ASCII) and falls through to nondet (conservatively UNKNOWN) otherwise. The
     * same `NEW;DUP`-dropping stack surgery as the char[] ctor redirect applies — these share
     * [STRING_FROMARRAY_CTOR_REDIRECTS] in the construction-region matcher.
     */
    private val STRING_BYTES_CTOR_REDIRECTS: Map<String, String> = mapOf(
            "([B)V" to "([B)Ljava/lang/String;",
            "([BII)V" to "([BII)Ljava/lang/String;",
            "([BLjava/nio/charset/Charset;)V" to "([BLjava/nio/charset/Charset;)Ljava/lang/String;",
            "([BLjava/lang/String;)V" to "([BLjava/lang/String;)Ljava/lang/String;",
            "([BIILjava/nio/charset/Charset;)V" to "([BIILjava/nio/charset/Charset;)Ljava/lang/String;",
            "([BIILjava/lang/String;)V" to "([BIILjava/lang/String;)Ljava/lang/String;")

    /** All `new String(<array>, ...)` ctor descriptors we redirect, mapped to `(factory-name, factory-desc)`. */
    private val STRING_FROMARRAY_CTOR_REDIRECTS: Map<String, Pair<String, String>> =
            (STRING_CTOR_REDIRECTS.mapValues { (_, d) -> "ofChars" to d }
                    + STRING_BYTES_CTOR_REDIRECTS.mapValues { (_, d) -> "ofBytes" to d })

    /**
     * `"owner fieldName"` of the well-known `Charset` singletons -> a charset TAG (`"utf8"` /
     * `"latin1"`). JBMC can't reason about a `Charset` object's identity or `name()` at runtime (the
     * singletons come from a static initializer it havocs), so a `new String(byte[], <one of these>)`
     * is recognized by the `getstatic` field that feeds it AT REWRITE TIME and retargeted to the
     * monomorphic [BmcStrings] decoder for that tag — the Charset object never reaches the analysis.
     * Both the JDK `StandardCharsets` and Kotlin `kotlin.text.Charsets` singletons are covered (Kotlin
     * charset-decode sites load `kotlin/text/Charsets.UTF_8`). US-ASCII shares the Latin-1 decoder (identity on
     * its 0x00..0x7F domain). A Charset from any other source (a variable, an unrecognized field) is
     * left on the generic [BmcStrings.ofBytes] path, which falls through to nondet — conservative.
     */
    private val CHARSET_FIELD_TAGS: Map<String, String> = mapOf(
            "java/nio/charset/StandardCharsets UTF_8" to "utf8",
            "kotlin/text/Charsets UTF_8" to "utf8",
            "java/nio/charset/StandardCharsets ISO_8859_1" to "latin1",
            "kotlin/text/Charsets ISO_8859_1" to "latin1",
            "java/nio/charset/StandardCharsets US_ASCII" to "latin1",
            "kotlin/text/Charsets US_ASCII" to "latin1")

    /** Charset tag -> the monomorphic [BmcStrings] decoder name (no `Charset` parameter). */
    private val CHARSET_TAG_FACTORY: Map<String, String> = mapOf(
            "utf8" to "ofBytesUtf8",
            "latin1" to "ofBytesLatin1")

    /** `(byte[],Charset)` / `(byte[],int,int,Charset)` ctor desc -> the monomorphic factory desc (Charset dropped). */
    private val CHARSET_CTOR_TO_MONO_DESC: Map<String, String> = mapOf(
            "([BLjava/nio/charset/Charset;)V" to "([B)Ljava/lang/String;",
            "([BIILjava/nio/charset/Charset;)V" to "([BII)Ljava/lang/String;")

    private val CACHE = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Rewrite directory AND jar entries of [classpath], returning the new classpath. Memoized
     *  per classpath — computed once per worker, which also makes concurrent proofs race-free. */
    @JvmStatic
    fun rewrite(classpath: String): String =
            CACHE.computeIfAbsent(classpath, StringBytecode::doRewrite)

    private fun doRewrite(classpath: String): String =
            ClasspathMirror.mirror(classpath, "strings", { b ->
                ClasspathMirror.Transformed(rewriteClass(b))
            })

    /**
     * True for a call site that dispatches `equals(Object)` through a static type whose own
     * `equals` JBMC won't intercept as String — i.e. `INVOKEVIRTUAL java/lang/Object.equals`
     * (receiver statically typed `Object`, as the generic collection models emit) or any
     * `INVOKEINTERFACE .../equals(Object)Z` (interfaces that redeclare equals, e.g.
     * `java/util/List`/`Set`/`Map`). `String`'s own virtual `equals` is
     * handled by the owner-matched String redirect, so it is deliberately excluded here. A virtual
     * call on a concrete non-Object class (e.g. `java/lang/Integer.equals`) is left alone: its
     * receiver is never a String, and its modeled `equals` is already sound.
     */
    private fun isObjectEqualsCallSite(op: Int, mOwner: String?, name: String?, desc: String?): Boolean {
        if (OBJECT_EQUALS != name + " " + desc) {
            return false
        }
        if (op == Opcodes.INVOKEVIRTUAL) {
            return OBJECT == mOwner
        }
        return op == Opcodes.INVOKEINTERFACE
    }

    /** A concat `invokedynamic` site we replaced with a call to a generated helper. */
    private class ConcatHelper(
            @JvmField val name: String,          // generated method name
            @JvmField val desc: String,          // same descriptor as the indy (args...)Ljava/lang/String;
            @JvmField val recipe: String?,       // makeConcatWithConstants recipe, or null for makeConcat
            @JvmField val constants: Array<Any?>) // bootstrap constants consumed by TAG_CONST (may be empty)

    /** A record-`equals` `invokedynamic` site we replaced with a generated helper. */
    private class RecordEqHelper(
            @JvmField val name: String,          // generated method name
            @JvmField val desc: String,          // same descriptor as the indy: (LRecord;Ljava/lang/Object;)Z
            @JvmField val getters: List<Handle>) // one accessor MethodHandle per record component

    /** A record-`hashCode` `invokedynamic` site we replaced with a generated helper. */
    private class RecordHashHelper(
            @JvmField val name: String,          // generated method name
            @JvmField val desc: String,          // same descriptor as the indy: (LRecord;)I
            @JvmField val getters: List<Handle>) // one accessor MethodHandle per record component

    /** A record-`toString` `invokedynamic` site we replaced with a generated helper. */
    private class RecordStrHelper(
            @JvmField val name: String,          // generated method name
            @JvmField val desc: String,          // same descriptor as the indy: (LRecord;)Ljava/lang/String;
            @JvmField val simpleName: String,    // record's simple class name, the "Point" in "Point[x=.., y=..]"
            @JvmField val compNames: List<String>, // component names in declaration order
            @JvmField val getters: List<Handle>)   // one accessor MethodHandle per record component

    /** Pure transform: redirect String ops to [BmcStrings] and desugar concat indy sites.
     *  Exposed for unit tests. */
    internal fun rewriteClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cw = ClassWriter(0)
        val helpers = ArrayList<ConcatHelper>()
        val recordEqHelpers = ArrayList<RecordEqHelper>()
        val recordHashHelpers = ArrayList<RecordHashHelper>()
        val recordStrHelpers = ArrayList<RecordStrHelper>()
        val counter = intArrayOf(0)
        val owner = arrayOfNulls<String>(1)
        val isInterface = booleanArrayOf(false)

        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visit(version: Int, access: Int, name: String?, sig: String?,
                               superName: String?, ifs: Array<String>?) {
                owner[0] = name
                isInterface[0] = (access and Opcodes.ACC_INTERFACE) != 0
                super.visit(version, access, name, sig, superName, ifs)
            }

            override fun visitMethod(a: Int, n: String?, d: String?, s: String?,
                                     ex: Array<String>?): MethodVisitor {
                val mv = super.visitMethod(a, n, d, s, ex)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    // Deferred-replay buffer for the `new String(char[])` / `new String(char[],int,int)`
                    // shape `NEW String; DUP; <build args>; INVOKESPECIAL String.<init>([C(II))V`. We can't
                    // decide at the NEW whether the constructor is a from-chars redirect (the args sit in
                    // between), and we must not leave a dangling uninitialized `NEW String` that fails JVM
                    // verification. So from a `NEW String` we RECORD subsequent visits as deferred actions;
                    // at the decision point we either DROP the NEW;DUP and replay the recorded args before
                    // emitting the sound `BmcStrings.ofChars` factory (redirect case), or replay everything
                    // verbatim (any non-redirect outcome) — so unrelated code is byte-for-byte unchanged.
                    // Buffering is abandoned (replayed verbatim) on anything that isn't the simple
                    // NEW;DUP;args;<init> shape (a branch/label/another NEW String/method end), keeping the
                    // transform conservative.
                    private val recorded = ArrayList<() -> Unit>()
                    private var recording = false
                    private var sawDup = false

                    // For a `new String(byte[], <charset-getstatic>)`: the charset tag ("utf8"/"latin1")
                    // of a recognized `getstatic <Charsets>.X` IF it is the LAST recorded action when the
                    // ctor is reached. `trailingCharsetIndex` pins it to that trailing position — set when
                    // such a getstatic is recorded, and only honoured if no further action was recorded
                    // after it (so a Charset that isn't the immediate ctor operand is not mis-claimed).
                    private var trailingCharsetTag: String? = null
                    private var trailingCharsetIndex = -1

                    /** Replay the buffered `NEW String` (and DUP, if seen) and recorded args verbatim,
                     *  then stop recording — the conservative fallback for any non-redirect shape. */
                    private fun flushRecording() {
                        if (!recording) {
                            return
                        }
                        val hadDup = sawDup
                        val actions = ArrayList(recorded)
                        recording = false
                        sawDup = false
                        trailingCharsetTag = null
                        trailingCharsetIndex = -1
                        recorded.clear()
                        super.visitTypeInsn(Opcodes.NEW, STRING)
                        if (hadDup) {
                            super.visitInsn(Opcodes.DUP)
                        }
                        for (act in actions) {
                            act()
                        }
                    }

                    override fun visitTypeInsn(op: Int, type: String?) {
                        if (recording) {
                            if (op == Opcodes.NEW && STRING == type) {
                                // A second `new String` before resolving the first: replay the first verbatim,
                                // then start recording afresh from this one.
                                flushRecording()
                            } else {
                                recorded.add { super.visitTypeInsn(op, type) }
                                return
                            }
                        }
                        if (op == Opcodes.NEW && STRING == type) {
                            recording = true
                            sawDup = false
                            trailingCharsetTag = null
                            trailingCharsetIndex = -1
                            recorded.clear()
                            return
                        }
                        super.visitTypeInsn(op, type)
                    }

                    override fun visitInsn(op: Int) {
                        if (recording) {
                            if (op == Opcodes.DUP && !sawDup && recorded.isEmpty()) {
                                sawDup = true        // the DUP immediately after NEW: part of the prefix
                                return
                            }
                            recorded.add { super.visitInsn(op) }
                            return
                        }
                        super.visitInsn(op)
                    }

                    override fun visitIntInsn(op: Int, operand: Int) {
                        if (recording) { recorded.add { super.visitIntInsn(op, operand) }; return }
                        super.visitIntInsn(op, operand)
                    }

                    override fun visitVarInsn(op: Int, varIdx: Int) {
                        if (recording) { recorded.add { super.visitVarInsn(op, varIdx) }; return }
                        super.visitVarInsn(op, varIdx)
                    }

                    override fun visitFieldInsn(op: Int, o: String?, nm: String?, d: String?) {
                        if (recording) {
                            recorded.add { super.visitFieldInsn(op, o, nm, d) }
                            // Remember a recognized Charset-singleton getstatic at its position, so the
                            // ctor can drop it and route to the monomorphic decoder iff it is the trailing
                            // operand. (No-op for any other field access — trailingCharsetIndex stays put
                            // and the next non-charset recorded action leaves it behind the tail.)
                            if (op == Opcodes.GETSTATIC) {
                                val tag = CHARSET_FIELD_TAGS["$o $nm"]
                                if (tag != null) {
                                    trailingCharsetTag = tag
                                    trailingCharsetIndex = recorded.size - 1
                                }
                            }
                            return
                        }
                        super.visitFieldInsn(op, o, nm, d)
                    }

                    override fun visitLdcInsn(value: Any?) {
                        if (recording) { recorded.add { super.visitLdcInsn(value) }; return }
                        super.visitLdcInsn(value)
                    }

                    override fun visitJumpInsn(op: Int, label: org.objectweb.asm.Label?) {
                        // A branch inside the construction region is not the simple shape we redirect;
                        // bail out to verbatim replay (conservative).
                        flushRecording()
                        super.visitJumpInsn(op, label)
                    }

                    override fun visitLabel(label: org.objectweb.asm.Label?) {
                        // A label inside the construction region is, in practice, only a line-number
                        // anchor: kotlinc/javac place one between an argument load and the next arg of a
                        // multi-line/multi-arg `new String(bytes, charset)` (LineNumberTable line 60: 33 in
                        // the byte[]+charset shape), with NO stack effect. Record it (replayed in-place
                        // before the factory) rather than abandoning the redirect — a real control-flow
                        // join is already excluded because [visitJumpInsn]/the switch visitors flush, so a
                        // recorded label can only be such a forward anchor. (The char[] shape has no such
                        // intervening label, which is why it redirected and byte[]+charset did not.)
                        if (recording) { recorded.add { super.visitLabel(label) }; return }
                        super.visitLabel(label)
                    }

                    override fun visitLineNumber(line: Int, start: org.objectweb.asm.Label?) {
                        if (recording) { recorded.add { super.visitLineNumber(line, start) }; return }
                        super.visitLineNumber(line, start)
                    }

                    override fun visitIincInsn(varIdx: Int, increment: Int) {
                        if (recording) { recorded.add { super.visitIincInsn(varIdx, increment) }; return }
                        super.visitIincInsn(varIdx, increment)
                    }

                    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: org.objectweb.asm.Label?,
                                                      vararg labels: org.objectweb.asm.Label?) {
                        flushRecording(); super.visitTableSwitchInsn(min, max, dflt, *labels)
                    }

                    override fun visitLookupSwitchInsn(dflt: org.objectweb.asm.Label?, keys: IntArray?,
                                                       labels: Array<out org.objectweb.asm.Label>?) {
                        flushRecording(); super.visitLookupSwitchInsn(dflt, keys, labels)
                    }

                    override fun visitMultiANewArrayInsn(d: String?, dims: Int) {
                        if (recording) { recorded.add { super.visitMultiANewArrayInsn(d, dims) }; return }
                        super.visitMultiANewArrayInsn(d, dims)
                    }

                    override fun visitMethodInsn(op: Int, mOwner: String?, name: String?,
                                                 desc: String?, itf: Boolean) {
                        val nd = name + " " + desc
                        // The matching from-array constructor for a recorded `NEW String; DUP; args`: drop
                        // the NEW;DUP, replay the arg-building (which leaves [array(, int, int)(, charset)]
                        // on the stack), then call the sound BmcStrings factory (ofChars for char[],
                        // ofBytes for byte[]) in place of the ctor. No uninitialized-ref ever reaches a
                        // frame, so JVM verification stays valid and the stack is one ref shallower.
                        val fromArray = if (desc != null) STRING_FROMARRAY_CTOR_REDIRECTS[desc] else null
                        if (op == Opcodes.INVOKESPECIAL && STRING == mOwner && "<init>" == name
                                && fromArray != null
                                && recording && sawDup && BMC_STRINGS != owner[0]) {
                            // A `new String(byte[], <recognized Charset getstatic>)` whose Charset operand
                            // is the TRAILING recorded action: drop that getstatic and route to the
                            // monomorphic decoder (utf8/latin1) — JBMC can't reason about a Charset object,
                            // so the decoder must not take one. Otherwise replay all recorded args and call
                            // the descriptor-matched factory (generic ofBytes -> nondet for an unrecognized
                            // charset; ofChars for char[]).
                            val monoDesc = if (desc != null) CHARSET_CTOR_TO_MONO_DESC[desc] else null
                            val trailing = trailingCharsetTag
                            val useMono = monoDesc != null && trailing != null
                                    && trailingCharsetIndex == recorded.size - 1
                            val actions = ArrayList(recorded)
                            if (useMono) {
                                actions.removeAt(actions.size - 1)   // drop the trailing charset getstatic
                            }
                            recorded.clear()
                            recording = false
                            sawDup = false
                            trailingCharsetTag = null
                            trailingCharsetIndex = -1
                            for (act in actions) {
                                act()    // replay only the argument-building instructions (NEW;DUP dropped)
                            }
                            if (useMono) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS,
                                        CHARSET_TAG_FACTORY[trailing]!!, monoDesc, false)
                            } else {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, fromArray.first,
                                        fromArray.second, false)
                            }
                            return
                        }
                        if (recording) {
                            // Any other method call inside the construction region (incl. a non-redirect
                            // String ctor, or a nested call that builds the array): not our simple shape —
                            // replay verbatim, then process this call normally below.
                            flushRecording()
                        }
                        if (op == Opcodes.INVOKEVIRTUAL && STRING == mOwner
                                && REDIRECTS.contains(nd)) {
                            // The receiver becomes the first arg, so the operand stack is unchanged:
                            // desc "(P...)R" -> "(Ljava/lang/String;P...)R".
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, name,
                                    "(Ljava/lang/String;" + desc!!.substring(1), false)
                        } else if (op == Opcodes.INVOKESTATIC && STRING == mOwner
                                && STRING_VALUEOF_REDIRECTS.containsKey(nd)) {
                            // String.valueOf(char)/(char[])/(char[],int,int) and copyValueOf: JBMC links
                            // these to a nondet string (CProverString.ofCharArray). The BmcStrings factory
                            // has the SAME descriptor, so retarget the static call in place.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS,
                                    STRING_VALUEOF_REDIRECTS[nd]!!, desc, false)
                        } else if (op == Opcodes.INVOKESTATIC && CHARACTER == mOwner
                                && CHARACTER_TOSTRING == nd) {
                            // Character.toString(char) materializes a 1-char String the same nondet way;
                            // route it through the sound single-char factory.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "ofChar", desc, false)
                        } else if (isObjectEqualsCallSite(op, mOwner, name, desc)
                                && BMC_STRINGS != owner[0]) {
                            // Soundness hole (issue #18): a call site whose static receiver type is
                            // Object (or an interface that declares equals, e.g. java/util/List) emits
                            // INVOKEVIRTUAL java/lang/Object.equals / INVOKEINTERFACE .../equals, which
                            // String.equals's owner-matched redirect above never touches. The collection
                            // models compare keys/elements exactly this way (HashMap.indexOfKey,
                            // HashSet.indexOf, ArrayList.indexOf use `key.equals(keys[i])` with key typed
                            // Object), so String-keyed lookups dispatched into JBMC's unsound native
                            // String.equals. Redirect to BmcStrings.objEquals, whose descriptor is
                            // exactly (Object,Object)Z once the receiver is prepended: it routes the
                            // String/String case through the sound shim and delegates to the receiver's
                            // own equals for everything else (boxed primitives, user classes), so
                            // non-String semantics are preserved. The BmcStrings guard above prevents
                            // rewriting objEquals's own `a.equals(b)` fallback into infinite recursion.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
                        } else {
                            super.visitMethodInsn(op, mOwner, name, desc, itf)
                        }
                    }

                    override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?,
                                                        vararg bsmArgs: Any?) {
                        flushRecording()
                        if (CONCAT_FACTORY == bsm!!.owner
                                && (name == "makeConcat" || name == "makeConcatWithConstants")) {
                            // Replace with invokestatic to a fresh same-descriptor helper; the dynamic
                            // args already on the stack become the helper's arguments unchanged.
                            val hName = "bmc\$concat$" + (counter[0]++)
                            val recipe = if (name == "makeConcatWithConstants") bsmArgs[0] as String? else null
                            val consts: Array<Any?> = if (name == "makeConcatWithConstants")
                                java.util.Arrays.copyOfRange(bsmArgs, 1, bsmArgs.size)
                            else
                                arrayOfNulls(0)
                            helpers.add(ConcatHelper(hName, desc!!, recipe, consts))
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0])
                        } else if (OBJECT_METHODS == bsm.owner && name == "equals") {
                            // Record equals(): replace the ObjectMethods bootstrap with a synthesized
                            // field-by-field comparison. bsmArgs = [recordClass, names, getter MHs...].
                            val hName = "bmc\$recordEquals$" + (counter[0]++)
                            val getters = ArrayList<Handle>()
                            for (i in 2 until bsmArgs.size) {
                                if (bsmArgs[i] is Handle) {
                                    getters.add(bsmArgs[i] as Handle)
                                }
                            }
                            recordEqHelpers.add(RecordEqHelper(hName, desc!!, getters))
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0])
                        } else if (OBJECT_METHODS == bsm.owner && name == "hashCode") {
                            // Record hashCode(): replace the ObjectMethods bootstrap with a synthesized
                            // deterministic fold over the components. JBMC otherwise links the indy to an
                            // unconstrained int. bsmArgs = [recordClass, names, getter MHs...].
                            val hName = "bmc\$recordHashCode$" + (counter[0]++)
                            val getters = ArrayList<Handle>()
                            for (i in 2 until bsmArgs.size) {
                                if (bsmArgs[i] is Handle) {
                                    getters.add(bsmArgs[i] as Handle)
                                }
                            }
                            recordHashHelpers.add(RecordHashHelper(hName, desc!!, getters))
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0])
                        } else if (OBJECT_METHODS == bsm.owner && name == "toString") {
                            // Record toString(): build the canonical "Name[c1=v1, c2=v2]" with the same
                            // sound StringBuilder machinery the concat desugar uses. bsmArgs =
                            // [recordClass, ";"-joined names, getter MHs...]. We only desugar when EVERY
                            // component renders soundly (primitive or String); otherwise String.valueOf
                            // of a reference component is JBMC-nondet, so we leave the indy untouched
                            // (no silently-wrong desugar) rather than emit an unsound stand-in.
                            val getters = ArrayList<Handle>()
                            for (i in 2 until bsmArgs.size) {
                                if (bsmArgs[i] is Handle) {
                                    getters.add(bsmArgs[i] as Handle)
                                }
                            }
                            val namesJoined = if (bsmArgs.size > 1) java.lang.String.valueOf(bsmArgs[1]) else ""
                            val compNames: List<String> = if (namesJoined.isEmpty())
                                listOf()
                            else
                                java.util.Arrays.asList(*(namesJoined as java.lang.String).split(";", -1))
                            val simple = simpleName((bsmArgs[0] as Type).internalName)
                            if (allComponentsRenderSoundly(getters)
                                    && compNames.size == getters.size) {
                                val hName = "bmc\$recordToString$" + (counter[0]++)
                                recordStrHelpers.add(RecordStrHelper(
                                        hName, desc!!, simple, compNames, getters))
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner[0], hName, desc, isInterface[0])
                            } else {
                                super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
                            }
                        } else {
                            super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
                        }
                    }

                    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
                        // Safety net: any still-open recording (a NEW String never resolved to a redirect
                        // ctor) is replayed verbatim before the method closes.
                        flushRecording()
                        super.visitMaxs(maxStack, maxLocals)
                    }
                }
            }

            override fun visitEnd() {
                for (h in helpers) {
                    emitConcatHelper(cw, owner[0]!!, isInterface[0], h)
                }
                for (h in recordEqHelpers) {
                    emitRecordEqualsHelper(cw, isInterface[0], h)
                }
                for (h in recordHashHelpers) {
                    emitRecordHashCodeHelper(cw, isInterface[0], h)
                }
                for (h in recordStrHelpers) {
                    emitRecordToStringHelper(cw, isInterface[0], h)
                }
                super.visitEnd()
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }

    /**
     * Synthesize `static String <name>(<args>)` that builds the concatenation with
     * `StringBuilder.append(...)` (sound in JBMC) instead of the `invokedynamic`.
     * Recipe literal chars and `TAG_CONST` constants collapse into literal append(String)
     * chunks; each `TAG_ARG` appends the next parameter via its typed overload.
     */
    private fun emitConcatHelper(cw: ClassWriter, owner: String, isInterface: Boolean, h: ConcatHelper) {
        var access = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC
        if (isInterface) {
            access = access or Opcodes.ACC_PUBLIC // private static interface methods are fine, but keep callable
            access = access and Opcodes.ACC_PRIVATE.inv()
        }
        val mv = cw.visitMethod(access, h.name, h.desc, null, null)
        mv.visitCode()

        val params = Type.getArgumentTypes(h.desc)
        val slot = IntArray(params.size)
        var running = 0
        var wide = false
        for (i in params.indices) {
            slot[i] = running
            running += params[i].size
            if (params[i].size == 2) {
                wide = true
            }
        }

        mv.visitTypeInsn(Opcodes.NEW, STRING_BUILDER)
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, STRING_BUILDER, "<init>", "()V", false)

        val lit = StringBuilder()
        var argIdx = 0
        var constIdx = 0
        if (h.recipe == null) {
            // makeConcat: every parameter, in order, no literals.
            for (i in params.indices) {
                appendArg(mv, params[i], slot[i])
            }
        } else {
            for (i in 0 until h.recipe.length) {
                val c = h.recipe[i]
                if (c == TAG_ARG) {
                    flushLiteral(mv, lit)
                    appendArg(mv, params[argIdx], slot[argIdx])
                    argIdx++
                } else if (c == TAG_CONST) {
                    lit.append(java.lang.String.valueOf(h.constants[constIdx++]))
                } else {
                    lit.append(c)
                }
            }
            flushLiteral(mv, lit)
        }

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "toString", "()Ljava/lang/String;", false)
        mv.visitInsn(Opcodes.ARETURN)
        // SB ref (1) on the stack while pushing the widest arg (1 or 2); new/dup peak is 2.
        mv.visitMaxs(if (wide) 3 else 2, Math.max(running, 1))
        mv.visitEnd()
    }

    /**
     * Synthesize `static boolean <name>(Record this, Object o)` that compares a record
     * field-by-field, replacing the `ObjectMethods` bootstrap that JBMC links to an
     * unconstrained result. Mirrors the generated record `equals`: `o` must be the same
     * record type, then every component must match (primitives by value/`compare`,
     * references via [BmcStrings.objEquals] so String components stay sound). Has a single
     * branch target (`FALSE`) reached with an empty stack, so one explicit frame suffices.
     */
    private fun emitRecordEqualsHelper(cw: ClassWriter, isInterface: Boolean, h: RecordEqHelper) {
        val access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or
                (if (isInterface) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE)
        val mv = cw.visitMethod(access, h.name, h.desc, null, null)
        mv.visitCode()

        val recordType = Type.getArgumentTypes(h.desc)[0].internalName // slot 0 = this
        val falseLabel = org.objectweb.asm.Label()
        var wide = false

        // if (!(o instanceof Record)) return false;
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitTypeInsn(Opcodes.INSTANCEOF, recordType)
        mv.visitJumpInsn(Opcodes.IFEQ, falseLabel)

        for (g in h.getters) {
            // this.<component>
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            val rt = loadComponent(mv, g)
            // ((Record) o).<component>
            mv.visitVarInsn(Opcodes.ALOAD, 1)
            mv.visitTypeInsn(Opcodes.CHECKCAST, recordType)
            loadComponent(mv, g)
            when (rt.sort) {
                Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT ->
                    mv.visitJumpInsn(Opcodes.IF_ICMPNE, falseLabel)
                Type.LONG -> {
                    wide = true
                    mv.visitInsn(Opcodes.LCMP)
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel)
                }
                Type.FLOAT -> {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "compare", "(FF)I", false)
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel)
                }
                Type.DOUBLE -> {
                    wide = true
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "compare", "(DD)I", false)
                    mv.visitJumpInsn(Opcodes.IFNE, falseLabel)
                }
                else -> { // reference / array
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objEquals",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
                    mv.visitJumpInsn(Opcodes.IFEQ, falseLabel)
                }
            }
        }

        mv.visitInsn(Opcodes.ICONST_1)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitLabel(falseLabel)
        mv.visitFrame(Opcodes.F_NEW, 2, arrayOf<Any>(recordType, "java/lang/Object"), 0, arrayOf<Any>())
        mv.visitInsn(Opcodes.ICONST_0)
        mv.visitInsn(Opcodes.IRETURN)
        // Peak stack = two operands of the widest component (2+2 for long/double), else 2.
        mv.visitMaxs(if (wide) 4 else 2, 2)
        mv.visitEnd()
    }

    /**
     * Synthesize `static int <name>(Record this)` that folds the components into a hash,
     * replacing the `ObjectMethods` bootstrap that JBMC links to an unconstrained int.
     *
     * **Soundness contract.** The JDK deliberately leaves a record's exact hashCode value
     * *unspecified* ("derived from the components"), so asserting a specific magic constant
     * would be wrong. What the JDK *does* guarantee — and what we make true and visible to
     * JBMC — is that hashCode is a *pure, deterministic function of the components*: it reads
     * only the components, with no nondet, so equal records (equal components) get equal hashCode and
     * repeated calls agree. We emit the classic `result = 31*result + componentHash` fold (the
     * same shape `java.util.Objects.hash` / `Arrays.hashCode` use), with each
     * component's hash computed the canonical way: booleans→1231/1237, long/double folded high^low,
     * float via `floatToIntBits`, and reference components via [BmcStrings.objHashCode]
     * (null→0, String content-hashed soundly, other refs delegated). This is a real consistent value,
     * not nondet — which is exactly the property the conformance proofs check.
     */
    private fun emitRecordHashCodeHelper(cw: ClassWriter, isInterface: Boolean, h: RecordHashHelper) {
        val access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or
                (if (isInterface) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE)
        val mv = cw.visitMethod(access, h.name, h.desc, null, null)
        mv.visitCode()
        var wide = false

        // int result = 0;
        mv.visitInsn(Opcodes.ICONST_0)
        var hasBoolean = false
        for (g in h.getters) {
            // result = result * 31 + componentHash(this.<component>);
            mv.visitIntInsn(Opcodes.BIPUSH, 31)
            mv.visitInsn(Opcodes.IMUL)
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            val rt = loadComponent(mv, g) // pushes the component value
            if (componentHashIsWide(rt)) {
                wide = true
            }
            if (rt.sort == Type.BOOLEAN) {
                hasBoolean = true
            }
            emitComponentHash(mv, rt) // consumes the value, pushes its int hash
            mv.visitInsn(Opcodes.IADD)
        }
        mv.visitInsn(Opcodes.IRETURN)
        // Stack peak with the running result int (1 slot) underneath: long/double folding spikes to 6
        // (result + a long DUP2'd = 1+2+2 then +1 for the shift count), a boolean to 3, else 2.
        mv.visitMaxs(if (wide) 6 else (if (hasBoolean) 3 else 2), 1)
        mv.visitEnd()
    }

    /** True if hashing this component type transiently puts a category-2 value on the stack. */
    private fun componentHashIsWide(t: Type): Boolean =
            t.sort == Type.LONG || t.sort == Type.DOUBLE

    /**
     * Given a component value of type `t` on top of the stack, replace it with its `int`
     * hash, using the canonical per-type recipe (matches `Boolean/Integer/Long/Float/Double
     * .hashCode` and [java.util.Objects.hashCode]). All paths are pure functions of the value,
     * so equal components hash equal — the only property we rely on for soundness.
     */
    private fun emitComponentHash(mv: MethodVisitor, t: Type) {
        when (t.sort) {
            Type.BOOLEAN -> {
                // Boolean.hashCode: b ? 1231 : 1237, computed branchlessly as 1237 - 6*b (b in {0,1})
                // so no stack-map frame is needed (the running result int stays on the stack untouched).
                mv.visitIntInsn(Opcodes.BIPUSH, 6)
                mv.visitInsn(Opcodes.IMUL)
                mv.visitLdcInsn(1237)
                mv.visitInsn(Opcodes.SWAP)
                mv.visitInsn(Opcodes.ISUB)
                return
            }
            Type.BYTE, Type.SHORT, Type.CHAR, Type.INT ->
                return // already an int whose hashCode is itself
            Type.LONG -> {
                // (int)(v ^ (v >>> 32))
                mv.visitInsn(Opcodes.DUP2)
                mv.visitIntInsn(Opcodes.BIPUSH, 32)
                mv.visitInsn(Opcodes.LUSHR)
                mv.visitInsn(Opcodes.LXOR)
                mv.visitInsn(Opcodes.L2I)
                return
            }
            Type.FLOAT -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToIntBits",
                        "(F)I", false)
                return
            }
            Type.DOUBLE -> {
                // long bits = doubleToLongBits(v); (int)(bits ^ (bits >>> 32))
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToLongBits",
                        "(D)J", false)
                mv.visitInsn(Opcodes.DUP2)
                mv.visitIntInsn(Opcodes.BIPUSH, 32)
                mv.visitInsn(Opcodes.LUSHR)
                mv.visitInsn(Opcodes.LXOR)
                mv.visitInsn(Opcodes.L2I)
                return
            }
            else -> // reference / array
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, BMC_STRINGS, "objHashCode",
                        "(Ljava/lang/Object;)I", false)
        }
    }

    /** Component types whose `String.valueOf` we can render soundly in [appendValue]:
     *  the primitives (numerics routed through `Integer/Long.toString`, the rest via the sound
     *  `StringBuilder` overloads) and `String` (appended directly). Other reference
     *  components would need `o.toString()`, which JBMC links to nondet — so a record with one
     *  is left with its original `toString` indy rather than desugared unsoundly. */
    private fun allComponentsRenderSoundly(getters: List<Handle>): Boolean {
        for (g in getters) {
            val t = if (g.tag == Opcodes.H_GETFIELD)
                Type.getType(g.desc)
            else
                Type.getReturnType(g.desc)
            if (t.sort == Type.OBJECT && t.internalName != STRING) {
                return false
            }
            if (t.sort == Type.ARRAY) {
                return false
            }
        }
        return true
    }

    /** "a/b/Point" -> "Point"; "a/b/Outer$Inner" -> "Inner" (record toString uses the simple name). */
    private fun simpleName(internalName: String): String {
        val slash = internalName.lastIndexOf('/')
        val s = if (slash < 0) internalName else internalName.substring(slash + 1)
        val dollar = s.lastIndexOf('$')
        return if (dollar < 0) s else s.substring(dollar + 1)
    }

    /**
     * Synthesize `static String <name>(Record this)` that builds the canonical record
     * `"Name[c1=v1, c2=v2]"` with `StringBuilder` (sound in JBMC) instead of the
     * `ObjectMethods` bootstrap that JBMC links to an unconstrained String. Only reached when
     * every component renders soundly (primitive or String — see [allComponentsRenderSoundly]),
     * so each value is appended via the same sound path the concat desugar uses: numerics through
     * `Integer/Long.toString`, `String` directly, the remaining primitives via the typed
     * `StringBuilder` overloads. The literal scaffolding (`"Name["`, `"="`, `", "`, `"]"`) is exact, so the result's content is a sound function of the components.
     */
    private fun emitRecordToStringHelper(cw: ClassWriter, isInterface: Boolean, h: RecordStrHelper) {
        val access = Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC or
                (if (isInterface) Opcodes.ACC_PUBLIC else Opcodes.ACC_PRIVATE)
        val mv = cw.visitMethod(access, h.name, h.desc, null, null)
        mv.visitCode()

        mv.visitTypeInsn(Opcodes.NEW, STRING_BUILDER)
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, STRING_BUILDER, "<init>", "()V", false)

        var wide = false
        appendLiteral(mv, h.simpleName + "[")
        for (i in h.getters.indices) {
            if (i > 0) {
                appendLiteral(mv, ", ")
            }
            appendLiteral(mv, h.compNames[i] + "=")
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            val rt = loadComponent(mv, h.getters[i])
            if (rt.size == 2) {
                wide = true
            }
            // Reuse the concat desugar's sound per-type append (int/long -> Integer/Long.toString,
            // String/others via the appropriate StringBuilder overload).
            appendValue(mv, rt)
        }
        appendLiteral(mv, "]")

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "toString", "()Ljava/lang/String;", false)
        mv.visitInsn(Opcodes.ARETURN)
        // SB ref (1) under the receiver/loaded component; a wide (long/double) component peaks at 3.
        mv.visitMaxs(if (wide) 3 else 2, 1)
        mv.visitEnd()
    }

    /** Append a constant String to the StringBuilder already on the stack (ref left on the stack). */
    private fun appendLiteral(mv: MethodVisitor, s: String) {
        mv.visitLdcInsn(s)
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
    }

    /** Append a component value (already on the stack) to the StringBuilder, soundly: int/long go
     *  through `Integer/Long.toString` (append(int/long) is unsound in JBMC), the rest use the
     *  typed overload. Shares the recipe with [appendArg] but the value is already loaded. */
    private fun appendValue(mv: MethodVisitor, t: Type) {
        val sort = t.sort
        if (sort == Type.BYTE || sort == Type.SHORT || sort == Type.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString",
                    "(I)Ljava/lang/String;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            return
        }
        if (sort == Type.LONG) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "toString",
                    "(J)Ljava/lang/String;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            return
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append", appendDesc(t), false)
    }

    /** Emit the access for one record component (the receiver is already on the stack) and return
     *  its type. Records pass components as field getters (H_GETFIELD) or accessor handles. */
    private fun loadComponent(mv: MethodVisitor, g: Handle): Type {
        if (g.tag == Opcodes.H_GETFIELD) {
            mv.visitFieldInsn(Opcodes.GETFIELD, g.owner, g.name, g.desc)
            return Type.getType(g.desc)
        }
        val itf = g.tag == Opcodes.H_INVOKEINTERFACE
        val op = if (g.tag == Opcodes.H_INVOKESTATIC) Opcodes.INVOKESTATIC
        else if (itf) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        mv.visitMethodInsn(op, g.owner, g.name, g.desc, itf)
        return Type.getReturnType(g.desc)
    }

    private fun flushLiteral(mv: MethodVisitor, lit: StringBuilder) {
        if (lit.length == 0) {
            return
        }
        mv.visitLdcInsn(lit.toString())
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
        lit.setLength(0)
    }

    private fun appendArg(mv: MethodVisitor, t: Type, slot: Int) {
        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot)
        // int/long → string via Integer/Long.toString (JBMC models those soundly), then append the
        // String — StringBuilder.append(int) itself is unsound. So "x" + anInt verifies. char stays
        // a char append (routing it through toString would print the code point, not the character).
        val sort = t.sort
        if (sort == Type.BYTE || sort == Type.SHORT || sort == Type.INT) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString",
                    "(I)Ljava/lang/String;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            return
        }
        if (sort == Type.LONG) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "toString",
                    "(J)Ljava/lang/String;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
            return
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STRING_BUILDER, "append", appendDesc(t), false)
    }

    /** StringBuilder.append overload descriptor for an argument type (arrays/objects via Object). */
    private fun appendDesc(t: Type): String {
        return when (t.sort) {
            Type.BOOLEAN -> "(Z)Ljava/lang/StringBuilder;"
            Type.CHAR -> "(C)Ljava/lang/StringBuilder;"
            Type.BYTE, Type.SHORT, Type.INT -> "(I)Ljava/lang/StringBuilder;"
            Type.LONG -> "(J)Ljava/lang/StringBuilder;"
            Type.FLOAT -> "(F)Ljava/lang/StringBuilder;"
            Type.DOUBLE -> "(D)Ljava/lang/StringBuilder;"
            else ->
                // String gets the String overload; everything else (incl. char[]) via Object,
                // matching StringConcat semantics (arrays concat as their Object.toString()).
                if (t.internalName == STRING)
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                else
                    "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
        }
    }
}
