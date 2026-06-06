package example.jakarta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * An outer bean that nests a validated {@link Address} behind {@code @Valid}: the cascade makes the
 * Address's own {@code @Min}/{@code @Max} part of "a valid Customer". Without the cascade, a proof
 * over a symbolic Customer would quietly range over Addresses the validator rejects.
 */
public class Customer {

    /** Cascade target: the Address must itself be valid. Null passes (only @NotNull would reject). */
    @Valid
    @NotNull
    public Address address;

    /** A non-blank display name — rejects null AND all-whitespace (the @NotBlank asymmetry). */
    @NotBlank
    public String name;
}
