package java.math;

/** BMC model of {@link java.math.RoundingMode} — the standard constants, in JDK order (the ordinal
 *  matters: it mirrors the legacy {@code BigDecimal.ROUND_*} ints). {@link BigDecimal} switches on
 *  these to round soundly. */
public enum RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY
}
