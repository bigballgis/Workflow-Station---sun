# Design Document

## Introduction

本设计文档描述流程设计器节点属性面板增强功能的技术架构和实现方案。目标是替换 bpmn-js 默认的属性面板，提供更丰富的节点配置能力，支持用户任务、服务任务、网关和事件节点的自定义属性配置。

## Architecture Overview

### 组件架构

```
ProcessDesigner.vue
├── BpmnModeler (bpmn-js)
│   └── Canvas (流程画布)
└── NodePropertiesPanel.vue (自定义属性面板)
    ├── ProcessProperties.vue (流程级属性)
    ├── UserTaskProperties.vue (用户任务属性)
    ├── ServiceTaskProperties.vue (服务任务属性)
    ├── GatewayProperties.vue (网关属性)
    ├── EventProperties.vue (事件属性)
    └── SequenceFlowProperties.vue (连接线属性)
```

### 数据流

```
用户选中节点 → bpmn-js selection.changed 事件
    ↓
NodePropertiesPanel 接收选中元素
    ↓
根据元素类型渲染对应属性组件
    ↓
用户修改属性 → 更新 BPMN XML Extension Elements
    ↓
保存时将完整 XML 发送到后端
```

## Design Details

### 1. 自定义属性面板框架 (NodePropertiesPanel.vue)

#### 组件结构
- 监听 bpmn-js 的 `selection.changed` 事件获取选中元素
- 根据元素类型 (`bpmn:UserTask`, `bpmn:ServiceTask` 等) 动态渲染对应属性组件
- 使用 Element Plus 的 Collapse 组件实现属性分组折叠

#### 属性存储方案
使用 BPMN 2.0 标准的 Extension Elements 存储自定义属性：

```xml
<bpmn:userTask id="Task_1" name="审批任务">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="assigneeType" value="role" />
      <custom:property name="assigneeValue" value="manager" />
      <custom:property name="formId" value="123" />
      <custom:property name="timeout" value="PT24H" />
    </custom:properties>
  </bpmn:extensionElements>
</bpmn:userTask>
```

#### 核心接口

```typescript
interface NodePropertiesPanelProps {
  modeler: BpmnModeler
  functionUnitId: number
}

interface ExtensionProperty {
  name: string
  value: string | number | boolean | object
}
```

### 2. 用户任务属性组件 (UserTaskProperties.vue)

#### 属性分组

| 分组 | 属性 | 类型 | 说明 |
|------|------|------|------|
| 基本信息 | name | string | 任务名称 |
| | description | string | 任务描述 |
| 处理人配置 | assigneeType | enum | 指定用户/指定角色/表达式 |
| | assigneeValue | string | 处理人值 |
| | candidateUsers | string[] | 候选用户列表 |
| | candidateGroups | string[] | 候选角色列表 |
| 表单绑定 | formId | number | 绑定的表单ID |
| | formName | string | 表单名称(只读) |
| 超时配置 | timeoutEnabled | boolean | 是否启用超时 |
| | timeoutDuration | string | 超时时间(ISO 8601) |
| | timeoutAction | enum | 超时动作(提醒/自动通过/自动拒绝) |
| 多实例 | multiInstance | boolean | 是否多实例 |
| | sequential | boolean | 串行/并行 |
| | collection | string | 集合变量 |
| | completionCondition | string | 完成条件 |

### 3. 服务任务属性组件 (ServiceTaskProperties.vue)

#### 属性分组

| 分组 | 属性 | 类型 | 说明 |
|------|------|------|------|
| 基本信息 | name | string | 任务名称 |
| | description | string | 任务描述 |
| 服务类型 | serviceType | enum | HTTP/脚本/消息 |
| HTTP配置 | httpUrl | string | 请求URL |
| | httpMethod | enum | GET/POST/PUT/DELETE |
| | httpHeaders | object | 请求头 |
| | httpBody | string | 请求体模板 |
| | httpResponseVar | string | 响应存储变量 |
| 脚本配置 | scriptLanguage | enum | JavaScript/Groovy |
| | scriptContent | string | 脚本内容 |
| 重试配置 | retryEnabled | boolean | 是否启用重试 |
| | retryCount | number | 重试次数 |
| | retryInterval | string | 重试间隔 |

### 4. 网关属性组件 (GatewayProperties.vue)

#### 排他网关属性
- defaultFlow: 默认分支ID
- 条件表达式在 SequenceFlowProperties 中配置

#### 并行网关属性
- 仅基本信息配置

### 5. 连接线属性组件 (SequenceFlowProperties.vue)

| 属性 | 类型 | 说明 |
|------|------|------|
| name | string | 连接线名称 |
| conditionExpression | string | 条件表达式 |
| conditionType | enum | 表达式类型(JUEL/脚本) |

### 6. 事件属性组件 (EventProperties.vue)

#### 开始事件
- startFormId: 启动表单ID
- initiator: 发起人变量名

#### 结束事件
- endAction: 结束动作(无/发送通知/调用服务)

#### 定时事件
- timerType: 定时类型(日期/持续时间/周期)
- timerValue: 定时表达式

#### 消息事件
- messageName: 消息名称
- correlationKey: 关联键

## Extension Elements 工具函数

```typescript
// utils/bpmnExtensions.ts

export function getExtensionProperties(element: any): Record<string, any> {
  const extensionElements = element.businessObject?.extensionElements
  if (!extensionElements) return {}
  
  const properties = extensionElements.values?.find(
    (ext: any) => ext.$type === 'custom:properties'
  )
  if (!properties) return {}
  
  return properties.values?.reduce((acc: any, prop: any) => {
    acc[prop.name] = parsePropertyValue(prop.value)
    return acc
  }, {}) || {}
}

export function setExtensionProperty(
  modeler: any,
  element: any,
  name: string,
  value: any
): void {
  const modeling = modeler.get('modeling')
  const moddle = modeler.get('moddle')
  const bpmnFactory = modeler.get('bpmnFactory')
  
  // 获取或创建 extensionElements
  let extensionElements = element.businessObject.extensionElements
  if (!extensionElements) {
    extensionElements = moddle.create('bpmn:ExtensionElements', { values: [] })
    modeling.updateProperties(element, { extensionElements })
  }
  
  // 获取或创建 custom:properties
  let properties = extensionElements.values?.find(
    (ext: any) => ext.$type === 'custom:properties'
  )
  if (!properties) {
    properties = moddle.create('custom:properties', { values: [] })
    extensionElements.values.push(properties)
  }
  
  // 更新或添加属性
  const existingProp = properties.values?.find((p: any) => p.name === name)
  if (existingProp) {
    existingProp.value = stringifyPropertyValue(value)
  } else {
    const newProp = moddle.create('custom:property', {
      name,
      value: stringifyPropertyValue(value)
    })
    properties.values.push(newProp)
  }
}
```

## 表单绑定集成

### 从 FormDesigner 获取表单列表

```typescript
// 在 UserTaskProperties.vue 中
const forms = ref<FormDefinition[]>([])

async function loadForms() {
  const res = await functionUnitApi.getForms(props.functionUnitId)
  forms.value = res.data
}

// 表单选择器
<el-select v-model="formId" placeholder="选择表单">
  <el-option 
    v-for="form in forms" 
    :key="form.id" 
    :label="form.formName" 
    :value="form.id" 
  />
</el-select>
```

## 样式设计

### 属性面板样式
- 宽度: 320px (比默认面板稍宽)
- 背景: #fff
- 分组标题: 可折叠，带图标
- 表单项: 紧凑布局，label-position="top"
- 主题色: #DB0011 (与系统一致)

## Testing Strategy

### 单元测试 (Vitest + fast-check)
1. Extension Elements 工具函数的属性测试
2. 属性值序列化/反序列化的正确性
3. 各属性组件的渲染和交互测试

### 集成测试
1. 选中不同类型节点时属性面板的切换
2. 属性修改后 BPMN XML 的正确更新
3. 保存后重新加载属性的正确恢复

## Dependencies

### 新增依赖
无需新增依赖，使用现有的:
- bpmn-js (已安装)
- Element Plus (已安装)
- Vue 3 Composition API

### 移除依赖
- bpmn-js-properties-panel (可选，保留作为备用)
- @bpmn-io/properties-panel (可选)

## File Structure

```
frontend/developer-workstation/src/
├── components/
│   └── designer/
│       ├── ProcessDesigner.vue (修改)
│       └── properties/
│           ├── NodePropertiesPanel.vue (新增)
│           ├── ProcessProperties.vue (新增)
│           ├── UserTaskProperties.vue (新增)
│           ├── ServiceTaskProperties.vue (新增)
│           ├── GatewayProperties.vue (新增)
│           ├── EventProperties.vue (新增)
│           └── SequenceFlowProperties.vue (新增)
├── utils/
│   └── bpmnExtensions.ts (新增)
└── types/
    └── bpmn.d.ts (新增，类型定义)
```
