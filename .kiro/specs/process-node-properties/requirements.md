# Requirements Document

## Introduction

增强流程设计器的节点属性面板功能，提供更完整的 BPMN 节点配置能力，包括用户任务的处理人配置、表单绑定、超时设置、服务任务的调用配置等。当前使用的是 bpmn-js 的默认属性面板，功能较为基础，需要扩展自定义属性配置以满足业务流程设计需求。

## Glossary

- **Process_Designer**: 流程设计器组件，基于 bpmn-js 实现
- **Properties_Panel**: 节点属性面板，用于配置选中节点的属性
- **User_Task**: 用户任务节点，需要人工处理的任务
- **Service_Task**: 服务任务节点，自动执行的系统任务
- **Assignee**: 任务处理人，可以是具体用户或角色
- **Form_Binding**: 表单绑定，将表单与流程节点关联
- **Extension_Elements**: BPMN 扩展元素，用于存储自定义属性

## Requirements

### Requirement 1: 自定义属性面板框架

**User Story:** As a 流程设计人员, I want 使用自定义的属性面板替代默认面板, so that 可以配置更丰富的节点属性。

#### Acceptance Criteria

1. WHEN 用户选中流程节点 THEN Properties_Panel SHALL 显示该节点类型对应的属性配置表单
2. WHEN 用户修改属性值 THEN Properties_Panel SHALL 实时更新 BPMN XML 中的对应属性
3. WHEN 用户取消选中节点 THEN Properties_Panel SHALL 显示流程级别的属性配置
4. THE Properties_Panel SHALL 支持折叠/展开属性分组

### Requirement 2: 用户任务节点属性配置

**User Story:** As a 流程设计人员, I want 配置用户任务的处理人和表单, so that 流程执行时能正确分配任务和显示表单。

#### Acceptance Criteria

1. WHEN 选中 User_Task 节点 THEN Properties_Panel SHALL 显示基本信息配置（名称、描述）
2. WHEN 选中 User_Task 节点 THEN Properties_Panel SHALL 显示处理人配置（指定用户、指定角色、表达式）
3. WHEN 选中 User_Task 节点 THEN Properties_Panel SHALL 显示表单绑定配置（从已创建的表单列表中选择）
4. WHEN 选中 User_Task 节点 THEN Properties_Panel SHALL 显示超时配置（超时时间、超时动作）
5. WHEN 选中 User_Task 节点 THEN Properties_Panel SHALL 显示多实例配置（并行/串行、完成条件）
6. WHEN 配置处理人为角色 THEN Properties_Panel SHALL 支持从系统角色列表中选择

### Requirement 3: 服务任务节点属性配置

**User Story:** As a 流程设计人员, I want 配置服务任务的执行方式, so that 流程能自动调用相应的服务。

#### Acceptance Criteria

1. WHEN 选中 Service_Task 节点 THEN Properties_Panel SHALL 显示基本信息配置（名称、描述）
2. WHEN 选中 Service_Task 节点 THEN Properties_Panel SHALL 显示服务类型选择（HTTP调用、脚本执行、消息发送）
3. WHEN 服务类型为 HTTP 调用 THEN Properties_Panel SHALL 显示 URL、方法、请求头、请求体配置
4. WHEN 服务类型为脚本执行 THEN Properties_Panel SHALL 显示脚本语言和脚本内容配置
5. WHEN 选中 Service_Task 节点 THEN Properties_Panel SHALL 显示重试配置（重试次数、重试间隔）

### Requirement 4: 网关节点属性配置

**User Story:** As a 流程设计人员, I want 配置网关的分支条件, so that 流程能根据条件正确路由。

#### Acceptance Criteria

1. WHEN 选中排他网关节点 THEN Properties_Panel SHALL 显示默认分支配置
2. WHEN 选中并行网关节点 THEN Properties_Panel SHALL 显示基本信息配置
3. WHEN 选中连接线且源节点为网关 THEN Properties_Panel SHALL 显示条件表达式配置
4. THE Properties_Panel SHALL 支持条件表达式的可视化编辑

### Requirement 5: 事件节点属性配置

**User Story:** As a 流程设计人员, I want 配置事件节点的触发条件, so that 流程能正确响应事件。

#### Acceptance Criteria

1. WHEN 选中开始事件节点 THEN Properties_Panel SHALL 显示流程启动表单配置
2. WHEN 选中结束事件节点 THEN Properties_Panel SHALL 显示流程结束动作配置
3. WHEN 选中定时事件节点 THEN Properties_Panel SHALL 显示定时表达式配置（cron 或 duration）
4. WHEN 选中消息事件节点 THEN Properties_Panel SHALL 显示消息名称和关联配置

### Requirement 6: 属性持久化

**User Story:** As a 流程设计人员, I want 配置的属性能正确保存到 BPMN XML, so that 流程引擎能读取这些配置。

#### Acceptance Criteria

1. WHEN 用户保存流程 THEN 系统 SHALL 将自定义属性保存到 BPMN XML 的 Extension_Elements 中
2. WHEN 用户重新打开流程 THEN Properties_Panel SHALL 正确读取并显示已保存的属性值
3. THE 系统 SHALL 使用标准的 BPMN 扩展元素格式存储自定义属性
