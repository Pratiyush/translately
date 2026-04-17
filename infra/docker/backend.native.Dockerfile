# Translately backend — native (GraalVM) container image.
#
# Build from the repo root:
#   docker build -f infra/docker/backend.native.Dockerfile -t translately-backend:native .
#
# Native builds are slow (~5–15m) and memory-hungry; reserve for release images.

# syntax=docker/dockerfile:1.7

# ---------- native build stage ----------
FROM ghcr.io/graalvm/native-image-community:21 AS build
WORKDIR /workspace

RUN microdnf install -y findutils \
 && microdnf clean all

COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle ./gradle
COPY backend ./backend

# Use Gradle wrapper committed to the repo (added in the Gradle skeleton PR).
RUN ./gradlew :backend:app:build -Dquarkus.package.type=native \
        -Dquarkus.native.container-build=false \
        -x test --no-daemon --stacktrace

# ---------- runtime stage ----------
FROM debian:13-slim AS runtime
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates wget \
 && rm -rf /var/lib/apt/lists/*

RUN groupadd -r translately -g 10001 \
 && useradd -r -u 10001 -g translately translately

COPY --from=build --chown=translately:translately \
    /workspace/backend/app/build/*-runner /app/translately

USER 10001:10001

ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8080

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=10s \
    CMD wget -qO- http://127.0.0.1:8080/q/health/ready || exit 1

ENTRYPOINT ["/app/translately", "-Dquarkus.http.host=0.0.0.0"]
