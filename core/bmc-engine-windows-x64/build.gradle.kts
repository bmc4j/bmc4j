import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// Resource-only jar that bundles the JBMC engine for Windows x64. The binary is
// fetched and extracted at OUR build time and packed into the published artifact,
// so consumers receive it as an ordinary, integrity-verified dependency — never a
// download at their test time. Nothing binary is committed to git.
//
// This module can only be built on Windows (extraction uses msiexec). The Linux
// and macOS engine jars follow the identical pattern on their own CI runners.

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
val platformId = "windows-x64"
val asset = "$cbmcVersion-win64.msi"
// SHA-256 of the upstream .msi, pinned for integrity (verified against the GitHub
// release API digest). A mismatch fails the build rather than packaging a tampered binary.
val assetSha256 = "46338b006958b9844ca394bec982e4197b7892c114596cd8357a25843b5e14cb"

// Extraction uses msiexec, so this module can only be ASSEMBLED on Windows — gate it
// like the other engine modules so `-p core build` works on any OS (this was the only
// engine module without the gate; the linux CI gate failed in msiexec without it).
val isThisPlatform = org.gradle.internal.os.OperatingSystem.current().isWindows

val downloadDir = layout.buildDirectory.dir("download")
val stageDir = layout.buildDirectory.dir("engine")              // -> jbmc/<platform>/... inside the jar
val engineDir = layout.buildDirectory.dir("engine/jbmc/$platformId")

val prepareEngine by tasks.registering {
    onlyIf("Windows x64 engine jar can only be assembled on Windows") { isThisPlatform }

    val out = engineDir.get().asFile
    val msi = downloadDir.get().file(asset).asFile
    val extractDir = downloadDir.get().dir("extracted").asFile
    outputs.dir(stageDir)

    doLast {
        val exe = File(out, "bin/jbmc.exe")
        val models = File(out, "lib/core-models.jar")

        if (!exe.exists() || !models.exists()) {
            if (!msi.exists()) {
                msi.parentFile.mkdirs()
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
                            HttpResponse.BodyHandlers.ofFile(msi.toPath()))
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
                    msi.delete()
                    Thread.sleep(backoffMs[attempt])
                    attempt++
                }
            }

            // Integrity check BEFORE extracting/executing anything (the cached msi is
            // re-verified too, not just a fresh download).
            val actual = sha256(msi)
            if (!actual.equals(assetSha256, ignoreCase = true)) {
                throw GradleException(
                    "Checksum mismatch for $asset\n  expected $assetSha256\n  actual   $actual")
            }

            extractDir.mkdirs()
            logger.lifecycle("Extracting $asset")
            val proc = ProcessBuilder(
                "msiexec", "/a", msi.absolutePath, "/qn", "TARGETDIR=${extractDir.absolutePath}")
                .redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            if (code != 0) throw GradleException("msiexec extraction failed with exit code $code")

            File(out, "bin").mkdirs()
            File(out, "lib").mkdirs()
            File(extractDir, "cbmc/bin/jbmc.exe").copyTo(exe, overwrite = true)
            File(extractDir, "cbmc/lib/core-models.jar").copyTo(models, overwrite = true)
        }

        // Manifest of files the runtime extractor should unpack, plus a version
        // marker used as the extraction cache key.
        File(out, "files.txt").writeText("bin/jbmc.exe\nlib/core-models.jar\n")
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
