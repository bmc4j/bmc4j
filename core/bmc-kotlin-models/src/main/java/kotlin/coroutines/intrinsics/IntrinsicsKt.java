package kotlin.coroutines.intrinsics;

/**
 * Clean model of the intrinsics facade. The compiler emits
 * {@code IntrinsicsKt.getCOROUTINE_SUSPENDED()} at each suspension point.
 */
public final class IntrinsicsKt {

    private IntrinsicsKt() {
    }

    public static Object getCOROUTINE_SUSPENDED() {
        return CoroutineSingletons.COROUTINE_SUSPENDED;
    }
}
