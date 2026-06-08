package java.time.chrono;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.time.DateTimeException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * JBMC model of {@link java.time.chrono.IsoEra} — the two-constant ISO era enum (BCE=0, CE=1). The
 * modeled surface is {@code getValue} (the ordinal, since BCE=0/CE=1) and the {@code of(int)} factory
 * (loud {@link DateTimeException} out of [0,1], like the JDK). Validated bit-for-bit by the differential
 * suite vs the real JDK.
 *
 * <p>It {@code implements java.time.chrono.Era} — the real JDK interface — ONLY so an IsoEra survives
 * the {@code checkcast} the JDK-compiled proof bytecode emits when one is passed to an Era-typed
 * parameter (the "Dynamic cast check" artifact). The remaining Era abstract methods are NOT modeled —
 * each is a LOUD stub so reaching it is a NAMED UNKNOWN, never a silent nondet.
 *
 * <p>NB: NO class-level {@code @BmcModelTail} here (same enum reason as {@link java.time.DayOfWeek}):
 * the tail loud-body synthesis would override {@code java.lang.Enum}'s final members and break the enum.
 * The remaining Era surface ({@code get}/{@code query}/{@code range} are interface defaults;
 * {@code getDisplayName} is out of scope) needs no synthesized body — this is a class-level COVERED enum,
 * and the required Era abstracts ({@code getValue}/{@code isSupported}/{@code getLong}/{@code adjustInto})
 * are modeled or the loud stubs below.
 */
public enum IsoEra implements Era {
    BCE,
    CE;

    /** Numeric era value: BCE=0, CE=1 (the ordinal). */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    @Override
    public int getValue() {
        return ordinal();
    }

    /** Factory from the era value; loud {@link DateTimeException} out of [0,1], like the JDK. */
    @BmcModelConforms("differential (TimeConformanceTest) + @BmcProof (proofs.time)")
    public static IsoEra of(int isoEra) {
        switch (isoEra) {
            case 0:
                return BCE;
            case 1:
                return CE;
            default:
                throw new DateTimeException("Invalid era: " + isoEra);
        }
    }

    // --- Era abstract surface: implemented ONLY to make an IsoEra an instanceof Era (so the proof-site
    //     checkcast passes); each is LOUD, never modeled. ---

    @BmcUnmodelable(reason = "the TemporalField query plumbing (isSupported) is out of scope for this BCE/CE enum model")
    @Override
    public boolean isSupported(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.chrono.IsoEra.isSupported(java.time.temporal.TemporalField) — the TemporalField query plumbing is out of scope for this BCE/CE enum model");
    }

    @BmcUnmodelable(reason = "the TemporalField accessor (getLong) is out of scope for this BCE/CE enum model")
    @Override
    public long getLong(TemporalField field) {
        throw fail("bmc4j: unmodelled member java.time.chrono.IsoEra.getLong(java.time.temporal.TemporalField) — the TemporalField accessor is out of scope for this BCE/CE enum model");
    }

    @BmcUnmodelable(reason = "applying an IsoEra to a Temporal (adjustInto) is out of scope for this BCE/CE enum model")
    @Override
    public Temporal adjustInto(Temporal temporal) {
        throw fail("bmc4j: unmodelled member java.time.chrono.IsoEra.adjustInto(java.time.temporal.Temporal) — applying an IsoEra to a Temporal is out of scope for this BCE/CE enum model");
    }
}
