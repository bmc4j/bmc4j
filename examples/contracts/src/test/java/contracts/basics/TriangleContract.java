package contracts.basics;

import example.basics.Triangle;
import org.bmc4j.BmcContractsFor;
import org.bmc4j.Ensures;
import org.bmc4j.Requires;

/**
 * The contract for {@link Triangle}, declared test-side. The mirror method's signature binds
 * to {@code Triangle.triangle(int)}; the predicates are ordinary static booleans. The
 * processor turns this into a replace-stub, an enforce proof, and a manifest entry — with
 * production code untouched.
 */
@BmcContractsFor(Triangle.class)
interface TriangleContract {

    @Requires("bounded")
    @Ensures("nonNeg")
    int triangle(int n);

    static boolean bounded(int n) {
        return n >= 0 && n <= 8;
    }

    static boolean nonNeg(int result, int n) {
        return result >= 0;
    }
}
