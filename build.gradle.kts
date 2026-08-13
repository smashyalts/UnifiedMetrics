/*
 *     This file is part of UnifiedMetrics.
 *
 *     UnifiedMetrics is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     UnifiedMetrics is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with UnifiedMetrics.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("kapt") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false

    // The fabric-loom plugin must be defined in the root project for it to function properly.
    id("fabric-loom") version "1.10.5" apply false
}

allprojects {
    group = "dev.cubxity.plugins"
    description = "Fully featured metrics collector agent for Minecraft servers."
    version = "0.3.10-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "signing")
    apply(plugin = "maven-publish")

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }
    configure<JavaPluginExtension> {
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "central"
                url = if (version.toString().endsWith("SNAPSHOT")) {
                    uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                } else {
                    uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                }
                credentials {
                    username = System.getenv("MAVEN_REPO_USER")
                    password = System.getenv("MAVEN_REPO_PASS")
                }
            }
        }
    }
    afterEvaluate {
        configure<SigningExtension> {
            // Sign only when a key is actually configured.
            //
            // Unconditional signing makes the build unusable anywhere that is
            // not a release machine: any consumer building this from source --
            // JitPack, a composite build, a fork's CI -- fails on
            //
            //   Cannot perform signing task ':unifiedmetrics-api:
            //   signMavenJavaPublication' because it has no configured signatory
            //
            // which has nothing to do with what they were building. Releases are
            // unaffected: with signing.keyId present this behaves exactly as
            // before, and `isRequired` still refuses to publish an unsigned
            // artifact from a machine that meant to sign one.
            isRequired = project.hasProperty("signing.keyId") || System.getenv("GPG_KEY_ID") != null
            if (isRequired) {
                sign(configurations["archives"])
            }
        }
        tasks.findByName("shadowJar")?.also {
            tasks.named("assemble") { dependsOn(it) }
            // signArchives only exists when signing is configured -- see above.
            // findByName rather than named(), which throws on a missing task and
            // fails configuration for the whole project.
            tasks.findByName("signArchives")?.dependsOn(it)
        }
    }
}