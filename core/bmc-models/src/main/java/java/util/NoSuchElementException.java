package java.util;

/** Minimal BMC model of {@link java.util.NoSuchElementException} (thrown by empty Optional/iterator). */
public class NoSuchElementException extends RuntimeException {

    public NoSuchElementException() {
        super();
    }

    public NoSuchElementException(String message) {
        super(message);
    }
}
