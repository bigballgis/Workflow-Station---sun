#!/bin/bash

# =====================================================
# Docker 容器运行脚本
# 使用不同的 .env 文件启动容器
# =====================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 获取环境参数
ENV=${1:-docker}
ENV_FILE=".env.${ENV}"

echo -e "${BLUE}🚀 启动 Docker 容器 (环境: ${ENV})${NC}"
echo ""

# 检查 .env 文件是否存在
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}❌ 错误: 找不到配置文件 ${ENV_FILE}${NC}"
    echo ""
    echo "可用的环境:"
    echo "  dev    - 开发环境 (.env.dev)"
    echo "  docker - Docker 环境 (.env.docker)"
    echo "  prod   - 生产环境 (.env.prod)"
    echo ""
    echo "用法: $0 [dev|docker|prod]"
    exit 1
fi

echo -e "${GREEN}📄 使用配置文件: ${ENV_FILE}${NC}"
echo ""

# 创建 Docker 网络
echo -e "${BLUE}🌐 创建 Docker 网络...${NC}"
docker network create platform-network 2>/dev/null || echo "网络已存在"
echo ""

# 停止并删除已存在的容器
echo -e "${YELLOW}🧹 清理旧容器...${NC}"
docker rm -f platform-workflow-engine 2>/dev/null || true
docker rm -f platform-admin-center 2>/dev/null || true
docker rm -f platform-user-portal 2>/dev/null || true
docker rm -f platform-developer-workstation 2>/dev/null || true
docker rm -f platform-api-gateway 2>/dev/null || true
echo ""

# 启动容器
echo -e "${BLUE}🐳 启动容器...${NC}"
echo ""

# 1. Workflow Engine Core
echo -e "${GREEN}1️⃣  启动 Workflow Engine Core (端口 8091)...${NC}"
docker run -d \
  --name platform-workflow-engine \
  --network platform-network \
  --env-file "$ENV_FILE" \
  -p 8091:8080 \
  --restart unless-stopped \
  workflow-engine:latest
echo "   容器 ID: $(docker ps -q -f name=platform-workflow-engine)"
sleep 5

# 2. Admin Center
echo -e "${GREEN}2️⃣  启动 Admin Center (端口 8092)...${NC}"
docker run -d \
  --name platform-admin-center \
  --network platform-network \
  --env-file "$ENV_FILE" \
  -p 8092:8080 \
  --restart unless-stopped \
  admin-center:latest
echo "   容器 ID: $(docker ps -q -f name=platform-admin-center)"
sleep 5

# 3. User Portal
echo -e "${GREEN}3️⃣  启动 User Portal (端口 8093)...${NC}"
docker run -d \
  --name platform-user-portal \
  --network platform-network \
  --env-file "$ENV_FILE" \
  -p 8093:8080 \
  --restart unless-stopped \
  user-portal:latest
echo "   容器 ID: $(docker ps -q -f name=platform-user-portal)"
sleep 5

# 4. Developer Workstation
echo -e "${GREEN}4️⃣  启动 Developer Workstation (端口 8094)...${NC}"
docker run -d \
  --name platform-developer-workstation \
  --network platform-network \
  --env-file "$ENV_FILE" \
  -p 8094:8080 \
  --restart unless-stopped \
  developer-workstation:latest
echo "   容器 ID: $(docker ps -q -f name=platform-developer-workstation)"
sleep 5

# 5. API Gateway
echo -e "${GREEN}5️⃣  启动 API Gateway (端口 8090)...${NC}"
docker run -d \
  --name platform-api-gateway \
  --network platform-network \
  --env-file "$ENV_FILE" \
  -p 8090:8080 \
  --restart unless-stopped \
  api-gateway:latest
echo "   容器 ID: $(docker ps -q -f name=platform-api-gateway)"

echo ""
echo -e "${GREEN}✅ 所有容器已启动！${NC}"
echo ""

# 显示容器状态
echo -e "${BLUE}📋 容器状态:${NC}"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""

# 显示访问地址
echo -e "${BLUE}🌐 服务访问地址:${NC}"
echo "  API Gateway:           http://localhost:8090"
echo "  Workflow Engine Core:  http://localhost:8091"
echo "  Admin Center:          http://localhost:8092"
echo "  User Portal:           http://localhost:8093"
echo "  Developer Workstation: http://localhost:8094"
echo ""

# 显示日志命令
echo -e "${YELLOW}💡 查看日志:${NC}"
echo "  docker logs -f platform-workflow-engine"
echo "  docker logs -f platform-admin-center"
echo "  docker logs -f platform-user-portal"
echo "  docker logs -f platform-developer-workstation"
echo "  docker logs -f platform-api-gateway"
echo ""

# 显示停止命令
echo -e "${YELLOW}💡 停止容器:${NC}"
echo "  docker stop platform-workflow-engine platform-admin-center platform-user-portal platform-developer-workstation platform-api-gateway"
echo ""
