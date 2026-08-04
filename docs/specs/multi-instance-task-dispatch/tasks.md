# 实现计划：BPMN 多实例子流程动态任务分发

## 概述

按依赖顺序实现多实例子流程功能：先建立数据库基础，再实现 developer-workstation 侧的 BPMN XML 生成与验证，然后实现 workflow-engine-core 侧的运行时组件（手动分配、数据注入、任务分配、数据回写、状态监控、级联取消），最后实现 user-portal 前端组件（子表 Assign 按钮、子任务表单、实时同步）。每个阶段包含对应的属性测试和单元测试。

## Tasks

- [x] 1. 数据库 Schema 变更
  - [x] 1.1 创建 wf_multi_instance_execution 表
    - 在 `deploy/init-scripts/00-schema/` 下新增 SQL 迁移脚本
    - 创建 `wf_multi_instance_execution` 表，包含 process_instance_id、activity_id、sub_table_name、execution_mode、total_instances、completed_instances、active_instances、cancelled_instances、status 等字段
    - 创建索引 idx_mi_exec_process_instance 和 idx_mi_exec_status
    - _需求: 7.1, 5.4_

  - [x] 1.2 修改动态子表建表逻辑，自动添加 row_version 列
    - 修改 DataTableManagerComponent 的建表逻辑，在创建 table_type=SUB 的表时自动添加 `row_version BIGINT NOT NULL DEFAULT 1` 列
    - 提供已有子表的迁移脚本 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 1`
    - _需求: 6.5, 6.6_

- [x] 2. developer-workstation：BPMN XML 多实例生成
  - [x] 2.1 实现 BpmnXmlGenerator 多实例子流程 XML 生成
    - 在 BpmnXmlGenerator 中增加多实例子流程节点生成逻辑
    - 生成 `<bpmn:subProcess>` + `<bpmn:multiInstanceLoopCharacteristics>` 结构
    - 生成 `flowable:collection`、`flowable:elementVariable` 属性
    - 生成子流程内部 StartEvent → UserTask → EndEvent 结构
    - UserTask 包含 assigneeType=ELEMENT_VARIABLE、subTableId、subTableName、assigneeField、rowIdVariable 扩展属性
    - 支持 isSequential=true/false（并行/顺序模式）
    - 支持可选的 completionCondition 生成
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 2.2 属性测试：BPMN XML 生成结构完整性
    - **Property 1: BPMN XML 生成结构完整性**
    - 随机生成合法的多实例子流程配置，验证输出 XML 包含所有必需元素和属性
    - **验证: 需求 1.1, 1.2, 1.4**

  - [x] 2.3 属性测试：执行模式映射正确性
    - **Property 2: 执行模式映射正确性**
    - 随机生成 PARALLEL/SEQUENTIAL 模式，验证 isSequential 属性值正确
    - **验证: 需求 1.3**

  - [x] 2.4 属性测试：完成条件条件性生成
    - **Property 3: 完成条件条件性生成**
    - 随机生成有/无 completionCondition 的配置，验证 XML 中条件元素的存在性
    - **验证: 需求 1.5**

  - [x] 2.5 属性测试：BPMN XML 往返一致性
    - **Property 15: BPMN XML 往返一致性**
    - 随机生成配置 → 序列化为 XML → 反序列化为配置对象 → 再序列化为 XML，验证语义等价
    - **验证: 需求 8.4**

- [x] 3. developer-workstation：多实例配置验证
  - [x] 3.1 实现 ProcessDesignComponent.validateMultiInstance() 方法
    - 在 ProcessDesignComponentImpl 中新增 validateMultiInstance 方法
    - 验证 collection 变量名格式合法（字母、数字、下划线）
    - 验证子流程内部至少包含一个 userTask
    - 验证 subTableId 属于当前 FunctionUnit 且 table_type=SUB
    - 验证 assigneeField 存在于子表的 FieldDefinition 列表中
    - 验证 formId（如配置）属于当前 FunctionUnit
    - _需求: 2.2, 2.3, 8.1, 8.2, 8.3_

  - [x] 3.2 属性测试：多实例配置验证正确性
    - **Property 4: 多实例配置验证正确性**
    - 随机生成 subTableId/functionUnitId/assigneeField 组合，验证通过条件的充要性
    - **验证: 需求 2.2, 2.3**

  - [x] 3.3 属性测试：部署验证正确性
    - **Property 14: 部署验证正确性**
    - 随机生成合法/非法变量名和 XML 结构，验证验证结果
    - **验证: 需求 8.1, 8.2**

  - [x] 3.4 扩展 DeploymentComponentImpl 部署验证
    - 在 executeDeployment() 的 Step 1 之前增加多实例配置验证步骤
    - 调用 processDesignComponent.validateMultiInstance() 进行验证
    - 验证失败时抛出 BusinessException("MULTI_INSTANCE_VALIDATION_FAILED")
    - _需求: 8.1, 8.2, 8.3_

  - [x] 3.5 单元测试：BPMN XML 验证边界条件
    - 测试缺少 collection 属性时返回验证错误
    - 测试缺少 elementVariable 属性时返回验证错误
    - 测试子流程内无 userTask 时返回验证错误
    - 测试 subTableId 不属于当前 FunctionUnit 时返回验证错误
    - _需求: 8.2, 8.3_

- [x] 4. Checkpoint - 确保 developer-workstation 侧所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. workflow-engine-core：SubTableAssignmentHandler 手动分配处理人
  - [x] 5.1 实现 SubTableAssignmentHandler 组件
    - 新建 SubTableAssignmentHandler Spring 组件
    - 实现 assign() 方法：验证任务存在且当前用户有权限
    - 从任务扩展属性或流程定义中获取子表配置（subTableName、assigneeField）
    - 验证 rowId 属于当前任务关联的主表记录
    - 验证 assigneeId 对应的用户存在且未禁用
    - 通过 JdbcTemplate 更新子表 assigneeField：`UPDATE {subTable} SET {assigneeField} = ? WHERE id = ?`
    - 返回 AssignmentResponse（success、rowId、assigneeId、assigneeName）
    - _需求: 新增功能 - 手动分配_

  - [x] 5.2 实现 TaskAssignmentController 子表行分配接口
    - 新建或扩展 TaskAssignmentController
    - 实现 POST /api/workflow/tasks/{taskId}/sub-table-rows/{rowId}/assign
    - 接收 AssignSubTableRowRequest（含 assigneeId）
    - 调用 SubTableAssignmentHandler.assign() 处理分配
    - 返回 AssignmentResponse
    - _需求: 新增功能 - 手动分配_

  - [x] 5.3 单元测试：SubTableAssignmentHandler 边界条件
    - 测试正常分配场景
    - 测试任务不存在时抛出异常
    - 测试 rowId 不属于当前任务时抛出异常
    - 测试用户不存在时抛出异常
    - _需求: 新增功能 - 手动分配_

- [x] 6. workflow-engine-core：SubTableDataInjector 子表数据注入
  - [x] 6.1 实现 SubTableDataInjector 组件
    - 新建 SubTableDataInjector Spring 组件
    - 通过 JdbcTemplate 查询子表数据：`SELECT id, {assigneeField}, row_version FROM {subTableName} WHERE {fk} = {mainRecordId}`
    - 验证数据行数 > 0，否则抛出 WorkflowValidationException
    - 验证所有行的 assigneeField 非空，否则抛出 WorkflowValidationException 并指明行号
    - 构建 List<Map<String, Object>> 集合变量（每个元素含 rowId、assigneeId、rowVersion）
    - 通过 runtimeService.setVariable() 注入集合变量，变量名格式 `multiInstance_{subTableName}_collection`
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 6.2 属性测试：子表数据注入正确性
    - **Property 5: 子表数据注入正确性**
    - 随机生成 N 条子表数据行（N>0，assigneeField 非空），验证集合变量包含恰好 N 个元素且结构正确
    - **验证: 需求 3.1, 3.2, 3.3**

  - [x] 6.3 单元测试：SubTableDataInjector 边界条件
    - 测试 3 行子表数据正确注入为集合变量
    - 测试子表数据为空时抛出 WorkflowValidationException
    - 测试 assigneeField 为空时抛出 WorkflowValidationException 并包含行号信息
    - 测试子表不存在时抛出 WorkflowBusinessException
    - _需求: 3.4, 3.5_

- [x] 7. workflow-engine-core：TaskAssignmentListener 扩展（ELEMENT_VARIABLE）
  - [x] 7.1 扩展 TaskAssignmentListener 支持 ELEMENT_VARIABLE 分配类型
    - 在 handleTaskCreated() 中增加 ELEMENT_VARIABLE 分支
    - 从 execution 变量中获取 currentItem（Map 类型）
    - 读取 assigneeId 并调用 taskService.setAssignee()
    - 创建 ExtendedTaskInfo 记录（assignment_type=USER，extended_properties 含 multiInstance=true、subTableRowId、subTableRowVersion、subTableId、subTableName）
    - 处理人不存在/已禁用时记录 WARN 日志，任务状态保持 CREATED
    - ExtendedTaskInfo 保存失败时记录 ERROR 日志，不影响 Flowable 任务创建
    - _需求: 4.2, 4.3, 4.4, 4.5_

  - [x] 7.2 属性测试：多实例子任务创建与分配正确性
    - **Property 6: 多实例子任务创建与分配正确性**
    - 随机生成 elementVariable 内容，验证 ExtendedTaskInfo 记录正确
    - **验证: 需求 4.2, 4.3, 4.4**

  - [x] 7.3 单元测试：TaskAssignmentListener ELEMENT_VARIABLE 处理
    - 测试正常分配场景
    - 测试 elementVariable 为 null 时的降级处理
    - 测试处理人 ID 无效时的降级处理
    - _需求: 4.2, 4.5_

- [x] 8. workflow-engine-core：MultiInstanceDataResolver 数据隔离与回写
  - [x] 8.1 实现 MultiInstanceDataResolver 组件
    - 新建 MultiInstanceDataResolver Spring 组件
    - 实现 loadSubTaskFormData()：加载主任务表单数据（从流程变量）+ 子表数据行
    - 实现 loadMainFormData()：从 runtimeService.getVariables() 获取主表单数据，过滤系统变量和集合变量
    - 实现 loadSubTableRow()：根据 taskId 查询 ExtendedTaskInfo 获取 subTableRowId，仅加载对应数据行
    - 实现 writeBackSubTableRow()：乐观锁校验 row_version，UPDATE ... SET row_version = row_version + 1 WHERE id = ? AND row_version = ?
    - 影响行数为 0 时区分数据行被删除（抛出 WorkflowValidationException）和 row_version 不一致（抛出 OptimisticLockException）
    - 实现辅助方法：isSystemVariable()、getMainFormFields()、getSubFormFields()
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 新增功能 - 主任务信息展示_

  - [x] 8.2 属性测试：子任务数据隔离
    - **Property 10: 子任务数据隔离**
    - 随机生成多个子任务，验证每个子任务只能访问自己关联的子表数据行
    - **验证: 需求 6.1, 6.2**

  - [x] 8.3 属性测试：子表数据回写往返一致性
    - **Property 11: 子表数据回写往返一致性**
    - 随机生成表单数据，写入后读取验证一致性
    - **验证: 需求 6.3**

  - [x] 8.4 属性测试：乐观锁正确性
    - **Property 12: 乐观锁正确性**
    - 随机生成 row_version 值，验证匹配时更新成功且 version+1，不匹配时更新被拒绝
    - **验证: 需求 6.5, 6.6**

  - [x] 8.5 单元测试：MultiInstanceDataResolver 边界条件
    - 测试正常数据回写和加载
    - 测试主任务数据正确过滤系统变量
    - 测试数据行已删除时抛出异常
    - 测试 row_version 不一致时抛出 OptimisticLockException
    - _需求: 6.4, 6.5_

- [x] 9. Checkpoint - 确保数据注入、任务分配、数据回写测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 10. workflow-engine-core：MultiInstanceCanceller 级联取消
  - [x] 10.1 实现 MultiInstanceCanceller 组件
    - 新建 MultiInstanceCanceller Spring 组件
    - 实现 cancelMultiInstanceTasks()：查询所有活跃的子流程执行，批量更新 ExtendedTaskInfo 状态为 CANCELLED
    - 返回 CancelResult（被取消数量、各子任务处理人、取消前状态）
    - 记录审计日志
    - 部分更新失败时记录 ERROR 日志，继续处理其他子任务
    - 无活跃子任务时静默跳过
    - _需求: 9.1, 9.2, 9.3, 9.4_

  - [x] 10.2 属性测试：级联取消正确性
    - **Property 16: 级联取消正确性**
    - 随机生成不同数量的活跃/已完成子任务，验证所有未完成子任务被取消
    - **验证: 需求 9.1, 9.2**

  - [x] 10.3 属性测试：取消时数据保留
    - **Property 17: 取消时数据保留**
    - 验证被取消的多实例子流程中已提交的子表数据不被回滚或删除
    - **验证: 需求 9.3**

  - [x] 10.4 属性测试：取消审计日志完整性
    - **Property 18: 取消审计日志完整性**
    - 验证审计日志包含被取消数量、处理人 ID 和取消前状态
    - **验证: 需求 9.4**

  - [x] 10.5 单元测试：MultiInstanceCanceller 边界条件
    - 测试取消 5 个子任务中的 3 个活跃任务
    - 测试无活跃子任务时的静默处理
    - 验证审计日志内容完整性
    - _需求: 9.1, 9.4_

- [x] 11. workflow-engine-core：MultiInstanceStatusController 状态监控
  - [x] 11.1 实现 MultiInstanceStatusController REST 接口
    - 新建 MultiInstanceStatusController
    - 实现 GET /api/workflow/multi-instance/{processInstanceId}/status
    - 从 Flowable 运行时数据和 ExtendedTaskInfo 中聚合子任务信息
    - 返回 MultiInstanceStatusResponse（totalInstances、completedInstances、activeInstances、各子任务详情）
    - _需求: 7.1, 7.2_

  - [x] 11.2 实现子任务表单数据加载接口
    - 实现 GET /api/workflow/tasks/{taskId}/sub-task-form-data
    - 调用 MultiInstanceDataResolver.loadSubTaskFormData() 加载数据
    - 返回 SubTaskFormData（mainFormData、mainFormFields、subTableRowData、subFormFields、rowVersion）
    - _需求: 6.1, 新增功能 - 主任务信息展示_

  - [x] 11.3 实现主任务子表数据查询接口（用于实时同步）
    - 实现 GET /api/workflow/tasks/{taskId}/sub-table-data/all
    - 查询子表所有数据行（含 assignee、status）
    - 返回子表数据列表
    - _需求: 新增功能 - 实时同步_

  - [x] 11.4 属性测试：状态查询一致性
    - **Property 9: 状态查询一致性**
    - 验证 completedInstances + activeInstances + cancelledInstances == totalInstances
    - **验证: 需求 5.4**

- [x] 12. workflow-engine-core：TaskManagerComponent 与 ProcessEngineComponent 扩展
  - [x] 12.1 扩展 TaskManagerComponent.completeTask() 支持多实例
    - 前置任务完成时：检测下一节点是否为多实例子流程，调用 SubTableDataInjector 注入数据
    - 子任务完成时：检测 extendedProperties 中 multiInstance=true，调用 MultiInstanceDataResolver 回写数据
    - _需求: 3.1, 5.1, 6.3_

  - [x] 12.2 扩展 TaskManagerComponent.returnTask() 支持多实例回退
    - 回退目标在多实例子流程之前时，调用 MultiInstanceCanceller 级联取消
    - _需求: 9.2_

  - [x] 12.3 扩展 ProcessEngineComponent.controlProcessInstance() 支持多实例终止
    - 在 terminate 分支中，调用 runtimeService.deleteProcessInstance() 之前先调用 MultiInstanceCanceller.cancelMultiInstanceTasks()
    - 更新 ExtendedTaskInfo 状态并记录审计日志
    - _需求: 9.1_

  - [x] 12.4 属性测试：子任务完成状态更新
    - **Property 7: 子任务完成状态更新**
    - 验证完成的子任务 ExtendedTaskInfo 状态为 COMPLETED，completed_time 和 completed_by 正确
    - **验证: 需求 5.1**

  - [x] 12.5 属性测试：多实例全部完成触发流程推进
    - **Property 8: 多实例全部完成触发流程推进**
    - 验证 N 个子任务全部完成后流程自动推进
    - **验证: 需求 5.2**

  - [x] 12.6 属性测试：历史记录保留
    - **Property 13: 历史记录保留**
    - 验证已完成的多实例子流程可通过 HistoryService 查询所有子任务历史
    - **验证: 需求 7.3**

- [x] 13. Checkpoint - 确保所有后端组件集成测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 14. user-portal 前端：MainTaskForm 子表 Assign 按钮
  - [x] 14.1 实现子表 Assign 按钮组件
    - 在 MainTaskForm 的子表组件中为每行数据增加 Assign 按钮
    - 已分配的行显示处理人姓名，按钮文本为"重新分配"
    - 未分配的行显示"未分配"，按钮文本为"分配"
    - Assign 按钮仅在当前用户是任务处理人时可用（canAssign 权限控制）
    - _需求: 新增功能 - 手动分配_

  - [x] 14.2 实现用户选择器对话框
    - 点击 Assign 按钮时弹出用户选择器
    - 支持搜索和选择用户
    - 确认后调用 POST /api/workflow/tasks/{taskId}/sub-table-rows/{rowId}/assign
    - 成功后刷新子表数据，更新该行显示
    - _需求: 新增功能 - 手动分配_

  - [x] 14.3 单元测试：Assign 按钮交互
    - 测试按钮权限控制
    - 测试用户选择器弹出和关闭
    - 测试分配成功后刷新
    - _需求: 新增功能 - 手动分配_

- [x] 15. user-portal 前端：SubTaskForm 子任务表单
  - [x] 15.1 实现子任务表单组件
    - 创建 SubTaskForm 组件，分为两部分：主任务信息（只读）+ 子任务表单（可编辑）
    - 主任务信息区域：灰色背景，显示主表单字段（只读）
    - 子任务表单区域：显示子表单字段（可编辑），包含隐藏的 rowVersion 字段
    - 调用 GET /api/workflow/tasks/{taskId}/sub-task-form-data 加载数据
    - 提交时调用 POST /api/workflow/tasks/{taskId}/complete，传递 formData 和 rowVersion
    - _需求: 6.1, 6.3, 新增功能 - 主任务信息展示_

  - [x] 15.2 实现表单样式和布局
    - 主任务信息区域：灰色背景、白色字段框、只读样式
    - 分隔线：清晰区分主任务和子任务区域
    - 子任务表单区域：蓝色标题、可编辑字段
    - 响应式布局，适配移动端
    - _需求: 新增功能 - 主任务信息展示_

  - [x] 15.3 单元测试：SubTaskForm 数据加载和提交
    - 测试主任务数据正确显示（只读）
    - 测试子任务数据正确加载和编辑
    - 测试表单提交（含 rowVersion）
    - 测试乐观锁冲突时的错误提示
    - _需求: 6.3, 6.5_

- [x] 16. user-portal 前端：主任务表单子表数据实时同步
  - [x] 16.1 实现轮询方式实时同步（简单实现）
    - 在 MainTaskForm 中每 5 秒轮询一次子表数据
    - 调用 GET /api/workflow/tasks/{taskId}/sub-table-data/all
    - 更新子表显示（处理人、状态）
    - 组件卸载时清除定时器
    - _需求: 新增功能 - 实时同步_

  - [x] 16.2* 实现 WebSocket 方式实时同步（推荐，可选）
    - 使用 WebSocket 订阅子表数据更新事件
    - 订阅 `/topic/tasks/{taskId}/sub-table-updates`
    - 收到消息时刷新子表数据
    - 组件卸载时取消订阅
    - _需求: 新增功能 - 实时同步_

  - [x] 16.3 单元测试：实时同步功能
    - 测试轮询定时器正确启动和清除
    - 测试数据更新后子表正确刷新
    - 测试 WebSocket 订阅和取消订阅（如实现）
    - _需求: 新增功能 - 实时同步_

- [x] 17. Checkpoint - 确保前端组件功能测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 18. Flowable 集成测试：端到端流程验证
  - [x] 18.1 编写多实例子流程端到端集成测试
    - 使用 Spring Boot Test + Flowable 内嵌引擎
    - 部署包含多实例子流程的 BPMN XML
    - 启动流程实例，模拟前端手动分配处理人
    - 完成前置任务触发数据注入
    - 验证子任务创建数量和分配正确
    - 逐个完成子任务，验证数据回写（含乐观锁）
    - 验证主任务表单数据在子任务中正确显示
    - 验证流程自动推进到下一节点
    - 测试取消和撤回场景的级联处理
    - _需求: 3.1, 4.1, 4.2, 5.1, 5.2, 6.3, 9.1, 9.2, 新增功能_

- [x] 19. 最终 Checkpoint - 确保全部测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试使用 jqwik 框架，每个属性至少运行 100 次迭代
- Checkpoint 任务用于阶段性验证，确保增量正确性
- 新增功能包括：手动分配处理人、主任务信息展示、实时同步
