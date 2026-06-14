# ========================================
# Backend Dockerfile — Multi-stage build
# ========================================
# Stage 1: Build with Maven
# Stage 2: Run with slim JRE
# ========================================

# --- Build Stage ---
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (Docker cache layer)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

# --- Run Stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from build stage
COPY --from=builder /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/api/health || exit 1

EXPOSE 8080 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
