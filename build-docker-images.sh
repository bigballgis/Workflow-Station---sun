#!/bin/bash

# =====================================================
# Docker 镜像构建脚本
# 使用 .env.build 文件中的构建参数
# =====================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔨 开始构建 Docker 镜像...${NC}"
echo ""

# 读取构建参数
if [ -f .env.build ]; then
    echo -e "${GREEN}📄 读取构建配置: .env.build${NC}"
    export $(cat .env.build | grep -v '^#' | xargs)
else
    echo -e "${YELLOW}⚠️  未找到 .env.build，使用默认值${NC}"
    BUILD_PLATFORM="linux/amd64"
fi

# 1. Maven 构建 JAR 文件
echo -e "${BLUE}📦 Step 1: 构建 JAR 文件...${NC}"
mvn clean package -DskipTests
echo ""

# 2. 构建 Docker 镜像
echo -e "${BLUE}🐳 Step 2: 构建 Docker 镜像...${NC}"
echo ""

# 构建 Workflow Engine Core
echo -e "${GREEN}构建 Workflow Engine Core...${NC}"
docker build \
  --platform ${BUILD_PLATFORM:-linux/amd64} \
  ${JAVA_OPTS:+--build-arg JAVA_OPTS="$JAVA_OPTS"} \
  -f backend/workflow-engine-core/Dockerfile \
  -t workflow-engine:latest \
  backend/workflow-engine-core
echo ""

# 构建 Admin Center
echo -e "${GREEN}构建 Admin Center...${NC}"
docker build \
  --platform ${BUILD_PLATFORM:-linux/amd64} \
  ${JAVA_OPTS:+--build-arg JAVA_OPTS="$JAVA_OPTS"} \
  -f backend/admin-center/Dockerfile \
  -t admin-center:latest \
  backend/admin-center
echo ""

# 构建 User Portal
echo -e "${GREEN}构建 User Portal...${NC}"
docker build \
  --platform ${BUILD_PLATFORM:-linux/amd64} \
  ${JAVA_OPTS:+--build-arg JAVA_OPTS="$JAVA_OPTS"} \
  -f backend/user-portal/Dockerfile \
  -t user-portal:latest \
  backend/user-portal
echo ""

# 构建 Developer Workstation
echo -e "${GREEN}构建 Developer Workstation...${NC}"
docker build \
  --platform ${BUILD_PLATFORM:-linux/amd64} \
  ${JAVA_OPTS:+--build-arg JAVA_OPTS="$JAVA_OPTS"} \
  -f backend/developer-workstation/Dockerfile \
  -t developer-workstation:latest \
  backend/developer-workstation
echo ""

# 构建 API Gateway
echo -e "${GREEN}构建 API Gateway...${NC}"
docker build \
  --platform ${BUILD_PLATFORM:-linux/amd64} \
  ${JAVA_OPTS:+--build-arg JAVA_OPTS="$JAVA_OPTS"} \
  -f backend/api-gateway/Dockerfile \
  -t api-gateway:latest \
  backend/api-gateway
echo ""

# 显示构建结果
echo -e "${GREEN}✅ 所有镜像构建完成！${NC}"
echo ""
echo -e "${BLUE}📋 镜像列表:${NC}"
docker images | grep -E "REPOSITORY|workflow-engine|admin-center|user-portal|developer-workstation|api-gateway"
echo ""

echo -e "${YELLOW}💡 下一步: 运行容器${NC}"
echo "  使用开发环境: ./run-docker-containers.sh dev"
echo "  使用 Docker 环境: ./run-docker-containers.sh docker"
echo "  使用生产环境: ./run-docker-containers.sh prod"
