package kotlin;

/**
 * Clean model of {@code ResultKt.throwOnFailure}. On a resumed success value this is
 * a no-op; only a {@code Result.Failure} carrier rethrows. The real implementation
 * pulls in stack-trace machinery that pollutes analysis.
 */
public final class ResultKt {

    private ResultKt() {
    }

    public static void throwOnFailure(Object result) {
        if (result instanceof Result.Failure) {
            sneakyThrow(((Result.Failure) result).exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
