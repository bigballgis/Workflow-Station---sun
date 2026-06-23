#!/bin/sh
set -e
if [ -z "$KONG_PROXY_URL" ]; then
  echo "ERROR: KONG_PROXY_URL is not set" >&2
  exit 1
fi
: "${HSTS_STS_HEADER:=}"
js_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}
cat > /usr/share/nginx/html/login/runtime-config.js <<EOF
window.__LOGIN_RUNTIME_CONFIG__ = {
  VITE_DSP_ENABLED: "$(js_escape "${VITE_DSP_ENABLED:-}")",
  VITE_DSP_AUTHENTICATE_URL: "$(js_escape "${VITE_DSP_AUTHENTICATE_URL:-}")",
  VITE_DSP_CLIENT_ID: "$(js_escape "${VITE_DSP_CLIENT_ID:-}")",
  VITE_DSP_CLIENT_SECRET: "$(js_escape "${VITE_DSP_CLIENT_SECRET:-}")",
  VITE_DSP_ACCEPT_API_VERSION: "$(js_escape "${VITE_DSP_ACCEPT_API_VERSION:-}")"
};
EOF
envsubst '${KONG_PROXY_URL} ${HSTS_STS_HEADER}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'