package example.jakarta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

/**
 * A self-referential validated bean: {@code @Valid Node next} cascades into itself. The processor's
 * visited-set keeps generation finite; the generated recursive {@code assumeValid} is bounded at
 * PROOF time by JBMC's unwind depth, exactly like every other loop/recursion in the tool.
 */
public class Node {

    @Min(0)
    public int value;

    /** Recursive cascade — must generate compiling, non-infinite code. */
    @Valid
    public Node next;
}
