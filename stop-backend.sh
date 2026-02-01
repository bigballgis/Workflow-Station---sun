#!/bin/bash

# =====================================================
# 停止后端服务脚本 (macOS)
# =====================================================

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"

echo "🛑 停止后端服务..."

# 停止函数
stop_service() {
    local service_name=$1
    local pid_file=$2
    
    if [ -f "$pid_file" ]; then
        PID=$(cat "$pid_file")
        if [ -n "$PID" ] && [ "$PID" != "0" ] && kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            echo "✅ 已停止 $service_name (PID: $PID)"
            # 等待进程结束
            for i in {1..10}; do
                if ! kill -0 "$PID" 2>/dev/null; then
                    break
                fi
                sleep 0.5
            done
            # 如果还没停止，强制杀死
            if kill -0 "$PID" 2>/dev/null; then
                kill -9 "$PID" 2>/dev/null
                echo "⚠️  强制停止 $service_name"
            fi
        else
            echo "ℹ️  $service_name 未运行"
        fi
        rm -f "$pid_file"
    else
        echo "ℹ️  未找到 $service_name 的 PID 文件"
    fi
}

# 停止所有后端服务
stop_service "API Gateway" "$LOG_DIR/api-gateway-prod.pid"
stop_service "Workflow Engine" "$LOG_DIR/workflow-engine-prod.pid"
stop_service "Admin Center" "$LOG_DIR/admin-center-prod.pid"
stop_service "Developer Workstation" "$LOG_DIR/developer-workstation-prod.pid"
stop_service "User Portal" "$LOG_DIR/user-portal-prod.pid"

# 额外检查：通过端口查找并停止 Java 进程
echo ""
echo "🔍 检查端口占用..."

for port in 8090 8091 8092 8093 8094; do
    PID=$(lsof -ti:$port 2>/dev/null)
    if [ -n "$PID" ]; then
        echo "⚠️  端口 $port 仍被占用 (PID: $PID)，正在停止..."
        kill -9 "$PID" 2>/dev/null
    fi
done

echo ""
echo "✅ 所有后端服务已停止"
