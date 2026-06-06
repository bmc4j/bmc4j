package org.bmc4j.engine;

/**
 * A plain Java class (no {@code kotlin.Metadata}) used as a reflection target so
 * {@code ReplayTestWriter}'s auto language detection sees a genuine Java proof class and emits a
 * {@code .java} replay. A Kotlin object here would carry {@code kotlin.Metadata} and defeat the test.
 */
public final class JavaProofFixture {
    private JavaProofFixture() {
    }

    public static void proof() {
    }
}
