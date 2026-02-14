plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
    id("java")
    id("application")
    jacoco
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
    
    // Ktor Server
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    
    // Logging (required by CallLogging)
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    
    // Kotlin test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.8.20")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude(
                    "**/model/*\$*.class",  // Kotlin serialization generated (Companion, $$serializer)
                    "**/Comparisons*.class"  // Kotlin stdlib inlined
                )
            }
        }
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

application {
    mainClass.set("com.meiken.ApplicationKt")
}

kotlin {
    jvmToolchain(11)
}
