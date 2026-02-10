#!/bin/sh
set -e

# Validate required environment variables
if [ -z "$USER_PORTAL_URL" ]; then
  echo "ERROR: USER_PORTAL_URL is not set" >&2
  exit 1
fi
if [ -z "$ADMIN_CENTER_URL" ]; then
  echo "ERROR: ADMIN_CENTER_URL is not set" >&2
  exit 1
fi

# Replace environment variables in nginx config template
# IMPORTANT: Only substitute our custom variables, NOT nginx's own $host, $uri, etc.
envsubst '${USER_PORTAL_URL} ${ADMIN_CENTER_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

echo "nginx config: USER_PORTAL_URL=$USER_PORTAL_URL ADMIN_CENTER_URL=$ADMIN_CENTER_URL"

# Start nginx
exec nginx -g 'daemon off;'
