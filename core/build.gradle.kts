// Root build for the bmc4j tooling: a JUnit-integrated runtime and a Gradle
// plugin that auto-provisions JBMC. The examples/ in the repo root consume it
// via includeBuild, exactly as a real project applies the published plugin.
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.zip.ZipFile

// Repo-root license + third-party notices. The notices file carries the
// CBMC license, whose clause 2 requires the notice to travel with any binary
// redistribution — which every published jar is, and the engine jars especially.
val licenseFile = rootProject.file("../LICENSE")
val noticesFile = rootProject.file("../THIRD-PARTY-NOTICES.md")

// Single source for the GitHub repo slug, woven into the POM url/scm coordinates and
// the GitHub Packages repository url. The repo is mid-migration to a public org; when
// that lands, changing this one val re-points every published POM + the packages repo.
val repoSlug = "bmc4j/bmc4j"

subprojects {
    group = "org.bmc4j"
    // Single version source: core/gradle.properties (bmc4jVersion). The release
    // workflow checks the README quickstart against it before publishing.
    version = providers.gradleProperty("bmc4jVersion").get()

    repositories {
        mavenCentral()
    }

    // Embed LICENSE + THIRD-PARTY-NOTICES.md in META-INF/ of every jar this build
    // produces, so the legal text travels with each artifact independently of the repo.
    plugins.withType(JavaPlugin::class.java) {
        tasks.withType(Jar::class.java).configureEach {
            metaInf {
                from(licenseFile)
                from(noticesFile)
            }
        }
        // Maven Central requires a -sources and -javadoc jar alongside every published
        // artifact. Wire them once here for every JVM module. Resource-only modules (the
        // engine jars) have no Java sources, so these resolve to present-but-empty jars —
        // which is exactly what Central wants (a jar per coordinate, content optional).
        extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    // Any module that publishes (the per-platform engine jars, runtime, plugin, models, …)
    // gets the GitHub Packages repository in addition to the default `publishToMavenLocal`
    // target. The engine-jars CI matrix publishes the platform jars here; `publishToMavenLocal`
    // is unaffected (it's a built-in target that ignores declared remote repositories).
    // Credentials come from GITHUB_ACTOR/GITHUB_TOKEN (the values GitHub Actions injects),
    // with gradle-property fallbacks (gpr.user / gpr.token) for local publishing.
    plugins.withType(MavenPublishPlugin::class.java) {
        extensions.configure(PublishingExtension::class.java) {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/$repoSlug")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                            ?: providers.gradleProperty("gpr.user").orNull
                        password = System.getenv("GITHUB_TOKEN")
                            ?: providers.gradleProperty("gpr.token").orNull
                    }
                }
                // Maven Central staging: a local DIRECTORY repo (-PcentralStagingDir=<abs path>).
                // The Central Publisher Portal takes one zipped bundle of the whole maven-repo
                // layout, so each CI job publishes its modules HERE (signed; signing is required
                // for Central), the release workflow merges the staging dirs from every platform
                // job, adds the .md5/.sha1 checksums Gradle doesn't generate for file:// repos,
                // and uploads the zip to the Portal API. Only declared when the property is set,
                // so ordinary builds/publishes are unaffected.
                providers.gradleProperty("centralStagingDir").orNull?.let { stagingDir ->
                    maven {
                        name = "CentralStaging"
                        url = uri(java.io.File(stagingDir).toURI())
                    }
                }
            }
            // License metadata on every published POM (required by Maven Central / the
            // Plugin Portal). bmc4j itself is Apache-2.0; the engine jars additionally
            // REDISTRIBUTE the CBMC binaries, so their POM also carries the CBMC license
            // and every jar embeds THIRD-PARTY-NOTICES.md (see above).
            publications.withType(MavenPublication::class.java).configureEach {
                pom {
                    // Per-module identity + the shared coordinates Maven Central requires
                    // (name/description/url/scm/developers). url+scm derive from repoSlug so
                    // the in-progress org move is a one-line change.
                    name.set(project.name)
                    description.set(
                        "bmc4j (${project.name}): bounded model checking for JVM tests, powered by JBMC.")
                    url.set("https://github.com/$repoSlug")
                    scm {
                        connection.set("scm:git:https://github.com/$repoSlug.git")
                        developerConnection.set("scm:git:ssh://git@github.com/$repoSlug.git")
                        url.set("https://github.com/$repoSlug")
                    }
                    developers {
                        developer {
                            id.set("bmc4j")
                            name.set("bmc4j contributors")
                            url.set("https://bmc4j.org")
                        }
                    }
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            distribution.set("repo")
                        }
                        if (project.name.startsWith("bmc-engine-")) {
                            license {
                                name.set("CBMC License (BSD-4-Clause style, bundled engine binaries)")
                                url.set("https://github.com/diffblue/cbmc/blob/develop/LICENSE")
                                distribution.set("repo")
                            }
                        }
                    }
                }
            }
        }

        // Maven Central requires every published artifact to be GPG-signed. We apply the
        // signing plugin and sign all publications, but ONLY when a key is supplied — so
        // ordinary builds (and publishToMavenLocal) without the key still succeed.
        //
        // To enable signing, set BOTH:
        //   - SIGNING_KEY      (env) or signingKey      (gradle property): ASCII-armored
        //                       PGP private key block.
        //   - SIGNING_PASSWORD (env) or signingPassword (gradle property): its passphrase
        //                       (empty string if the key has none).
        // These are Courtney's to provide (e.g. as CI secrets); none is invented here.
        val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
            ?: project.findProperty("signingKey") as String?
        val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
            ?: project.findProperty("signingPassword") as String?
        if (signingKey != null) {
            apply(plugin = "signing")
            extensions.configure(org.gradle.plugins.signing.SigningExtension::class.java) {
                useInMemoryPgpKeys(signingKey, signingPassword ?: "")
                val publishing = extensions.getByType(PublishingExtension::class.java)
                sign(publishing.publications)
            }
        }

        // Publish-time guard for the per-platform engine jars. Each bmc-engine-<platform>
        // jar can only be POPULATED on its matching host OS (prepareEngine is onlyIf host
        // OS); on every other host it builds as a ~6.6 KB META-INF-only placeholder. Nothing
        // otherwise blocks publishing that empty placeholder, and a consumer who resolves it
        // hits BundledEngine's "missing files.txt" failure at runtime. So before ANY publish
        // (publishToMavenLocal or a remote PublishToMavenRepository — i.e. the real per-OS
        // release path), assert the jar this publication will upload actually contains the
        // platform binary and the bundled core-models.jar. This is attached to publish tasks
        // only, NOT to build/jar, so a dev on another OS can still build/test without the
        // binary; only PUBLISHING an empty jar is blocked.
        if (project.name.startsWith("bmc-engine-")) {
            val platformId = project.name.removePrefix("bmc-engine-")
            val exeRel = "jbmc/$platformId/bin/jbmc" + (if (platformId.startsWith("windows")) ".exe" else "")
            val modelsRel = "jbmc/$platformId/lib/core-models.jar"
            // The artifact the publication uploads is the module's jar in build/libs.
            val jarTask = tasks.named("jar", Jar::class.java)
            tasks.withType(AbstractPublishToMaven::class.java).configureEach {
                dependsOn(jarTask)
                doFirst {
                    val jarFile = jarTask.get().archiveFile.get().asFile
                    if (!jarFile.isFile) {
                        throw GradleException(
                            "Engine jar for platform '$platformId' was not built: $jarFile\n" +
                                "Run :${project.name}:jar on the matching host OS before publishing.")
                    }
                    val entries = ZipFile(jarFile).use { zip ->
                        zip.entries().asSequence().map { it.name }.toSet()
                    }
                    val missing = listOf(exeRel, modelsRel).filterNot { it in entries }
                    if (missing.isNotEmpty()) {
                        throw GradleException(
                            "Refusing to publish an EMPTY engine jar for platform '$platformId'.\n" +
                                "  jar:     $jarFile\n" +
                                "  missing: ${missing.joinToString(", ")}\n" +
                                "This placeholder jar lacks the bundled JBMC engine — it can only be " +
                                "populated on a '$platformId' host (prepareEngine is gated to that OS). " +
                                "Build and publish this module from a matching host OS / CI runner.")
                    }
                }
            }
        }
    }

    // Every JVM module gets a JUnit 5 + JaCoCo test setup. Modules with no
    // src/test simply run an empty test task.
    plugins.withType(JavaPlugin::class.java) {
        apply(plugin = "jacoco")
        dependencies {
            add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
            // Gradle 9 no longer auto-provides the JUnit Platform launcher on the test
            // runtime classpath; declare it explicitly so test executors can start.
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")
        }
        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
        tasks.withType(JacocoReport::class.java).configureEach {
            reports { xml.required.set(true) }
        }
    }
}
