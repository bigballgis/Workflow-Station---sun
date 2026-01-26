# 用户列表页面数据修复说明

## 问题
访问 http://localhost:3000/user/list 页面没有显示用户数据。

## 根本原因
数据库中缺少初始化数据。虽然表结构存在，但 sys_users 等表是空的。

## 已执行的修复

### 1. 添加初始化数据
已创建并执行 `init-all-data.sql` 文件，包含：
- ✅ 7 个系统角色（SYS_ADMIN, AUDITOR, DEVELOPER, TEAM_LEADER, TECH_DIRECTOR, MANAGER, USER）
- ✅ 5 个系统虚拟组
- ✅ 虚拟组-角色绑定
- ✅ 8 个系统权限
- ✅ 角色-权限分配
- ✅ 5 个测试用户
- ✅ 用户-业务单元关联
- ✅ 虚拟组成员
- ✅ 角色分配

### 2. 验证数据
```bash
# 用户数量
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT COUNT(*) FROM sys_users;"
# 结果：5 个用户

# 用户列表
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT username, full_name FROM sys_users;"
```

### 3. 验证后端 API
```bash
curl http://localhost:8090/api/v1/admin/users
# 返回：5 个用户的完整数据
```

## 测试用户账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| admin | password123 | System Administrator | 系统管理员 |
| tech.director | password123 | Technical Director | 技术总监 |
| hr.manager | password123 | Manager | HR 经理 |
| corp.manager | password123 | Manager | 公司经理 |
| dev.john | password123 | Developer | 开发人员 |

## 下一步操作

### 如果前端仍然没有数据：

1. **检查是否需要登录**
   - 访问 http://localhost:3000/login
   - 使用 admin / password123 登录
   - 然后访问用户列表页面

2. **检查浏览器控制台**
   - 打开浏览器开发者工具（F12）
   - 查看 Console 标签是否有错误
   - 查看 Network 标签，检查 API 请求是否成功

3. **检查前端服务**
   ```bash
   # 确认前端服务正在运行
   ps aux | grep "vite.*admin-center"
   ```

4. **重启前端服务（如果需要）**
   ```bash
   # 停止前端
   pkill -f "vite.*admin-center"
   
   # 启动前端
   cd frontend/admin-center
   npm run dev
   ```

## 后端 API 端点

- 用户列表：`GET http://localhost:8090/api/v1/admin/users`
- 用户详情：`GET http://localhost:8090/api/v1/admin/users/{id}`
- 创建用户：`POST http://localhost:8090/api/v1/admin/users`

## 前端代理配置

前端通过 Vite 代理访问后端：
- 前端地址：http://localhost:3000
- 后端地址：http://localhost:8090
- 代理规则：`/api/v1/admin` → `http://localhost:8090/api/v1/admin`

## 数据库连接信息

- 数据库：workflow_platform
- 用户名：platform
- 密码：platform123
- 容器：platform-postgres

## 相关文件

- 初始化数据：`init-all-data.sql`
- 业务单元数据：`add-business-units-data.sql`
- 前端 API：`frontend/admin-center/src/api/user.ts`
- 后端控制器：`backend/admin-center/src/main/java/com/admin/controller/UserController.java`
