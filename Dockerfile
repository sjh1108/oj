# syntax=docker/dockerfile:1

# ─── Build stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache dependency resolution: copy only build files first.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Build the bootJar.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ─── Runtime stage ───────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /opt/algoj

# curl is used by the compose healthcheck (jre image ships without it).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# bootJar archiveFileName is fixed to algoj.jar (see build.gradle).
COPY --from=build /app/build/libs/algoj.jar app.jar

EXPOSE 8080
# Heap is overridable so the deploy can shrink it during the brief blue-green
# overlap on a memory-tight box. `exec` makes java PID 1 so it receives SIGTERM
# and shuts down gracefully.
ENV JAVA_OPTS="-Xms200m -Xmx400m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
