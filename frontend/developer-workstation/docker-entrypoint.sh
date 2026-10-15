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

# Runtime config for the browser: DSP authenticate endpoint used to silently refresh an expired
# AMToken before giving up on an AI gateway call (same keys as the unified login page).
# Empty URL = no silent refresh, the UI keeps asking the user to sign in again.
js_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}
cat > /usr/share/nginx/html/dev/runtime-config.js <<EOF
window.__DW_RUNTIME_CONFIG__ = {
  VITE_DSP_AUTHENTICATE_URL: "$(js_escape "${VITE_DSP_AUTHENTICATE_URL:-}")",
  VITE_DSP_CLIENT_ID: "$(js_escape "${VITE_DSP_CLIENT_ID:-}")",
  VITE_DSP_CLIENT_SECRET: "$(js_escape "${VITE_DSP_CLIENT_SECRET:-}")",
  VITE_DSP_ACCEPT_API_VERSION: "$(js_escape "${VITE_DSP_ACCEPT_API_VERSION:-}")"
};
EOF

# Start nginx
exec nginx -g 'daemon off;'
