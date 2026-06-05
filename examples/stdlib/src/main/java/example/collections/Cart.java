package example.collections;

import java.util.ArrayList;
import java.util.List;

/** A shopping cart backed by a {@code List} — proven against bmc4j's bounded collection model. */
public final class Cart {

    private final List<Integer> prices = new ArrayList<>();

    public void add(int price) {
        prices.add(price);
    }

    public int count() {
        return prices.size();
    }

    public int total() {
        int t = 0;
        for (int p : prices) {
            t += p;
        }
        return t;
    }

    /** Same total, via a stream pipeline. */
    public int totalViaStream() {
        return prices.stream().mapToInt(p -> p).sum();
    }

    /** BUG: assumes the cart is non-empty — {@code get(0)} throws on an empty cart. */
    public int firstPrice() {
        return prices.get(0);
    }
}
