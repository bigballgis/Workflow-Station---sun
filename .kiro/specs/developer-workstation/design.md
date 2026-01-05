# 开发者工作站设计文档

## 概述

开发者工作站是低代码工作流平台的核心开发工具，采用前后端分离架构。后端使用Java 17 + Spring Boot 3.x，前端使用Vue 3 + TypeScript + Element Plus，数据库使用PostgreSQL 16.5。

## 功能单元核心概念

### 功能单元组件关系

功能单元是平台的基本业务单位，包含完整的业务流程实现。每个功能单元由以下组件组成，它们之间存在紧密的关联关系：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           功能单元 (Function Unit)                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    工作流程 (Process) [1:1]                      │   │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐      │   │
│  │  │ 开始事件 │───▶│ 用户任务 │───▶│  网关   │───▶│ 结束事件 │      │   │
│  │  └─────────┘    └────┬────┘    └─────────┘    └─────────┘      │   │
│  │                      │                                          │   │
│  │                      │ 绑定 (多个表单、多个动作)                  │   │
│  │                      ▼                                          │   │
│  │              ┌───────────────┐                                  │   │
│  │              │ 表单[] + 动作[]│                                  │   │
│  │              └───────────────┘                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      数据表 (Tables) [1:N]                       │   │
│  │  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │   │
│  │  │   主表      │◀───▶│   子表      │     │  动作表     │       │   │
│  │  │ (Main)     │ 1:N │ (Sub) *N   │     │ (Action) *N│       │   │
│  │  └──────┬──────┘     └─────────────┘     └──────┬──────┘       │   │
│  │         │                                        │              │   │
│  │         │ 数据绑定                               │ 数据绑定      │   │
│  │         ▼                                        ▼              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      表单 (Forms) [1:N]                          │   │
│  │  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │   │
│  │  │  主表单     │     │  子表单     │     │  动作表单    │       │   │
│  │  │ (Main Form)│     │ (Sub Form) │     │(Action Form)│       │   │
│  │  │    *N      │     │    *N      │     │    *N       │       │   │
│  │  └─────────────┘     └─────────────┘     └─────────────┘       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      动作 (Actions) [1:N]                        │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │   │
│  │  │ 默认动作    │  │ 自定义动作   │  │ 组合动作    │             │   │
│  │  │ 同意/拒绝   │  │ API调用     │  │ 多步骤     │             │   │
│  │  │ 转办/委托   │  │ 表单弹出 *N │  │    *N      │             │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

关系说明:
- 功能单元 : 流程 = 1:1 (每个功能单元只有一个流程定义)
- 功能单元 : 表 = 1:N (每个功能单元可以有多张表)
- 功能单元 : 表单 = 1:N (每个功能单元可以有多个表单)
- 功能单元 : 动作 = 1:N (每个功能单元可以有多个动作)
- 流程用户任务 : 表单 = 1:N (每个用户任务可以绑定多个表单)
- 流程用户任务 : 动作 = 1:N (每个用户任务可以绑定多个动作)
- 表 : 表单 = 1:N (一张表可以被多个表单绑定)
```

### 组件关系说明

#### 1. 流程与表单的关系
- **用户任务绑定表单**: 每个用户任务节点可以绑定一个表单，用于数据录入和展示
- **表单类型**: 
  - 主表单：绑定到主表，用于业务数据录入
  - 动作表单：绑定到动作表，用于收集动作执行时的额外信息

#### 2. 流程与动作的关系
- **用户任务绑定动作**: 每个用户任务节点可以绑定多个动作
- **动作触发流程流转**: 用户执行动作后，流程根据动作结果流转到下一个节点
- **动作类型**:
  - 默认动作：同意、拒绝、转办、委托、回退、撤回
  - 自定义动作：API调用、表单弹出、脚本执行

#### 3. 表与表单的关系
- **数据绑定**: 表单字段与数据表字段建立映射关系
- **自动生成**: 可以根据表结构自动生成表单字段
- **验证规则**: 表单验证规则可以从表约束自动推导

#### 4. 表之间的关系
- **主表**: 每个功能单元有且仅有一张主表，存储核心业务数据
- **子表**: 与主表形成一对多关系，存储明细数据
- **动作表**: 存储动作执行时收集的数据，与主表关联

### 数据流转示例

```
用户发起流程
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 填写主表单 (绑定主表)                                      │
│    - 表单字段 → 主表字段                                      │
│    - 子表单 → 子表 (一对多)                                   │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. 提交动作 (触发流程流转)                                    │
│    - 执行"提交"动作                                          │
│    - 数据保存到主表和子表                                     │
│    - 流程流转到下一个节点                                     │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 审批任务 (用户任务节点)                                    │
│    - 显示主表单 (只读模式)                                    │
│    - 显示可用动作: 同意、拒绝、转办                           │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. 执行审批动作                                              │
│    - 选择"同意"动作                                          │
│    - 弹出动作表单 (绑定动作表)                                │
│    - 填写审批意见                                            │
│    - 数据保存到动作表                                        │
│    - 流程流转到下一个节点                                     │
└─────────────────────────────────────────────────────────────┘
```

## 架构

### 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    开发者工作站前端 (Vue 3)                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 流程设计器 │ │ 表设计器  │ │ 表单设计器 │ │ 动作设计器 │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ 功能单元  │ │ 图标库   │ │ 版本管理  │ │ 帮助系统  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
└─────────────────────────────────────────────────────────────────┘
                              │ REST API
┌─────────────────────────────────────────────────────────────────┐
│                    开发者工作站后端 (Spring Boot)                 │
├─────────────────────────────────────────────────────────────────┤
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐       │
│  │ FunctionUnit   │ │ ProcessDesign  │ │ TableDesign    │       │
│  │ Component      │ │ Component      │ │ Component      │       │
│  └────────────────┘ └────────────────┘ └────────────────┘       │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐       │
│  │ FormDesign     │ │ ActionDesign   │ │ IconLibrary    │       │
│  │ Component      │ │ Component      │ │ Component      │       │
│  └────────────────┘ └────────────────┘ └────────────────┘       │
│  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐       │
│  │ Version        │ │ Security       │ │ Export/Import  │       │
│  │ Component      │ │ Component      │ │ Component      │       │
│  └────────────────┘ └────────────────┘ └────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL 16.5 数据库                        │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

**后端:**
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL 16.5
- jqwik (属性测试)

**前端:**
- Vue 3 + TypeScript
- Element Plus
- Vite
- Pinia (状态管理)
- Vue I18n (国际化)
- bpmn-js (BPMN流程设计器)
- form-create (表单设计器)

## 组件和接口

### 后端组件

#### FunctionUnitComponent
负责功能单元的CRUD操作和生命周期管理。

```java
public interface FunctionUnitComponent {
    FunctionUnit create(FunctionUnitRequest request);
    FunctionUnit update(Long id, FunctionUnitRequest request);
    void delete(Long id);
    FunctionUnit getById(Long id);
    Page<FunctionUnit> list(FunctionUnitQuery query, Pageable pageable);
    FunctionUnit publish(Long id, String changeLog);
    FunctionUnit clone(Long id, String newName);
    ValidationResult validate(Long id);
}
```

#### ProcessDesignComponent
负责BPMN流程设计和验证。

```java
public interface ProcessDesignComponent {
    ProcessDefinition save(Long functionUnitId, String bpmnXml);
    ProcessDefinition getByFunctionUnitId(Long functionUnitId);
    ValidationResult validate(String bpmnXml);
    SimulationResult simulate(String bpmnXml, Map<String, Object> variables);
    String generateBpmnXml(ProcessDefinition definition);
}
```

#### TableDesignComponent
负责数据表结构设计和DDL生成。

```java
public interface TableDesignComponent {
    TableDefinition create(Long functionUnitId, TableDefinitionRequest request);
    TableDefinition update(Long id, TableDefinitionRequest request);
    void delete(Long id);
    List<TableDefinition> getByFunctionUnitId(Long functionUnitId);
    String generateDDL(Long id, DatabaseDialect dialect);
    DDLTestResult testDDL(Long id, DatabaseDialect dialect);
    ValidationResult validateRelationships(Long functionUnitId);
}
```

#### FormDesignComponent
负责表单设计和配置生成。

```java
public interface FormDesignComponent {
    FormDefinition create(Long functionUnitId, FormDefinitionRequest request);
    FormDefinition update(Long id, FormDefinitionRequest request);
    void delete(Long id);
    List<FormDefinition> getByFunctionUnitId(Long functionUnitId);
    String generateFormConfig(Long id);
    ValidationResult validate(Long id);
}
```

#### ActionDesignComponent
负责业务动作配置。

```java
public interface ActionDesignComponent {
    ActionDefinition create(Long functionUnitId, ActionDefinitionRequest request);
    ActionDefinition update(Long id, ActionDefinitionRequest request);
    void delete(Long id);
    List<ActionDefinition> getByFunctionUnitId(Long functionUnitId);
    ActionTestResult test(Long id, Map<String, Object> parameters);
}
```

#### IconLibraryComponent
负责图标资源管理。

```java
public interface IconLibraryComponent {
    Icon upload(MultipartFile file, IconMetadata metadata);
    void delete(Long id);
    Icon getById(Long id);
    Page<Icon> list(IconQuery query, Pageable pageable);
    byte[] getIconData(Long id, IconSize size);
}
```

#### VersionComponent
负责版本控制和变更跟踪。

```java
public interface VersionComponent {
    Version createVersion(Long functionUnitId, String changeLog);
    List<Version> getVersionHistory(Long functionUnitId);
    VersionDiff compare(Long versionId1, Long versionId2);
    FunctionUnit rollback(Long functionUnitId, Long versionId);
    byte[] exportVersion(Long versionId);
}
```

#### ExportImportComponent
负责功能单元的导入导出。

```java
public interface ExportImportComponent {
    byte[] export(Long functionUnitId);
    ImportResult importPackage(MultipartFile file, ImportOptions options);
    ValidationResult validatePackage(MultipartFile file);
}
```

### 前端组件

#### 流程设计器 (ProcessDesigner.vue)
- 每个功能单元只有一个流程定义
- 集成bpmn-js库
- 左侧BPMN元素工具箱
- 中央画布区域
- 右侧属性配置面板
- 支持拖拽、连接、缩放操作

#### 表设计器 (TableDesigner.vue)
- **初始页面为表列表视图**（一个功能单元可以有多张表）
- 支持创建主表、子表、动作表
- 点击表名进入表编辑界面
- 表编辑界面：类似Excel的表格编辑界面
- 字段类型选择器
- 外键关系配置
- DDL预览和测试

#### 表单设计器 (FormDesigner.vue)
- **初始页面为表单列表视图**（一个功能单元可以有多个表单）
- 支持创建主表单、动作表单
- 点击表单名进入表单编辑界面
- 表单编辑界面：集成form-create设计器
- 组件库面板
- 拖拽式布局
- 属性配置面板
- 实时预览

#### 动作设计器 (ActionDesigner.vue)
- **初始页面为动作列表视图**（一个功能单元可以有多个动作）
- 显示默认动作（同意、拒绝、转办等）和自定义动作
- 点击动作名进入动作编辑界面
- 动作类型选择
- 参数配置
- 流程步骤绑定
- 测试执行

## 数据模型

### 功能单元 (FunctionUnit)
```sql
CREATE TABLE dw_function_units (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_version VARCHAR(20),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP,
    CONSTRAINT fk_icon FOREIGN KEY (icon_id) REFERENCES dw_icons(id)
);
```

### 流程定义 (ProcessDefinition)
```sql
CREATE TABLE dw_process_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    bpmn_xml TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);
```

### 表定义 (TableDefinition)
```sql
CREATE TABLE dw_table_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    table_type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);

CREATE TABLE dw_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    length INTEGER,
    precision INTEGER,
    scale INTEGER,
    nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(500),
    is_primary_key BOOLEAN DEFAULT FALSE,
    is_unique BOOLEAN DEFAULT FALSE,
    description TEXT,
    sort_order INTEGER NOT NULL,
    CONSTRAINT fk_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id)
);

CREATE TABLE dw_foreign_keys (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    ref_table_id BIGINT NOT NULL,
    ref_field_id BIGINT NOT NULL,
    on_delete VARCHAR(20) DEFAULT 'NO ACTION',
    on_update VARCHAR(20) DEFAULT 'NO ACTION',
    CONSTRAINT fk_table FOREIGN KEY (table_id) REFERENCES dw_table_definitions(id),
    CONSTRAINT fk_field FOREIGN KEY (field_id) REFERENCES dw_field_definitions(id),
    CONSTRAINT fk_ref_table FOREIGN KEY (ref_table_id) REFERENCES dw_table_definitions(id),
    CONSTRAINT fk_ref_field FOREIGN KEY (ref_field_id) REFERENCES dw_field_definitions(id)
);
```

### 表单定义 (FormDefinition)
```sql
CREATE TABLE dw_form_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    form_name VARCHAR(100) NOT NULL,
    form_type VARCHAR(20) NOT NULL,
    config_json JSONB NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);
```

### 动作定义 (ActionDefinition)
```sql
CREATE TABLE dw_action_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    config_json JSONB NOT NULL,
    icon VARCHAR(50),
    button_color VARCHAR(20),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);
```

### 图标 (Icon)
```sql
CREATE TABLE dw_icons (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_data BYTEA NOT NULL,
    file_size INTEGER NOT NULL,
    width INTEGER,
    height INTEGER,
    tags VARCHAR(500),
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 版本 (Version)
```sql
CREATE TABLE dw_versions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    version_number VARCHAR(20) NOT NULL,
    change_log TEXT,
    snapshot_data BYTEA NOT NULL,
    published_by VARCHAR(50) NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_function_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id)
);
```

### 操作日志 (OperationLog)
```sql
CREATE TABLE dw_operation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id BIGINT,
    resource_name VARCHAR(200),
    details JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 正确性属性

*正确性属性是系统在所有有效执行中应保持为真的特征或行为——本质上是关于系统应该做什么的形式化陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: 功能单元名称唯一性
*对于任意*功能单元创建或更新操作，如果名称已存在于系统中，则操作应被拒绝并返回错误；如果名称不存在，则操作应成功。
**Validates: Requirements 1.2, 1.3**

### Property 2: 功能单元发布状态一致性
*对于任意*功能单元发布操作，发布成功后状态应更新为"已发布"，且版本号应递增，版本历史应包含新版本记录。
**Validates: Requirements 1.6, 7.1**

### Property 3: 功能单元克隆完整性
*对于任意*功能单元克隆操作，克隆后的功能单元应包含原功能单元的所有组件（流程、表、表单、动作），且数据内容应与原功能单元一致。
**Validates: Requirements 1.9**

### Property 4: BPMN流程验证一致性
*对于任意*BPMN流程定义，保存后生成的BPMN XML应能被正确解析，且解析后的流程结构应与原始定义一致（往返一致性）。
**Validates: Requirements 2.7**

### Property 5: BPMN流程错误检测
*对于任意*无效的BPMN流程定义（如缺少开始/结束节点、存在孤立节点），验证应返回错误，且错误信息应指向具体的问题元素。
**Validates: Requirements 2.8**

### Property 6: 表结构循环依赖检测
*对于任意*表外键关系配置，如果配置会导致循环依赖，则应被检测并阻止；如果不会导致循环依赖，则应允许配置。
**Validates: Requirements 3.4**

### Property 7: DDL生成正确性
*对于任意*表定义和数据库方言组合，生成的DDL语句应符合该方言的语法规范，且在对应数据库中执行应成功创建表结构。
**Validates: Requirements 3.5, 3.6**

### Property 8: 表单配置往返一致性
*对于任意*有效的表单定义，保存后生成的JSON配置应能被正确解析，且解析后的表单结构应与原始定义一致。
**Validates: Requirements 4.8**

### Property 9: 表单数据绑定一致性
*对于任意*表单组件数据绑定配置，绑定的字段应存在于关联的数据表中，且字段类型应与组件类型兼容。
**Validates: Requirements 4.4**

### Property 10: 动作流程步骤绑定一致性
*对于任意*动作绑定到流程步骤的操作，绑定的步骤应存在于功能单元的流程定义中，且步骤类型应支持动作绑定。
**Validates: Requirements 5.5**

### Property 11: 图标文件验证
*对于任意*图标上传操作，如果文件格式不在允许列表（SVG、PNG、ICO）或大小超过2MB，则应被拒绝；否则应上传成功。
**Validates: Requirements 6.2**

### Property 12: 图标使用保护
*对于任意*图标删除操作，如果图标被功能单元使用，则应显示警告并要求确认；删除后，使用该图标的功能单元应更新为默认图标。
**Validates: Requirements 6.6**

### Property 13: 版本回滚一致性
*对于任意*版本回滚操作，回滚后的功能单元内容应与目标版本的快照内容一致，且应创建新的版本记录。
**Validates: Requirements 7.4**

### Property 14: 版本比较正确性
*对于任意*两个版本的比较操作，返回的差异应准确反映两个版本之间的实际变更。
**Validates: Requirements 7.3**

### Property 15: 导入导出往返一致性
*对于任意*功能单元，导出后再导入应产生与原功能单元内容一致的新功能单元（除了ID和时间戳等系统字段）。
**Validates: Requirements 12.1, 12.2**

### Property 16: 导入冲突检测
*对于任意*功能单元导入操作，如果导入的功能单元名称与现有功能单元冲突，则应检测到冲突并提供解决选项。
**Validates: Requirements 12.3**

### Property 17: JWT认证一致性
*对于任意*有效的用户凭据，登录应生成有效的JWT令牌；使用该令牌的API请求应被认证通过。
**Validates: Requirements 11.1**

### Property 18: 账户锁定机制
*对于任意*用户账户，连续5次登录失败后应被锁定；锁定期间的登录尝试应被拒绝。
**Validates: Requirements 11.2**

### Property 19: 权限访问控制
*对于任意*功能单元访问请求，如果用户没有访问权限，则应返回403错误；如果有权限，则应允许访问。
**Validates: Requirements 11.3**

### Property 20: 审计日志完整性
*对于任意*敏感操作（创建、更新、删除、发布），应记录审计日志，且日志应包含操作者、操作类型、操作时间、操作对象等信息。
**Validates: Requirements 11.4**

### Property 21: API错误响应一致性
*对于任意*API请求，如果缺少认证令牌应返回401；如果参数无效应返回400并包含详细错误信息。
**Validates: Requirements 13.3, 13.4**

### Property 22: API限流机制
*对于任意*API调用，如果超过频率限制，则应返回429状态码；未超过限制的请求应正常处理。
**Validates: Requirements 13.5**

### Property 23: 国际化语言切换
*对于任意*语言切换操作，界面文本应切换到目标语言，且所有可翻译的文本都应显示为目标语言。
**Validates: Requirements 9.4**

### Property 24: 表达式智能补全
*对于任意*表达式编辑器输入，智能补全应返回与输入前缀匹配的函数和变量列表，且列表应按相关性排序。
**Validates: Requirements 8.3**

### Property 25: 搜索结果相关性
*对于任意*图标或帮助内容搜索，返回的结果应包含搜索关键词或与关键词语义相关的内容。
**Validates: Requirements 6.4, 8.5**

## 错误处理

### 错误分类

| 错误类型 | HTTP状态码 | 错误码前缀 | 描述 |
|---------|-----------|-----------|------|
| 验证错误 | 400 | VAL_ | 输入数据验证失败 |
| 认证错误 | 401 | AUTH_ | 未认证或令牌无效 |
| 授权错误 | 403 | PERM_ | 无权限访问资源 |
| 资源不存在 | 404 | NOT_FOUND_ | 请求的资源不存在 |
| 冲突错误 | 409 | CONFLICT_ | 资源冲突（如名称重复） |
| 业务错误 | 422 | BIZ_ | 业务规则验证失败 |
| 限流错误 | 429 | RATE_LIMIT_ | 请求频率超限 |
| 系统错误 | 500 | SYS_ | 系统内部错误 |

### 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "VAL_INVALID_NAME",
    "message": "功能单元名称不能为空",
    "details": [
      {
        "field": "name",
        "message": "名称长度必须在1-100个字符之间"
      }
    ],
    "suggestion": "请输入有效的功能单元名称",
    "timestamp": "2026-01-05T10:30:00Z",
    "traceId": "abc123"
  }
}
```

### 关键错误场景

1. **功能单元名称重复**: 返回CONFLICT_NAME_EXISTS，建议使用其他名称
2. **BPMN流程验证失败**: 返回BIZ_INVALID_PROCESS，列出所有验证错误
3. **循环依赖检测**: 返回BIZ_CIRCULAR_DEPENDENCY，显示依赖链
4. **DDL执行失败**: 返回BIZ_DDL_ERROR，显示数据库错误信息和修复建议
5. **导入包格式错误**: 返回VAL_INVALID_PACKAGE，说明格式要求
6. **图标文件过大**: 返回VAL_FILE_TOO_LARGE，显示大小限制
7. **会话超时**: 返回AUTH_SESSION_EXPIRED，提示重新登录
8. **账户锁定**: 返回AUTH_ACCOUNT_LOCKED，显示解锁时间

## 测试策略

### 测试框架

- **后端单元测试**: JUnit 5
- **后端属性测试**: jqwik (每个属性测试运行20次迭代)
- **前端单元测试**: Vitest
- **前端组件测试**: Vue Test Utils
- **E2E测试**: Playwright

### 属性测试配置

```java
@Property(tries = 20)
@Tag("Feature: developer-workstation, Property N: property_description")
void propertyTest(@ForAll ... inputs) {
    // 属性测试实现
}
```

### 测试覆盖要求

| 组件 | 单元测试覆盖率 | 属性测试 |
|-----|--------------|---------|
| FunctionUnitComponent | ≥80% | Property 1-3 |
| ProcessDesignComponent | ≥80% | Property 4-5 |
| TableDesignComponent | ≥80% | Property 6-7 |
| FormDesignComponent | ≥80% | Property 8-9 |
| ActionDesignComponent | ≥80% | Property 10 |
| IconLibraryComponent | ≥80% | Property 11-12 |
| VersionComponent | ≥80% | Property 13-14 |
| ExportImportComponent | ≥80% | Property 15-16 |
| SecurityComponent | ≥80% | Property 17-20 |
| API层 | ≥80% | Property 21-22 |

### 测试数据生成策略

1. **功能单元生成器**: 生成随机名称、描述、图标ID
2. **BPMN流程生成器**: 生成有效和无效的BPMN XML
3. **表定义生成器**: 生成各种字段类型和关系组合
4. **表单配置生成器**: 生成各种组件类型和配置
5. **用户凭据生成器**: 生成有效和无效的用户名密码组合

### 关键测试场景

1. **功能单元CRUD**: 创建、读取、更新、删除的完整流程
2. **BPMN流程设计**: 元素创建、连接、验证、保存
3. **表结构设计**: 字段添加、外键配置、DDL生成
4. **表单设计**: 组件拖拽、数据绑定、配置保存
5. **版本管理**: 发布、回滚、比较、导出
6. **导入导出**: 导出、导入、冲突处理
7. **安全认证**: 登录、令牌验证、权限检查、账户锁定
