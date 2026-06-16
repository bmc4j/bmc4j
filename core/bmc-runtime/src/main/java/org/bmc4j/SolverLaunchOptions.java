package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pass raw extra command-line options to the EXTERNAL SAT solver (e.g. the bundled kissat) for one
 * {@link BmcProof} method.
 *
 * <p>Where {@link JbmcOptions} tunes the <em>jbmc</em> process, this tunes the <em>SAT solver process
 * that jbmc shells out to</em>. The {@link #value()} string is tokenized on whitespace and handed to
 * the solver ahead of the DIMACS file, so the solver runs as {@code kissat <options> <dimacs>}. Use it
 * to pass solver-specific tuning flags such as {@code --shrink}.
 *
 * <pre>{@code
 * @BmcProof(solver = "kissat")
 * @SolverLaunchOptions("--shrink")
 * void numeric_invariant_holds() { ... }
 * }</pre>
 *
 * <p><b>Only applies when an external SAT solver is actually in use.</b> bmc4j routes a proof to the
 * external SAT solver only when it is proven text-free (or under {@code StringMode.CHAR_ARRAY_MODEL}).
 * A proof running on the built-in MiniSat, an SMT backend (z3/boolector/cvc4/cvc5), or jbmc's string
 * refinement does NOT spawn an external SAT solver, so these options have nowhere to go: they are a
 * no-op and bmc4j prints a one-line warning rather than silently dropping them or corrupting an
 * unrelated solver argument.
 *
 * <p><b>Mechanism.</b> CBMC executes the {@code --external-sat-solver} value as a single executable
 * (via {@code execvp} / {@code CreateProcessW}, no shell, no whitespace splitting) and appends only the
 * DIMACS path; it offers no way to pass extra solver arguments. To inject these options bmc4j generates
 * a tiny wrapper script that execs {@code <solver> <options> "$@"} and points {@code --external-sat-solver}
 * at the wrapper. The options are therefore attached soundly, independent of the solver binary.
 *
 * <p>It is a DELIBERATELY UNGUARDED escape hatch: the tokens are passed through with no validation and
 * no soundness checks. If a custom option causes a proof to pass, the user owns that result.
 *
 * <p>The options string is part of the verdict-cache key, so setting or changing it forces a fresh
 * engine run. A proof with no {@code @SolverLaunchOptions} keys identically to one without the
 * annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SolverLaunchOptions {

    /** Raw options passed to the external SAT solver for this proof, tokenized on whitespace. */
    String value();
}
