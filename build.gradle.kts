import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    `java-library`
    jacoco
    id("com.diffplug.spotless") version "8.10.1"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

// Reverse DNS of put.io; the Kotlin package stays io.putdotio.sdk.
group = "io.put"
// Release tags set the version: `./gradlew publish -Pversion=0.1.0`. Local builds stay SNAPSHOT.
version = providers.gradleProperty("version").orNull?.takeUnless { it == "unspecified" } ?: "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

jacoco {
    toolVersion = "0.8.13"
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val liveTestSourceSet =
    sourceSets.create("liveTest") {
        compileClasspath += sourceSets["main"].output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

configurations[liveTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[liveTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.5.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver3")

    add(liveTestSourceSet.implementationConfigurationName, kotlin("test"))
    add(liveTestSourceSet.implementationConfigurationName, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.90")
            }
        }
    }
}

val liveTest by tasks.registering(Test::class) {
    description = "Run opt-in live SDK verification against the real put.io API"
    group = "verification"
    testClassesDirs = liveTestSourceSet.output.classesDirs
    classpath = liveTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.register("verify") {
    group = "verification"
    description = "Run the canonical local verification checks"
    dependsOn("check", "jar", "jacocoTestCoverageVerification")
}

// Only the release workflow passes -Pversion; a default SNAPSHOT must never reach Central.
tasks.matching { it.name.startsWith("publish") && !it.name.endsWith("ToMavenLocal") }.configureEach {
    doFirst {
        check(!project.version.toString().endsWith("-SNAPSHOT")) {
            "Remote publishing needs an explicit release version, e.g. -Pversion=0.1.0"
        }
    }
}

mavenPublishing {
    // Central Portal publishing; credentials come from ORG_GRADLE_PROJECT_mavenCentralUsername/Password.
    // Waits for Central validation, then releases without a manual portal step.
    publishToMavenCentral(automaticRelease = true)
    // Central requires signed artifacts. The release workflow supplies the key through
    // ORG_GRADLE_PROJECT_signingInMemoryKey*; without one, local dry runs publish unsigned to mavenLocal.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    coordinates(group.toString(), "putio-sdk-kotlin", version.toString())

    pom {
        name.set("putio-sdk-kotlin")
        description.set("Coroutine-first Kotlin SDK for the put.io API")
        url.set("https://github.com/putdotio/putio-sdk-kotlin")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/license/mit")
            }
        }

        developers {
            developer {
                id.set("putdotio")
                name.set("put.io")
                email.set("devs@put.io")
            }
        }

        scm {
            connection.set("scm:git:https://github.com/putdotio/putio-sdk-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/putdotio/putio-sdk-kotlin.git")
            url.set("https://github.com/putdotio/putio-sdk-kotlin")
        }
    }
}
