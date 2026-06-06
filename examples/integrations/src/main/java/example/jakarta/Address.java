package example.jakarta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A nested validated bean. Its own constraints only reach a proof when the cascade is honored. */
public class Address {

    /** A US-style ZIP: 00000..99999. */
    @Min(0)
    @Max(99999)
    public int zip;
}
