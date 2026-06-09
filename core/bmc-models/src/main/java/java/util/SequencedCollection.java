package java.util;

/**
 * Minimal BMC model of {@link java.util.SequencedCollection} (Java 21+). Declared only so the
 * Java-17 floor build can resolve the type in the loud-stub signatures of {@link Collections}
 * (e.g. {@code unmodifiableSequencedCollection}); the head/tail surface itself is modeled directly
 * on the concrete collections, so this interface carries no members.
 */
public interface SequencedCollection<E> extends Collection<E> {
}
