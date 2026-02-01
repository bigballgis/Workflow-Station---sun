#!/bin/bash

# =====================================================
# 启动后端服务脚本（本地开发模式）
# =====================================================

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"
mkdir -p "$LOG_DIR"

echo "🚀 启动后端服务..."
echo ""

# 检查基础设施服务
echo "📦 检查基础设施服务..."
if ! docker-compose ps | grep -q "platform-postgres.*healthy"; then
    echo "⚠️  警告: PostgreSQL 可能未就绪，请等待..."
fi

# 启动顺序：
# 1. Workflow Engine Core (8091) - 核心引擎，其他服务依赖它
# 2. Admin Center (8092) - 管理中心，提供用户/角色管理
# 3. User Portal (8093) - 用户门户，依赖 workflow-engine 和 admin-center
# 4. Developer Workstation (8094) - 开发工作站，依赖 admin-center
# 5. API Gateway (8090) - 网关，路由到所有服务

# 1. 启动 Workflow Engine Core
echo "1️⃣  启动 Workflow Engine Core (端口 8091)..."
cd "$BASE_DIR/backend/workflow-engine-core"
nohup mvn spring-boot:run > "$LOG_DIR/workflow-engine.log" 2>&1 &
WORKFLOW_ENGINE_PID=$!
echo "   PID: $WORKFLOW_ENGINE_PID"
sleep 10

# 2. 启动 Admin Center
echo "2️⃣  启动 Admin Center (端口 8092)..."
cd "$BASE_DIR/backend/admin-center"
nohup mvn spring-boot:run > "$LOG_DIR/admin-center.log" 2>&1 &
ADMIN_CENTER_PID=$!
echo "   PID: $ADMIN_CENTER_PID"
sleep 10

# 3. 启动 User Portal
echo "3️⃣  启动 User Portal (端口 8093)..."
cd "$BASE_DIR/backend/user-portal"
nohup mvn spring-boot:run > "$LOG_DIR/user-portal.log" 2>&1 &
USER_PORTAL_PID=$!
echo "   PID: $USER_PORTAL_PID"
sleep 10

# 4. 启动 Developer Workstation
echo "4️⃣  启动 Developer Workstation (端口 8094)..."
cd "$BASE_DIR/backend/developer-workstation"
nohup mvn spring-boot:run > "$LOG_DIR/developer-workstation.log" 2>&1 &
DEV_WORKSTATION_PID=$!
echo "   PID: $DEV_WORKSTATION_PID"
sleep 10

# 5. 启动 API Gateway
echo "5️⃣  启动 API Gateway (端口 8090)..."
cd "$BASE_DIR/backend/api-gateway"
nohup mvn spring-boot:run > "$LOG_DIR/api-gateway.log" 2>&1 &
API_GATEWAY_PID=$!
echo "   PID: $API_GATEWAY_PID"

# 保存 PID 到文件
echo "$API_GATEWAY_PID" > "$LOG_DIR/api-gateway.pid"
echo "$WORKFLOW_ENGINE_PID" > "$LOG_DIR/workflow-engine.pid"
echo "$ADMIN_CENTER_PID" > "$LOG_DIR/admin-center.pid"
echo "$DEV_WORKSTATION_PID" > "$LOG_DIR/developer-workstation.pid"
echo "$USER_PORTAL_PID" > "$LOG_DIR/user-portal.pid"

echo ""
echo "✅ 所有后端服务已启动！"
echo ""
echo "服务访问地址："
echo "- API Gateway: http://localhost:8090"
echo "- Workflow Engine: http://localhost:8091"
echo "- Admin Center: http://localhost:8092"
echo "- User Portal: http://localhost:8093"
echo "- Developer Workstation: http://localhost:8094"
echo ""
echo "查看日志："
echo "  tail -f $LOG_DIR/*.log"
echo ""
echo "停止服务："
echo "  ./stop-backend.sh"
