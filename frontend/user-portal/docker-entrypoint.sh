#!/bin/sh
set -e

# Validate required environment variables
if [ -z "$KONG_PROXY_URL" ]; then
  echo "ERROR: KONG_PROXY_URL is not set" >&2
  exit 1
fi

# Replace environment variables in nginx config template
# IMPORTANT: Only substitute our custom variables, NOT nginx's own $host, $uri, etc.
envsubst '${KONG_PROXY_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

echo "nginx config: KONG_PROXY_URL=$KONG_PROXY_URL"

# Start nginx
exec nginx -g 'daemon off;'
