package example.records;

/** A simple record used to demonstrate record deconstruction patterns in {@code instanceof}. */
public record Point(int x, int y) {

    /**
     * Manhattan distance from the origin, computed by deconstructing {@code this} via a record
     * pattern in {@code instanceof}. The {@code instanceof} form lowers to a plain type check plus
     * accessor calls — it does NOT use the {@code SwitchBootstraps.typeSwitch} invokedynamic that a
     * pattern-matching {@code switch} would, so JBMC analyses it soundly.
     */
    public static int manhattan(Object o) {
        if (o instanceof Point(int px, int py)) {
            return Math.abs(px) + Math.abs(py);
        }
        return -1;
    }
}
