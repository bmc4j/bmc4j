package kotlin;

/**
 * Minimal model of Kotlin's {@code Result} value class. Only the {@code Failure}
 * carrier referenced by generated resume code and {@code ResultKt.throwOnFailure}
 * is needed; success values flow as the raw boxed value.
 */
public final class Result {

    private Result() {
    }

    public static final class Failure {
        public final Throwable exception;

        public Failure(Throwable exception) {
            this.exception = exception;
        }
    }
}
