package proofs.jarmodels;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * End-to-end guard: a proof that needs BOTH a {@code bmc-models} model (the bounded
 * {@code ArrayList}) AND a bytecode-rewritten construct ({@code String.equals}, redirected to the
 * sound {@code BmcStrings} shim, and string concat, desugared away). The dedicated
 * {@code jarModelsConformanceTest} Gradle task runs this class with {@code bmc-models} supplied as a
 * <b>jar</b> on the analysis classpath (not the {@code includeBuild} class directory the rest of the
 * suite uses), so it exercises the jar-mirroring rewrite path a published consumer hits — proving the
 * shipped product is as sound as the in-repo test bed.
 *
 * <p>If jar entries were NOT rewritten (the directories-only bug), the {@code String.equals} below would bind
 * to JBMC's own unsound {@code String.equals} and these would not verify the way they do with the
 * shim — but the model itself comes from the jar regardless, so a green run here is the conformance
 * record that jar-supplied models + jar-reachable rewrites both work on real jbmc.
 */
class JarModelLaws {

    @BmcProof(unwind = 1)
    void arraylist_model_loads_from_jar_and_roundtrips() {
        ArrayList<Integer> l = new ArrayList<>();
        int x = Bmc.anyInt();
        l.add(x);
        Bmc.check(l.size() == 1 && l.get(0) == x);
    }

    @BmcProof(unwind = 4)
    void string_equals_shim_is_sound_over_jar_classpath() {
        // String.equals is rewritten to BmcStrings.equals; with a literal both sides this must hold.
        String a = "bmc";
        Bmc.check(a.equals("bmc"));
        Bmc.check(!a.equals("BMC"));
    }

    @BmcProof(unwind = 1)
    void concat_desugar_reaches_through_jar_classpath() {
        // The "+" emits a StringConcatFactory indy, desugared to a sound StringBuilder helper.
        String s = "x" + "y";
        Bmc.check(s.length() == 2);
        Bmc.check(s.charAt(0) == 'x' && s.charAt(1) == 'y');
    }

    // ---- #18: String-keyed collection lookups are sound via the Object.equals redirect ----
    //
    // The collection models compare keys/elements with `key.equals(x)` where key is statically typed
    // Object, so javac emits INVOKEVIRTUAL java/lang/Object.equals — a call site the String-owner
    // redirect never touched. Before the #18 fix that dispatched into JBMC's NATIVE String.equals,
    // whose result is an unconstrained boolean: a lookup with a content-equal-but-distinct-reference
    // key could nondeterministically miss, so these proofs would be refutable. After the fix the site
    // routes through BmcStrings.objEquals -> the sound shim, so equal content means a guaranteed hit.
    //
    // Each proof uses TWO DISTINCT references (`a` stored, `b` queried) constrained to equal content
    // char-by-char. With `a == b` reference-identity the objEquals fast path would make the test pass
    // even under the native model, so distinct references are what genuinely exercises the redirect.

    /** Constrain two bounded symbolic strings to have identical content (but be distinct references):
     *  same length, and equal at every in-range index via the sound charAt primitive. */
    private static void assumeSameContent(String a, String b, int maxLen) {
        Bmc.assume(a.length() == b.length());
        for (int i = 0; i < maxLen; i++) {
            if (i < a.length()) {
                Bmc.assume(a.charAt(i) == b.charAt(i));
            }
        }
    }

    @BmcProof(unwind = 4)
    void hashmap_string_key_get_is_sound() {
        // m.put(a, 42); then m.get(b) where b has the same content as a must return 42 — a property the
        // native (unconstrained) String.equals could not guarantee for distinct references.
        String a = Bmc.anyAsciiString(3);
        String b = Bmc.anyAsciiString(3);
        assumeSameContent(a, b, 3);
        HashMap<String, Integer> m = new HashMap<>();
        m.put(a, 42);
        Integer v = m.get(b);
        Bmc.check(v != null && v == 42);
        Bmc.check(m.containsKey(b));
    }

    @BmcProof(unwind = 4)
    void hashset_string_dedup_is_sound() {
        // Adding two content-equal Strings must dedup to a single element, and contains(b) must hit.
        String a = Bmc.anyAsciiString(3);
        String b = Bmc.anyAsciiString(3);
        assumeSameContent(a, b, 3);
        HashSet<String> set = new HashSet<>();
        set.add(a);
        boolean addedAgain = set.add(b);
        Bmc.check(!addedAgain);          // b is a duplicate of a
        Bmc.check(set.size() == 1);      // dedup happened
        Bmc.check(set.contains(b));      // and contains finds it
    }

    @BmcProof(unwind = 4)
    void arraylist_string_indexof_is_sound() {
        // list.indexOf(b) must find the content-equal element a at position 0.
        String a = Bmc.anyAsciiString(3);
        String b = Bmc.anyAsciiString(3);
        assumeSameContent(a, b, 3);
        ArrayList<String> list = new ArrayList<>();
        list.add(a);
        Bmc.check(list.indexOf(b) == 0);
        Bmc.check(list.contains(b));
    }
}
