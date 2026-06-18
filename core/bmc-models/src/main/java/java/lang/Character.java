package java.lang;

import static org.bmc4j.analysis.BmcUnmodelledReached.fail;

import org.bmc4j.models.audit.BmcModelConforms;

/**
 * Sound, bounded BMC model of the static case-folding and classification surface of
 * {@link java.lang.Character} that consumer code reaches constantly (parsing, validation,
 * normalization): {@code toLowerCase}/{@code toUpperCase}, {@code isDigit}/{@code isLetter}/
 * {@code isLetterOrDigit}/{@code isWhitespace}/{@code isSpaceChar}/{@code isUpperCase}/
 * {@code isLowerCase}, {@code digit(char,int)} and {@code getNumericValue}.
 *
 * <p><b>Why this model exists.</b> Under string refinement OFF
 * ({@code StringMode.CHAR_ARRAY_MODEL} / {@code --no-refine-strings}) JBMC does <em>not</em> intercept
 * {@code Character} with intrinsics, so the call links the engine's core-models {@code Character} -
 * which routes every {@code isX}/{@code toX} through {@code java.lang.CharacterData.of(cp).<m>(cp)}.
 * {@code CharacterData} is ABSENT from that core-models jar, so {@code CharacterData.of} havocs to a
 * nondet (possibly null) reference and the virtual call NPEs. Net: essentially every
 * {@code Character.isX}/{@code toX} NPEs under no-refine. This model stands in so those calls are
 * SOUND there. (Under refinement JBMC's own intrinsics handle Character - this model is irrelevant
 * to that path.)
 *
 * <h2>Soundness boundary (probed differentially vs the real JDK over the whole BMP, 2026-06)</h2>
 *
 * <p><b>Case folding - {@code toLowerCase}/{@code toUpperCase}.</b> These overloads take NO
 * {@code Locale} (the {@code Character} API is locale-free by contract - it returns the locale-neutral
 * Unicode <em>simple</em> mapping), so there is NO Turkish-{@code 'I'}/{@code 'i'} divergence to trap
 * here (unlike {@code String.toLowerCase(Locale)}, whose result IS locale-dependent - see that model).
 * The simple mapping over ASCII + the Latin-1 supplement ({@code 0x00..0xFF}) is plain arithmetic and
 * is matched EXACTLY:
 * <ul>
 *   <li>{@code toLowerCase}: {@code 'A'..'Z'} and the Latin-1 uppercase block {@code 0xC0..0xDE}
 *       (excluding {@code 0xD7} x) fold {@code +0x20}; every other {@code 0x00..0xFF} char is
 *       unchanged. (Verified: 0 mismatches vs the JDK over {@code 0x00..0xFF}.)</li>
 *   <li>{@code toUpperCase}: {@code 'a'..'z'} and the Latin-1 lowercase block {@code 0xE0..0xFE}
 *       (excluding {@code 0xF7} (div)) fold {@code -0x20}; PLUS the two non-arithmetic Latin-1 cases the
 *       JDK has - {@code 0xB5} MICRO SIGN {@literal ->} {@code 0x039C} GREEK CAPITAL MU and {@code 0xFF}
 *       y-diaeresis {@literal ->} {@code 0x0178} Y-diaeresis - handled explicitly; every other
 *       {@code 0x00..0xFF} char is unchanged (notably {@code 0xDF} sharp-s stays under the simple
 *       mapping). (Verified: 0 mismatches.)</li>
 * </ul>
 * Any code point {@code >= 0x100} needs the full Unicode case table this model does not carry (and
 * includes expanding / context-dependent cases), so it is LOUD ({@code -> UNKNOWN}, never a wrong
 * VERIFIED).
 *
 * <p><b>Classification predicates + {@code digit}/{@code getNumericValue}.</b> The ASCII band
 * ({@code 0x00..0x7F}) is clean, regular arithmetic and is matched EXACTLY (verified: 0 mismatches for
 * each over {@code 0x00..0x7F}, and {@code digit} over every radix {@code 2..36}). The Latin-1
 * supplement ({@code 0x80..0xFF}) classification is irregular (scattered letter/case ranges, the
 * feminine/masculine ordinals, the micro sign, NBSP) and the rest of the BMP needs the Unicode
 * property tables - so {@code >= 0x80} is LOUD here rather than open-coding a brittle Latin-1 table.
 * (Composition note: {@code isLetterOrDigit} is modeled directly over ASCII; callers needing wider
 * coverage can compose {@code isLetter(c) || isDigit(c)} - each loud beyond its own precise band.)
 *
 * <p>Every {@code char} overload widens to its {@code int} twin (the JDK does the same), so the
 * boundary is defined once on the {@code int} form.
 */
public final class Character implements java.io.Serializable, Comparable<Character> {

    /** Boxed value (the {@code char} this wrapper carries). */
    private final char value;

    /**
     * Box a {@code char}. Present (alongside {@link #valueOf(char)}, {@link #charValue()} and the
     * {@code Comparable}/{@code equals}/{@code hashCode}/{@code toString} surface below) because once
     * this class shadows {@code java.lang.Character} it must remain a faithful boxed wrapper: javac's
     * autoboxing of a {@code char} compiles to {@code Character.valueOf}/{@code charValue}, and sibling
     * models (the natural-order comparator) box chars through it. All are small total functions - exact.
     */
    public Character(char value) {
        this.value = value;
    }

    /** Box a {@code char} (the autoboxing entry point). */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); boxing identity")
    public static Character valueOf(char c) {
        return new Character(c);
    }

    /** Unbox to the carried {@code char}. */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); unboxing identity")
    public char charValue() {
        return value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); value equality")
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Character) && ((Character) obj).value == value;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); hash == char value")
    @Override
    public int hashCode() {
        return value;
    }

    /** Natural ordering: unsigned 16-bit {@code char} order, exact (delegates to {@link #compare}). */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); unsigned char order")
    @Override
    public int compareTo(Character other) {
        return compare(this.value, other.value);
    }

    // ===== case folding (precise over ASCII + Latin-1 supplement; loud beyond) =====================

    /**
     * Locale-neutral simple lowercase mapping. Exact over {@code 0x00..0xFF}; loud beyond (needs the
     * full Unicode case table, with expanding/context-dependent cases).
     */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII+Latin-1 simple fold, differentially exact")
    public static int toLowerCase(int codePoint) {
        if (codePoint < 0 || codePoint > 0xFF) {
            throw fail("bmc4j: unmodelled member java.lang.Character.toLowerCase(int)"
                + " - code point >= 0x100 needs the full Unicode case table (expanding / context-dependent"
                + " cases); the model is precise only over ASCII + the Latin-1 supplement (0x00..0xFF)");
        }
        // 'A'..'Z' and the Latin-1 uppercase block 0xC0..0xDE (excluding 0xD7 x) fold +0x20; rest unchanged.
        if ((codePoint >= 'A' && codePoint <= 'Z')
            || (codePoint >= 0xC0 && codePoint <= 0xDE && codePoint != 0xD7)) {
            return codePoint + 0x20;
        }
        return codePoint;
    }

    /** {@code char} overload - widens to {@link #toLowerCase(int)} (the JDK does the same). */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to toLowerCase(int)")
    public static char toLowerCase(char ch) {
        return (char) toLowerCase((int) ch);
    }

    /**
     * Locale-neutral simple uppercase mapping. Exact over {@code 0x00..0xFF} (including the two
     * non-arithmetic Latin-1 cases MICRO SIGN and y-diaeresis); loud beyond.
     */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII+Latin-1 simple fold, differentially exact")
    public static int toUpperCase(int codePoint) {
        if (codePoint < 0 || codePoint > 0xFF) {
            throw fail("bmc4j: unmodelled member java.lang.Character.toUpperCase(int)"
                + " - code point >= 0x100 needs the full Unicode case table (expanding / context-dependent"
                + " cases); the model is precise only over ASCII + the Latin-1 supplement (0x00..0xFF)");
        }
        // The two non-arithmetic Latin-1 uppercase mappings the JDK has.
        if (codePoint == 0xB5) {   // MICRO SIGN -> GREEK CAPITAL LETTER MU
            return 0x039C;
        }
        if (codePoint == 0xFF) {   // y-diaeresis -> LATIN CAPITAL LETTER Y WITH DIAERESIS
            return 0x0178;
        }
        // 'a'..'z' and the Latin-1 lowercase block 0xE0..0xFE (excluding 0xF7 (div)) fold -0x20; rest unchanged.
        if ((codePoint >= 'a' && codePoint <= 'z')
            || (codePoint >= 0xE0 && codePoint <= 0xFE && codePoint != 0xF7)) {
            return codePoint - 0x20;
        }
        return codePoint;
    }

    /** {@code char} overload - widens to {@link #toUpperCase(int)} (the JDK does the same). */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to toUpperCase(int)")
    public static char toUpperCase(char ch) {
        return (char) toUpperCase((int) ch);
    }

    // ===== classification predicates (precise over ASCII; loud beyond) =============================

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII '0'..'9', differentially exact")
    public static boolean isDigit(int codePoint) {
        requireAscii(codePoint, "isDigit");
        return codePoint >= '0' && codePoint <= '9';
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isDigit(int)")
    public static boolean isDigit(char ch) {
        return isDigit((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII letters, differentially exact")
    public static boolean isLetter(int codePoint) {
        requireAscii(codePoint, "isLetter");
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isLetter(int)")
    public static boolean isLetter(char ch) {
        return isLetter((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII letter|digit, differentially exact")
    public static boolean isLetterOrDigit(int codePoint) {
        requireAscii(codePoint, "isLetterOrDigit");
        return (codePoint >= 'A' && codePoint <= 'Z')
            || (codePoint >= 'a' && codePoint <= 'z')
            || (codePoint >= '0' && codePoint <= '9');
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isLetterOrDigit(int)")
    public static boolean isLetterOrDigit(char ch) {
        return isLetterOrDigit((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII whitespace set, differentially exact")
    public static boolean isWhitespace(int codePoint) {
        requireAscii(codePoint, "isWhitespace");
        // The ASCII chars the JDK treats as Java whitespace: \t \n  \f \r, the file/group/record/
        // unit separators 0x1C..0x1F, and SPACE. (NBSP 0xA0 is NOT whitespace here - and is beyond the
        // precise band anyway.)
        return codePoint == '\t' || codePoint == '\n' || codePoint == 0x0B || codePoint == '\f'
            || codePoint == '\r' || (codePoint >= 0x1C && codePoint <= 0x1F) || codePoint == ' ';
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isWhitespace(int)")
    public static boolean isWhitespace(char ch) {
        return isWhitespace((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII SPACE only, differentially exact")
    public static boolean isSpaceChar(int codePoint) {
        requireAscii(codePoint, "isSpaceChar");
        // Within ASCII, only SPACE is a Unicode space char (the C0 controls and 0x1C..0x1F are NOT).
        return codePoint == ' ';
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isSpaceChar(int)")
    public static boolean isSpaceChar(char ch) {
        return isSpaceChar((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII 'A'..'Z', differentially exact")
    public static boolean isUpperCase(int codePoint) {
        requireAscii(codePoint, "isUpperCase");
        return codePoint >= 'A' && codePoint <= 'Z';
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isUpperCase(int)")
    public static boolean isUpperCase(char ch) {
        return isUpperCase((int) ch);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII 'a'..'z', differentially exact")
    public static boolean isLowerCase(int codePoint) {
        requireAscii(codePoint, "isLowerCase");
        return codePoint >= 'a' && codePoint <= 'z';
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to isLowerCase(int)")
    public static boolean isLowerCase(char ch) {
        return isLowerCase((int) ch);
    }

    // ===== digit value / numeric value (precise over ASCII; loud beyond) ===========================

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII radix digit, differentially exact (radix 2..36)")
    public static int digit(int codePoint, int radix) {
        requireAscii(codePoint, "digit");
        int value;
        if (codePoint >= '0' && codePoint <= '9') {
            value = codePoint - '0';
        } else if (codePoint >= 'a' && codePoint <= 'z') {
            value = codePoint - 'a' + 10;
        } else if (codePoint >= 'A' && codePoint <= 'Z') {
            value = codePoint - 'A' + 10;
        } else {
            value = -1;
        }
        // A digit out of [0, radix) (incl. a radix outside MIN_RADIX..MAX_RADIX) is "not a digit".
        return (value >= 0 && value < radix && radix >= 2 && radix <= 36) ? value : -1;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to digit(int,int)")
    public static int digit(char ch, int radix) {
        return digit((int) ch, radix);
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); ASCII numeric value, differentially exact")
    public static int getNumericValue(int codePoint) {
        requireAscii(codePoint, "getNumericValue");
        if (codePoint >= '0' && codePoint <= '9') {
            return codePoint - '0';
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePoint - 'a' + 10;
        }
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return codePoint - 'A' + 10;
        }
        return -1;
    }

    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); widens to getNumericValue(int)")
    public static int getNumericValue(char ch) {
        return getNumericValue((int) ch);
    }

    // ===== ordering (total, exact over the whole char range) =======================================

    /**
     * Numeric comparison of two {@code char}s, treated as UNSIGNED 16-bit (the {@code char} contract):
     * {@code x - y} over {@code 0..0xFFFF}. Exact over the whole range (no Unicode table involved), so
     * no loud boundary. Used by the natural-order comparator the sort/priority-queue models drive, hence
     * it must stay present once this class shadows {@code java.lang.Character}.
     */
    @BmcModelConforms("@BmcProof (proofs.strings.CharacterModelLaws); unsigned char compare, exact over 0..0xFFFF")
    public static int compare(char x, char y) {
        return x - y;
    }

    // ===== loud boundary helper ====================================================================

    /**
     * Trap any non-ASCII code point loudly. The classification predicates and {@code digit}/
     * {@code getNumericValue} are precise only over {@code 0x00..0x7F}; the Latin-1 supplement and the
     * rest of the BMP need the Unicode property tables this model does not carry, so reaching them goes
     * to UNKNOWN (never a silently wrong classification). Routed through {@code member} so the demoted
     * verdict names the offending method.
     */
    private static void requireAscii(int codePoint, String member) {
        if (codePoint < 0 || codePoint > 0x7F) {
            throw fail("bmc4j: unmodelled member java.lang.Character." + member
                + " - non-ASCII code point (>= 0x80) needs the Unicode property tables; the model is"
                + " precise only over the ASCII band (0x00..0x7F)");
        }
    }
}
