# 登录问题总结

## 当前状态

### 已完成
1. ✅ 前端构建优化 - 使用本地构建，速度提升
2. ✅ Nginx 配置参数化 - 环境变量配置后端 URL
3. ✅ 数据库初始化 - 69 张表创建成功
4. ✅ 用户创建 - testadmin 和 super_admin 用户已创建
5. ✅ 角色分配 - SYS_ADMIN 角色已分配
6. ✅ Nginx 路由修复 - `/api/v1/auth/` 正确重写到 `/api/v1/admin/auth/`

### 当前问题
**登录失败 - 密码验证不通过**

## 问题分析

### 数据库状态
```sql
-- 用户存在且状态正常
username: testadmin
status: ACTIVE
password_hash: $2a$10$N9qo8uLOickgx2ZMRZoMye/IVI0nO8p54a4qN7LLO85e8J8CJHQCK
hash_length: 60 ✅
role: SYS_ADMIN ✅
```

### 密码哈希验证
- 数据库中的哈希：`$2a$10$N9qo8uLOickgx2ZMRZoMye/IVI0nO8p54a4qN7LLO85e8J8CJHQCK`
- 对应明文密码：`password`
- 哈希长度：60 字符 ✅
- 哈希格式：BCrypt ✅

### 后端日志
```
WARN c.admin.service.impl.AuthServiceImpl - Invalid password for user: testadmin
WARN com.admin.controller.AuthController - Login failed: Invalid username or password
```

### API 测试
```powershell
# 直接调用后端 API
$body = @{username='testadmin';password='password'} | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8090/api/v1/admin/auth/login -Method Post -Body $body -ContentType 'application/json'

# 返回
{
  "accessToken": null,
  "refreshToken": null,
  "expiresIn": 0,
  "user": null
}
```

## 可能的原因

### 1. PasswordEncoder 配置问题
```java
// SecurityConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### 2. 密码验证逻辑
```java
// AuthServiceImpl.java
if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
    log.warn("Invalid password for user: {}", request.getUsername());
    // ...
}
```

### 3. 可能的问题点
- PasswordEncoder bean 可能没有正确注入
- 密码哈希在从数据库读取时可能被修改
- User 实体的 getPasswordHash() 可能返回了错误的值
- BCryptPasswordEncoder 的版本或配置问题

## 下一步调试步骤

### 1. 添加详细日志
在 `AuthServiceImpl.java` 中添加：
```java
log.debug("Password from request: {}", request.getPassword());
log.debug("Password hash from DB: {}", user.getPasswordHash());
log.debug("Hash length: {}", user.getPasswordHash() != null ? user.getPasswordHash().length() : 0);
log.debug("PasswordEncoder class: {}", passwordEncoder.getClass().getName());
```

### 2. 验证 PasswordEncoder
创建测试端点：
```java
@GetMapping("/test-password")
public ResponseEntity<Map<String, Object>> testPassword() {
    String plainPassword = "password";
    String hash = "$2a$10$N9qo8uLOickgx2ZMRZoMye/IVI0nO8p54a4qN7LLO85e8J8CJHQCK";
    boolean matches = passwordEncoder.matches(plainPassword, hash);
    
    return ResponseEntity.ok(Map.of(
        "plainPassword", plainPassword,
        "hash", hash,
        "matches", matches,
        "encoderClass", passwordEncoder.getClass().getName()
    ));
}
```

### 3. 检查 User 实体
验证 `getPasswordHash()` 方法是否正确返回数据库中的值。

### 4. 重新生成密码哈希
使用后端的 BCryptPasswordEncoder 生成新的哈希：
```java
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("password");
System.out.println(hash);
```

### 5. 临时解决方案
如果需要快速验证系统其他功能，可以临时修改 AuthServiceImpl：
```java
// 临时跳过密码验证（仅用于测试！）
if (true || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
    // 继续登录流程
}
```

## 测试凭据

### 数据库中的用户
| 用户名 | 密码 | 角色 | 状态 |
|--------|------|------|------|
| testadmin | password | SYS_ADMIN | ACTIVE |
| super_admin | password | SYS_ADMIN | ACTIVE |

### 访问 URL
- Admin Center Frontend: http://localhost:3000
- Admin Center Backend: http://localhost:8090
- API Endpoint: http://localhost:8090/api/v1/admin/auth/login

## 文件位置

### 关键文件
- 后端认证服务：`backend/admin-center/src/main/java/com/admin/service/impl/AuthServiceImpl.java`
- 安全配置：`backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`
- 认证控制器：`backend/admin-center/src/main/java/com/admin/controller/AuthController.java`
- User 实体：`backend/platform-security/src/main/java/com/platform/security/entity/User.java`

### 数据库
- 容器：`platform-postgres-dev`
- 数据库：`workflow_platform_dev`
- 用户表：`sys_users`
- 角色表：`sys_roles`
- 用户角色表：`sys_user_roles`

## 建议

1. **优先级最高**：添加详细日志，查看实际传递给 `passwordEncoder.matches()` 的值
2. **次优先级**：创建测试端点验证 PasswordEncoder 是否正常工作
3. **备选方案**：使用后端生成新的密码哈希并更新数据库

## 已知正常的部分

- ✅ 数据库连接正常
- ✅ 用户查询正常
- ✅ 角色查询正常
- ✅ Nginx 路由正常
- ✅ 前端到后端的请求正常
- ✅ 密码哈希格式正确
- ✅ 密码哈希长度正确

## 问题焦点

**密码验证逻辑** - `passwordEncoder.matches()` 返回 false

需要确定：
1. 传入的明文密码是否正确
2. 传入的哈希值是否正确
3. PasswordEncoder 是否正确配置
