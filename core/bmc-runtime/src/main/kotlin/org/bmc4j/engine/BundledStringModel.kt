package org.bmc4j.engine

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Extracts the sound char-array-backed `java.lang.String` / `StringBuilder` / `AbstractStringBuilder` /
 * `StringBuffer` models (bundled as resources under `bmc-string-model/` in this jar) to a local
 * directory, so JBMC can be pointed at them - but ONLY under string refinement OFF (the
 * `--no-refine-strings` / `StringMode.CHAR_ARRAY_MODEL` path; prepended when `request.stringMode == CHAR_ARRAY_MODEL`).
 *
 * ## Why these are resources, not a classpath dependency
 * They carry the real fully-qualified JDK names (`java.lang.String`, ...). On a real JVM the bootstrap
 * loader always wins for `java.*`, but a stray copy on a test JVM's classpath could still shadow the
 * real classes and break tests - so, exactly like [BundledKotlinModels], they ship as inert resources
 * and reach ONLY JBMC's analysis classpath at verification time.
 *
 * ## Why only under no-refine
 * With refinement ON (the default) JBMC's string-refinement solver IS the sound String model, and these
 * classes must NOT shadow it. With refinement OFF the cbmc `core-models.jar` String/StringBuilder are
 * degenerate intrinsic-only shells (length -> nondetInt, charAt -> a placeholder, StringBuilder.toString
 * -> a possibly-null nondet), so a String's backing is null and a correct property like
 * `Buffer().writeUtf8("ab"); size==2` false-REFUTES with a NullPointerException. These models supply a
 * genuine `char[]` backing so construction / `length()` / `charAt()` become sound array operations.
 *
 * ## Atomic, content-keyed extraction
 * Identical discipline to [BundledKotlinModels]: read the resources into memory, hash them, publish once
 * into a `<sha256>` directory marked complete with a `.done` file. A completed content dir is immutable,
 * so concurrent proofs (the JUnit pool) or other agents sharing `~/.cache/bmc4j/` never observe a
 * partial/mid-overwrite model class (the torn read that intermittently makes JBMC nondet-stub a
 * present-on-classpath model body).
 */
object BundledStringModel {

    private const val ROOT = "bmc-string-model"
    private val FILES = arrayOf(
            "java/lang/String.class",
            "java/lang/AbstractStringBuilder.class",
            "java/lang/StringBuilder.class",
            "java/lang/StringBuffer.class")

    /**
     * Extract the models and return the classpath root dir, or null if none bundled. See the class doc
     * for the atomic, content-keyed publish.
     */
    @JvmStatic
    fun extractRoot(): String? {
        val contents = LinkedHashMap<String, ByteArray>()
        for (rel in FILES) {
            try {
                BundledStringModel::class.java.classLoader
                        .getResourceAsStream("$ROOT/$rel").use { input ->
                            if (input != null) {
                                contents[rel] = input.readAllBytes()
                            }
                        }
            } catch (e: IOException) {
                // Best effort: if a model can't be read, JBMC falls back to the cbmc core-models class.
            }
        }
        if (contents.isEmpty()) {
            return null
        }

        val root = cacheRoot()
        val hash = contentHash(contents)
        val dest = root.resolve(hash)
        val done = root.resolve(hash + DONE_SUFFIX)

        if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
            return dest.toString()
        }

        try {
            Files.createDirectories(root)
            val tmp = Files.createTempDirectory(root, "$hash-")
            try {
                for ((rel, bytes) in contents) {
                    val target = tmp.resolve(rel)
                    Files.createDirectories(target.parent)
                    Files.write(target, bytes)
                }
                if (!Files.isDirectory(dest)) {
                    try {
                        Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE)
                    } catch (raced: FileAlreadyExistsException) {
                        // another run published first - its content equals ours, so reuse it
                    } catch (atomicUnsupported: IOException) {
                        if (!Files.isDirectory(dest)) {
                            Files.move(tmp, dest)
                        }
                    }
                }
                if (!Files.isRegularFile(done)) {
                    Files.write(done, ByteArray(0)) // completion marker last
                }
            } finally {
                deleteRecursivelyIfExists(tmp) // no-op if the move consumed it
            }
        } catch (e: IOException) {
            if (Files.isDirectory(dest) && Files.isRegularFile(done)) {
                return dest.toString()
            }
            return null
        }
        return if (Files.isDirectory(dest)) dest.toString() else null
    }

    private const val DONE_SUFFIX = ".done"

    private fun cacheRoot(): Path =
            Path.of(System.getProperty("user.home"), ".cache", "bmc4j", "string-model")

    private fun contentHash(contents: Map<String, ByteArray>): String {
        val md = try {
            MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 unavailable", e)
        }
        for (rel in contents.keys.sorted()) {
            val relBytes = rel.replace('\\', '/').toByteArray(StandardCharsets.UTF_8)
            md.update(intToBytes(relBytes.size))
            md.update(relBytes)
            val bytes = contents.getValue(rel)
            md.update(intToBytes(bytes.size))
            md.update(bytes)
        }
        return toHex(md.digest())
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
            (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun deleteRecursivelyIfExists(dir: Path) {
        if (!Files.exists(dir)) {
            return
        }
        Files.walk(dir).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.deleteIfExists(p)
                } catch (ignored: IOException) {
                    // best-effort temp cleanup; a leftover temp dir never affects correctness
                }
            }
        }
    }

    private fun toHex(d: ByteArray): String {
        val sb = StringBuilder(d.size * 2)
        for (b in d) {
            sb.append(Character.forDigit((b.toInt() shr 4) and 0xf, 16))
                    .append(Character.forDigit(b.toInt() and 0xf, 16))
        }
        return sb.toString()
    }
}
