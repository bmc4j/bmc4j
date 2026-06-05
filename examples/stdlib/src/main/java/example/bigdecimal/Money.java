package example.bigdecimal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money arithmetic with {@code BigDecimal} — exact decimals, proven against bmc4j's model. */
public final class Money {

    private Money() {
    }

    /** Each person's share when splitting a bill evenly, rounded to cents. */
    public static BigDecimal shareOf(BigDecimal total, int people) {
        return total.divide(BigDecimal.valueOf(people), 2, RoundingMode.HALF_UP);
    }

    /** Amount plus tax at {@code rate}, rounded to cents. */
    public static BigDecimal withTax(BigDecimal amount, BigDecimal rate) {
        return amount.add(amount.multiply(rate)).setScale(2, RoundingMode.HALF_UP);
    }
}
