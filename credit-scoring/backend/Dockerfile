# ═══════════════════════════════════════════════════════════════
# FinScore AI — Spring Boot Backend Dockerfile
# ═══════════════════════════════════════════════════════════════
# Multi-stage build:
# Stage 1: Maven build (compile + package JAR)
# Stage 2: JRE 17 runtime (minimal image for deployment)
# ═══════════════════════════════════════════════════════════════

# ─── Stage 1: Build ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy POM first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ───────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port (configurable via PORT env var)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/api/states || exit 1

# Run the application
# All configuration via environment variables
ENTRYPOINT ["java", "-jar", "app.jar"]
