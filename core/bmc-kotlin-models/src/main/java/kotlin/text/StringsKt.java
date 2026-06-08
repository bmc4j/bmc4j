package kotlin.text;

import org.bmc4j.models.audit.BmcModelConforms;
import org.bmc4j.models.audit.BmcModelTail;

import kotlin.jvm.functions.Function1;

/**
 * Clean model of Kotlin's {@code kotlin.text.StringsKt} facade for the {@code buildString { }} string
 * builder. {@code buildString} is an INLINE stdlib function, so from a Kotlin call site its body lands
 * in the caller: the inlined body does {@code new StringBuilder()}, invokes the user builder action on
 * it, then {@code .toString()} — all of which bmc4j already models (the {@code StringBuilder} model and
 * the desugared lambda), so the inline path needs no facade method. This {@code buildString} facade JVM
 * method is the NON-inline / Java reach (and for completeness): it mirrors the inlined shape exactly —
 * allocate a {@code StringBuilder}, run the concrete (devirtualized) builder lambda on it, return its
 * {@code toString()}. The capacity-hint overload ignores the hint (the bounded StringBuilder backing is
 * fixed-size) — sound, matching the collection builders' mapCapacity precedent.
 *
 * <p>Only the {@code buildString} members are modeled here; the vast remainder of this multifile facade
 * (~280 stdlib String extension functions: split/replace/trim/regex/case/number-parsing/etc.) is the
 * tail — those reach the real kotlin-stdlib / JBMC String handling exactly as before (not loud).
 */
@BmcModelTail(reason = "exotic StringsKt facade remainder — the bulk of kotlin-stdlib's CharSequence/String "
        + "extension functions (split/replace/trim/regex/case/parsing/etc.) the bounded proofs do not "
        + "exercise; loud under JBMC if reached")
public final class StringsKt {

    private StringsKt() {
    }

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
}
