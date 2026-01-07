# Implementation Tasks

## Task 1: 创建 BPMN Extension Elements 工具函数 ✅

### Description
创建用于读写 BPMN Extension Elements 的工具函数，这是所有属性配置的基础。

### Files to Create/Modify
- `frontend/developer-workstation/src/utils/bpmnExtensions.ts` (已创建)
- `frontend/developer-workstation/src/types/bpmn.d.ts` (已创建)

### Acceptance Criteria
- [x] 实现 `getExtensionProperties(element)` 函数读取扩展属性
- [x] 实现 `setExtensionProperty(modeler, element, name, value)` 函数设置扩展属性
- [x] 实现 `removeExtensionProperty(modeler, element, name)` 函数删除扩展属性
- [x] 支持复杂类型值的序列化和反序列化 (JSON)
- [x] 创建 TypeScript 类型定义

### Tests Required
- [x] 属性读写的正确性测试
- [x] 复杂值序列化/反序列化测试
- [x] 空值和边界情况处理测试

---

## Task 2: 创建自定义属性面板框架组件 ✅

### Description
创建 NodePropertiesPanel.vue 作为属性面板的容器组件，负责监听节点选择事件并动态渲染对应的属性组件。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/NodePropertiesPanel.vue` (已创建)

### Acceptance Criteria
- [x] 监听 bpmn-js 的 selection.changed 事件
- [x] 根据选中元素类型动态渲染对应属性组件
- [x] 无选中元素时显示流程级属性
- [x] 支持属性分组的折叠/展开
- [x] 样式与系统主题一致

### Tests Required
- [x] 节点选择切换时组件渲染测试
- [x] 多选和取消选择的处理测试

---

## Task 3: 实现流程级属性组件 ✅

### Description
创建 ProcessProperties.vue 组件，用于配置流程级别的属性。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/ProcessProperties.vue` (已创建)

### Acceptance Criteria
- [x] 显示流程ID和名称配置
- [x] 显示流程描述配置
- [x] 显示流程是否可执行配置
- [x] 属性修改实时更新到 BPMN XML

### Tests Required
- [x] 流程属性读取和更新测试

---

## Task 4: 实现用户任务属性组件 ✅

### Description
创建 UserTaskProperties.vue 组件，提供用户任务的完整属性配置。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/UserTaskProperties.vue` (已创建)

### Acceptance Criteria
- [x] 基本信息配置 (名称、描述)
- [x] 处理人配置 (指定用户/角色/表达式)
- [x] 表单绑定配置 (从表单列表选择)
- [x] 超时配置 (时间、动作)
- [x] 多实例配置 (并行/串行、完成条件)
- [x] 所有配置保存到 Extension Elements

### Tests Required
- [x] 各属性分组的渲染测试
- [x] 表单列表加载和选择测试
- [x] 属性保存和恢复测试

---

## Task 5: 实现服务任务属性组件 ✅

### Description
创建 ServiceTaskProperties.vue 组件，提供服务任务的配置。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/ServiceTaskProperties.vue` (已创建)

### Acceptance Criteria
- [x] 基本信息配置
- [x] 服务类型选择 (HTTP/脚本/消息)
- [x] HTTP 调用配置 (URL、方法、请求头、请求体)
- [x] 脚本执行配置 (语言、内容)
- [x] 重试配置 (次数、间隔)

### Tests Required
- [x] 服务类型切换时表单变化测试
- [x] HTTP 配置的完整性测试

---

## Task 6: 实现网关和连接线属性组件 ✅

### Description
创建网关和连接线的属性配置组件。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/GatewayProperties.vue` (已创建)
- `frontend/developer-workstation/src/components/designer/properties/SequenceFlowProperties.vue` (已创建)

### Acceptance Criteria
- [x] 排他网关默认分支配置
- [x] 连接线条件表达式配置
- [x] 条件表达式类型选择 (JUEL/脚本)
- [x] 并行网关基本信息配置

### Tests Required
- [x] 条件表达式保存和读取测试
- [x] 默认分支设置测试

---

## Task 7: 实现事件属性组件 ✅

### Description
创建事件节点的属性配置组件。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/properties/EventProperties.vue` (已创建)

### Acceptance Criteria
- [x] 开始事件启动表单配置
- [x] 结束事件结束动作配置
- [x] 定时事件表达式配置
- [x] 消息事件名称和关联配置

### Tests Required
- [x] 不同事件类型的属性渲染测试
- [x] 定时表达式格式验证测试

---

## Task 8: 集成自定义属性面板到 ProcessDesigner ✅

### Description
将自定义属性面板集成到 ProcessDesigner.vue，替换默认的 bpmn-js-properties-panel。

### Files to Create/Modify
- `frontend/developer-workstation/src/components/designer/ProcessDesigner.vue` (已修改)

### Acceptance Criteria
- [x] 移除默认属性面板的引用
- [x] 集成 NodePropertiesPanel 组件
- [x] 传递 modeler 实例和 functionUnitId
- [x] 保存时正确序列化所有扩展属性
- [x] 加载时正确恢复所有扩展属性

### Tests Required
- [x] 完整流程设计和保存测试
- [x] 流程重新加载后属性恢复测试

---

## Task 9: 属性面板样式优化 ✅

### Description
优化属性面板的视觉效果，确保与系统整体风格一致。

### Files to Create/Modify
- 各属性组件的样式部分 (已完成)

### Acceptance Criteria
- [x] 统一的分组标题样式
- [x] 紧凑的表单布局
- [x] 响应式宽度适配
- [x] 主题色一致性 (#DB0011)
- [x] 折叠动画流畅

### Tests Required
- [x] 视觉回归测试 (手动)

---

## Task 10: 编写属性测试 (Property-Based Testing) ✅

### Description
使用 Vitest + fast-check 编写属性测试，确保工具函数和组件的健壮性。

### Files to Create/Modify
- `frontend/developer-workstation/src/utils/__tests__/bpmnExtensions.property.test.ts` (已创建)

### Acceptance Criteria
- [x] Extension Elements 工具函数的属性测试
- [x] 属性值序列化往返测试
- [x] 边界值和特殊字符处理测试

### Tests Required
- [x] 所有属性测试通过
