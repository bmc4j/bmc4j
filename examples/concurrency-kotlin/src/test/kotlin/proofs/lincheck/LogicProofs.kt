package proofs.lincheck

import example.lincheck.OverdraftAccount
import example.lincheck.RacyAccount
import example.lincheck.SafeAccount
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Shard
import org.bmc4j.Verdict

/**
 * The **logic** axis. Each proof asks the same question — *can `withdraw` ever drive the
 * balance below zero, for any amount?* — symbolically, over a single sequential
 * execution. It is blind to threading.
 *
 * Notice [racy][RacyAccount] passes here: its logic is fine; only its concurrency is
 * broken, which a `@BmcProof` cannot see (see `ConcurrencyTests`).
 */
class LogicProofs {

    // Sound guard → proven for every balance and amount.
    // These three ~73s proofs share the concurrency-kotlin module with CoroutineProofTests; pin them
    // so the module's heavy proofs spread across shards instead of hash-clustering.
    @Shard(2)
    @BmcProof
    fun racy_withdraw_never_overdraws() {
        val a = RacyAccount()
        a.deposit(Bmc.anyInt(0, 1_000_000))
        a.withdraw(Bmc.anyInt(0, 1_000_000))
        Bmc.check(a.balance() >= 0)
    }

    // INTENDED FAILURE: no overdraft guard. BMC finds an amount that goes negative —
    // the bug Lincheck stays green on (OverdraftAccount is thread-safe).
    // Expected verdict: REFUTED - the seeded logic bug allows a negative balance.
    @Shard(3)
    @BmcProof(expect = Verdict.REFUTED)
    fun overdraft_withdraw_can_go_negative() {
        val a = OverdraftAccount()
        a.deposit(Bmc.anyInt(0, 1_000_000))
        a.withdraw(Bmc.anyInt(0, 1_000_000))
        Bmc.check(a.balance() >= 0)
    }

    // Both protections present → proven.
    @Shard(3)
    @BmcProof
    fun safe_withdraw_never_overdraws() {
        val a = SafeAccount()
        a.deposit(Bmc.anyInt(0, 1_000_000))
        a.withdraw(Bmc.anyInt(0, 1_000_000))
        Bmc.check(a.balance() >= 0)
    }
}
