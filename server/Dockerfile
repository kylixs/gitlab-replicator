# GitLab Mirror Server Dockerfile
# Multi-stage build for optimized image size

# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy pom files for dependency resolution
COPY pom.xml .
COPY common/pom.xml common/
COPY server/pom.xml server/
COPY cli-client/pom.xml cli-client/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY common/src common/src
COPY server/src server/src

# Build the application
RUN mvn clean package -DskipTests -pl server -am

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="GitLab Mirror Team"
LABEL description="GitLab Mirror Server with Pull-Sync Support"

# Install required tools
RUN apk add --no-cache \
    git \
    openssh-client \
    curl \
    bash

# Create application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create application directories
RUN mkdir -p /opt/gitlab-mirror/repos \
    /opt/gitlab-mirror/logs \
    /opt/gitlab-mirror/config && \
    chown -R appuser:appgroup /opt/gitlab-mirror

WORKDIR /opt/gitlab-mirror

# Copy JAR from build stage
COPY --from=build /build/server/target/server-*.jar app.jar

# Copy config files if exists
COPY --chown=appuser:appgroup server/src/main/resources/application.yml config/application.yml

# Set ownership
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/api/status || exit 1

# Environment variables with defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=gitlab_mirror \
    DB_USERNAME=gitlab_mirror \
    DB_PASSWORD=mirror_pass_123 \
    PULL_SYNC_BASE_PATH=/opt/gitlab-mirror/repos \
    JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
