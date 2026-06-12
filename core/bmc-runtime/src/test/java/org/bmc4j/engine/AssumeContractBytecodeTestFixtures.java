package org.bmc4j.engine;

import org.bmc4j.Bmc;

/**
 * Java fixtures for {@link AssumeContractBytecodeTest}. These MUST stay Java so javac emits the real
 * {@code LambdaMetafactory} {@code invokedynamic} sites for the method reference + predicate that the
 * {@link AssumeContractBytecode} decoder reads statically (the {@code bsmArgs[1]} implementation
 * handle). The Kotlin caller path SAM-converts to the identical encoding against these Java functional
 * interfaces — pinned end-to-end by the example proofs, not here.
 */
final class AssumeContractBytecodeTestFixtures {

    private AssumeContractBytecodeTestFixtures() {
    }

    interface Repo {
        User findById(int id);
    }

    interface TwoArg {
        User find(int tenant, int id);
    }

    static final class User {
        final int id;
        final int age;

        User(int id, int age) {
            this.id = id;
            this.age = age;
        }
    }

    /** Output-only assumeEvery over a bound instance reference. */
    static void outputOnly(Repo repo) {
        Bmc.assumeEvery(repo::findById, u -> u == null || u.age >= 0);
    }

    /** Args-aware assumeEvery: predicate gets the result AND the call argument. */
    static void argsAware(Repo repo) {
        Bmc.assumeEvery(repo::findById, (u, id) -> u == null || u.id == id);
    }

    /** assumeStable over a zero-arg reference (the env case). */
    static void stable() {
        Bmc.assumeStable(Runtime.getRuntime()::availableProcessors, n -> n == 8);
    }

    /** Two assumed contracts in one proof. */
    static void two(Repo repo) {
        Bmc.assumeEvery(repo::findById, u -> u == null || u.age >= 0);
        Bmc.assumeStable(Runtime.getRuntime()::availableProcessors, n -> n == 8);
    }

    /** Args-aware over a two-argument reference. */
    static void twoArg(TwoArg repo) {
        Bmc.assumeEvery(repo::find, (u, tenant, id) -> u == null || u.id == id);
    }
}
