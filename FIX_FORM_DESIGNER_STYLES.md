# 修复表单设计器样式问题

## 问题描述

在表单设计中，点开具体表单时样式是空的，但是能够查看具体的 HTML 代码。这说明 HTML 结构存在，但 CSS 样式没有正确加载或应用。

## 问题原因

1. **缺少 form-create 样式文件导入**：`@form-create/designer` 和 `@form-create/element-ui` 的样式文件没有在 `main.ts` 中导入
2. **样式作用域问题**：预览对话框和设计器中的样式可能因为 Vue 的 scoped 样式导致 form-create 的样式无法正确应用

## 修复方案

### 1. 导入 form-create 样式文件

在 `src/main.ts` 中添加样式导入：

```typescript
// 导入 form-create 样式
import '@form-create/designer/dist/index.css'
import '@form-create/element-ui/dist/index.css'
```

### 2. 修复预览对话框样式

在 `FormDesigner.vue` 的预览对话框中添加包装器和样式：

```vue
<div class="preview-container">
  <div class="form-preview-wrapper">
    <form-create v-if="previewRule.length" v-model="previewData" :rule="previewRule" :option="previewOption" />
    <el-empty v-else description="暂无表单内容" />
  </div>
</div>
```

添加样式：

```scss
.preview-container {
  min-height: 300px;
  padding: 20px;
  
  .form-preview-wrapper {
    // 确保 form-create 样式能够正确应用
    :deep(.form-create) {
      width: 100%;
    }
    
    // 确保表单项样式正确
    :deep(.el-form-item) {
      margin-bottom: 18px;
    }
    
    // 确保输入框等组件样式正确
    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-picker),
    :deep(.el-textarea) {
      width: 100%;
    }
    
    // 确保按钮样式正确
    :deep(.el-button) {
      margin-right: 10px;
    }
  }
}
```

### 3. 修复表单设计器样式

在 `.fc-designer-wrapper` 中添加样式：

```scss
.fc-designer-wrapper {
  flex: 1;
  overflow: hidden;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  
  :deep(.fc-designer) {
    height: 100% !important;
  }
  
  // 确保 form-create 设计器内的样式正确应用
  :deep(.form-create) {
    width: 100%;
  }
  
  // 确保设计器内的表单项样式正确
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }
  
  // 确保设计器内的输入框等组件样式正确
  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-picker),
  :deep(.el-textarea) {
    width: 100%;
  }
}
```

## 验证

修复后，应该能够：
1. 在表单设计器中正常看到表单的样式
2. 在预览对话框中正常看到表单的样式
3. 所有表单组件（输入框、选择器、日期选择器等）都能正确显示样式

## 注意事项

1. **样式作用域**：使用 `:deep()` 确保样式能够穿透 Vue 的 scoped 样式限制
2. **样式优先级**：如果样式仍然不生效，可能需要使用 `!important` 提高优先级
3. **浏览器缓存**：修复后需要清除浏览器缓存或硬刷新（Ctrl+Shift+R 或 Cmd+Shift+R）

## 相关文件

- `/src/main.ts` - 导入 form-create 样式
- `/src/components/designer/FormDesigner.vue` - 表单设计器组件
