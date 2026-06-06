package proofs.symbolicparams

import example.symbolicparams.Wallet
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * Symbolic Kotlin object parameters. A `@BmcProof`'s own non-null-typed parameters are
 * auto-assumed non-null (the kotlinc `checkNotNullParameter` prologue becomes `assume`), so these
 * proofs run their bodies instead of refuting on `p = null` before the first line. Nullable
 * parameters keep `null` in their domain, and interior calls keep the throwing semantics —
 * `bmc { kotlinNullableParams = true }` restores the honest-JVM prologue for proofs that
 * deliberately model hostile Java callers.
 */
class SymbolicParamProofs {

    // PASS: runs for EVERY Wallet — the parameter is non-null by its Kotlin type, the field is
    // fully symbolic. (Before the prologue rewrite this refuted with the un-constructible w = null.)
    @BmcProof
    fun overdrawn_means_negative(w: Wallet) {
        Bmc.check(w.isOverdrawn() == (w.cents < 0))
    }

    // FAIL (the bug): the body genuinely runs over the whole field domain — BMC finds the one
    // value where negation overflows. Expected verdict: REFUTED at cents == Int.MIN_VALUE.
    @BmcProof(expect = Verdict.REFUTED)
    fun abs_is_non_negative(w: Wallet) {
        Bmc.check(w.absCents() >= 0)
    }

    // PASS: the saturating fix holds for every Wallet.
    @BmcProof
    fun safe_abs_is_non_negative(w: Wallet) {
        Bmc.check(w.safeAbsCents() >= 0)
    }

    // FAIL (by design): a NULLABLE parameter keeps null in its domain — the auto-assume applies
    // only to non-null types (kotlinc emits no prologue check for `Wallet?`).
    // Expected verdict: REFUTED with w = null.
    @BmcProof(expect = Verdict.REFUTED)
    fun nullable_param_keeps_null(w: Wallet?) {
        Bmc.check(w != null)
    }
}
