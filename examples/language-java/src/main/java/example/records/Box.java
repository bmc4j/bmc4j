package example.records;

/**
 * A record with a NON-String reference component. Its {@code toString} stays an {@code
 * ObjectMethods} invokedynamic: the desugar only rewrites record {@code toString} when every
 * component renders soundly (primitive or String), and {@code inner} is neither — so this is a
 * deliberate RESIDUAL indy, the fixture for the visible-not-trusted demos.
 */
public record Box(Point inner) {
}
