#!/bin/sh
set -e

# Validate required environment variables
if [ -z "$KONG_PROXY_URL" ]; then
  echo "ERROR: KONG_PROXY_URL is not set" >&2
  exit 1
fi

# Replace environment variables in nginx config template
# IMPORTANT: Only substitute our custom variables, NOT nginx's own $host, $uri, etc.
: "${HSTS_STS_HEADER:=}"
envsubst '${KONG_PROXY_URL} ${HSTS_STS_HEADER}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

echo "nginx config: KONG_PROXY_URL=$KONG_PROXY_URL"

# Inject runtime app config (per-environment). config.js ships with a ${AP_BRIDGE_URL}
# placeholder; substitute it here. Empty -> the admin "Activepieces" launcher stays hidden
# (prod runtime-only). Non-prod sets AP_BRIDGE_URL to the env's AP gateway bridge URL.
: "${AP_BRIDGE_URL:=}"
APP_CONFIG_JS=/usr/share/nginx/html/admin/config.js
if [ -f "$APP_CONFIG_JS" ]; then
  envsubst '${AP_BRIDGE_URL}' < "$APP_CONFIG_JS" > "$APP_CONFIG_JS.tmp" && mv "$APP_CONFIG_JS.tmp" "$APP_CONFIG_JS"
  echo "app config: AP_BRIDGE_URL=$AP_BRIDGE_URL"
fi

# Start nginx
exec nginx -g 'daemon off;'
