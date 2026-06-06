package example.jakarta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** An element bean validated inside {@code List<@Valid OrderLine>}. */
public class OrderLine {

    /** Quantity 1..100. The container cascade brings this into a proof over a symbolic Order. */
    @Min(1)
    @Max(100)
    public int quantity;
}
