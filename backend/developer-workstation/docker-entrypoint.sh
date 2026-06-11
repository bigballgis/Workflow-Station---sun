#!/bin/sh
set -e

# Run the application
exec sh -c "java ${JAVA_OPTS} -cp /app org.springframework.boot.loader.launch.JarLauncher"
