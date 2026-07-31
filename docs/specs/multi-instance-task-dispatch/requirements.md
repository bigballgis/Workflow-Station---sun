# 需求文档：BPMN 多实例子流程（Multi-Instance Sub-Process）动态任务分发

## 简介

本功能为平台引入 BPMN 多实例子流程（Multi-Instance Sub-Process）模式，实现基于子表数据的动态任务分发。当流程到达多实例节点时，系统根据子表中的数据行数动态创建对应数量的子任务实例，每个实例分配给对应的处理人。所有子任务实例完成后，流程自动继续执行后续节点。

典型场景：宣讲会参与人信息收集流程中，先由管理员统计参与人员并在子表中逐条录入，然后系统根据子表行数自动为每位参与人创建独立的"补充个人信息"任务，全部完成后流程进入"收集完成"节点。

## 术语表

- **Multi_Instance_Sub_Process（多实例子流程）**：BPMN 2.0 标准中的子流程元素，配置了多实例特性（multiInstanceLoopCharacteristics），可根据集合数据动态创建多个并行或顺序执行的子流程实例
- **Function_Unit（功能单元）**：平台核心组织单元，包含 ProcessDefinition、FormDefinition、TableDefinition、ActionDefinition、DecisionDefinition
- **Sub_Table（子表）**：TableDefinition 中 table_type 为 SUB 的数据表，通过外键关联主表，存储一对多的明细数据
- **Collection_Variable（集合变量）**：Flowable 多实例配置中的 flowable:collection 属性，指向一个列表类型的流程变量，列表长度决定子实例数量
- **Element_Variable（元素变量）**：Flowable 多实例配置中的 flowable:elementVariable 属性，每个子实例中可访问的当前迭代元素变量
- **Loop_Cardinality（循环基数）**：多实例的实例数量，等于 Collection_Variable 列表的长度
- **Completion_Condition（完成条件）**：多实例的完成判断表达式，决定何时终止多实例执行
- **BPMN_XML_Generator（BPMN XML 生成器）**：developer-workstation 中负责将流程设计转换为 BPMN 2.0 XML 的组件
- **Task_Assignment_Listener（任务分配监听器）**：workflow-engine-core 中的 TaskAssignmentListener，在任务创建时根据扩展属性自动分配处理人
- **Extended_Task_Info（扩展任务信息）**：wf_extended_task_info 表中存储的任务扩展数据，包含分配类型、分配目标等
- **Sub_Table_Row_Id（子表行ID）**：子表中每条数据记录的主键 ID，用于将子任务实例与具体数据行关联
- **Assignee_Field（处理人字段）**：子表中用于指定每条数据对应处理人的字段，存储用户 ID

## 需求

### 需求 1：多实例子流程 BPMN XML 生成

**用户故事：** 作为功能单元设计者，我希望在流程定义中配置多实例子流程节点，以便系统能根据子表数据动态创建多个并行任务实例。

#### 验收标准

1. WHEN 功能单元设计者在流程中添加多实例子流程节点并指定关联子表时，THE BPMN_XML_Generator SHALL 生成包含 `<bpmn:subProcess>` 元素和 `<bpmn:multiInstanceLoopCharacteristics>` 子元素的合法 BPMN 2.0 XML
2. THE BPMN_XML_Generator SHALL 在多实例配置中生成 `flowable:collection` 属性指向子表数据集合变量名，并生成 `flowable:elementVariable` 属性指向当前迭代元素变量名
3. WHEN 多实例子流程配置为并行模式时，THE BPMN_XML_Generator SHALL 生成 `isSequential="false"` 属性；WHEN 配置为顺序模式时，THE BPMN_XML_Generator SHALL 生成 `isSequential="true"` 属性
4. THE BPMN_XML_Generator SHALL 在子流程内部生成至少一个 `<bpmn:userTask>` 元素，该元素包含自定义扩展属性 `assigneeType`、`subTableId`、`assigneeField` 和 `rowIdVariable`
5. WHEN 设计者配置了完成条件表达式时，THE BPMN_XML_Generator SHALL 在 `<bpmn:multiInstanceLoopCharacteristics>` 中生成 `<bpmn:completionCondition>` 子元素

### 需求 2：多实例子流程节点配置数据模型

**用户故事：** 作为功能单元设计者，我希望能够配置多实例子流程的关联子表、处理人字段和执行模式，以便系统知道如何分发任务。

#### 验收标准

1. THE Function_Unit SHALL 支持在 ProcessDefinition 的 BPMN XML 中存储多实例子流程节点的配置信息，包括关联子表 ID、处理人字段名、元素变量名和执行模式（并行/顺序）
2. WHEN 功能单元设计者选择一个 Sub_Table 作为多实例数据源时，THE Function_Unit SHALL 验证该 Sub_Table 属于当前 Function_Unit 且 table_type 为 SUB
3. WHEN 功能单元设计者指定处理人字段时，THE Function_Unit SHALL 验证该字段存在于所选 Sub_Table 的 FieldDefinition 列表中
4. THE Function_Unit SHALL 支持为多实例子流程内的用户任务绑定独立的 Task Form（FormDefinition，form_type 为 SUB 或 MAIN），用于每个子任务实例的数据填写

### 需求 3：子表数据注入流程变量

**用户故事：** 作为流程参与人，我希望在"批准收集"任务完成后，子表数据能自动注入为流程变量，以便多实例子流程能根据数据行数创建对应数量的子任务。

#### 验收标准

1. WHEN 多实例子流程的前置任务（如"批准收集"）完成时，THE Process_Engine SHALL 从关联的 Sub_Table 中查询当前主表记录下的所有子表数据行，并将其注入为流程变量
2. THE Process_Engine SHALL 将子表数据行转换为一个 List 类型的流程变量，列表中每个元素包含该行的 Sub_Table_Row_Id、Assignee_Field 的值和当前行的 row_version
3. THE Process_Engine SHALL 将该 List 变量以 Collection_Variable 名称设置到流程实例的变量中
4. IF 子表数据行数为零，THEN THE Process_Engine SHALL 阻止前置任务完成并返回错误信息"多实例数据源为空，至少需要一条子表数据"
5. IF 子表数据行中存在 Assignee_Field 值为空的记录，THEN THE Process_Engine SHALL 阻止前置任务完成并返回错误信息，指明哪些行缺少处理人

### 需求 4：多实例子任务动态创建与分配

**用户故事：** 作为系统，我希望在流程执行到多实例子流程节点时，能为每条子表数据自动创建独立的子任务并分配给对应的处理人。

#### 验收标准

1. WHEN 流程执行到多实例子流程节点时，THE Process_Engine SHALL 根据 Collection_Variable 列表的长度创建对应数量的子流程实例
2. WHEN 每个子流程实例中的用户任务被创建时，THE Task_Assignment_Listener SHALL 从 Element_Variable 中读取当前迭代元素的 Assignee_Field 值，并将该用户任务分配给对应的处理人
3. THE Task_Assignment_Listener SHALL 为每个子任务创建 Extended_Task_Info 记录，其中 assignment_type 为 USER，assignment_target 为 Element_Variable 中的处理人 ID
4. THE Task_Assignment_Listener SHALL 在 Extended_Task_Info 的 extended_properties 字段中存储当前子任务关联的 Sub_Table_Row_Id，以便前端加载对应的子表数据行
5. IF Element_Variable 中的处理人 ID 对应的用户不存在或已禁用，THEN THE Task_Assignment_Listener SHALL 记录异常日志并将任务状态设置为 CREATED（待手动分配）

### 需求 5：多实例子任务完成与流程推进

**用户故事：** 作为流程参与人，我希望完成自己的子任务后，系统能自动判断是否所有子任务都已完成，并在全部完成后推进流程。

#### 验收标准

1. WHEN 一个子任务实例被完成时，THE Process_Engine SHALL 更新对应的 Extended_Task_Info 记录状态为 COMPLETED
2. WHEN 所有子任务实例都已完成且未配置自定义 Completion_Condition 时，THE Process_Engine SHALL 自动完成多实例子流程节点并推进流程到下一个节点
3. WHEN 配置了自定义 Completion_Condition 表达式时，THE Process_Engine SHALL 在每个子任务完成后评估该表达式，表达式为 true 时提前终止多实例执行并推进流程
4. WHILE 多实例子流程正在执行中，THE Process_Engine SHALL 支持查询当前多实例的总实例数、已完成实例数和未完成实例数

### 需求 6：多实例子任务数据隔离与回写

**用户故事：** 作为子任务处理人，我希望在处理任务时只能看到和编辑分配给我的那条子表数据，完成后数据能回写到子表中。

#### 验收标准

1. WHEN 子任务处理人打开任务表单时，THE Process_Engine SHALL 根据 Extended_Task_Info 中存储的 Sub_Table_Row_Id 仅加载对应的子表数据行
2. THE Process_Engine SHALL 确保子任务处理人只能编辑自己被分配的子表数据行，不能访问其他子任务实例关联的数据行
3. WHEN 子任务处理人提交任务表单时，THE Process_Engine SHALL 将表单数据回写到 Sub_Table 中对应 Sub_Table_Row_Id 的数据行
4. IF 子任务处理人提交数据时对应的子表数据行已被删除，THEN THE Process_Engine SHALL 返回错误信息"关联的数据行已不存在"并阻止任务完成
5. THE Process_Engine SHALL 在子任务加载数据时记录当前行的 row_version，提交时校验 row_version 是否与数据库中一致；IF row_version 不一致，THEN THE Process_Engine SHALL 返回错误信息"数据已被修改，请刷新后重试"并阻止任务完成
6. WHEN 子任务处理人成功提交数据后，THE Process_Engine SHALL 递增对应子表数据行的 row_version 值

### 需求 7：多实例执行状态监控

**用户故事：** 作为流程管理员，我希望能够查看多实例子流程的执行进度，以便了解任务分发和完成情况。

#### 验收标准

1. THE Process_Engine SHALL 提供 API 接口返回指定流程实例中多实例子流程的执行状态，包括：总实例数、已完成数、进行中数、各子任务的处理人和状态
2. WHEN 流程管理员查询多实例执行状态时，THE Process_Engine SHALL 从 Flowable 运行时数据和 Extended_Task_Info 中聚合子任务信息
3. THE Process_Engine SHALL 在多实例子流程全部完成后保留历史执行记录，支持通过 Flowable HistoryService 查询已完成的多实例子任务详情

### 需求 8：BPMN XML 多实例配置解析与验证

**用户故事：** 作为系统，我希望在部署流程定义时能正确解析和验证多实例子流程的 BPMN XML 配置，以确保运行时不会出现配置错误。

#### 验收标准

1. WHEN 流程定义被部署时，THE BPMN_XML_Generator SHALL 解析 BPMN XML 中的多实例子流程配置，验证 flowable:collection 引用的变量名格式合法
2. WHEN 流程定义被部署时，THE BPMN_XML_Generator SHALL 验证多实例子流程内部至少包含一个用户任务节点
3. IF BPMN XML 中多实例子流程的配置不完整（缺少 collection 或 elementVariable），THEN THE BPMN_XML_Generator SHALL 返回明确的验证错误信息
4. THE BPMN_XML_Generator SHALL 解析多实例子流程 BPMN XML 后能还原为等价的配置对象，再序列化回 BPMN XML 后与原始配置语义一致（往返一致性）

### 需求 9：多实例子流程取消与撤回

**用户故事：** 作为流程管理员，我希望在主流程被取消或撤回时，所有正在执行的多实例子任务也能被自动取消，以避免出现孤立的子任务。

#### 验收标准

1. WHEN 主流程实例被取消时，THE Process_Engine SHALL 自动终止多实例子流程中所有未完成的子流程实例，并将对应的 Extended_Task_Info 记录状态更新为 CANCELLED
2. WHEN 主流程实例被撤回到多实例子流程之前的节点时，THE Process_Engine SHALL 自动终止多实例子流程中所有未完成的子流程实例，并将对应的 Extended_Task_Info 记录状态更新为 CANCELLED
3. WHEN 多实例子任务被取消时，THE Process_Engine SHALL 保留子任务处理人已提交的数据（不回滚子表数据），但将任务标记为已取消
4. THE Process_Engine SHALL 在取消多实例子流程后记录审计日志，包含被取消的子任务数量、各子任务的处理人和取消前的状态
