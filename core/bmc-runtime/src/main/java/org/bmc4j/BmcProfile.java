package org.bmc4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Emit a per-stage <b>performance breakdown</b> for a proof's engine run, alongside its
 * pass/fail verdict. Add it next to {@link BmcProof} on a proof you want to diagnose — a slow or
 * timing-out proof — and bmc4j prints a readable table to the test's stdout/report showing where
 * the engine's time and formula size went.
 *
 * <pre>{@code
 * @BmcProof(unwind = 64, timeoutSeconds = 5, expect = Verdict.TIMEOUT)
 * @BmcProfile
 * void heavy_proof_profile() { ... }
 * }</pre>
 *
 * <p>It is purely <b>additive</b>: it never changes the verdict or the pass/fail outcome — it only
 * surfaces extra diagnostic output. The breakdown is parsed from the verbose engine stream the
 * harness already captures (no second engine run), so the normal, non-profiled path is unaffected.
 * The most valuable case is a proof that <em>times out</em>: the profile parses what was captured up
 * to the kill, which tells you whether the solver was ever reached (a symex timeout never reaches
 * SAT) and which method's loops dominated the unwinding.
 *
 * <p>The breakdown reports, when present in the engine's output:
 * <ul>
 *   <li><b>Phase timing</b> — symbolic execution vs SSA/postprocessing vs solving, and crucially
 *       <b>whether the SAT/SMT solver was ever reached</b> (the single most useful signal on a
 *       timeout).</li>
 *   <li><b>Loop-unwinding breakdown by method</b> — a "top offenders" list of which methods' loops
 *       were unwound and how many times, pinpointing the hot method.</li>
 *   <li><b>Formula size stats</b> — program-expression size (steps), VCCs generated/remaining, and
 *       SAT variables/clauses when the solver was reached.</li>
 * </ul>
 *
 * <p>Default off: only a proof annotated {@code @BmcProfile} produces the breakdown.
 *
 * <p>A proof whose verdict is served from the verdict cache has no fresh engine run to profile;
 * {@code @BmcProfile} forces a live run so the breakdown is always produced (the verdict is
 * unchanged — the cache short-circuit is a performance optimization, not a correctness one).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BmcProfile {
}
