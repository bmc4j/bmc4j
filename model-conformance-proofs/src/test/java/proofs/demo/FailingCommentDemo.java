package proofs.demo;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;

/** Demo only — deliberately refutable, to exercise the PR proof-results failure comment. */
class FailingCommentDemo {

    @BmcProof
    void some_int_is_always_not_42() {
        int x = Bmc.anyInt();
        Bmc.check(x != 42);
    }
}
