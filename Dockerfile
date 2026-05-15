# syntax=docker/dockerfile:1.7
# Multi-stage build cho Spring Boot 4 + Java 21
# Stage 1: Maven build (dùng JDK 21 full)
# Stage 2: Runtime (dùng JRE 21 alpine, ~150MB vs 480MB cho JDK)

# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper + pom.xml first để cache dependency layer
# (chỉ chạy lại khi pom.xml thay đổi, không phải mỗi lần source code đổi)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B dependency:go-offline

# Copy source code và build
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests \
    && cp target/*.jar /build/app.jar

# ─────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install minimal tools (curl cho healthcheck, dumb-init cho proper signal handling)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl dumb-init \
    && rm -rf /var/lib/apt/lists/*

# Tạo non-root user (security best practice — không chạy Java app dưới root)
RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --shell /bin/false spring

WORKDIR /app

# Tạo dirs cho uploads + Firebase credentials (mount qua volume khi run)
RUN mkdir -p /app/uploads /app/secrets \
    && chown -R spring:spring /app

# Copy jar từ builder stage
COPY --from=builder --chown=spring:spring /build/app.jar /app/app.jar

# Switch sang non-root user
USER spring

# Spring Boot port
EXPOSE 8080

# JVM tuning cho container:
# - UseContainerSupport: aware of cgroup memory limits (default từ Java 11)
# - MaxRAMPercentage=75: dùng 75% RAM container, chừa 25% cho OS/buffer
# - ExitOnOutOfMemoryError: crash ngay khi OOM để Docker restart kích hoạt
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# dumb-init forward SIGTERM properly (Spring Boot graceful shutdown 30s)
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
