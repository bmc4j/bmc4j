package kotlin.random;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcNotModelled;
import org.cprover.CProver;

/**
 * Clean BMC model of Kotlin's {@code kotlin.random.Random} — the "prove for every random outcome" model,
 * mirroring the {@link java.util.Random} one. Replaces the real {@code kotlin.random.Random} on JBMC's
 * analysis classpath only.
 *
 * <h2>The two contracts, kept separate (identical reasoning to {@code java.util.Random})</h2>
 * <ul>
 *   <li><b>The range / distribution contract IS the ideal BMC model.</b> {@code nextInt(until)} returns
 *       some value in {@code [0, until)}, {@code nextBoolean()} returns some {@code boolean}, etc. Each
 *       draw is a <em>nondeterministic</em> value within its documented range, so a proof holds for
 *       <em>EVERY</em> outcome the RNG could produce — strictly stronger than sampling.</li>
 *   <li><b>The seeded-determinism contract is NOT modelable as nondet.</b>
 *       {@code Random(42).nextInt() == Random(42).nextInt()} is <em>true</em> in reality; nondet would
 *       FALSELY REFUTE it. Reproducing it needs the exact {@code XorWowRandom} algorithm — out of
 *       scope.</li>
 * </ul>
 *
 * <h2>The clean separation</h2>
 * The abstract real type is {@code kotlin.random.Random} (+ the {@code Random.Default} object and the
 * {@code XorWowRandom} impl). bmc4j models at the level the proof bytecode actually calls: a proof writes
 * {@code Random.Default.nextInt(6)}, which reads the static {@code Default} field (type
 * {@code Random$Default}) and virtual-dispatches the draw. So this model is a <em>concrete</em>
 * {@code Random} whose draw methods are nondet-in-range, with {@link Default} a trivial subclass that
 * inherits them (the dispatch target). The {@code XorWowRandom}/{@code AbstractPlatformRandom} impls are
 * never modeled — a proof never names them; it goes through {@code Random.Default}.
 *
 * <p>The <b>seeded</b> surface — the top-level {@code Random(seed)} factory in
 * {@code kotlin.random.RandomKt} — is a LOUD stub (see {@code RandomKt}), so a seeded / reproducibility
 * proof reports {@code UNKNOWN} (honest) instead of a false refutation. You cannot reach a seeded
 * {@code Random} without that loud factory, so the false-refutation can never arise.
 *
 * <p>Per the no-{@code double} convention, {@code nextDouble}/{@code nextFloat} are loud; together with
 * the {@code nextBytes} family and the value-returning bit helpers they live in the {@link BmcModelTail}.
 * Reaching any is a NAMED, LOUD {@code UNKNOWN}, never a silent nondet stub.
 */
@BmcModelTail(reason = "exotic kotlin.random.Random remainder — the double-valued draws "
        + "(nextDouble/nextDouble(bound)/nextDouble(from,until)/nextFloat, no-double policy) and the "
        + "nextBytes(...) family (byte-array fill); loud (UNKNOWN) under JBMC if reached")
public class Random {

    /**
     * The {@code Random.Default} companion object the bytecode reads. Holds a trivial {@link Default}
     * instance whose draws are the inherited nondet-in-range bodies below — exactly the dispatch target
     * of {@code Random.Default.nextInt(...)}.
     */
    public static final Default Default = new Default();

    /**
     * The single real abstract method. The integer draws below don't go through it (they're nondet
     * directly), but it's modeled as nondet too so a custom {@code Random} subclass calling
     * {@code nextBits} stays sound rather than hitting an unmodeled stub.
     */
    @BmcModelConforms("nondet bits — model proofs (RandomLaws)")
    public int nextBits(int bitCount) {
        return CProver.nondetInt();
    }

    /** Any {@code int} — the full 32-bit domain. */
    @BmcModelConforms("nondet-in-range — proven for every outcome (RandomLaws model proofs)")
    public int nextInt() {
        return CProver.nondetInt();
    }

    /**
     * Some value in {@code [0, until)} — for EVERY outcome. Throws {@link IllegalArgumentException} on an
     * empty range ({@code until <= 0}), like the stdlib's {@code checkRangeBounds}.
     */
    @BmcModelConforms("nondet-in-range [0,until) + range check — RandomLaws model proofs")
    public int nextInt(int until) {
        if (until <= 0) {
            throw new IllegalArgumentException("Random range is empty: [0, " + until + ").");
        }
        return anyIntInRange(0, until - 1);
    }

    /**
     * Some value in {@code [from, until)} — for every outcome. Throws {@link IllegalArgumentException} on
     * an empty range ({@code from >= until}), like the stdlib.
     */
    @BmcModelConforms("nondet-in-range [from,until) + range check — RandomLaws model proofs")
    public int nextInt(int from, int until) {
        if (from >= until) {
            throw new IllegalArgumentException("Random range is empty: [" + from + ", " + until + ").");
        }
        return anyIntInRange(from, until - 1);
    }

    /** Any {@code long} — the full 64-bit domain. */
    @BmcModelConforms("nondet-in-range — proven for every outcome (RandomLaws model proofs)")
    public long nextLong() {
        return CProver.nondetLong();
    }

    /** Some value in {@code [0, until)} — for every outcome. Throws on an empty range. */
    @BmcModelConforms("nondet-in-range [0,until) + range check — RandomLaws model proofs")
    public long nextLong(long until) {
        if (until <= 0) {
            throw new IllegalArgumentException("Random range is empty: [0, " + until + ").");
        }
        return anyLongInRange(0, until - 1);
    }

    /** Some value in {@code [from, until)} — for every outcome. Throws on an empty range. */
    @BmcModelConforms("nondet-in-range [from,until) + range check — RandomLaws model proofs")
    public long nextLong(long from, long until) {
        if (from >= until) {
            throw new IllegalArgumentException("Random range is empty: [" + from + ", " + until + ").");
        }
        return anyLongInRange(from, until - 1);
    }

    /** Either {@code true} or {@code false} — both outcomes considered. */
    @BmcModelConforms("nondet boolean — proven true-or-false for every outcome (RandomLaws model proofs)")
    public boolean nextBoolean() {
        return CProver.nondetBoolean();
    }

    /** LOUD: {@code double} draws are out of scope (no-double policy) and need the real bit math. */
    @BmcNotModelled(reason = "double draw — no-double policy; the IEEE-754 mapping needs the real bit math")
    public double nextDouble() {
        throw fail("bmc4j: unmodelled member kotlin.random.Random.nextDouble() — no-double policy");
    }

    // --- internal nondet-in-range helpers ------------------------------------------------------------

    private static int anyIntInRange(int lo, int hi) {
        int v = CProver.nondetInt();
        CProver.assume(v >= lo && v <= hi);
        return v;
    }

    private static long anyLongInRange(long lo, long hi) {
        long v = CProver.nondetLong();
        CProver.assume(v >= lo && v <= hi);
        return v;
    }

    /**
     * Model of {@code kotlin.random.Random.Default} — the companion object a proof reads via the static
     * {@code Default} field. The draws are the dispatch target of {@code Random.Default.nextInt(...)}.
     *
     * <p>Each draw is an EXPLICIT override delegating to the {@link Random} body rather than relying on
     * pure inheritance: JBMC's devirtualization needs a body ON the dispatched-to class — an empty
     * subclass leaves the inherited draw as "no body for callee", silently stubbed to unconstrained
     * nondet (a spurious refutation). This is the same "composition/explicit-override, not bare
     * subclassing" precedent the {@code kotlin.time.Duration} / collection-facade models established.
     * (Inner class — outside the per-member audit surface — so the overrides carry no annotations.)
     */
    public static final class Default extends Random {

        @Override
        public int nextBits(int bitCount) {
            return super.nextBits(bitCount);
        }

        @Override
        public int nextInt() {
            return super.nextInt();
        }

        @Override
        public int nextInt(int until) {
            return super.nextInt(until);
        }

        @Override
        public int nextInt(int from, int until) {
            return super.nextInt(from, until);
        }

        @Override
        public long nextLong() {
            return super.nextLong();
        }

        @Override
        public long nextLong(long until) {
            return super.nextLong(until);
        }

        @Override
        public long nextLong(long from, long until) {
            return super.nextLong(from, until);
        }

        @Override
        public boolean nextBoolean() {
            return super.nextBoolean();
        }

        @Override
        public double nextDouble() {
            return super.nextDouble();
        }
    }
}
