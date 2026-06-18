package proofs.properties;

import java.util.Properties;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the {@link java.util.Properties} model. The headline property is the one
 * that motivated the model: {@code getProperty} returns the STORED value or the supplied DEFAULT — never
 * a nondet, unbounded {@code String}. The {@code length}-bound law below is the ComparableVersion case
 * (a static Properties filled in {@code <clinit>} with short literals, read with
 * {@code getProperty(value, value)}): with the table backed by the Hashtable model, the result length is
 * bounded by the inputs, so a downstream {@code new String(char[], 0, count)} loop stays bounded.
 */
class PropertiesLaws {

    /** setProperty/put then getProperty returns the very value stored (shared backing state). */
    @BmcProof(unwind = 4)
    void set_then_get_returns_stored_value() {
        Properties p = new Properties();
        p.setProperty("cr", "rc");
        Bmc.check("rc".equals(p.getProperty("cr")));   // the put is seen by get (not a nondet)
        Bmc.check(p.getProperty("cr").length() == 2);
        Bmc.check(p.size() == 1 && !p.isEmpty());
    }

    /** getProperty(key, default): a present key yields the stored value; the default is ignored. */
    @BmcProof(unwind = 4)
    void getProperty_present_returns_stored_not_default() {
        Properties p = new Properties();
        p.setProperty("cr", "rc");
        Bmc.check("rc".equals(p.getProperty("cr", "FALLBACK")));
    }

    /** getProperty(key, default): an absent key yields the default verbatim. */
    @BmcProof(maxStringLength = 4, unwind = 8)
    void getProperty_absent_returns_default() {
        Properties p = new Properties();
        p.setProperty("cr", "rc");
        Bmc.check("d".equals(p.getProperty("ms", "d")));   // "ms" absent -> the default
        Bmc.check(p.getProperty("ms") == null);            // no default -> null, not havoc
    }

    /**
     * THE COMPARABLEVERSION CASE — length bound. A static-style Properties holds short literals; a lookup
     * with a SYMBOLIC, length-bounded input as both key and default ({@code getProperty(value, value)})
     * returns EITHER the stored literal (length 2) OR that very input, so the result length is bounded by
     * the inputs — never the unbounded-nondet that made a downstream {@code new String(char[], 0, count)}
     * loop climb to an absurd bound. A nondet model could not satisfy this.
     */
    @BmcProof(maxStringLength = 4, unwind = 8)
    void getProperty_result_length_is_bounded_by_inputs() {
        Properties aliases = new Properties();
        aliases.setProperty("cr", "rc");        // a stored alias literal (length 2)

        String value = Bmc.anyString(4);         // the bounded input, like ComparableVersion's token
        String resolved = aliases.getProperty(value, value);   // alias or fall back to the input itself

        // The result is one of two bounded things — never an unbounded havoc: it is either the stored
        // alias "rc" (length 2) or the input itself (length <= 4). Both length-bounded by the inputs.
        Bmc.check(resolved.length() <= 4);
        Bmc.check(resolved == value || "rc".equals(resolved));
    }

    /** The defaults chain: an absent key falls through to the defaults table's value. */
    @BmcProof(maxStringLength = 4, unwind = 8)
    void defaults_chain_is_consulted() {
        Properties base = new Properties();
        base.setProperty("cr", "rc");
        Properties p = new Properties(base);
        p.setProperty("ga", "al");
        Bmc.check("al".equals(p.getProperty("ga")));      // own entry
        Bmc.check("rc".equals(p.getProperty("cr")));      // inherited from defaults
        Bmc.check("d".equals(p.getProperty("no", "d")));  // neither -> default
    }
}
