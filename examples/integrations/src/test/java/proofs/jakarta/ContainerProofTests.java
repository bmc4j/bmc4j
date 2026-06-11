package proofs.jakarta;

import example.jakarta.Order;
import example.jakarta.OrderConstraints;
import example.jakarta.OrderLine;
import example.jakarta.Orders;
import java.util.ArrayList;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Container-element constraints: Jakarta 3.0 puts constraints inside generics
 * ({@code List<@Min(1) Integer>}, {@code List<@Valid OrderLine>}). The generated {@code assumeValid}
 * walks each list with a bounded loop, assuming the element constraints / cascading per element.
 *
 * <p>Idiom (matching the collections examples): build the list structure concretely with SYMBOLIC
 * elements, then {@code assumeValid} constrains those elements. The model's {@code get}/{@code size}
 * are conformance-proven; the loop bound (here 3, from {@code @Size(max = 3)}) keeps it cheap.
 */
class ContainerProofTests {

    private static Order orderWithScores(int n) {
        Order o = new Order();
        ArrayList<Integer> scores = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            scores.add(Bmc.anyInt());        // each element symbolic; assumeValid pins it >= 1
        }
        o.scores = scores;
        return o;
    }

    private static Order orderWithLines(int n) {
        Order o = new Order();
        ArrayList<OrderLine> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            OrderLine l = new OrderLine();
            l.quantity = Bmc.anyInt();       // symbolic; the container cascade pins it to 1..100
            lines.add(l);
        }
        o.lines = lines;
        return o;
    }

    /**
     * REFUTED: every score is {@code @Min(1)}, so a score of exactly 1 makes {@code score - 1 == 0}
     * — a division by zero reachable at the element boundary. The element loop puts {@code >= 1}
     * (not {@code > 1}) into the domain, so the boundary element triggers it.
     */
    @BmcProof(expect = Verdict.REFUTED)
    void element_min_boundary_divides_by_zero() {
        Order o = orderWithScores(1);
        OrderConstraints.assumeValid(o);
        Orders.firstScoreReciprocal(o);
    }

    /** PASSES: the guarded version is safe for every valid Order. */
    @BmcProof(unwind = 2)
    void element_reciprocal_safe() {
        Order o = orderWithScores(1);
        OrderConstraints.assumeValid(o);
        Orders.firstScoreReciprocalSafe(o);
    }

    /**
     * REFUTED: the container {@code @Valid} cascade brings each OrderLine's {@code @Min(1)} into the
     * domain — admitting quantity 1, which refutes "first line quantity >= 2".
     */
    @BmcProof(expect = Verdict.REFUTED)
    void container_valid_cascade_admits_the_element_minimum() {
        Order o = orderWithLines(1);
        OrderConstraints.assumeValid(o);
        Bmc.check(Orders.firstLineQuantityAtLeast2(o));
    }

    /**
     * PASSES — only because the container cascade is honored: firstLineBucket indexes a 101-slot
     * array by quantity, safe EXACTLY because the cascade bounds each line's quantity to 1..100.
     * Without the cascade, quantity would be free and this would be REFUTED. The green verdict pins
     * the container {@code @Valid} cascade fires.
     */
    @BmcProof(unwind = 2)
    void container_cascade_makes_the_bucket_index_safe() {
        Order o = orderWithLines(1);
        OrderConstraints.assumeValid(o);
        Orders.firstLineBucket(o);
    }

    /**
     * PASSES: a null list passes (jakarta semantics) — a valid Order may have null scores/lines, and
     * assumeValid's null-guarded loops admit that without exploring any element.
     */
    @BmcProof
    void null_list_is_valid() {
        Order o = new Order();   // scores and lines left null
        OrderConstraints.assumeValid(o);
        Bmc.check(o.scores == null && o.lines == null);
    }
}
