# Actions 不显示问题排查指南

## 问题

在 Developer Workstation 的 PURCHASE Function Unit 中，Action Design 标签页显示为空，看不到任何 actions。

## 已完成的修复

1. ✅ 数据库中已成功插入 9 个 actions
2. ✅ 后端 ActionType 枚举已更新，添加了 SAVE, CANCEL, EXPORT 类型
3. ✅ developer-workstation 服务已重新编译和部署
4. ✅ 服务启动成功，没有 IllegalArgumentException 错误

## 排查步骤

### 1. 刷新浏览器

**重要**: 浏览器可能缓存了旧的 JavaScript 代码或 API 响应。

```
按 Ctrl + Shift + R (Windows/Linux) 或 Cmd + Shift + R (Mac) 强制刷新
```

### 2. 检查浏览器开发者工具

按 F12 打开开发者工具，然后：

#### A. 检查 Console 标签

查看是否有 JavaScript 错误，例如：
- `TypeError: Cannot read property...`
- `Network Error`
- `401 Unauthorized`
- `403 Forbidden`

#### B. 检查 Network 标签

1. 刷新页面
2. 在 Network 标签中找到 `/api/v1/function-units/1/actions` 请求
3. 点击该请求，查看：
   - **Status**: 应该是 200 OK
   - **Response**: 应该返回 JSON 数组，包含 9 个 actions
   - **Headers**: 检查 Authorization 头是否存在

**预期的响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 5,
      "actionName": "submit",
      "actionType": "PROCESS_SUBMIT",
      "description": "Submit Purchase Request",
      "configJson": {...},
      "icon": "send",
      "buttonColor": "primary",
      "isDefault": true
    },
    // ... 其他 8 个 actions
  ]
}
```

### 3. 检查认证状态

在浏览器 Console 中运行：

```javascript
// 检查是否有 token
console.log('Token:', localStorage.getItem('token'));

// 检查用户信息
console.log('User:', localStorage.getItem('user'));

// 检查是否已登录
console.log('Is Authenticated:', !!localStorage.getItem('token'));
```

如果 token 为 null，说明未登录或登录已过期，需要重新登录。

### 4. 手动测试 API

在浏览器 Console 中运行以下代码：

```javascript
// 获取 token
const token = localStorage.getItem('token');

// 调用 API
fetch('http://localhost:8083/api/v1/function-units/1/actions', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
.then(res => res.json())
.then(data => console.log('Actions:', data))
.catch(err => console.error('Error:', err));
```

### 5. 检查后端日志

```powershell
# 查看最新日志
docker logs platform-developer-workstation --tail 100

# 实时查看日志
docker logs platform-developer-workstation -f
```

查找以下内容：
- API 请求日志: `GET /api/v1/function-units/1/actions`
- 错误信息: `ERROR`, `Exception`, `IllegalArgumentException`
- 认证失败: `401`, `403`, `Unauthorized`

### 6. 验证数据库数据

```powershell
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SELECT id, action_name, action_type FROM dw_action_definitions WHERE function_unit_id = 1 ORDER BY id;"
```

应该返回 9 行数据 (ID: 5-13)。

### 7. 检查服务健康状态

```powershell
# 检查容器状态
docker ps --filter "name=platform-developer-workstation"

# 应该显示 "healthy" 或 "health: starting"
```

## 常见问题和解决方案

### 问题 1: 401 Unauthorized

**原因**: Token 过期或无效

**解决方案**:
1. 退出登录
2. 重新登录
3. 刷新页面

### 问题 2: 403 Forbidden

**原因**: 用户没有权限访问该 API

**解决方案**:
1. 确认使用的是 admin 账户
2. 检查用户角色和权限
3. 查看后端日志中的权限检查信息

### 问题 3: 404 Not Found

**原因**: API 路径错误或服务未启动

**解决方案**:
1. 检查 developer-workstation 服务是否运行
2. 确认 API 路径正确: `/api/v1/function-units/1/actions`
3. 检查 function_unit_id 是否正确 (PURCHASE 的 ID 是 1)

### 问题 4: 500 Internal Server Error

**原因**: 后端代码错误

**解决方案**:
1. 查看后端日志: `docker logs platform-developer-workstation --tail 100`
2. 查找 Exception 或 ERROR 信息
3. 如果是 `IllegalArgumentException: No enum constant`，说明枚举类型还没更新

### 问题 5: 前端显示空列表

**原因**: API 返回成功但数据为空数组

**可能的原因**:
1. function_unit_id 不正确
2. 数据库中没有数据
3. 前端解析响应错误

**解决方案**:
1. 在 Network 标签中检查实际的响应数据
2. 验证数据库中的数据
3. 检查前端 Console 是否有错误

## 调试技巧

### 启用详细日志

在 `backend/developer-workstation/src/main/resources/application.yml` 中添加：

```yaml
logging:
  level:
    com.developer: DEBUG
    org.springframework.web: DEBUG
```

然后重新编译和部署。

### 使用 Postman 测试 API

1. 打开 Postman
2. 创建新请求:
   - Method: GET
   - URL: `http://localhost:8083/api/v1/function-units/1/actions`
   - Headers:
     - `Authorization`: `Bearer <your_token>`
     - `Content-Type`: `application/json`
3. 发送请求
4. 查看响应

### 检查前端 Store 状态

在浏览器 Console 中：

```javascript
// 如果使用 Vue DevTools
// 查看 functionUnit store 的 actions 数组
```

## 下一步

如果以上步骤都无法解决问题，请提供以下信息：

1. 浏览器 Console 的错误信息（截图）
2. Network 标签中 `/api/v1/function-units/1/actions` 请求的详细信息（截图）
3. 后端日志中的相关错误信息
4. `localStorage.getItem('token')` 的值（前几个字符即可）

---

**更新日期**: 2026-02-06  
**作者**: Kiro AI Assistant
