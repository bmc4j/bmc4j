package example.records;

/**
 * A record with a primitive and a reference (String) component, used to exercise the synthesized
 * record {@code equals} / {@code hashCode} that bmc4j desugars from the {@code ObjectMethods}
 * invokedynamic bootstrap. javac emits {@code equals}, {@code hashCode} and {@code toString} for a
 * record as {@code invokedynamic} call sites linked to {@code java.lang.runtime.ObjectMethods} —
 * JBMC links those to unconstrained results, so bmc4j rewrites the equals/hashCode sites to sound
 * field-by-field stand-ins during analysis.
 */
public record User(int id, String name) {
}
