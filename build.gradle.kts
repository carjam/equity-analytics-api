plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
    id("java")
}

group = "com.meiken"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    
    // kotlinx.datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    
    // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    
    // Kotlin test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.8.20")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
