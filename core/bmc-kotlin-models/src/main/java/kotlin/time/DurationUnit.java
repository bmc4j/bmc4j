package kotlin.time;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean model of Kotlin's {@code kotlin.time.DurationUnit} enum. The real enum carries a
 * {@code java.util.concurrent.TimeUnit} field used for {@code Long}-precision unit conversion; this
 * model carries the equivalent nanosecond scale directly so the {@link Duration} model's conversions
 * are self-contained (no dependency on {@code TimeUnit}, which is itself a JBMC-stubbed enum here).
 *
 * <p>Analysis-facing code references only the enum constants (e.g.
 * {@code Duration.Companion.getSeconds} passes {@code DurationUnit.SECONDS}); the {@code nanosScale}
 * field is internal to the model.
 */
@BmcModelConforms("Kotlin stdlib model — @BmcProof (model-conformance-proofs); facade/value model, audited at class level")
public enum DurationUnit {
    NANOSECONDS(1L),
    MICROSECONDS(1_000L),
    MILLISECONDS(1_000_000L),
    SECONDS(1_000_000_000L),
    MINUTES(60_000_000_000L),
    HOURS(3_600_000_000_000L),
    DAYS(86_400_000_000_000L);

    final long nanosScale;

    DurationUnit(long nanosScale) {
        this.nanosScale = nanosScale;
    }
}
