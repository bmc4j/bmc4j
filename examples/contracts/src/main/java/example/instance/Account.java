package example.instance;

/**
 * A pure instance method: {@code project} reads the receiver's {@code balance} but never mutates
 * it (the account is immutable). Its contract lives test-side in {@code contracts.instance} —
 * production code carries no bmc references. This is the shape v2 contracts add over v1's
 * static-only targets: the receiver is threaded into the predicates as {@code self}.
 */
public final class Account {

    private final int balance;

    public Account(int balance) {
        this.balance = balance;
    }

    public int balance() {
        return balance;
    }

    /**
     * The balance after applying {@code amount} as a sequence of unit steps — a pure projection over
     * {@code this} and the argument (reads {@code this.balance}, mutates nothing). The loop is
     * artificial but real: it makes the method costly to inline, so a caller at a tiny {@code unwind}
     * can only get through by reusing the contract instead of unrolling it — the same "contracts beat
     * inlining" point as {@code basics}, now over an instance method whose contract depends on a field.
     */
    public int project(int amount) {
        int result = balance;
        for (int i = 0; i < amount; i++) {
            result += 1;
        }
        return result;
    }

    /**
     * Identical projection — a second pure instance method, used only to carry a deliberately-false
     * demo contract (the contract's {@code @Ensures} is the lie, not this body). Kept separate so a
     * single contract interface can mirror both a genuine instance method and a fail-on-purpose one.
     */
    public int projectAgain(int amount) {
        return project(amount);
    }
}
