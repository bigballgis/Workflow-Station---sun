# Form Popup 功能实现 ✅

## 功能描述

实现了 FORM_POPUP 类型的 action 按钮功能，允许用户点击按钮后弹出表单对话框。

## 问题

Action 按钮已经显示，但点击 "Perform Credit Check" 等 FORM_POPUP 类型的按钮时，表单弹窗没有打开，只显示了一个提示消息。

## 解决方案

### 1. 添加表单弹窗状态

在 `frontend/user-portal/src/views/tasks/detail.vue` 中添加了表单弹窗相关的响应式变量：

```typescript
// 表单弹窗状态
const formPopupVisible = ref(false)
const formPopupTitle = ref('')
const formPopupFields = ref<FormField[]>([])
const formPopupTabs = ref<FormTab[]>([])
const formPopupData = ref<Record<string, any>>({})
const formPopupReadOnly = ref(false)
const formPopupWidth = ref('800px')
const currentFormPopupAction = ref<TaskActionInfo | null>(null)
```

### 2. 实现表单弹窗逻辑

添加了三个新函数：

#### `openFormPopup(action, config)`
- 解析 action 的 configJson 获取表单配置
- 从 Admin Center API 获取表单定义
- 设置弹窗标题、宽度、只读状态
- 打开弹窗

#### `parseFormPopupConfig(config)`
- 解析 form-create 格式的表单配置
- 支持 Tab 布局和平铺布局
- 提取表单字段定义

#### `submitFormPopup()`
- 提交表单数据
- 更新流程变量或调用相应 API
- 刷新任务详情

### 3. 修改 handleCustomAction 函数

```typescript
case 'FORM_POPUP':
  try {
    const config = action.configJson ? JSON.parse(action.configJson) : {}
    console.log('Form popup config:', config)
    openFormPopup(action, config)
  } catch (error) {
    console.error('Failed to parse configJson:', error)
    ElMessage.error('配置解析失败')
  }
  break
```

### 4. 添加表单弹窗对话框

在模板中添加了表单弹窗对话框：

```vue
<!-- 表单弹窗对话框 -->
<el-dialog v-model="formPopupVisible" :title="formPopupTitle" :width="formPopupWidth">
  <div v-if="formPopupFields.length > 0 || formPopupTabs.length > 0" class="form-popup-container">
    <FormRenderer
      :fields="formPopupFields"
      :tabs="formPopupTabs"
      v-model="formPopupData"
      label-width="120px"
      :readonly="formPopupReadOnly"
    />
  </div>
  <el-empty v-else description="无表单数据" />
  <template #footer>
    <el-button @click="formPopupVisible = false">{{ t('common.cancel') }}</el-button>
    <el-button v-if="!formPopupReadOnly" type="primary" @click="submitFormPopup" :loading="submitting">
      {{ t('common.submit') }}
    </el-button>
  </template>
</el-dialog>
```

### 5. 添加样式

```scss
.form-popup-container {
  width: 100%;
  max-height: 60vh;
  overflow-y: auto;
}
```

## 配置格式

FORM_POPUP action 的 configJson 格式：

```json
{
  "formId": 22,
  "formName": "Credit Check Form",
  "popupTitle": "信用局检查",
  "popupWidth": "800px",
  "readOnly": false,
  "allowedRoles": ["CREDIT_OFFICER", "CREDIT_MANAGER"],
  "requireComment": false,
  "successMessage": "信用检查已完成"
}
```

### 配置字段说明

- `formId`: 表单 ID（必填），用于从数据库查找表单定义
- `formName`: 表单名称（可选），用于匹配表单
- `popupTitle`: 弹窗标题（可选），默认使用 action 名称
- `popupWidth`: 弹窗宽度（可选），默认 800px
- `readOnly`: 是否只读（可选），默认 false
- `allowedRoles`: 允许的角色列表（可选）
- `requireComment`: 是否需要备注（可选）
- `successMessage`: 成功提示消息（可选）

## 数据流

```
用户点击 FORM_POPUP 按钮
  ↓
handleCustomAction(action)
  ↓
openFormPopup(action, config)
  ↓
调用 processApi.getFunctionUnitContents(functionUnitId, 'FORM')
  ↓
查找匹配的表单（通过 sourceId 或 contentName）
  ↓
parseFormPopupConfig(formConfig)
  ↓
设置 formPopupFields / formPopupTabs
  ↓
formPopupVisible = true
  ↓
显示表单弹窗 ✅
```

## 部署步骤

### 1. 构建前端

```powershell
cd frontend/user-portal
npx vite build
```

### 2. 复制到容器

```powershell
docker cp frontend/user-portal/dist/. platform-user-portal-frontend-dev:/usr/share/nginx/html/
```

### 3. 测试

1. 访问 http://localhost:3001
2. 清除缓存 (Ctrl+F5)
3. 登录并查看任务
4. 点击 "Perform Credit Check" 按钮
5. 应该看到信用检查表单弹窗

## 支持的 Action 类型

### FORM_POPUP
- 打开表单弹窗
- 支持只读和可编辑模式
- 支持 Tab 布局

### APPROVE
- 打开审批对话框
- 可以添加备注

### REJECT
- 打开拒绝对话框
- 可以添加拒绝原因

### 其他类型
- API_CALL: 调用 API（待实现）
- PROCESS_SUBMIT: 提交流程（待实现）
- WITHDRAW: 撤回（待实现）

## 相关文件

### 前端代码
- `frontend/user-portal/src/views/tasks/detail.vue` - 任务详情页面
- `frontend/user-portal/src/components/FormRenderer.vue` - 表单渲染组件

### API
- `frontend/user-portal/src/api/process.ts` - 流程 API
- `frontend/user-portal/src/api/task.ts` - 任务 API

## 后续改进

### 1. 实现表单提交逻辑
当前 `submitFormPopup` 只是显示成功消息，需要实现：
- 根据 action 类型调用不同的 API
- 更新流程变量
- 触发流程流转

### 2. 支持表单验证
- 必填字段验证
- 格式验证
- 自定义验证规则

### 3. 支持权限控制
- 根据 `allowedRoles` 控制按钮显示
- 根据用户角色控制表单字段的可见性和可编辑性

### 4. 支持其他 Action 类型
- API_CALL: 调用后端 API
- PROCESS_SUBMIT: 提交流程
- WITHDRAW: 撤回申请

## 测试清单

- [ ] 点击 FORM_POPUP 按钮打开弹窗
- [ ] 弹窗显示正确的表单字段
- [ ] 表单字段可以正常输入
- [ ] 只读模式下字段不可编辑
- [ ] 提交按钮在只读模式下隐藏
- [ ] 取消按钮关闭弹窗
- [ ] 提交按钮触发提交逻辑
- [ ] 表单支持 Tab 布局
- [ ] 表单支持平铺布局

---

**状态**: 🟢 基本功能已实现  
**实现时间**: 2026-02-08 09:10
