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
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    
    // API key hashing (for future key storage)
    implementation("at.favre.lib:bcrypt:0.10.2")
    
    // Micrometer Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
    // Logging (required by CallLogging)
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // Ktor Client (for Alpha Vantage)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Resilience4j (1.7.x for Java 11; 2.x requires Java 17)
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:1.7.1")
    implementation("io.github.resilience4j:resilience4j-retry:1.7.1")
    implementation("io.github.resilience4j:resilience4j-timelimiter:1.7.1")
    implementation("io.github.resilience4j:resilience4j-kotlin:1.7.1")
    
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    
    // Kotlin test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.20")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.8.20")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
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
                    "**/model/**",  // Data classes / DTOs (getters, serialization)
                    "**/Comparisons*.class",  // Kotlin stdlib inlined
                    "**/error/*.class",  // Exception classes (covered indirectly via handlers)
                    "**/ApplicationKt*.class",  // Application wiring (StatusPages, plugins)
                    "**/ReturnsRoutes*.class",  // Stub (501)
                    "**/AlphaRoutes*.class",  // Stub (501)
                    "**/api/*.class",  // Routes/HealthResponse (covered by ApplicationTest)
                    "**/resilience/*.class",  // Circuit/retry wiring (covered via ResilientMarketDataService + integration)
                    "**/lifecycle/*.class"  // ShutdownState (covered via integration)
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

tasks.named<ProcessResources>("processResources") {
    from("docs/legal") {
        include("*.md")
        into("legal")
    }
    from("docs/NOTICE.md") {
        into("legal")
    }
}

// Fat JAR for Docker (includes all dependencies)
tasks.register<Jar>("fatJar") {
    archiveBaseName.set("equity-analytics-api")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.meiken.ApplicationKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

kotlin {
    jvmToolchain(11)
}
