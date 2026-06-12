package org.bmc4j.engine

/**
 * Renders the Java source of a contract **stub** method for the replace direction.
 * For a contracted static method `T C.f(P... args)` the generated stub has the
 * same signature and body:
 *
 * ```
 * public static T f__stub(P... args) {
 *     org.bmc4j.Bmc.check(C.<requires>(args));     // assert the precondition (caller's duty)
 *     T r = org.cprover.CProver.nondetT();               // an arbitrary result...
 *     org.bmc4j.Bmc.assume(C.<ensures>(r, args));  // ...constrained by the postcondition
 *     return r;
 * }
 * ```
 *
 * For a **pure instance** method `T R.f(P... args)` the stub threads the receiver as an
 * ordinary leading parameter (`self`), exactly where the call site already has it on the
 * operand stack — the predicates take `self` as their first argument after `result`/before
 * `args`:
 *
 * ```
 * public static T f__stub(R self, P... args) {
 *     org.bmc4j.Bmc.check(C.<requires>(self, args));
 *     T r = org.cprover.CProver.nondetT();
 *     org.bmc4j.Bmc.assume(C.<ensures>(r, self, args));
 *     return r;
 * }
 * ```
 *
 * [ContractRewriter] redirects call sites of `C.f` to this stub, so callers
 * reuse the contract instead of re-analyzing the body. The stub is the analysis-time
 * counterpart of the enforce-proof that discharges the same predicates against the real
 * body. Targets: static and **pure instance**, value-returning methods.
 */
object ContractStubGenerator {

    /** One contracted method to stub. */
    class Contract @JvmOverloads constructor(
            /** Production class declaring the contracted method (the enforce-proof calls it; the
             *  replace rewriter redirects its call sites). */
            @JvmField val targetFqn: String,
            /** Class holding the `static boolean` predicate methods — the test-side contract type. */
            @JvmField val predicateOwnerFqn: String,
            @JvmField val methodName: String,
            @JvmField val returnType: String,
            /** Ordered (type, name) parameters of the contracted method. */
            @JvmField val params: List<Map.Entry<String, String>>,
            /** static boolean predicate over params, or null. */
            @JvmField val requires: String?,
            /** static boolean predicate over (result, params), or null. */
            @JvmField val ensures: String?,
            /** Expected verdict of the generated enforce-proof ("VERIFIED" default; demo contracts
             *  declare "REFUTED"/"VACUOUS" via `@BmcContractsFor(expectEnforce = ...)`). */
            expectEnforce: String? = "VERIFIED",
            /** Receiver type (the target class FQN) for a **pure instance** contract, threaded as the
             *  leading `self` parameter of the stub/enforce and predicates; `null` for a static target.
             *  Exact-class binding, like the static case — no virtual dispatch of the target method. */
            @JvmField val receiverType: String? = null,
            /** Non-null iff the contracted method is a Kotlin `suspend` function. Kotlin lowers
             *  `suspend fun f(args): T` to `Object f(args, Continuation)`: the body is a state machine
             *  that, under bmc4j's immediate-dispatch idealization, completes in one call and returns the
             *  BOXED declared result (never COROUTINE_SUSPENDED). [returnType] then holds the DECLARED
             *  (unboxed where primitive) Kotlin result type — what the predicates bind — while this field
             *  carries the boxed reference form the ABI actually returns, so the stub/enforce can box on
             *  return and unbox before the predicate. `null` for an ordinary (non-suspend) method. */
            @JvmField val suspendBoxedReturn: String? = null,
            /** True iff the predicates are ordinary members of a Kotlin `object` (a singleton), rather
             *  than `static` methods. Kotlin compiles an `object`'s un-`@JvmStatic` `fun`s to INSTANCE
             *  methods reached through the synthetic `<Owner>.INSTANCE` singleton field, so the generated
             *  Java must invoke them on that receiver (`<Owner>.INSTANCE.pred(args)`) rather than
             *  statically (`<Owner>.pred(args)`). A pure boolean method on a known singleton is analyzed
             *  by JBMC identically to a static one (and the purity audit certifies the singleton read via
             *  its `static final INSTANCE` field), so this is a pure call-shape change — no soundness
             *  difference. `false` for the static/companion form (call statically, unchanged). */
            @JvmField val predicateOnObject: Boolean = false) {

        @JvmField
        val expectEnforce: String = expectEnforce ?: "VERIFIED"

        /** True for a pure-instance contract (receiver threaded as `self`). */
        @JvmField
        val isInstance: Boolean = receiverType != null

        /** True for a `suspend` contract (lowered to a trailing `Continuation` parameter + `Object`
         *  return; driven to completion under the immediate-dispatch idealization). */
        @JvmField
        val isSuspend: Boolean = suspendBoxedReturn != null

        /** The Java expression on which a predicate is invoked: the bare owner FQN for a `static`
         *  predicate (`Owner.pred(args)`), or the singleton instance `Owner.INSTANCE` for a predicate
         *  hosted as an ordinary member of a Kotlin `object` (`Owner.INSTANCE.pred(args)`). */
        val predicateTarget: String
            get() = if (predicateOnObject) "$predicateOwnerFqn.INSTANCE" else predicateOwnerFqn

        /** The receiver parameter name used in generated stubs/proofs; "self" by convention. */
        val receiverName: String
            get() = "self"

        /** The generated parameter name for the trailing `Continuation` of a suspend target. */
        val continuationName: String
            get() = "\$completion"
    }

    /** Render a stub class holding a `<method>__stub` method per contract. */
    @JvmStatic
    fun generate(packageName: String?, stubClassName: String, contracts: List<Contract>): String =
            buildString {
                if (!packageName.isNullOrEmpty()) {
                    append("package ").append(packageName).append(";\n\n")
                }
                append("// Generated by bmc-contracts (replace stubs). Do not edit.\n")
                append("public final class ").append(stubClassName).append(" {\n\n")
                append("    private ").append(stubClassName).append("() {}\n")
                for (c in contracts) {
                    append('\n').append(method(c))
                }
                append("}\n")
            }

    private fun method(c: Contract): String = buildString {
        // The user's parameters, plus the receiver threaded as a leading `self` for an instance
        // contract. The predicate argument list mirrors the declared predicate shape:
        //   requires(self?, args...)        ensures(result, self?, args...)
        val userParamDecls = c.params.map { "${it.key} ${it.value}" }
        val userArgs = c.params.map { it.value }
        var paramDecls = if (c.isInstance) {
            listOf("${c.receiverType} ${c.receiverName}") + userParamDecls
        } else {
            userParamDecls
        }
        // A suspend target's lowered ABI is `(args, Continuation)Object`: the stub must replicate it so
        // the call-site rewrite keeps the operand stack and descriptor identical. The trailing
        // Continuation is a real parameter of the stub (matching the call site) but is NOT a predicate
        // argument — it's coroutine plumbing the contract never references. The stub never suspends, so
        // it ignores the continuation entirely and returns the boxed result directly.
        if (c.isSuspend) {
            paramDecls = paramDecls + "kotlin.coroutines.Continuation ${c.continuationName}"
        }
        // The arguments passed to the requires predicate: (self?, args...).
        val preArgs = if (c.isInstance) listOf(c.receiverName) + userArgs else userArgs

        // A suspend stub returns the erased `Object` (the lowered ABI); an ordinary stub returns the
        // declared type.
        val stubReturnType = if (c.isSuspend) "java.lang.Object" else c.returnType
        append("    public static ").append(stubReturnType).append(' ')
                .append(c.methodName).append("__stub(").append(paramDecls.joinToString(", ")).append(") {\n")
        if (c.requires != null) {
            append("        org.bmc4j.Bmc.check(")
                    .append(c.predicateTarget).append('.').append(c.requires)
                    .append('(').append(preArgs.joinToString(", ")).append("));\n")
        }
        if (c.returnType == "void") {
            // Degenerate: no result to constrain (targets value-returning methods). A suspend Unit
            // function is out of scope (rejected by the processor), so this stays the plain case.
            append("    }\n")
            return@buildString
        }
        // Havoc a result of the DECLARED (unboxed-where-primitive) type and constrain it with @Ensures —
        // exactly the value a suspend body resolves to under immediate dispatch.
        append("        ").append(c.returnType).append(" r = ")
                .append(nondetExpr(c.returnType)).append(";\n")
        if (c.ensures != null) {
            // ensures(result, self?, args...).
            val postArgs = listOf("r") + preArgs
            append("        org.bmc4j.Bmc.assume(")
                    .append(c.predicateTarget).append('.').append(c.ensures)
                    .append('(').append(postArgs.joinToString(", ")).append("));\n")
        }
        if (c.isSuspend) {
            // Return the result BOXED into Object, matching the suspend ABI. A primitive declared type
            // is boxed via its wrapper's valueOf; a reference declared type is already Object-assignable.
            append("        return ").append(boxExpr(c.returnType, "r")).append(";\n")
        } else {
            append("        return r;\n")
        }
        append("    }\n")
    }

    /** Box [valueExpr] of the declared (possibly primitive) [type] into a reference for the suspend
     *  ABI's `Object` return. A reference type is returned as-is. */
    internal fun boxExpr(type: String, valueExpr: String): String = when (type) {
        "int" -> "java.lang.Integer.valueOf($valueExpr)"
        "long" -> "java.lang.Long.valueOf($valueExpr)"
        "short" -> "java.lang.Short.valueOf($valueExpr)"
        "byte" -> "java.lang.Byte.valueOf($valueExpr)"
        "char" -> "java.lang.Character.valueOf($valueExpr)"
        "boolean" -> "java.lang.Boolean.valueOf($valueExpr)"
        "float" -> "java.lang.Float.valueOf($valueExpr)"
        "double" -> "java.lang.Double.valueOf($valueExpr)"
        else -> valueExpr
    }

    /** Unbox [valueExpr] (an `Object` holding the boxed declared result) to the declared primitive
     *  [type] for the enforce-proof's predicate call. The boxed reference is first cast to its wrapper.
     *  A reference declared type is cast directly. */
    internal fun unboxExpr(type: String, valueExpr: String): String = when (type) {
        "int" -> "((java.lang.Integer) $valueExpr).intValue()"
        "long" -> "((java.lang.Long) $valueExpr).longValue()"
        "short" -> "((java.lang.Short) $valueExpr).shortValue()"
        "byte" -> "((java.lang.Byte) $valueExpr).byteValue()"
        "char" -> "((java.lang.Character) $valueExpr).charValue()"
        "boolean" -> "((java.lang.Boolean) $valueExpr).booleanValue()"
        "float" -> "((java.lang.Float) $valueExpr).floatValue()"
        "double" -> "((java.lang.Double) $valueExpr).doubleValue()"
        else -> "($type) $valueExpr"
    }

    /** The CProver nondet call producing an arbitrary value of [type]. Shared with
     *  [ContractEnforceProofGenerator], which builds nondet enforce-proof args. */
    internal fun nondetExpr(type: String): String = when (type) {
        "int" -> "org.cprover.CProver.nondetInt()"
        "long" -> "org.cprover.CProver.nondetLong()"
        "short" -> "org.cprover.CProver.nondetShort()"
        "byte" -> "org.cprover.CProver.nondetByte()"
        "char" -> "org.cprover.CProver.nondetChar()"
        "boolean" -> "org.cprover.CProver.nondetBoolean()"
        "float" -> "org.cprover.CProver.nondetFloat()"
        "double" -> "org.cprover.CProver.nondetDouble()"
        else -> "($type) org.cprover.CProver.nondetWithoutNull()"
    }
}
