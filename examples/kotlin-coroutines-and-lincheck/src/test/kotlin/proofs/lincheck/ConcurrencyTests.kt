package proofs.lincheck

import example.lincheck.OverdraftAccount
import example.lincheck.RacyAccount
import example.lincheck.SafeAccount
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * The **concurrency** axis. Lincheck runs the operations from multiple threads across
 * many interleavings and checks each history is *linearizable* — equivalent to some
 * sequential one. It is blind to whether that sequential behaviour is *correct*.
 *
 * Notice [overdraft][OverdraftAccount] passes here: it's `@Synchronized`, so every
 * interleaving matches a sequential run — even though that run can overdraw. Lincheck
 * checks consistency, not the business rule (the `@BmcProof` checks the rule).
 *
 * These tests exercise **Lincheck** (a JetBrains library, not bmc4j) and its interleaving
 * search is slow (~minutes), so they are **opt-in**: run with `-Dbmc.lincheck=true`. By default
 * only the `@BmcProof` side runs, keeping the suite fast.
 */
private const val LINCHECK_FLAG = "bmc.lincheck"

// INTENDED FAILURE: no lock. Lincheck finds a lost-update interleaving — the bug the
// @BmcProof stays green on (RacyAccount's logic is sound).
@EnabledIfSystemProperty(named = LINCHECK_FLAG, matches = "true")
class RacyAccountConcurrencyTest {
    private val account = RacyAccount()

    @Operation fun deposit(amount: Int) = account.deposit(amount)
    @Operation fun withdraw(amount: Int): Boolean = account.withdraw(amount)
    @Operation fun balance(): Int = account.balance()

    @Test fun modelChecking() = ModelCheckingOptions().check(this::class)
}

// Locked → linearizable → PASSES, despite the overdraft logic bug.
@EnabledIfSystemProperty(named = LINCHECK_FLAG, matches = "true")
class OverdraftAccountConcurrencyTest {
    private val account = OverdraftAccount()

    @Operation fun deposit(amount: Int) = account.deposit(amount)
    @Operation fun withdraw(amount: Int): Boolean = account.withdraw(amount)
    @Operation fun balance(): Int = account.balance()

    @Test fun modelChecking() = ModelCheckingOptions().check(this::class)
}

// Both protections present → PASSES.
@EnabledIfSystemProperty(named = LINCHECK_FLAG, matches = "true")
class SafeAccountConcurrencyTest {
    private val account = SafeAccount()

    @Operation fun deposit(amount: Int) = account.deposit(amount)
    @Operation fun withdraw(amount: Int): Boolean = account.withdraw(amount)
    @Operation fun balance(): Int = account.balance()

    @Test fun modelChecking() = ModelCheckingOptions().check(this::class)
}
