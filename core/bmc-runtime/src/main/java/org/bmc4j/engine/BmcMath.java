package org.bmc4j.engine;

/**
 * Sound replacements for the integer-valued {@code java.lang.Math} methods that JBMC's bundled
 * {@code core-models.jar} does NOT model — it stubs them to an unconstrained (nondet) result, so a
 * proof over them is silently unsound (it can't even prove {@code Math.floorDiv(-7, 3) == -3}, and a
 * refutation it reports is spurious). {@link MathBytecode} redirects only those specific
 * {@code Math.*} call sites here during analysis; the methods JBMC <em>does</em> model soundly
 * ({@code sqrt}/{@code pow}/{@code sin}/...) are left untouched, so we keep its real math models.
 *
 * <p>Each method is reimplemented from plain {@code int}/{@code long} arithmetic that JBMC reasons
 * about exactly. The {@code *Exact} family matches the JDK contract: it throws
 * {@link ArithmeticException} on overflow (the JDK's loud failure), which JBMC surfaces as an
 * uncaught-exception property violation — so overflow is flagged, never silently wrapped. The
 * overflow predicates mirror the JDK's own ({@code java.lang.Math}) implementations.
 */
public final class BmcMath {

    private BmcMath() {
    }

    // --- floorDiv / floorMod (correct floor semantics for negative operands) ----------------------

    /** Sound stand-in for {@code Math.floorDiv(int, int)} (rounds toward negative infinity). */
    public static int floorDiv(int x, int y) {
        int q = x / y; // throws ArithmeticException on y == 0, exactly like the JDK
        // If the signs of the operands differ and the division has a non-zero remainder, round down.
        if ((x ^ y) < 0 && q * y != x) {
            q--;
        }
        return q;
    }

    /** Sound stand-in for {@code Math.floorDiv(long, long)}. */
    public static long floorDiv(long x, long y) {
        long q = x / y;
        if ((x ^ y) < 0 && q * y != x) {
            q--;
        }
        return q;
    }

    /** Sound stand-in for {@code Math.floorDiv(long, int)} (JDK overload returning long). */
    public static long floorDiv(long x, int y) {
        return floorDiv(x, (long) y);
    }

    /** Sound stand-in for {@code Math.floorMod(int, int)}: {@code x - floorDiv(x, y) * y}. */
    public static int floorMod(int x, int y) {
        int r = x % y; // throws ArithmeticException on y == 0
        // If the signs of the operands differ and the remainder is non-zero, shift it into [0, |y|).
        if ((x ^ y) < 0 && r != 0) {
            r += y;
        }
        return r;
    }

    /** Sound stand-in for {@code Math.floorMod(long, long)}. */
    public static long floorMod(long x, long y) {
        long r = x % y;
        if ((x ^ y) < 0 && r != 0) {
            r += y;
        }
        return r;
    }

    /** Sound stand-in for {@code Math.floorMod(long, int)} (JDK overload returning int). */
    public static int floorMod(long x, int y) {
        return (int) floorMod(x, (long) y);
    }

    // --- *Exact: compute and throw ArithmeticException on overflow (the JDK's loud failure) --------

    /** Sound stand-in for {@code Math.addExact(int, int)}. */
    public static int addExact(int x, int y) {
        int r = x + y;
        // Overflow iff both operands have the same sign and the result has the other (JDK predicate).
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    /** Sound stand-in for {@code Math.addExact(long, long)}. */
    public static long addExact(long x, long y) {
        long r = x + y;
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    /** Sound stand-in for {@code Math.subtractExact(int, int)}. */
    public static int subtractExact(int x, int y) {
        int r = x - y;
        // Overflow iff the operands have different signs and the result's sign differs from x.
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    /** Sound stand-in for {@code Math.subtractExact(long, long)}. */
    public static long subtractExact(long x, long y) {
        long r = x - y;
        if (((x ^ y) & (x ^ r)) < 0) {
            throw new ArithmeticException("long overflow");
        }
        return r;
    }

    /** Sound stand-in for {@code Math.multiplyExact(int, int)}. */
    public static int multiplyExact(int x, int y) {
        long r = (long) x * (long) y;
        if ((int) r != r) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) r;
    }

    /** Sound stand-in for {@code Math.multiplyExact(long, long)}. */
    public static long multiplyExact(long x, long y) {
        long r = x * y;
        // JDK overflow test: recompute the high bits and check none were lost. Guard the divide so a
        // zero operand (and the Long.MIN_VALUE * -1 case) is handled exactly like the JDK. Compute the
        // magnitudes inline (not via Math.abs, which JBMC stubs to nondet) so this stays sound.
        long ax = x < 0 ? -x : x;
        long ay = y < 0 ? -y : y;
        if (((ax | ay) >>> 31 != 0)) {
            if ((y != 0 && (r / y != x || (x == Long.MIN_VALUE && y == -1)))) {
                throw new ArithmeticException("long overflow");
            }
        }
        return r;
    }

    /** Sound stand-in for {@code Math.multiplyExact(long, int)} (JDK overload). */
    public static long multiplyExact(long x, int y) {
        return multiplyExact(x, (long) y);
    }

    /** Sound stand-in for {@code Math.negateExact(int)} (overflows only at {@code Integer.MIN_VALUE}). */
    public static int negateExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return -a;
    }

    /** Sound stand-in for {@code Math.negateExact(long)}. */
    public static long negateExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return -a;
    }

    /** Sound stand-in for {@code Math.incrementExact(int)}. */
    public static int incrementExact(int a) {
        if (a == Integer.MAX_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return a + 1;
    }

    /** Sound stand-in for {@code Math.incrementExact(long)}. */
    public static long incrementExact(long a) {
        if (a == Long.MAX_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a + 1;
    }

    /** Sound stand-in for {@code Math.decrementExact(int)}. */
    public static int decrementExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return a - 1;
    }

    /** Sound stand-in for {@code Math.decrementExact(long)}. */
    public static long decrementExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return a - 1;
    }

    /** Sound stand-in for {@code Math.toIntExact(long)}: loud when the value doesn't fit in an int. */
    public static int toIntExact(long value) {
        if ((int) value != value) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) value;
    }

    /** Sound stand-in for {@code Math.absExact(int)} (overflows only at {@code Integer.MIN_VALUE}). */
    public static int absExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("Overflow to represent absolute value of Integer.MIN_VALUE");
        }
        return a < 0 ? -a : a;
    }

    /** Sound stand-in for {@code Math.absExact(long)}. */
    public static long absExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("Overflow to represent absolute value of Long.MIN_VALUE");
        }
        return a < 0 ? -a : a;
    }

    // --- abs (int/long): JBMC's stub returns nondet; reimplement the exact JDK semantics ----------

    /** Sound stand-in for {@code Math.abs(int)}. Note {@code abs(Integer.MIN_VALUE)} stays negative,
     *  exactly per the JDK contract (no overflow trap) — {@code absExact} is the loud variant. */
    public static int abs(int a) {
        return a < 0 ? -a : a;
    }

    /** Sound stand-in for {@code Math.abs(long)}. */
    public static long abs(long a) {
        return a < 0 ? -a : a;
    }
}
