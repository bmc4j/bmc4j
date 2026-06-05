package proofs.textblocks;

import example.textblocks.Banner;
import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.Verdict;

/**
 * Text blocks (Java 15+) are ordinary compile-time String constants — there is no {@code
 * invokedynamic} or special runtime support behind {@code """ ... """}. So JBMC sees the same thing
 * it would for a quoted literal, and bmc4j's sound String modelling ({@code length}/{@code charAt}/
 * {@code equals}/{@code startsWith}/{@code contains}) applies unchanged.
 */
class TextBlockProofs {

    // PASS: the multi-line text block has the exact length and newline structure of its content.
    // "line1\nline2\nline3" = 5 + 1 + 5 + 1 + 5 = 17 chars, with '\n' at indices 5 and 11.
    @BmcProof
    void multiline_length_and_newlines() {
        String t = Banner.text();
        Bmc.check(t.length() == 17);
        Bmc.check(t.charAt(5) == '\n');
        Bmc.check(t.charAt(11) == '\n');
        Bmc.check(t.charAt(0) == 'l');
    }

    // PASS: a text block compares equal to the equivalent plain String via sound equals. The 17-char
    // content needs the loop bound raised above the default 16 so the char-wise compare fully unwinds.
    @BmcProof(unwind = 20)
    void text_block_equals_plain_string() {
        Bmc.check(Banner.text().equals("line1\nline2\nline3"));
        Bmc.check(Banner.text().startsWith("line1"));
        Bmc.check(Banner.text().contains("line2"));
    }

    // PASS: a single-line text block is exactly its bare content — no quotes, no trailing newline.
    @BmcProof
    void single_line_block_is_bare_content() {
        Bmc.check(Banner.word().equals("prod"));
        Bmc.check(Banner.word().length() == 4);
    }

    // PASS over every bounded name: a text block concatenated with symbolic input tracks length
    // soundly (the "hi " prefix is 3 chars; concat is desugared from StringConcatFactory indy).
    @BmcProof
    void greeting_length_is_prefix_plus_name() {
        String name = Bmc.anyString(4);
        Bmc.check(Banner.greeting(name).length() == 3 + name.length());
    }

    // FAIL (the bug): the single-line block is "prod", not "dev" — its first char is 'p', not 'd'.
    // BMC refutes the false claim immediately.
    @BmcProof(expect = Verdict.REFUTED)
    void block_is_not_dev() {
        Bmc.check(Banner.word().charAt(0) == 'd');
    }
}
