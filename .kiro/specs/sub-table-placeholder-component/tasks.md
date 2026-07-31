# 实现计划：Sub Table 占位符组件增强

## 概述

按照设计文档，依次实现 `SubTablePlaceholderWidget.vue`、`SubTableBindingSelect.vue`，再修改 `FormDesigner.vue` 完成注册、配置面板、重复绑定检测、跳转导航和保存验证，最后补充属性测试。

## 任务

- [x] 1. 新增 SubTablePlaceholderWidget.vue
  - [x] 1.1 创建组件文件，定义 Props（`bindingId`、`subBindings`）和 Emits（`navigate`）
    - 参考设计文档"组件与接口 §1"中的 Props/Emits 接口定义
    - _需求：1.1, 1.2, 1.3_
  - [x] 1.2 实现 `state` 和 `displayName` 计算属性，覆盖 `unconfigured / valid / stale` 三种状态
    - `state` 逻辑：`bindingId` 为 null/undefined → `unconfigured`；在列表中找到 → `valid`；否则 → `stale`
    - `displayName` 逻辑：`valid` 时拼接 `tableName（tableDescription）`，无描述时仅返回 `tableName`
    - _需求：1.1, 1.2, 5.2, 5.3_
  - [x] 1.3 实现模板：图标 + 状态文字 + 跳转按钮（仅 `valid` 时显示），绑定 `:class="[is-${state}]"`
    - 跳转按钮点击时 `emit('navigate', bindingId!)`
    - _需求：1.3, 3.1, 3.3_
  - [x] 1.4 添加三态样式（`unconfigured` 灰色、`valid` 蓝色、`stale` 橙色），参考设计文档样式规范表格
    - _需求：1.3, 5.3_
  - [x] 1.5 为 Property 1（displayName 反映 subBindings）编写属性测试
    - **Property 1：displayName 反映当前 subBindings 列表**
    - **Validates: Requirements 1.1, 1.4**
    - 测试文件：`__tests__/SubTablePlaceholderWidget.property.test.ts`
  - [x] 1.6 为 Property 2（状态计算三分支）编写属性测试
    - **Property 2：占位符状态计算覆盖三种分支**
    - **Validates: Requirements 1.2, 5.2, 5.3**
    - 测试文件：`__tests__/SubTablePlaceholderWidget.property.test.ts`
  - [x] 1.7 为 Property 4（跳转按钮可见性与 state 一致）编写属性测试
    - **Property 4：跳转按钮可见性与 state 一致**
    - **Validates: Requirements 3.1, 3.3**
    - 测试文件：`__tests__/SubTablePlaceholderWidget.property.test.ts`

- [x] 2. 新增 SubTableBindingSelect.vue
  - [x] 2.1 创建组件文件，定义 Props（`modelValue`、`subBindings`）和 Emits（`update:modelValue`）
    - 参考设计文档"组件与接口 §2"
    - _需求：2.1, 2.2_
  - [x] 2.2 实现模板：`el-select` + `el-option` 列表，`clearable`，空状态插槽显示"暂无可用 Sub Table"
    - 选项标签使用 `formatBindingLabel` 格式（`tableName（tableDescription）`）
    - 清除时 `emit('update:modelValue', null)`
    - _需求：2.2, 2.4, 2.6_
  - [x] 2.3 为 Property 7（绑定标签格式化）编写属性测试
    - **Property 7：绑定标签格式化**
    - **Validates: Requirements 2.2**
    - 测试文件：`__tests__/SubTablePlaceholderWidget.property.test.ts`
  - [x] 2.4 为 Property 3（绑定选择 round-trip）编写属性测试
    - **Property 3：绑定选择的 round-trip**
    - **Validates: Requirements 2.3, 2.4**
    - 测试文件：`__tests__/FormDesigner.subTablePlaceholder.property.test.ts`

- [x] 3. 检查点 — 确保新增组件逻辑正确
  - 确保所有已写属性测试通过，向用户确认是否有疑问。

- [x] 4. 修改 FormDesigner.vue — 注册与配置面板
  - [x] 4.1 导入 `SubTablePlaceholderWidget` 和 `SubTableBindingSelect`，在 `designerConfig.componentMap` 中注册 `subTable` 类型
    - 参考设计文档"组件与接口 §3a"
    - _需求：4.1, 4.2_
  - [x] 4.2 在配置面板的 `subTable` 条目中，将 `_bindingId` 配置项改为使用 `SubTableBindingSelect`，并动态注入 `designerSubBindings`
    - 参考设计文档"组件与接口 §3b"
    - _需求：2.1, 2.3_
  - [x] 4.3 为 Property 5（拖拽插入结构与多实例独立）编写属性测试
    - **Property 5：拖拽插入产生正确结构且多实例独立**
    - **Validates: Requirements 4.2, 4.3**
    - 测试文件：`__tests__/FormDesigner.subTablePlaceholder.property.test.ts`
  - [x] 4.4 为 Property 6（_bindingId 序列化与加载 round-trip）编写属性测试
    - **Property 6：_bindingId 序列化与加载的 round-trip**
    - **Validates: Requirements 4.4, 6.3**
    - 测试文件：`__tests__/FormDesigner.subTablePlaceholder.property.test.ts`

- [x] 5. 修改 FormDesigner.vue — 重复绑定检测与跳转导航
  - [x] 5.1 实现 `checkDuplicateBinding` 函数，在配置面板选择绑定时调用，检测到重复时显示警告提示
    - 参考设计文档"组件与接口 §3c"
    - _需求：5.1_
  - [x] 5.2 实现 `handleSubTableNavigate` 函数，监听 `SubTablePlaceholderWidget` 的 `navigate` 事件，调用 `router.push` 跳转到 `SubTableFormDesigner` 路由
    - 在当前窗口内导航，不使用 `window.open`；加 guard：`if (!bindingId) return`
    - _需求：3.2, 3.4_
  - [x] 5.3 为 Property 9（重复绑定检测）编写属性测试
    - **Property 9：重复绑定检测**
    - **Validates: Requirements 5.1**
    - 测试文件：`__tests__/FormDesigner.subTablePlaceholder.property.test.ts`

- [x] 6. 修改 FormDesigner.vue — 保存验证
  - [x] 6.1 在 `handleSaveForm` 中补充验证逻辑：扫描 rule 中 `_bindingId` 为 null 的 `subTable` 条目，有则调用 `ElMessage.error` 并阻止保存
    - 参考设计文档"组件与接口 §3e"
    - _需求：2.5, 4.4_
  - [x] 6.2 为 Property 8（保存验证阻止未绑定占位符）编写属性测试
    - **Property 8：保存验证阻止未绑定占位符**
    - **Validates: Requirements 2.5**
    - 测试文件：`__tests__/FormDesigner.subTablePlaceholder.property.test.ts`

- [x] 7. 最终检查点 — 确保所有测试通过
  - 确保所有测试通过，向用户确认是否有疑问。

## 备注

- 标有 `*` 的子任务为可选项，可跳过以加快 MVP 交付
- 每个任务均引用具体需求条款以保证可追溯性
- 属性测试使用 `fast-check`（developer-workstation 已安装），每个属性最少运行 100 次迭代
- 本功能不修改 `FormRenderer.vue` 及 `user-portal` 侧任何组件
- `config_json` 数据结构不引入新字段，与 `sub-table-position-control` 规范完全兼容
