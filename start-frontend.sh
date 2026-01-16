#!/bin/bash

# =====================================================
# 启动前端服务脚本
# =====================================================

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"
mkdir -p "$LOG_DIR"

echo "🎨 启动前端服务..."
echo ""

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到 Node.js，请先安装 Node.js 20+"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 20 ]; then
    echo "⚠️  警告: Node.js 版本过低，建议使用 Node.js 20+"
fi

# 启动 Frontend Admin
echo "1️⃣  启动 Frontend Admin (端口 3000)..."
cd "$BASE_DIR/frontend/admin-center"
if [ ! -d "node_modules" ]; then
    echo "   安装依赖..."
    npm install
fi
nohup npm run dev > "$LOG_DIR/frontend-admin.log" 2>&1 &
FRONTEND_ADMIN_PID=$!
echo "   PID: $FRONTEND_ADMIN_PID"
sleep 3

# 启动 Frontend Portal
echo "2️⃣  启动 Frontend Portal (端口 3001)..."
cd "$BASE_DIR/frontend/user-portal"
if [ ! -d "node_modules" ]; then
    echo "   安装依赖..."
    npm install
fi
nohup npm run dev > "$LOG_DIR/frontend-portal.log" 2>&1 &
FRONTEND_PORTAL_PID=$!
echo "   PID: $FRONTEND_PORTAL_PID"
sleep 3

# 启动 Frontend Developer
echo "3️⃣  启动 Frontend Developer (端口 3002)..."
cd "$BASE_DIR/frontend/developer-workstation"
if [ ! -d "node_modules" ]; then
    echo "   安装依赖..."
    npm install
fi
nohup npm run dev > "$LOG_DIR/frontend-developer.log" 2>&1 &
FRONTEND_DEVELOPER_PID=$!
echo "   PID: $FRONTEND_DEVELOPER_PID"

# 保存 PID 到文件
echo "$FRONTEND_ADMIN_PID" > "$LOG_DIR/frontend-admin.pid"
echo "$FRONTEND_PORTAL_PID" > "$LOG_DIR/frontend-portal.pid"
echo "$FRONTEND_DEVELOPER_PID" > "$LOG_DIR/frontend-developer.pid"

echo ""
echo "✅ 所有前端服务已启动！"
echo ""
echo "服务访问地址："
echo "- Frontend Admin: http://localhost:3000"
echo "- Frontend Portal: http://localhost:3001"
echo "- Frontend Developer: http://localhost:3002"
echo ""
echo "查看日志："
echo "  tail -f $LOG_DIR/frontend-*.log"
echo ""
echo "停止服务："
echo "  ./stop-frontend.sh"
