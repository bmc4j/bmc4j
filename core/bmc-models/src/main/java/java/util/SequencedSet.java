package java.util;

/**
 * Minimal BMC model of {@link java.util.SequencedSet} (Java 21+). Declared only so the Java-17 floor
 * build can resolve the type in the loud-stub signatures of {@link Collections} (e.g.
 * {@code unmodifiableSequencedSet}); no members — the modeled surface lives on the concrete sets.
 */
public interface SequencedSet<E> extends SequencedCollection<E>, Set<E> {
}
