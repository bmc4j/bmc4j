package example.jakarta;

import java.time.Instant;

/** Business logic over a temporally-validated {@link Event}. */
public final class Events {

    private Events() {
    }

    /**
     * Holds for every valid event ONLY because of the SHARED now: {@code signupAt <= now < expiry}
     * forces {@code signupAt < expiry}. With INDEPENDENT nows per field this would be refutable (a
     * late signup vs an early expiry). A green proof here pins that both temporal fields relate to
     * the SAME moment.
     */
    public static boolean signupBeforeExpiry(Event e) {
        return e.signupAt == null || e.expiry == null || e.signupAt.isBefore(e.expiry);
    }

    /**
     * BUG: claims every valid event's {@code createdAt} is before a FIXED instant. A valid event can
     * have any past createdAt, including one after that fixed instant — so this is refutable.
     */
    public static boolean createdBeforeEpochPlusOneDay(Event e) {
        return e.createdAt == null || e.createdAt.isBefore(Instant.ofEpochSecond(86400)); // 1970-01-02
    }
}
