package java.util;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Clean BMC model of {@link java.util.Properties} over the {@link Hashtable} model: a string-keyed
 * property table whose {@code put}/{@code get} share real backing state through the inherited
 * {@code Hashtable} arrays.
 *
 * <p><b>Why this model exists.</b> The motivating case is a static {@code Properties} table filled in a
 * class's {@code <clinit>} with short literals (e.g. {@code put("cr", "rc")}) and later read with
 * {@code getProperty(value, value)}. Unmodeled, the {@code <clinit>} {@code put} and the proof-time
 * {@code get} did not share state, so {@code getProperty} returned a NONDET, unbounded {@code String}
 * and any downstream length bound (a {@code new String(char[], 0, count)} loop) climbed to an absurd
 * bound. Backed by the faithful {@code Hashtable} model, {@code getProperty} now returns either the
 * stored value or the supplied default — its length bounded by the inputs, never havoc.
 *
 * <p>Modeled surface: {@code getProperty(String)}, {@code getProperty(String, String)},
 * {@code setProperty(String, String)} (plus everything inherited from the {@code Hashtable} model —
 * {@code put}/{@code get}/{@code remove}/{@code size}/…). The {@code defaults} fallback chain is
 * supported. The IO / listing / enumeration surface ({@code load}/{@code store}/{@code save}/
 * {@code list}/{@code propertyNames}/{@code stringPropertyNames}/{@code loadFromXML}/{@code storeToXML})
 * is genuinely unbounded external-world I/O and is absorbed by the class-level {@code @BmcModelTail}
 * with LOUD synthesized bodies — reaching it is an honest member-named UNKNOWN, never a silent stub.
 */
@org.bmc4j.models.audit.BmcModelTail(
    reason = "Properties' IO/listing surface (load/store/save/list/loadFromXML/storeToXML/propertyNames/"
        + "stringPropertyNames) is unbounded external-world I/O out of scope for this bounded model; the "
        + "in-memory property surface (getProperty/getProperty+default/setProperty + the inherited "
        + "Hashtable get/put/remove/size/…) is modeled and audited")
public class Properties extends Hashtable<Object, Object> {

    /** Optional fallback table consulted when a key is absent here, exactly like the JDK's chain. */
    protected Properties defaults;

    public Properties() {
    }

    public Properties(Properties defaults) {
        this.defaults = defaults;
    }

    public Properties(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * The stored {@code String} value for {@code key}, walking the {@code defaults} chain if absent
     * here; {@code null} if no value is found or the value is a non-{@code String}.
     */
    @BmcModelConforms("@BmcProof (proofs.properties PropertiesLaws)")
    public String getProperty(String key) {
        Object value = super.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaults == null ? null : defaults.getProperty(key);
    }

    /** {@link #getProperty(String)}, or {@code defaultValue} when no property is found. */
    @BmcModelConforms("@BmcProof (proofs.properties PropertiesLaws)")
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? defaultValue : value;
    }

    /** {@code put(key, value)} typed for string properties; returns the prior value (or {@code null}). */
    @BmcModelConforms("@BmcProof (proofs.properties PropertiesLaws)")
    public Object setProperty(String key, String value) {
        return put(key, value);
    }
}
