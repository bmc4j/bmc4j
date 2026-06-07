package org.bmc4j.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The handshake between the `bmc-contracts` annotation processor and `JbmcBackend`.
 * The processor emits one line per contracted method and one line per generated enforce-proof
 * class into [RESOURCE] on the analysis classpath; the backend reads it back to decide
 * how to rewrite call sites for a given proof:
 *
 * ```
 * contract <ownerInternal> <name> <descriptor> <stubOwnerInternal> <stubName>
 * contract <ownerInternal> <name> <descriptor> <stubOwnerInternal> <stubName> instance <stubDescriptor>
 * enforce  <proofClassInternal>
 * ```
 *
 * A 6-token `contract` line is a static target (the call site is `invokestatic`, the stub keeps
 * the descriptor). An 8-token line with `instance` is a pure-instance target: the call site is
 * `invokevirtual`/`invokeinterface`, and `<stubDescriptor>` is the receiver-prepended descriptor
 * of the generated static stub.
 *
 * - `contract` lines become [ContractRewriter.Redirect]s (replace direction).
 * - `enforce` lines name the generated proof classes; when one of *those* is the
 *   analysis entry, the backend excludes it as a caller so the proof sees the real body
 *   (modular enforce). Any other entry is a replace proof and is rewritten fully.
 */
class ContractManifest private constructor(
        private val redirects: List<ContractRewriter.Redirect>,
        private val enforceProofClasses: Set<String>) {

    /** Redirects for the replace direction, one per contracted method. */
    @JvmName("redirects")
    fun redirects(): List<ContractRewriter.Redirect> = redirects

    /** Internal names of the generated enforce-proof classes. */
    @JvmName("enforceProofClasses")
    fun enforceProofClasses(): Set<String> = enforceProofClasses

    val isEmpty: Boolean
        get() = redirects.isEmpty() && enforceProofClasses.isEmpty()

    companion object {

        const val RESOURCE = "META-INF/bmc-contracts.txt"

        // --- formatting (used by the processor) ---

        @JvmStatic
        @JvmOverloads
        fun contractLine(ownerInternal: String, name: String, descriptor: String,
                         stubOwnerInternal: String, stubName: String,
                         instance: Boolean = false, stubDescriptor: String? = null): String {
            val base = listOf("contract", ownerInternal, name, descriptor, stubOwnerInternal, stubName)
            // A static target stays a 6-token line (unchanged format). A pure-instance target appends
            // `instance <stubDescriptor>` so the backend knows to match the virtual call site and emit
            // the receiver-prepended stub descriptor.
            return if (instance) {
                (base + listOf("instance", stubDescriptor ?: descriptor)).joinToString(" ")
            } else {
                base.joinToString(" ")
            }
        }

        @JvmStatic
        fun enforceLine(proofClassInternal: String): String = "enforce $proofClassInternal"

        // --- parsing (used by the backend) ---

        @JvmStatic
        fun parse(lines: List<String>): ContractManifest {
            val redirects = mutableListOf<ContractRewriter.Redirect>()
            val enforce = LinkedHashSet<String>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }
                val t = line.split(Regex("\\s+"))
                if (t[0] == "contract" && t.size == 6) {
                    // Static target: invokestatic, stub keeps the descriptor.
                    redirects.add(ContractRewriter.Redirect(t[1], t[2], t[3], t[4], t[5]))
                } else if (t[0] == "contract" && t.size == 8 && t[6] == "instance") {
                    // Pure-instance target: invokevirtual/interface call site, receiver-prepended stub
                    // descriptor in t[7].
                    redirects.add(ContractRewriter.Redirect(t[1], t[2], t[3], t[4], t[5],
                            true, t[7]))
                } else if (t[0] == "enforce" && t.size == 2) {
                    enforce.add(t[1])
                }
            }
            return ContractManifest(redirects, enforce)
        }

        /** Read and merge every [RESOURCE] found in the directory entries of [classpath]. */
        @JvmStatic
        fun readFromClasspath(classpath: String?): ContractManifest {
            val lines = mutableListOf<String>()
            if (classpath != null) {
                for (entry in classpath.split(File.pathSeparator)) {
                    if (entry.isEmpty()) {
                        continue
                    }
                    val res = Path.of(entry).resolve(RESOURCE)
                    if (Files.isRegularFile(res)) {
                        try {
                            lines.addAll(Files.readAllLines(res))
                        } catch (ignored: IOException) {
                            // best effort: a manifest we can't read just yields no contracts
                        }
                    }
                }
            }
            return parse(lines)
        }
    }
}
