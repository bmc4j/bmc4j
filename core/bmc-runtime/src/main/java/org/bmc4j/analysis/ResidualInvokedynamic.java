package org.bmc4j.analysis;

/**
 * The residual-invokedynamic marker. {@link org.bmc4j.engine.ResidualIndyBytecode} replaces every
 * {@code invokedynamic} the desugar passes left behind with an {@code invokestatic} to a method of
 * this class named {@code <indyName>__<bootstrapOwner>} (e.g. {@code enumSwitch__SwitchBootstraps}).
 *
 * <p><b>This class deliberately declares NO such methods — ever.</b> The class itself must exist on
 * the analysis classpath so JBMC takes its standard missing-METHOD path for the call: a nondet stub
 * with an opaque-symbol report (which the stub policy then footnotes / escalates), and no
 * exception edge. A missing CLASS is handled differently by the engine (an unknown-class throw
 * edge), which would spuriously refute proofs that merely reach a residual site. Adding a method
 * body here would silently change residual-indy semantics for every proof — don't.
 */
public final class ResidualInvokedynamic {

    private ResidualInvokedynamic() {
    }
}
