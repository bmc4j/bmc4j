import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// Resource-only jar that bundles the JBMC engine for musl/Alpine Linux x64. The
// binary is fetched and extracted at OUR build time and packed into the published
// artifact, so consumers receive it as an ordinary, integrity-verified dependency —
// never a download at their test time. Nothing binary is committed to git.
//
// WHY THIS MODULE NEEDS A SEPARATE SOURCE FROM bmc-engine-linux-x64:
// Upstream CBMC 6.9.0 publishes only glibc artifacts (the win64 .msi, the Ubuntu
// .deb's, the Homebrew bottles). There is NO musl/Alpine or static Linux release.
// A glibc-linked jbmc cannot exec on a musl C library (Alpine): the dynamic linker
// emits a confusing "not found". So the static musl jbmc is built — once per CBMC
// bump — in a dedicated builder repo (bmc4j/jbmc-musl-builds) and published there
// as a GitHub release asset. This module assembles its jar exactly like every other
// platform: FETCH the prebuilt tarball, SHA-256-verify it, extract, pack. No
// compiler runs here, so this module assembles in ~seconds on any Linux x64 host.
//
// The release tarball already carries the runtime extractor's layout
// (jbmc/linux-x64-musl/{bin/jbmc,lib/core-models.jar}); its core-models.jar is the
// architecture-independent operational model the builder took verbatim from the
// integrity-pinned glibc .deb, so the bundled model stays byte-for-byte identical
// to the other Linux engines.

plugins {
    `java-library`
    `maven-publish`
}

// 17 baseline so 17-targeting consumers (e.g. Kotlin 1.9) can resolve this
// resource-only jar (no Java sources; this only sets the variant's JVM version).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val cbmcVersion = "cbmc-6.9.0"
val platformId = "linux-x64-musl"

// The prebuilt static-musl engine tarball, published by bmc4j/jbmc-musl-builds.
// Pinned by SHA-256 for integrity (same model as the glibc .deb): a mismatch fails
// the build rather than packaging a tampered binary. Bump muslBuildTag + assetSha256
// together when a new builder release is cut.
val muslBuilderRepo = "bmc4j/jbmc-musl-builds"
val muslBuildTag = "cbmc-6.9.0-musl-r1"
val asset = "jbmc-6.9.0-linux-x64-musl.tar.gz"
val assetSha256 = "9cd3c9a2bd7fd6862a6252832a253943da1521b0497d85cc676b0794db31266b"

// Tarball extraction + the bundled binary is a Linux x64 resource, so (like the
// glibc module) assembly is gated to Linux x64 with `onlyIf` and produces an empty
// placeholder elsewhere. It does NOT require a musl host: the binary is prebuilt,
// not compiled here. `-p core build` still configures on any OS.
val isThisPlatform = org.gradle.internal.os.OperatingSystem.current().isLinux &&
    !System.getProperty("os.arch", "").lowercase().let { it.contains("aarch64") || it.contains("arm") }

val downloadDir = layout.buildDirectory.dir("download")
val stageDir = layout.buildDirectory.dir("engine")              // -> jbmc/<platform>/... inside the jar
val engineDir = layout.buildDirectory.dir("engine/jbmc/$platformId")

val prepareEngine by tasks.registering {
    onlyIf("Linux x64 musl/Alpine engine jar can only be assembled on Linux x64") { isThisPlatform }

    val out = engineDir.get().asFile
    val tarball = downloadDir.get().file(asset).asFile
    val extractDir = downloadDir.get().dir("extracted").asFile
    outputs.dir(stageDir)

    doLast {
        val exe = File(out, "bin/jbmc")
        val models = File(out, "lib/core-models.jar")

        if (!exe.exists() || !models.exists()) {
            if (!tarball.exists()) {
                tarball.parentFile.mkdirs()
                val url = URI.create(
                    "https://github.com/$muslBuilderRepo/releases/download/$muslBuildTag/$asset")
                logger.lifecycle("Downloading $url")
                val client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL).build()
                val resp = client.send(
                    HttpRequest.newBuilder(url).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(tarball.toPath()))
                if (resp.statusCode() != 200) {
                    throw GradleException("Download failed (${resp.statusCode()}) for $url")
                }
            }

            // Integrity check BEFORE extracting anything.
            val actual = sha256(tarball)
            if (!actual.equals(assetSha256, ignoreCase = true)) {
                throw GradleException(
                    "Checksum mismatch for $asset\n  expected $assetSha256\n  actual   $actual")
            }

            extractDir.deleteRecursively()
            extractDir.mkdirs()
            logger.lifecycle("Extracting $asset")
            // The tarball contains jbmc/linux-x64-musl/{bin/jbmc,lib/core-models.jar}.
            val proc = ProcessBuilder("tar", "-xzf", tarball.absolutePath, "-C", extractDir.absolutePath)
                .redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            if (code != 0) throw GradleException("tar extraction failed with exit code $code")

            File(out, "bin").mkdirs()
            File(out, "lib").mkdirs()
            File(extractDir, "jbmc/$platformId/bin/jbmc").copyTo(exe, overwrite = true)
            File(extractDir, "jbmc/$platformId/lib/core-models.jar").copyTo(models, overwrite = true)
            exe.setExecutable(true)
        }

        // Manifest of files the runtime extractor should unpack, plus a version
        // marker used as the extraction cache key.
        File(out, "files.txt").writeText("bin/jbmc\nlib/core-models.jar\n")
        File(out, "version.txt").writeText(cbmcVersion)
    }
}

fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

sourceSets.main {
    resources.srcDir(stageDir)
}

tasks.named("processResources") {
    dependsOn(prepareEngine)
}

// withSourcesJar() (applied centrally in core/build.gradle.kts) packs main.allSource,
// which includes the staged engine resources prepareEngine produces — declare the
// dependency Gradle's strict validation requires.
tasks.named("sourcesJar") {
    dependsOn(prepareEngine)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
