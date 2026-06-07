package example.instance;

/**
 * An identical pure instance method with <b>no contract</b>. Calls to it are never summarized, so a
 * proof that uses it must inline the real loop — the instance-method baseline that shows what the
 * contract buys (the {@code basics} concept makes the same point over a static method).
 */
public final class AccountNaive {

    private final int balance;

    public AccountNaive(int balance) {
        this.balance = balance;
    }

    public int balance() {
        return balance;
    }

    public int project(int amount) {
        int result = balance;
        for (int i = 0; i < amount; i++) {
            result += 1;
        }
        return result;
    }
}
