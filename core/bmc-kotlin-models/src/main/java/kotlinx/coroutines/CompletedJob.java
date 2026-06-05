package kotlinx.coroutines;

/**
 * A {@link Job} that has already completed — what the {@code launch} model returns
 * after running the launched block synchronously (structured-concurrency scopes await
 * their children, so for a logic proof the body has run by the time the scope returns).
 */
final class CompletedJob implements Job {
}
