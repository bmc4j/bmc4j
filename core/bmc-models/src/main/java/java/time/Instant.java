package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.Instant} as an epoch-millisecond {@code long}, so
 * temporal logic reduces to integer arithmetic JBMC reasons about precisely.
 *
 * <p>Only the common methods are modeled; time zones, leap seconds and sub-milli
 * precision are out of scope (a model, not a reimplementation). {@code now()} is
 * intentionally not modeled — pass Instants as proof parameters (symbolic inputs).
 *
 * <p><b>Why the 20-member tail is genuinely not-modelable here (deliberate, not an oversight):</b> the
 * epoch-millis backing carries a single {@code long} of milliseconds and NOTHING else, so the entire
 * Instant tail falls into one of three buckets that a millis {@code long} simply cannot represent:
 * (1) <b>sub-millisecond precision</b> — {@code getNano}/{@code plusNanos}/{@code minusNanos}/
 * {@code ofEpochSecond(long,long)} need the nano-of-second field (declined LOUD per-member above);
 * (2) <b>zone / offset / calendar projection</b> — {@code atZone}/{@code atOffset} and the
 * {@code TemporalField}/{@code TemporalUnit}/{@code TemporalAdjuster}/{@code TemporalQuery} plumbing
 * ({@code with}/{@code get}/{@code getLong}/{@code until}/{@code range}/{@code isSupported}/{@code query}/
 * {@code adjustInto}/{@code plus}/{@code minus}(TemporalAmount/long,TemporalUnit)) all require a
 * ZoneId/ZoneOffset or a field-enum the bounded model deliberately doesn't carry; (3) <b>external state /
 * text</b> — {@code now(Clock)} (non-deterministic) and {@code parse}/{@code from}. None can be made
 * sound on a millis {@code long}, so the whole tail stays LOUD under JBMC rather than forcing a
 * lossy/wrong body — reaching any of it is an honest member-named UNKNOWN, never a silent wrong value.
 *
 * <p>It {@code implements java.time.temporal.Temporal} ONLY so an Instant survives the
 * {@code checkcast java.time.temporal.Temporal} the JDK-compiled proof bytecode emits when an Instant
 * is passed to an interface-typed parameter (e.g. {@code Duration.between(Temporal, Temporal)}). Without
 * it that cast fails under JBMC and refutes spuriously ("Dynamic cast check"). The {@code Temporal}
 * abstract methods are NOT modeled — each is a LOUD stub ({@link #fail}) so reaching it is a NAMED
 * UNKNOWN, never a silent nondet: implementing the interface buys only {@code instanceof}, never turns
 * unmodeled temporal plumbing into a fake answer.
 */
@BmcModelTail(reason = "the epoch-millis long carries no nanos, zone/offset, or field-enum, so the tail is genuinely not-modelable: sub-milli precision (nano accessors), zone/offset projection (atZone/atOffset), the TemporalField/Unit/Adjuster/Query plumbing (with/get/getLong/until/query/adjustInto/range/isSupported/plus/minus(TemporalAmount or long,TemporalUnit)), and external-state/text (now(Clock)/parse/from) — all loud under JBMC, never forced")
public final class Instant implements Temporal {

    final long millis;

    private Instant(long millis) {
        this.millis = millis;
    }

    @BmcUnmodelable(reason = "wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters")
    public static Instant now() {
        throw fail("bmc4j: unmodelled member java.time.Instant.now() — wall-clock read is non-deterministic external state — pass Instants as symbolic proof parameters");
    }

    // The epoch-millis backing has no sub-millisecond resolution, so the nanosecond surface
    // (getNano / plusNanos / minusNanos / ofEpochSecond(long, nanoAdjustment)) cannot be modeled
    // soundly — declined LOUD rather than silently dropping precision.

    @BmcUnmodelable(reason = "sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing")
    public int getNano() {
        throw fail("bmc4j: unmodelled member java.time.Instant.getNano() — sub-millisecond resolution — the nano-of-second field can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant plusNanos(long nanosToAdd) {
        throw fail("bmc4j: unmodelled member java.time.Instant.plusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "sub-millisecond resolution — nanos can't be represented on the epoch-millis backing")
    public Instant minusNanos(long nanosToSubtract) {
        throw fail("bmc4j: unmodelled member java.time.Instant.minusNanos(long) — sub-millisecond resolution — nanos can't be represented on the epoch-millis backing");
    }

    @BmcUnmodelable(reason = "the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks")
    public static Instant ofEpochSecond(long epochSecond, long nanoAdjustment) {
        throw fail("bmc4j: unmodelled member java.time.Instant.ofEpochSecond(long,long) — the nanoAdjustment second-overflow normalization needs sub-millisecond resolution the epoch-millis backing lacks");
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Instant ofEpochMilli(long epochMilli) {
        return new Instant(epochMilli);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static Instant ofEpochSecond(long epochSecond) {
        // This model is millis-bounded (narrower than the real Instant's range). Route the
        // seconds->millis scale through a checked multiply so an out-of-bound second count fails
        // LOUDLY (MathBytecode redirects Math.multiplyExact to the loud BmcMath under analysis)
        // rather than silently wrapping to a wrong value.
        return new Instant(Math.multiplyExact(epochSecond, 1000L));
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long toEpochMilli() {
        return millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public long getEpochSecond() {
        // Floor toward negative infinity like the real Instant (seconds + 0..999ms), NOT truncate
        // toward zero: ofEpochMilli(-1).getEpochSecond() is -1, not 0.
        long s = millis / 1000L;
        if (millis % 1000L != 0L && millis < 0L) {
            s--;
        }
        return s;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isBefore(Instant other) {
        return this.millis < other.millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean isAfter(Instant other) {
        return this.millis > other.millis;
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int compareTo(Instant other) {
        return this.millis < other.millis ? -1 : (this.millis == other.millis ? 0 : 1);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant plusMillis(long ms) {
        return new Instant(this.millis + ms);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant minusMillis(long ms) {
        return new Instant(this.millis - ms);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant plusSeconds(long seconds) {
        return new Instant(this.millis + seconds * 1000L);
    }

    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public Instant minusSeconds(long seconds) {
        return new Instant(this.millis - seconds * 1000L);
    }

    // --- Temporal / TemporalAccessor abstract surface: implemented ONLY to make the Instant an
    //     instanceof Temporal (so the proof-site checkcast passes); each is LOUD, never modeled. ---

    @BmcUnmodelable(reason = "the TemporalField query plumbing (which fields an Instant supports) is out of scope for the epoch-millis model")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.Instant.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for the epoch-millis model");
    }

    @BmcUnmodelable(reason = "the TemporalUnit query plumbing (which units an Instant supports) is out of scope for the epoch-millis model")
    @Override
    public boolean isSupported(TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.Instant.isSupported(java.time.temporal.TemporalUnit) — the TemporalUnit query plumbing is out of scope for the epoch-millis model");
    }

    @BmcUnmodelable(reason = "the TemporalField accessor (getLong) is out of scope for the epoch-millis model")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.Instant.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for the epoch-millis model");
    }

    @BmcUnmodelable(reason = "the generic TemporalField setter (with) is out of scope for the epoch-millis model")
    @Override
    public Temporal with(TemporalField field, long newValue) {
        throw fail("bmc4j: unmodelled member java.time.Instant.with(java.time.temporal.TemporalField,long) — the generic TemporalField setter is out of scope for the epoch-millis model");
    }

    @BmcUnmodelable(reason = "the generic TemporalUnit add (plus) is out of scope for the epoch-millis model")
    @Override
    public Temporal plus(long amountToAdd, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.Instant.plus(long,java.time.temporal.TemporalUnit) — the generic TemporalUnit add is out of scope for the epoch-millis model");
    }

    @BmcUnmodelable(reason = "the generic TemporalUnit difference (until) is out of scope for the epoch-millis model")
    @Override
    public long until(Temporal endExclusive, TemporalUnit unit) {
        throw fail("bmc4j: unmodelled member java.time.Instant.until(java.time.temporal.Temporal,java.time.temporal.TemporalUnit) — the generic TemporalUnit difference is out of scope for the epoch-millis model");
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public boolean equals(Object o) {
        return (o instanceof Instant) && ((Instant) o).millis == this.millis;
    }

    @Override
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public int hashCode() {
        return (int) (millis ^ (millis >>> 32));
    }
}
