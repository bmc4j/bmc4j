package example.jakarta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An ordinary validated model. Its annotations become proof preconditions. */
public class User {

    @Min(0)
    @Max(120)
    public int age;

    @NotNull
    @Size(min = 3, max = 20)
    public String name;

    /**
     * A BOXED numeric without {@code @NotNull}: per jakarta semantics, {@code null} is a VALID
     * value here ({@code @Min} only constrains non-null values) — so the generated assume must
     * keep null in the proof domain. The {@code bonus} demos pin exactly that.
     */
    @Min(0)
    public Integer loyaltyPoints;

    /** A primitive boolean constraint — @AssertTrue becomes a plain assume on the field. */
    @jakarta.validation.constraints.AssertTrue
    public boolean termsAccepted;
}
