import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// Resource-only jar that bundles the JBMC engine for macOS arm64. The binary is
// fetched and extracted at OUR build time and packed into the published artifact,
// so consumers receive it as an ordinary, integrity-verified dependency — never a
// download at their test time. Nothing binary is committed to git.
//
// macOS engine binaries come from the Homebrew bottle for cbmc, hosted as an OCI
// blob on ghcr.io. The blob is a gzip tarball laying out cbmc/<ver>/bin/jbmc and
// cbmc/<ver>/libexec/lib/core-models.jar. Anonymous pull needs a short-lived bearer
// token from ghcr.io/token (fetched here in-JVM). Extraction relies on `tar`, so this
// module can only be ASSEMBLED on macOS; its assembly tasks are gated with `onlyIf`
// so the build still configures on any OS.

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
val cbmcBottleVersion = "6.9.0"   // bottle internal path: cbmc/<this>/bin/jbmc
val platformId = "macos-arm64"
// Homebrew bottle (ghcr.io OCI blob). arm64 macOS: the `arm64_sonoma` bottle
// (matches the macos-14 / Sonoma arm64 assembly runner).
val bottleSha256 = "311dc85117244dc3fc2bb567d6bdd8d36dbd8dff1bd2f3959546b85224c9e422"

val isThisPlatform = org.gradle.internal.os.OperatingSystem.current().isMacOsX &&
    System.getProperty("os.arch", "").lowercase().let { it.contains("aarch64") || it.contains("arm") }

val downloadDir = layout.buildDirectory.dir("download")
val stageDir = layout.buildDirectory.dir("engine")              // -> jbmc/<platform>/... inside the jar
val engineDir = layout.buildDirectory.dir("engine/jbmc/$platformId")

val prepareEngine by tasks.registering {
    onlyIf("macOS arm64 engine jar can only be assembled on macOS arm64") { isThisPlatform }

    val out = engineDir.get().asFile
    val bottle = downloadDir.get().file("cbmc-$platformId-bottle.tar.gz").asFile
    val extractDir = downloadDir.get().dir("extracted").asFile
    outputs.dir(stageDir)

    doLast {
        val exe = File(out, "bin/jbmc")
        val models = File(out, "lib/core-models.jar")

        if (!exe.exists() || !models.exists()) {
            if (!bottle.exists()) {
                bottle.parentFile.mkdirs()
                downloadBottle(bottleSha256, bottle)
            }

            // Integrity check BEFORE extracting/executing anything. The ghcr blob digest
            // IS its content sha256, so this is the bottle's pinned checksum.
            val actual = sha256(bottle)
            if (!actual.equals(bottleSha256, ignoreCase = true)) {
                throw GradleException(
                    "Checksum mismatch for macOS arm64 bottle\n  expected $bottleSha256\n  actual   $actual")
            }

            extractDir.mkdirs()
            logger.lifecycle("Extracting cbmc bottle ($platformId)")
            val proc = ProcessBuilder("tar", "xzf", bottle.absolutePath, "-C", extractDir.absolutePath)
                .redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            if (code != 0) throw GradleException("tar extraction failed with exit code $code")

            File(out, "bin").mkdirs()
            File(out, "lib").mkdirs()
            File(extractDir, "cbmc/$cbmcBottleVersion/bin/jbmc").copyTo(exe, overwrite = true)
            File(extractDir, "cbmc/$cbmcBottleVersion/libexec/lib/core-models.jar")
                .copyTo(models, overwrite = true)
        }

        // Manifest of files the runtime extractor should unpack, plus a version
        // marker used as the extraction cache key.
        File(out, "files.txt").writeText("bin/jbmc\nlib/core-models.jar\n")
        File(out, "version.txt").writeText(cbmcVersion)
    }
}

// Pull a Homebrew cbmc bottle blob from ghcr.io by digest, using an anonymous bearer token.
fun downloadBottle(digest: String, target: File) {
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val tokenUrl = URI.create(
        "https://ghcr.io/token?service=ghcr.io&scope=repository:homebrew/core/cbmc:pull")
    val tokenResp = client.send(
        HttpRequest.newBuilder(tokenUrl).GET().build(), HttpResponse.BodyHandlers.ofString())
    if (tokenResp.statusCode() != 200) {
        throw GradleException("ghcr token request failed (${tokenResp.statusCode()})")
    }
    val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(tokenResp.body())?.groupValues?.get(1)
        ?: throw GradleException("Could not parse ghcr token from response")

    val blobUrl = URI.create(
        "https://ghcr.io/v2/homebrew/core/cbmc/blobs/sha256:$digest")
    logger.lifecycle("Downloading cbmc bottle blob sha256:$digest")
    val resp = client.send(
        HttpRequest.newBuilder(blobUrl)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.oci.image.layer.v1.tar+gzip")
            .GET().build(),
        HttpResponse.BodyHandlers.ofFile(target.toPath()))
    if (resp.statusCode() != 200) {
        throw GradleException("Bottle blob download failed (${resp.statusCode()}) for $blobUrl")
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
