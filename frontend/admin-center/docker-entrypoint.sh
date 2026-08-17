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

# Inject runtime app config (per-environment) into config.js. The file currently ships
# with no placeholders — the AP_BRIDGE_URL launcher gate was removed together with the
# Admin Center automation entries (automation management lives in Developer Workstation
# now) — so this pass is a no-op until a new per-environment key is added. AP_BRIDGE_URL
# itself is still consumed by the admin-center BACKEND for its /launch endpoint.
: "${AP_BRIDGE_URL:=}"
APP_CONFIG_JS=/usr/share/nginx/html/admin/config.js
if [ -f "$APP_CONFIG_JS" ]; then
  envsubst '${AP_BRIDGE_URL}' < "$APP_CONFIG_JS" > "$APP_CONFIG_JS.tmp" && mv "$APP_CONFIG_JS.tmp" "$APP_CONFIG_JS"
fi

# Start nginx
exec nginx -g 'daemon off;'
