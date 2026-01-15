# 修复任务查询 500 错误

## 问题描述

在 `http://localhost:3001/tasks` 中浏览待办任务时，返回 500 错误：
```
POST http://localhost:3001/api/portal/tasks/query 500 (Internal Server Error)
```

## 问题原因

后端日志显示：
```
Workflow engine not available: I/O error on GET request for "http://localhost:8081/actuator/health": Connection refused
java.lang.IllegalStateException: Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动
```

**根本原因**：Workflow Engine 服务（端口 8081）没有运行，导致 `TaskQueryComponent` 无法连接到 Flowable 引擎查询任务。

## 修复方案

### 1. 修复数据库和 Redis 密码配置

Workflow Engine 的配置文件中数据库和 Redis 密码为空，导致无法连接。已修复：

```yaml
# backend/workflow-engine-core/src/main/resources/application.yml
datasource:
  password: ${SPRING_DATASOURCE_PASSWORD:platform123}  # 添加默认密码

data:
  redis:
    password: ${SPRING_REDIS_PASSWORD:redis123}  # 添加默认密码
```

### 2. 启动 Workflow Engine 服务

Workflow Engine 是查询任务的核心服务，必须运行才能查询待办任务。

```bash
cd /Users/qiweige/Desktop/PROJECTXX/Workflow-Station---sun
./start-backend.sh
```

或者单独启动 Workflow Engine：

```bash
cd /Users/qiweige/Desktop/PROJECTXX/Workflow-Station---sun/backend/workflow-engine
mvn spring-boot:run
```

### 2. 验证服务状态

启动后，验证服务是否正常运行：

```bash
# 检查健康状态
curl http://localhost:8081/actuator/health

# 应该返回: {"status":"UP"}
```

### 3. 服务依赖关系

任务查询功能的依赖关系：
1. **User Portal** (8082) - 提供任务查询 API
2. **Workflow Engine** (8081) - 提供 Flowable 引擎，存储和查询任务
3. **Admin Center** (8090) - 提供用户权限和虚拟组信息

所有服务都需要运行才能正常查询任务。

## 验证

修复后，应该能够：
1. 访问 `http://localhost:3001/tasks`
2. 正常查询待办任务列表
3. 不再出现 500 错误

## 相关服务

- **Workflow Engine**: http://localhost:8081
  - 健康检查: http://localhost:8081/actuator/health
- **User Portal**: http://localhost:8082
  - 任务查询 API: POST /api/portal/tasks/query

## 注意事项

1. **服务启动顺序**：建议先启动基础设施服务（PostgreSQL, Redis, Kafka），然后启动后端服务
2. **服务启动时间**：Workflow Engine 需要 30-60 秒完全启动
3. **服务检查**：如果任务查询仍然失败，检查 Workflow Engine 日志：
   ```bash
   tail -f logs/workflow-engine.log
   ```
