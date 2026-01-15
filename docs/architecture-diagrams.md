# 工作流平台解决方案架构

## 1. 系统总体架构图

```mermaid
graph TB
    subgraph "前端层 Frontend Layer"
        AdminUI[管理员中心<br/>Admin Center<br/>Vue 3 + Element Plus]
        DevUI[开发者工作站<br/>Developer Workstation<br/>Vue 3 + BPMN.js]
        UserUI[用户门户<br/>User Portal<br/>Vue 3 + Element Plus]
    end

    subgraph "API网关层 API Gateway Layer"
        Gateway[API Gateway<br/>Spring Cloud Gateway<br/>:8080]
    end

    subgraph "微服务层 Microservices Layer"
        AdminService[管理员中心服务<br/>Admin Center Service<br/>:8090]
        DevService[开发者工作站服务<br/>Developer Workstation<br/>:8083]
        UserService[用户门户服务<br/>User Portal Service<br/>:8092]
        WorkflowService[工作流引擎核心<br/>Workflow Engine Core<br/>:8091]
    end

    subgraph "共享模块层 Shared Modules"
        Security[平台安全<br/>Platform Security<br/>JWT + RBAC]
        Common[平台公共<br/>Platform Common<br/>工具类 + DTO]
        Cache[平台缓存<br/>Platform Cache<br/>Redis]
        Messaging[平台消息<br/>Platform Messaging<br/>RabbitMQ]
    end

    subgraph "工作流引擎 Workflow Engine"
        Flowable[Flowable Engine<br/>BPMN 2.0]
    end

    subgraph "数据层 Data Layer"
        PostgreSQL[(PostgreSQL<br/>workflow_platform)]
        Redis[(Redis<br/>缓存)]
    end

    AdminUI --> Gateway
    DevUI --> Gateway
    UserUI --> Gateway

    Gateway --> AdminService
    Gateway --> DevService
    Gateway --> UserService
    Gateway --> WorkflowService

    AdminService --> Security
    DevService --> Security
    UserService --> Security
    WorkflowService --> Security

    AdminService --> Common
    DevService --> Common
    UserService --> Common
    WorkflowService --> Common

    AdminService --> Cache
    DevService --> Cache
    UserService --> Cache
    WorkflowService --> Cache

    AdminService --> Messaging
    UserService --> Messaging
    WorkflowService --> Messaging

    WorkflowService --> Flowable

    AdminService --> PostgreSQL
    DevService --> PostgreSQL
    UserService --> PostgreSQL
    WorkflowService --> PostgreSQL

    Cache --> Redis
    Messaging --> Redis

    style AdminUI fill:#e1f5ff
    style DevUI fill:#e1f5ff
    style UserUI fill:#e1f5ff
    style Gateway fill:#fff4e6
    style AdminService fill:#e8f5e9
    style DevService fill:#e8f5e9
    style UserService fill:#e8f5e9
    style WorkflowService fill:#e8f5e9
    style Flowable fill:#fce4ec
    style PostgreSQL fill:#f3e5f5
    style Redis fill:#f3e5f5
```

## 2. 微服务交互架构图

```mermaid
graph LR
    subgraph "用户门户 User Portal"
        UP[User Portal Service<br/>:8092]
    end

    subgraph "工作流引擎 Workflow Engine"
        WF[Workflow Engine Core<br/>:8091]
        FL[Flowable Engine]
    end

    subgraph "管理员中心 Admin Center"
        AC[Admin Center Service<br/>:8090]
    end

    subgraph "开发者工作站 Developer Workstation"
        DW[Developer Workstation<br/>:8083]
    end

    UP -->|启动流程<br/>完成任务<br/>查询任务| WF
    WF -->|Flowable API| FL
    WF -->|查询用户信息<br/>查询部门信息<br/>查询虚拟组| AC
    DW -->|部署流程定义<br/>查询流程| WF
    DW -->|查询部门树<br/>查询虚拟组| AC
    UP -->|查询用户信息<br/>权限验证| AC

    style UP fill:#e8f5e9
    style WF fill:#e8f5e9
    style AC fill:#e8f5e9
    style DW fill:#e8f5e9
    style FL fill:#fce4ec
```

## 3. 工作流引擎架构图

```mermaid
graph TB
    subgraph "用户门户 User Portal"
        ProcessComp[ProcessComponent<br/>流程组件]
        TaskComp[TaskProcessComponent<br/>任务处理组件]
        WFClient[WorkflowEngineClient<br/>工作流客户端]
    end

    subgraph "工作流引擎核心 Workflow Engine Core"
        ProcessCtrl[ProcessController<br/>流程控制器]
        TaskCtrl[TaskController<br/>任务控制器]
        ProcessEngine[ProcessEngineComponent<br/>流程引擎组件]
        TaskManager[TaskManagerComponent<br/>任务管理组件]
        TaskResolver[TaskAssigneeResolver<br/>处理人解析器]
        TaskListener[TaskAssignmentListener<br/>任务分配监听器]
        AdminClient[AdminCenterClient<br/>管理中心客户端]
    end

    subgraph "Flowable 引擎"
        RuntimeService[RuntimeService<br/>运行时服务]
        TaskService[TaskService<br/>任务服务]
        RepositoryService[RepositoryService<br/>仓库服务]
        HistoryService[HistoryService<br/>历史服务]
    end

    ProcessComp --> WFClient
    TaskComp --> WFClient
    WFClient -->|HTTP REST| ProcessCtrl
    WFClient -->|HTTP REST| TaskCtrl

    ProcessCtrl --> ProcessEngine
    TaskCtrl --> TaskManager

    ProcessEngine --> RuntimeService
    ProcessEngine --> RepositoryService
    TaskManager --> TaskService
    TaskManager --> HistoryService

    TaskListener --> TaskResolver
    TaskResolver --> AdminClient
    TaskListener -->|TASK_CREATED| TaskService

    style ProcessComp fill:#e1f5ff
    style TaskComp fill:#e1f5ff
    style WFClient fill:#e1f5ff
    style ProcessCtrl fill:#e8f5e9
    style TaskCtrl fill:#e8f5e9
    style ProcessEngine fill:#fff4e6
    style TaskManager fill:#fff4e6
    style TaskResolver fill:#fff4e6
    style TaskListener fill:#fff4e6
    style RuntimeService fill:#fce4ec
    style TaskService fill:#fce4ec
    style RepositoryService fill:#fce4ec
    style HistoryService fill:#fce4ec
```

## 4. 任务分配机制架构图

```mermaid
graph TB
    Start[流程启动] --> SetInitiator[设置 initiator 变量]
    SetInitiator --> TaskCreated[任务创建事件]
    TaskCreated --> Listener[TaskAssignmentListener<br/>监听 TASK_CREATED]
    Listener --> ReadBPMN[读取 BPMN 扩展属性<br/>assigneeType<br/>assigneeValue]
    ReadBPMN --> Resolver[TaskAssigneeResolver<br/>解析处理人]

    Resolver --> CheckType{分配类型}

    CheckType -->|FUNCTION_MANAGER| GetFM[查询职能经理]
    CheckType -->|ENTITY_MANAGER| GetEM[查询实体经理]
    CheckType -->|INITIATOR| GetInit[获取发起人]
    CheckType -->|DEPT_OTHERS| GetDO[查询部门其他人]
    CheckType -->|PARENT_DEPT| GetPD[查询上级部门]
    CheckType -->|FIXED_DEPT| GetFD[查询指定部门]
    CheckType -->|VIRTUAL_GROUP| GetVG[查询虚拟组]

    GetFM --> DirectAssign[直接分配<br/>setAssignee]
    GetEM --> DirectAssign
    GetInit --> DirectAssign

    GetDO --> CandidateAssign[候选人分配<br/>setCandidateUsers/Group]
    GetPD --> CandidateAssign
    GetFD --> CandidateAssign
    GetVG --> CandidateAssign

    DirectAssign --> TaskReady[任务就绪<br/>直接处理]
    CandidateAssign --> TaskClaim[任务待认领<br/>需要认领]

    style Start fill:#e8f5e9
    style Listener fill:#fff4e6
    style Resolver fill:#fff4e6
    style DirectAssign fill:#e1f5ff
    style CandidateAssign fill:#fce4ec
    style TaskReady fill:#c8e6c9
    style TaskClaim fill:#ffccbc
```

## 5. 数据库架构图

```mermaid
erDiagram
    %% 管理员中心表
    admin_organizations ||--o{ admin_departments : contains
    admin_departments ||--o{ admin_users : belongs_to
    admin_users ||--o{ admin_role_assignments : has
    admin_roles ||--o{ admin_role_assignments : assigned_to
    admin_virtual_groups ||--o{ admin_virtual_group_members : contains
    admin_users ||--o{ admin_virtual_group_members : member_of

    %% 开发者工作站表
    dw_function_units ||--o{ dw_table_definitions : contains
    dw_function_units ||--o{ dw_form_definitions : contains
    dw_function_units ||--o{ dw_action_definitions : contains
    dw_function_units ||--o{ dw_process_definitions : contains
    dw_table_definitions ||--o{ dw_field_definitions : contains
    dw_table_definitions ||--o{ dw_foreign_keys : source
    dw_table_definitions ||--o{ dw_foreign_keys : target
    dw_form_definitions ||--o{ dw_form_table_bindings : binds
    dw_table_definitions ||--o{ dw_form_table_bindings : bound_by

    %% 工作流引擎表
    ACT_RE_PROCDEF ||--o{ ACT_RU_EXECUTION : defines
    ACT_RU_EXECUTION ||--o{ ACT_RU_TASK : creates
    ACT_RU_TASK ||--o{ ACT_HI_TASKINST : history

    %% 平台安全表
    sys_users ||--o{ sys_user_roles : has
    sys_roles ||--o{ sys_user_roles : assigned
    sys_roles ||--o{ sys_role_permissions : has
    sys_permissions ||--o{ sys_role_permissions : granted

    admin_organizations {
        bigint id PK
        varchar code UK
        varchar name
        varchar type
        timestamp created_at
    }

    admin_departments {
        bigint id PK
        bigint organization_id FK
        bigint parent_id FK
        varchar code UK
        varchar name
        int level
    }

    admin_users {
        bigint id PK
        bigint department_id FK
        varchar username UK
        varchar email
        varchar phone
        varchar status
    }

    dw_function_units {
        bigint id PK
        varchar code UK
        varchar name
        varchar status
        bigint icon_id FK
    }

    dw_table_definitions {
        bigint id PK
        bigint function_unit_id FK
        varchar table_name
        varchar table_type
        text description
    }

    dw_field_definitions {
        bigint id PK
        bigint table_id FK
        varchar field_name
        varchar data_type
        int length
        boolean nullable
    }

    dw_form_definitions {
        bigint id PK
        bigint function_unit_id FK
        varchar form_name
        varchar form_type
        jsonb config_json
    }

    dw_action_definitions {
        bigint id PK
        bigint function_unit_id FK
        varchar action_name
        varchar action_type
        jsonb config_json
    }

    dw_process_definitions {
        bigint id PK
        bigint function_unit_id FK
        text bpmn_xml
    }
```

## 6. 部署架构图

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Ingress Layer"
            Ingress[Ingress Controller<br/>Nginx]
        end

        subgraph "Frontend Pods"
            AdminPod[Admin Center Pod<br/>Nginx + Vue]
            DevPod[Developer Workstation Pod<br/>Nginx + Vue]
            UserPod[User Portal Pod<br/>Nginx + Vue]
        end

        subgraph "Gateway Pod"
            GatewayPod[API Gateway Pod<br/>Spring Cloud Gateway]
        end

        subgraph "Backend Pods"
            AdminSvcPod[Admin Center Service Pod]
            DevSvcPod[Developer Workstation Pod]
            UserSvcPod[User Portal Service Pod]
            WorkflowPod[Workflow Engine Pod]
        end

        subgraph "Data Layer"
            PostgresPod[PostgreSQL StatefulSet]
            RedisPod[Redis StatefulSet]
        end

        subgraph "Config & Discovery"
            ConfigMap[ConfigMap<br/>应用配置]
            Secrets[Secrets<br/>敏感信息]
        end
    end

    Ingress --> AdminPod
    Ingress --> DevPod
    Ingress --> UserPod
    Ingress --> GatewayPod

    GatewayPod --> AdminSvcPod
    GatewayPod --> DevSvcPod
    GatewayPod --> UserSvcPod
    GatewayPod --> WorkflowPod

    AdminSvcPod --> PostgresPod
    DevSvcPod --> PostgresPod
    UserSvcPod --> PostgresPod
    WorkflowPod --> PostgresPod

    AdminSvcPod --> RedisPod
    DevSvcPod --> RedisPod
    UserSvcPod --> RedisPod
    WorkflowPod --> RedisPod

    ConfigMap -.-> AdminSvcPod
    ConfigMap -.-> DevSvcPod
    ConfigMap -.-> UserSvcPod
    ConfigMap -.-> WorkflowPod

    Secrets -.-> PostgresPod
    Secrets -.-> RedisPod

    style Ingress fill:#fff4e6
    style AdminPod fill:#e1f5ff
    style DevPod fill:#e1f5ff
    style UserPod fill:#e1f5ff
    style GatewayPod fill:#fff4e6
    style AdminSvcPod fill:#e8f5e9
    style DevSvcPod fill:#e8f5e9
    style UserSvcPod fill:#e8f5e9
    style WorkflowPod fill:#e8f5e9
    style PostgresPod fill:#f3e5f5
    style RedisPod fill:#f3e5f5
```

## 7. 技术栈架构图

```mermaid
graph TB
    subgraph "前端技术栈"
        Vue[Vue 3<br/>Composition API]
        ElementPlus[Element Plus<br/>UI组件库]
        BPMN[BPMN.js<br/>流程设计器]
        Pinia[Pinia<br/>状态管理]
        VueRouter[Vue Router<br/>路由管理]
        Axios[Axios<br/>HTTP客户端]
        I18n[Vue I18n<br/>国际化]
    end

    subgraph "后端技术栈"
        SpringBoot[Spring Boot 3.x<br/>应用框架]
        SpringCloud[Spring Cloud Gateway<br/>API网关]
        SpringSecurity[Spring Security<br/>安全框架]
        Flowable[Flowable 7.x<br/>工作流引擎]
        JPA[Spring Data JPA<br/>数据访问]
        Flyway[Flyway<br/>数据库迁移]
        Lombok[Lombok<br/>代码简化]
        Jackson[Jackson<br/>JSON处理]
    end

    subgraph "数据存储"
        PostgreSQL[PostgreSQL 15<br/>关系数据库]
        Redis[Redis 7<br/>缓存/消息]
    end

    subgraph "DevOps"
        Docker[Docker<br/>容器化]
        K8s[Kubernetes<br/>容器编排]
        Helm[Helm<br/>包管理]
        Maven[Maven<br/>构建工具]
        Vite[Vite<br/>前端构建]
    end

    Vue --> ElementPlus
    Vue --> BPMN
    Vue --> Pinia
    Vue --> VueRouter
    Vue --> Axios
    Vue --> I18n

    SpringBoot --> SpringCloud
    SpringBoot --> SpringSecurity
    SpringBoot --> Flowable
    SpringBoot --> JPA
    SpringBoot --> Flyway
    SpringBoot --> Lombok
    SpringBoot --> Jackson

    JPA --> PostgreSQL
    SpringBoot --> Redis

    Docker --> K8s
    K8s --> Helm
    Maven --> Docker
    Vite --> Docker

    style Vue fill:#42b883
    style SpringBoot fill:#6db33f
    style PostgreSQL fill:#336791
    style Redis fill:#dc382d
    style Docker fill:#2496ed
    style K8s fill:#326ce5
```

## 8. 安全架构图

```mermaid
graph TB
    User[用户] --> Browser[浏览器]
    Browser --> Ingress[Ingress<br/>HTTPS/TLS]
    Ingress --> Frontend[前端应用]
    Frontend --> Gateway[API Gateway<br/>JWT验证]

    Gateway --> AuthCheck{JWT有效?}
    AuthCheck -->|否| Reject[拒绝访问<br/>401]
    AuthCheck -->|是| ExtractUser[提取用户信息]

    ExtractUser --> RBACCheck{权限检查}
    RBACCheck -->|无权限| Reject403[拒绝访问<br/>403]
    RBACCheck -->|有权限| Backend[后端服务]

    Backend --> SecurityContext[Security Context<br/>用户上下文]
    SecurityContext --> BusinessLogic[业务逻辑]

    subgraph "认证流程"
        Login[登录请求] --> ValidateCred[验证凭证]
        ValidateCred --> GenToken[生成JWT Token]
        GenToken --> ReturnToken[返回Token]
    end

    subgraph "授权流程"
        CheckRole[检查角色] --> CheckPerm[检查权限]
        CheckPerm --> CheckResource[检查资源]
    end

    RBACCheck --> CheckRole

    style User fill:#e1f5ff
    style Ingress fill:#fff4e6
    style Gateway fill:#fff4e6
    style Reject fill:#ffcdd2
    style Reject403 fill:#ffcdd2
    style Backend fill:#e8f5e9
    style SecurityContext fill:#c8e6c9
```

## 9. 功能单元设计流程图

```mermaid
graph TB
    Start[开始] --> CreateFU[创建功能单元]
    CreateFU --> DesignTable[设计表结构]
    DesignTable --> DefineFields[定义字段]
    DefineFields --> SetFK[设置外键关系]
    SetFK --> CreateForm[创建表单]
    CreateForm --> BindTable[绑定表单-表关系]
    BindTable --> ConfigForm[配置表单规则<br/>form-create]
    ConfigForm --> CreateAction[创建动作]
    CreateAction --> ConfigAction[配置动作<br/>config_json]
    ConfigAction --> DesignProcess[设计流程<br/>BPMN]
    DesignProcess --> BindNode[绑定节点<br/>表单+动作]
    BindNode --> SetAssignee[配置处理人<br/>7种分配方式]
    SetAssignee --> Deploy[部署流程]
    Deploy --> Test[测试流程]
    Test --> End[完成]

    style Start fill:#e8f5e9
    style CreateFU fill:#e1f5ff
    style DesignTable fill:#fff4e6
    style CreateForm fill:#fff4e6
    style CreateAction fill:#fff4e6
    style DesignProcess fill:#fce4ec
    style Deploy fill:#c8e6c9
    style End fill:#e8f5e9
```

## 10. 系统集成架构图

```mermaid
graph TB
    subgraph "外部系统"
        LDAP[LDAP/AD<br/>用户目录]
        Email[邮件服务<br/>SMTP]
        SMS[短信服务<br/>SMS Gateway]
        FileStorage[文件存储<br/>MinIO/S3]
    end

    subgraph "工作流平台"
        Gateway[API Gateway]
        AdminCenter[管理员中心]
        UserPortal[用户门户]
        WorkflowEngine[工作流引擎]
        Messaging[消息服务]
    end

    subgraph "监控与日志"
        Prometheus[Prometheus<br/>监控]
        Grafana[Grafana<br/>可视化]
        ELK[ELK Stack<br/>日志分析]
    end

    LDAP -->|用户同步| AdminCenter
    AdminCenter -->|发送邮件| Email
    Messaging -->|发送短信| SMS
    UserPortal -->|上传文件| FileStorage

    Gateway --> Prometheus
    AdminCenter --> Prometheus
    UserPortal --> Prometheus
    WorkflowEngine --> Prometheus
    Prometheus --> Grafana

    Gateway --> ELK
    AdminCenter --> ELK
    UserPortal --> ELK
    WorkflowEngine --> ELK

    style LDAP fill:#e1f5ff
    style Email fill:#e1f5ff
    style SMS fill:#e1f5ff
    style FileStorage fill:#e1f5ff
    style Gateway fill:#e8f5e9
    style Prometheus fill:#fff4e6
    style Grafana fill:#fff4e6
    style ELK fill:#fff4e6
```

---

## 架构说明

### 核心特性

1. **微服务架构**: 采用 Spring Boot 微服务架构，服务间通过 REST API 通信
2. **工作流引擎**: 基于 Flowable 7.x 实现 BPMN 2.0 标准工作流
3. **前后端分离**: Vue 3 前端 + Spring Boot 后端
4. **容器化部署**: Docker + Kubernetes 容器编排
5. **安全认证**: JWT + RBAC 权限控制
6. **多租户支持**: 组织-部门-用户三级结构

### 技术选型理由

- **Flowable**: 成熟的 BPMN 2.0 工作流引擎，支持复杂流程
- **PostgreSQL**: 强大的关系数据库，支持 JSONB 类型
- **Redis**: 高性能缓存和消息队列
- **Vue 3**: 现代化前端框架，Composition API
- **Spring Boot 3**: 最新的 Java 企业级框架
- **Kubernetes**: 云原生容器编排平台

### 扩展性设计

1. **水平扩展**: 所有服务支持多实例部署
2. **垂直扩展**: 数据库支持读写分离和分片
3. **插件化**: 功能单元可独立开发和部署
4. **API优先**: 所有功能通过 REST API 暴露

---

**文档版本**: 1.0  
**最后更新**: 2026-01-14  
**维护者**: 架构团队
