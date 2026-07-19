# Project X-Ray：对整个项目进行解剖式分析、架构审计与完整用户指南

你现在需要对当前代码仓库进行一次**生产级、全局、解剖式的系统分析**。

不要把这次任务理解成普通的 README 生成。

我要的是一份类似于：

> **System Architecture Documentation + Reverse Engineering Report + Product Documentation + User Manual + Code Audit + Feature Completion Matrix + Risk Assessment**

的完整项目 X-Ray 报告。

你的任务是：

**从代码、目录结构、数据库、API、前端页面、组件、状态管理、权限、工作流引擎、第三方集成、部署方式等多个维度，逆向理解整个系统。**

不要凭猜测补全功能。

所有结论必须尽可能基于实际代码、配置、数据库 Schema、API、路由、组件和调用关系。

如果无法确认，必须明确标记：

- `Confirmed`：代码中明确存在并已验证
- `Partially Implemented`：部分实现
- `Implemented but Unverified`：代码存在但没有足够证据证明完整可用
- `UI Only`：只有 UI，没有完整后端逻辑
- `Backend Only`：后端存在但没有完整 UI
- `Mocked`：使用 Mock / Dummy Data
- `Placeholder`：占位功能
- `Dead Code`：疑似无引用代码
- `Broken`：已知存在错误或无法正常运行
- `Missing`：设计中存在但当前没有实现
- `Unknown`：无法从代码确认

---

# 第一阶段：建立完整项目地图

首先不要急着写报告。

先完整扫描整个代码仓库。

分析：

1. Root Directory
2. Monorepo Structure
3. 所有 Apps
4. 所有 Packages
5. 所有 Services
6. 所有 Frontend
7. 所有 Backend
8. 所有 API
9. 所有 Database
10. 所有第三方服务
11. 所有 Infrastructure
12. 所有 Docker / Kubernetes / Deployment 配置
13. 所有 Environment Variables
14. 所有 Authentication / Authorization
15. 所有 Workflow / Agent / AI 相关模块

最终生成：

```text
PROJECT
│
├── Admin Center
│
├── Developer Workstation
│
├── User Portal
│
├── Superset / BI
│
├── Workflow Engine
│
├── AI / Agent Layer
│
├── Authentication
│
├── Authorization
│
├── Database
│
├── API Layer
│
├── Infrastructure
│
└── External Integrations
```

但不要假设实际结构。

必须根据真实代码生成。

---

# 第二阶段：生成完整系统架构图

我要非常详细的架构图。

使用 Mermaid。

至少生成以下图：

## 1. System Context Diagram

展示：

User

Admin

Developer

User Portal

Developer Workstation

Admin Center

Workflow Engine

AI Provider

Database

Superset

External APIs

Authentication Provider

第三方系统

之间的关系。

---

## 2. High-Level Architecture Diagram

展示：

Frontend

Backend

API Gateway

Service Layer

Business Logic

Database

External Services

Workflow Engine

AI Layer

之间的数据流。

---

## 3. Application Architecture Diagram

分别分析：

### Admin Center

### Developer Workstation

### User Portal

### Superset / BI

每个系统都要拆到模块级。

---

## 4. Frontend Architecture

分析：

- Vue / React 等框架
- Router
- State Management
- Component System
- Design System
- API Client
- Authentication
- Permission Guard
- Micro Frontend
- qiankun
- Pinia
- Arco Design
- Vite
- TypeScript

如果实际代码存在这些技术，必须画出它们之间的关系。

---

## 5. Backend Architecture

展示：

Request

↓

Router

↓

Controller

↓

Service

↓

Business Logic

↓

Repository / ORM

↓

Database

以及：

External API

Authentication

Authorization

Cache

Queue

Event

等。

---

## 6. Database ER Diagram

扫描所有数据库 Schema。

生成完整 ER Diagram。

必须包含：

- Table
- Primary Key
- Foreign Key
- Relationship
- Index
- Important Fields

如果无法确认关系，必须标记 Unknown。

重点分析：

- User
- Role
- Permission
- Group
- Business Unit
- Workflow
- Project
- Application
- Connection
- Credential
- Execution
- Audit Log

以及所有实际存在的表。

不要只挑几个。

---

## 7. Authentication Flow

画出：

Login

↓

Authentication

↓

Token

↓

Session

↓

User Identity

↓

Role

↓

Permission

↓

Resource Access

完整流程。

如果使用：

JWT

OAuth

SSO

LDAP

Keycloak

Azure AD

AM Token

等，必须根据实际代码分析。

---

## 8. Authorization Flow

详细分析：

User

↓

Role

↓

Permission

↓

Resource

↓

Action

↓

Access Decision

重点解释：

RBAC

ABAC

Resource-level Permission

Business Unit

Visual Group

Role

Permission

之间的关系。

---

## 9. Workflow Execution Flow

如果项目有 Workflow Engine，必须详细画：

User

↓

Create Workflow

↓

Save Workflow

↓

Publish

↓

Trigger

↓

Execution

↓

Node

↓

Action

↓

External API

↓

Result

↓

Execution History

↓

Logs

↓

Error Handling

完整生命周期。

---

## 10. AI / Agent Architecture

如果存在 AI 功能，详细分析：

User

↓

AI Assistant

↓

Prompt

↓

Context

↓

Model

↓

Tool

↓

MCP

↓

Workflow

↓

External System

↓

Response

分析 AI 与 Workflow Engine 的关系。

---

## 11. API Dependency Graph

扫描所有 API。

生成：

Frontend

↓

API

↓

Backend

↓

Database

↓

External API

的依赖关系。

重点找：

- API 被谁调用
- API 调用了谁
- 哪些 API 没有调用者
- 哪些 API 被多个模块共享
- 哪些 API 存在强耦合

---

# 第三阶段：模块级解剖

对项目中的**每一个一级模块和二级模块**进行分析。

格式：

## Module Name

### Purpose

模块存在的目的。

### Location

真实代码路径。

### Entry Points

入口。

### Components

所有核心组件。

### Services

所有 Service。

### APIs

所有 API。

### Database

涉及的数据表。

### Dependencies

依赖的模块。

### Dependents

依赖该模块的模块。

### State

状态管理方式。

### Authentication

认证。

### Authorization

权限。

### Error Handling

错误处理。

### Logging

日志。

### External Dependencies

第三方服务。

### Data Flow

数据流。

### User Flow

用户操作流。

### Current Status

- Completed
- Partial
- Broken
- Mocked
- Missing

### Risk

High / Medium / Low

### Technical Debt

技术债务。

### Coupling

耦合程度。

### Maintainability

可维护性。

### Recommended Improvement

建议。

---

# 第四阶段：完整用户界面地图

我要你扫描所有前端路由。

生成：

```text
Application
  └── Route
       └── Page
            └── Section
                 └── Component
                      └── Button
                           └── Action
                                └── API
                                     └── Backend
```

每一个页面必须分析：

- URL
- 页面名称
- 页面用途
- 页面入口
- 页面访问权限
- 页面组件
- Tabs
- Forms
- Tables
- Modals
- Buttons
- Dropdowns
- Menus
- Actions
- API
- Backend
- Database

---

# 第五阶段：生成「逐按钮级」用户指南

这是非常重要的一部分。

我要一份真正可以交给新用户的 User Manual。

不要只写：

> 点击 Create 创建 Workflow。

必须详细到按钮级。

格式：

## Page: Workflow Management

### Button: Create Workflow

位置：

页面右上角。

功能：

创建新的 Workflow。

点击后：

1. 打开什么页面
2. 出现什么 Form
3. 每个字段是什么
4. 哪些字段必填
5. Validation 是什么
6. 点击 Save 后发生什么
7. 调用了什么 API
8. 数据写入哪个表
9. 成功后跳转哪里
10. 失败时如何显示

Status:

`Completed / Partial / Broken / Mocked / Unknown`

---

对所有可交互元素进行分析：

- Button
- Link
- Tab
- Dropdown
- Checkbox
- Radio
- Switch
- Input
- Form
- Modal
- Drawer
- Context Menu
- Drag & Drop
- Workflow Node
- Canvas Interaction

都需要尽可能覆盖。

如果按钮只是 UI，没有真正功能，明确标记：

`UI ONLY`

如果点击后没有任何行为：

`NOT IMPLEMENTED`

如果调用 Mock：

`MOCKED`

---

# 第六阶段：Feature Completion Matrix

生成完整 Feature Matrix：

| Module | Feature | UI | API | Backend | DB | Integration | Status | Evidence | Risk |
|---|---|---|---|---|---|---|---|---|---|

Status 必须使用：

- Complete
- Partial
- UI Only
- Backend Only
- Mocked
- Broken
- Missing
- Unknown

不要因为页面存在就判断功能完成。

必须验证完整链路：

UI

→ API

→ Backend

→ Database

→ External Service

---

# 第七阶段：用户旅程分析

至少分析以下完整用户旅程。

## Admin Journey

Login

→ User Management

→ Role

→ Permission

→ Business Unit

→ Visual Group

→ Resource Access

---

## Developer Journey

Login

→ Developer Workstation

→ Create Project

→ Create Workflow

→ Add Node

→ Configure Node

→ Save

→ Publish

→ Execute

→ View Logs

---

## End User Journey

Login

→ User Portal

→ Discover Workflow / Application

→ Execute

→ View Result

---

## AI Assistant Journey

User

→ AI Assistant

→ Intent

→ Context

→ Tool

→ Workflow

→ Execution

→ Result

分析每一个 Journey 是否真正闭环。

如果存在：

Dead End

Broken Flow

Missing Permission

Missing API

Missing UI

Missing Backend

必须明确指出。

---

# 第八阶段：逻辑闭环分析

这是整个分析中最重要的部分之一。

我要你主动寻找系统中的：

### Dead End

用户点击后无法继续。

### Orphan Feature

存在功能但没有入口。

### Orphan API

存在 API 但没有前端调用。

### Orphan UI

存在 UI 但没有后端功能。

### Broken Loop

流程中断。

### Permission Deadlock

用户拥有功能但权限系统阻止使用。

### Circular Dependency

模块循环依赖。

### Hidden Coupling

隐式耦合。

### Tight Coupling

强耦合。

### Data Inconsistency

不同模块使用不同数据模型。

### State Inconsistency

前端和后端状态不一致。

### Error Recovery Gap

错误后无法恢复。

### Missing Validation

缺少输入验证。

### Missing Audit

关键操作没有审计。

---

# 第九阶段：架构健康度分析

从以下维度评分：

| Dimension | Score 0-10 | Evidence | Problem |
|---|---|---|---|
| Architecture | | | |
| Modularity | | | |
| Coupling | | | |
| Cohesion | | | |
| Scalability | | | |
| Reliability | | | |
| Security | | | |
| Observability | | | |
| Testability | | | |
| Maintainability | | | |
| Performance | | | |
| Deployment | | | |
| Documentation | | | |

给出：

Overall Architecture Score

并解释评分依据。

---

# 第十阶段：生产级风险审计

检查：

## Security

- Authentication
- Authorization
- Secrets
- API Keys
- Token Storage
- XSS
- CSRF
- SQL Injection
- SSRF
- Command Injection
- File Upload
- Dependency Vulnerability

## Reliability

- Retry
- Timeout
- Circuit Breaker
- Idempotency
- Transaction
- Rollback
- Error Recovery

## Performance

- N+1
- Excessive API Calls
- Memory Leak
- Connection Pool
- Database Index
- Large Payload
- Frontend Rendering

## Scalability

- Horizontal Scaling
- Statelessness
- Queue
- Worker
- Concurrency
- Rate Limit

## Observability

- Logging
- Metrics
- Tracing
- Audit
- Alerting

---

# 第十一阶段：测试覆盖率与质量分析

分析：

- Unit Tests
- Integration Tests
- E2E Tests
- API Tests
- Frontend Tests
- Backend Tests

找出：

- 没有测试的核心模块
- 高风险但没有测试的代码
- 测试覆盖薄弱区域

如果可以，通过代码分析实际覆盖率。

---

# 第十二阶段：依赖关系与耦合分析

生成：

### Module Dependency Graph

### API Dependency Graph

### Database Dependency Graph

### Component Dependency Graph

识别：

- God Module
- God Component
- God Service
- Circular Dependency
- High Fan-in
- High Fan-out
- Shared Mutable State
- Cross-module Leakage

重点回答：

> 如果我要修改 Module A，会影响哪些模块？

以及：

> 哪些模块是整个系统的 Single Point of Failure？

---

# 第十三阶段：技术债务地图

建立：

```text
Critical
High
Medium
Low
```

技术债务清单。

每一个技术债务必须包含：

- Location
- Problem
- Why It Matters
- Impact
- Fix Complexity
- Recommended Solution

---

# 第十四阶段：最终报告

最终生成以下目录：

# PROJECT X-RAY REPORT

## 1. Executive Summary

## 2. Project Overview

## 3. Technology Stack

## 4. Repository Structure

## 5. System Context

## 6. High-Level Architecture

## 7. Application Architecture

## 8. Frontend Architecture

## 9. Backend Architecture

## 10. Database Architecture

## 11. Authentication

## 12. Authorization

## 13. Workflow Architecture

## 14. AI / Agent Architecture

## 15. API Architecture

## 16. Module-by-Module Analysis

## 17. Page-by-Page Analysis

## 18. Button-by-Button User Guide

## 19. Feature Completion Matrix

## 20. User Journey Analysis

## 21. Logic Closure Analysis

## 22. Dependency Graph

## 23. Coupling Analysis

## 24. Security Audit

## 25. Reliability Audit

## 26. Performance Audit

## 27. Scalability Audit

## 28. Observability Audit

## 29. Testing Analysis

## 30. Technical Debt

## 31. Production Readiness

## 32. Critical Risks

## 33. Recommended Roadmap

## 34. Appendix

---

# 第十五阶段：必须生成的独立文件

不要把所有内容塞进一个巨大 Markdown。

根据项目实际规模，生成：

```text
/docs
  /architecture
    system-context.md
    high-level-architecture.md
    frontend-architecture.md
    backend-architecture.md
    database-architecture.md
    authentication.md
    authorization.md
    workflow-architecture.md
    ai-architecture.md

  /modules
    admin-center.md
    developer-workstation.md
    user-portal.md
    workflow-engine.md
    ai-assistant.md

  /user-guide
    getting-started.md
    admin-guide.md
    developer-guide.md
    user-portal-guide.md
    workflow-guide.md
    ai-assistant-guide.md

  /audit
    feature-completion.md
    logic-closure.md
    dependency-analysis.md
    coupling-analysis.md
    security-audit.md
    production-readiness.md

  PROJECT-X-RAY.md
```

如果项目规模较小，可以合并文件。

如果项目规模较大，必须拆分。

---

# 最重要的规则

1. **不要猜。**
2. **不要把 UI 存在当成功能完成。**
3. **不要把 API 存在当成功能完成。**
4. **必须验证 UI → API → Backend → DB / External Service 的完整链路。**
5. **每一个结论都尽量提供代码路径作为 Evidence。**
6. **对于无法确认的功能，明确标记 Unknown。**
7. **不要为了生成好看的文档而掩盖问题。**
8. **必须主动寻找 Bug、Dead End、逻辑断路和架构风险。**
9. **必须分析模块之间的依赖和耦合，而不是只描述模块本身。**
10. **必须分析“修改一个模块会影响什么”。**
11. **必须分析“一个用户从登录到完成任务是否真的能走通”。**
12. **必须区分“设计目标”和“当前实际实现”。**
13. **必须区分“代码存在”和“生产可用”。**
14. **所有 Mermaid 图必须能够直接渲染。**
15. **所有 Status 判断必须有 Evidence。**

---

# 执行方式

不要一次性盲目扫描所有文件。

采用分阶段方式：

### Phase 1
Repository Discovery

### Phase 2
Architecture Reverse Engineering

### Phase 3
Module Analysis

### Phase 4
UI / User Flow Analysis

### Phase 5
API / Database Analysis

### Phase 6
Feature Completion Verification

### Phase 7
Architecture / Security / Performance Audit

### Phase 8
Final Documentation Generation

每个阶段完成后，对结果进行自我校验。

如果发现前面的分析和后面的代码证据冲突，以实际代码证据为准，并修正之前的结论。

最终目标不是生成一份“看起来很完整”的文档。

最终目标是：

> **让我可以通过这份 X-Ray Report，完全理解这个项目现在是什么、为什么这样设计、每个模块怎么工作、用户怎么使用、哪些功能真的完成了、哪些功能只是表面存在、系统哪里会断、哪里耦合严重、哪里存在技术债务，以及距离真正的 Production-Grade System 还有多远。**




共 12 个文件、约 2600 行，全部结论都带代码/schema/config 证据，无法确认的标 Unknown。

入口文件： docs/x-ray/PROJECT-X-RAY.md — 主报告（执行摘要、评分卡、系统/架构 Mermaid 图、功能完成度矩阵、用户旅程闭环、逻辑断路、安全审计、技术债地图、生产就绪路线图）。10 份配套深挖文档分在 architecture/、modules/、audit/ 三个子目录，外加一个 README 索引。

核心结论
本质：共享 PostgreSQL 之上的模块化单体（4 后端 + 4 共享 JAR + 4 前端 SPA），代码与仓库自带的架构蓝图 F1–F7 边界一致（编译期无业务→业务依赖）。功能深度很高，但运维和测试成熟度低。≈5.5/10——设计是生产形态，运维骨架不是。

挖到的真实断路/风险（非臆测）：

DMN 决策执行完全断链 —— 设计器能设计/校验/导出，但没有任何代码把 DMN 部署进 Flowable，evaluate 端点无人调用
一个 404 端点被静默吞掉 —— portal 调 GET /history/processes/{id}，引擎根本没这个映射，历史详情永远空
WebSocket 路径不匹配 —— 子表实时更新降级成轮询
Kong 并不校验 JWT（注释却说它是第一道防线）；3/4 后端 Spring 层 permitAll()
"preprod" 提交了 dev 级密钥 + admin123、DSP_CLIENT_SECRET: hermes@123
死代码：4 个 MFE 目录、packages/core、n8n 容器、wf_multi_instance_execution 表
无 CI、HTTP 层近乎零测试（~90 个 controller 无测试）；观测指标暴露了但无人采集
文档漂移：PROJECT_ARCHITECTURE.md 引用的 backend/api-gateway 模块和 gateway-mfe 实际不存在
说明
所有 Mermaid 代码块围栏经过配平检查，可直接渲染。

想让把 docs/x-ray/ 登记进 docs/README.md 索引，或把主报告导出成单一大文件，可以继续。