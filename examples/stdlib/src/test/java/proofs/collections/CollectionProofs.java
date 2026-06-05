package proofs.collections;

import example.collections.Cart;
import example.collections.Catalog;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Proving code that uses {@code List}/{@code Map}/{@code Optional}. JBMC has no model for the JDK
 * collections — it stubs them to nondeterministic values, which makes any collection-touching proof
 * unsound. bmc4j ships clean, bounded models (array-backed) so they verify for real. Keep
 * collections within the proof's {@code unwind} bound (the lookup/iteration loops unwind to size).
 */
class CollectionProofs {

    // PASS: iterating a List and summing is sound.
    @BmcProof
    void total_sums_prices() {
        Cart c = new Cart();
        c.add(10);
        c.add(20);
        c.add(5);
        Bmc.check(c.total() == 35 && c.count() == 3);
    }

    // PASS over every pair: the model tracks element identity, so the total is exactly a + b.
    @BmcProof
    void total_is_symbolic_sum() {
        Cart c = new Cart();
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        c.add(a);
        c.add(b);
        Bmc.check(c.total() == a + b);
    }

    // FAIL (the bug): firstPrice() does get(0) on a possibly-empty cart — IndexOutOfBounds.
    // Expected verdict: REFUTED - firstPrice() indexes a possibly-empty cart.
    @BmcProof(expect = Verdict.REFUTED)
    void first_price_throws_on_empty() {
        Cart c = new Cart();
        c.firstPrice();
    }

    // PASS: a Map lookup for a known key returns its value (via Optional).
    @BmcProof
    void known_sku_has_price() {
        Catalog cat = new Catalog();
        cat.put(1, 100);
        Bmc.check(cat.price(1).get() == 100);
    }

    // PASS: an unknown key yields an empty Optional.
    @BmcProof
    void unknown_sku_is_empty() {
        Catalog cat = new Catalog();
        cat.put(1, 100);
        Bmc.check(cat.price(2).isEmpty());
    }

    // PASS: the immutable factory List.of(...) is modeled too.
    @BmcProof
    void immutable_list_of() {
        java.util.List<Integer> l = java.util.List.of(10, 20, 30);
        Bmc.check(l.size() == 3 && l.get(1) == 20);
    }

    // PASS: a stream pipeline (list.stream().mapToInt(...).sum()) gives the same total.
    @BmcProof
    void stream_total_matches() {
        Cart c = new Cart();
        c.add(10);
        c.add(20);
        c.add(5);
        Bmc.check(c.totalViaStream() == 35);
    }
}
