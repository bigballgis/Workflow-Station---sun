#!/bin/sh
set -e
if [ -z "$KONG_PROXY_URL" ]; then
  echo "ERROR: KONG_PROXY_URL is not set" >&2
  exit 1
fi
: "${HSTS_STS_HEADER:=}"
envsubst '${KONG_PROXY_URL} ${HSTS_STS_HEADER}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g 'daemon off;'
