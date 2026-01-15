#!/bin/bash

# =====================================================
# 停止前端服务脚本
# =====================================================

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"

echo "🛑 停止前端服务..."

if [ -f "$LOG_DIR/frontend-admin.pid" ]; then
    PID=$(cat "$LOG_DIR/frontend-admin.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Frontend Admin (PID: $PID)"
    fi
    rm -f "$LOG_DIR/frontend-admin.pid"
fi

if [ -f "$LOG_DIR/frontend-portal.pid" ]; then
    PID=$(cat "$LOG_DIR/frontend-portal.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Frontend Portal (PID: $PID)"
    fi
    rm -f "$LOG_DIR/frontend-portal.pid"
fi

if [ -f "$LOG_DIR/frontend-developer.pid" ]; then
    PID=$(cat "$LOG_DIR/frontend-developer.pid")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "✅ 已停止 Frontend Developer (PID: $PID)"
    fi
    rm -f "$LOG_DIR/frontend-developer.pid"
fi

echo ""
echo "✅ 所有前端服务已停止"
