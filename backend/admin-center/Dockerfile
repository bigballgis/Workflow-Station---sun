# Runtime image for Admin Center
ARG JAVA_BASE_IMAGE=eclipse-temurin:17-jre

# Stage 1: explode the Spring Boot fat JAR into layers so that the large,
# rarely-changing dependency layers stay cached in the registry between pushes.
FROM ${JAVA_BASE_IMAGE} AS extract
WORKDIR /extract
COPY target/admin-center-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM ${JAVA_BASE_IMAGE}

WORKDIR /app

# Add non-root user
RUN groupadd --system platform && useradd --system --gid platform --no-create-home platform

# Copy layers from least to most frequently changing (uses --chown instead of
# a separate chown -R, which would duplicate the whole layer)
COPY --from=extract --chown=platform:platform /extract/dependencies/ ./
COPY --from=extract --chown=platform:platform /extract/spring-boot-loader/ ./
COPY --from=extract --chown=platform:platform /extract/snapshot-dependencies/ ./
COPY --from=extract --chown=platform:platform /extract/application/ ./

USER platform

# JVM configuration
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# Health check (context-path is /api/v1/admin)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q -T 5 -O /dev/null http://localhost:8080/api/v1/admin/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app org.springframework.boot.loader.launch.JarLauncher"]
