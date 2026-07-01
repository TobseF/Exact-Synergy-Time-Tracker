plugins {
    kotlin("jvm") version "2.4.0"
    application
}

application {
    mainClass.set("de.tfr.tool.timetrack.TimeTrackerKt")
}

group = "de.tfr.tool"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.seleniumhq.selenium:selenium-java:4.33.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(22)
}

tasks.test {
    useJUnitPlatform()
}