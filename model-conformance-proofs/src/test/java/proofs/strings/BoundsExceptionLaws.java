package proofs.strings;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.StringMode;

/**
 * Conformance for the message-free index/bounds exception models under string refinement OFF
 * (StringMode.CHAR_ARRAY_MODEL). See {@code core/bmc-models/.../java/lang/IndexOutOfBoundsException.java}
 * and its {@code StringIndexOutOfBoundsException} / {@code ArrayIndexOutOfBoundsException} subclasses.
 *
 * <p><b>The disease.</b> The char-array {@code String} model's bounds checks throw
 * {@code new StringIndexOutOfBoundsException(index)} with a SYMBOLIC {@code index}
 * (see {@code String.charAt(int)} / {@code substring}). The REAL JDK {@code (int)} constructor builds
 * {@code super("String index out of range: " + index)}; with {@code index} symbolic that concat compiles
 * to a {@code new String(char[], 0, count)} with a symbolic {@code count}, whose per-char copy loop
 * unwinds without bound — a giant blowup on the (usually infeasible) out-of-bounds branch the engine
 * explores before pruning. Modeling the exception classes message-free removes the concat, so the throw
 * costs nothing.
 *
 * <p><b>What these pin.</b> Each proof drives a genuinely out-of-bounds (often symbolic-index) access on a
 * String built under CHAR_ARRAY_MODEL and asserts the throw still happens with the CORRECT TYPE — type +
 * control flow preserved exactly, only the (non-observable) detail message removed. Because the model
 * builds no symbolic message, these RESOLVE IN BUDGET; against the real JDK exception (the unmodeled
 * baseline) the symbolic-index charAt below times out. The bounds (small unwind/length) deliberately
 * leave NO room for a stray message-copy loop, so a regression to the message-building exception would
 * re-introduce the blowup and fail here.
 */
class BoundsExceptionLaws {

    // ---- the keystone: a SYMBOLIC out-of-bounds charAt still throws SIOOBE and resolves in budget ----
    // Pre-fix this TIMED OUT: the real (int) ctor concats "...: " + (symbolic index) -> unbounded
    // new String(char[],0,count). With the message-free model it resolves fast. unwind/length kept tiny
    // so any reintroduced message-copy loop has no slack to hide in.

    @BmcProof(unwind = 3, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void symbolicOutOfBoundsCharAt_throwsStringIndexOob_inBudget() {
        char[] data = {'a', 'b'};
        String s = new String(data);              // concrete length 2, char-array backed
        int i = Bmc.anyInt();
        Bmc.assume(i < 0 || i >= 2);              // genuinely out of bounds (symbolic)
        boolean threw = false;
        try {
            s.charAt(i);
        } catch (StringIndexOutOfBoundsException e) {
            threw = true;
        }
        Bmc.check(threw);                          // the out-of-bounds branch is feasible and throws SIOOBE
    }

    // ---- substring out of bounds throws SIOOBE (the no-arg ctor path) ----

    @BmcProof(unwind = 3, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void symbolicOutOfBoundsSubstring_throwsStringIndexOob_inBudget() {
        char[] data = {'a', 'b'};
        String s = new String(data);
        int begin = Bmc.anyInt();
        Bmc.assume(begin < 0 || begin > 2);       // out-of-range begin index
        boolean threw = false;
        try {
            s.substring(begin);
        } catch (StringIndexOutOfBoundsException e) {
            threw = true;
        }
        Bmc.check(threw);
    }

    // ---- TYPE hierarchy preserved: SIOOBE is-an IndexOutOfBoundsException is-a RuntimeException ----
    // Caught at the SUPERTYPE: control flow / instanceof unchanged by the message-free model.

    @BmcProof(unwind = 3, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void outOfBoundsCharAt_caughtAtSupertype_indexOob() {
        char[] data = {'a', 'b'};
        String s = new String(data);
        int i = Bmc.anyInt();
        Bmc.assume(i < 0 || i >= 2);
        boolean caughtAsIndexOob = false;
        boolean isRuntime = false;
        try {
            s.charAt(i);
        } catch (IndexOutOfBoundsException e) {   // the supertype catch must still fire
            caughtAsIndexOob = true;
            isRuntime = (e instanceof RuntimeException);
            // a StringIndexOob caught here must still be exactly that subtype
            Bmc.check(e instanceof StringIndexOutOfBoundsException);
        }
        Bmc.check(caughtAsIndexOob);
        Bmc.check(isRuntime);
    }

    // ---- in-bounds path is unaffected: no throw, exact content (refute a nondet model) ----

    @BmcProof(unwind = 3, stringMode = StringMode.CHAR_ARRAY_MODEL)
    void inBoundsCharAt_doesNotThrow_exactContent() {
        char[] data = {'a', 'b'};
        String s = new String(data);
        boolean threw = false;
        char c0 = 0;
        char c1 = 0;
        try {
            c0 = s.charAt(0);
            c1 = s.charAt(1);
        } catch (StringIndexOutOfBoundsException e) {
            threw = true;
        }
        Bmc.check(!threw);
        Bmc.check(c0 == 'a');
        Bmc.check(c1 == 'b');
    }
}
