package proofs.nullsafety

import example.nullsafety.Account
import example.nullsafety.Accounts
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

class NullSafetyProofs {

    /** A non-null account whose parent may or may not be present. */
    private fun anyAccount(): Account =
        Account(parent = if (Bmc.anyBoolean()) Account(null, 0) else null, balance = Bmc.anyInt())

    /**
     * FAILS: `!!` throws when an account has no parent. Kotlin's null-check intrinsic analyzes
     * cleanly thanks to the bundled clean Intrinsics model.
     */
    // Expected verdict: REFUTED - a parentless account dereferences null.
    @BmcProof(expect = Verdict.REFUTED)
    fun parentBalance_handles_any_account() {
        Accounts.parentBalance(anyAccount())
    }

    /** PASSES: the null-safe version (`?.`/`?:`) handles a missing parent. */
    @BmcProof
    fun parentBalanceOrZero_handles_any_account() {
        Accounts.parentBalanceOrZero(anyAccount())
    }
}
