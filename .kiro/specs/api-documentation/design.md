# API 文档梳理 - 设计文档

## 1. 设计概述

### 1.1 目标
创建一份完整的API文档，梳理工作流平台中所有前端应用调用的后端API端点，包括功能说明、数据库操作、请求/响应格式等详细信息。

### 1.2 范围
- **前端应用**: Admin Center (3000), User Portal (3001), Developer Workstation (3002)
- **后端服务**: API Gateway (8080), Workflow Engine Core (8081), User Portal Backend (8082), Developer Workstation Backend (8083), Admin Center Backend (8090)
- **文档内容**: API端点清单、功能说明、数据库操作、请求/响应格式、模块归属

### 1.3 设计原则
1. **完整性**: 覆盖所有前端调用的API端点
2. **准确性**: 基于实际代码分析，确保信息准确
3. **可维护性**: 使用清晰的分类和格式，便于后续更新
4. **实用性**: 包含开发者需要的所有关键信息

## 2. 架构设计

### 2.1 文档结构设计

```
API 文档
├── 1. 认证授权 API
├── 2. 用户管理 API (Admin Center)
├── 3. 角色管理 API (Admin Center)
├── 4. 组织架构 API (Admin Center)
├── 5. 虚拟组 API (Admin Center)
├── 6. 功能单元 API (Developer Workstation)
├── 7. 表设计 API (Developer Workstation)
├── 8. 表单设计 API (Developer Workstation)
├── 9. 动作设计 API (Developer Workstation)
├── 10. 流程设计 API (Developer Workstation)
├── 11. 图标库 API (Developer Workstation)
├── 12. 任务管理 API (User Portal)
├── 13. 流程管理 API (User Portal)
├── 14. 委托管理 API (User Portal)
├── 15. 权限申请 API (User Portal)
├── 16. 工作流引擎 API (Workflow Engine Core)
├── 17. 安全审计 API (Admin Center)
├── 18. 系统监控 API (Admin Center)
└── 19. 数据字典 API (Admin Center)
```

### 2.2 API 信息模板

每个API端点包含以下信息：

```markdown
### X.Y API名称
- **端点**: HTTP方法 /api路径
- **服务**: 后端服务名称 (端口)
- **功能**: 功能描述
- **数据库操作**: 是/否
  - 操作表: 表名列表
  - 操作类型: SELECT/INSERT/UPDATE/DELETE
- **请求参数**:
  - 参数名 (类型): 说明
- **响应格式**:
  ```json
  {
    "field": "type - description"
  }
  ```
- **权限要求**: 所需角色/权限
```

### 2.3 数据库表分类

| 表前缀 | 模块 | 说明 |
|--------|------|------|
| `dw_*` | Developer Workstation | 开发工作站相关表 |
| `sys_*` | Platform Security | 系统安全相关表 |
| `admin_*` | Admin Center | 管理中心相关表 |
| `act_*` | Workflow Engine | Flowable内置表 |
| `extended_*` | Workflow Engine | 扩展任务信息表 |
| `delegation_*` | Workflow Engine | 委托相关表 |

## 3. 详细设计

### 3.1 API 分析方法

#### 3.1.1 前端API调用分析
1. **扫描前端API文件**: `frontend/*/src/api/*.ts`
2. **识别API端点**: 提取所有HTTP请求的URL、方法、参数
3. **追踪后端路由**: 根据URL匹配后端Controller

#### 3.1.2 后端Controller分析
1. **扫描Controller文件**: `backend/*/src/main/java/com/*/controller/*.java`
2. **提取端点信息**: @RequestMapping, @GetMapping, @PostMapping等
3. **分析Service层**: 追踪业务逻辑和数据库操作
4. **识别Repository操作**: 确定操作的数据库表和操作类型

#### 3.1.3 数据库操作分析
1. **JPA Repository**: 分析自定义查询和方法名推断
2. **@Query注解**: 提取JPQL/SQL语句
3. **Service层逻辑**: 识别事务和多表操作

### 3.2 API 分类设计

#### 3.2.1 认证授权 API
- **服务**: API Gateway (8080) / Platform Security
- **核心功能**: 登录、登出、Token管理、密码修改
- **数据库表**: `sys_users`, `sys_roles`, `sys_permissions`

#### 3.2.2 用户管理 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 用户CRUD、状态管理、角色分配、业务单元管理
- **数据库表**: `sys_users`, `sys_business_unit_members`, `sys_virtual_group_members`

#### 3.2.3 角色管理 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 角色CRUD、权限管理、成员管理
- **数据库表**: `sys_roles`, `sys_permissions`, `sys_virtual_group_roles`

#### 3.2.4 组织架构 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 业务单元树查询、CRUD、成员管理、角色绑定
- **数据库表**: `sys_business_units`, `sys_business_unit_members`, `sys_business_unit_roles`

#### 3.2.5 虚拟组 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 虚拟组CRUD、成员管理、角色绑定
- **数据库表**: `sys_virtual_groups`, `sys_virtual_group_members`, `sys_virtual_group_roles`

#### 3.2.6 功能单元 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 功能单元CRUD、发布、克隆、验证、导出
- **数据库表**: `dw_function_units`, `sys_function_units`, `sys_function_unit_contents`

#### 3.2.7 表设计 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 表定义CRUD、字段管理、外键关系、DDL生成
- **数据库表**: `dw_table_definitions`, `dw_field_definitions`, `dw_foreign_keys`

#### 3.2.8 表单设计 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 表单定义CRUD、表绑定、配置管理
- **数据库表**: `dw_form_definitions`, `dw_form_table_bindings`

#### 3.2.9 动作设计 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 动作定义CRUD、测试
- **数据库表**: `dw_action_definitions`

#### 3.2.10 流程设计 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 流程定义保存/获取、验证、模拟
- **数据库表**: `dw_process_definitions`

#### 3.2.11 图标库 API (Developer Workstation)
- **服务**: Developer Workstation Backend (8083)
- **核心功能**: 图标上传/查询/删除、分类/标签管理
- **数据库表**: `dw_icons`

#### 3.2.12 任务管理 API (User Portal)
- **服务**: User Portal Backend (8082) + Workflow Engine Core (8081)
- **核心功能**: 任务查询、认领、完成、委托、转办
- **数据库表**: `act_ru_task`, `extended_task_info`, `delegation_rules`

#### 3.2.13 流程管理 API (User Portal)
- **服务**: User Portal Backend (8082) + Workflow Engine Core (8081)
- **核心功能**: 流程启动、撤回、催办、草稿管理
- **数据库表**: `act_ru_execution`, `act_hi_procinst`, `sys_function_unit_contents`

#### 3.2.14 委托管理 API (User Portal)
- **服务**: User Portal Backend (8082)
- **核心功能**: 委托规则CRUD、暂停/恢复、审计记录
- **数据库表**: `delegation_rules`, `delegation_audit`

#### 3.2.15 权限申请 API (User Portal)
- **服务**: User Portal Backend (8082) + Admin Center Backend (8090)
- **核心功能**: 角色/虚拟组查询、权限申请、审批
- **数据库表**: `sys_roles`, `sys_virtual_groups`, `sys_virtual_group_members`

#### 3.2.16 工作流引擎 API (Workflow Engine Core)
- **服务**: Workflow Engine Core (8081)
- **核心功能**: 流程部署、实例管理、任务管理、历史查询
- **数据库表**: Flowable内置表 (`act_*`)

#### 3.2.17 安全审计 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 安全策略配置、审计日志查询、异常检测
- **数据库表**: `admin_audit_logs`, `admin_security_policies`

#### 3.2.18 系统监控 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 系统指标查询、告警规则管理
- **数据库表**: 监控相关表（待确认）

#### 3.2.19 数据字典 API (Admin Center)
- **服务**: Admin Center Backend (8090)
- **核心功能**: 字典CRUD、字典项查询
- **数据库表**: `admin_dictionaries`, `admin_dictionary_items`

### 3.3 文档生成流程

```
1. 代码扫描
   ├── 扫描前端API文件
   ├── 扫描后端Controller
   └── 扫描Repository和Service

2. 信息提取
   ├── 提取API端点信息
   ├── 分析数据库操作
   └── 提取请求/响应格式

3. 信息整合
   ├── 按模块分类
   ├── 匹配前后端API
   └── 补充数据库操作信息

4. 文档生成
   ├── 生成Markdown文档
   ├── 添加目录和索引
   └── 格式化和美化
```

## 4. 实现策略

### 4.1 分析工具选择

#### 4.1.1 手动分析
- **优点**: 准确、可控、理解深入
- **缺点**: 耗时、容易遗漏
- **适用**: 核心API、复杂逻辑

#### 4.1.2 脚本辅助
- **优点**: 快速、全面
- **缺点**: 需要编写脚本、可能不够准确
- **适用**: 大量简单API、初步扫描

#### 4.1.3 混合方式（推荐）
1. 使用脚本快速扫描所有API端点
2. 手动分析核心API的详细信息
3. 补充数据库操作和业务逻辑说明

### 4.2 实现步骤

#### 阶段1: 前端API扫描
1. 扫描所有前端API文件
2. 提取API调用信息（URL、方法、参数）
3. 按模块分类整理

#### 阶段2: 后端Controller分析
1. 扫描所有后端Controller
2. 提取端点定义（路径、方法、参数）
3. 匹配前端API调用

#### 阶段3: 数据库操作分析
1. 分析Repository和Service层
2. 识别数据库表和操作类型
3. 补充到API文档中

#### 阶段4: 文档生成
1. 按分类生成API文档
2. 添加请求/响应示例
3. 补充权限和注意事项

#### 阶段5: 审查和完善
1. 审查文档完整性
2. 验证API信息准确性
3. 补充遗漏的API

### 4.3 文档维护策略

1. **版本控制**: 文档随代码一起版本管理
2. **自动化**: 考虑使用工具自动生成部分文档
3. **定期更新**: 每次API变更时同步更新文档
4. **审查机制**: 代码审查时检查文档更新

## 5. 输出格式

### 5.1 主文档结构

```markdown
# 工作流平台 API 文档

## 目录
[自动生成的目录]

## 1. 认证授权 API

### 1.1 用户登录
- **端点**: POST /api/v1/auth/login
- **服务**: API Gateway (8080)
- **功能**: 用户登录认证，返回JWT Token
- **数据库操作**: 是
  - 操作表: sys_users
  - 操作类型: SELECT
- **请求参数**:
  - username (String): 用户名
  - password (String): 密码
- **响应格式**:
  ```json
  {
    "token": "JWT Token",
    "user": {
      "id": "用户ID",
      "username": "用户名",
      "fullName": "全名"
    }
  }
  ```
- **权限要求**: 无（公开接口）

[更多API...]

## 附录

### A. 数据库表清单
[所有表的列表和说明]

### B. 枚举类型定义
[所有枚举类型的定义]

### C. 错误码说明
[错误码和说明]
```

### 5.2 辅助文档

#### 5.2.1 API索引表
按字母顺序列出所有API端点，便于快速查找

#### 5.2.2 数据库表关系图
展示表之间的关联关系

#### 5.2.3 服务依赖图
展示前后端服务的调用关系

## 6. 验收标准

### 6.1 完整性检查
- [ ] 所有前端API调用都已记录
- [ ] 所有后端Controller端点都已记录
- [ ] 每个API都包含完整的信息（端点、功能、数据库操作、请求/响应）

### 6.2 准确性检查
- [ ] API端点路径正确
- [ ] 数据库表名正确
- [ ] 请求/响应格式与实际代码一致

### 6.3 可用性检查
- [ ] 文档结构清晰，易于导航
- [ ] 分类合理，便于查找
- [ ] 包含足够的示例和说明

### 6.4 维护性检查
- [ ] 文档格式统一
- [ ] 使用Markdown格式，便于版本控制
- [ ] 包含更新日期和版本信息

## 7. 风险和挑战

### 7.1 技术风险
- **API数量庞大**: 可能遗漏部分API
  - 缓解: 使用脚本辅助扫描，多次审查
- **代码复杂**: 难以准确分析数据库操作
  - 缓解: 重点分析核心API，其他API标注"待确认"

### 7.2 维护风险
- **文档过时**: API变更后文档未更新
  - 缓解: 建立文档更新流程，代码审查时检查
- **信息不一致**: 文档与实际代码不符
  - 缓解: 定期验证文档准确性

## 8. 后续优化

### 8.1 自动化工具
- 开发API文档生成工具
- 集成到CI/CD流程
- 自动检测API变更

### 8.2 交互式文档
- 使用Swagger/OpenAPI规范
- 提供在线API测试功能
- 集成到开发者门户

### 8.3 文档增强
- 添加API使用示例
- 添加常见问题解答
- 添加最佳实践指南

## 9. 参考资料

### 9.1 代码位置
- 前端API: `frontend/*/src/api/*.ts`
- 后端Controller: `backend/*/src/main/java/com/*/controller/*.java`
- Repository: `backend/*/src/main/java/com/*/repository/*.java`
- Service: `backend/*/src/main/java/com/*/service/*.java`

### 9.2 相关文档
- 系统架构文档: `docs/PLATFORM_MODULES_ARCHITECTURE.md`
- 数据库设计文档: `docs/database-refactor-plan.md`
- 工作流引擎架构: `.kiro/steering/workflow-engine-architecture.md`

## 10. 正确性属性

### 10.1 完整性属性
**属性 10.1.1**: 所有前端API调用都在文档中有对应记录
- **验证方法**: 扫描所有前端API文件，检查每个API调用是否在文档中存在
- **测试策略**: 编写脚本提取所有前端API调用，与文档中的API列表对比

**属性 10.1.2**: 所有后端Controller端点都在文档中有对应记录
- **验证方法**: 扫描所有后端Controller，检查每个端点是否在文档中存在
- **测试策略**: 编写脚本提取所有Controller端点，与文档中的API列表对比

### 10.2 准确性属性
**属性 10.2.1**: 文档中的API端点路径与实际代码一致
- **验证方法**: 对比文档中的端点路径与Controller中的@RequestMapping注解
- **测试策略**: 随机抽样验证，确保至少95%的端点路径准确

**属性 10.2.2**: 文档中的数据库操作信息与实际代码一致
- **验证方法**: 对比文档中的数据库表与Repository/Service中的实际操作
- **测试策略**: 重点验证核心API的数据库操作，确保准确性

### 10.3 一致性属性
**属性 10.3.1**: 文档格式在所有API条目中保持一致
- **验证方法**: 检查每个API条目是否包含所有必需字段（端点、服务、功能、数据库操作、请求参数、响应格式）
- **测试策略**: 编写脚本验证文档格式的一致性

**属性 10.3.2**: 同一API在不同部分的描述保持一致
- **验证方法**: 检查API在目录、正文、索引中的描述是否一致
- **测试策略**: 交叉引用验证，确保信息一致

### 10.4 可维护性属性
**属性 10.4.1**: 文档使用标准Markdown格式，便于版本控制
- **验证方法**: 检查文档是否符合Markdown语法规范
- **测试策略**: 使用Markdown linter工具验证

**属性 10.4.2**: 文档包含版本信息和更新日期
- **验证方法**: 检查文档头部是否包含版本号和最后更新日期
- **测试策略**: 手动检查文档元数据

## 11. 测试策略

### 11.1 单元测试
- 不适用（文档项目）

### 11.2 集成测试
- 验证文档中的API端点是否可访问
- 验证请求/响应格式是否与文档一致

### 11.3 验收测试
- 开发者审查文档完整性和准确性
- 使用文档进行实际API调用测试

### 11.4 属性测试
- 使用脚本验证完整性属性（10.1.1, 10.1.2）
- 使用脚本验证一致性属性（10.3.1）
- 手动抽样验证准确性属性（10.2.1, 10.2.2）
