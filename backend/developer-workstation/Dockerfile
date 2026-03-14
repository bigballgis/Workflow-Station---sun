# Runtime image for Developer Workstation
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install su-exec for safe privilege dropping at runtime
#RUN apk add --no-cache su-exec

# Add non-root user
RUN addgroup -S platform && adduser -S platform -G platform

# Copy the pre-built artifact
COPY target/developer-workstation-*.jar app.jar

# Copy entrypoint script (runs as root, fixes volume perms, then drops to platform)
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
# Convert Windows CRLF → LF and ensure executable
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh && chmod +x /usr/local/bin/docker-entrypoint.sh

# Create directories ahead of time so chown in entrypoint always succeeds
RUN mkdir -p /app/uploads /app/logs && chown -R platform:platform /app

# JVM configuration
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Health check (context-path is /api/v1)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -T 5 -O /dev/null http://localhost:8080/api/v1/actuator/health || exit 1

EXPOSE 8080

# Entrypoint runs as root, fixes volume ownership, then drops to platform user
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
