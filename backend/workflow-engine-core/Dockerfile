# Runtime image for Workflow Engine Core (uses pre-built JAR)
FROM eclipse-temurin:17-jre

WORKDIR /app

# Add non-root user
RUN groupadd --system platform && useradd --system --gid platform --no-create-home platform

# Copy the pre-built artifact
COPY target/workflow-engine-core-*.jar app.jar

# Set ownership
RUN chown -R platform:platform /app

USER platform

# JVM configuration
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Health check (no context-path, actuator exposed at /actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -T 5 -O /dev/null http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
