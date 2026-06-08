package java.time;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import java.io.Serializable;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.time.zone.ZoneRules;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bmc4j.models.audit.BmcModelTail;
import org.bmc4j.models.audit.BmcUnmodelable;

/**
 * Minimal JBMC model of the abstract {@link java.time.ZoneId} base — present ONLY so the concrete
 * {@link ZoneOffset} model can {@code extend} it (matching the real class hierarchy) and so a ZoneOffset
 * is an {@code instanceof ZoneId} where the JDK-compiled proof bytecode expects one. bmc4j models offsets
 * (the {@link ZoneOffset} total-seconds wrapper), NOT named regions / DST rules, so this base carries no
 * region machinery: the abstract {@code getId}/{@code getRules} are supplied by {@link ZoneOffset}, and
 * the named-region factories/accessors are LOUD stubs ({@code now}-free, region-free). Reaching any stub
 * is a NAMED UNKNOWN, never a silent nondet.
 */
@BmcModelTail(reason = "named-region / DST-rule machinery (systemDefault/getAvailableZoneIds/the of(String[,Map]) and ofOffset region factories/normalized/from/getDisplayName) is out of scope for the offset-only zone model; all loud under JBMC")
public abstract class ZoneId implements Serializable {

    ZoneId() {
    }

    /** Supplied by {@link ZoneOffset} (the only concrete subclass modeled). */
    public abstract String getId();

    /** Supplied by {@link ZoneOffset} (a fixed-offset rule). */
    public abstract ZoneRules getRules();

    @BmcUnmodelable(reason = "the system default zone is non-deterministic external state — out of scope for the offset-only zone model")
    public static ZoneId systemDefault() {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.systemDefault() — the system default zone is non-deterministic external state — out of scope for the offset-only zone model");
    }

    @BmcUnmodelable(reason = "the available-zone-ids set is region machinery — out of scope for the offset-only zone model")
    public static Set<String> getAvailableZoneIds() {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.getAvailableZoneIds() — the available-zone-ids set is region machinery — out of scope for the offset-only zone model");
    }

    @BmcUnmodelable(reason = "named-region parsing is out of scope; build offsets via ZoneOffset.ofTotalSeconds/ofHours")
    public static ZoneId of(String zoneId) {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.of(java.lang.String) — named-region parsing is out of scope; build offsets via ZoneOffset.ofTotalSeconds/ofHours");
    }

    @BmcUnmodelable(reason = "named-region parsing with aliases is out of scope for the offset-only zone model")
    public static ZoneId of(String zoneId, Map<String, String> aliasMap) {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.of(java.lang.String,java.util.Map) — named-region parsing with aliases is out of scope for the offset-only zone model");
    }

    @BmcUnmodelable(reason = "the prefix+offset region factory is out of scope; build offsets via ZoneOffset.ofTotalSeconds/ofHours")
    public static ZoneId ofOffset(String prefix, ZoneOffset offset) {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.ofOffset(java.lang.String,java.time.ZoneOffset) — the prefix+offset region factory is out of scope; build offsets via ZoneOffset.ofTotalSeconds/ofHours");
    }

    @BmcUnmodelable(reason = "extracting a ZoneId from a TemporalAccessor is out of scope for the offset-only zone model")
    public static ZoneId from(TemporalAccessor temporal) {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.from(java.time.temporal.TemporalAccessor) — extracting a ZoneId from a TemporalAccessor is out of scope for the offset-only zone model");
    }

    @BmcUnmodelable(reason = "localized zone display text is out of scope for the offset-only zone model")
    public String getDisplayName(TextStyle style, Locale locale) {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.getDisplayName(java.time.format.TextStyle,java.util.Locale) — localized zone display text is out of scope for the offset-only zone model");
    }

    @BmcUnmodelable(reason = "region-to-offset normalization is out of scope; a ZoneOffset is already normalized (see ZoneOffset.normalized)")
    public ZoneId normalized() {
        throw fail("bmc4j: unmodelled member java.time.ZoneId.normalized() — region-to-offset normalization is out of scope; a ZoneOffset is already normalized (see ZoneOffset.normalized)");
    }
}
