#!/bin/sh
set -e

# Fix ownership of mounted volumes so the platform user can write to them
if [ -d "/app/uploads" ]; then
  chown -R platform:platform /app/uploads 2>/dev/null || true
  chmod 755 /app/uploads
fi

if [ -d "/app/logs" ]; then
  chown -R platform:platform /app/logs 2>/dev/null || true
  chmod 755 /app/logs
fi

# Drop privileges and run the application
exec su-exec platform sh -c "java ${JAVA_OPTS} -jar /app/app.jar"
