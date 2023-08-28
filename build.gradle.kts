plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    id("com.google.cloud.tools.jib") version "3.3.2"
}

group = "com.mrpowergamerbr.rinhadebackend2023q3"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-netty:2.3.3")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.7.3")

    // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging:2.1.21")
    implementation("ch.qos.logback:logback-classic:1.4.9")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

jib {
    container {
        mainClass = "com.mrpowergamerbr.rinhadebackend2023q3.RinhaDeBackend2023Q3Launcher"
    }

    to {
        image = "ghcr.io/mrpowergamerbr/rinha-de-backend-2023-q3-unleashed"

        auth {
            username = System.getProperty("DOCKER_USERNAME") ?: System.getenv("DOCKER_USERNAME")
            password = System.getProperty("DOCKER_PASSWORD") ?: System.getenv("DOCKER_PASSWORD")
        }
    }

    from {
        image = "eclipse-temurin:20-jammy"
    }
}