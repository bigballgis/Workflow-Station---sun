#!/bin/bash

# =====================================================
# 停止后端服务脚本
# =====================================================

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"

echo "🛑 停止后端服务..."

if [ -f "$LOG_DIR/api-gateway.pid" ]; then
    PID=$(cat "$LOG_DIR/api-gateway.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 API Gateway (PID: $PID)"
    fi
    rm -f "$LOG_DIR/api-gateway.pid"
fi

if [ -f "$LOG_DIR/workflow-engine.pid" ]; then
    PID=$(cat "$LOG_DIR/workflow-engine.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Workflow Engine (PID: $PID)"
    fi
    rm -f "$LOG_DIR/workflow-engine.pid"
fi

if [ -f "$LOG_DIR/admin-center.pid" ]; then
    PID=$(cat "$LOG_DIR/admin-center.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Admin Center (PID: $PID)"
    fi
    rm -f "$LOG_DIR/admin-center.pid"
fi

if [ -f "$LOG_DIR/developer-workstation.pid" ]; then
    PID=$(cat "$LOG_DIR/developer-workstation.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Developer Workstation (PID: $PID)"
    fi
    rm -f "$LOG_DIR/developer-workstation.pid"
fi

if [ -f "$LOG_DIR/user-portal.pid" ]; then
    PID=$(cat "$LOG_DIR/user-portal.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 User Portal (PID: $PID)"
    fi
    rm -f "$LOG_DIR/user-portal.pid"
fi

echo ""
echo "✅ 所有后端服务已停止"
