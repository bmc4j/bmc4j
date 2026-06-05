package kotlin.coroutines.intrinsics;

/**
 * Clean model of Kotlin's suspension sentinel enum. Suspend functions compare their
 * result against {@code COROUTINE_SUSPENDED} to decide whether they actually suspended.
 */
public enum CoroutineSingletons {
    COROUTINE_SUSPENDED,
    UNDECIDED,
    RESUMED
}
