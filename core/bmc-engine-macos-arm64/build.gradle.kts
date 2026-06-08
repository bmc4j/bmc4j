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

// KISSAT SAT solver, bundled ALONGSIDE jbmc in this engine jar (built once per
// platform in bmc4j/kissat-builds and published as a SHA-pinned release asset). It is
// shipped and integrity-verified but NOT wired into the run path - BundledEngine exposes
// its extracted path; nothing invokes it yet. Bump kissatBuildTag + kissatSha256 together.
val kissatBuilderRepo = "bmc4j/kissat-builds"
val kissatVersion = "kissat-4.0.4"
val kissatBuildTag = "kissat-4.0.4-r1"
val kissatAsset = "kissat-macos-arm64"
val kissatSha256 = "e146ffb4306dd85347ccf6365b7ff395aa5af35d658b459d6cb2766c226562e1"


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
    val kissatDl = downloadDir.get().file(kissatAsset).asFile
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

        // KISSAT: fetch + SHA-verify + stage alongside jbmc as bin/kissat. Bundled, not
        // wired into the run path. Its LICENSE (MIT) ships inside the jar next to jbmc.
        val kissat = File(out, "bin/kissat")
        val staged = stageKissat(kissatDl, kissat, kissatAsset, kissatSha256,
            "https://github.com/$kissatBuilderRepo/releases/download/$kissatBuildTag/$kissatAsset",
            "https://raw.githubusercontent.com/$kissatBuilderRepo/main/LICENSE",
            File(out, "KISSAT-LICENSE"), required = true, logger = logger)

        // Manifest of files the runtime extractor should unpack, plus a version
        // marker used as the extraction cache key. kissat is appended only when staged.
        val manifest = StringBuilder("bin/jbmc\nlib/core-models.jar\n")
        if (staged) {
            manifest.append("bin/kissat\nKISSAT-LICENSE\n")
            File(out, "kissat-version.txt").writeText(kissatVersion)
        }
        File(out, "files.txt").writeText(manifest.toString())
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


// Fetch a kissat binary (and its LICENSE) from kissat-builds, SHA-256-verify the
// binary, and stage it as [kissatTarget] (+ the LICENSE). Returns true if staged.
// When [required] is false a missing release asset (HTTP 404) is tolerated so the
// engine jar still builds without kissat (the optional-per-platform case, e.g.
// windows-x64 before its kissat binary exists) - any other failure still fails the build.
fun stageKissat(download: File, kissatTarget: File, assetName: String, expectedSha: String,
                assetUrl: String, licenseUrl: String, licenseTarget: File,
                required: Boolean, logger: org.gradle.api.logging.Logger): Boolean {
    if (!kissatTarget.exists()) {
        download.parentFile.mkdirs()
        val fetched = fetchToFile(URI.create(assetUrl), download, allowMissing = !required, logger = logger)
        if (!fetched) {
            logger.lifecycle("kissat not bundled for $assetName (release asset absent; engine jar builds without it)")
            return false
        }
        val actual = sha256(download)
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            throw GradleException("Checksum mismatch for $assetName\n  expected $expectedSha\n  actual   $actual")
        }
        kissatTarget.parentFile.mkdirs()
        download.copyTo(kissatTarget, overwrite = true)
        kissatTarget.setExecutable(true)
    }
    // MIT license must travel inside the jar next to the binary.
    fetchToFile(URI.create(licenseUrl), licenseTarget, allowMissing = false, logger = logger)
    return true
}

// Download [url] to [dest] with the same retry/backoff as the engine fetch. Returns
// false (instead of throwing) on a 404 when [allowMissing] is true.
fun fetchToFile(url: URI, dest: File, allowMissing: Boolean,
                logger: org.gradle.api.logging.Logger): Boolean {
    logger.lifecycle("Downloading $url")
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val backoffMs = longArrayOf(5_000, 15_000)
    var attempt = 0
    while (true) {
        try {
            val resp = client.send(
                HttpRequest.newBuilder(url).GET().build(),
                HttpResponse.BodyHandlers.ofFile(dest.toPath()))
            val code = resp.statusCode()
            if (code == 200) return true
            if (code == 404 && allowMissing) { dest.delete(); return false }
            if (code < 500 || attempt >= backoffMs.size) {
                throw GradleException("Download failed ($code) for $url")
            }
            logger.lifecycle("Download got HTTP $code, retrying in ${backoffMs[attempt] / 1000}s (attempt ${attempt + 2}/${backoffMs.size + 1}) for $url")
        } catch (e: java.io.IOException) {
            if (attempt >= backoffMs.size) throw GradleException("Download failed (${e.message}) for $url")
            logger.lifecycle("Download failed (${e.message}), retrying in ${backoffMs[attempt] / 1000}s (attempt ${attempt + 2}/${backoffMs.size + 1}) for $url")
        }
        dest.delete()
        Thread.sleep(backoffMs[attempt])
        attempt++
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
