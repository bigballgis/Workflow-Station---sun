# 服务启动状态

**更新时间**: $(date)

## ✅ 服务状态

### 基础设施服务
- ✅ **PostgreSQL** - 运行中 (端口 5432)
- ✅ **Redis** - 运行中 (端口 6379)
- ✅ **Kafka** - 运行中 (端口 9092)
- ✅ **Zookeeper** - 运行中 (端口 2181)

### 后端服务
- ✅ **API Gateway** - http://localhost:8080 (健康检查: UP)
- ✅ **Workflow Engine** - http://localhost:8081
- ✅ **Admin Center** - http://localhost:8090
- ✅ **User Portal** - http://localhost:8082
- ✅ **Developer Workstation** - http://localhost:8083

### 前端服务
- ✅ **Frontend Admin** - http://localhost:3000
- ✅ **Frontend Portal** - http://localhost:3001
- ✅ **Frontend Developer** - http://localhost:3002

## 📝 常用命令

### 查看服务日志
```bash
# 查看所有后端服务日志
tail -f logs/*.log

# 查看特定服务日志
tail -f logs/api-gateway.log
tail -f logs/workflow-engine.log
tail -f logs/admin-center.log
tail -f logs/user-portal.log
tail -f logs/developer-workstation.log

# 查看前端服务日志
tail -f logs/frontend-*.log
```

### 停止服务
```bash
# 停止所有后端服务
./stop-backend.sh

# 停止所有前端服务
./stop-frontend.sh
```

### 重启服务
```bash
# 重启后端服务
./stop-backend.sh && ./start-backend.sh

# 重启前端服务
./stop-frontend.sh && ./start-frontend.sh
```

## 🔐 测试账户

- **系统管理员**: `admin / admin123`
- **HR经理**: `hr.manager / admin123`
- **企业银行总监**: `corp.director / admin123`
- **技术总监**: `tech.director / admin123`
- **开发团队负责人**: `core.lead / admin123`
- **开发人员**: `dev.john / admin123`

## 🌐 访问地址

### 前端应用
- **管理员中心**: http://localhost:3000
- **用户门户**: http://localhost:3001
- **开发者工作站**: http://localhost:3002

### 后端 API
- **API Gateway**: http://localhost:8080
  - 健康检查: http://localhost:8080/actuator/health
- **Workflow Engine**: http://localhost:8081
- **Admin Center**: http://localhost:8090
- **User Portal**: http://localhost:8082
- **Developer Workstation**: http://localhost:8083

## 📊 服务端口汇总

| 服务 | 端口 | 说明 |
|------|------|------|
| API Gateway | 8080 | 统一入口 |
| Workflow Engine | 8081 | 工作流引擎 |
| User Portal | 8082 | 用户门户后端 |
| Developer Workstation | 8083 | 开发者工作站后端 |
| Admin Center | 8090 | 管理员中心后端 |
| Frontend Admin | 3000 | 管理员前端 |
| Frontend Portal | 3001 | 用户门户前端 |
| Frontend Developer | 3002 | 开发者工作站前端 |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Kafka | 9092 | 消息队列 |
| Zookeeper | 2181 | Kafka 协调器 |

## ⚠️ 注意事项

1. **服务启动时间**: 后端服务需要 30-60 秒完全启动，请耐心等待
2. **数据库连接**: 确保 PostgreSQL 服务正常运行
3. **端口冲突**: 如果端口被占用，请检查并停止占用端口的进程
4. **日志查看**: 如果服务无法访问，请查看对应的日志文件

## 🐛 故障排查

### 服务无法启动
1. 检查 Java 版本: `java -version` (需要 Java 17+)
2. 检查 Maven: `mvn -version`
3. 检查 Node.js: `node -v` (需要 Node.js 20+)
4. 查看日志: `tail -f logs/[service-name].log`

### 数据库连接失败
1. 检查 PostgreSQL 是否运行: `docker-compose ps postgres`
2. 检查数据库密码配置是否正确
3. 验证数据库连接: `psql -h localhost -U platform -d workflow_platform`

### 端口被占用
```bash
# 查找占用端口的进程
lsof -i :8080
lsof -i :3000

# 停止占用端口的进程
kill -9 [PID]
```
