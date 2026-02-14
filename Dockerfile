# Multi-stage build: build then run with Java 17
# Stage 1: build
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY src src/
RUN chmod +x gradlew && ./gradlew fatJar --no-daemon

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/meiken-all-*.jar meiken.jar
EXPOSE 8080
# API key can be set at build time (ARG) or at runtime (-e ALPHA_VANTAGE_API_KEY=...)
ARG ALPHA_VANTAGE_API_KEY
ENV ALPHA_VANTAGE_API_KEY=${ALPHA_VANTAGE_API_KEY}
CMD ["java", "-jar", "meiken.jar"]
