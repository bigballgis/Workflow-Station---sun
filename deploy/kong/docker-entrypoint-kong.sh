#!/bin/sh
set -e

# =====================================================
# 环境变量验证
# JWT_SECRET 和 CORS_ALLOWED_ORIGINS 为必须
# REDIS_PASSWORD 允许为空（开发环境可能不设密码）
# =====================================================
if [ -z "$JWT_SECRET" ]; then
  echo "ERROR: JWT_SECRET is not set. Kong JWT authentication will not work." >&2
  exit 1
fi

if [ -z "$CORS_ALLOWED_ORIGINS" ]; then
  echo "ERROR: CORS_ALLOWED_ORIGINS is not set. Kong CORS plugin will not work." >&2
  exit 1
fi

# =====================================================
# 将逗号分隔的 CORS_ALLOWED_ORIGINS 转换为 YAML 数组格式
# 例: "http://localhost:3000,http://localhost:3001"
# 转换为:
#         - http://localhost:3000
#         - http://localhost:3001
# =====================================================
build_cors_yaml() {
  echo "$CORS_ALLOWED_ORIGINS" | tr ',' '\n' | while read -r origin; do
    origin=$(echo "$origin" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    if [ -n "$origin" ]; then
      echo "        - ${origin}"
    fi
  done
}

CORS_YAML_LINES=$(build_cors_yaml)

# =====================================================
# 第一步: 替换简单占位符（JWT_SECRET, REDIS_HOST, REDIS_PASSWORD）
# =====================================================
sed -e "s|__JWT_SECRET__|${JWT_SECRET}|g" \
    -e "s|__REDIS_HOST__|${REDIS_HOST:-redis}|g" \
    -e "s|__REDIS_PASSWORD__|${REDIS_PASSWORD}|g" \
    /kong/kong.yml.template > /tmp/kong.yml.tmp

# =====================================================
# 第二步: 将 CORS origins 占位符行替换为多行 YAML 数组
# =====================================================
awk -v origins="$CORS_YAML_LINES" '
  /- __CORS_ALLOWED_ORIGINS__/ { print origins; next }
  { print }
' /tmp/kong.yml.tmp > /tmp/kong.yml

rm -f /tmp/kong.yml.tmp

# 设置 Kong 使用生成的配置文件
export KONG_DECLARATIVE_CONFIG=/tmp/kong.yml

# 启动 Kong
exec /docker-entrypoint.sh kong docker-start
