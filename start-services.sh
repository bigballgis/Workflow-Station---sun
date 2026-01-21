#!/bin/bash

# =====================================================
# 启动项目前后端服务脚本
# =====================================================

set -e

echo "🚀 开始启动项目服务..."
echo ""

# 检查基础设施服务
echo "📦 检查基础设施服务..."
if ! docker-compose ps | grep -q "platform-postgres.*running"; then
    echo "启动基础设施服务..."
    docker-compose up -d postgres redis kafka zookeeper
    echo "等待服务就绪..."
    sleep 10
else
    echo "✅ 基础设施服务已运行"
fi

echo ""
echo "🔧 启动选项："
echo "1. 使用 Docker Compose 启动（推荐，需要先构建镜像）"
echo "2. 使用本地开发模式启动（需要 Java 17+ 和 Node.js 20+）"
echo ""
read -p "请选择启动方式 (1/2): " choice

case $choice in
    1)
        echo ""
        echo "🐳 使用 Docker Compose 启动服务..."
        echo "启动后端服务..."
        docker-compose --profile backend up -d
        
        echo "等待后端服务启动..."
        sleep 15
        
        echo "启动前端服务..."
        docker-compose --profile frontend up -d
        
        echo ""
        echo "✅ 所有服务已启动！"
        echo ""
        echo "服务访问地址："
        echo "- API Gateway: http://localhost:8080"
        echo "- Workflow Engine: http://localhost:8081"
        echo "- Admin Center: http://localhost:8090"
        echo "- User Portal: http://localhost:8082"
        echo "- Developer Workstation: http://localhost:8083"
        echo "- Frontend Admin: http://localhost:3000"
        echo "- Frontend Portal: http://localhost:3001"
        echo "- Frontend Developer: http://localhost:3002"
        ;;
    2)
        echo ""
        echo "💻 使用本地开发模式启动服务..."
        echo ""
        echo "⚠️  注意：需要在不同的终端窗口运行以下命令"
        echo ""
        echo "终端 1 - API Gateway:"
        echo "  cd backend/api-gateway && mvn spring-boot:run"
        echo ""
        echo "终端 2 - Workflow Engine:"
        echo "  cd backend/workflow-engine-core && mvn spring-boot:run"
        echo ""
        echo "终端 3 - Admin Center:"
        echo "  cd backend/admin-center && mvn spring-boot:run"
        echo ""
        echo "终端 4 - User Portal:"
        echo "  cd backend/user-portal && mvn spring-boot:run"
        echo ""
        echo "终端 5 - Developer Workstation:"
        echo "  cd backend/developer-workstation && mvn spring-boot:run"
        echo ""
        echo "终端 6 - Frontend Admin:"
        echo "  cd frontend/admin-center && npm install && npm run dev"
        echo ""
        echo "终端 7 - Frontend Portal:"
        echo "  cd frontend/user-portal && npm install && npm run dev"
        echo ""
        echo "终端 8 - Frontend Developer:"
        echo "  cd frontend/developer-workstation && npm install && npm run dev"
        ;;
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo ""
echo "📊 查看服务状态："
echo "  docker-compose ps"
echo ""
echo "📝 查看服务日志："
echo "  docker-compose logs -f [service-name]"
