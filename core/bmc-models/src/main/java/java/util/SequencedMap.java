package java.util;

/**
 * Minimal BMC model of {@link java.util.SequencedMap} (Java 21+). Declared only so the Java-17 floor
 * build can resolve the type in the loud-stub signatures of {@link Collections} (e.g.
 * {@code unmodifiableSequencedMap}); no members — the modeled surface lives on the concrete maps.
 */
public interface SequencedMap<K, V> extends Map<K, V> {
}
