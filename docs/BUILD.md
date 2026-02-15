# Building and testing Meiken

The project uses **Gradle** (Kotlin DSL). There is no Maven build.

## Quick commands

```bash
# Build and run tests
./gradlew test
# Windows
.\gradlew.bat test

# Run application
./gradlew run

# Full clean and coverage report
./gradlew clean test jacocoTestReport
```

Reports: `build/reports/tests/test/index.html`, coverage: `build/reports/jacoco/test/html/index.html`.

## Troubleshooting

### Gradle daemon or cache issues

```bash
./gradlew --stop
rm -rf .gradle build
./gradlew clean test --no-daemon
```

On Windows (PowerShell):

```powershell
.\gradlew.bat --stop
Remove-Item -Recurse -Force .gradle, build -ErrorAction SilentlyContinue
.\gradlew.bat clean test --no-daemon
```

### Plugin or dependency resolution errors

- Ensure **Java 11** is active: `java -version`
- Use the wrapper: `./gradlew` (or `gradlew.bat`) so the correct Gradle version is used.
- If you see Kotlin/Gradle compatibility errors, try the clean steps above and run without the daemon: `./gradlew clean test --no-daemon`.

### Verbose output

```bash
./gradlew test --info
./gradlew test --stacktrace
```

### Key configuration

- **Kotlin**: 1.8.20 (see `build.gradle.kts`)
- **JVM**: Java 11 (`jvmToolchain(11)`)
- **Main class**: `com.meiken.ApplicationKt`

### Docker build

See root **README.md** for building the Docker image and running the app in a container.
