package proofs.streams;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/**
 * Model proofs (axis 2) for the bounded Stream / IntStream models. These use CProver
 * intrinsics and can't run on a real JVM, so they're validated under JBMC rather than by the
 * differential axis — with concrete expected results, so a wrong pipeline result is caught. Lambdas
 * are desugared by bmc4j's own layer (no engine fork).
 */
class StreamLaws {

    @BmcProof
    void intStream_of_sum() {
        Bmc.check(IntStream.of(1, 2, 3, 4).sum() == 10);
    }

    @BmcProof
    void intStream_range_sum() {
        Bmc.check(IntStream.range(0, 5).sum() == 10); // 0+1+2+3+4
    }

    @BmcProof
    void intStream_map_filter_sum() {
        // 1,2,3,4 -> *2 -> 2,4,6,8 -> keep >4 -> 6,8 -> 14
        Bmc.check(IntStream.of(1, 2, 3, 4).map(x -> x * 2).filter(x -> x > 4).sum() == 14);
    }

    @BmcProof
    void list_stream_mapToInt_sum() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(xs.stream().mapToInt(x -> x + 1).sum() == 9); // 2+3+4
    }

    @BmcProof
    void stream_filter_count() {
        // Use a <=4-arg List.of (explicit overload) so JBMC keeps the concrete ArrayList type and can
        // devirtualize .stream(); List.of(5+) routes to the varargs overload, through which JBMC loses
        // the type and can't resolve List.stream() (a loud "no body for callee", not a silent result).
        List<Integer> xs = List.of(1, 2, 3, 4);
        Bmc.check(xs.stream().filter(x -> x % 2 == 1).count() == 2L); // 1,3
    }

    @BmcProof
    void stream_map_collect_toList() {
        List<Integer> xs = List.of(1, 2, 3);
        List<Integer> ys = xs.stream().map(x -> x * 10).collect(Collectors.toList());
        Bmc.check(ys.size() == 3 && ys.get(0) == 10 && ys.get(2) == 30);
    }

    @BmcProof
    void stream_anyMatch() {
        List<Integer> xs = List.of(1, 2, 3);
        Bmc.check(xs.stream().anyMatch(x -> x == 2));
    }

    /** Symbolic law: summing a 2-element stream equals the sum of its elements, for all inputs. */
    @BmcProof
    void symbolic_intStream_sum() {
        int a = Bmc.anyInt(0, 1000);
        int b = Bmc.anyInt(0, 1000);
        Bmc.check(IntStream.of(a, b).sum() == a + b);
    }
}
