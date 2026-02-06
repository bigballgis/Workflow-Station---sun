# 测试自定义操作按钮

## 测试步骤

### 1. 访问任务详情页面

打开浏览器访问：
```
http://localhost:3001/tasks/4cad1fce-02bb-11f1-9c21-5aaa8f1520e4
```

### 2. 验证自定义按钮显示

在页面底部的"操作按钮"区域，应该看到4个自定义按钮：

1. **Verify Documents** (蓝色按钮，带圆形勾选图标)
2. **Approve Loan** (绿色按钮，带勾选图标)
3. **Reject Loan** (红色按钮，带圆形叉号图标)
4. **Request Additional Info** (黄色按钮，带文件图标)

### 3. 测试按钮功能

#### 测试 "Verify Documents" 按钮
1. 点击 "Verify Documents" 按钮
2. 应该弹出审批对话框，标题为 "Verify Documents"
3. 可以输入评论
4. 点击确认提交

#### 测试 "Approve Loan" 按钮
1. 点击 "Approve Loan" 按钮
2. 应该弹出审批对话框，标题为 "Approve Loan"
3. 可以输入评论
4. 点击确认提交

#### 测试 "Reject Loan" 按钮
1. 点击 "Reject Loan" 按钮
2. 应该弹出审批对话框，标题为 "Reject Loan"
3. 可以输入评论
4. 点击确认提交

#### 测试 "Request Additional Info" 按钮
1. 点击 "Request Additional Info" 按钮
2. 应该显示消息："打开表单: 7"
3. （注意：表单弹窗功能尚未实现，这是预期行为）

### 4. 验证API响应

打开浏览器开发者工具（F12），切换到 Network 标签：

1. 刷新页面
2. 找到请求：`GET /api/portal/tasks/4cad1fce-02bb-11f1-9c21-5aaa8f1520e4`
3. 查看响应，应该包含 `actions` 数组：

```json
{
  "success": true,
  "data": {
    "taskId": "4cad1fce-02bb-11f1-9c21-5aaa8f1520e4",
    "taskName": "Verify Documents",
    "actions": [
      {
        "actionId": "action-dl-verify-docs",
        "actionName": "Verify Documents",
        "actionType": "APPROVE",
        "icon": "check-circle",
        "buttonColor": "primary"
      },
      {
        "actionId": "action-dl-approve-loan",
        "actionName": "Approve Loan",
        "actionType": "APPROVE",
        "icon": "check",
        "buttonColor": "success"
      },
      {
        "actionId": "action-dl-reject-loan",
        "actionName": "Reject Loan",
        "actionType": "REJECT",
        "icon": "times-circle",
        "buttonColor": "danger"
      },
      {
        "actionId": "action-dl-request-info",
        "actionName": "Request Additional Info",
        "actionType": "FORM_POPUP",
        "icon": "file-alt",
        "buttonColor": "warning",
        "configJson": "{\"formId\": 7}"
      }
    ]
  }
}
```

### 5. 验证控制台日志

打开浏览器开发者工具（F12），切换到 Console 标签：

1. 点击任意自定义按钮
2. 应该看到日志：`Custom action clicked: {actionId: "...", actionName: "...", ...}`

### 6. 测试向后兼容性

为了测试默认按钮（当任务没有自定义actions时）：

1. 访问一个没有配置actionIds的任务
2. 应该看到默认的5个按钮：
   - Approve (绿色)
   - Reject (红色)
   - Delegate (默认)
   - Transfer (默认)
   - Urge (黄色)

---

## 预期结果

✅ 页面显示4个自定义操作按钮  
✅ 按钮颜色正确（蓝、绿、红、黄）  
✅ 按钮图标正确显示  
✅ 点击APPROVE类型按钮打开审批对话框  
✅ 点击REJECT类型按钮打开审批对话框  
✅ 点击FORM_POPUP类型按钮显示消息  
✅ API返回完整的actions数组  
✅ 控制台显示正确的日志  

---

## 故障排除

### 问题1: 看不到自定义按钮，只看到默认按钮

**可能原因**:
- 前端代码未更新
- 浏览器缓存

**解决方案**:
```bash
# 重新构建前端
cd frontend/user-portal
npx vite build

# 重新部署
docker cp dist platform-user-portal-frontend-dev:/usr/share/nginx/html
docker restart platform-user-portal-frontend-dev

# 清除浏览器缓存（Ctrl+Shift+Delete）或使用无痕模式
```

### 问题2: API没有返回actions数组

**可能原因**:
- 后端代码未更新
- 数据库中没有action定义

**解决方案**:
```bash
# 检查数据库
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -c "SELECT * FROM sys_action_definitions;"

# 如果没有数据，重新插入
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev < deploy/init-scripts/06-digital-lending/07-copy-actions-to-admin.sql

# 重新构建和部署后端
mvn clean package -DskipTests -pl backend/user-portal -am
docker cp backend/user-portal/target/user-portal-1.0.0-SNAPSHOT.jar platform-user-portal-dev:/app/app.jar
docker restart platform-user-portal-dev
```

### 问题3: 按钮显示但图标不显示

**可能原因**:
- 图标名称映射错误
- Element Plus图标未正确导入

**解决方案**:
检查 `frontend/user-portal/src/views/tasks/detail.vue` 中的 `getIconComponent` 函数，确保所有图标都已导入。

---

## 下一步

1. 实现FORM_POPUP功能（表单弹窗）
2. 为其他BPMN任务添加actionIds
3. 添加国际化支持
4. 实现权限控制
