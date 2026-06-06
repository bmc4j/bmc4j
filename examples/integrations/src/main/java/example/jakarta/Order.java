package example.jakarta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Container-element constraints — Jakarta 3.0 puts constraints INSIDE generics ({@code TYPE_USE}).
 * The generated {@code assumeValid} walks each list with a bounded loop (capped by {@code @Size(max)}
 * when present, else a default cap surfaced as a processor NOTE).
 */
public class Order {

    /** Every score is at least 1; the list is capped at 3 so the element loop is tightly bounded. */
    @Size(max = 3)
    public List<@Min(1) Integer> scores;

    /** Every line is itself valid — the container @Valid cascade (composes the field cascade). */
    @Size(max = 3)
    public List<@Valid OrderLine> lines;
}
