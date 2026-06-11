package proofs.collectionliterals

import example.collectionliterals.feeFor
import example.collectionliterals.safeFeeFor
import org.bmc4j.Bmc
import org.bmc4j.BmcProof
import org.bmc4j.Verdict

/**
 * A collection literal is sugar for the stdlib's `of` factory, so under BMC it behaves exactly
 * like `listOf(...)` — including the part where indexing past its end throws. The literal's
 * size is right there in the source, which makes the off-by-N easy to miss and easy to prove.
 */
class CollectionLiteralProofs {

    // FAIL (the bug): the literal has five entries but days run 0..6 — Saturday (5) throws
    // IndexOutOfBoundsException. Expected verdict: REFUTED at dayOfWeek == 5.
    @BmcProof(expect = Verdict.REFUTED)
    fun fee_defined_for_every_day() {
        val day = Bmc.anyInt()
        Bmc.assume(day in 0..6)
        Bmc.check(feeFor(day) >= 0)
    }

    // PASS: within the literal's actual extent every fee is positive.
    @BmcProof(unwind = 8)
    fun weekday_fees_are_positive() {
        val day = Bmc.anyInt()
        Bmc.assume(day in 0..4)
        Bmc.check(feeFor(day) > 0)
    }

    // PASS: the fix totalizes the function — weekends are free, weekdays hit the literal.
    @BmcProof(unwind = 8)
    fun safe_fee_defined_for_every_day() {
        val day = Bmc.anyInt()
        Bmc.assume(day in 0..6)
        Bmc.check(safeFeeFor(day) >= 0)
    }
}
