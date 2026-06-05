package example.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A SKU→price catalog backed by a {@code Map}, returning an {@code Optional} for lookups. */
public final class Catalog {

    private final Map<Integer, Integer> priceBySku = new HashMap<>();

    public void put(int sku, int price) {
        priceBySku.put(sku, price);
    }

    public Optional<Integer> price(int sku) {
        return priceBySku.containsKey(sku) ? Optional.of(priceBySku.get(sku)) : Optional.empty();
    }
}
