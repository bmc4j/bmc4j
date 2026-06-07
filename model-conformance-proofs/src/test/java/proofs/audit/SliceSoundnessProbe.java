package proofs.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.bmc4j.Bmc;
import org.bmc4j.BmcProof;
import org.bmc4j.engine.BmcRequest;
import org.bmc4j.engine.JbmcResult;
import org.bmc4j.engine.VerificationBackend;
import org.bmc4j.engine.VerificationBackends;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * HARD-soundness floor for per-proof model slicing: a class that is sliced AWAY but is nevertheless
 * reached at analysis time must surface as a member-named opaque-symbol UNKNOWN — never a silent stub,
 * a false VERIFIED, or a wrong verdict.
 *
 * <p>Slicing puts only the proof's reachable cone on the analysis classpath. The cone is a sound
 * over-approximation, so in a normal run a reached class is always present — the dangerous case
 * (a reached class missing from the classpath) cannot arise. This probe manufactures it anyway: it
 * runs the engine on a classpath from which the reached helper's {@code .class} has been physically
 * removed (exactly what slicing does to an out-of-cone class — a class file absent from a directory
 * classpath entry), and asserts the engine does NOT verify and NAMES the missing member as an opaque
 * symbol. That pins the property the cone's over-approximation is what normally guarantees: even if a
 * cone ever under-approximated, the engine fails toward "we don't know" (member-named), never toward a
 * false green.
 *
 * <p>{@link #sanity_reaching_the_helper_verifies_when_present()} is the control: with the helper on the
 * classpath the same proof VERIFIES, so the slice-away result below is caused by the missing class, not
 * by the proof itself.
 */
class SliceSoundnessProbe {

    /** A helper whose body the proof depends on. When present it is in the cone and the proof verifies;
     *  when sliced away (removed from the classpath) reaching it must surface as an opaque symbol. */
    static final class SlicedAwayHelper {
        static int identity(int x) {
            return x;
        }
    }

    /** The proof under probe: it reaches {@link SlicedAwayHelper#identity(int)}. As a green control it
     *  verifies; the soundness test below runs THIS entry on a classpath with the helper removed. */
    @BmcProof
    void reaches_helper() {
        int x = Bmc.anyInt(0, 10);
        Bmc.check(SlicedAwayHelper.identity(x) == x);
    }

    /** Control: with the whole classpath present the proof verifies — so the soundness test's failure to
     *  verify is attributable to the removed class, not to the proof. */
    @Test
    void sanity_reaching_the_helper_verifies_when_present() {
        JbmcResult result = runEntry(System.getProperty("java.class.path"));
        assertTrue(result.isVerified(),
                "with the helper present the proof must verify (control for the slice-away probe)");
    }

    /**
     * The floor: remove the reached helper's class file from the analysis classpath (what slicing does
     * to an out-of-cone class) and run the SAME proof. It must NOT verify — and the missing member must
     * be named as an opaque symbol, never silently stubbed to a passing nondet.
     */
    @Test
    void reaching_a_sliced_away_class_yields_member_named_unknown_never_silent_green(@TempDir Path tmp)
            throws IOException {
        String classpath = classpathWithHelperRemoved(tmp);
        JbmcResult result = runEntry(classpath);

        assertFalse(result.isVerified(),
                "a proof reaching a sliced-away class must NEVER silently VERIFY (the hard-soundness floor)");
        String helperName = SlicedAwayHelper.class.getName(); // proofs.audit.SliceSoundnessProbe$SlicedAwayHelper
        assertTrue(result.stubbedMethods().stream().anyMatch(m -> m.contains(helperName)),
                "the reached-but-sliced-away member must be named as an opaque symbol, not silently stubbed: "
                        + result.stubbedMethods());
    }

    // --- helpers ---------------------------------------------------------------

    private JbmcResult runEntry(String classpath) {
        BmcRequest req = new BmcRequest(
                "proofs.audit.SliceSoundnessProbe",
                "proofs.audit.SliceSoundnessProbe.reaches_helper",
                classpath, 16, true, 16, false, "", 0);
        VerificationBackend backend = VerificationBackends.select(req);
        return backend.verify(req);
    }

    /**
     * A copy of this module's compiled-class directory (the classpath entry that holds the probe and its
     * helper) with the helper's {@code .class} deleted, spliced back into {@code java.class.path} in place
     * of the original directory — modeling a slice that pruned exactly the helper class.
     */
    private String classpathWithHelperRemoved(Path tmp) throws IOException {
        String classFile = SlicedAwayHelper.class.getName().replace('.', '/') + ".class";
        String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        List<String> out = new ArrayList<>();
        for (String entry : entries) {
            Path p = Path.of(entry);
            if (Files.isDirectory(p) && Files.isRegularFile(p.resolve(classFile))) {
                Path mirror = Files.createDirectory(tmp.resolve("sliced"));
                copyDirExcluding(p, mirror, classFile);
                out.add(mirror.toString());
            } else {
                out.add(entry); // jars and unrelated dirs pass through unchanged, exactly as slicing does
            }
        }
        return String.join(java.io.File.pathSeparator, out);
    }

    /** Copy every file under {@code src} into {@code dest}, skipping the one relative path {@code exclude}. */
    private void copyDirExcluding(Path src, Path dest, String exclude) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(p)) {
                    continue;
                }
                String rel = src.relativize(p).toString().replace('\\', '/');
                if (rel.equals(exclude)) {
                    continue; // the sliced-away class
                }
                Path target = dest.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.copy(p, target);
            }
        }
    }
}
