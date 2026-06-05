// SPDX-License-Identifier: Apache-2.0
// Provenance: written from scratch for bmc4j against JBMC's documented intrinsic API
// (method names/signatures only); NOT copied or adapted from diffblue/cbmc or the
// java-models-library, so no CBMC license header applies.
package org.cprover;

/**
 * Self-contained stand-in for CBMC's {@code org.cprover.CProverString} intrinsics. JBMC
 * recognises these by fully-qualified name and lowers them to its sound string operations
 * during analysis (the bodies here only need to compile/load). Shipping our own copy keeps
 * the runtime a single publishable artifact, exactly like {@link CProver}.
 *
 * <p>We expose only what we build sound String operations on top of — {@code charAt} is the
 * no-exception character read JBMC handles soundly, used to synthesise a sound
 * {@code String.equals} that the engine itself mishandles (see the strings work / issue).
 */
public final class CProverString {

    private CProverString() {
    }

    /** Character at {@code index} of {@code s}, JBMC's no-bounds-exception variant. */
    public static char charAt(String s, int index) {
        return '\0';
    }
}
