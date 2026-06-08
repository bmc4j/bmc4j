package kotlin.text;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcNotNeeded;
import org.bmc4j.models.audit.BmcUnmodelable;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import kotlin.jvm.functions.Function1;
import kotlin.ranges.IntRange;

/**
 * Clean model of Kotlin's {@code kotlin.text.StringsKt} multifile facade. This class carries the SAME
 * fully-qualified name as the real stdlib facade, so on JBMC's analysis classpath it SHADOWS it: every
 * {@code String}/{@code CharSequence} extension a Kotlin call site emits ({@code "x".trim()} →
 * {@code StringsKt.trim((CharSequence)"x")}) binds here. An UN-modeled facade member is therefore a
 * silent JBMC nondet stub (the recurring facade disease), so the bulk of the bounded char-array
 * transforms are modeled here directly over the SOUND {@code java.lang.String} primitives JBMC's string
 * refinement handles ({@code length}/{@code charAt}/{@code substring}/{@code indexOf}/{@code replace};
 * see {@code proofs.strings.StringLaws}) — never the JBMC-unsound {@code repeat}/{@code strip}/{@code
 * isBlank} JDK ops, and never via a virtual {@code CharIterator}: a concrete {@code String} is obtained
 * with {@code .toString()} and walked BY INDEX.
 *
 * <p><b>buildString</b> is an INLINE stdlib function; from a Kotlin call site its body lands in the
 * caller (allocate {@code StringBuilder}, run the builder lambda, {@code toString()}) — all already
 * modeled. These facade JVM methods are the NON-inline / Java reach and mirror that shape; the
 * capacity-hint overload ignores the hint (the bounded StringBuilder backing is fixed-size — sound,
 * matching the collection builders' mapCapacity precedent).
 *
 * <p><b>Soundness conventions.</b> Every modeled op normalizes its {@code CharSequence}/{@code String}
 * receiver to a concrete {@code String s = cs.toString()} and then uses only by-index {@code charAt} /
 * {@code length} / {@code substring} (the sound primitives). The default (no-case-flag) overloads are
 * modeled; the {@code ignoreCase} overloads model the {@code false} branch exactly and route the
 * {@code true} branch through ASCII-only case folding (documented per method) — never locale tables.
 *
 * <p><b>Genuine walls</b> carry a loud {@link BmcUnmodelable} stub (reaching one demotes to a
 * member-named UNKNOWN, never a silent havoc): regex ops (the regex engine), locale/full-Unicode case
 * mapping (locale tables), number parse/format that hits dtoa or locale tables, and charset/encoding.
 * The higher-order / collection-/sequence-returning remainder is now fully classified: the non-inline
 * collection/sequence/scan/parse/indent ops are modeled directly, and the inline higher-order ops
 * (map/filter/fold/associate/group/... + the *OrThrow / append* inline helpers) carry a class-level
 * {@link BmcNotNeeded} waiver — kotlinc inlines their bodies into the caller, where JBMC analyzes them
 * soundly, so the facade JVM method is never called and needs no model. No class-level model-tail
 * remainder is left.
 */
// Every real StringsKt facade member is now CLASSIFIED (no class-level @BmcModelTail remainder):
//   - modeled (@BmcModelConforms) above: the bounded char-array transforms + the non-inline
//     collection/sequence/scan/parse/indent ops and their $default bridges;
//   - @BmcUnmodelable below: the genuine walls (regex, locale/Unicode case, dtoa/locale number
//     parse, charset);
//   - @BmcNotNeeded here: the inline higher-order ops (map/filter/fold/... and the *OrThrow /
//     append* inline helpers). kotlinc inlines their bodies into the caller, where JBMC analyzes
//     them directly and soundly (the same green-if-reached path proven by the kotlincollections
//     laws); the facade JVM method is never called from a Kotlin call site, so no model is needed.
    @BmcNotNeeded(member = "all(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "any(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "appendElement(java.lang.Appendable, java.lang.Object, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "appendRange(java.lang.Appendable, java.lang.CharSequence, int, int)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "appendln(java.lang.Appendable)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "appendln(java.lang.StringBuilder)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associate(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateBy(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateBy(java.lang.CharSequence, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateByTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateByTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateWith(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "associateWithTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "chunked(java.lang.CharSequence, int, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "chunkedSequence(java.lang.CharSequence, int, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "count(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "dropLastWhile(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "dropLastWhile(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "dropWhile(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "dropWhile(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filter(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filter(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterIndexed(java.lang.String, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterIndexedTo(java.lang.CharSequence, java.lang.Appendable, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterNot(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterNot(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterNotTo(java.lang.CharSequence, java.lang.Appendable, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "filterTo(java.lang.CharSequence, java.lang.Appendable, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "first(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "firstOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "flatMap(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "flatMapTo(java.lang.CharSequence, java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "fold(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "foldIndexed(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "foldRight(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "foldRightIndexed(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "forEach(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "forEachIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "groupBy(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "groupBy(java.lang.CharSequence, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "groupByTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "groupByTo(java.lang.CharSequence, java.util.Map, kotlin.jvm.functions.Function1, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "groupingBy(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "indexOfFirst(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "indexOfLast(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "last(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "lastOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "map(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapIndexedNotNull(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapIndexedNotNullTo(java.lang.CharSequence, java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapIndexedTo(java.lang.CharSequence, java.util.Collection, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapNotNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapNotNullTo(java.lang.CharSequence, java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "mapTo(java.lang.CharSequence, java.util.Collection, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "maxByOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "maxByOrThrow(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "maxOrThrow(java.lang.CharSequence)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "maxWithOrThrow(java.lang.CharSequence, java.util.Comparator)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "minByOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "minByOrThrow(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "minOrThrow(java.lang.CharSequence)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "minWithOrThrow(java.lang.CharSequence, java.util.Comparator)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "none(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "onEach(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "onEachIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "partition(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "partition(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduce(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceIndexedOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceRight(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceRightIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceRightIndexedOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "reduceRightOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "runningFold(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "runningFoldIndexed(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "runningReduce(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "runningReduceIndexed(java.lang.CharSequence, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "scan(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "scanIndexed(java.lang.CharSequence, java.lang.Object, kotlin.jvm.functions.Function3)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "single(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "singleOrNull(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "skipWhile(java.lang.String, int, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "sumBy(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "sumByDouble(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "takeLastWhile(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "takeLastWhile(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "takeWhile(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "takeWhile(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trim(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trim(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trimEnd(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trimEnd(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trimStart(java.lang.CharSequence, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "trimStart(java.lang.String, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "windowed(java.lang.CharSequence, int, int, boolean, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "windowedSequence(java.lang.CharSequence, int, int, boolean, kotlin.jvm.functions.Function1)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "zip(java.lang.CharSequence, java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
    @BmcNotNeeded(member = "zipWithNext(java.lang.CharSequence, kotlin.jvm.functions.Function2)", reason = "inline — kotlinc inlines the body into the caller, where JBMC analyzes it directly; the facade JVM method is never called from a Kotlin call site")
public final class StringsKt {

    private StringsKt() {
    }

    // ===================================================================================================
    // buildString (inline-shape mirror; non-inline / Java reach)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String buildString(Function1<? super StringBuilder, kotlin.Unit> builderAction) {
        StringBuilder sb = new StringBuilder();
        builderAction.invoke(sb);
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String buildString(int capacity, Function1<? super StringBuilder, kotlin.Unit> builderAction) {
        StringBuilder sb = new StringBuilder();
        builderAction.invoke(sb);
        return sb.toString();
    }

    // ===================================================================================================
    // Indices / size queries
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int getLastIndex(CharSequence cs) {
        return cs.toString().length() - 1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static IntRange getIndices(CharSequence cs) {
        return new IntRange(0, cs.toString().length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean isBlank(CharSequence cs) {
        String s = cs.toString();
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ===================================================================================================
    // first / last / single / getOrNull (no-predicate element access; by-index, never an iterator)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char first(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        return s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char last(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        return s.charAt(s.length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character firstOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 0 ? null : s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character lastOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 0 ? null : s.charAt(s.length() - 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char single(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        if (s.length() != 1) {
            throw new IllegalArgumentException("Char sequence has more than one element.");
        }
        return s.charAt(0);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character singleOrNull(CharSequence cs) {
        String s = cs.toString();
        return s.length() == 1 ? s.charAt(0) : null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character getOrNull(CharSequence cs, int index) {
        String s = cs.toString();
        return (index >= 0 && index < s.length()) ? s.charAt(index) : null;
    }

    // ===================================================================================================
    // trim / trimStart / trimEnd
    //   - no-arg: drop leading/trailing whitespace (Character.isWhitespace — char-by-char, sound).
    //   - char[]: drop leading/trailing chars contained in the set (membership by-index, no Set).
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trim(CharSequence cs) {
        return trimImpl(cs.toString(), true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trim(String s, char[] chars) {
        return trimCharsImpl(s, chars, true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trim(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, true, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimStart(CharSequence cs) {
        return trimImpl(cs.toString(), true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimStart(String s, char[] chars) {
        return trimCharsImpl(s, chars, true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimStart(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, true, false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimEnd(CharSequence cs) {
        return trimImpl(cs.toString(), false, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimEnd(String s, char[] chars) {
        return trimCharsImpl(s, chars, false, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence trimEnd(CharSequence cs, char[] chars) {
        return trimCharsImpl(cs.toString(), chars, false, true);
    }

    private static String trimImpl(String s, boolean start, boolean end) {
        int lo = 0;
        int hi = s.length();
        if (start) {
            while (lo < hi && Character.isWhitespace(s.charAt(lo))) {
                lo++;
            }
        }
        if (end) {
            while (hi > lo && Character.isWhitespace(s.charAt(hi - 1))) {
                hi--;
            }
        }
        return s.substring(lo, hi);
    }

    private static String trimCharsImpl(String s, char[] chars, boolean start, boolean end) {
        int lo = 0;
        int hi = s.length();
        if (start) {
            while (lo < hi && inChars(s.charAt(lo), chars)) {
                lo++;
            }
        }
        if (end) {
            while (hi > lo && inChars(s.charAt(hi - 1), chars)) {
                hi--;
            }
        }
        return s.substring(lo, hi);
    }

    private static boolean inChars(char c, char[] chars) {
        for (char ch : chars) {
            if (ch == c) {
                return true;
            }
        }
        return false;
    }

    // ===================================================================================================
    // take / drop / takeLast / dropLast (bounded prefix/suffix slices)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence take(CharSequence cs, int n) {
        return takeImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String take(String s, int n) {
        return takeImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence drop(CharSequence cs, int n) {
        return dropImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String drop(String s, int n) {
        return dropImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence takeLast(CharSequence cs, int n) {
        return takeLastImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String takeLast(String s, int n) {
        return takeLastImpl(s, n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence dropLast(CharSequence cs, int n) {
        return dropLastImpl(cs.toString(), n);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String dropLast(String s, int n) {
        return dropLastImpl(s, n);
    }

    private static String takeImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return s.substring(0, Math.min(n, s.length()));
    }

    private static String dropImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return s.substring(Math.min(n, s.length()));
    }

    private static String takeLastImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        int len = s.length();
        return s.substring(len - Math.min(n, len));
    }

    private static String dropLastImpl(String s, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Requested character count " + n + " is less than zero.");
        }
        return takeImpl(s, s.length() - n < 0 ? 0 : s.length() - n);
    }

    // ===================================================================================================
    // substring / slice / subSequence over an IntRange (closed range -> [first, last])
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substring(String s, IntRange range) {
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substring(CharSequence cs, IntRange range) {
        String s = cs.toString();
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String slice(String s, IntRange range) {
        if (range.isEmpty()) {
            return "";
        }
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence slice(CharSequence cs, IntRange range) {
        String s = cs.toString();
        if (range.isEmpty()) {
            return "";
        }
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence subSequence(CharSequence cs, IntRange range) {
        String s = cs.toString();
        return s.substring(range.getFirst(), range.getLast() + 1);
    }

    // ===================================================================================================
    // substringBefore / substringAfter / *Last (delimiter by char or String; default missingDelimiter)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + 1, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + delimiter.length(), s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(0, idx);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast(String s, char delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + 1, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast(String s, String delimiter, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : s.substring(idx + delimiter.length(), s.length());
    }

    // ===================================================================================================
    // removePrefix / removeSuffix / removeSurrounding / removeRange
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removePrefix(String s, CharSequence prefix) {
        String p = prefix.toString();
        if (startsWithImpl(s, p, 0)) {
            return s.substring(p.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removePrefix(CharSequence cs, CharSequence prefix) {
        String s = cs.toString();
        String p = prefix.toString();
        if (startsWithImpl(s, p, 0)) {
            return s.substring(p.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSuffix(String s, CharSequence suffix) {
        String suf = suffix.toString();
        if (endsWithImpl(s, suf)) {
            return s.substring(0, s.length() - suf.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSuffix(CharSequence cs, CharSequence suffix) {
        String s = cs.toString();
        String suf = suffix.toString();
        if (endsWithImpl(s, suf)) {
            return s.substring(0, s.length() - suf.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSurrounding(String s, CharSequence delimiter) {
        return removeSurroundingImpl(s, delimiter.toString(), delimiter.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String removeSurrounding(String s, CharSequence prefix, CharSequence suffix) {
        return removeSurroundingImpl(s, prefix.toString(), suffix.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSurrounding(CharSequence cs, CharSequence delimiter) {
        return removeSurroundingImpl(cs.toString(), delimiter.toString(), delimiter.toString());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeSurrounding(CharSequence cs, CharSequence prefix, CharSequence suffix) {
        return removeSurroundingImpl(cs.toString(), prefix.toString(), suffix.toString());
    }

    private static String removeSurroundingImpl(String s, String prefix, String suffix) {
        if (s.length() >= prefix.length() + suffix.length()
                && startsWithImpl(s, prefix, 0) && endsWithImpl(s, suffix)) {
            return s.substring(prefix.length(), s.length() - suffix.length());
        }
        return s;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeRange(CharSequence cs, int startIndex, int endIndex) {
        String s = cs.toString();
        if (endIndex < startIndex) {
            throw new IndexOutOfBoundsException("End index (" + endIndex
                    + ") is less than start index (" + startIndex + ").");
        }
        if (endIndex == startIndex) {
            return s;
        }
        return s.substring(0, startIndex) + s.substring(endIndex, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence removeRange(CharSequence cs, IntRange range) {
        return removeRange(cs, range.getFirst(), range.getLast() + 1);
    }

    // ===================================================================================================
    // startsWith / endsWith / contains / indexOf / lastIndexOf
    //   The default (caseSensitive) branch uses the sound java.lang.String primitives directly; the
    //   ignoreCase=true branch routes through ASCII-only case folding (asciiLower) — never locale tables.
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, char ch, boolean ignoreCase) {
        String s = cs.toString();
        return s.length() != 0 && charEq(s.charAt(0), ch, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, CharSequence prefix, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), 0, prefix.toString(), 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(CharSequence cs, CharSequence prefix, int startIndex, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), startIndex, prefix.toString(), 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(String s, String prefix, boolean ignoreCase) {
        return regionMatchesImplBool(s, 0, prefix, 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith(String s, String prefix, int startIndex, boolean ignoreCase) {
        return regionMatchesImplBool(s, startIndex, prefix, 0, prefix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(CharSequence cs, char ch, boolean ignoreCase) {
        String s = cs.toString();
        return s.length() != 0 && charEq(s.charAt(s.length() - 1), ch, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(CharSequence cs, CharSequence suffix, boolean ignoreCase) {
        String s = cs.toString();
        String suf = suffix.toString();
        return regionMatchesImplBool(s, s.length() - suf.length(), suf, 0, suf.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith(String s, String suffix, boolean ignoreCase) {
        return regionMatchesImplBool(s, s.length() - suffix.length(), suffix, 0, suffix.length(), ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(CharSequence cs, char ch, boolean ignoreCase) {
        return indexOf(cs, ch, 0, ignoreCase) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains(CharSequence cs, CharSequence other, boolean ignoreCase) {
        return indexOf(cs, other.toString(), 0, ignoreCase) >= 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(CharSequence cs, char ch, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        for (int i = from; i < s.length(); i++) {
            if (charEq(s.charAt(i), ch, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf(CharSequence cs, String needle, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        int last = s.length() - needle.length();
        for (int i = from; i <= last; i++) {
            if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(CharSequence cs, char ch, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length() - 1);
        for (int i = from; i >= 0; i--) {
            if (charEq(s.charAt(i), ch, ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf(CharSequence cs, String needle, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length() - needle.length());
        for (int i = from; i >= 0; i--) {
            if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithImpl(String s, String prefix, int offset) {
        return regionMatchesImplBool(s, offset, prefix, 0, prefix.length(), false);
    }

    private static boolean endsWithImpl(String s, String suffix) {
        return regionMatchesImplBool(s, s.length() - suffix.length(), suffix, 0, suffix.length(), false);
    }

    // ===================================================================================================
    // regionMatches
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatches(CharSequence cs, int thisOffset, CharSequence other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), thisOffset, other.toString(), otherOffset, length, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatches(String s, int thisOffset, String other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(s, thisOffset, other, otherOffset, length, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean regionMatchesImpl(CharSequence cs, int thisOffset, CharSequence other,
            int otherOffset, int length, boolean ignoreCase) {
        return regionMatchesImplBool(cs.toString(), thisOffset, other.toString(), otherOffset, length, ignoreCase);
    }

    private static boolean regionMatchesImplBool(String s, int thisOffset, String other,
            int otherOffset, int length, boolean ignoreCase) {
        if (thisOffset < 0 || otherOffset < 0
                || thisOffset > s.length() - length || otherOffset > other.length() - length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!charEq(s.charAt(thisOffset + i), other.charAt(otherOffset + i), ignoreCase)) {
                return false;
            }
        }
        return true;
    }

    // ===================================================================================================
    // compareTo / equals / contentEquals (default + ASCII ignoreCase)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean equals(String a, String b, boolean ignoreCase) {
        if (a == null) {
            return b == null;
        }
        if (b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (!charEq(a.charAt(i), b.charAt(i), ignoreCase)) {
                return false;
            }
        }
        return true;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int compareTo(String a, String b, boolean ignoreCase) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            char ca = ignoreCase ? asciiLower(a.charAt(i)) : a.charAt(i);
            char cb = ignoreCase ? asciiLower(b.charAt(i)) : b.charAt(i);
            if (ca != cb) {
                return ca - cb;
            }
        }
        return a.length() - b.length();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEquals(CharSequence a, CharSequence b) {
        return contentEqualsImpl(a, b);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEquals(CharSequence a, CharSequence b, boolean ignoreCase) {
        return ignoreCase ? contentEqualsIgnoreCaseImpl(a, b) : contentEqualsImpl(a, b);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEqualsImpl(CharSequence a, CharSequence b) {
        return equals(a == null ? null : a.toString(), b == null ? null : b.toString(), false);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contentEqualsIgnoreCaseImpl(CharSequence a, CharSequence b) {
        return equals(a == null ? null : a.toString(), b == null ? null : b.toString(), true);
    }

    // ===================================================================================================
    // commonPrefixWith / commonSuffixWith
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonPrefixWith(CharSequence a, CharSequence b, boolean ignoreCase) {
        String sa = a.toString();
        String sb = b.toString();
        int n = Math.min(sa.length(), sb.length());
        int i = 0;
        while (i < n && charEq(sa.charAt(i), sb.charAt(i), ignoreCase)) {
            i++;
        }
        if (hasSurrogatePairAt(sa, i - 1) || hasSurrogatePairAt(sb, i - 1)) {
            i--;
        }
        return sa.substring(0, i);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonSuffixWith(CharSequence a, CharSequence b, boolean ignoreCase) {
        String sa = a.toString();
        String sb = b.toString();
        int la = sa.length();
        int lb = sb.length();
        int n = Math.min(la, lb);
        int i = 0;
        while (i < n && charEq(sa.charAt(la - i - 1), sb.charAt(lb - i - 1), ignoreCase)) {
            i++;
        }
        if (hasSurrogatePairAt(sa, la - i - 1) || hasSurrogatePairAt(sb, lb - i - 1)) {
            i--;
        }
        return sa.substring(la - i, la);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean hasSurrogatePairAt(CharSequence cs, int index) {
        String s = cs.toString();
        return index >= 0 && index <= s.length() - 2
                && Character.isHighSurrogate(s.charAt(index))
                && Character.isLowSurrogate(s.charAt(index + 1));
    }

    // ===================================================================================================
    // replace / replaceFirst (char,char + String,String) and the before/after positional replacers
    //   The default branch is sound over java.lang.String.replace (native-sound; StringLaws). ignoreCase
    //   String replace routes through the by-index ASCII fold above.
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace(String s, char oldChar, char newChar, boolean ignoreCase) {
        if (!ignoreCase) {
            return s.replace(oldChar, newChar);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(charEq(c, oldChar, true) ? newChar : c);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace(String s, String oldValue, String newValue, boolean ignoreCase) {
        if (oldValue.length() == 0) {
            // Kotlin inserts newValue between every char (and at both ends); model the non-empty case
            // soundly and route the empty-needle interleave through the bounded builder.
            StringBuilder sb = new StringBuilder();
            sb.append(newValue);
            for (int i = 0; i < s.length(); i++) {
                sb.append(s.charAt(i));
                sb.append(newValue);
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i <= s.length() - oldValue.length()) {
            if (regionMatchesImplBool(s, i, oldValue, 0, oldValue.length(), ignoreCase)) {
                sb.append(newValue);
                i += oldValue.length();
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        sb.append(s.substring(i, s.length()));
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst(String s, char oldChar, char newChar, boolean ignoreCase) {
        int idx = indexOf(s, oldChar, 0, ignoreCase);
        if (idx < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        sb.setCharAt(idx, newChar);
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst(String s, String oldValue, String newValue, boolean ignoreCase) {
        int idx = indexOf(s, oldValue, 0, ignoreCase);
        if (idx < 0) {
            return s;
        }
        return s.substring(0, idx) + newValue + s.substring(idx + oldValue.length(), s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBefore(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBefore(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBeforeLast(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceBeforeLast(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, 0, idx, replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfter(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, idx + 1, s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfter(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.indexOf(delimiter);
        return idx == -1 ? missingDelimiterValue
                : replaceRangeStr(s, idx + delimiter.length(), s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfterLast(String s, char delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue : replaceRangeStr(s, idx + 1, s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceAfterLast(String s, String delimiter, String replacement, String missingDelimiterValue) {
        int idx = s.lastIndexOf(delimiter);
        return idx == -1 ? missingDelimiterValue
                : replaceRangeStr(s, idx + delimiter.length(), s.length(), replacement);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence replaceRange(CharSequence cs, int startIndex, int endIndex, CharSequence replacement) {
        String s = cs.toString();
        if (endIndex < startIndex) {
            throw new IndexOutOfBoundsException("End index (" + endIndex
                    + ") is less than start index (" + startIndex + ").");
        }
        return s.substring(0, startIndex) + replacement.toString() + s.substring(endIndex, s.length());
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence replaceRange(CharSequence cs, IntRange range, CharSequence replacement) {
        return replaceRange(cs, range.getFirst(), range.getLast() + 1, replacement);
    }

    private static String replaceRangeStr(String s, int start, int end, String replacement) {
        return s.substring(0, start) + replacement + s.substring(end, s.length());
    }

    // ===================================================================================================
    // padStart / padEnd / reversed / repeat
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence padStart(CharSequence cs, int length, char padChar) {
        return padStartImpl(cs.toString(), length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String padStart(String s, int length, char padChar) {
        return padStartImpl(s, length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence padEnd(CharSequence cs, int length, char padChar) {
        return padEndImpl(cs.toString(), length, padChar);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String padEnd(String s, int length, char padChar) {
        return padEndImpl(s, length, padChar);
    }

    private static String padStartImpl(String s, int length, char padChar) {
        if (length < 0) {
            throw new IllegalArgumentException("Desired length " + length + " is less than zero.");
        }
        if (length <= s.length()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(s);
        return sb.toString();
    }

    private static String padEndImpl(String s, int length, char padChar) {
        if (length < 0) {
            throw new IllegalArgumentException("Desired length " + length + " is less than zero.");
        }
        if (length <= s.length()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s);
        for (int i = s.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence reversed(CharSequence cs) {
        String s = cs.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // repeat: built char-by-char over the bounded StringBuilder model (the JDK String.repeat is
    // JBMC-UNSOUND per StringLaws — it havocs both length and content — so this never delegates to it).
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String repeat(CharSequence cs, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Count 'n' must be non-negative, but was " + n + ".");
        }
        String s = cs.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    // ===================================================================================================
    // toCharArray / concatToString (bounded char-array <-> String, by-index)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char[] toCharArray(String s, int startIndex, int endIndex) {
        char[] out = new char[endIndex - startIndex];
        for (int i = startIndex; i < endIndex; i++) {
            out[i - startIndex] = s.charAt(i);
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String concatToString(char[] chars) {
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String concatToString(char[] chars, int startIndex, int endIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    // ===================================================================================================
    // toList / toMutableList / toSet / toHashSet (bounded element collections, by-index — no CharIterator)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toList(CharSequence cs) {
        return toMutableList(cs);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<Character> toMutableList(CharSequence cs) {
        String s = cs.toString();
        ArrayList<Character> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(s.charAt(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static java.util.Set<Character> toSet(CharSequence cs) {
        return toHashSet(cs);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static java.util.HashSet<Character> toHashSet(CharSequence cs) {
        String s = cs.toString();
        java.util.HashSet<Character> out = new java.util.HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(s.charAt(i));
        }
        return out;
    }

    // ===================================================================================================
    // any / none (no-predicate emptiness queries — non-inline, so the facade method IS called)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean any(CharSequence cs) {
        return cs.toString().length() != 0;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean none(CharSequence cs) {
        return cs.toString().length() == 0;
    }

    // ===================================================================================================
    // max / min (no-selector char extrema; nullable on empty) + Comparator variants
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character maxOrNull(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            return null;
        }
        char m = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) > m) {
                m = s.charAt(i);
            }
        }
        return m;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character minOrNull(CharSequence cs) {
        String s = cs.toString();
        if (s.length() == 0) {
            return null;
        }
        char m = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) < m) {
                m = s.charAt(i);
            }
        }
        return m;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character maxWithOrNull(CharSequence cs, java.util.Comparator<? super Character> comparator) {
        String s = cs.toString();
        if (s.length() == 0) {
            return null;
        }
        char m = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (comparator.compare(s.charAt(i), m) > 0) {
                m = s.charAt(i);
            }
        }
        return m;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character minWithOrNull(CharSequence cs, java.util.Comparator<? super Character> comparator) {
        String s = cs.toString();
        if (s.length() == 0) {
            return null;
        }
        char m = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (comparator.compare(s.charAt(i), m) < 0) {
                m = s.charAt(i);
            }
        }
        return m;
    }

    // ===================================================================================================
    // random / randomOrNull (bounded nondet-in-range draw via the kotlin.random.Random model)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static char random(CharSequence cs, kotlin.random.Random random) {
        String s = cs.toString();
        if (s.length() == 0) {
            throw new NoSuchElementException("Char sequence is empty.");
        }
        return s.charAt(random.nextInt(s.length()));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Character randomOrNull(CharSequence cs, kotlin.random.Random random) {
        String s = cs.toString();
        if (s.length() == 0) {
            return null;
        }
        return s.charAt(random.nextInt(s.length()));
    }

    // ===================================================================================================
    // indexOfAny / lastIndexOfAny / findAnyOf / findLastAnyOf (char-set and string-collection scans)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOfAny(CharSequence cs, char[] chars, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            for (char ch : chars) {
                if (charEq(c, ch, ignoreCase)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOfAny(CharSequence cs, char[] chars, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length() - 1);
        for (int i = from; i >= 0; i--) {
            char c = s.charAt(i);
            for (char ch : chars) {
                if (charEq(c, ch, ignoreCase)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOfAny(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase) {
        kotlin.Pair<Integer, String> hit = findAnyOf(cs, strings, startIndex, ignoreCase);
        return hit == null ? -1 : hit.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOfAny(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase) {
        kotlin.Pair<Integer, String> hit = findLastAnyOf(cs, strings, startIndex, ignoreCase);
        return hit == null ? -1 : hit.getFirst();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.Pair<Integer, String> findAnyOf(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = startIndex < 0 ? 0 : startIndex;
        for (int i = from; i <= s.length(); i++) {
            for (String needle : strings) {
                if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                    return new kotlin.Pair<>(i, needle);
                }
            }
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.Pair<Integer, String> findLastAnyOf(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase) {
        String s = cs.toString();
        int from = Math.min(startIndex, s.length());
        for (int i = from; i >= 0; i--) {
            for (String needle : strings) {
                if (regionMatchesImplBool(s, i, needle, 0, needle.length(), ignoreCase)) {
                    return new kotlin.Pair<>(i, needle);
                }
            }
        }
        return null;
    }

    // ===================================================================================================
    // slice(Iterable<Int>) — collect the chars at the given indices (by-index over the concrete backing)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static CharSequence slice(CharSequence cs, Iterable<Integer> indices) {
        String s = cs.toString();
        StringBuilder sb = new StringBuilder();
        for (Integer i : indices) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // ===================================================================================================
    // collection-returning: toCollection / toSortedSet / withIndex / zip / zipWithNext (bounded, by-index)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <C extends java.util.Collection<? super Character>> C toCollection(CharSequence cs, C destination) {
        String s = cs.toString();
        for (int i = 0; i < s.length(); i++) {
            destination.add(s.charAt(i));
        }
        return destination;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static java.util.SortedSet<Character> toSortedSet(CharSequence cs) {
        String s = cs.toString();
        java.util.TreeSet<Character> out = new java.util.TreeSet<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(s.charAt(i));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Iterable<kotlin.collections.IndexedValue<Character>> withIndex(CharSequence cs) {
        String s = cs.toString();
        ArrayList<kotlin.collections.IndexedValue<Character>> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            out.add(new kotlin.collections.IndexedValue<>(i, s.charAt(i)));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<kotlin.Pair<Character, Character>> zip(CharSequence a, CharSequence b) {
        String sa = a.toString();
        String sb = b.toString();
        int n = Math.min(sa.length(), sb.length());
        ArrayList<kotlin.Pair<Character, Character>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new kotlin.Pair<>(sa.charAt(i), sb.charAt(i)));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<kotlin.Pair<Character, Character>> zipWithNext(CharSequence cs) {
        String s = cs.toString();
        ArrayList<kotlin.Pair<Character, Character>> out = new ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(new kotlin.Pair<>(s.charAt(i), s.charAt(i + 1)));
        }
        return out;
    }

    // ===================================================================================================
    // chunked / windowed (no-transform overloads -> List<String>) and lines (split on line terminators)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> chunked(CharSequence cs, int size) {
        return windowed(cs, size, size, true);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> windowed(CharSequence cs, int size, int step, boolean partialWindows) {
        if (size <= 0 || step <= 0) {
            throw new IllegalArgumentException("Both size " + size + " and step " + step + " must be greater than zero.");
        }
        String s = cs.toString();
        ArrayList<String> out = new ArrayList<>();
        int n = s.length();
        for (int i = 0; i < n; i += step) {
            int end = i + size;
            if (end > n) {
                if (!partialWindows) {
                    break;
                }
                end = n;
            }
            out.add(s.substring(i, end));
        }
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> lines(CharSequence cs) {
        String s = cs.toString();
        ArrayList<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                out.add(s.substring(start, i));
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                i++;
                start = i;
            } else {
                i++;
            }
        }
        out.add(s.substring(start, s.length()));
        return out;
    }

    // ===================================================================================================
    // split / splitToSequence by char[] / String[] delimiters (delimiter-based, NOT regex) — bounded scan
    //   (the java.util.regex.Pattern overload stays a loud regex wall below)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> split(CharSequence cs, char[] delimiters, boolean ignoreCase, int limit) {
        String s = cs.toString();
        ArrayList<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < s.length()) {
            if (limit > 0 && out.size() == limit - 1) {
                break;
            }
            boolean hit = false;
            char c = s.charAt(i);
            for (char d : delimiters) {
                if (charEq(c, d, ignoreCase)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                out.add(s.substring(start, i));
                i++;
                start = i;
            } else {
                i++;
            }
        }
        out.add(s.substring(start, s.length()));
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> split(CharSequence cs, String[] delimiters, boolean ignoreCase, int limit) {
        String s = cs.toString();
        ArrayList<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < s.length()) {
            if (limit > 0 && out.size() == limit - 1) {
                break;
            }
            String matched = null;
            for (String d : delimiters) {
                if (d.length() != 0 && regionMatchesImplBool(s, i, d, 0, d.length(), ignoreCase)) {
                    matched = d;
                    break;
                }
            }
            if (matched != null) {
                out.add(s.substring(start, i));
                i += matched.length();
                start = i;
            } else {
                i++;
            }
        }
        out.add(s.substring(start, s.length()));
        return out;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> splitToSequence(CharSequence cs, char[] delimiters, boolean ignoreCase, int limit) {
        return new kotlin.sequences.ListSequence<>(new ArrayList<>(split(cs, delimiters, ignoreCase, limit)));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> splitToSequence(CharSequence cs, String[] delimiters, boolean ignoreCase, int limit) {
        return new kotlin.sequences.ListSequence<>(new ArrayList<>(split(cs, delimiters, ignoreCase, limit)));
    }

    // ===================================================================================================
    // asIterable / asSequence / chunkedSequence / windowedSequence / lineSequence (eager bounded backing,
    //   never a virtual CharIterator — the concrete char list is materialized by index)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Iterable<Character> asIterable(CharSequence cs) {
        return toMutableList(cs);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<Character> asSequence(CharSequence cs) {
        return new kotlin.sequences.ListSequence<>((ArrayList<Character>) toMutableList(cs));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> chunkedSequence(CharSequence cs, int size) {
        return new kotlin.sequences.ListSequence<>(new ArrayList<>(chunked(cs, size)));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> windowedSequence(CharSequence cs, int size, int step, boolean partialWindows) {
        return new kotlin.sequences.ListSequence<>(new ArrayList<>(windowed(cs, size, step, partialWindows)));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> lineSequence(CharSequence cs) {
        return new kotlin.sequences.ListSequence<>(new ArrayList<>(lines(cs)));
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.collections.CharIterator iterator(CharSequence cs) {
        return new kotlin.text.StringCharIterator(cs.toString());
    }

    // ===================================================================================================
    // StringBuilder append/clear (non-inline facade reach; bounded)
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static StringBuilder clear(StringBuilder sb) {
        sb.setLength(0);
        return sb;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static StringBuilder append(StringBuilder sb, Object[] value) {
        for (Object o : value) {
            sb.append(o);
        }
        return sb;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static StringBuilder append(StringBuilder sb, String[] value) {
        for (String v : value) {
            sb.append(v);
        }
        return sb;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static <T extends Appendable> T append(T sb, CharSequence[] value) {
        try {
            for (CharSequence v : value) {
                sb.append(v);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return sb;
    }

    // ===================================================================================================
    // requireNonNegativeLimit — the internal @PublishedApi argument check the split path emits
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static void requireNonNegativeLimit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit must be non-negative, but was " + limit);
        }
    }

    // ===================================================================================================
    // toBooleanStrict / integer parses (Byte/Short/Int/Long, optional radix) — bounded digit scan, NO
    //   dtoa and NO locale tables (those float/locale parses stay loud walls below)
    // ===================================================================================================

    // String literal comparison goes through the by-index char helper (equals(...,false)), NEVER
    // String.equals — that lowers to org.cprover.CProverString.equals, which JBMC nondet-stubs.
    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean toBooleanStrict(String s) {
        if (equals(s, "true", false)) {
            return true;
        }
        if (equals(s, "false", false)) {
            return false;
        }
        throw new IllegalArgumentException("The string doesn't represent a boolean value: " + s);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Boolean toBooleanStrictOrNull(String s) {
        if (equals(s, "true", false)) {
            return Boolean.TRUE;
        }
        if (equals(s, "false", false)) {
            return Boolean.FALSE;
        }
        return null;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer toIntOrNull(String s) {
        return toIntOrNull(s, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Integer toIntOrNull(String s, int radix) {
        long v = parseLongInRadix(s, radix, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return v == PARSE_FAIL ? null : (int) v;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long toLongOrNull(String s) {
        return toLongOrNull(s, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Long toLongOrNull(String s, int radix) {
        long v = parseLongInRadix(s, radix, Long.MIN_VALUE, Long.MAX_VALUE);
        return v == PARSE_FAIL ? null : v;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte toByteOrNull(String s) {
        return toByteOrNull(s, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Byte toByteOrNull(String s, int radix) {
        long v = parseLongInRadix(s, radix, Byte.MIN_VALUE, Byte.MAX_VALUE);
        return v == PARSE_FAIL ? null : (byte) v;
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short toShortOrNull(String s) {
        return toShortOrNull(s, 10);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static Short toShortOrNull(String s, int radix) {
        long v = parseLongInRadix(s, radix, Short.MIN_VALUE, Short.MAX_VALUE);
        return v == PARSE_FAIL ? null : (short) v;
    }

    // Sentinel for an out-of-band parse failure. Long.MIN_VALUE never collides with a valid signed
    // result in [min,max] for the int-family ranges (and for the Long range, a genuine Long.MIN_VALUE
    // is re-derived correctly because we accumulate in NEGATIVE space and bound-check before negating).
    private static final long PARSE_FAIL = Long.MIN_VALUE;

    private static long parseLongInRadix(String s, int radix, long min, long max) {
        if (radix < 2 || radix > 36 || s.length() == 0) {
            return PARSE_FAIL;
        }
        boolean negative = false;
        int start = 0;
        char c0 = s.charAt(0);
        if (c0 == '-') {
            negative = true;
            start = 1;
        } else if (c0 == '+') {
            start = 1;
        }
        if (start == s.length()) {
            return PARSE_FAIL;
        }
        // Accumulate in NEGATIVE space so MIN_VALUE is representable.
        long result = 0;
        long limit = negative ? min : -max;
        long mulLimit = limit / radix;
        for (int i = start; i < s.length(); i++) {
            int digit = digitOf(s.charAt(i), radix);
            if (digit < 0) {
                return PARSE_FAIL;
            }
            if (result < mulLimit) {
                return PARSE_FAIL;
            }
            result *= radix;
            if (result < limit + digit) {
                return PARSE_FAIL;
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    // Inline digit decode (NEVER Character.digit — the JDK path trips JBMC's string intrinsics). Returns
    // the value of c in [0,radix), or -1 if c is not a digit of that radix. ASCII letters fold to 10..35.
    private static int digitOf(char c, int radix) {
        int v = -1;
        if (c >= '0' && c <= '9') {
            v = c - '0';
        } else if (c >= 'a' && c <= 'z') {
            v = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'Z') {
            v = c - 'A' + 10;
        }
        return (v >= 0 && v < radix) ? v : -1;
    }

    // ===================================================================================================
    // indent ops: prependIndent / replaceIndent / replaceIndentByMargin / trimIndent / trimMargin
    //   (line-based over the bounded backing; reIndent walks lines and re-joins with '\n')
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String prependIndent(String s, String indent) {
        List<String> ls = lines(s);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String line = ls.get(i);
            if (isBlank(line)) {
                if (line.length() < indent.length()) {
                    sb.append(indent);
                } else {
                    sb.append(line);
                }
            } else {
                sb.append(indent);
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimIndent(String s) {
        return replaceIndent(s, "");
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceIndent(String s, String newIndent) {
        List<String> ls = lines(s);
        // minimal common indent across the non-blank lines
        int minIndent = Integer.MAX_VALUE;
        for (String line : ls) {
            if (isBlank(line)) {
                continue;
            }
            int ind = indentWidth(line);
            if (ind < minIndent) {
                minIndent = ind;
            }
        }
        if (minIndent == Integer.MAX_VALUE) {
            minIndent = 0;
        }
        StringBuilder sb = new StringBuilder();
        boolean firstEmitted = false;
        for (String line : ls) {
            if (firstEmitted) {
                sb.append('\n');
            }
            firstEmitted = true;
            if (isBlank(line)) {
                // blank lines collapse to empty
                continue;
            }
            sb.append(newIndent);
            sb.append(line.substring(Math.min(minIndent, line.length())));
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceIndentByMargin(String s, String newIndent, String marginPrefix) {
        List<String> ls = lines(s);
        StringBuilder sb = new StringBuilder();
        boolean firstEmitted = false;
        for (String line : ls) {
            if (firstEmitted) {
                sb.append('\n');
            }
            firstEmitted = true;
            if (isBlank(line)) {
                continue;
            }
            int firstNonWs = indentWidth(line);
            String trimmedStart = line.substring(firstNonWs);
            if (startsWithImpl(trimmedStart, marginPrefix, 0)) {
                sb.append(newIndent);
                sb.append(trimmedStart.substring(marginPrefix.length()));
            } else {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimMargin(String s, String marginPrefix) {
        return replaceIndentByMargin(s, "", marginPrefix);
    }

    private static int indentWidth(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return i;
    }

    // ===================================================================================================
    // $default synthetic bridges — a Kotlin call site that OMITS an optional argument
    // ({@code "hi".startsWith("h")}, {@code s.contains("x")}, {@code s.substringBefore('.')}) does NOT
    // call the full-arg method; kotlinc emits an invokestatic to a synthesized {@code <op>$default}
    // bridge that decodes the default-arg bitmask and supplies the defaults. Unmodeled, JBMC nondet-stubs
    // the bridge and the verdict is UNKNOWN regardless of the real body. Each bridge below decodes the
    // mask (bit (1<<i) set => parameter i was omitted, take its stdlib default) and delegates to the
    // modeled full-arg method. Default values per kotlin-stdlib: {@code ignoreCase=false},
    // {@code indexOf startIndex=0}, {@code lastIndexOf startIndex=lastIndex}, and the substring*
    // {@code missingDelimiterValue} defaults to the receiver string.
    // ===================================================================================================

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith$default(String s, String prefix, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return startsWith(s, prefix, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean startsWith$default(CharSequence cs, CharSequence prefix, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return startsWith(cs, prefix, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith$default(String s, String suffix, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return endsWith(s, suffix, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean endsWith$default(CharSequence cs, CharSequence suffix, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return endsWith(cs, suffix, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains$default(CharSequence cs, CharSequence other, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return contains(cs, other, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static boolean contains$default(CharSequence cs, char ch, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return contains(cs, ch, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf$default(CharSequence cs, char ch, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return indexOf(cs, ch, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOf$default(CharSequence cs, String needle, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return indexOf(cs, needle, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf$default(CharSequence cs, char ch, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = cs.toString().length() - 1;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return lastIndexOf(cs, ch, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOf$default(CharSequence cs, String needle, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = cs.toString().length() - 1;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return lastIndexOf(cs, needle, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonPrefixWith$default(CharSequence a, CharSequence b, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return commonPrefixWith(a, b, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String commonSuffixWith$default(CharSequence a, CharSequence b, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        return commonSuffixWith(a, b, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace$default(String s, char oldChar, char newChar, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return replace(s, oldChar, newChar, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replace$default(String s, String oldValue, String newValue, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return replace(s, oldValue, newValue, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst$default(String s, char oldChar, char newChar, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return replaceFirst(s, oldChar, newChar, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceFirst$default(String s, String oldValue, String newValue, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return replaceFirst(s, oldValue, newValue, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore$default(String s, char delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringBefore(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBefore$default(String s, String delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringBefore(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter$default(String s, char delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringAfter(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfter$default(String s, String delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringAfter(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast$default(String s, char delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringBeforeLast(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringBeforeLast$default(String s, String delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringBeforeLast(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast$default(String s, char delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringAfterLast(s, delimiter, missingDelimiterValue);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String substringAfterLast$default(String s, String delimiter, String missingDelimiterValue, int mask, Object marker) {
        if ((mask & 4) != 0) {
            missingDelimiterValue = s;
        }
        return substringAfterLast(s, delimiter, missingDelimiterValue);
    }

    // ---- $default bridges for the modeled non-inline collection/scan/indent ops ----

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> split$default(CharSequence cs, char[] delimiters, boolean ignoreCase, int limit, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        if ((mask & 8) != 0) {
            limit = 0;
        }
        return split(cs, delimiters, ignoreCase, limit);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> split$default(CharSequence cs, String[] delimiters, boolean ignoreCase, int limit, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        if ((mask & 8) != 0) {
            limit = 0;
        }
        return split(cs, delimiters, ignoreCase, limit);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> splitToSequence$default(CharSequence cs, char[] delimiters, boolean ignoreCase, int limit, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        if ((mask & 8) != 0) {
            limit = 0;
        }
        return splitToSequence(cs, delimiters, ignoreCase, limit);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> splitToSequence$default(CharSequence cs, String[] delimiters, boolean ignoreCase, int limit, int mask, Object marker) {
        if ((mask & 4) != 0) {
            ignoreCase = false;
        }
        if ((mask & 8) != 0) {
            limit = 0;
        }
        return splitToSequence(cs, delimiters, ignoreCase, limit);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static List<String> windowed$default(CharSequence cs, int size, int step, boolean partialWindows, int mask, Object marker) {
        if ((mask & 4) != 0) {
            step = 1;
        }
        if ((mask & 8) != 0) {
            partialWindows = false;
        }
        return windowed(cs, size, step, partialWindows);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.sequences.Sequence<String> windowedSequence$default(CharSequence cs, int size, int step, boolean partialWindows, int mask, Object marker) {
        if ((mask & 4) != 0) {
            step = 1;
        }
        if ((mask & 8) != 0) {
            partialWindows = false;
        }
        return windowedSequence(cs, size, step, partialWindows);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOfAny$default(CharSequence cs, char[] chars, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return indexOfAny(cs, chars, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int indexOfAny$default(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return indexOfAny(cs, strings, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOfAny$default(CharSequence cs, char[] chars, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = cs.toString().length() - 1;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return lastIndexOfAny(cs, chars, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static int lastIndexOfAny$default(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = cs.toString().length() - 1;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return lastIndexOfAny(cs, strings, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.Pair<Integer, String> findAnyOf$default(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = 0;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return findAnyOf(cs, strings, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static kotlin.Pair<Integer, String> findLastAnyOf$default(CharSequence cs, java.util.Collection<String> strings, int startIndex, boolean ignoreCase, int mask, Object marker) {
        if ((mask & 4) != 0) {
            startIndex = cs.toString().length() - 1;
        }
        if ((mask & 8) != 0) {
            ignoreCase = false;
        }
        return findLastAnyOf(cs, strings, startIndex, ignoreCase);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceIndent$default(String s, String newIndent, int mask, Object marker) {
        if ((mask & 2) != 0) {
            newIndent = "";
        }
        return replaceIndent(s, newIndent);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String replaceIndentByMargin$default(String s, String newIndent, String marginPrefix, int mask, Object marker) {
        if ((mask & 2) != 0) {
            newIndent = "";
        }
        if ((mask & 4) != 0) {
            marginPrefix = "|";
        }
        return replaceIndentByMargin(s, newIndent, marginPrefix);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String trimMargin$default(String s, String marginPrefix, int mask, Object marker) {
        if ((mask & 2) != 0) {
            marginPrefix = "|";
        }
        return trimMargin(s, marginPrefix);
    }

    @BmcModelConforms("@BmcProof (model-conformance-proofs)")
    public static String prependIndent$default(String s, String indent, int mask, Object marker) {
        if ((mask & 2) != 0) {
            indent = "    ";
        }
        return prependIndent(s, indent);
    }

    // ===================================================================================================
    // ---- shared char helpers: ASCII-only case folding (NEVER locale tables) ----
    // ===================================================================================================

    private static char asciiLower(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    private static boolean charEq(char a, char b, boolean ignoreCase) {
        if (a == b) {
            return true;
        }
        return ignoreCase && asciiLower(a) == asciiLower(b);
    }

    // ===================================================================================================
    // ============================  GENUINE WALLS (loud @BmcUnmodelable)  ===============================
    // ===================================================================================================

    // ---- Locale-dependent / full-Unicode case mapping: needs locale tables ----
    @BmcUnmodelable(reason = "locale-dependent case mapping — needs the locale case tables")
    public static String capitalize(String a0, java.util.Locale a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.capitalize(java.lang.String,java.util.Locale) — locale-dependent case mapping — needs the locale case tables");
    }

    @BmcUnmodelable(reason = "locale-dependent case mapping — needs the locale case tables")
    public static String decapitalize(String a0, java.util.Locale a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decapitalize(java.lang.String,java.util.Locale) — locale-dependent case mapping — needs the locale case tables");
    }

    @BmcUnmodelable(reason = "full-Unicode title-case mapping of the first char — needs the Unicode case tables")
    public static String capitalize(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.capitalize(java.lang.String) — full-Unicode title-case mapping — needs the Unicode case tables");
    }

    @BmcUnmodelable(reason = "full-Unicode case mapping — needs the Unicode case tables")
    public static String decapitalize(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decapitalize(java.lang.String) — full-Unicode case mapping — needs the Unicode case tables");
    }

    @BmcUnmodelable(reason = "case-insensitive ordering comparator — full-Unicode/locale case fold over an open comparator")
    public static java.util.Comparator getCASE_INSENSITIVE_ORDER(kotlin.jvm.internal.StringCompanionObject a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.getCASE_INSENSITIVE_ORDER(kotlin.jvm.internal.StringCompanionObject) — full-Unicode/locale case-insensitive ordering");
    }

    // ---- Regex engine ----
    @BmcUnmodelable(reason = "regex engine — split over a java.util.regex.Pattern")
    public static List split(CharSequence a0, java.util.regex.Pattern a1, int a2) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.split(java.lang.CharSequence,java.util.regex.Pattern,int) — regex engine");
    }

    // ---- Number parse with radix/locale: full radix parsing + (for FP) dtoa ----
    @BmcUnmodelable(reason = "floating-point parse — needs dtoa")
    public static Double toDoubleOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toDoubleOrNull(java.lang.String) — floating-point parse needs dtoa");
    }

    @BmcUnmodelable(reason = "floating-point parse — needs dtoa")
    public static Float toFloatOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toFloatOrNull(java.lang.String) — floating-point parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigDecimal parse — needs dtoa / arbitrary-precision decimal parsing")
    public static java.math.BigDecimal toBigDecimalOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigDecimalOrNull(java.lang.String) — BigDecimal parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigDecimal parse with MathContext — needs dtoa / arbitrary-precision decimal parsing")
    public static java.math.BigDecimal toBigDecimalOrNull(String a0, java.math.MathContext a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigDecimalOrNull(java.lang.String,java.math.MathContext) — BigDecimal parse needs dtoa");
    }

    @BmcUnmodelable(reason = "BigInteger parse — arbitrary-precision radix parsing")
    public static java.math.BigInteger toBigIntegerOrNull(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigIntegerOrNull(java.lang.String) — arbitrary-precision radix parsing");
    }

    @BmcUnmodelable(reason = "BigInteger parse with radix — arbitrary-precision radix parsing")
    public static java.math.BigInteger toBigIntegerOrNull(String a0, int a1) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.toBigIntegerOrNull(java.lang.String,int) — arbitrary-precision radix parsing");
    }

    @BmcUnmodelable(reason = "number-format error helper — throws a NumberFormatException constructed from locale-formatted text")
    public static Void numberFormatError(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.numberFormatError(java.lang.String) — number-format error helper");
    }

    // ---- Charset / encoding ----
    @BmcUnmodelable(reason = "charset decode — UTF-8 byte decoding")
    public static String decodeToString(byte[] a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decodeToString(byte[]) — charset decode");
    }

    @BmcUnmodelable(reason = "charset decode — UTF-8 byte decoding")
    public static String decodeToString(byte[] a0, int a1, int a2, boolean a3) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.decodeToString(byte[],int,int,boolean) — charset decode");
    }

    @BmcUnmodelable(reason = "charset encode — UTF-8 byte encoding")
    public static byte[] encodeToByteArray(String a0) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.encodeToByteArray(java.lang.String) — charset encode");
    }

    @BmcUnmodelable(reason = "charset encode — UTF-8 byte encoding")
    public static byte[] encodeToByteArray(String a0, int a1, int a2, boolean a3) {
        throw fail("bmc4j: unmodelled member kotlin.text.StringsKt.encodeToByteArray(java.lang.String,int,int,boolean) — charset encode");
    }
}
