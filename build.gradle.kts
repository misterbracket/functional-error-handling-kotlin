plugins {
    kotlin("jvm") version "2.1.20"
}

dependencies {
    implementation("io.arrow-kt:arrow-core:2.1.0")
    implementation("io.arrow-kt:arrow-fx-coroutines:2.1.0")
    testImplementation(kotlin("test"))
}

group = "pleo-io"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
