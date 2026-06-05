import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// Resource-only jar that bundles the JBMC engine for Linux x64. The binary is
// fetched and extracted at OUR build time and packed into the published artifact,
// so consumers receive it as an ordinary, integrity-verified dependency — never a
// download at their test time. Nothing binary is committed to git.
//
// Linux engine jars are assembled from the upstream CBMC release .deb (a Debian
// `ar` archive whose `data.tar.gz` member holds /usr/bin/jbmc + /usr/lib/core-models.jar).
// Extraction relies on `dpkg-deb`, so this module can only be ASSEMBLED on Linux;
// its assembly tasks are gated with `onlyIf` so the build still configures on any OS.

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
val platformId = "linux-x64"
val asset = "ubuntu-24.04-cbmc-6.9.0-Linux.deb"
// SHA-256 of the upstream .deb, pinned for integrity (verified against the GitHub
// release API digest). A mismatch fails the build rather than packaging a tampered binary.
val assetSha256 = "3de313799ec2afc0154dad0e9d72db25d55aab007fa4652bf237626b808ca932"

val isThisPlatform = org.gradle.internal.os.OperatingSystem.current().isLinux &&
    !System.getProperty("os.arch", "").lowercase().let { it.contains("aarch64") || it.contains("arm") }

val downloadDir = layout.buildDirectory.dir("download")
val stageDir = layout.buildDirectory.dir("engine")              // -> jbmc/<platform>/... inside the jar
val engineDir = layout.buildDirectory.dir("engine/jbmc/$platformId")

val prepareEngine by tasks.registering {
    onlyIf("Linux x64 engine jar can only be assembled on Linux x64") { isThisPlatform }

    val out = engineDir.get().asFile
    val deb = downloadDir.get().file(asset).asFile
    val extractDir = downloadDir.get().dir("extracted").asFile
    outputs.dir(stageDir)

    doLast {
        val exe = File(out, "bin/jbmc")
        val models = File(out, "lib/core-models.jar")

        if (!exe.exists() || !models.exists()) {
            if (!deb.exists()) {
                deb.parentFile.mkdirs()
                val url = URI.create(
                    "https://github.com/diffblue/cbmc/releases/download/$cbmcVersion/$asset")
                logger.lifecycle("Downloading $url")
                val client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL).build()
                val resp = client.send(
                    HttpRequest.newBuilder(url).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(deb.toPath()))
                if (resp.statusCode() != 200) {
                    throw GradleException("Download failed (${resp.statusCode()}) for $url")
                }
            }

            // Integrity check BEFORE extracting/executing anything.
            val actual = sha256(deb)
            if (!actual.equals(assetSha256, ignoreCase = true)) {
                throw GradleException(
                    "Checksum mismatch for $asset\n  expected $assetSha256\n  actual   $actual")
            }

            extractDir.mkdirs()
            logger.lifecycle("Extracting $asset")
            // dpkg-deb unpacks the .deb's data tree (./usr/...) into extractDir.
            val proc = ProcessBuilder("dpkg-deb", "-x", deb.absolutePath, extractDir.absolutePath)
                .redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            if (code != 0) throw GradleException("dpkg-deb extraction failed with exit code $code")

            File(out, "bin").mkdirs()
            File(out, "lib").mkdirs()
            File(extractDir, "usr/bin/jbmc").copyTo(exe, overwrite = true)
            File(extractDir, "usr/lib/core-models.jar").copyTo(models, overwrite = true)
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
