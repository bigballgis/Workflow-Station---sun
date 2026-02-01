#!/bin/sh
# Generate nginx default.conf from template (RESOLVER for Docker vs IKP/K8s)
# Default: 127.0.0.11 (Docker embedded DNS). IKP/K8s: set NGINX_RESOLVER to cluster DNS.
RESOLVER="${NGINX_RESOLVER:-127.0.0.11}"
sed "s/__RESOLVER__/$RESOLVER/g" /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
exec nginx -g "daemon off;"
