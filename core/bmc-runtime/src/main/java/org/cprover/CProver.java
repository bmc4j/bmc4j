// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j against JBMC's documented intrinsic API
// (method names/signatures only); NOT copied or adapted from diffblue/cbmc or the
// java-models-library, so no CBMC license header applies.
package org.cprover;

/**
 * Self-contained stand-in for the CProver harness API.
 *
 * <p>JBMC recognises calls to {@code org.cprover.CProver.*} by their fully
 * qualified name and replaces them with its own nondeterminism / assumption
 * semantics during analysis. The bodies here therefore only need to compile and
 * load — they are never what JBMC reasons about. Shipping our own copy keeps the
 * runtime a single, publishable artifact with no vendored binaries.
 *
 * <p>Verified: JBMC substitutes these correctly (a self-written {@code CProver}
 * yields genuine nondet inputs in counterexamples).
 */
public final class CProver {

    private CProver() {
    }

    public static int nondetInt() {
        return 0;
    }

    public static long nondetLong() {
        return 0L;
    }

    public static short nondetShort() {
        return (short) 0;
    }

    public static byte nondetByte() {
        return (byte) 0;
    }

    public static char nondetChar() {
        return (char) 0;
    }

    public static boolean nondetBoolean() {
        return false;
    }

    public static float nondetFloat() {
        return 0f;
    }

    public static double nondetDouble() {
        return 0.0;
    }

    public static <T> T nondetWithNull() {
        return null;
    }

    public static <T> T nondetWithoutNull() {
        return null;
    }

    public static void assume(boolean __assumption) {
        // Replaced by JBMC: constrains analysis to paths where the condition holds.
    }
}
