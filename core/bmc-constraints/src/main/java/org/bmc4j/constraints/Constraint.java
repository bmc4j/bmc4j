package org.bmc4j.constraints;

/**
 * A single fact that must hold for a value to be valid, rendered as a Java boolean
 * expression. The expression is emitted into generated {@code assumeValid} code as
 * {@code Bmc.assume(<expression>)}, so it must be reflection-free and analyzable by
 * JBMC.
 */
@FunctionalInterface
public interface Constraint {

    /**
     * @param accessor a Java expression that reads the value, e.g. {@code obj.age}
     *                 or {@code obj.getName()}
     * @return a Java boolean expression that is true exactly when the value is valid
     */
    String toExpression(String accessor);
}
