package java.lang;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Minimal sequential BMC model of {@link java.lang.Runtime} — just {@code getRuntime()} and
 * {@code availableProcessors()}, the environment stand-in a proof actually reaches.
 *
 * <p>The processor count is a fixed property of the (single, abstract) machine a bmc4j proof reasons
 * about, not an input to vary; libraries read it to SIZE data structures (thread-pool widths, striped/
 * sharded free-list bucket counts — e.g. a {@code availableProcessors()*2}-wide bucket array). Left
 * unmodeled it stubs to a nondet {@code int}, which makes any derived size/mask symbolic and turns a
 * deterministic computation (a fixed-width static array, an {@code id & (count-1)} bucket index) into a
 * conservative nondet/REFUTED — which, together with a nondet thread id, is enough to false-REFUTE an
 * otherwise-deterministic thread-sharded data structure. A fixed positive constant is the standard,
 * faithful environment value and keeps those sizes/masks CONCRETE.
 *
 * <p>Only those two members are modeled. The rest of {@code Runtime} — {@code exec}/{@code exit}/
 * {@code halt}/{@code gc}/{@code addShutdownHook}/the memory accessors/… — is external-world process and
 * memory control that bmc4j does not model; it is absorbed by the class-level {@code @BmcModelTail} with
 * LOUD synthesized bodies, so reaching any of it is an honest member-named UNKNOWN, never a silent nondet.
 */
@org.bmc4j.models.audit.BmcModelTail(
    reason = "java.lang.Runtime's process/memory-control surface (exec/exit/halt/gc/addShutdownHook/the "
        + "free/total/maxMemory accessors/…) is external-world control bmc4j does not model; only the "
        + "environment stand-ins getRuntime()/availableProcessors() are modeled (a fixed processor count)")
public class Runtime {

    private static final Runtime CURRENT = new Runtime();

    private Runtime() {
    }

    @BmcModelConforms("constant environmental stand-in — deterministic representative machine width; no behavioral surface to differentially test")
    public static Runtime getRuntime() {
        return CURRENT;
    }

    /**
     * A fixed processor count. The machine a proof reasons about has a definite, non-symbolic width; a
     * constant keeps any derived size/mask (e.g. a {@code count*2}-wide bucket array and its
     * {@code id & (count*2-1)} index) concrete. 8 is a sane, representative value.
     */
    @BmcModelConforms("constant environmental stand-in — deterministic representative machine width; no behavioral surface to differentially test")
    public int availableProcessors() {
        return 8;
    }
}
