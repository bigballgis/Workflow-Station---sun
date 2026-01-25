#!/bin/bash

# =====================================================
# 检查 Platform 模块是否已正确构建
# =====================================================

echo "🔍 检查 Platform 模块构建状态..."
echo ""

M2_REPO="$HOME/.m2/repository/com/platform"
ERRORS=0

# 检查函数
check_module() {
    local module=$1
    local required=$2
    
    if [ -d "$M2_REPO/$module" ]; then
        local version_dir=$(find "$M2_REPO/$module" -type d -name "1.0.0-SNAPSHOT" | head -1)
        if [ -n "$version_dir" ]; then
            local jar_file=$(find "$version_dir" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | head -1)
            if [ -n "$jar_file" ]; then
                echo "✅ $module - 已构建 ($(basename $jar_file))"
                return 0
            else
                echo "❌ $module - 目录存在但缺少 JAR 文件"
                ERRORS=$((ERRORS + 1))
                return 1
            fi
        else
            echo "❌ $module - 目录存在但缺少版本目录"
            ERRORS=$((ERRORS + 1))
            return 1
        fi
    else
        if [ "$required" = "required" ]; then
            echo "❌ $module - 未找到（必需模块）"
            ERRORS=$((ERRORS + 1))
            return 1
        else
            echo "⚠️  $module - 未找到（可选模块）"
            return 0
        fi
    fi
}

# 检查必需模块
echo "📦 检查必需模块："
check_module "platform-common" "required"
check_module "platform-cache" "required"
check_module "platform-security" "required"

echo ""
echo "📦 检查可选模块："
check_module "platform-messaging" "optional"

echo ""

# 检查服务 JAR 文件
echo "🔍 检查服务 JAR 文件："
SERVICES=("api-gateway" "workflow-engine-core" "admin-center" "user-portal" "developer-workstation")

for service in "${SERVICES[@]}"; do
    jar_file=$(find "backend/$service/target" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" ! -name "original-*.jar" 2>/dev/null | head -1)
    if [ -n "$jar_file" ]; then
        echo "✅ $service - JAR 文件存在 ($(basename $jar_file))"
    else
        echo "❌ $service - JAR 文件不存在"
        ERRORS=$((ERRORS + 1))
    fi
done

echo ""

# 总结
if [ $ERRORS -eq 0 ]; then
    echo "✅ 所有必需模块已正确构建！"
    echo ""
    echo "💡 提示：如果服务仍然无法运行，请检查："
    echo "   1. Docker 容器日志：docker logs <container-name>"
    echo "   2. 服务健康检查：curl http://localhost:<port>/actuator/health"
    exit 0
else
    echo "❌ 发现 $ERRORS 个问题！"
    echo ""
    echo "🔧 修复方法："
    echo "   1. 构建 platform 模块："
    echo "      cd backend/platform-common && mvn clean install"
    echo "      cd ../platform-cache && mvn clean install"
    echo "      cd ../platform-security && mvn clean install"
    echo ""
    echo "   2. 重新构建服务："
    echo "      mvn clean package -DskipTests"
    echo ""
    echo "   3. 重新构建 Docker 镜像："
    echo "      docker-compose build --profile backend"
    exit 1
fi
