import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    `maven-publish`
}

group = "io.putdotio"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
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

val liveTestSourceSet = sourceSets.create("liveTest") {
    compileClasspath += sourceSets["main"].output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[liveTestSourceSet.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[liveTestSourceSet.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver3")

    add(liveTestSourceSet.implementationConfigurationName, kotlin("test"))
    add(liveTestSourceSet.implementationConfigurationName, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.test {
    useJUnitPlatform()
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
    dependsOn("check", "jar")
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
