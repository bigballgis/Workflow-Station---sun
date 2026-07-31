# 实施计划：审批流程系统

## 概述

本实施计划将审批流程系统设计转换为可执行的编码任务。系统基于 Spring Boot 框架，使用 Camunda 流程引擎和 PostgreSQL 数据库。实施将按照分层架构进行，从数据层到服务层再到 API 层，确保每个步骤都经过测试验证。

## 任务

- [x] 1. 项目初始化和基础设施配置
  - 创建 Spring Boot 项目结构（Maven/Gradle）
  - 配置 PostgreSQL 数据库连接
  - 集成 Camunda BPM 或 Flowable 流程引擎
  - 配置 Spring Security 基础设施
  - 配置 JPA/Hibernate
  - 添加必要的依赖（Spring Web、Spring Data JPA、Validation、Lombok 等）
  - _需求：11.3, 11.5_

- [ ] 2. 数据模型实现
  - [ ] 2.1 创建枚举类型
    - 实现 RequestStatus 枚举（DRAFT, PENDING, APPROVED, REJECTED）
    - 实现 ActionType 枚举（SUBMIT, APPROVE, REJECT）
    - 实现 DecisionType 枚举（APPROVE, REJECT）
    - _需求：3.3, 5.2_
  
  - [ ] 2.2 实现 Request 实体
    - 创建 Request JPA 实体类
    - 添加字段：id, initiatorId, managerId, title, content, status, processInstanceId, createdAt, updatedAt
    - 实现 @PrePersist 和 @PreUpdate 生命周期回调
    - 配置与 ApprovalRecord 的一对多关系
    - _需求：2.1, 2.2, 2.4, 2.5_
  
  - [ ]* 2.3 编写 Request 实体的属性测试
    - **属性 5：Request 创建默认值**
    - **验证需求：2.2**
  
  - [ ]* 2.4 编写 Request 实体的属性测试
    - **属性 6：Request 数据持久化往返**
    - **验证需求：2.5**
  
  - [ ]* 2.5 编写 Request 实体的属性测试
    - **属性 7：状态变更更新时间戳**
    - **验证需求：2.4**
  
  - [ ] 2.6 实现 ApprovalRecord 实体
    - 创建 ApprovalRecord JPA 实体类
    - 添加字段：id, request, approverId, action, comment, createdAt
    - 配置与 Request 的多对一关系
    - 实现 @PrePersist 生命周期回调
    - _需求：3.1, 3.2, 3.4_
  
  - [ ] 2.7 实现 FunctionUnit 相关实体
    - 创建 FunctionUnit 实体
    - 创建 ProcessDefinition 实体
    - 创建 TableDefinition 实体
    - 创建 FormDefinition 实体
    - 创建 ActionDefinition 实体
    - 配置实体之间的关联关系
    - _需求：11.1, 11.2_

- [ ] 3. DTO 和验证实现
  - [ ] 3.1 创建请求 DTO
    - 实现 CreateRequestDTO（title, content, managerId）
    - 添加 Bean Validation 注解（@NotBlank, @Size）
    - 实现 ApprovalDTO（decision, comment）
    - 添加验证注解
    - _需求：4.2, 4.3, 5.3, 5.4_
  
  - [ ] 3.2 创建响应 DTO
    - 实现 RequestDTO
    - 实现 RequestDetailDTO
    - 实现 ApprovalRecordDTO
    - 实现 DTO 与实体之间的映射方法
    - _需求：8.2, 8.3_
  
  - [ ] 3.3 创建错误响应 DTO
    - 实现 ErrorResponse 类
    - 包含 errorCode, message, details, timestamp 字段
    - _需求：13.5, 14.7, 14.8, 14.9_
  
  - [ ]* 3.4 编写输入验证的属性测试
    - **属性 10：输入验证错误响应**
    - **验证需求：4.4, 13.1, 13.2, 13.5**
  
  - [ ]* 3.5 编写枚举验证的属性测试
    - **属性 11：枚举值验证**
    - **验证需求：3.3, 5.2, 5.3, 5.5**

- [ ] 4. Repository 层实现
  - [ ] 4.1 创建 Repository 接口
    - 实现 RequestRepository 继承 JpaRepository
    - 添加自定义查询方法（findByInitiatorId, findByManagerId）
    - 实现 ApprovalRecordRepository
    - 添加查询方法（findByRequestIdOrderByCreatedAtDesc）
    - 实现 FunctionUnitRepository
    - _需求：2.5, 3.5_
  
  - [ ]* 4.2 编写 Repository 的单元测试
    - 测试基本 CRUD 操作
    - 测试自定义查询方法
    - _需求：2.5_

- [ ] 5. BPMN 流程定义
  - [ ] 5.1 创建审批流程 BPMN 文件
    - 创建 approval-process.bpmn XML 文件
    - 定义开始事件、用户任务、排他网关、结束事件
    - 配置流程变量（requestId, managerId, approved）
    - 配置任务分配表达式（${managerId}）
    - 配置网关条件表达式
    - _需求：1.1, 1.2, 1.3, 1.4_
  
  - [ ]* 5.2 编写 BPMN 定义验证的属性测试
    - **属性 2：BPMN 定义结构验证**
    - **验证需求：1.2**
  
  - [ ]* 5.3 编写 BPMN 流程变量的属性测试
    - **属性 3：BPMN 流程变量完整性**
    - **验证需求：1.3**

- [ ] 6. 检查点 - 确保数据层和流程定义完成
  - 确保所有测试通过，如有问题请向用户询问。

- [ ] 7. 流程引擎服务实现
  - [ ] 7.1 实现 ProcessEngineService
    - 注入 RuntimeService 和 TaskService
    - 实现 startProcess 方法
    - 实现 completeTask 方法
    - 实现 getTaskByRequestId 方法
    - 实现 getProcessInstance 方法
    - 实现 isProcessActive 方法
    - _需求：6.3, 7.5, 12.1, 12.4_
  
  - [ ]* 7.2 编写流程引擎服务的单元测试
    - 测试流程启动
    - 测试任务完成
    - 测试流程查询
    - _需求：6.3, 7.5_
  
  - [ ]* 7.3 编写流程网关的属性测试
    - **属性 4：流程网关条件评估**
    - **验证需求：1.4**

- [ ] 8. 审批记录服务实现
  - [ ] 8.1 实现 ApprovalRecordService
    - 注入 ApprovalRecordRepository
    - 实现 createRecord 方法
    - 实现 getRecordsByRequestId 方法
    - 添加 @Transactional 注解
    - _需求：3.2, 6.2, 7.3, 7.4_
  
  - [ ]* 8.2 编写审批记录服务的属性测试
    - **属性 8：审批操作创建记录**
    - **验证需求：3.2, 6.2, 7.3, 7.4**
  
  - [ ]* 8.3 编写审批记录排序的属性测试
    - **属性 21：审批记录排序**
    - **验证需求：8.3**

- [ ] 9. 请求服务核心逻辑实现
  - [ ] 9.1 实现 RequestService - 基础方法
    - 注入 RequestRepository, ApprovalRecordService, ProcessEngineService
    - 实现 createRequest 方法
    - 实现 getRequestDetail 方法
    - 添加 @Transactional 注解
    - _需求：2.2, 8.2, 8.3_
  
  - [ ] 9.2 实现状态转换验证逻辑
    - 实现 validateStateTransition 方法
    - 定义有效的状态转换规则
    - 实现终态检查逻辑
    - 抛出 InvalidStateTransitionException
    - _需求：10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [ ]* 9.3 编写状态转换的属性测试
    - **属性 12：有效状态转换路径**
    - **验证需求：10.1, 10.2, 10.5**
  
  - [ ]* 9.4 编写终态不可变的属性测试
    - **属性 13：终态不可变性**
    - **验证需求：10.3, 10.4**
  
  - [ ] 9.3 实现权限检查逻辑
    - 实现 checkPermission 方法
    - 验证 Initiator 权限（只能提交自己的 Request）
    - 验证 Manager 权限（只能审批分配给自己的 Request）
    - 验证查看权限
    - 抛出 UnauthorizedException
    - _需求：7.6, 8.4, 8.5, 9.1, 9.2, 9.3_
  
  - [ ]* 9.6 编写权限验证的属性测试
    - **属性 19：审批权限验证**
    - **验证需求：7.6**
  
  - [ ]* 9.7 编写权限验证的属性测试
    - **属性 22：查看权限验证**
    - **验证需求：8.4, 8.5**
  
  - [ ]* 9.8 编写权限验证的属性测试
    - **属性 23：未授权操作拒绝**
    - **验证需求：9.1, 9.2, 9.3**

- [ ] 10. 请求服务业务操作实现
  - [ ] 10.1 实现 submitRequest 方法
    - 加载 Request
    - 验证状态转换（DRAFT → PENDING）
    - 检查权限（只有 Initiator 可以提交）
    - 更新 Request 状态为 PENDING
    - 创建 ApprovalRecord（action = SUBMIT）
    - 启动流程实例
    - 更新 Request 的 processInstanceId
    - 添加 @Transactional 注解
    - _需求：6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [ ]* 10.2 编写提交操作的属性测试
    - **属性 14：提交操作状态转换**
    - **验证需求：6.1, 6.2, 6.3**
  
  - [ ]* 10.3 编写提交操作的属性测试
    - **属性 15：非 DRAFT 状态提交拒绝**
    - **验证需求：6.5**
  
  - [ ]* 10.4 编写任务分配的属性测试
    - **属性 16：任务分配正确性**
    - **验证需求：6.4**
  
  - [ ] 10.5 实现 approveRequest 方法
    - 加载 Request
    - 检查权限（只有分配的 Manager 可以审批）
    - 更新 Request 状态为 APPROVED
    - 创建 ApprovalRecord（action = APPROVE）
    - 获取流程任务
    - 完成任务（approved = true）
    - 添加 @Transactional 注解
    - _需求：7.1, 7.3, 7.5, 7.6_
  
  - [ ] 10.6 实现 rejectRequest 方法
    - 加载 Request
    - 检查权限
    - 更新 Request 状态为 REJECTED
    - 创建 ApprovalRecord（action = REJECT）
    - 获取流程任务
    - 完成任务（approved = false）
    - 添加 @Transactional 注解
    - _需求：7.2, 7.4, 7.5, 7.6_
  
  - [ ]* 10.7 编写审批操作的属性测试
    - **属性 17：审批操作状态转换**
    - **验证需求：7.1, 7.2**
  
  - [ ]* 10.8 编写审批操作的属性测试
    - **属性 18：审批操作推进流程**
    - **验证需求：7.5**

- [ ] 11. 检查点 - 确保服务层完成
  - 确保所有测试通过，如有问题请向用户询问。

- [ ] 12. 异常处理实现
  - [ ] 12.1 创建自定义异常类
    - 实现 InvalidStateTransitionException
    - 实现 UnauthorizedException
    - 实现 ResourceNotFoundException
    - _需求：6.5, 9.1, 9.2, 9.3, 10.5_
  
  - [ ] 12.2 实现全局异常处理器
    - 创建 @RestControllerAdvice 类
    - 实现 handleValidationException（返回 400）
    - 实现 handleUnauthorizedException（返回 403，记录日志）
    - 实现 handleNotFoundException（返回 404）
    - 实现 handleStateTransitionException（返回 400）
    - 实现 handleProcessEngineException（返回 500）
    - 实现 handleDataIntegrityException（返回 400）
    - 实现 handleGenericException（返回 500）
    - _需求：9.5, 13.5, 14.7, 14.8, 14.9_
  
  - [ ]* 12.3 编写异常处理的单元测试
    - 测试各种异常的 HTTP 响应码
    - 测试错误响应格式
    - _需求：14.7, 14.8, 14.9_
  
  - [ ]* 12.4 编写 HTTP 响应格式的属性测试
    - **属性 33：HTTP 错误响应格式**
    - **验证需求：14.7, 14.8, 14.9**

- [ ] 13. REST API 控制器实现
  - [ ] 13.1 实现 RequestController
    - 添加 @RestController 和 @RequestMapping("/api/requests")
    - 注入 RequestService
    - 实现 createRequest 端点（POST /api/requests）
    - 实现 submitRequest 端点（POST /api/requests/{id}/submit）
    - 实现 approveRequest 端点（POST /api/requests/{id}/approve）
    - 实现 rejectRequest 端点（POST /api/requests/{id}/reject）
    - 实现 getRequest 端点（GET /api/requests/{id}）
    - 添加 @Valid 注解进行参数验证
    - 从 SecurityContext 获取当前用户 ID
    - _需求：14.1, 14.2, 14.3, 14.4, 14.5, 14.6_
  
  - [ ]* 13.2 编写 API 端点的集成测试
    - 测试创建 Request 端点
    - 测试提交 Request 端点
    - 测试审批 Request 端点
    - 测试拒绝 Request 端点
    - 测试查看 Request 端点
    - 验证 HTTP 状态码和响应格式
    - _需求：14.1, 14.2, 14.3, 14.4, 14.5, 14.6_
  
  - [ ]* 13.3 编写 HTTP 成功响应的属性测试
    - **属性 32：HTTP 成功响应格式**
    - **验证需求：14.6**
  
  - [ ]* 13.4 编写查看操作响应的属性测试
    - **属性 20：查看操作响应完整性**
    - **验证需求：8.2, 8.3**

- [ ] 14. 安全配置实现
  - [ ] 14.1 配置 Spring Security
    - 创建 SecurityConfig 类
    - 配置 HTTP 安全规则
    - 配置认证机制（JWT 或 Session）
    - 集成 Developer Workstation 的认证系统
    - _需求：9.4, 11.5_
  
  - [ ]* 14.2 编写身份验证的属性测试
    - **属性 24：操作前身份验证**
    - **验证需求：9.4**
  
  - [ ]* 14.3 编写授权日志的属性测试
    - **属性 25：授权失败日志记录**
    - **验证需求：9.5**

- [ ] 15. 功能单元服务实现
  - [ ] 15.1 实现 FunctionUnitService
    - 实现 createApprovalWorkflowUnit 方法
    - 创建 FunctionUnit 实体（name = "approval-workflow"）
    - 实现 registerProcessDefinition 方法
    - 实现 registerTableDefinitions 方法
    - 实现 registerFormDefinitions 方法
    - 实现 registerActionDefinitions 方法
    - _需求：11.1, 11.2, 11.3_
  
  - [ ] 15.2 创建初始化组件
    - 创建 @Component 类实现 ApplicationRunner
    - 在应用启动时调用 FunctionUnitService
    - 注册所有定义到系统
    - _需求：11.3_
  
  - [ ]* 15.3 编写功能单元的单元测试
    - 测试 FunctionUnit 创建
    - 测试定义注册
    - _需求：11.1, 11.2, 11.3_

- [ ] 16. 事务管理和数据完整性
  - [ ]* 16.1 编写事务原子性的属性测试
    - **属性 34：事务原子性**
    - **验证需求：15.1, 15.2, 15.3**
  
  - [ ]* 16.2 编写外键完整性的属性测试
    - **属性 30：外键引用完整性**
    - **验证需求：2.3, 13.3**
  
  - [ ]* 16.3 编写输入清理的属性测试
    - **属性 31：输入清理防注入**
    - **验证需求：13.4**

- [ ] 17. 流程实例管理测试
  - [ ]* 17.1 编写流程实例的属性测试
    - **属性 26：流程实例唯一性**
    - **验证需求：12.1, 12.2**
  
  - [ ]* 17.2 编写流程完成的属性测试
    - **属性 27：流程完成状态同步**
    - **验证需求：12.3**
  
  - [ ]* 17.3 编写流程查询的属性测试
    - **属性 28：流程实例查询**
    - **验证需求：12.4**
  
  - [ ]* 17.4 编写流程错误处理的属性测试
    - **属性 29：流程错误状态保持**
    - **验证需求：12.5**

- [ ] 18. BPMN 流程测试
  - [ ]* 18.1 编写流程执行的集成测试
    - 测试完整的审批流程（批准路径）
    - 测试完整的审批流程（拒绝路径）
    - 验证任务分配
    - 验证网关路由
    - 验证流程变量传递
    - _需求：1.4, 6.3, 6.4, 7.5_
  
  - [ ]* 18.2 编写 BPMN 往返的属性测试
    - **属性 1：BPMN 定义往返一致性**
    - **验证需求：1.1**

- [ ] 19. 端到端集成测试
  - [ ]* 19.1 编写完整业务流程的集成测试
    - 测试：创建 Request → 提交 → 审批 → 完成
    - 测试：创建 Request → 提交 → 拒绝 → 完成
    - 验证所有数据库记录
    - 验证流程实例状态
    - 验证审批历史记录
    - _需求：6.1, 6.2, 6.3, 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 20. 配置和文档
  - [ ] 20.1 配置 application.yml
    - 配置数据库连接
    - 配置 Camunda/Flowable
    - 配置日志级别
    - 配置事务隔离级别（READ_COMMITTED）
    - _需求：15.5_
  
  - [ ] 20.2 创建 API 文档
    - 添加 Swagger/OpenAPI 依赖
    - 配置 Swagger UI
    - 为所有端点添加 @Operation 注解
    - _需求：11.4_
  
  - [ ] 20.3 创建数据库迁移脚本
    - 创建 Flyway 或 Liquibase 迁移脚本
    - 定义所有表结构
    - 定义索引和约束
    - _需求：2.1, 3.1_

- [ ] 21. 最终检查点
  - 运行所有测试确保通过
  - 验证代码覆盖率达到目标（行覆盖率 80%，分支覆盖率 75%）
  - 验证所有 API 端点可访问
  - 验证所有正确性属性都有对应的测试
  - 如有问题请向用户询问。

## 注意事项

- 标记为 `*` 的任务是可选的测试任务，可以跳过以加快 MVP 开发
- 每个任务都引用了具体的需求编号，便于追溯
- 检查点任务确保增量验证
- 属性测试验证通用正确性属性
- 单元测试验证特定示例和边界情况
- 集成测试验证端到端流程
