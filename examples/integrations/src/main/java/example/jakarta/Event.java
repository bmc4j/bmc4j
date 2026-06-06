package example.jakarta;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Instant;

/**
 * A validated model with temporal constraints. {@code @Past}/{@code @Future} compare against the
 * validation moment; the generated {@code assumeValid} pins ONE symbolic "now" shared by every
 * temporal field — so {@code createdAt} and {@code signupAt} are both in the past OF THE SAME
 * moment, exactly as the validator meant.
 *
 * <p>Modeled as {@link Instant} (epoch-millis backed): {@code isBefore}/{@code isAfter} become exact
 * integer comparisons JBMC reasons about precisely even for symbolic instances. ({@code LocalDate}'s
 * symbolic {@code isBefore} hits a JBMC dynamic-cast artifact — see the time models' coverage notes —
 * so the demo uses the {@code Instant} surface that verifies symbolically.)
 */
public class Event {

    /** Must be strictly before now. */
    @Past
    public Instant createdAt;

    /** Must be before-or-equal to now. */
    @PastOrPresent
    public Instant signupAt;

    /** Must be strictly after now. */
    @Future
    public Instant expiry;
}
