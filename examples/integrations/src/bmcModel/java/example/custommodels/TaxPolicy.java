package example.custommodels;

import org.bmc4j.Bmc;

/**
 * Analysis-only <b>model</b> of a tax-policy service. Like {@link ExchangeRates} it shadows the real
 * (un-analyzable) class on JBMC's analysis classpath only; it never reaches the test runtime classpath.
 *
 * <p>Unlike a pure-nondet stub, this model contains <em>real logic</em> that exercises the very
 * constructs JBMC handles unsoundly inside a model: a {@code String.equals} branch and integer
 * {@code Math.floorDiv}. Because the model is now run through the same bytecode rewrite passes as the
 * proof code, that logic is sound — {@code .equals} compares string content (not a nondet stub) and
 * {@code Math.floorDiv} computes a real quotient. The proofs over this model therefore decide on the
 * model's actual behavior, not on JBMC's nondet defaults.
 */
public class TaxPolicy {

    /**
     * Tax (in basis points) for a region. A region equal (by content) to {@code "EXEMPT"} is always
     * zero; any other region gets a bounded symbolic rate. The exemption is decided by
     * {@code String.equals}, which is sound only when the model is rewritten — otherwise JBMC treats
     * the comparison as nondet and the branch is unconstrained.
     */
    public int rateBips(String region) {
        if (region.equals("EXEMPT")) {
            return 0;
        }
        return Bmc.anyInt(1, 2_000); // 0.0001 .. 0.2
    }

    /**
     * A self-consistency invariant the model must uphold: comparing a region against itself by content
     * is always true. With the model rewritten this holds soundly; left unrewritten, JBMC's nondet
     * {@code String.equals} can make {@code region.equals(region)} false — a self-equality the proof
     * relies on to detect that the model was genuinely (soundly) exercised.
     */
    public boolean regionMatchesItself(String region) {
        return region.equals(region);
    }

    /**
     * Apply this policy's rate to an amount, using integer {@code Math.floorDiv} (one of the Math.*
     * methods JBMC stubs to nondet unless rewritten). Floor division of two non-negative operands is
     * itself non-negative — a fact the proof relies on.
     */
    public long taxOn(long amountCents, String region) {
        long bips = rateBips(region);
        return Math.floorDiv(amountCents * bips, 10_000L);
    }
}
