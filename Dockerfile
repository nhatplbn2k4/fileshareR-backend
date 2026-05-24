# syntax=docker/dockerfile:1.7
# Multi-stage build cho Spring Boot 4 + Java 21
# Stage 1: Maven build (dùng JDK 21 full)
# Stage 2: Runtime (dùng JRE 21 alpine, ~150MB vs 480MB cho JDK)

# ─────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper + pom.xml + lombok.config first để cache dependency layer
# (chỉ chạy lại khi pom.xml thay đổi, không phải mỗi lần source code đổi)
# lombok.config bắt buộc — chứa lombok.copyableAnnotations += @Qualifier để
# Spring DI chọn đúng bean khi inject vào constructor.
COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
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

# Install runtime tools + Python + LibreOffice cho PDF→Word conversion (LocalPdfConvertEngine)
# - python3 + pip: chạy scripts/convert_pdf.py + scripts/merge_docx.py
# - libreoffice-writer: convert special pages (cover, TOC) sang DOCX với layout chuẩn
# - fonts-dejavu + fonts-liberation: tránh missing glyph khi render
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl dumb-init \
        python3 python3-pip \
        libreoffice-writer \
        fonts-dejavu fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

# Tạo non-root user (security best practice — không chạy Java app dưới root)
RUN groupadd --system --gid 1001 spring \
    && useradd --system --uid 1001 --gid spring --shell /bin/false spring

WORKDIR /app

# Tạo dirs cho uploads + Firebase credentials (mount qua volume khi run)
RUN mkdir -p /app/uploads /app/secrets \
    && chown -R spring:spring /app

# Install Python packages cho PDF→Word
COPY scripts/requirements.txt /tmp/requirements.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt

# Copy scripts/ vào /app/scripts (LocalPdfConvertEngine resolves "scripts/convert_pdf.py" relative to WORKDIR)
COPY --chown=spring:spring scripts/ /app/scripts/

# Copy jar từ builder stage
COPY --from=builder --chown=spring:spring /build/app.jar /app/app.jar

# Copy GCP Vertex AI service account key vào /app/secrets (decoded từ workflow)
# File này gitignored, được tạo lúc CI build bằng decode GCP_VERTEX_SA_B64 secret.
# Vertex AI SDK đọc qua env GOOGLE_APPLICATION_CREDENTIALS (set bên dưới).
COPY --chown=spring:spring gcp-vertex-key.json /app/secrets/gcp-vertex-key.json

# Switch sang non-root user
USER spring

# Spring Boot port
EXPOSE 8080

# JVM tuning cho container:
# - UseContainerSupport: aware of cgroup memory limits (default từ Java 11)
# - MaxRAMPercentage=75: dùng 75% RAM container, chừa 25% cho OS/buffer
# - ExitOnOutOfMemoryError: crash ngay khi OOM để Docker restart kích hoạt
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# GCP Vertex AI config (project + region không phải secret, hardcode OK; key path
# trỏ tới file COPY từ workflow build step)
ENV GCP_PROJECT_ID="filesharer-app" \
    GCP_VERTEX_LOCATION="us-central1" \
    GEMINI_MODEL="gemini-2.5-flash" \
    GOOGLE_APPLICATION_CREDENTIALS="/app/secrets/gcp-vertex-key.json"

# dumb-init forward SIGTERM properly (Spring Boot graceful shutdown 30s)
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
