# 登录调试指南

## 问题：登录时没有打印出密码哈希值

## 已添加的调试日志位置

### 1. RequestLoggingFilter（最早拦截）
**文件**: `backend/admin-center/src/main/java/com/admin/config/RequestLoggingFilter.java`

**作用**: 在请求到达 Controller 之前就记录日志

**输出内容**:
- Request URI
- Request URL
- Context Path
- Servlet Path
- Method
- Remote Address

### 2. AuthController.login()（Controller 层）
**文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`

**输出内容**:
- 确认请求到达了 Controller
- 请求路径信息
- 用户名

### 3. AuthServiceImpl.login()（Service 层）
**文件**: `backend/admin-center/src/main/java/com/admin/service/impl/AuthServiceImpl.java`

**输出内容**:
- 用户名
- **密码哈希值**（重点）
- 哈希值长度
- 用户状态

## 调试步骤

### 步骤 1: 确认服务已启动

```bash
# 检查 admin-center 服务是否运行
ps aux | grep admin-center
# 或
lsof -i:8090
```

### 步骤 2: 检查日志文件

```bash
# 查看日志文件
tail -f logs/admin-center.log

# 或查看控制台输出（如果使用 mvn spring-boot:run）
```

### 步骤 3: 确认请求路径

前端调用：`/api/v1/auth/login`

根据 `vite.config.ts` 的代理配置：
- 开发模式：`/api/v1/auth` → 重写为 `/api/v1/admin/auth` → 转发到 `http://localhost:8090`
- 实际后端路径：`/api/v1/admin/auth/login`

### 步骤 4: 测试直接调用 API

使用 curl 或 Postman 直接测试：

```bash
curl -X POST http://localhost:8090/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 步骤 5: 检查是否有其他服务处理登录

可能的情况：
1. **请求被路由到 platform-security**
   - `platform-security` 也有 `/api/v1/auth/login` 端点
   - 如果请求没有经过 context-path，可能被 platform-security 处理

2. **请求被 API Gateway 拦截**
   - 如果使用了 API Gateway，请求可能被路由到其他服务

3. **前端代理配置问题**
   - 检查 `vite.config.ts` 的代理配置是否正确

## 排查方法

### 方法 1: 检查所有服务的日志

```bash
# 检查 admin-center 日志
tail -f logs/admin-center.log

# 检查其他服务的日志（如果有）
tail -f logs/workflow-engine.log
tail -f logs/api-gateway.log
```

### 方法 2: 在浏览器开发者工具中检查

1. 打开浏览器开发者工具（F12）
2. 切换到 Network 标签
3. 点击登录按钮
4. 查看实际发送的请求：
   - URL
   - 请求方法
   - 响应状态码
   - 响应内容

### 方法 3: 检查服务是否扫描到 Controller

在 `AdminCenterApplication` 中，确认 `AuthController` 没有被排除：

```java
@ComponentScan(
    basePackages = {"com.admin"},  // 应该包含 com.admin.controller
    excludeFilters = {
        // 注意：这里排除了 platform.security.controller，但不应该排除 admin.controller
    }
)
```

### 方法 4: 添加启动日志

在 `AuthController` 的构造函数中添加日志：

```java
public AuthController(AuthService authService) {
    this.authService = authService;
    System.out.println("=== AuthController 已初始化 ===");
    log.info("AuthController initialized");
}
```

## 预期输出

如果一切正常，应该看到以下输出（按顺序）：

```
=== RequestLoggingFilter: Login request detected ===
Request URI: /api/v1/admin/auth/login
...

=== AuthController.login() called ===
Request URI: /api/v1/admin/auth/login
Username: admin
...

=== AuthServiceImpl.login() 被调用了！ ===
Username: admin
...

=== 密码哈希值调试信息 ===
用户名: admin
密码哈希值: $2a$10$EIXvYkRAhq0xaOye6lEnoOQowMIJQx1QpO1XLbHrZhtLc/4sHlUHq
哈希值长度: 60
...
```

## 如果仍然没有输出

### 可能的原因：

1. **服务没有正确启动**
   - 检查服务是否真的在运行
   - 检查端口 8090 是否被占用

2. **请求被路由到其他服务**
   - 检查是否有 API Gateway 或其他代理
   - 检查前端代理配置

3. **日志级别问题**
   - 虽然使用了 `System.out.println()`，但如果服务没有运行，也不会有输出

4. **请求根本没有到达后端**
   - 检查网络连接
   - 检查 CORS 配置
   - 检查前端是否有错误

## 快速验证方法

在 `AuthController` 的构造函数中添加：

```java
public AuthController(AuthService authService) {
    this.authService = authService;
    System.out.println("========================================");
    System.out.println("AuthController 已创建！");
    System.out.println("AuthService: " + (authService != null ? "已注入" : "未注入"));
    System.out.println("========================================");
}
```

如果服务启动时没有看到这个输出，说明 Controller 没有被扫描到。

## 下一步

1. 重新启动 admin-center 服务
2. 检查启动日志，确认所有组件都已加载
3. 尝试登录，查看控制台输出
4. 如果仍然没有输出，检查浏览器 Network 标签，确认请求的实际 URL 和响应
