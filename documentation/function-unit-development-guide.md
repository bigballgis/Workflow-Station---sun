# 功能单元 (Function Unit) 完整开发文档

> 本文档面向 AI 助手和开发者，详尽描述功能单元模块的架构、实体关系、API、数据流、枚举、配置和约定。  
> **关联**：工作区访问控制（拦截器 / 虚拟组）见仓库 [docs/developer-workstation-workspace-rbac.md](../docs/developer-workstation-workspace-rbac.md)；数据库 **init-scripts 与 Flyway** 及 **Dev Compose 关闭 Flyway** 见 [docs/schema-and-migration.md](../docs/schema-and-migration.md)。  
> 最后更新: 2026-04-08（与 `backend/developer-workstation` 源码清点）

---

## 目录

1. [概述](#1-概述)
2. [分层架构](#2-分层架构)
3. [实体关系模型 (ERD)](#3-实体关系模型)
4. [实体详解](#4-实体详解)
5. [枚举类型](#5-枚举类型)
6. [DTO 体系](#6-dto-体系)
7. [API 端点](#7-api-端点)
8. [核心数据流](#8-核心数据流)
9. [版本管理与快照](#9-版本管理与快照)
10. [部署流程](#10-部署流程)
11. [AI 生成模块](#11-ai-生成模块)
12. [导入导出](#12-导入导出)
13. [安全模型](#13-安全模型)
14. [国际化 (i18n)](#14-国际化)
15. [配置属性](#15-配置属性)
16. [数据库迁移 (Flyway)](#16-数据库迁移)
17. [已知限制与技术债务](#17-已知限制与技术债务)

---

## 1. 概述

功能单元 (Function Unit) 是低代码工作流平台的核心设计实体。一个功能单元封装了完整的业务流程定义，包含：

- **表设计** (Table Design) — 数据模型定义
- **表单设计** (Form Design) — 用户交互界面
- **动作设计** (Action Design) — 按钮/操作行为
- **流程设计** (Process Design) — BPMN 工作流
- **版本管理** (Version) — 发布快照与历史
- **图标** (Icon) — 可视化标识

功能单元的生命周期: `DRAFT` → `PUBLISHED` → 可选 `ARCHIVED`。每次发布会创建版本快照，支持克隆、导出、一键部署到 admin-center。

所属模块: `developer-workstation` (context-path: `/api/v1`)

---

## 2. 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                    Controller 层                         │
│  继承 BaseController，使用 handleRequest() 统一响应       │
│  职责: 路由 + 参数校验 (@Valid) + 权限注解                │
├─────────────────────────────────────────────────────────┤
│                  Component 接口层                        │
│  定义业务操作契约 (component/ 包)                         │
├─────────────────────────────────────────────────────────┤
│                Component Impl 实现层                     │
│  业务编排逻辑 (component/impl/ 包)                       │
│  注入 Service、Repository，处理事务                       │
├─────────────────────────────────────────────────────────┤
│                   Service 层                             │
│  领域逻辑 (service/ + service/impl/)                     │
├─────────────────────────────────────────────────────────┤
│                  Repository 层                           │
│  Spring Data JPA 接口 (repository/ 包)                   │
└─────────────────────────────────────────────────────────┘
```

### 包结构 (developer-workstation)

```
com.developer/
├── client/          # 跨服务调用 (admin-center REST client)
├── component/       # 业务组件接口
│   └── impl/        # 业务组件实现
├── config/          # Spring 配置 (CORS, Jackson, Async, OpenAPI)
├── controller/      # REST 控制器：22 个具体类 + BaseController（数量随迭代变化，以 controller/ 目录为准）
├── dto/             # 请求/响应 DTO（约 48 个 Java 文件，含子包）
├── entity/          # JPA 实体（约 26 个 Java 文件，含决策/关联表/部署任务等）
├── enums/           # 枚举类型 (14 个)
├── exception/       # 自定义异常
├── repository/      # JPA Repository（约 29 个接口，见附录 A）
├── resilience/      # 弹性/重试逻辑
├── security/        # JWT 认证、权限注解
├── service/         # 服务接口
│   └── impl/        # 服务实现
├── util/            # 工具类
└── validation/      # 自定义校验器
```

### BaseController 统一响应模式

```java
// 所有继承 BaseController 的控制器使用此模式:
@PostMapping
public ResponseEntity<ApiResponse<Entity>> create(@Valid @RequestBody Request req) {
    return handleRequest(() -> component.create(req));
}

// BaseController 内部处理:
// 1. 调用 processor.process()
// 2. 成功 → ApiResponse.success(result)
// 3. IllegalArgumentException → 400 + VAL_INVALID_INPUT
// 4. SecurityException → 403 + SEC_ACCESS_DENIED
// 5. 其他异常 → 500 + SYS_REQUEST_PROCESSING_ERROR (不暴露内部细节)
// 附加功能: SecurityInputValidator 输入校验, SecurityAuditLogger 审计日志
```

### 统一响应格式 (ApiResponse)

```json
{
  "success": true,
  "data": { ... },
  "error": null
}

// 错误时:
{
  "success": false,
  "data": null,
  "error": {
    "code": "CONFLICT_NAME_EXISTS",
    "message": "Function unit name already exists: xxx",
    "details": [...],
    "suggestion": "Please use a different name",
    "timestamp": "2026-03-24T10:00:00Z",
    "traceId": "uuid-string",
    "path": "/function-units"
  }
}
```

---

## 3. 实体关系模型

```
┌──────────────────────────────────────────────────────────────────┐
│                        FunctionUnit                              │
│  dw_function_units                                               │
│  PK: id (BIGINT AUTO)                                           │
│  code (VARCHAR 50, UNIQUE) — fu-{timestamp}-{random}            │
│  name (VARCHAR 100, UNIQUE)                                      │
│  status: DRAFT | PUBLISHED | ARCHIVED                            │
│  version (VARCHAR 20, default "1.0.0")                           │
│  currentVersion (VARCHAR 20)                                     │
│  isActive, enabled (BOOLEAN)                                     │
│  deployedAt (TIMESTAMP, nullable)                                │
│  lockVersion (乐观锁)                                            │
│  FK: icon_id → dw_icons(id)                                     │
│  FK: previous_version_id → dw_function_units(id) (自引用)        │
│  审计: createdBy, createdAt, updatedBy, updatedAt                │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──── 1:N ────┐  ┌──── 1:N ────┐  ┌──── 1:N ────┐            │
│  ▼              │  ▼              │  ▼              │            │
│ TableDefinition │ FormDefinition  │ ActionDefinition│            │
│ dw_table_       │ dw_form_        │ dw_action_      │            │
│ definitions     │ definitions     │ definitions     │            │
│                 │                 │                  │            │
│  ┌── 1:N ──┐   │  ┌── 1:N ──┐   │                  │            │
│  ▼          │   │  ▼          │   │                  │            │
│ FieldDef    │   │ FormTable   │   │                  │            │
│ dw_field_   │   │ Binding     │   │                  │            │
│ definitions │   │ dw_form_    │   │                  │            │
│             │   │ table_      │   │                  │            │
│  ┌── 1:N ──┘   │ bindings    │   │                  │            │
│  ▼              │             │   │                  │            │
│ ForeignKey      │             │   │                  │            │
│ dw_foreign_keys │             │   │                  │            │
└─────────────────┴─────────────┴───┴──────────────────┘            │
                                                                    │
│  ┌──── 1:1 ────┐  ┌──── 1:N ────┐  ┌──── N:1 ────┐             │
│  ▼              │  ▼              │  ▼              │             │
│ ProcessDef      │ Version         │ Icon            │             │
│ dw_process_     │ dw_versions     │ dw_icons        │             │
│ definitions     │                 │                  │             │
└─────────────────┴─────────────────┴──────────────────┘
```

### 关系总结

| 父实体 | 子实体 | 关系 | 级联 | 说明 |
|--------|--------|------|------|------|
| FunctionUnit | TableDefinition | 1:N | ALL + orphanRemoval | 表定义 |
| FunctionUnit | FormDefinition | 1:N | ALL + orphanRemoval | 表单定义 |
| FunctionUnit | ActionDefinition | 1:N | ALL + orphanRemoval | 动作定义 |
| FunctionUnit | ProcessDefinition | 1:1 | ALL + orphanRemoval | 流程定义 |
| FunctionUnit | Version | 1:N | ALL + orphanRemoval | 版本快照 |
| FunctionUnit | Icon | N:1 | 无级联 | 图标引用 |
| FunctionUnit | FunctionUnit | N:1 (自引用) | 无级联 | previousVersion |
| TableDefinition | FieldDefinition | 1:N | ALL + orphanRemoval | 字段定义, OrderBy sortOrder |
| TableDefinition | ForeignKey | 1:N | ALL + orphanRemoval | 外键关系 |
| FormDefinition | FormTableBinding | 1:N | ALL + orphanRemoval | 表绑定, OrderBy sortOrder |
| FormDefinition | TableDefinition | N:1 | 无级联 | boundTable (向后兼容) |
| FormTableBinding | TableDefinition | N:1 | 无级联 | 绑定的目标表 |
| ForeignKey | FieldDefinition | N:1 | 无级联 | 源字段 |
| ForeignKey | TableDefinition | N:1 | 无级联 | 引用表 |
| ForeignKey | FieldDefinition | N:1 | 无级联 | 引用字段 |

---

## 4. 实体详解

### 4.1 FunctionUnit (功能单元)

表名: `dw_function_units`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 唯一编码, 格式: `fu-{yyyyMMdd}-{6位随机}` |
| name | VARCHAR(100) | UNIQUE, NOT NULL | 功能单元名称 |
| description | TEXT | nullable | 描述 |
| icon_id | BIGINT | FK → dw_icons | 图标引用 |
| status | VARCHAR(20) | NOT NULL | DRAFT / PUBLISHED / ARCHIVED |
| current_version | VARCHAR(20) | nullable | 当前已发布版本号 (如 "1.0.0") |
| version | VARCHAR(20) | NOT NULL, default "1.0.0" | 语义版本号 |
| is_active | BOOLEAN | NOT NULL, default true | 是否为活跃版本 |
| enabled | BOOLEAN | NOT NULL, default true | 是否启用 (列表可见) |
| deployed_at | TIMESTAMP | nullable | 部署时间, DRAFT 时为 null |
| previous_version_id | BIGINT | FK → dw_function_units | 前一版本引用 (自引用) |
| created_by | VARCHAR(64) | NOT NULL | 创建者 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_by | VARCHAR(64) | nullable | 最后修改者 |
| updated_at | TIMESTAMP | nullable | 最后修改时间 |
| lock_version | BIGINT | 乐观锁 | JPA @Version |

特殊行为:
- `code` 由 `generateUniqueCode()` 自动生成，格式 `fu-{yyyyMMdd}-{6位随机hex}`
- `@EqualsAndHashCode(of = "id")` — 仅用 id 判断相等
- 所有子集合 (`tableDefinitions`, `formDefinitions`, `actionDefinitions`, `versions`) 使用 `@JsonIgnore` + `FetchType.LAZY`
- 乐观锁通过 `@jakarta.persistence.Version` 实现并发控制

### 4.2 TableDefinition (表定义)

表名: `dw_table_definitions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| function_unit_id | BIGINT | FK, NOT NULL | 所属功能单元 |
| table_name | VARCHAR(100) | NOT NULL | 表名 |
| table_type | VARCHAR(20) | NOT NULL | MAIN / SUB / ACTION / RELATION |
| table_display_name | VARCHAR(200) | nullable | 显示名称 |
| description | TEXT | nullable | 描述 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | nullable | 修改时间 |

子集合:
- `fieldDefinitions` — 1:N, OrderBy `sortOrder ASC`, 级联 ALL + orphanRemoval
- `foreignKeys` — 1:N, `@JsonIgnore`, 级联 ALL + orphanRemoval

### 4.3 FieldDefinition (字段定义)

表名: `dw_field_definitions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| table_id | BIGINT | FK, NOT NULL | 所属表 |
| field_name | VARCHAR(100) | NOT NULL | 字段名 |
| data_type | VARCHAR(50) | NOT NULL | 数据类型枚举 |
| length | INTEGER | nullable | 长度 (VARCHAR) |
| precision_value | INTEGER | nullable | 精度 (DECIMAL) |
| scale | INTEGER | nullable | 小数位 (DECIMAL) |
| nullable | BOOLEAN | default true | 是否可空 |
| default_value | VARCHAR(500) | nullable | 默认值 |
| is_primary_key | BOOLEAN | default false | 是否主键 |
| is_unique | BOOLEAN | default false | 是否唯一 |
| description | TEXT | nullable | 描述 |
| sort_order | INTEGER | NOT NULL | 排序序号 |

### 4.4 ForeignKey (外键定义)

表名: `dw_foreign_keys`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| table_id | BIGINT | FK, NOT NULL | 所属表 |
| field_id | BIGINT | FK, NOT NULL | 源字段 |
| ref_table_id | BIGINT | FK, NOT NULL | 引用表 |
| ref_field_id | BIGINT | FK, NOT NULL | 引用字段 |
| on_delete | VARCHAR(20) | default "NO ACTION" | 删除策略 |
| on_update | VARCHAR(20) | default "NO ACTION" | 更新策略 |

### 4.5 FormDefinition (表单定义)

表名: `dw_form_definitions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| function_unit_id | BIGINT | FK, NOT NULL | 所属功能单元 |
| form_name | VARCHAR(100) | NOT NULL | 表单名称 |
| form_type | VARCHAR(20) | NOT NULL | MAIN / SUB / ACTION / POPUP |
| config_json | JSONB | NOT NULL | 表单配置 (form-create 格式) |
| description | TEXT | nullable | 描述 |
| bound_table_id | BIGINT | FK, nullable | 绑定表 (向后兼容) |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | nullable | 修改时间 |

便捷方法:
- `getBoundTableId()` — 返回绑定表 ID (JSON 序列化用)
- `getBoundTableName()` — 返回绑定表名 (JSON 序列化用)

子集合:
- `tableBindings` — 1:N FormTableBinding, OrderBy `sortOrder ASC`

### 4.6 FormTableBinding (表单表绑定)

表名: `dw_form_table_bindings`
唯一约束: `(form_id, table_id)`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| form_id | BIGINT | FK, NOT NULL | 所属表单 |
| table_id | BIGINT | FK, NOT NULL | 绑定表 |
| binding_type | VARCHAR(20) | NOT NULL | PRIMARY / SUB / RELATED |
| binding_mode | VARCHAR(20) | NOT NULL | EDITABLE / READONLY |
| foreign_key_field | VARCHAR(100) | nullable | 子表/关联表的外键字段名 |
| sort_order | INTEGER | nullable | 排序序号 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | nullable | 修改时间 |

便捷方法: `getTableId()`, `getTableName()`, `getFormId()` — 用于 JSON 序列化

### 4.7 ActionDefinition (动作定义)

表名: `dw_action_definitions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| function_unit_id | BIGINT | FK, NOT NULL | 所属功能单元 |
| action_name | VARCHAR(100) | NOT NULL | 动作名称 |
| action_type | VARCHAR(20) | NOT NULL | 动作类型枚举 |
| config_json | JSONB | NOT NULL | 动作配置 (类型相关) |
| icon | VARCHAR(50) | nullable | 图标名 |
| button_color | VARCHAR(20) | nullable | 按钮颜色 |
| description | TEXT | nullable | 描述 |
| is_default | BOOLEAN | default false | 是否默认动作 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | nullable | 修改时间 |

### 4.8 ProcessDefinition (流程定义)

表名: `dw_process_definitions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| function_unit_id | BIGINT | FK, NOT NULL, UNIQUE | 所属功能单元 (1:1) |
| function_unit_version_id | BIGINT | NOT NULL | 引用 dw_function_units(id), 非 dw_versions(id) |
| bpmn_xml | TEXT | NOT NULL | BPMN 2.0 XML 内容 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | nullable | 修改时间 |

注意: `function_unit_version_id` 的 FK 指向 `dw_function_units(id)` 而非 `dw_versions(id)`，这是设计决策。

### 4.9 Version (版本快照)

表名: `dw_versions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| function_unit_id | BIGINT | FK, NOT NULL | 所属功能单元 |
| version_number | VARCHAR(20) | NOT NULL | 版本号 (如 "1.0.0") |
| change_log | TEXT | nullable | 变更日志 |
| snapshot_data | BYTEA | NOT NULL | JSON 序列化的完整快照 |
| published_by | VARCHAR(50) | NOT NULL | 发布者 |
| published_at | TIMESTAMP | NOT NULL | 发布时间 |

### 4.10 Icon (图标)

表名: `dw_icons`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO | 主键 |
| name | VARCHAR(100) | UNIQUE, NOT NULL | 图标名称 |
| category | VARCHAR(30) | NOT NULL | 图标分类枚举 |
| svg_content | TEXT | NOT NULL | SVG 内容 |
| file_size | INTEGER | nullable | 文件大小 (bytes) |
| description | VARCHAR(500) | nullable | 描述 |
| created_by | VARCHAR(64) | nullable | 创建者 |
| created_at | DATETIME | nullable | 创建时间 |
| updated_by | VARCHAR(64) | nullable | 修改者 |
| updated_at | DATETIME | nullable | 修改时间 |

注意: Icon 使用 `LocalDateTime` 而非 `Instant` (历史原因)。

---

## 5. 枚举类型

### 5.1 核心业务枚举

#### FunctionUnitStatus (功能单元状态)
| 值 | 说明 |
|----|------|
| `DRAFT` | 草稿状态 (默认) |
| `PUBLISHED` | 已发布 |
| `ARCHIVED` | 已归档 |

#### TableType (表类型)
| 值 | 说明 |
|----|------|
| `MAIN` | 主表 |
| `SUB` | 子表 |
| `ACTION` | 动作表 |
| `RELATION` | 关联表 |

#### FormType (表单类型)
| 值 | 说明 |
|----|------|
| `MAIN` | 主表单 |
| `SUB` | 子表单 |
| `ACTION` | 动作表单 |
| `POPUP` | 弹出表单 |

#### DataType (数据类型)
| 值 | 说明 | 对应 PostgreSQL |
|----|------|----------------|
| `VARCHAR` | 字符串 | VARCHAR(n) |
| `TEXT` | 文本 | TEXT |
| `INTEGER` | 整数 | INTEGER |
| `BIGINT` | 长整数 | BIGINT |
| `DECIMAL` | 小数 | DECIMAL(p,s) |
| `BOOLEAN` | 布尔 | BOOLEAN |
| `DATE` | 日期 | DATE |
| `TIME` | 时间 | TIME |
| `TIMESTAMP` | 日期时间 | TIMESTAMP |
| `JSON` | JSON | JSONB |
| `BYTEA` | 二进制 | BYTEA |
| `FILE` | 文件上传 | VARCHAR (存路径/URL) |

#### ActionType (动作类型)
| 值 | 分组 | 说明 |
|----|------|------|
| `APPROVE` | 审批操作 | 同意 |
| `REJECT` | 审批操作 | 拒绝 |
| `TRANSFER` | 审批操作 | 转办 |
| `DELEGATE` | 审批操作 | 委托 |
| `ROLLBACK` | 审批操作 | 回退 |
| `WITHDRAW` | 审批操作 | 撤回 |
| `CANCEL` | 审批操作 | 取消 |
| `SAVE` | 通用操作 | 保存草稿 |
| `EXPORT` | 通用操作 | 导出数据 |
| `PROCESS_SUBMIT` | 流程操作 | 流程提交 |
| `PROCESS_REJECT` | 流程操作 | 流程驳回 |
| `API_CALL` | 自定义 | API 调用 |
| `FORM_POPUP` | 自定义 | 表单弹出 |
| `SCRIPT` | 自定义 | 脚本执行 |
| `CUSTOM_SCRIPT` | 自定义 | 自定义脚本 |
| `N8N_ACTION` | 自定义 | N8N 工作流动作 |
| `COMPOSITE` | 组合 | 组合动作 |

### 5.2 表单绑定枚举

#### BindingType (绑定类型)
| 值 | 说明 |
|----|------|
| `PRIMARY` | 主表 — 表单主要数据源，支持完整 CRUD |
| `SUB` | 子表 — 与主表一对多关系 |
| `RELATED` | 关联表 — 多对多或引用关系 |

#### BindingMode (绑定模式)
| 值 | 说明 |
|----|------|
| `EDITABLE` | 可编辑 — 允许增删改 |
| `READONLY` | 只读 — 仅查看 |

### 5.3 图标分类枚举

#### IconCategory
| 值 | 说明 |
|----|------|
| `APPROVAL` | 审批流程 (请假、报销、采购) |
| `CREDIT` | 信贷业务 (贷款、授信、风控) |
| `ACCOUNT` | 账户服务 (开户、销户) |
| `PAYMENT` | 支付结算 (转账、汇款) |
| `CUSTOMER` | 客户管理 (KYC、尽调) |
| `COMPLIANCE` | 合规风控 (反洗钱) |
| `OPERATION` | 运营管理 (报表、监控) |
| `GENERAL` | 通用图标 |

### 5.4 AI 相关枚举

#### AiMode
| 值 | 说明 |
|----|------|
| `NEW` | 新建模式 — 功能单元无现有数据 |
| `MODIFY` | 修改模式 — 功能单元已有数据 |

#### AiPhase
| 值 | 说明 |
|----|------|
| `REQUIREMENTS` | 需求收集阶段 |
| `DESIGN` | 设计方案阶段 |
| `GENERATION` | 生成预览与确认阶段 |

#### AiSessionStatus
| 值 | 说明 |
|----|------|
| `ACTIVE` | 活跃 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消 |

#### AiDocumentType
| 值 | 说明 |
|----|------|
| `REQUIREMENTS` | 需求文档 |
| `DESIGN` | 设计文档 |

#### AiMessageRole
| 值 | 说明 |
|----|------|
| `USER` | 用户消息 |
| `ASSISTANT` | AI 助手消息 |

### 5.5 其他枚举

#### DatabaseDialect
`POSTGRESQL` | `MYSQL` | `ORACLE` | `SQLSERVER` — 用于 DDL 生成

---

## 6. DTO 体系

### 请求 DTO

| DTO | 用途 | 关键字段 |
|-----|------|----------|
| `FunctionUnitRequest` | 创建/更新功能单元 | name, description, iconId |
| `TableDefinitionRequest` | 创建/更新表 | tableName, tableType, tableDisplayName, fields[] (注意: 字段名为 `fields` 而非 `fieldDefinitions`) |
| `FieldDefinitionRequest` | 字段定义 (嵌套在 TableDefinitionRequest) | fieldName, dataType, length, nullable, isPrimaryKey |
| `FormDefinitionRequest` | 创建/更新表单 | formName, formType, configJson, boundTableId |
| `FormTableBindingRequest` | 创建/更新表绑定 | tableId, bindingType, bindingMode, foreignKeyField |
| `ActionDefinitionRequest` | 创建/更新动作 | actionName, actionType, configJson, icon, buttonColor |
| `DeployRequest` | 部署请求 | targetUrl, changeLog, conflictStrategy, environment (DeployEnvironment 枚举: DEVELOPMENT/TESTING/PRODUCTION), autoEnable |
| `AiChatRequest` | AI 对话请求 | functionUnitId, message, sessionId, phase (@NotNull AiPhase), mode (@NotNull AiMode) |
| `ApplyGeneratedDataRequest` | 应用 AI 生成数据 | sessionId, tables[], forms[], actions[], processXml |
| `SaveDocumentRequest` | 保存 AI 文档 | functionUnitId, documentType, content |
| `ForceUnlockResponseRequest` | 强制解锁响应 | accept (boolean) |
| `DeploymentRequest` | 版本部署请求 (VersionController) | bpmnXml, changeType, metadata |
| `RollbackRequest` | 版本回滚请求 | targetVersion, confirmed |

### 响应 DTO

| DTO | 用途 | 关键字段 |
|-----|------|----------|
| `FunctionUnitResponse` | 功能单元详情 | id, code, name, description, iconId, icon (IconInfo: id/name/svgContent), status, currentVersion, createdBy, createdAt, updatedBy, updatedAt, tableCount, formCount, actionCount, hasProcess |
| `DeployResponse` | 部署状态 | deploymentId, status, progress, steps[], versionNumber |
| `VersionResponse` | 版本信息 | id, versionNumber, changeLog, createdBy (映射自 publishedBy), createdAt (映射自 publishedAt) |
| `FormTableBindingResponse` | 绑定详情 | id, formId, tableId, tableName, tableType, bindingType, bindingMode, foreignKeyField, sortOrder, createdAt, updatedAt |
| `ValidationResult` | 校验结果 | valid (boolean), errors[], warnings[] |
| `AiSessionResponse` | AI 会话信息 | sessionId, functionUnitId, phase, status |
| `AiMessageResponse` | AI 消息 | role, content, timestamp |
| `LockInfoResponse` | 编辑锁信息 | functionUnitId, userId, userName, lockedAt, locked |
| `IconDTO` | 图标信息 | id, name, category, svgContent, fileSize, description, createdBy, createdAt |
| `FunctionUnitDisplay` | UI 展示用 | 活跃版本的展示信息 |
| `VersionHistoryDisplay` | 版本历史 UI | 版本历史展示信息 |
| `DeploymentResult` | 版本部署结果 | version, deployedAt |
| `RollbackResult` | 回滚结果 | rolledBackToVersion |
| `RollbackImpact` | 回滚影响评估 | versionsToDelete, totalProcessInstancesToDelete |
| `ErrorResponse` | 错误响应 | code, message, details (List), suggestion, timestamp, traceId, path |

---

## 7. API 端点

### 7.1 功能单元管理 — FunctionUnitController

基础路径: `/function-units`
继承: `BaseController` ✅

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/function-units` | FUNCTION_UNIT_CREATE | 创建功能单元 |
| PUT | `/function-units/{id}` | FUNCTION_UNIT_UPDATE | 更新功能单元 |
| DELETE | `/function-units/{id}` | FUNCTION_UNIT_DELETE | 删除功能单元 |
| GET | `/function-units/{id}` | FUNCTION_UNIT_VIEW | 获取详情 (返回 FunctionUnitResponse) |
| GET | `/function-units?name=&status=` | FUNCTION_UNIT_VIEW | 分页列表 (Pageable) |
| POST | `/function-units/{id}/publish?changeLog=` | FUNCTION_UNIT_PUBLISH | 发布版本 |
| POST | `/function-units/{id}/clone?newName=` | FUNCTION_UNIT_CREATE | 克隆功能单元 |
| GET | `/function-units/{id}/validate` | FUNCTION_UNIT_VIEW | 完整性校验 |
| GET | `/function-units/{id}/versions` | FUNCTION_UNIT_VIEW | 版本历史 |

### 7.2 表设计 — TableDesignController

基础路径: `/function-units/{functionUnitId}/tables`
继承: `BaseController` ✅

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tables` | 列出所有表 |
| POST | `/tables` | 创建表 |
| PUT | `/tables/{tableId}` | 更新表 |
| DELETE | `/tables/{tableId}` | 删除表 |
| GET | `/tables/{tableId}` | 获取表详情 |
| GET | `/tables/{tableId}/ddl?dialect=POSTGRESQL` | 生成 DDL |
| GET | `/tables/validate` | 校验表结构 |
| GET | `/tables/foreign-keys` | 获取所有外键关系 |

### 7.3 表单设计 — FormDesignController

基础路径: `/function-units/{functionUnitId}/forms`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/forms` | 列出所有表单 |
| POST | `/forms` | 创建表单 |
| PUT | `/forms/{formId}` | 更新表单 |
| DELETE | `/forms/{formId}` | 删除表单 |
| GET | `/forms/{formId}` | 获取表单详情 |
| GET | `/forms/{formId}/form-create-config` | 生成 form-create 配置 |
| GET | `/forms/{formId}/validate` | 校验表单配置 |
| GET | `/forms/{formId}/bindings` | 列出表绑定 |
| POST | `/forms/{formId}/bindings` | 创建表绑定 |
| PUT | `/forms/{formId}/bindings/{bindingId}` | 更新表绑定 |
| DELETE | `/forms/{formId}/bindings/{bindingId}` | 删除表绑定 |

### 7.4 动作设计 — ActionDesignController

基础路径: `/function-units/{functionUnitId}/actions`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/actions` | 列出所有动作 |
| POST | `/actions` | 创建动作 |
| PUT | `/actions/{actionId}` | 更新动作 |
| DELETE | `/actions/{actionId}` | 删除动作 |
| GET | `/actions/{actionId}` | 获取动作详情 |
| POST | `/actions/{actionId}/test` | 测试动作执行 |

### 7.5 动作查询 — ActionQueryController

基础路径: `/actions` (跨功能单元)
继承: `BaseController` ❌

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/actions/batch?ids=12,16,17` | 批量获取动作定义 |
| GET | `/actions/{actionId}` | 按 ID 获取动作 |

### 7.6 流程设计 — ProcessDesignController

基础路径: `/function-units/{functionUnitId}/process`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/process` | 获取流程定义 |
| POST | `/process` | 保存流程定义 (body: `{"bpmnXml": "..."}`) |
| GET | `/process/validate` | 校验流程定义 |
| POST | `/process/simulate` | 模拟流程执行 |

### 7.7 部署 — DeploymentController

基础路径: `/function-units`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/function-units/{id}/export` | FUNCTION_UNIT_VIEW | 导出 ZIP 包 |
| POST | `/function-units/{id}/deploy` | FUNCTION_UNIT_PUBLISH | 一键部署到 admin-center |
| GET | `/function-units/deployments/{deploymentId}/status` | FUNCTION_UNIT_VIEW | 查询部署状态 |
| GET | `/function-units/{id}/deployments` | FUNCTION_UNIT_VIEW | 部署历史 |

### 7.8 版本管理 — VersionController

基础路径: `/api/function-units` (注意: 不同于其他控制器的路径)
继承: `BaseController` ❌

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/{functionUnitName}/deploy` | 部署新版本 |
| GET | `/{functionUnitName}/versions` | 获取版本历史 |
| GET | `/{functionUnitName}/versions/active` | 获取活跃版本 |
| POST | `/{functionUnitName}/rollback` | 回滚到指定版本 (需确认) |
| GET | `/` | 列出所有功能单元 (UI 展示) |
| GET | `/{functionUnitName}/history` | 版本历史 (UI 展示) |

### 7.9 导入导出 — ExportImportController

基础路径: `/export-import`
继承: `BaseController` ❌

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/function-units/{id}/export` | 导出功能单元 ZIP |
| POST | `/import` | 导入功能单元 (multipart, conflictStrategy: SKIP/OVERWRITE) |
| POST | `/validate` | 校验导入包 |
| POST | `/check-conflicts` | 检查导入冲突 |

### 7.10 图标库 — IconLibraryController

基础路径: `/icons`
继承: `BaseController` ❌

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/icons?keyword=&category=&tag=` | 分页搜索图标 |
| GET | `/icons/tags` | 获取所有标签 |
| GET | `/icons/categories` | 获取所有分类 |
| POST | `/icons` | 上传图标 (multipart: file, name, category) |
| DELETE | `/icons/{id}` | 删除图标 |
| GET | `/icons/{id}` | 获取图标详情 |
| GET | `/icons/{id}/usage` | 检查图标是否被使用 |

### 7.11 AI 生成 — AiGenerationController

基础路径: `/ai-generation`
继承: `BaseController` ✅

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/chat/stream` (SSE) | FUNCTION_UNIT_UPDATE | AI 对话流 |
| GET | `/events/{functionUnitId}` (SSE) | FUNCTION_UNIT_VIEW | 事件流 |
| POST | `/lock/{functionUnitId}` | FUNCTION_UNIT_UPDATE | 获取编辑锁 |
| DELETE | `/lock/{functionUnitId}` | FUNCTION_UNIT_UPDATE | 释放编辑锁 |
| POST | `/lock/{functionUnitId}/force-unlock-request` | FUNCTION_UNIT_UPDATE | 请求强制解锁 |
| POST | `/lock/{functionUnitId}/force-unlock-response` | FUNCTION_UNIT_UPDATE | 响应强制解锁 |
| GET | `/sessions?functionUnitId=` | FUNCTION_UNIT_VIEW | 列出会话 |
| GET | `/sessions/{sessionId}/messages` | FUNCTION_UNIT_VIEW | 获取消息 (分页) |
| PUT | `/sessions/{sessionId}/phase?phase=` | FUNCTION_UNIT_UPDATE | 更新会话阶段 |
| GET | `/documents?functionUnitId=&documentType=` | FUNCTION_UNIT_VIEW | 列出文档版本 |
| GET | `/documents/version?functionUnitId=&documentType=&version=` | FUNCTION_UNIT_VIEW | 获取指定版本文档 |
| POST | `/documents` | FUNCTION_UNIT_UPDATE | 保存文档 |
| POST | `/{functionUnitId}/apply` | FUNCTION_UNIT_UPDATE | 应用 AI 生成数据 |

### 7.12 文件上传 — FileUploadController

基础路径: `/upload`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload` | 上传文件 (multipart, 支持 jpg/png/gif/pdf/doc/docx/xls/xlsx, 最大 10MB) |
| GET | `/upload/files/{filename}` | 获取文件 (支持内联预览, 含路径遍历防护) |
| DELETE | `/upload/files/{filename}` | 删除文件 |

### 7.13 认证 — AuthController

基础路径: `/auth`
继承: `BaseController` ❌ (手动构建响应)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 用户登录 (返回 JWT token) |
| POST | `/auth/logout` | 用户登出 |
| GET | `/auth/me` | 获取当前用户信息 (需 Authorization header) |
| GET | `/auth/validate` | 验证 token 有效性 |

### 7.14 成员管理 — MemberController

基础路径: `/members`
继承: `BaseController` ✅

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/members` | 创建成员 |
| GET | `/members/{id}` | 按 ID 获取成员 |
| GET | `/members/username/{username}` | 按用户名获取成员 |
| PUT | `/members/{id}` | 更新成员 |
| DELETE | `/members/{id}` | 删除成员 (软删除) |
| GET | `/members` | 分页列表 (支持搜索) |
| GET | `/members/business-unit/{businessUnitId}` | 按业务单元获取成员 |

### 7.15 弹性管理 — ResilienceController

基础路径: `/api/resilience` (注意: 不同于其他控制器的路径)
继承: `BaseController` ✅

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/resilience/health` | 获取弹性健康状态 (熔断器 + 降级) |
| POST | `/api/resilience/health-check` | 手动触发健康检查 |
| POST | `/api/resilience/circuit-breakers/reset` | 重置所有熔断器 |
| POST | `/api/resilience/emergency-mode/enter` | 进入紧急模式 |
| POST | `/api/resilience/emergency-mode/exit` | 退出紧急模式 |

---

## 8. 核心数据流

### 8.1 创建功能单元

```
POST /function-units
    │
    ▼
FunctionUnitController.create()
    │ @Valid @RequestBody FunctionUnitRequest
    │ @RequireDeveloperPermission("FUNCTION_UNIT_CREATE")
    ▼
handleRequest(() -> functionUnitComponent.create(request))
    │
    ▼
FunctionUnitComponentImpl.create()
    │ @Transactional
    │ @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    │
    ├── 1. 检查名称唯一性 (existsByName)
    │   └── 重复 → throw BusinessException("CONFLICT_NAME_EXISTS")
    │
    ├── 2. 生成唯一编码 generateUniqueCode()
    │   └── 格式: fu-{yyyyMMdd}-{6位随机hex}
    │
    ├── 3. 构建 FunctionUnit 实体
    │   └── status = DRAFT, version = "1.0.0"
    │
    ├── 4. 如果有 iconId → 查找 Icon 实体
    │   └── 不存在 → throw ResourceNotFoundException
    │
    └── 5. functionUnitRepository.save()
         └── 返回持久化的 FunctionUnit
```

### 8.2 发布版本

```
POST /function-units/{id}/publish?changeLog=xxx
    │
    ▼
FunctionUnitComponentImpl.publish(id, changeLog)
    │ @Transactional
    │ @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD', 'DEVELOPER')")
    │
    ├── 1. getById(id) → 加载功能单元
    │
    ├── 2. validate(id) → 完整性校验
    │   ├── 检查是否有 ProcessDefinition
    │   ├── 检查是否有 MAIN 类型的 TableDefinition
    │   └── 检查是否有 MAIN 类型的 FormDefinition
    │   └── 校验失败 → throw BusinessException("BIZ_INVALID_FUNCTION_UNIT")
    │
    ├── 3. calculateNextVersion(currentVersion)
    │   └── null → "1.0.0", "1.0.0" → "1.0.1", ...
    │
    ├── 4. 检查版本号是否已存在 (幂等处理)
    │   └── 已存在 → 跳过快照创建 (上次部署中途失败的恢复)
    │
    ├── 5. createSnapshot(functionUnit) → byte[]
    │   ├── 序列化: name, code, description, status, processXml
    │   ├── 序列化: tableDefinitions[] (含 fieldDefinitions[])
    │   ├── 序列化: formDefinitions[] (含 configJson, boundTableName)
    │   └── 序列化: actionDefinitions[] (含 configJson)
    │
    ├── 6. 创建 Version 实体并保存
    │   └── versionNumber, changeLog, snapshotData, publishedBy
    │
    └── 7. 更新 FunctionUnit
         ├── status = PUBLISHED
         └── currentVersion = newVersion
```

### 8.3 克隆功能单元

```
POST /function-units/{id}/clone?newName=xxx
    │
    ▼
FunctionUnitComponentImpl.clone(id, newName)
    │ @Transactional
    │ @PreAuthorize("hasAnyRole('TECH_LEAD', 'TEAM_LEAD')")
    │
    ├── 1. 检查新名称唯一性
    │
    ├── 2. 加载源功能单元
    │
    ├── 3. 创建新 FunctionUnit (新 code, status=DRAFT)
    │
    ├── 4. 克隆 ProcessDefinition (如果存在)
    │
    ├── 5. 克隆 TableDefinitions
    │   ├── 逐表克隆 (cloneTable)
    │   ├── 克隆每个表的 FieldDefinitions
    │   └── 建立 tableMapping: sourceTableId → clonedTable
    │
    ├── 6. 克隆 ForeignKeys (所有表克隆完成后)
    │   ├── 构建 clonedFieldLookup: tableId → {fieldName → FieldDefinition}
    │   └── 通过 fieldName 匹配重建外键关系
    │
    ├── 7. 克隆 FormDefinitions (含 FormTableBindings)
    │   ├── 通过 tableMapping 映射 boundTable
    │   └── 通过 tableMapping 映射每个 binding 的 table
    │
    └── 8. 克隆 ActionDefinitions
         └── 深拷贝 configJson (new HashMap<>)
```

### 8.4 完整性校验

```
GET /function-units/{id}/validate
    │
    ▼
FunctionUnitComponentImpl.validate(id)
    │ @Transactional(readOnly = true)
    │
    ├── 检查 ProcessDefinition 是否存在
    │   └── 缺失 → warning "MISSING_PROCESS"
    │
    ├── 检查是否有 MAIN 类型的 TableDefinition
    │   └── 缺失 → warning "MISSING_MAIN_TABLE"
    │
    └── 检查是否有 MAIN 类型的 FormDefinition
        └── 缺失 → warning "MISSING_MAIN_FORM"
    
    返回 ValidationResult { valid, errors[], warnings[] }
```

---

## 9. 版本管理与快照

### 版本号规则

- 格式: `MAJOR.MINOR.PATCH` (语义版本)
- 自动递增: `calculateNextVersion()` 递增 PATCH
  - `null` → `"1.0.0"`
  - `"1.0.0"` → `"1.0.1"`
  - `"1.0.9"` → `"1.0.10"`
- 手动版本 (VersionController): 支持 `MAJOR`, `MINOR`, `PATCH` changeType

### 快照数据结构

`Version.snapshotData` 存储为 BYTEA (Jackson JSON 序列化的 byte[]):

```json
{
  "name": "报销审批",
  "code": "fu-20260112-a1b2c3",
  "description": "...",
  "status": "PUBLISHED",
  "processXml": "<bpmn:definitions ...>...</bpmn:definitions>",
  "tableDefinitions": [
    {
      "tableName": "expense_main",
      "tableType": "MAIN",
      "tableDisplayName": "报销主表",
      "description": "...",
      "fieldDefinitions": [
        {
          "fieldName": "id",
          "dataType": "BIGINT",
          "length": null,
          "precision": null,
          "scale": null,
          "nullable": false,
          "defaultValue": null,
          "isPrimaryKey": true,
          "isUnique": false,
          "description": "主键",
          "sortOrder": 0
        }
      ]
    }
  ],
  "formDefinitions": [
    {
      "formName": "报销申请表",
      "formType": "MAIN",
      "configJson": { /* form-create 配置 */ },
      "description": "...",
      "boundTableName": "expense_main"
    }
  ],
  "actionDefinitions": [
    {
      "actionName": "提交",
      "actionType": "PROCESS_SUBMIT",
      "configJson": { /* 动作配置 */ },
      "icon": "el-icon-check",
      "buttonColor": "#409EFF",
      "description": "...",
      "isDefault": true
    }
  ]
}
```

### 版本比较 (前端)

前端 `version` 模块支持两个版本的 JSON diff 比较，展示:
- 表定义变更 (新增/修改/删除)
- 表单定义变更
- 流程定义变更

---

## 10. 部署流程

### 一键部署 (DeploymentController → DeploymentComponentImpl)

```
POST /function-units/{id}/deploy
    │
    ▼
DeploymentComponentImpl.deployToAdminCenter(id, request)
    │
    ├── 1. 加载 FunctionUnit
    ├── 2. 生成 deploymentId (UUID)
    ├── 3. 创建初始 DeployResponse (status=DEPLOYING, progress=0)
    ├── 4. 捕获 SecurityContext + Locale (传递到异步线程)
    └── 5. taskExecutor.execute(() -> executeDeployment(...))
         │  (异步执行，使用 Spring TaskExecutor)
         │
         ├── Step 0: 自动创建版本 (5%→15%)
         │   └── functionUnitComponent.publish(id, changeLog)
         │
         ├── Step 1: 导出功能单元 (20%→30%)
         │   └── exportImportComponent.exportFunctionUnit(id) → byte[] ZIP
         │
         ├── Step 2: 上传到 admin-center (→60%)
         │   ├── POST {targetUrl}/api/v1/admin/function-units-import/import
         │   ├── Content-Type: multipart/form-data
         │   ├── file: {name}.zip
         │   └── conflictStrategy: OVERWRITE (默认)
         │
         └── Step 3: 触发部署 (→100%)
             ├── POST {targetUrl}/api/v1/admin/function-units-import/{importedId}/deploy
             ├── body: { environment, autoEnable }
             └── 成功 → status=SUCCESS, progress=100

异常处理:
- 任何步骤失败 → status=FAILED, 标记当前步骤 FAILED
- 部署历史保存到 deploymentHistoryMap (内存)
```

### 部署状态查询

```
GET /function-units/deployments/{deploymentId}/status
    → 返回 DeployResponse { deploymentId, status, progress, steps[], versionNumber }

DeployResponse.DeployStatus:
  PENDING | DEPLOYING | SUCCESS | FAILED | ROLLED_BACK
```

### 配置

```yaml
admin-center:
  url: ${ADMIN_CENTER_URL:http://localhost:8090}
```

---

## 11. AI 生成模块

### 概述

AI 生成模块通过 N8N 工作流与大语言模型交互，支持通过对话式交互自动生成功能单元的表、表单、动作和流程定义。

### 核心流程

```
用户 → AiGenerationController → AiGenerationComponent → N8N Webhook (SSE)
                                                              │
                                                              ▼
                                                         LLM 处理
                                                              │
                                                              ▼
                                                     SSE 流式返回结果
                                                              │
                                                              ▼
                                                   用户确认 → applyGeneratedData()
```

### 编辑锁机制

- 同一功能单元同一时间只允许一个用户进行 AI 生成操作
- 锁 TTL: 1800 秒 (30 分钟), 可配置
- 支持强制解锁请求/响应流程 (通过 SSE 事件通知锁持有者)
- 强制解锁超时: 60 秒

### AI 会话阶段

1. `REQUIREMENTS` — 需求收集: AI 引导用户描述业务需求
2. `DESIGN` — 设计方案: AI 生成设计文档
3. `GENERATION` — 生成预览: AI 生成具体的表/表单/动作/流程定义，用户确认后应用

### AI 文档版本管理

- 每个阶段的文档支持多版本存储
- 用户可以手动编辑并保存文档
- 文档类型: `REQUIREMENTS` (需求文档), `DESIGN` (设计文档)

### 配置

```yaml
n8n:
  ai-generation:
    webhook-url: ${N8N_AI_GENERATION_WEBHOOK_URL:http://localhost:5678/webhook/ai-function-unit-gen}
    timeout-seconds: ${N8N_AI_GENERATION_TIMEOUT:120}

ai-generation:
  lock:
    ttl-seconds: ${AI_GENERATION_LOCK_TTL:1800}
    force-unlock-timeout-seconds: ${AI_GENERATION_FORCE_UNLOCK_TIMEOUT:60}
  context:
    max-size-bytes: ${AI_GENERATION_CONTEXT_MAX_SIZE:102400}
```

---

## 12. 导入导出

### 导出格式

导出为 ZIP 包，包含:
- `manifest.json` — 元数据 (ExportManifest)
- 功能单元完整数据的 JSON 序列化

### 导入策略

| 策略 | 说明 |
|------|------|
| `SKIP` | 遇到冲突跳过 |
| `OVERWRITE` | 覆盖已有数据 |

### 导入流程

```
1. POST /export-import/validate — 校验包格式
2. POST /export-import/check-conflicts — 检查冲突
3. POST /export-import/import?conflictStrategy=OVERWRITE — 执行导入
```

---

## 13. 安全模型

### 权限注解

使用自定义注解 `@RequireDeveloperPermission`:

```java
@RequireDeveloperPermission("FUNCTION_UNIT_CREATE")   // 创建
@RequireDeveloperPermission("FUNCTION_UNIT_UPDATE")   // 更新
@RequireDeveloperPermission("FUNCTION_UNIT_DELETE")   // 删除
@RequireDeveloperPermission("FUNCTION_UNIT_VIEW")     // 查看
@RequireDeveloperPermission("FUNCTION_UNIT_PUBLISH")  // 发布/部署
```

### 角色权限 (Spring Security @PreAuthorize)

| 操作 | 允许角色 |
|------|----------|
| 创建功能单元 | TECH_LEAD, TEAM_LEAD |
| 更新功能单元 | (由 @RequireDeveloperPermission 控制) |
| 发布版本 | TECH_LEAD, TEAM_LEAD, DEVELOPER |
| 克隆功能单元 | TECH_LEAD, TEAM_LEAD |

### JWT 认证

- Token 通过 `Authorization: Bearer {token}` 传递
- 用户 ID 通过 `X-User-Id` 请求头传递 (AI 模块使用)
- JWT secret 通过环境变量 `JWT_SECRET` 注入
- Token 有效期: 24 小时 (可配置)

### 乐观锁

FunctionUnit 使用 `@jakarta.persistence.Version` + `lockVersion` 字段实现乐观锁，防止并发修改冲突。

---

## 14. 国际化

### 后端 i18n

- 框架: Spring MessageSource
- 配置: `spring.messages.basename=i18n/messages`
- 使用: `i18nService.getMessage("deploy.started")`
- Bean Validation 消息: `ValidationMessages*.properties` (独立于 i18n/messages)

### 前端 i18n

- 框架: vue-i18n v11 (Composition API 模式, `legacy: false`)
- 默认语言: `en`
- 支持语言: `en`, `zh-CN`, `zh-TW`
- 使用: `t('functionUnit.createSuccess')` 或 `$t('key')`

### i18n Key 命名规范

```
模块.区域.键名

functionUnit.title          → "Function Units"
functionUnit.createSuccess  → "Created successfully"
table.tableName             → "Table Name"
form.bindTable              → "Bind Table"
action.approve              → "Approve"
process.save                → "Save"
icon.upload                 → "Upload Icon"
version.history             → "Version History"
common.save                 → "Save"
common.cancel               → "Cancel"
```

### 关键 i18n 模块

| 模块 | 前缀 | 说明 |
|------|------|------|
| `functionUnit.*` | 功能单元管理 | CRUD、发布、克隆、部署 |
| `table.*` | 表设计 | 表/字段/外键/DDL |
| `form.*` | 表单设计 | 表单/绑定/导入字段 |
| `action.*` | 动作设计 | 动作类型/配置/N8N |
| `process.*` | 流程设计 | BPMN 设计器 |
| `icon.*` | 图标库 | 上传/分类/搜索 |
| `version.*` | 版本管理 | 历史/比较/回滚 |
| `properties.*` | 流程属性 | 节点配置/分配/超时 |
| `common.*` | 通用 | 保存/取消/删除/确认 |

---

## 15. 配置属性

### application.yml (developer-workstation)

```yaml
# 服务端口与上下文路径
server:
  port: ${SERVER_PORT:8083}
  servlet:
    context-path: /api/v1

# CORS 白名单
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3002,http://localhost:5173}

# Admin Center 地址 (部署目标)
admin-center:
  url: ${ADMIN_CENTER_URL:http://localhost:8090}

spring:
  # 排除默认 UserDetailsService 自动配置
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
  application:
    name: developer-workstation

  # 数据源 (PostgreSQL)
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform}
    username: ${SPRING_DATASOURCE_USERNAME:platform}
    password: ${SPRING_DATASOURCE_PASSWORD:platform123}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  # JPA
  jpa:
    hibernate:
      ddl-auto: none          # 使用 Flyway 管理 schema
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: Asia/Shanghai

  # Jackson 序列化
  jackson:
    serialization:
      write-dates-as-timestamps: false
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

  # Redis
  data:
    redis:
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:6379}
      password: ${SPRING_REDIS_PASSWORD:redis123}
      timeout: 5000ms

  # i18n
  messages:
    basename: i18n/messages
    encoding: UTF-8

  # 文件上传
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

# 文件上传目录
file:
  upload:
    dir: ${FILE_UPLOAD_DIR:uploads}
    base-url: ${FILE_UPLOAD_BASE_URL:/api/v1/upload/files}

# 安全配置 (单一 security: 键，包含所有子配置)
security:
  jwt:
    secret: ${JWT_SECRET:...}           # 必须通过环境变量注入
    expiration: ${JWT_EXPIRATION:86400000}  # 24 小时
  max-login-attempts: ${MAX_LOGIN_ATTEMPTS:5}
  lock-duration-minutes: ${LOCK_DURATION_MINUTES:30}
  # 权限缓存
  cache:
    session-timeout-minutes: ${SECURITY_CACHE_SESSION_TIMEOUT:30}
    max-size: ${SECURITY_CACHE_MAX_SIZE:1000}
    enabled: ${SECURITY_CACHE_ENABLED:true}
    cleanup-interval-minutes: ${SECURITY_CACHE_CLEANUP_INTERVAL:15}
  # 安全数据库配置
  database:
    query-timeout-seconds: ${SECURITY_DB_QUERY_TIMEOUT:30}
    retry-attempts: ${SECURITY_DB_RETRY_ATTEMPTS:2}
    retry-delay-ms: ${SECURITY_DB_RETRY_DELAY:1000}
    connection-pooling-enabled: ${SECURITY_DB_CONNECTION_POOLING:true}
  # 权限系统
  permission:
    resolution-strategy: ${SECURITY_PERMISSION_STRATEGY:DATABASE_FIRST}
    strict-checking: ${SECURITY_PERMISSION_STRICT:true}
    audit-logging: ${SECURITY_PERMISSION_AUDIT:true}
    max-permission-name-length: ${SECURITY_PERMISSION_MAX_NAME_LENGTH:100}
    max-role-name-length: ${SECURITY_ROLE_MAX_NAME_LENGTH:100}
  # 限流
  rate-limit:
    requests-per-minute: 100

# 工作流引擎
workflow-engine:
  url: ${WORKFLOW_ENGINE_URL:http://localhost:8081}
  enabled: true
  jwt:
    secret: ${JWT_SECRET:...}

# JWT 配置 (顶层, AuthController 使用)
jwt:
  secret: ${JWT_SECRET:...}
  expiration: ${JWT_EXPIRATION:86400000}

# N8N AI 生成
n8n:
  ai-generation:
    webhook-url: ${N8N_AI_GENERATION_WEBHOOK_URL:http://localhost:5678/webhook/ai-function-unit-gen}
    timeout-seconds: ${N8N_AI_GENERATION_TIMEOUT:120}

# AI 编辑锁
ai-generation:
  lock:
    ttl-seconds: ${AI_GENERATION_LOCK_TTL:1800}
    force-unlock-timeout-seconds: ${AI_GENERATION_FORCE_UNLOCK_TIMEOUT:60}
  context:
    max-size-bytes: ${AI_GENERATION_CONTEXT_MAX_SIZE:102400}

# OpenAPI (生产环境禁用)
springdoc:
  api-docs:
    path: /api-docs
    enabled: ${SWAGGER_ENABLED:true}
  swagger-ui:
    path: /swagger-ui.html
    enabled: ${SWAGGER_ENABLED:true}

# 日志
logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.developer: ${LOG_LEVEL_PLATFORM:DEBUG}
    org.springframework.security: INFO
    org.hibernate.SQL: ${LOG_LEVEL_SQL:DEBUG}

# Actuator 端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized
```

### 环境变量速查

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SERVER_PORT` | 服务端口 | 8083 |
| `ADMIN_CENTER_URL` | admin-center 地址 | http://localhost:8090 |
| `SPRING_DATASOURCE_URL` | PostgreSQL 连接 | jdbc:postgresql://localhost:5432/workflow_platform |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户 | platform |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | (环境变量注入) |
| `SPRING_REDIS_HOST` | Redis 主机 | localhost |
| `SPRING_REDIS_PORT` | Redis 端口 | 6379 |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | (环境变量注入) |
| `JWT_SECRET` | JWT 签名密钥 | (必须环境变量注入) |
| `JWT_EXPIRATION` | JWT 有效期 (ms) | 86400000 (24h) |
| `ENCRYPTION_SECRET_KEY` | AES 加密密钥 | (必须环境变量注入) |
| `CORS_ALLOWED_ORIGINS` | CORS 白名单 | http://localhost:3000,... |
| `MAX_LOGIN_ATTEMPTS` | 最大登录尝试次数 | 5 |
| `LOCK_DURATION_MINUTES` | 登录锁定时长 (分钟) | 30 |
| `SECURITY_CACHE_SESSION_TIMEOUT` | 权限缓存会话超时 (分钟) | 30 |
| `SECURITY_CACHE_MAX_SIZE` | 权限缓存最大条目数 | 1000 |
| `SECURITY_CACHE_ENABLED` | 权限缓存开关 | true |
| `SECURITY_CACHE_CLEANUP_INTERVAL` | 缓存清理间隔 (分钟) | 15 |
| `SECURITY_DB_QUERY_TIMEOUT` | 安全查询超时 (秒) | 30 |
| `SECURITY_DB_RETRY_ATTEMPTS` | 安全查询重试次数 | 2 |
| `SECURITY_DB_RETRY_DELAY` | 安全查询重试延迟 (ms) | 1000 |
| `SECURITY_DB_CONNECTION_POOLING` | 安全连接池开关 | true |
| `SECURITY_PERMISSION_STRATEGY` | 权限解析策略 | DATABASE_FIRST |
| `SECURITY_PERMISSION_STRICT` | 严格权限检查 | true |
| `SECURITY_PERMISSION_AUDIT` | 权限审计日志 | true |
| `SECURITY_PERMISSION_MAX_NAME_LENGTH` | 权限名最大长度 | 100 |
| `SECURITY_ROLE_MAX_NAME_LENGTH` | 角色名最大长度 | 100 |
| `WORKFLOW_ENGINE_URL` | 工作流引擎地址 | http://localhost:8081 |
| `N8N_AI_GENERATION_WEBHOOK_URL` | N8N webhook | http://localhost:5678/webhook/... |
| `N8N_AI_GENERATION_TIMEOUT` | N8N 超时 (秒) | 120 |
| `AI_GENERATION_LOCK_TTL` | AI 编辑锁 TTL (秒) | 1800 |
| `AI_GENERATION_FORCE_UNLOCK_TIMEOUT` | AI 强制解锁超时 (秒) | 60 |
| `AI_GENERATION_CONTEXT_MAX_SIZE` | AI 上下文最大字节数 | 102400 |
| `SWAGGER_ENABLED` | Swagger 开关 | true (生产环境设 false) |
| `FILE_UPLOAD_DIR` | 文件上传目录 | uploads |
| `FILE_UPLOAD_BASE_URL` | 文件访问基础 URL | /api/v1/upload/files |
| `LOG_LEVEL_ROOT` | 根日志级别 | INFO |
| `LOG_LEVEL_PLATFORM` | 平台日志级别 | DEBUG |
| `LOG_LEVEL_SQL` | SQL 日志级别 | DEBUG |

---

## 16. 数据库迁移

### Flyway 约定

- 路径: `src/main/resources/db/migration/{module}/`
- 命名: `V{版本号}__{描述}.sql`
- 版本号分段（约定，非强制）:
  - admin-center: 200+
  - developer-workstation: 300+
  - user-portal: 400+（示例：`V405__...`）
- **本仓库** `workflow-engine-core` **未**使用 Flyway；引擎表结构依赖 `deploy/init-scripts` 等。
- **Dev Docker Compose** 对 admin-center / user-portal / developer-workstation 常设置 **`SPRING_FLYWAY_ENABLED=false`**，与默认 `application.yml` 不同，见 [docs/schema-and-migration.md](../docs/schema-and-migration.md) §2.1。
- `.sql` 文件必须使用 LF 换行 (`.gitattributes` 已配置)

### 核心表清单

| 表名 | 实体 | 说明 |
|------|------|------|
| `dw_function_units` | FunctionUnit | 功能单元主表 |
| `dw_table_definitions` | TableDefinition | 表定义 |
| `dw_field_definitions` | FieldDefinition | 字段定义 |
| `dw_foreign_keys` | ForeignKey | 外键关系 |
| `dw_form_definitions` | FormDefinition | 表单定义 |
| `dw_form_table_bindings` | FormTableBinding | 表单表绑定 |
| `dw_action_definitions` | ActionDefinition | 动作定义 |
| `dw_process_definitions` | ProcessDefinition | 流程定义 |
| `dw_versions` | Version | 版本快照 |
| `dw_icons` | Icon | 图标库 |
| `dw_ai_sessions` | AiSession | AI 会话 |
| `dw_ai_messages` | AiMessage | AI 对话消息 |
| `dw_ai_documents` | AiDocument | AI 生成文档 |
| `dw_operation_logs` | OperationLog | 操作日志 |
| `dw_function_unit_access` | FunctionUnitAccess | 功能单元访问控制 |
| `dw_deployment_jobs` | DeploymentJob | 一键部署到 admin-center 的异步任务状态（多实例/重启可恢复查询） |
| `dw_function_unit_dev_groups` | FunctionUnitDevGroupAssignment | 功能单元与虚拟开发组映射（工作区 RBAC） |
| `members` | Member | 成员管理 (非 dw_ 前缀) |
| `sys_users` | User | 用户 (引用 platform-security 实体) |
| `up_process_instance` | ProcessInstance | 流程实例 (跨模块引用) |

表名前缀: `dw_` = developer-workstation

注意: `Permission` 和 `Role` 实体来自 `platform-security` 模块 (`com.platform.security.entity`)，developer-workstation 通过 Repository 引用但不定义这些实体。

---

## 17. 已知限制与技术债务

### 控制器层

当前 `controller` 包共 **22 个具体控制器类** + **`BaseController` 抽象基类**（2026-04-08 清点）。其中 **7 个**继承 `BaseController`，**15 个**不继承；不继承者响应格式与异常处理各自实现，**并非**都是 `ApiResponse` 包装。

**继承 `BaseController`（7）**  
`FunctionUnitController`、`TableDesignController`、`TableRelationController`、`MemberController`、`DecisionDesignController`、`AiGenerationController`、`ResilienceController`。

**不继承 `BaseController`（15）**  
`AuthController`、`AuthSsoExchangeController`、`FileUploadController`、`FormDesignController`、`FormStageBindingController`、`ProcessDesignController`、`ActionDesignController`、`ActionQueryController`、`DeploymentController`、`ExportImportController`、`IconLibraryController`、`LookupComponentController`、`RelationTableBindingController`、`RelationTableViewController`、`VersionController`。

| 控制器 | 备注 |
|--------|------|
| `ActionQueryController` | 部分路径直接走 Repository / 查询，与典型 Component 分层不一致 |
| `VersionController` | 路径前缀为 `/api/function-units`，与其余多数 `/function-units` 风格并存 |
| `AuthController` / `AuthSsoExchangeController` | 认证与 SSO 兑换，响应模型与业务 API 不同 |

### 安全相关

- `SubTableField.vue` (developer-workstation 和 user-portal) 使用 `v-html="scope.row[col.field]"` 渲染富文本预览，未做 sanitize — 可接受 (企业内部应用，数据来源可信)
- `ForceUnlockResponseRequest` 仅有 `boolean accept` 字段，无需额外校验注解

### 类型安全

- 部分前端 TS 文件使用 `any` 类型 — 已确认为合理场景:
  - BPMN 类型定义 (bpmn-js 库无完整 TS 类型)
  - 动态 JSON 解析
  - Element Plus 事件回调

### 部署状态存储

- **已演进**：`DeploymentComponentImpl` 通过 **`DeploymentJobService`** 将部署任务写入表 **`dw_deployment_jobs`**（实体 `DeploymentJob`，Flyway **`V309__create_dw_deployment_jobs.sql`** 等），支持多实例与进程重启后仍可查进度/历史。
- 若仍有少量仅内存的辅助状态（如 SSE、AI undo 快照等），见各 `*ServiceImpl` / `*ComponentImpl` 实现，**不等同于**部署任务主存储。

### VersionController 路径

- `VersionController` 使用 `/api/function-units` 路径前缀，与其他控制器的 `/function-units` 不一致
- 这是因为 VersionController 属于独立的版本管理 spec，有自己的 Service 层 (DeploymentService, VersionService, RollbackService, UIService)

### ProcessDefinition.functionUnitVersionId

- `function_unit_version_id` 的 FK 指向 `dw_function_units(id)` 而非 `dw_versions(id)` — 这是设计决策，非 bug

### ActionDefinition.action_type 列长度不一致

- `developer-workstation` 中 `action_type` 为 `VARCHAR(20)`
- `admin-center` 和 `user-portal` 中 `action_type` 为 `VARCHAR(50)`
- 当前最长枚举值 `PROCESS_SUBMIT` (14 字符) 在 20 字符限制内，暂无问题
- 建议统一为 `VARCHAR(50)` 以保持跨模块一致性

---

## 附录 A: Repository 清单

| Repository | 实体 | 说明 |
|------------|------|------|
| FunctionUnitRepository | FunctionUnit | existsByName, findByName |
| TableDefinitionRepository | TableDefinition | findByFunctionUnitId |
| FieldDefinitionRepository | FieldDefinition | findByTableDefinitionId |
| ForeignKeyRepository | ForeignKey | findByTableDefinitionId |
| FormDefinitionRepository | FormDefinition | findByFunctionUnitId |
| FormTableBindingRepository | FormTableBinding | findByFormId |
| ActionDefinitionRepository | ActionDefinition | findByFunctionUnitId |
| ProcessDefinitionRepository | ProcessDefinition | findByFunctionUnitId |
| VersionRepository | Version | findByFunctionUnitIdAndVersionNumber |
| IconRepository | Icon | findByName, search |
| AiSessionRepository | AiSession | findByFunctionUnitId |
| AiMessageRepository | AiMessage | findBySessionId |
| AiDocumentRepository | AiDocument | findByFunctionUnitIdAndDocumentType |
| FunctionUnitAccessRepository | FunctionUnitAccess | 访问控制 |
| MemberRepository | Member | 成员管理 |
| UserRepository | User | 用户查询 (实体来自 platform-security) |
| RoleRepository | Role | 角色查询 (实体来自 platform-security) |
| PermissionRepository | Permission | 权限查询 (实体来自 platform-security) |
| OperationLogRepository | OperationLog | 操作日志 |
| ProcessInstanceRepository | ProcessInstance | 流程实例 |
| DeploymentJobRepository | DeploymentJob | 部署任务持久化 |
| DecisionDefinitionRepository | DecisionDefinition | DMN / 决策定义 |
| FormStageBindingRepository | FormStageBinding | 表单阶段绑定 |
| FunctionUnitDevGroupAssignmentRepository | FunctionUnitDevGroupAssignment | 功能单元—虚拟开发组 |
| TableRelationRepository | TableRelation | 表关系 |
| RelationLookupConfigRepository | RelationLookupConfig | 关联表查找配置 |
| RelationViewConfigRepository | RelationViewConfig | 关联视图配置 |
| RelationViewFieldRepository | RelationViewField | 关联视图字段 |

---

## 附录 B: Component 接口清单

| Component | 实现类 | 职责 |
|-----------|--------|------|
| FunctionUnitComponent | FunctionUnitComponentImpl | 功能单元 CRUD、发布、克隆、校验 |
| TableDesignComponent | TableDesignComponentImpl | 表设计 CRUD、DDL 生成、关系校验 |
| FormDesignComponent | FormDesignComponentImpl | 表单设计 CRUD、绑定管理、配置生成 |
| ActionDesignComponent | ActionDesignComponentImpl | 动作设计 CRUD、测试执行 |
| ProcessDesignComponent | ProcessDesignComponentImpl | 流程设计 CRUD、BPMN 校验、模拟 |
| DeploymentComponent | DeploymentComponentImpl | 一键部署、状态查询、历史 |
| ExportImportComponent | ExportImportComponentImpl | 导入导出、冲突检查 |
| IconLibraryComponent | IconLibraryComponentImpl | 图标上传、搜索、使用检查 |
| AiGenerationComponent | AiGenerationComponentImpl | AI 对话、锁管理、文档、数据应用 |
| VersionComponent | VersionComponentImpl | 版本历史查询 (注意: VersionController 不使用此 Component，而是直接注入 DeploymentService, VersionService, RollbackService, UIService) |

---

## 附录 C: 前端视图结构 (developer-workstation)

```
frontend/developer-workstation/src/
├── api/
│   ├── index.ts             # Axios 实例配置
│   ├── functionUnit.ts    # 功能单元 API
│   ├── aiGeneration.ts    # AI 生成 API
│   ├── icon.ts            # 图标 API
│   ├── auth.ts            # 认证 API
│   ├── adminCenter.ts     # admin-center 跨服务调用
│   ├── n8n.ts             # N8N 工作流 API
│   ├── user.ts            # 用户 API
│   ├── relationTable.ts   # 关联表（RelationTable）API
│   └── decision.ts        # 决策（Decision）API
├── views/
│   ├── function-unit/       # 功能单元列表/详情
│   ├── icon/                # 图标管理
│   ├── profile/             # 用户资料
│   └── Login.vue            # 登录页
├── components/
│   ├── ai/                  # AI 对话面板
│   ├── debug/               # 调试工具
│   ├── designer/            # 设计器公共组件
│   ├── function-unit/       # 功能单元相关组件
│   ├── icon/                # 图标选择器
│   ├── version/             # 版本管理组件
│   └── UserProfileDropdown.vue  # 用户下拉菜单
├── composables/
│   ├── useAiChat.ts         # AI 对话
│   ├── useAiEvents.ts       # AI SSE 事件
│   ├── useAiLock.ts         # AI 编辑锁
│   ├── useAiSession.ts      # AI 会话管理
│   └── useSidebarState.ts   # 侧边栏状态
├── stores/
│   └── functionUnit.ts      # Pinia store
└── i18n/
    └── locales/
        ├── en.ts            # 英文 (默认)
        ├── zh-CN.ts         # 简体中文
        └── zh-TW.ts         # 繁体中文
```

---

> 文档结束。本文档覆盖了功能单元模块的完整架构、实体、API、数据流、配置和约定，可作为 AI 助手和开发者的权威参考。
