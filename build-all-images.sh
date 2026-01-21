#!/bin/bash

# Workflow Platform - 批量构建 Docker 镜像脚本
# 用法: ./build-all-images.sh [version] [registry]

set -e

# 默认值
VERSION=${1:-latest}
REGISTRY=${2:-workflow-platform}

echo "=========================================="
echo "Workflow Platform - Docker 镜像构建"
echo "=========================================="
echo "版本: $VERSION"
echo "仓库前缀: $REGISTRY"
echo "=========================================="
echo ""

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ 错误: Docker 未运行，请先启动 Docker"
    exit 1
fi

# 启用 BuildKit（如果支持）
export DOCKER_BUILDKIT=1

# 构建后端服务
echo "📦 开始构建后端服务..."
echo ""

echo "  [1/5] 构建 API Gateway..."
docker build -t $REGISTRY/api-gateway:$VERSION ./backend/api-gateway || {
    echo "❌ API Gateway 构建失败"
    exit 1
}

echo "  [2/5] 构建 Workflow Engine..."
docker build -t $REGISTRY/workflow-engine:$VERSION ./backend/workflow-engine-core || {
    echo "❌ Workflow Engine 构建失败"
    exit 1
}

echo "  [3/5] 构建 Admin Center..."
docker build -t $REGISTRY/admin-center:$VERSION ./backend/admin-center || {
    echo "❌ Admin Center 构建失败"
    exit 1
}

echo "  [4/5] 构建 Developer Workstation..."
docker build -t $REGISTRY/developer-workstation:$VERSION ./backend/developer-workstation || {
    echo "❌ Developer Workstation 构建失败"
    exit 1
}

echo "  [5/5] 构建 User Portal..."
docker build -t $REGISTRY/user-portal:$VERSION ./backend/user-portal || {
    echo "❌ User Portal 构建失败"
    exit 1
}

echo ""
echo "✅ 所有后端服务构建完成！"
echo ""

# 构建前端服务
echo "📦 开始构建前端服务..."
echo ""

echo "  [1/3] 构建 Admin Center Frontend..."
docker build -t $REGISTRY/frontend-admin:$VERSION ./frontend/admin-center || {
    echo "❌ Admin Center Frontend 构建失败"
    exit 1
}

echo "  [2/3] 构建 Developer Workstation Frontend..."
docker build -t $REGISTRY/frontend-developer:$VERSION ./frontend/developer-workstation || {
    echo "❌ Developer Workstation Frontend 构建失败"
    exit 1
}

echo "  [3/3] 构建 User Portal Frontend..."
docker build -t $REGISTRY/frontend-portal:$VERSION ./frontend/user-portal || {
    echo "❌ User Portal Frontend 构建失败"
    exit 1
}

echo ""
echo "✅ 所有前端服务构建完成！"
echo ""

# 显示构建结果
echo "=========================================="
echo "✅ 所有镜像构建成功！"
echo "=========================================="
echo ""
echo "构建的镜像列表:"
docker images | grep $REGISTRY | grep $VERSION
echo ""
echo "镜像统计:"
echo "  后端服务: 5 个"
echo "  前端服务: 3 个"
echo "  总计: 8 个镜像"
echo ""
echo "下一步操作:"
echo "  1. 查看镜像: docker images | grep $REGISTRY"
echo "  2. 测试镜像: docker-compose up -d"
echo "  3. 推送到仓库: docker push $REGISTRY/<service>:$VERSION"
echo ""
