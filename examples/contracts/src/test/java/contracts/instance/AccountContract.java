package contracts.instance;

import example.instance.Account;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.ExpectEnforce;
import org.bmc4j.Requires;
import org.bmc4j.Verdict;

/**
 * The contract for {@link Account}'s pure instance method {@code project(int)}, declared
 * test-side. The mirror's signature binds to the instance method on {@code Account}; because the
 * resolved target is an instance method, the processor threads the receiver into the predicates as
 * a leading {@code self} parameter — {@code requires(self, amount)} and
 * {@code ensures(result, self, amount)}.
 *
 * <p>The postcondition depends on a field of the receiver ({@code self.balance()}), so the
 * enforce-proof is only green because it holds for ALL receiver states (JBMC makes {@code self}
 * nondet, exactly like a static input). The {@code badProject} mirror is a deliberately-false demo
 * pinned to {@link Verdict#REFUTED}: it claims a strictly-greater result, which is false at
 * {@code amount == 0} for any balance — so it never publishes a reusable redirect and its
 * enforce-proof passes by refutation.
 */
@BmcContractsFor(Account.class)
interface AccountContract {

    @Requires("nonNegative")
    @Ensures("atLeastBalance")
    int project(int amount);

    // A FALSE instance contract on a second instance method: "result is strictly greater than the
    // balance" is a lie at amount == 0 for any balance. Pinned REFUTED, so it stays a demo and
    // publishes no redirect (a non-VERIFIED contract never summarizes callers against a false post).
    @ExpectEnforce(Verdict.REFUTED)
    @Requires("nonNegative")
    @Ensures("exceedsBalance")
    int projectAgain(int amount);

    static boolean nonNegative(Account self, int amount) {
        // Bound BOTH the receiver state and the argument: a precondition over `self` is exactly
        // what an instance contract adds. Without bounding the balance, balance + amount could
        // overflow for a near-MAX balance, which would (correctly) refute the postcondition — the
        // receiver is symbolic, so its full range is in play unless the contract constrains it.
        return self.balance() >= 0 && self.balance() <= 1000 && amount >= 0 && amount <= 8;
    }

    static boolean atLeastBalance(int result, Account self, int amount) {
        return result >= self.balance();
    }

    static boolean exceedsBalance(int result, Account self, int amount) {
        return result > self.balance();
    }
}
