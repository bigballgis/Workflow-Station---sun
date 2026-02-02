#!/bin/sh
set -e

# Replace environment variables in nginx config
envsubst '${USER_PORTAL_BACKEND_URL} ${ADMIN_CENTER_BACKEND_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

# Start nginx
exec nginx -g 'daemon off;'
