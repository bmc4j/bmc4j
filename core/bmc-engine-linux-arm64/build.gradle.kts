import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// Resource-only jar that bundles the JBMC engine for Linux arm64. The binary is
// fetched and extracted at OUR build time and packed into the published artifact,
// so consumers receive it as an ordinary, integrity-verified dependency — never a
// download at their test time. Nothing binary is committed to git.
//
// Linux engine jars are assembled from the upstream CBMC release .deb (a Debian
// `ar` archive whose `data.tar.gz` member holds /usr/bin/jbmc + /usr/lib/core-models.jar).
// Extraction relies on `dpkg-deb`, so this module can only be ASSEMBLED on Linux arm64;
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
val platformId = "linux-arm64"
val asset = "ubuntu-24.04-arm64-cbmc-6.9.0-Linux.deb"
// SHA-256 of the upstream .deb, pinned for integrity (verified against the GitHub
// release API digest). A mismatch fails the build rather than packaging a tampered binary.
val assetSha256 = "b22f18c0ae43072b828487424e4392ec78f96591b90ee0b564e5a4513326dccc"

// KISSAT SAT solver, bundled ALONGSIDE jbmc in this engine jar (built once per
// platform in bmc4j/kissat-builds and published as a SHA-pinned release asset). It is
// shipped and integrity-verified but NOT wired into the run path - BundledEngine exposes
// its extracted path; nothing invokes it yet. Bump kissatBuildTag + kissatSha256 together.
val kissatBuilderRepo = "bmc4j/kissat-builds"
val kissatVersion = "kissat-4.0.4"
val kissatBuildTag = "kissat-4.0.4-r1"
val kissatAsset = "kissat-linux-arm64"
val kissatSha256 = "cf03c0165bdc0272bfb9a9067775c5aaa9855fb451dd5fe64532be84054be5bb"


val isThisPlatform = org.gradle.internal.os.OperatingSystem.current().isLinux &&
    System.getProperty("os.arch", "").lowercase().let { it.contains("aarch64") || it.contains("arm") }

val downloadDir = layout.buildDirectory.dir("download")
val stageDir = layout.buildDirectory.dir("engine")              // -> jbmc/<platform>/... inside the jar
val engineDir = layout.buildDirectory.dir("engine/jbmc/$platformId")

val prepareEngine by tasks.registering {
    onlyIf("Linux arm64 engine jar can only be assembled on Linux arm64") { isThisPlatform }

    val out = engineDir.get().asFile
    val deb = downloadDir.get().file(asset).asFile
    val extractDir = downloadDir.get().dir("extracted").asFile
    val kissatDl = downloadDir.get().file(kissatAsset).asFile
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
                // Transient 5xx / IOExceptions from the release CDN flake CI; retry a
                // few times with a short backoff. 4xx is a real error and fails at once.
                val backoffMs = longArrayOf(5_000, 15_000)
                var attempt = 0
                while (true) {
                    try {
                        val resp = client.send(
                            HttpRequest.newBuilder(url).GET().build(),
                            HttpResponse.BodyHandlers.ofFile(deb.toPath()))
                        val code = resp.statusCode()
                        if (code == 200) break
                        if (code < 500 || attempt >= backoffMs.size) {
                            throw GradleException("Download failed ($code) for $url")
                        }
                        logger.lifecycle("Download got HTTP $code, retrying in ${backoffMs[attempt] / 1000}s (attempt ${attempt + 2}/${backoffMs.size + 1}) for $url")
                    } catch (e: java.io.IOException) {
                        if (attempt >= backoffMs.size) throw GradleException("Download failed (${e.message}) for $url")
                        logger.lifecycle("Download failed (${e.message}), retrying in ${backoffMs[attempt] / 1000}s (attempt ${attempt + 2}/${backoffMs.size + 1}) for $url")
                    }
                    // BodyHandlers.ofFile may have written a partial file; drop it before retrying.
                    deb.delete()
                    Thread.sleep(backoffMs[attempt])
                    attempt++
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
