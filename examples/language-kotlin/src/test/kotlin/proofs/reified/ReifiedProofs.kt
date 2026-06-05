package proofs.reified

import example.reified.asType
import example.reified.classify
import example.reified.isType
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Reified generics are sound under BMC because `inline fun <reified T>` is INLINED at the call site
 * with `T` replaced by a concrete type — `x is T` becomes a plain `instanceof`, with no
 * `invokedynamic` and no reflection over an erased parameter. The proofs feed symbolic subjects
 * through reified type tests and check the classification is exactly right.
 */
class ReifiedProofs {

    // PASS over every Int subject: isType<Int> recognizes it and the other tests reject it.
    @BmcProof
    fun reified_recognizes_int() {
        val x: Any = Bmc.anyInt(-1000, 1000)
        Bmc.check(isType<Int>(x))
        Bmc.check(!isType<String>(x))
        Bmc.check(!isType<Boolean>(x))
    }

    // PASS over every Boolean subject: isType<Boolean> recognizes it; it is not an Int.
    @BmcProof
    fun reified_recognizes_boolean() {
        val x: Any = Bmc.anyBoolean()
        Bmc.check(isType<Boolean>(x))
        Bmc.check(!isType<Int>(x))
    }

    // PASS: classify() routes a symbolic Int to exactly the "int" branch (the reified type tests
    // are real, not nondet — every Int lands in "int", never another bucket).
    @BmcProof
    fun classify_int_is_exact() {
        val x: Any = Bmc.anyInt(-1000, 1000)
        Bmc.check(classify(x) == "int")
    }

    // PASS: reified safe-cast returns a non-null, value-preserving result for a matching type.
    @BmcProof
    fun reified_safe_cast_preserves_value() {
        val n = Bmc.anyInt(-1000, 1000)
        val x: Any = n
        val back: Int? = asType<Int>(x)
        Bmc.check(back != null && back == n)
    }

    // PASS: reified safe-cast of a mismatched type yields null (the `as? T` is a genuine type test).
    @BmcProof
    fun reified_safe_cast_mismatch_is_null() {
        val x: Any = Bmc.anyInt(-1000, 1000)
        Bmc.check(asType<String>(x) == null)
    }

    // FAIL (the bug): an Int subject is NOT a String, so claiming isType<String> holds for it is
    // false. BMC refutes it — confirming the reified test is genuinely evaluated.
    // Expected verdict: REFUTED - reified is/as? resolves the REAL type - the false claim is refuted.
    @BmcProof(expect = Verdict.REFUTED)
    fun int_is_wrongly_claimed_string() {
        val x: Any = Bmc.anyInt(-1000, 1000)
        Bmc.check(isType<String>(x))
    }
}
