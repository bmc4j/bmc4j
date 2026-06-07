package example.purity;

/**
 * A deliberately <b>impure</b> method, for the purity-audit demo. {@link #record(int)} returns the
 * running total <em>and</em> mutates the pre-existing static {@link #total} as a side effect — a
 * value-returning method that is <em>not</em> a function of its inputs.
 *
 * <p>A contract summarizes only the <em>return value</em>: if a caller's call site were redirected
 * to a {@code record__stub}, the increment of {@link #total} would silently never happen, yet the
 * generated enforce-proof (which checks {@code @Ensures}, not purity) would still pass. That is the
 * exact false-green the purity audit closes — see {@code contracts.purity.LedgerContract} and
 * {@code proofs.purity.PurityAuditDemoTest}.
 */
public final class Ledger {

    /** Pre-existing global state the impure method mutates. */
    public static int total = 0;

    private Ledger() {
    }

    /** Adds {@code amount} to the running {@link #total} and returns the new total — a heap write to
     *  pre-existing state, so this is NOT a legal contract target. */
    public static int record(int amount) {
        total += amount;        // PUTSTATIC: a caller-observable side effect a contract would drop
        return total;
    }
}
