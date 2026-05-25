#!/bin/sh
set -e

# Run the application
exec sh -c "java ${JAVA_OPTS} -jar /app/app.jar"
