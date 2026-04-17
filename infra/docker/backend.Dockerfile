# Translately backend — JVM container image.
#
# Build from the repo root:
#   docker build -f infra/docker/backend.Dockerfile -t translately-backend:dev .
#
# Multi-stage: stage 1 runs Gradle inside the container so the host doesn't
# need a JDK; stage 2 produces the runtime image with only the fast-jar layers.
# For the native build, see backend.native.Dockerfile (Phase 1+).

# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=21

# ---------- build stage ----------
FROM gradle:8.10-jdk${JAVA_VERSION} AS build
WORKDIR /workspace

# Cache dependencies first
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle ./gradle
COPY backend ./backend
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :backend:app:quarkusBuild -x test --no-daemon --stacktrace

# ---------- runtime stage ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S translately && adduser -S -G translately -u 10001 translately

# Quarkus fast-jar layout
COPY --from=build --chown=translately:translately \
    /workspace/backend/app/build/quarkus-app/lib/       /app/lib/
COPY --from=build --chown=translately:translately \
    /workspace/backend/app/build/quarkus-app/*.jar      /app/
COPY --from=build --chown=translately:translately \
    /workspace/backend/app/build/quarkus-app/app/       /app/app/
COPY --from=build --chown=translately:translately \
    /workspace/backend/app/build/quarkus-app/quarkus/   /app/quarkus/

USER 10001:10001

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=20s \
    CMD wget -qO- http://127.0.0.1:8080/q/health/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
