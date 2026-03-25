#!/bin/sh
set -e

# 将模板中的 __PLACEHOLDER__ 替换为环境变量值
sed -e "s|__JWT_SECRET__|${JWT_SECRET}|g" \
    -e "s|__REDIS_HOST__|${REDIS_HOST:-redis}|g" \
    -e "s|__REDIS_PASSWORD__|${REDIS_PASSWORD}|g" \
    -e "s|__CORS_ALLOWED_ORIGINS__|${CORS_ALLOWED_ORIGINS}|g" \
    /kong/kong.yml.template > /kong/kong.yml

# 启动 Kong
exec /docker-entrypoint.sh kong docker-start
