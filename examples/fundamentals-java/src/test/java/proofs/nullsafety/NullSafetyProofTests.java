package proofs.nullsafety;

import example.nullsafety.User;
import example.nullsafety.Users;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

class NullSafetyProofTests {

    /**
     * FAILS: the harness builds a normal array of non-null users with symbolic
     * fields. When none happens to be an admin, {@code admin()} returns null and
     * {@code adminId()} dereferences it — the null is produced and consumed entirely
     * inside the code under test.
     */
    // Expected verdict: REFUTED - the admin-less path dereferences null.
    @BmcProof(unwind = 3, expect = Verdict.REFUTED)
    void adminId_never_dereferences_null() {
        User[] users = {
            new User(Bmc.anyInt(), Bmc.anyBoolean()),
            new User(Bmc.anyInt(), Bmc.anyBoolean()),
        };
        Users.adminId(users);
    }

    /** PASSES: the guarded lookup handles the no-admin case for every input. */
    @BmcProof(unwind = 3)
    void adminIdOrDefault_is_safe() {
        User[] users = {
            new User(Bmc.anyInt(), Bmc.anyBoolean()),
            new User(Bmc.anyInt(), Bmc.anyBoolean()),
        };
        Users.adminIdOrDefault(users, -1);
    }
}
