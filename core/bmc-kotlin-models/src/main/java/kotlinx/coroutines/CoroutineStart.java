package kotlinx.coroutines;

/**
 * Clean model of the {@code kotlinx.coroutines.CoroutineStart} enum. The real enum
 * carries an {@code invoke(...)} operator that dispatches a suspend block (a coroutine
 * method JBMC's frontend chokes on); we only ever receive a CoroutineStart as an
 * ignored builder argument, so a bare enum with the same constants suffices.
 */
public enum CoroutineStart {
    DEFAULT,
    LAZY,
    ATOMIC,
    UNDISPATCHED
}
