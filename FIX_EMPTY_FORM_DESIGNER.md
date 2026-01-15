# 修复表单设计器显示为空的问题

## 问题描述

打开 "Purchase Request Main Form" 表单时，表单设计器显示为空，无法看到表单内容。

## 问题原因

1. **数据库中的 `config_json` 为空**：表单的 `config_json` 字段值为 `{}`，没有 `rule` 和 `options` 配置
2. **加载逻辑不完善**：`handleSelectForm` 函数在 `configJson` 为空或没有 `rule` 时，没有正确初始化设计器
3. **组件加载时序问题**：设计器组件可能还没有完全初始化就尝试加载配置

## 修复方案

### 1. 改进表单加载逻辑

修改 `handleSelectForm` 函数，确保：
- 即使 `configJson` 为空，也能正确初始化设计器
- 使用 `setTimeout` 确保设计器组件完全初始化后再加载配置
- 添加错误处理，防止加载失败导致设计器无法使用

```typescript
function handleSelectForm(row: FormDefinition) {
  selectedForm.value = { ...row }
  // 等待设计器组件完全渲染后再加载配置
  nextTick(() => {
    // 使用 setTimeout 确保设计器组件完全初始化
    setTimeout(() => {
      if (designerRef.value) {
        try {
          // 加载已保存的表单配置到设计器
          const config = row.configJson || {}
          
          // 如果配置中有规则，则加载；否则初始化为空数组
          if (config.rule && Array.isArray(config.rule) && config.rule.length > 0) {
            designerRef.value.setRule(config.rule)
          } else {
            // 初始化空表单
            designerRef.value.setRule([])
          }
          
          // 如果配置中有选项，则加载；否则使用默认选项
          if (config.options && Object.keys(config.options).length > 0) {
            designerRef.value.setOption(config.options)
          } else {
            // 使用默认选项
            designerRef.value.setOption({})
          }
        } catch (error) {
          console.error('Failed to load form config:', error)
          // 如果加载失败，至少初始化一个空表单
          try {
            designerRef.value.setRule([])
            designerRef.value.setOption({})
          } catch (e) {
            console.error('Failed to initialize empty form:', e)
          }
        }
      }
    }, 100)
  })
}
```

## 验证

修复后，应该能够：
1. 打开空表单时，显示一个空的设计器界面（可以开始设计表单）
2. 打开有配置的表单时，正确加载并显示表单内容
3. 如果加载失败，至少显示一个空的设计器，而不是完全空白

## 下一步

如果表单是空的，可以：
1. 点击"导入表字段"按钮，从绑定的数据表导入字段
2. 手动在设计器中拖拽添加表单组件
3. 设计完成后，点击"保存"按钮保存表单配置

## 相关文件

- `/src/components/designer/FormDesigner.vue` - 表单设计器组件
