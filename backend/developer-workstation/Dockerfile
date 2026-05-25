# Runtime image for Developer Workstation
ARG JAVA_BASE_IMAGE=eclipse-temurin:17-jre
FROM ${JAVA_BASE_IMAGE}

ARG PLATFORM_UID=10001
ARG PLATFORM_GID=10001

WORKDIR /app

# Add non-root user
RUN groupadd --gid ${PLATFORM_GID} --system platform \
    && useradd --uid ${PLATFORM_UID} --gid platform --system --no-create-home --shell /usr/sbin/nologin platform

# Copy the pre-built artifact
COPY target/developer-workstation-*.jar app.jar

# Copy entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
# Convert Windows CRLF → LF and ensure executable
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh && chmod +x /usr/local/bin/docker-entrypoint.sh

# Create writable directories ahead of time
RUN mkdir -p /app/logs && chown -R platform:platform /app

USER platform:platform

# JVM configuration
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Health check (context-path is /api/v1)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -T 5 -O /dev/null http://localhost:8080/api/v1/actuator/health || exit 1

EXPOSE 8080

# Entrypoint starts the application
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
