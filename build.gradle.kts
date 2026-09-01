import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    `java-library`
    `maven-publish`
    jacoco
    id("com.diffplug.spotless") version "8.10.1"
}

group = "io.putdotio"
version = "0.1.0-SNAPSHOT"

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "putio-sdk-kotlin"

            pom {
                name.set("putio-sdk-kotlin")
                description.set("Coroutine-first Kotlin SDK for the put.io API")
                url.set("https://github.com/putdotio/putio-sdk-kotlin")

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
    }
}
