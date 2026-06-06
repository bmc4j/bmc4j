package example.jakarta;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * A money-shaped validated model. {@code @DecimalMin}/{@code @DecimalMax} bound the amount through
 * the modeled {@code BigDecimal.compareTo}; {@code @Digits} bounds the integer/fraction digit counts.
 */
public class Money {

    /** At least one cent (inclusive), at most 1000 (inclusive). */
    @DecimalMin("0.01")
    @DecimalMax("1000.00")
    public BigDecimal amount;

    /** A strict lower bound: must be > 0 (inclusive=false), so exactly 0 is invalid. */
    @DecimalMin(value = "0", inclusive = false)
    public BigDecimal fee;

    /** At most 5 integer digits and 2 fraction digits — e.g. 99999.99 is the largest valid value. */
    @Digits(integer = 5, fraction = 2)
    public BigDecimal price;
}
